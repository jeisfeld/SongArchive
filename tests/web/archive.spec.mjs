import { expect, test } from '@playwright/test';

const credentials = { username: 'fixture', password: 'fixture-password' };
const publicSongIds = ['0012a', '0011', '0010', '1000'];

function resultIds(page) {
    return page.locator('#results tr td:first-child a');
}

async function search(request, query, options = {}) {
    const response = await request.get('/search.php', { params: { q: query }, ...options });
    expect(response.status()).toBe(200);
    return response.json();
}

test('PHP search ranks text, keeps requested ID order and groups meanings', async ({ request }) => {
    const ranked = await search(request, 'Morning river');
    expect(ranked.map(song => song.id)).toEqual(['0012a', '0011', '0010']);

    const requested = await search(request, '0010; 0012a, 0010');
    expect(requested.map(song => song.id)).toEqual(['0010', '0012a']);
    expect(requested[0].meanings).toEqual([]);
    expect(requested[1].meanings).toEqual(expect.arrayContaining([
        { title: 'Context', meaning: 'A fictional translation' },
        { title: 'Pronunciation', meaning: 'Sing gently' },
    ]));
    expect(requested[1].meanings).toHaveLength(2);
    expect((await search(request, '0012a'))[0]).toEqual(requested[1]);
});

test('public browse and shuffle show the same public songs', async ({ page }) => {
    await page.goto('/');
    await expect(resultIds(page)).toHaveText(publicSongIds);
    await expect(page.getByAltText('Edit Song')).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Ada Example' })).toHaveAttribute('href', 'https://example.test');

    const shuffled = page.waitForResponse(response =>
        new URL(response.url()).searchParams.get('q') === '@@shuffle@@' && response.ok());
    await page.getByRole('button', { name: 'Shuffle songs' }).click();
    await shuffled;
    await expect.poll(async () => (await resultIds(page).allTextContents()).sort())
        .toEqual([...publicSongIds].sort());
});

test('a shared song link initializes search and opens lyrics, meanings and chords', async ({ page }) => {
    await page.goto('/?q=0012a');
    await expect(page.locator('#searchBox')).toHaveValue('0012a');
    await expect(resultIds(page)).toHaveText(['0012a']);

    await page.getByAltText('View Lyrics').click();
    const lyrics = page.locator('#lyrics-content');
    await expect(lyrics).toBeVisible();
    await expect(lyrics).toHaveText('Morning riverCarry me home');
    await expect(lyrics.locator('br')).toHaveCount(1);
    await page.locator('#show-meanings-btn').click();
    await expect(page.getByText('A fictional translation')).toBeVisible();
    await page.locator('#close-modal').click();
    await page.locator('#popup .popup-close').click();
    await expect(page.locator('#popup')).toBeHidden();

    await page.getByAltText('View Image').click();
    const chordImage = page.locator('#popup-image');
    await expect(chordImage).toBeVisible();
    await expect.poll(() => chordImage.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    await page.locator('#popup .popup-close').click();

    await page.getByRole('button', { name: 'Clear search' }).click();
    await expect(resultIds(page)).toHaveText(publicSongIds);
});

test('the audio player follows track controls and keeps a playing song visible during a search', async ({ page }) => {
    // This checks player state and track selection independently of media decoding.
    await page.addInitScript(() => {
        HTMLMediaElement.prototype.play = () => Promise.resolve();
    });
    await page.route('**/audio/songs/**', route => route.fulfill({ status: 204 }));
    await page.goto('/');
    await page.getByAltText('Play Audio').click();
    const source = page.locator('#audio-player-element source');
    await expect(source).toHaveAttribute('src', '/audio/songs/river.mp3');

    await page.getByRole('button', { name: 'Next track' }).click();
    await expect(source).toHaveAttribute('src', '/audio/songs/river-alternate.mp3');
    await page.getByRole('button', { name: 'Next track' }).click();
    await expect(source).toHaveAttribute('src', '/audio/songs/river.mp3');

    const input = page.locator('#searchBox');
    await input.fill('Evening Song');
    await input.press('End');
    await expect(resultIds(page)).toHaveText(['0012a', '0011']);
    await expect(page.locator('#results .playing-row td').first()).toHaveText('0012a');

    await page.locator('#stop-track-btn').click();
    await expect(page.locator('#audio-player-section')).toBeHidden();
    await expect(resultIds(page)).toHaveText(['0011']);
});

test('VIP entry preserves the query and expands browse in the same browser session', async ({ browser, request }) => {
    expect((await request.get('/vip/', { maxRedirects: 0 })).status()).toBe(401);
    const context = await browser.newContext({ httpCredentials: credentials });
    try {
        const page = await context.newPage();
        await page.goto('/vip/?q=Rehearsal');
        await expect(page).toHaveURL(/\/\?q=Rehearsal$/);
        await expect(resultIds(page)).toHaveText(['X001', 'Y001']);
        await page.getByRole('button', { name: 'Clear search' }).click();
        await expect(resultIds(page)).toHaveText([...publicSongIds, 'X001', 'Y001']);
        expect((await search(request, '*')).map(song => song.id)).toEqual(publicSongIds);
    } finally {
        await context.close();
    }
});

test.describe('admin', () => {
    test.use({ httpCredentials: credentials });

    test('sync endpoints include media metadata and filter private songs and their links', async ({ request }) => {
        const standardResponse = await request.get('/admin/download_data.php');
        expect(standardResponse.status()).toBe(200);
        const standard = await standardResponse.json();
        expect(standard.songs.map(song => song.id).sort()).toEqual([...publicSongIds, 'Y001'].sort());
        expect(standard.songs.find(song => song.id === '0012a')).toMatchObject({
            lyrics_short: 'Carry me home',
            lyrics_paged: 'Morning river|Carry me home',
            chordmarkup: '[Am]',
            tabfilename: 'river.jpg',
            mp3filename2: 'river-alternate.mp3',
        });
        expect(standard.meanings.map(meaning => ({ ...meaning, id: Number(meaning.id) })))
            .toContainEqual({ id: 17, title: 'Context', meaning: 'A fictional translation' });
        expect(standard.song_meanings.map(link => ({ ...link, meaning_id: Number(link.meaning_id) })))
            .toContainEqual({ song_id: '0012a', meaning_id: 17 });
        expect(standard.song_meanings.map(link => link.song_id)).not.toContain('X001');

        const privateResponse = await request.get('/admin/download_data.php?user=private');
        expect(privateResponse.status()).toBe(200);
        const privateArchive = await privateResponse.json();
        expect(privateArchive.songs.map(song => song.id)).toContain('X001');
        expect(privateArchive.song_meanings.map(link => ({ ...link, meaning_id: Number(link.meaning_id) })))
            .toContainEqual({ song_id: 'X001', meaning_id: 18 });

        const chords = await request.get('/admin/download_chords.php');
        expect(chords.status()).toBe(200);
        expect(chords.headers()['content-type']).toBe('application/zip');
        const archive = await chords.body();
        expect(archive.subarray(0, 4)).toEqual(Buffer.from([0x50, 0x4b, 0x03, 0x04]));
        expect(archive.includes(Buffer.from('river.jpg'))).toBe(true);

        const metadata = await request.get('/admin/check_update.php');
        expect(metadata.status()).toBe(200);
        expect(await metadata.json()).toEqual({ tab_count: 1, chords_zip_size: archive.length });
    });

    test('adding, editing and deleting a song refreshes the admin listing', async ({ page }) => {
        const id = 'Y900';
        const row = page.locator('#results tr').filter({ has: page.getByRole('link', { name: id, exact: true }) });
        const editor = page.frameLocator('#modal-content iframe');

        await page.goto('/admin/');
        await expect(page.getByRole('link', { name: 'X001', exact: true })).toBeVisible();
        await page.getByRole('button', { name: 'Add new song' }).click();
        await editor.locator('#song-id').fill(id);
        await editor.locator('#song-title').fill('Temporary practice song');
        await editor.locator('#song-lyrics').fill('A fictional lyric');
        await editor.getByRole('button', { name: 'Save', exact: true }).click();

        await expect(editor.locator('#song-title')).toHaveValue('Temporary practice song');
        await expect(row).toContainText('Temporary practice song');
        await editor.locator('#song-title').fill('Updated practice song');
        await editor.getByRole('button', { name: 'Save', exact: true }).click();
        await expect(page.locator('#modal-main')).toBeHidden();
        await expect(row).toContainText('Updated practice song');

        await row.getByAltText('Edit Song').focus();
        await page.keyboard.press('Enter');
        await editor.getByRole('button', { name: 'Delete', exact: true }).click();
        await expect(editor.locator('#confirm-delete-btn')).toBeDisabled();
        await editor.locator('#confirm-delete-checkbox').check();
        await editor.locator('#confirm-delete-btn').click();
        await expect(page.locator('#modal-main')).toBeHidden();
        await expect(row).toHaveCount(0);
    });

    test('the Android upload endpoint accepts local songs and rejects duplicate IDs', async ({ request }) => {
        const endpoint = '/admin/addsong_external.php';
        const id = 'Y901';
        const form = { id, title: 'Uploaded practice song', lyrics: 'First line', lyrics_paged: 'First page' };

        expect((await request.get(endpoint)).status()).toBe(405);
        expect((await request.post(endpoint, { form: { ...form, lyrics: ' ' } })).status()).toBe(400);
        expect((await request.post(endpoint, { form: { ...form, id: '0901' } })).status()).toBe(403);

        const created = await request.post(endpoint, { form });
        expect(created.status()).toBe(201);
        try {
            expect((await request.post(endpoint, { form })).status()).toBe(409);
            const sync = await request.get('/admin/download_data.php');
            expect(sync.status()).toBe(200);
            expect((await sync.json()).songs.find(song => song.id === id))
                .toMatchObject(form);
        } finally {
            const deleted = await request.post('/admin/deletesong.php', {
                form: { id, confirm_delete: 'on' },
            });
            expect((await deleted.text())).toContain('Song deleted successfully.');
        }
    });
});
