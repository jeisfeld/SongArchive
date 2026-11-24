# Circle Song Archive / Heilsame Lieder

Circle Song Archive collects songs for singing circles. The website lets visitors search the archive, read lyrics and translations, view chord sheets, and listen to recordings. The Android app downloads the song catalog and chord images, supports local songs and favorite lists, and can display songs on nearby Android devices.

## Project map

| Location | Responsibility |
| --- | --- |
| [`app/`](app/) | Kotlin and Jetpack Compose Android app. Room stores songs, meanings, favorites, and settings. Retrofit and OkHttp handle archive sync, Media3 plays recordings, and Google Nearby Connections links devices. |
| [`songarchivexplugin/`](songarchivexplugin/) | Optional Android companion APK. The app uses its verification response to decide whether to request the private archive during sync. |
| [`web/`](web/) | Web document root. Plain HTML, CSS, and JavaScript for public and admin pages, with PHP and a MySQL-compatible database for search, editing, and sync. |
| [`tests/web/`](tests/web/) | Playwright browser and endpoint tests, with a local Apache/PHP/MariaDB stack and fictional songs. |

## Run locally

For Android, open the repository root in Android Studio or use the checked-in Gradle wrapper with JDK 17 or newer and Android SDK API 37. Both APKs target API 37 and support Android API 26 or newer.

```sh
./gradlew :app:assembleDebug :songarchivexplugin:assembleDebug
```

Install the main app with `./gradlew :app:installDebug` when a device or emulator is connected. Build and test commands work with empty service configuration. Archive sync uses `app/auth.properties` with `basicAuthUsername` and `basicAuthPassword`. Camera OCR uses `app/firebase.properties` with `firebaseCloudVisionApiKey`. Both files are local and ignored by Git. Android sync and streaming URLs in the app point to the hosted archive. The local web stack below is for web testing.

For the website, install Docker with Compose v2 and start the fixture stack from the repository root.

```sh
docker compose -f tests/web/compose.yaml up --build --wait
```

Open `http://127.0.0.1:8123/` for the public site. Open `/admin/` for the editor or `/vip/` for an expanded search session. Both protected paths use the local fixture credentials `fixture` / `fixture-password`. Stop the stack with `docker compose -f tests/web/compose.yaml down`. The fixture is reset when the stack is recreated, and rebuilding picks up changes under `web/`.

See [Testing](docs/testing.md) for automated checks, reports, and the device smoke tests. See [Behavior and data contracts](docs/behavior.md) for entry points and compatibility details.
