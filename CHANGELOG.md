# Changelog

Notable changes per release. Each heading matches `versionName` in `app/build.gradle.kts`, and the section becomes the GitHub release notes.

## Unreleased

- The status screen notices by itself when an `adb` command from it has been run: the command disappears, the Alexa icon is hidden right away, and a confirmation says what, if anything, is left to do. No need to reopen the app.

## 1.5-beta.1 (versionCode 6), 2026-10-27

First public release.

> [!NOTE]
> Beta: tested in the Android emulator only, not yet verified on the Alexa Tablet 12 Pro.

Hides the Alexa button in the Fire Launcher taskbar on the Alexa Tablet 12 Pro (Android 16). No root required.

- Keeps the Alexa button hidden, including after the two resets SystemUI performs on every boot.
- Event-driven: runs only when the setting changes. No background service, polling or wakelocks; ~57 KB APK with no dependencies.
- On/off switch, plus loop protection that turns the app off if another process keeps resetting the value.
- Status screen with run history since boot and copyable `adb` commands for missing permissions.
- English UI with German translation, light and dark theme.

SHA-256 of `alexa-icon-hider-1.5-beta.1.apk`: `5bfe4717d1d011e1d2a8e5403bb79c83f2f5fa0b25e39936ed8f2ab598bdcc76`
