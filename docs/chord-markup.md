# Chord sheets from Word

SongArchive stores versioned JSON in the existing `songs.chordmarkup` column.
The regular chord button on the public and admin pages and in the Android app
opens valid chord text by default. It uses the existing chord image when the
text is missing, invalid, or disabled. A locally attached image takes priority
on Android. The app detects chord text changes even when the image ZIP is current.

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
authenticated admin and VIP views. Omit
`--images` as well to export directly from Word alone.

## Disable chord text for a song

Set `"enabled": false` at the top level of the song's `chordmarkup` JSON to
switch to the image. To restore the text, remove `enabled` or set it to `true`.
For example, run the first statement to disable text for `0478`, or the second
statement to restore it.

```sql
UPDATE songs SET chordmarkup = JSON_SET(chordmarkup, '$.enabled', JSON_EXTRACT('{"enabled":false}', '$.enabled'))
WHERE id = '0478' AND JSON_VALID(chordmarkup);

UPDATE songs SET chordmarkup = JSON_REMOVE(chordmarkup, '$.enabled')
WHERE id = '0478' AND JSON_VALID(chordmarkup);
```

Generated SQL imports preserve an existing `"enabled": false`. On the website,
refresh the song results after editing the flag. Android picks it up on the
next archive sync. Songs with disabled text need an image for the chord button
to be available.

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
Lines separated by Shift+Return stay together. The admin chord viewer shows a notice
when an older import lacks the paragraph markers for consecutive lines. To
refresh just songs `0478` and `0611`, generate and apply this SQL file.

```sh
python3 tools/import_chants.py Chants.docx --song 0478 --song 0611 --format sql > chord-spacing.sql
mysql song_database < chord-spacing.sql
```

Use the image-checked command above to refresh the full catalog.

The song body preserves line breaks and underlining. Alternate chords use sky blue.
Right-hand annotations use smaller text, with golden yellow for pitch hints.
The viewer opens the sheet fitted to the available area. Pinch to enlarge it or
shrink it to half the fitted size, and drag to move around the sheet.
On the website, you can also drag with the mouse and use Ctrl plus the mouse
wheel to zoom. With the sheet focused, `+` and `-` change the zoom and `0`
returns to the fitted size.

Phone-sized views use smaller side and bottom margins to give the sheet more
room. Wide sheets use landscape. Browsers and Android windows that stay in portrait
rotate the sheet within the viewer. Android accounts for display cutouts when
fitting the sheet and placing the viewer controls. When a wide sheet has spare
height, the viewer uses some of it between a lyric line and the next chord line.
Long or dense sheets can scroll at the fitted size, with the chord and lyric
rows moving together.

When several Word headings share a number, `tools/chant-variants.json` maps them
to database IDs in **document order**. This order differs from the filename order
for `0159` and `0187`. The second `0484` arrangement has no assigned ID and
`06xx` is a placeholder, so the importer reports and skips them. Update the map
when arrangements are added or reordered. A mismatch in the number of mapped
arrangements stops the export.

## Release the public website and Android app

Deploy the updated `web/` files together so the public viewer and editor can
load the shared renderer. Keep Apache Basic Auth enabled for `/admin/` and
`/vip/`.
The website uses the existing `chordmarkup` data as soon as the files are
deployed. Publish the updated Android build so users can fetch the sheets
through the regular archive update.

## Edit a song’s chord markup on the website

Open **Edit Song** in the admin page. Change the JSON in **Chord markup (JSON)**
and check the live landscape preview. On a narrow screen, the preview appears
below the editor. Use **Save** to apply the draft or clear the field and save to
remove the sheet.

**Extract from Chants.docx** loads the current song ID into the editor using the
same variant map as the command-line importer. It preserves `"enabled": false`
from the current draft. Review the preview and save when ready.

Deploy the updated `web/` files, including `web/.htaccess`, and place the two
importer files, `import_chants.py` and `chant-variants.json`, in a `tools/`
directory alongside the document root. The web server needs Python 3 on its
PATH, PHP process execution through `proc_open`, and read access to those files.
Keep Apache Basic Auth on `/admin/`. Before placing `Chants.docx` in the
document root, verify that Apache denies direct downloads of it. The supplied
`.htaccess` rule does this. Configure the same rule in the virtual host if
Apache ignores `.htaccess`.
