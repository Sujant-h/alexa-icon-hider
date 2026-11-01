# Changelog

Notable changes per release. Each heading matches `versionName` in `app/build.gradle.kts`, and the section becomes the GitHub release notes.

## 1.5 (versionCode 7), 2026-11-02

- **Privacy: disable ads & tracking.** A new "Disable ads & tracking" switch writes `limit_ad_tracking` (Secure) and `IS_DTCP_ENABLED` (Global) and resets the advertising ID. It goes through Amazon's own ad id service (`com.amazon.adidmanager`), like the Settings app, so the ad backend is notified and the per-app ad ids are renewed too. Switching off restores the previous values, but leaves alone any key you changed in Settings meanwhile. If the service is missing, the app falls back to writing `advertising_id` itself.
- **The status screen notices `adb` commands by itself.** While a command from the screen is shown, the app checks once a second (never in the background). Once it works, the command disappears, the Alexa icon is hidden right away, and a confirmation says what, if anything, is left to do.
- **Clearer history.** The reason column now reads "Alexa icon hidden" instead of "Value changed".
- Unit tests for the loop guard, the history trimming and the history line format, uploaded as a test report when CI fails.
- Verified on the Alexa Tablet 12 Pro, not only in the emulator.

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
