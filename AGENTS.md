# Working on Circle Song Archive

- Start each new task on a descriptive branch with a flat name, then commit and push the branch. Continue follow-up requests on the same branch. The maintainer tests the branch and handles updates to `master`. Switch to the latest `master` only when the maintainer asks.
- Start with [README.md](README.md) and [docs/behavior.md](docs/behavior.md). The project contains an Android app, an optional companion APK, and a PHP website. Changes to song IDs, fields, or sync responses can affect both the website and installed Android clients.
- Follow [docs/testing.md](docs/testing.md). For Android changes run the Gradle JVM suite and build the affected APKs. For web changes run Playwright against the disposable fixture stack. For search, database, and favorites changes on Android, also run the Room tests when a device or emulator is available.
- Add or extend a regression test for user-visible behavior you change. Use synthetic fixtures and local test credentials. Keep production song data, account credentials, and service keys out of tests and commits.
- Preserve existing song IDs and persisted data formats. Review Room migrations and Android/web compatibility when changing persisted fields or response formats. Update the behavior documentation when the user-facing workflow or data contract changes.
- Report which checks ran and which require a device, a configured service, or a manual check.
