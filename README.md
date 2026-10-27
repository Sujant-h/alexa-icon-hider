# Alexa Icon Hider

Blendet auf dem Amazon Alexa Tablet 12 Pro das Alexa-Symbol (`AlexaIngressButton`) in der Taskleiste des Fire Launchers dauerhaft aus. Root ist nicht nötig, und MacroDroid wird damit überflüssig. Die APK ist rund 15 KB groß und kommt ohne AndroidX und ohne Bibliotheken aus. Sie läuft ohne Dienst, ohne Polling und ohne Wakelocks.

Getestet am 27.10.2026 auf: Alexa Tablet 12 Pro (ATSPWI), Alexa Tablet UI 16.1.10 (Android 16, API 36), Build SL16SHIP2003.1344N, Fire Launcher (Home UI) 1.0.21178.0, Headless System User Mode mit Nutzer 10.

## Wie es funktioniert

Der Fire Launcher (`TabletAlexaIngressVisibilityController`) zeigt das Symbol nur, solange `alexa_window_is_open` **und** `alexa_app_in_foreground` beide 0 sind. Er beobachtet beide Werte per ContentObserver und reagiert ohne Neustart. Gemessen: 44 ms vom Schreiben bis zum Ausblenden. Die App setzt nur `alexa_app_in_foreground = 1`.

SystemUI (`com.amazon.wm.shell.alexa`) setzt den Wert bei jedem Boot **zweimal** auf 0 zurück:

1. `AlexaFloatingWindowModel.onInit` beim Start von SystemUI, etwa 10 s nach dem Boot, noch vor `LOCKED_BOOT_COMPLETED`.
2. `AlexaForegroundTracker.notifyIfChanged` beim **ersten Entsperren**, ausgelöst durch die Keyguard-going-away-Transition. Der Tracker meldet dabei einmal „Alexa nicht im Vordergrund“. Danach schreibt er laut Code nur noch, wenn sich der Zustand ändert.

Wegen des zweiten Resets hat ein einmaliges Setzen direkt nach dem Boot nicht gereicht, und MacroDroid brauchte die Wartezeit von 60 s. Die App setzt den Wert deshalb an zwei Stellen:

- **`BootReceiver`** (`directBootAware`): bei `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`. Er schreibt nur, wenn der Wert abweicht.
- **`ResetJob`**: ein JobScheduler-Job, der nur auslöst, wenn sich `content://settings/secure/alexa_app_in_foreground` ändert. Er setzt den Wert wieder auf 1 und plant sich neu. Beobachtet wird die Setting vom System, die App läuft also nur bei einer Änderung. Solche Jobs dürfen keinen Neustart überleben, deshalb plant der `BootReceiver` ihn nach jedem Boot neu.

Den Launcher stoppt die App nicht, das ist auch nicht nötig.

## Installation

Voraussetzung: `adb` mit USB- oder WLAN-Debugging, das Tablet ist mit Nutzer 10 angemeldet.

```bash
adb install --user 10 alexa-icon-hider/app/build/outputs/apk/release/app-release.apk
```

```bash
adb shell pm grant --user 10 com.minnallab.alexaiconhider android.permission.WRITE_SECURE_SETTINGS
```

```bash
adb shell pm grant --user 10 com.minnallab.alexaiconhider android.permission.POST_NOTIFICATIONS
```

Danach **„Alexa Icon Hider“ einmal öffnen**, auf dem Tablet oder per `adb shell am start --user 10 -n com.minnallab.alexaiconhider/.MainActivity`. Eine frisch installierte App ist im Zustand „stopped“ (`stopped=true notLaunched=true`) und erhält bis zum ersten Start keine Boot-Broadcasts. Die App zeigt den Status und die letzten 20 Läufe. Ein MacroDroid-Makro für das Alexa-Symbol kannst du danach löschen.

**Berechtigungen:**

- `WRITE_SECURE_SETTINGS` (per ADB): zum Schreiben der Setting.
- `RECEIVE_BOOT_COMPLETED`: wird bei der Installation automatisch erteilt, Android verlangt sie für Boot-Broadcasts.
- `POST_NOTIFICATIONS`: nur für den Hinweis, falls `WRITE_SECURE_SETTINGS` fehlt. Fehlt sie, erscheint der Hinweis nur in der App und im Log.

## Rückgängig machen

Erst deinstallieren, dann die Setting löschen. In umgekehrter Reihenfolge würde der Job den gelöschten Wert sofort wieder setzen.

```bash
adb shell "pm uninstall --user 10 com.minnallab.alexaiconhider && settings delete --user 10 secure alexa_app_in_foreground"
```

Das Symbol erscheint danach sofort wieder.

## Bauen

Java 17 Bytecode, AGP 9.4.1, Gradle 9.7.1 (Wrapper mit SHA-256), compileSdk/targetSdk/minSdk 36, R8 mit `isMinifyEnabled` und `isShrinkResources`. Das in AGP 9 eingebaute Kotlin ist abgeschaltet (`android.builtInKotlin=false`). Sonst kämen die Kotlin-Stdlib und 12 KB Builtins in die APK.

```bash
cd alexa-icon-hider && ./gradlew assembleRelease
```

- JDK 17 oder neuer, getestet mit der JBR 25 von Android Studio.
- `local.properties` mit `sdk.dir` (nicht im Repo).
- Signiert wird mit dem Keystore aus `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`, nicht im Repo). Updates müssen mit demselben Schlüssel signiert sein. Ohne diese Datei entsteht eine unsignierte Release-APK.

## Testergebnisse (27.10.2026)

### Neustarts

Zeiten in Sekunden seit dem Boot. Die Systemereignisse stammen aus `logcat -b all`, umgerechnet über `boot_progress_start`. Die Läufe der App stehen in ihrem eigenen Verlauf, denn Logcat verwirft auf diesem Gerät die App-Logs in der frühen Boot-Phase.

| Boot | 1. Reset (SystemUI-Init) | App schreibt (`LOCKED_BOOT_COMPLETED`) | Entsperrt (`USER_UNLOCKED`) | 2. Reset abgefangen | Symbol sichtbar entsperrt |
|---|---|---|---|---|---|
| 2 | +10,06 | +12,9 (0→1) | +17,54 | +18,3 (0→1) | ≤ 0,8 s |
| 3 | +10,06 | +13,0 (0→1) | +15,61 | +16,3 (0→1) | ≤ 0,7 s |
| 4 | +10,13 | +13,1 (0→1) | +18,23 | +18,9 (0→1) | ≤ 0,7 s |

- Zwischen dem 1. Reset und dem Schreiben der App (etwa 3 s) ist das Tablet noch gesperrt. Die Taskleiste ist dann nicht zu sehen.
- Die Spalte „sichtbar“ ist eine Obergrenze: Zeit vom Entsperren bis zum Schreiben. Der 2. Reset kommt erst am Ende der Entsperr-Animation, etwa 0,2 s nach `USER_UNLOCKED`, und der Job läuft frühestens 0,5 s danach.
- Auf dem Startbildschirm (Lawnchair) zeigt der Fire Launcher keine Taskleiste.
- Boot 1 lief noch mit Version 1.0 ohne Verlauf. Die App startete 0,44 s nach `LOCKED_BOOT_COMPLETED`, am Ende stand `pkg:com.minnallab.alexaiconhider value:1`.
- Nach allen vier Boots stand `alexa_app_in_foreground` mit `pkg:com.minnallab.alexaiconhider` auf `1`.

### Weitere Tests

| Test | Ergebnis |
|---|---|
| `am force-stop` Fire Launcher | Der Launcher startet neu, der Wert bleibt 1, das Symbol bleibt weg. Die App muss nicht eingreifen. |
| `settings put … 0` von Hand | nach 0,52 s wieder 1 (2 Läufe: 0,52 s, 0,53 s) |
| `settings delete …` von Hand | nach etwa 0,5 s wieder 1 |
| App-Update (`install -r`, versionCode 1→2→3→4) | `MY_PACKAGE_REPLACED` kommt an, Berechtigungen bleiben, der Job ist wieder geplant |
| RAM (`dumpsys meminfo`), ohne Oberfläche | 4,7 MB PSS direkt nach einem Lauf, 4,4 MB nach 30 s (davon 3,5 MB in zram), Prozess im Cache (`oom_score_adj` 915) |
| RAM, während die App geöffnet ist | 32 MB PSS, nur solange die Oberfläche angezeigt wird |
| Wakelocks (`dumpsys power`) | keine eigenen, `count=0`. Nur die kurzen Wakelocks, die das System während eines Jobs bzw. Starts hält (`*job*r/…`, `*launch*`, wenige ms) |
| `WRITE_SECURE_SETTINGS` fehlt | kein Absturz, genau eine Benachrichtigung mit dem `pm grant`-Befehl. Nach erneutem Erteilen und Öffnen ist sie weg und der Wert wieder 1 |
| APK-Größe | 15.404 Bytes (Release, R8, v2-signiert) |
| Standby-Bucket | 10 (ACTIVE) direkt nach den Tests |

Noch nicht über mehrere Tage beobachtet: ob der Content-Trigger-Job in einem niedrigeren Standby-Bucket verzögert wird, und ob spätere Entsperrungen wirklich nicht mehr zurücksetzen (laut Code nicht). Beides zeigt der Verlauf in der App.

Ein **Content-Trigger** wartet ohne `setTriggerContentUpdateDelay(0)` etwa 10 s auf Ruhe, bevor der Job startet (gemessen). Mit `0` greift die Untergrenze des Systems von etwa 0,5 s.
