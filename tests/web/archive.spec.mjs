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

    await page.getByRole('button', { name: 'View chords', exact: true }).click();
    await expect(page.locator('.chord-sheet h2')).toHaveText('Morning River');
    await page.locator('#popup .popup-close').click();

    await page.getByRole('button', { name: 'Clear search' }).click();
    await expect(resultIds(page)).toHaveText(publicSongIds);
});

test('the chord button uses enabled text or falls back to the existing image', async ({ page }) => {
    const listed = await search(page.request, '*');
    expect(listed.map(song => [song.id, song.has_chordmarkup])).toEqual([
        ['0012a', true], ['0011', false], ['0010', false], ['1000', true],
    ]);
    expect(listed.every(song => !Object.hasOwn(song, 'chordmarkup'))).toBe(true);

    await page.goto('/');
    const chords = id => page.locator('#results tr')
        .filter({ has: page.getByRole('link', { name: id, exact: true }) })
        .getByRole('button', { name: 'View chords' });
    for (const id of ['0011', '0010']) {
        await chords(id).click();
        const image = page.locator('#popup-image');
        await expect(image).toBeVisible();
        await expect.poll(() => image.evaluate(element => element.naturalWidth)).toBeGreaterThan(0);
        await page.locator('#popup .popup-close').click();
    }
    await chords('1000').click();
    await expect(page.locator('.chord-sheet h2')).toHaveText('Morning Sky');

    await page.goto('/?q=Y001');
    await expect(resultIds(page)).toHaveText(['Y001']);
    await expect(chords('Y001')).toHaveCount(0);
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
    expect((await search(request, 'X001'))[0]).toMatchObject({ chordmarkup: null, has_chordmarkup: false });
    const context = await browser.newContext({ httpCredentials: credentials });
    try {
        const page = await context.newPage();
        await page.goto('/vip/?q=Rehearsal');
        await expect(page).toHaveURL(/\/\?q=Rehearsal$/);
        await expect(resultIds(page)).toHaveText(['X001', 'Y001']);
        await page.getByRole('button', { name: 'View chords' }).click();
        await expect(page.locator('.chord-sheet h2')).toHaveText('Hidden Rehearsal');
        await page.locator('#popup .popup-close').click();
        await page.getByRole('button', { name: 'Clear search' }).click();
        await expect(resultIds(page)).toHaveText([...publicSongIds, 'X001', 'Y001']);
        expect((await search(request, '*')).map(song => song.id)).toEqual(publicSongIds);
    } finally {
        await context.close();
    }
});

test('Word source and extraction are protected from public requests', async ({ request }) => {
    expect((await request.get('/Chants.docx')).status()).toBe(403);
    expect((await request.post('/admin/extract_chord_markup.php', { form: { id: 'Y911' } })).status()).toBe(401);
});

test.describe('admin', () => {
    test.use({ httpCredentials: credentials });

    test('the song editor previews, extracts, validates and saves chord markup', async ({ page, request }) => {
        const id = 'Y911';
        const endpoint = '/admin/extract_chord_markup.php';
        const extract = songId => request.post(endpoint, { form: { id: songId } });
        const getChords = async () => (await search(request, id))[0].chordmarkup;
        expect((await request.get(endpoint)).status()).toBe(405);
        expect((await extract('../0012')).status()).toBe(400);
        expect((await extract('Y999')).status()).toBe(404);
        const mapped = await extract('0159a');
        expect(mapped.status()).toBe(200);
        expect((await mapped.json()).sheet.title).toBe('First Arrangement');

        const created = await request.post('/admin/addsong_external.php', {
            form: { id, title: 'Editing fixture', lyrics: 'A fictional lyric' },
        });
        expect(created.status()).toBe(201);
        try {
            await page.setViewportSize({ width: 1200, height: 850 });
            await page.goto('/admin/editsong.php?id=' + id);
            const input = page.getByLabel('Chord markup (JSON)');
            const preview = page.getByRole('region', { name: 'Landscape chord preview' });
            const save = page.getByRole('button', { name: 'Save', exact: true });
            const inputBox = await input.boundingBox();
            const previewBox = await preview.boundingBox();
            expect(previewBox.x).toBeGreaterThan(inputBox.x + inputBox.width);
            expect(previewBox.width / previewBox.height).toBeCloseTo(16 / 9, 1);

            await input.fill(JSON.stringify({
                version: 1, enabled: false, title: 'Edited preview', details: [],
                lines: [{ kind: 'chords', runs: [{ text: 'D' }] }],
            }));
            await expect(preview.locator('h2')).toHaveText('Edited preview');
            await expect(page.getByText('Chord text is disabled for viewers.')).toBeVisible();
            page.once('dialog', dialog => dialog.accept());
            await page.getByRole('button', { name: 'Extract from Chants.docx' }).click();
            await expect(preview.locator('h2')).toHaveText('Word Practice Song');
            await expect(preview.locator('.chord-sheet-lyrics')).toHaveText('A fictional verse');
            expect(JSON.parse(await input.inputValue()).enabled).toBe(false);
            expect(await getChords()).toBeNull();

            const draft = JSON.parse(await input.inputValue());
            draft.enabled = true;
            await input.fill(JSON.stringify(draft));
            await save.click();
            await expect(page.getByText('Song updated successfully.')).toBeVisible();
            expect(JSON.parse(await getChords())).toMatchObject({ title: 'Word Practice Song', enabled: true });

            await input.fill('{"version":1}');
            await expect(preview).toContainText('Preview unavailable.');
            await save.click();
            await expect(page.locator('.alert-error')).toContainText('Enter valid version 1 chord markup');
            await expect(input).toHaveValue('{"version":1}');
            expect(JSON.parse(await getChords()).title).toBe('Word Practice Song');

            await input.fill('');
            await save.click();
            await expect(page.getByText('Song updated successfully.')).toBeVisible();
            expect(await getChords()).toBeNull();
        } finally {
            await request.post('/admin/deletesong.php', { form: { id, confirm_delete: 'on' } });
        }
    });

    test('the regular admin chord button displays imported text and formatting', async ({ page }) => {
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();
        await expect(page.getByRole('button', { name: 'Preview chord text' })).toHaveCount(0);
        await expect(page.locator('.chord-sheet h2')).toHaveText('Morning River');
        const heading = page.locator('.chord-sheet header');
        const credits = heading.locator('.chord-sheet-credits');
        const capo = heading.locator('.chord-sheet-capo');
        await expect(credits).toHaveText('(Ada Example)');
        await expect(credits).toHaveCSS('text-align', 'center');
        await expect(capo).toHaveText('¾Capo 2');
        const headingBounds = await heading.boundingBox();
        const titleBounds = await heading.locator('h2').boundingBox();
        const creditsBounds = await credits.boundingBox();
        const capoBounds = await capo.boundingBox();
        expect(Math.abs(capoBounds.x + capoBounds.width - headingBounds.x - headingBounds.width)).toBeLessThan(1);
        expect(creditsBounds.x).toBeGreaterThan(titleBounds.x + titleBounds.width);
        expect(creditsBounds.x + creditsBounds.width).toBeLessThan(capoBounds.x);
        expect(creditsBounds.x + creditsBounds.width / 2)
            .toBeCloseTo((titleBounds.x + titleBounds.width + capoBounds.x) / 2, 0);
        const chords = page.locator('.chord-sheet-chords .chord-sheet-main');
        expect(await chords.textContent()).toBe('    Am  G');
        await expect(chords.locator('span').first()).toHaveCSS('color', 'rgb(0, 100, 158)');
        const alternate = chords.locator('.chord-sheet-tone-alternate');
        await expect(alternate).toHaveText('G');
        await expect(alternate).toHaveCSS('color', 'rgb(98, 172, 230)');
        const aside = page.locator('.chord-sheet-chords .chord-sheet-aside');
        await expect(aside).toHaveText('123');
        await expect(aside).toHaveCSS('color', 'rgb(191, 143, 0)');
        const repeat = page.locator('.chord-sheet-lyrics .chord-sheet-aside');
        await expect(repeat).toHaveText('2x');
        await expect(repeat).toHaveCSS('color', 'rgb(95, 85, 55)');
        const fontSize = locator => locator.evaluate(element => parseFloat(getComputedStyle(element).fontSize));
        expect(await fontSize(aside)).toBeCloseTo((await fontSize(chords)) * 0.78, 1);
        await expect(page.locator('#popup-image')).toHaveCount(0);
    });

    test('wide chord text fills a landscape layout and refits as the viewport resizes', async ({ page }) => {
        await page.addInitScript(() => {
            Element.prototype.requestFullscreen = () => Promise.resolve();
        });
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            sheet.lines.push(...sheet.lines);
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const viewport = page.locator('.chord-sheet-viewport');
        const sheet = viewport.locator('article');
        const fontSize = () => sheet.evaluate(element => parseFloat(getComputedStyle(element).fontSize));
        const sheetWidth = () => sheet.evaluate(element => element.offsetWidth);
        await expect(viewport).toHaveClass(/chord-sheet-sideways/);
        await expect(viewport).toHaveCSS('padding-left', '4px');
        await expect(viewport).toHaveCSS('padding-bottom', '4px');
        await expect(viewport).toHaveCSS('padding-top', '44px');
        await expect.poll(sheetWidth).toBeGreaterThan(830);
        const initialFontSize = await fontSize();
        expect(initialFontSize).toBeGreaterThan(16);
        const phoneBounds = await sheet.boundingBox();
        expect(phoneBounds.x).toBeGreaterThan(0);
        expect(phoneBounds.x + phoneBounds.width).toBeLessThan(390);

        await page.setViewportSize({ width: 844, height: 390 });
        await expect(viewport).not.toHaveClass(/chord-sheet-sideways/);
        await expect(viewport).toHaveCSS('padding-left', '4px');
        await expect(viewport).toHaveCSS('padding-bottom', '4px');
        await expect.poll(sheetWidth).toBeGreaterThan(830);

        await page.setViewportSize({ width: 1200, height: 780 });
        await expect(viewport).not.toHaveClass(/chord-sheet-sideways/);
        await expect(viewport).toHaveCSS('padding-left', '23.4px');
        await expect(viewport).toHaveCSS('padding-bottom', '16px');
        await expect.poll(fontSize).toBeGreaterThan(initialFontSize);
        const bounds = await sheet.boundingBox();
        expect(bounds.width).toBeGreaterThan(1100);
        expect(bounds.y).toBeGreaterThan(150);
        expect(bounds.y + bounds.height).toBeLessThan(780);
        const nextChords = sheet.locator('.chord-sheet-lyrics + .chord-sheet-chords');
        const gap = () => nextChords.evaluate(element => parseFloat(getComputedStyle(element).marginTop));
        expect(await gap()).toBeGreaterThan(0);

        await page.setViewportSize({ width: 1200, height: 140 });
        await expect.poll(gap).toBe(0);
    });

    test('a sideways chord sheet can pinch to zoom and drag in both directions', async ({ page }) => {
        await page.addInitScript(() => {
            Element.prototype.requestFullscreen = () => Promise.resolve();
        });
        await page.setViewportSize({ width: 390, height: 844 });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const viewport = page.locator('.chord-sheet-viewport');
        const fontSize = () => viewport.locator('article').evaluate(element => parseFloat(getComputedStyle(element).fontSize));
        const scroll = () => viewport.evaluate(element => [element.scrollLeft, element.scrollTop]);
        await expect(viewport).toHaveClass(/chord-sheet-sideways/);
        const initialSize = await fontSize();

        const session = await page.context().newCDPSession(page);
        await session.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 2 });
        const touch = (type, xs) => session.send('Input.dispatchTouchEvent', {
            type, touchPoints: xs.map((x, id) => ({ x, y: 420, id })),
        });
        await touch('touchStart', [160, 230]);
        await touch('touchMove', [50, 340]);
        await touch('touchEnd', []);
        await expect.poll(fontSize).toBeGreaterThan(initialSize * 3);
        await session.send('Emulation.setTouchEmulationEnabled', { enabled: false });

        const [beforeLeft, beforeTop] = await scroll();
        await page.mouse.move(195, 420);
        await page.mouse.down();
        await page.mouse.move(245, 355, { steps: 5 });
        await page.mouse.up();
        await expect.poll(scroll).toEqual([beforeLeft + 65, beforeTop + 50]);

        await session.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 2 });
        await touch('touchStart', [245]);
        await touch('touchMove', [195]);
        await touch('touchMove', [145]);
        await expect.poll(async () => (await scroll())[1]).toBeLessThan(beforeTop + 50);
        await touch('touchEnd', []);
        await session.send('Emulation.setTouchEmulationEnabled', { enabled: false });

        await page.keyboard.press('0');
        await expect.poll(fontSize).toBeCloseTo(initialSize);
        await session.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 2 });
        await touch('touchStart', [50, 340]);
        await touch('touchMove', [150, 240]);
        await touch('touchEnd', []);
        await expect.poll(fontSize).toBeCloseTo(initialSize * 0.5);
        await session.send('Emulation.setTouchEmulationEnabled', { enabled: false });
        await page.keyboard.press('0');
        await expect.poll(fontSize).toBeCloseTo(initialSize);
        await page.keyboard.down('Control');
        await page.mouse.wheel(0, -200);
        await page.keyboard.up('Control');
        await expect.poll(fontSize).toBeGreaterThan(initialSize * 1.3);
    });

    test('the heading shrinks onto one line and renders Word comments in regular gray text', async ({ page }) => {
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            const title = 'Morning River';
            const titleComment = ' [A long explanation of the song and its original title, which would otherwise wrap]';
            const creditComment = ' [Translation]';
            sheet.title = title + titleComment;
            sheet.details = ['(Ada Example)' + creditComment, 'Capo 2'];
            sheet.headingRuns = [
                [{ text: title }, { text: titleComment, tone: 'comment' }],
                [{ text: '(Ada Example)' }, { text: creditComment, tone: 'comment' }],
                [{ text: 'Capo 2' }],
            ];
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const heading = page.locator('.chord-sheet header');
        const titleComment = heading.locator('h2 .chord-sheet-tone-comment');
        const creditComment = heading.locator('.chord-sheet-credits .chord-sheet-tone-comment');
        await expect(titleComment).toHaveCSS('color', 'rgb(117, 117, 117)');
        await expect(titleComment).toHaveCSS('font-weight', '400');
        await expect(heading.locator('h2')).toHaveCSS('font-weight', '700');
        await expect(creditComment).toHaveCSS('color', 'rgb(117, 117, 117)');
        await expect(heading.locator('.chord-sheet-capo')).toHaveText('Capo 2');
        const sizes = await heading.evaluate(element => ({
            font: parseFloat(getComputedStyle(element).fontSize),
            bodyFont: parseFloat(getComputedStyle(element.parentElement).fontSize),
            width: element.clientWidth,
            scrollWidth: element.scrollWidth,
            commentLines: element.querySelector('h2 span').getClientRects().length,
        }));
        expect(sizes.font).toBeLessThan(sizes.bodyFont);
        expect(sizes.scrollWidth).toBeLessThanOrEqual(sizes.width + 1);
        expect(sizes.commentLines).toBe(1);
    });

    test('additional text fits one line and adds space only at paragraph boundaries', async ({ page }) => {
        await page.setViewportSize({ width: 740, height: 420 });
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            const longLine = 'This additional text has to stay on one line even with a very long sentence and a pitch hint.';
            sheet.lines.push(
                { kind: 'note', runs: [{ text: longLine }], aside: '123456', asideTone: 'pitch', paragraphStart: true },
                { kind: 'note', runs: [{ text: 'After Shift+Return' }], paragraphStart: false },
                { kind: 'note', runs: [{ text: 'After Return' }], paragraphStart: true },
                { kind: 'lyrics', runs: [{ text: 'Next verse' }] },
            );
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const notes = page.locator('.chord-sheet-note');
        await expect(notes).toHaveCount(3);
        const layout = await notes.evaluateAll(rows => rows.map(row => {
            const text = row.querySelector('.chord-sheet-main');
            const rowStyle = getComputedStyle(row);
            return {
                fontSize: parseFloat(getComputedStyle(text).fontSize),
                overflow: text.scrollWidth - text.clientWidth,
                lines: text.firstElementChild.getClientRects().length,
                before: parseFloat(rowStyle.paddingTop),
                after: parseFloat(rowStyle.paddingBottom),
            };
        }));
        expect(layout[0].fontSize).toBeLessThan(layout[1].fontSize);
        expect(layout[0].overflow).toBeLessThanOrEqual(0);
        expect(layout[0].lines).toBe(1);
        expect(layout[0].before).toBeGreaterThan(0);
        expect(layout[0].after).toBe(0);
        expect(layout[1].before).toBe(0);
        expect(layout[1].after).toBe(0);
        expect(layout[2].before).toBeGreaterThan(0);
        expect(layout[2].after).toBeGreaterThan(0);
        const noteAside = notes.first().locator('.chord-sheet-aside');
        await expect(noteAside).toHaveText('123456');
        await expect(noteAside).toHaveCSS('color', 'rgb(191, 143, 0)');
        const sizeRatio = await noteAside.evaluate(element =>
            parseFloat(getComputedStyle(element).fontSize) / parseFloat(getComputedStyle(element.closest('.chord-sheet')).fontSize));
        expect(sizeRatio).toBeCloseTo(0.78, 2);
    });

    test('the admin chord viewer identifies an older import and shows the gap after a refresh', async ({ page }) => {
        let hasParagraphs = false;
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            sheet.lines.push(...['First line', 'Continuation', 'New paragraph'].map((text, index) => ({
                kind: 'note', runs: [{ text }],
                ...(hasParagraphs ? { paragraphStart: index !== 1 } : {}),
            })));
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.goto('/admin/?q=0012a');

        const chords = page.getByRole('button', { name: 'View chords' });
        const notice = page.locator('.chord-sheet-import-notice');
        const notes = page.locator('.chord-sheet-note .chord-sheet-main');
        const distances = () => notes.evaluateAll(elements => {
            const [first, second, third] = elements.map(element => element.getBoundingClientRect().top);
            return [second - first, third - second];
        });

        await chords.click();
        await expect(notice).toContainText('no paragraph markers');
        await expect(notes).toHaveCount(3);
        const [oldFirst, oldSecond] = await distances();
        expect(oldFirst).toBeCloseTo(oldSecond, 0);
        await page.locator('#popup .popup-close').click();

        hasParagraphs = true;
        await chords.click();
        await expect(notes).toHaveCount(3);
        await expect(notice).toHaveCount(0);
        const [freshFirst, freshSecond] = await distances();
        expect(freshSecond).toBeGreaterThan(freshFirst + 5);
    });

    test('a wide song presents all three additional verses on a compact display', async ({ page }) => {
        await page.addInitScript(() => {
            Element.prototype.requestFullscreen = () => Promise.resolve();
        });
        await page.setViewportSize({ width: 390, height: 844 });
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            const chord = { kind: 'chords', runs: [{ text: 'C        Dm      F              G7       Am         Dm       G7         C' }] };
            const lyric = { kind: 'lyrics', runs: [{ text: 'The quiet river moves through fields and carries the morning into the day.' }] };
            const verses = Array.from({ length: 9 }, (_, index) => ({
                kind: 'note',
                runs: [{ text: 'The next verse follows the river under the trees and into the open valley.' }],
                paragraphStart: index % 3 === 0,
            }));
            sheet.lines = [...Array.from({ length: 5 }, () => [chord, lyric]).flat(), ...verses];
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const viewer = page.locator('.chord-sheet-viewport');
        await expect(viewer).toHaveClass(/chord-sheet-sideways/);
        await expect(viewer.locator('.chord-sheet-note')).toHaveCount(9);
        const metrics = await viewer.evaluate(element => {
            const lyrics = element.querySelector('.chord-sheet-lyrics .chord-sheet-main');
            const notes = [...element.querySelectorAll('.chord-sheet-note')];
            return {
                extraHeight: element.scrollHeight - element.clientHeight,
                paragraphStarts: notes.flatMap((row, index) =>
                    parseFloat(getComputedStyle(row).paddingTop) > 0 ? [index] : []),
                lyricFont: parseFloat(getComputedStyle(lyrics).fontSize),
                noteFont: parseFloat(getComputedStyle(notes[0].firstElementChild).fontSize),
            };
        });
        expect(metrics.extraHeight).toBe(0);
        expect(metrics.paragraphStarts).toEqual([0, 3, 6]);
        expect(metrics.noteFont).toBeCloseTo(metrics.lyricFont * 0.85, 1);
    });

    test('a long chord sheet stays upright and can scroll at a readable size', async ({ page }) => {
        await page.addInitScript(() => {
            Element.prototype.requestFullscreen = () => Promise.resolve();
        });
        await page.setViewportSize({ width: 390, height: 844 });
        await page.route('**/admin/search.php?q=0012a', async route => {
            if (route.request().method() !== 'GET') return route.continue();
            const response = await route.fetch();
            const songs = await response.json();
            const sheet = JSON.parse(songs[0].chordmarkup);
            const [chords, lyrics] = sheet.lines;
            sheet.lines = [...Array.from({ length: 80 }, () => [chords, lyrics]).flat(), chords, chords];
            songs[0].chordmarkup = JSON.stringify(sheet);
            await route.fulfill({ response, json: songs });
        });
        await page.goto('/admin/?q=0012a');
        await page.getByRole('button', { name: 'View chords' }).click();

        const viewport = page.locator('.chord-sheet-viewport');
        await expect(viewport).toBeVisible();
        await expect(viewport).not.toHaveClass(/chord-sheet-sideways/);
        await expect(viewport.locator('article')).toHaveCSS('font-size', '12.8px');
        const spacing = await viewport.locator('article').evaluate(article => {
            const rows = [...article.querySelectorAll('.chord-sheet-row')];
            const gap = (from, to) => to.getBoundingClientRect().top - from.getBoundingClientRect().bottom;
            return {
                pairedGap: gap(rows[0], rows[1]),
                betweenPairs: gap(rows[1], rows[2]),
                unpairedGap: gap(rows.at(-2), rows.at(-1)),
                fontSize: parseFloat(getComputedStyle(article).fontSize)
            };
        });
        expect(spacing.pairedGap / spacing.fontSize).toBeCloseTo(-0.2, 2);
        expect(spacing.betweenPairs).toBe(0);
        expect(spacing.unpairedGap).toBe(0);
        const scroll = await viewport.evaluate(element => {
            element.scrollTop = element.scrollHeight;
            return element.scrollTop;
        });
        expect(scroll).toBeGreaterThan(0);
    });

    test('sync endpoints include media metadata and filter private songs and their links', async ({ request }) => {
        const standardResponse = await request.get('/admin/download_data.php');
        expect(standardResponse.status()).toBe(200);
        const standard = await standardResponse.json();
        expect(standard.songs.map(song => song.id).sort()).toEqual([...publicSongIds, 'Y001'].sort());
        expect(standard.songs.find(song => song.id === '0012a')).toMatchObject({
            lyrics_short: 'Carry me home',
            lyrics_paged: 'Morning river|Carry me home',
            chordmarkup: expect.any(String),
            tabfilename: 'river.jpg',
            mp3filename2: 'river-alternate.mp3',
        });
        expect(JSON.parse(standard.songs.find(song => song.id === '0011').chordmarkup).enabled).toBe(false);
        expect(standard.chord_markup_revision).toMatch(/^[0-9a-f]{64}$/);
        expect(standard.meanings.map(meaning => ({ ...meaning, id: Number(meaning.id) })))
            .toContainEqual({ id: 17, title: 'Context', meaning: 'A fictional translation' });
        expect(standard.song_meanings.map(link => ({ ...link, meaning_id: Number(link.meaning_id) })))
            .toContainEqual({ song_id: '0012a', meaning_id: 17 });
        expect(standard.song_meanings.map(link => link.song_id)).not.toContain('X001');

        const privateResponse = await request.get('/admin/download_data.php?user=private');
        expect(privateResponse.status()).toBe(200);
        const privateArchive = await privateResponse.json();
        expect(privateArchive.songs.find(song => song.id === 'X001').chordmarkup).toContain('Hidden Rehearsal');
        expect(privateArchive.chord_markup_revision).not.toBe(standard.chord_markup_revision);
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
        expect(await metadata.json()).toEqual({
            tab_count: 3, chords_zip_size: archive.length, chord_markup_revision: standard.chord_markup_revision,
        });
        const privateMetadata = await request.get('/admin/check_update.php?user=private');
        expect((await privateMetadata.json()).chord_markup_revision).toBe(privateArchive.chord_markup_revision);
        const legacyDownload = await request.get('/download_data.php?user=private');
        const legacy = await legacyDownload.json();
        expect(legacy.songs.find(song => song.id === 'X001').chordmarkup).toBeNull();
        const legacyMetadata = await request.get('/check_update.php');
        expect((await legacyMetadata.json()).chord_markup_revision).toBe(legacy.chord_markup_revision);
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
