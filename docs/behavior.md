# Behavior and data contracts

## Website

| Workflow | Entry points and behavior |
| --- | --- |
| Browse and search | Public `web/index.html` and admin `web/admin/index.html` use `web/js/songbook.js`. An empty input loads the default list. A shared URL can set `?q=…`. Public shuffle sends the special query `@@shuffle@@`. |
| Query handling | `web/search.php` accepts a query in the URL. Text search matches catalog fields and ranks title, lyrics, and keyword results. Exact ID lookup returns lyrics and grouped meanings. A list of IDs separated by spaces, pipes, commas, or semicolons returns the requested songs in first-occurrence order. |
| Visibility | Public browsing, text search, and shuffle filter out IDs starting with `X` or `Y`. The admin listing requests expanded results. The Basic Auth-protected `/vip/` route sets a PHP session flag and redirects to the public page with the supplied query. Exact ID lookup uses its own query path. |
| Song viewers and player | The lyrics popup fetches full song details and can show linked meanings. Chord images are served from `/img/songs/`. The admin **Text** button opens imported chord sheets for review alongside the image action. The text heading fits one line and preserves the gray comments marked in Word. Red notes use smaller type, each fit one line, and have extra space at Word paragraph boundaries. The admin preview identifies older imports that lack those boundaries. After the [chord text release](chord-markup.md), the public viewer prefers text and falls back to the image. The player streams from `/audio/songs/`, can cycle through two recordings, and keeps the active song visible when search results change. |
| Admin editing | `web/js/songadmin.js` opens the PHP add and edit forms in an iframe. Successful add, edit, and delete actions notify the parent page so it refreshes the listing. The editor can clone a song and manage its meaning links. `web/admin/addsong_external.php` accepts the Android upload form for `Y` IDs. |

The hosted site relies on Apache directory-level Basic Auth for `/admin/` and `/vip/`. It uses local password files and an untracked `web/db_config.php` that supplies a `mysqli` connection as `$conn`. PHP needs `mysqli` for the database and `ZipArchive` to generate the chord download. Media files and database contents live outside this checkout. The local test stack supplies its own Apache configuration and a minimal schema for the tested endpoints.

## Android

| Area | Entry points and behavior |
| --- | --- |
| Song list and search | `ui/MainActivity.kt` and `ui/SongTable.kt` render the archive. `db/SongViewModel.kt` normalizes queries and stored text. `db/SongDao.kt` owns filtering, ranking, and default order. Normalization covers diacritics, sharp S, apostrophe variants, and line breaks. |
| Local songs and viewers | The app can create or clone an editable song with a `Y` ID and attach a selected document as a chord sheet. `util/LocalTabUtils.kt` stores selected document URIs as `local_uri:<uri>`. The lyrics and chords viewers read from Room, stored images, or a selected local document. The chords viewer prefers released text over a downloaded image and gives a locally attached image priority. Text sheets fit the viewport and wide sheets request landscape, with in-view rotation when the window stays in portrait. |
| Favorites | `db/FavoriteListViewModel.kt` and `db/FavoriteListDao.kt` maintain lists and per-list entries. A custom title and a position belong to the entry, so the original song can appear under different titles in different lists. The Nearby import accepts entry metadata and an older comma-separated song ID payload. |
| Audio and Nearby | `audio/AudioPlayerService.kt` streams the selected recording. `network/NearbyConnectionHandler.kt` handles server and client modes, display commands, and favorite-list sharing. See [Android 17 upgrade notes](android-17-upgrade.md) for the permission flow. |
| Database upgrades | `db/AppDatabase.kt` registers migrations from version 11 through version 18. Version 17 adds nullable `chordmarkup`, and version 18 adds the synced chord text revision to app metadata. Preserve upgrade paths when the schema changes, especially for favorite entries and local songs. |

Android source paths in the table are relative to `app/src/main/java/de/jeisfeld/songarchive/`.

## Sync across the web and Android

- `/admin/download_data.php` returns `songs`, `meanings`, and `song_meanings`. IDs for songs are strings and can have leading zeros, a letter suffix, or a prefix. Meaning IDs may arrive as numeric strings. `app/…/sync/ApiService.kt` and `RemoteMetadata.kt` define the Android caller and JSON models.
- Standard sync excludes `X` songs and their meaning links. When the optional companion is verified, the Android request includes `user=private`. Many lyrics and media fields are nullable, including `lyrics_short`, `lyrics_paged`, `chordmarkup`, and the recording filenames.
- Android refreshes downloaded songs and meanings in Room, keeps local `Y` rows during the clear step, and restores saved favorite entries whose songs still exist. Changes here should be checked with a local song, a retained downloaded song, and a downloaded song removed from the server.
- `/admin/check_update.php` returns the tab count and chord ZIP size, either of which can be unavailable. The app downloads chord images through `/admin/download_chords.php` and streams recordings individually.
- Once chord text is released, the sync endpoints include a chord text revision. A revision change can prompt a catalog update even when the image ZIP is current. If the server has chord images and its ZIP is missing, the update check offers synchronization so the download endpoint can rebuild the archive. Android validates and installs the downloaded ZIP before publishing the refreshed catalog and chord icons. It saves the size of the installed download and updates the tab count when the server provides it.

## Nearby message format

Connected devices exchange UTF-8 JSON with a `command` string and a `params` object. Parameter values are strings and the sender omits null values. Song display commands can carry a `songId`, a `style`, and optional `lyrics` and `lyricsShort` strings. Chord display sends the song ID and style so the receiver can load its own chord sheet. A blank display uses `DISPLAY_TEXT` with style `REMOTE_BLACK`.

A favorite-list message includes `name`, comma-separated `songIds`, and a string `sorted` flag. When present, `entries` is a JSON array encoded as a string and can carry positions and custom titles. The recipient also accepts older messages that omit `entries` and `sorted`.
