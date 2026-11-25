# Testing

Run commands from the repository root. The automated suites use synthetic data and require no production account or song catalog.

## Android

```sh
# Fast JVM tests and both debug APK builds
./gradlew :app:testDebugUnitTest :app:assembleDebug :songarchivexplugin:assembleDebug

# In-memory Room tests with an API 26+ Android device or emulator connected
./gradlew :app:connectedDebugAndroidTest
```

JVM tests cover the Nearby permission matrix and message formats, local chord URI storage, and decoding the archive sync payload. The Room tests exercise the real DAO queries for ranking and default song order, preserve local songs and their favorite entries during the catalog clear step, and verify favorite order and per-list titles. To compile the instrumentation APK without a device, run `./gradlew :app:assembleDebugAndroidTest`.

The JVM HTML report is at `app/build/reports/tests/testDebugUnitTest/index.html`. Gradle writes instrumentation results under `app/build/outputs/androidTest-results/connected/` and the report under `app/build/reports/androidTests/connected/`.

## Web

Use Docker with Compose v2, Node.js 20 or newer, and npm. Playwright runs real PHP requests and Chromium UI flows against a local Apache server. MariaDB is preloaded with the synthetic schema and seed data in `tests/web/fixture.sql`.

```sh
npm --prefix tests/web ci
npm --prefix tests/web exec -- playwright install chromium
docker compose -f tests/web/compose.yaml up --build --wait
npm --prefix tests/web test
docker compose -f tests/web/compose.yaml down
```

On Linux, Playwright's `install --with-deps chromium` can also install missing system libraries. Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to use an existing local Chromium. Set `SONGARCHIVE_TEST_PORT` in the shell to change the port for both Compose and Playwright.

The suite covers text and ID search, browse and shuffle, shared song links, lyrics and meanings, chord viewing, audio controls, the VIP session, sync JSON, serving a seeded chord ZIP, admin add/edit/delete, and the Android upload endpoint. The audio controls test uses a stub for media playback. To reset the fixture after an interrupted admin test, bring the test stack down and start it again. Playwright writes failure artifacts under `tests/web/test-results/`.

## Android workflow coverage

Use a test device and, for sync or upload, a configured test archive account.

| Workflow | Automated coverage | Device check |
| --- | --- | --- |
| Sync with the web database | PHP catalog and chord ZIP responses, Android JSON decoding, local-song retention, and a recreated ZIP with the new chord available as soon as sync finishes | Sync a test catalog with an existing local song and favorites. Verify retained entries and open downloaded songs and chords offline. On a test server, remove its cached chord ZIP and add a new chord image. Accept the startup prompt, confirm the icon appears immediately, and restart to verify the prompt has cleared. |
| Search for a song | Room query ranking and browse order | Search from the app, clear the query, and open the expected result. |
| Display lyrics | Nearby lyrics message format | Open a song with full and short lyrics and verify the text in portrait and landscape. |
| Display chords as an image | Local chord URI encoding and the web chord image viewer | Open a downloaded sheet and a locally attached image. The Android viewer currently requests orientation based on the image dimensions. |
| Open a meaning from the chords view | Web meaning display and Android sync decoding | Open a chord sheet for a song with a linked meaning and use the meaning control. |
| Switch lyric pages | Paged lyrics field in the sync payload | Open a song with multiple lyric pages and move forward and back. |
| Add a song manually | Room local-song retention and web editor creation | Create a local song in the app, reopen it, and verify its title, lyrics, and any selected image. |
| Upload a song to the database | PHP accepts a local song, handles a duplicate ID, and returns the new song in the sync download | Upload from the Android app and verify the new song in a test archive. |
| Change language in Settings | — | Switch between available languages and verify the preference after restarting the app. |
| Set a default network connection | Nearby permission rules | Select a default connection, restart the app, and verify the shortcut starts the chosen mode. |
| Create sorted and unsorted favorite lists | Room entry order and custom titles, plus both sort flag values in the sharing payload | Create both list types in the app, add songs, and verify ordering after reopening. |
| Send songs over Nearby | Outbound song, lyrics, chords, blank-display, disconnect, and favorite-list JSON, plus decoding of existing text and favorite-list messages | Connect two devices, send songs and lyric-only updates, verify the text and chord client modes, then reconnect and check that the current display is restored. |

Also check audio track switching and notification controls with the screen off, and capture an image with OCR configured when changing the camera flow. For Nearby permission and reconnection cases, see the [Android 17 upgrade notes](android-17-upgrade.md).
