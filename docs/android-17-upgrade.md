# Android 17 (API 37)

Both application modules compile against and target API 37. The existing AGP and
Gradle versions support this SDK; no build-tool migration is required.

## Network Connection

The default connection shortcut and mode picker use the same permission flow.
A common explanation precedes a single request for all applicable runtime permissions:
precise and approximate location together, Bluetooth on Android 12+, nearby Wi-Fi
on Android 13+, and local network access on Android 17+. Android controls the
number of system dialogs shown for the different permission groups.

Location access is retained for the existing Nearby discovery behavior. Normal
permissions (Wi-Fi state, Internet, and legacy Bluetooth) are install-time grants
and are excluded from the runtime request. Notification permission is optional
for a foreground service and does not block establishing a connection.

The chosen connection mode is applied only after permissions and device settings
have been checked. Denial offers app settings; disabled Wi-Fi, Bluetooth or
location services offer system settings. Returning from settings rechecks access
and resumes the pending action. Cancel leaves the current connection unchanged.
Disabling a connection stops the service without requesting permissions.

## Other compatibility checks

- Background audio: playback starts from user actions and promotes the existing
  `mediaPlayback` service to the foreground before playing. This follows the
  Android 17 requirement for a foreground service with while-in-use capabilities.
- Large screens: activities do not opt out of resizing or enforce orientation.
  Existing layouts respond to configuration and available screen width.
- Reflection, native code, widgets, contacts and SMS: application code does not
  use the APIs affected by the corresponding Android 17 changes.
- HTTPS: platform certificate transparency and supported Encrypted Client Hello
  defaults are retained. Server compatibility still needs a device smoke test.
- Camera: capture uses AndroidX `TakePicture`. The removal of implicit URI grants
  documented alongside Android 17 is scheduled for Android 18.

## Device verification

Automated verification passed for both debug builds, both debug lint tasks and
the five permission unit tests (`:app:testDebugUnitTest`). Lint still reports
existing non-fatal warnings. Device checks below have not been run in this environment.

- Fresh install: choose server and client modes, then grant every requested
  permission. Repeat with a configured default connection shortcut.
- Deny location, select approximate location only, deny nearby devices, and cancel
  permission dialogs. The requested connection must remain inactive; grant access
  through settings and verify it starts on return.
- Rotate while permission or settings dialogs are open; the pending mode should
  survive and resume after granting access.
- Turn off Wi-Fi, Bluetooth or location; Network Connection must remain visible
  and explain which settings are needed. Cancel and disable must not ask for access.
- Upgrade an existing install with nearby-device permissions already granted;
  check local access and transfer between two devices in both directions.
- Smoke-test synchronization, image capture/OCR, audio playback with the screen
  off, notification playback controls, and viewer rotation on phones and tablets.

Sources: [Nearby Connections permissions](https://developers.google.com/nearby/connections/android/get-started),
[targeted behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17),
[all-app behavior changes](https://developer.android.com/about/versions/17/behavior-changes-all),
[background audio](https://developer.android.com/about/versions/17/changes/bg-audio).
