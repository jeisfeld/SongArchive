# Chord sheets from Word

SongArchive stores versioned JSON in the existing `songs.chordmarkup` column.
The admin page can preview the text before release. After release, the public
website and Android prefer chord text and use the existing image as a fallback.
Android also supports user-attached chord photos. App synchronization detects
changes to chord text even when the image archive has the same size.

## Generate an import

From the repository root, use Python 3 with `Chants.docx` and `chants.zip`:

```sh
python3 tools/import_chants.py Chants.docx --images chants.zip --image-only > chords.json
python3 tools/import_chants.py Chants.docx --images chants.zip --image-only --format sql > chords.sql
```

The first command exports JSON keyed by song ID for review. The second generates
a MySQL or MariaDB transaction that updates `chordmarkup` on matching song rows.
Apply it to the configured database after reviewing the ID mapping. For example,
with a client configured to connect to the database:

```sh
mysql song_database < chords.sql
```

The archive is used to check image coverage. With `--image-only`, the export
contains songs that have both an image and a Word section. Images with no Word
section are reported on stderr and stay on the image view. Add `--strict-images`
to fail the export if every image must have a Word section. The private ZIP
contains 18 matching sheets and an image-only score for `X004` (La ténèbre).
Omit `--image-only` to include numbered sheets without an image,
including the document's `X` songs. The website serves `X` chord text through the
authenticated admin view and, after release, the authenticated VIP view. Omit
`--images` as well to export directly from Word alone.

## Preview on the admin page

1. Upload `web/search.php`, `web/chord_markup.php`, `web/js/songbook.js` and
   `web/css/songbook.css`. Upload `search.php`, `download_data.php` and
   `check_update.php` under `web/admin/` along with `web/download_data.php`
   and `web/check_update.php`. The download endpoints need to be in place
   before importing the preview data. Keep the existing Apache Basic Auth
   configuration for `/admin/`.
2. Leave `SONGARCHIVE_CHORD_MARKUP_RELEASED` unset or set it to `false` in
   `web/db_config.php`. In this mode the public website uses the image viewer
   and the Android sync endpoints omit the preview content.
3. Import `chords.sql`. Sign in to `/admin/` and search for a song. Open the
   usual chord icon to see the original image and the **Text** button to see
   the same song in the new renderer. The **Text** button appears for songs
   with imported chord text. A preview error appears directly in that view.
4. Review a few representative songs first. For example, `0001` has pitch
   hints and underlined syllables, and `0159` and `0159a` have different
   arrangements. Correct the Word document or ID map as needed, run the
   export again, and reimport before reviewing the affected songs.

## Update the source

The importer uses paragraph styles for song headings, chord rows, lyrics,
additional text and pitch hints. Leading and interior spaces in the chord and
lyric rows define their alignment. A tab opens the annotation column for pitch
hints or repeats. Titles and their tab-separated details carry credits, references,
and capo information. The viewer places the title on the left, centers credits
in the space between the title and capo, and aligns the capo with the right
edge. It scales the headline separately so it fits on one line. Re-export and
reimport to preserve gray comments in regular weight from the Word headings.
Red additional text uses a smaller font than the chords and lyrics, and each
red line shrinks further if needed to fit its row. Wide sheets with additional
verses can scale further to fit a short landscape display. The viewer adds
space around additional text and between Word paragraphs created with Return.
Lines separated by Shift+Return stay together. The admin preview shows a notice
when an older import lacks the paragraph markers for consecutive lines. To
refresh just songs `0478` and `0611`, generate and apply this SQL file.

```sh
python3 tools/import_chants.py Chants.docx --song 0478 --song 0611 --format sql > chord-spacing.sql
mysql song_database < chord-spacing.sql
```

Use the image-checked command above to refresh the full catalog.

The song body preserves line breaks and underlining. Alternate chords use sky blue.
Right-hand annotations use smaller text, with golden yellow for pitch hints.
The viewer centers and scales the sheet to fit the available area. Wide sheets
use landscape. Browsers and Android windows that stay in portrait
rotate the sheet within the viewer. Long or dense sheets remain readable by
scrolling when needed, with the chord and lyric rows moving together.

When several Word headings share a number, `tools/chant-variants.json` maps them
to database IDs in **document order**. This order differs from the filename order
for `0159` and `0187`. The second `0484` arrangement has no assigned ID and
`06xx` is a placeholder, so the importer reports and skips them. Update the map
when arrangements are added or reordered. A mismatch in the number of mapped
arrangements stops the export.

## Release the public website and Android app

After review, set the release flag in `web/db_config.php`:

```php
define('SONGARCHIVE_CHORD_MARKUP_RELEASED', true);
```

The public website then displays text where a sheet is available and keeps the
image fallback. Publish the updated Android build when ready. Once installed,
the app can fetch the sheets through its regular song update.
