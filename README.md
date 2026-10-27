# Alexa Icon Hider

Blendet auf dem Amazon Alexa Tablet 12 Pro das Alexa-Symbol (`AlexaIngressButton`) in der Taskleiste des Fire Launchers dauerhaft aus. Root ist nicht nötig, und MacroDroid wird damit überflüssig. Die APK ist rund 57 KB groß und kommt ohne AndroidX und ohne Bibliotheken aus. Sie läuft ohne Dienst, ohne Polling und ohne Wakelocks.

## Kompatibilität

- **Getestet:** Alexa Tablet 12 Pro (ATSPWI), Alexa Tablet UI 16.1.10 (Android 16, API 36), Build SL16SHIP2003.1344N, Fire Launcher (Home UI) 1.0.21178.0, Headless System User Mode mit Nutzer 10, Alexa-App deaktiviert.
- **Voraussetzung:** Android 16 (minSdk 36). Auf älteren Fire-Tablets mit Fire OS 7 oder 8 lässt sich die App nicht installieren, dort gibt es diesen Launcher auch nicht.
- **Nicht getestet:** bei aktivierter und genutzter Alexa-App. Dann schreiben SystemUI und diese App abwechselnd denselben Wert. Gerät das in eine Schleife, schaltet sich die App selbst aus (siehe [Schleifenschutz](#einaus-schalter-und-schleifenschutz)).
- Ein Software-Update von Amazon kann die Logik von Launcher oder SystemUI ändern. Ob die App noch greift, zeigt ihr Verlauf.

## Wie es funktioniert

Der Fire Launcher (`TabletAlexaIngressVisibilityController`) zeigt das Symbol nur, solange `alexa_window_is_open` **und** `alexa_app_in_foreground` beide 0 sind. Er beobachtet beide Werte per ContentObserver und reagiert ohne Neustart. Gemessen: 44 ms vom Schreiben bis zum Ausblenden. Die App setzt nur `alexa_app_in_foreground = 1`.

SystemUI (`com.amazon.wm.shell.alexa`) setzt den Wert bei jedem Boot **zweimal** auf 0 zurück:

1. `AlexaFloatingWindowModel.onInit` beim Start von SystemUI, etwa 10 s nach dem Boot, noch vor `LOCKED_BOOT_COMPLETED`.
2. `AlexaForegroundTracker.notifyIfChanged` beim **ersten Entsperren**, ausgelöst durch die Keyguard-going-away-Transition. Der Tracker meldet dabei einmal „Alexa nicht im Vordergrund“. Danach schreibt er laut Code nur noch, wenn sich der Zustand ändert.

Wegen des zweiten Resets hat ein einmaliges Setzen direkt nach dem Boot nicht gereicht, und MacroDroid brauchte die Wartezeit von 60 s. Die App setzt den Wert deshalb an zwei Stellen:

- **`BootReceiver`** (`directBootAware`): bei `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`. Er schreibt nur, wenn der Wert abweicht. Explizite Broadcasts mit anderen Aktionen ignoriert er.
- **`ResetJob`**: ein JobScheduler-Job, der nur auslöst, wenn sich `content://settings/secure/alexa_app_in_foreground` ändert. Er setzt den Wert wieder auf 1 und plant sich neu. Beobachtet wird die Setting vom System, die App läuft also nur bei einer Änderung. Solche Jobs dürfen keinen Neustart überleben, deshalb plant der `BootReceiver` ihn nach jedem Boot neu.

Den Launcher stoppt die App nicht, das ist auch nicht nötig.

## Ein/Aus-Schalter und Schleifenschutz

Oben in der App gibt es den Schalter „Alexa-Symbol ausblenden“. Beim Ausschalten hebt die App den Änderungs-Trigger auf und setzt `alexa_app_in_foreground` auf 0, den Wert, den auch SystemUI schreibt. Das Symbol erscheint dann sofort wieder. Bis zum Wiedereinschalten schreibt die App nichts mehr, auch nicht nach einem Neustart oder Update. Eine Quick-Settings-Kachel gibt es bewusst nicht.

Der Schleifenschutz greift, wenn ein anderer Prozess den Wert ständig zurücksetzt, zum Beispiel eine aktivierte Alexa-App. Muss die App den Wert **10-mal innerhalb von 60 s** neu setzen, schaltet sie sich selbst aus. Den Wert lässt sie dabei, wie er ist, und zeigt eine Benachrichtigung. Ein normaler Boot braucht zwei Schreibvorgänge. Die Zeitpunkte werden mit der Uhrzeit gespeichert, damit Läufe aus verschiedenen Boots nicht zusammengezählt werden. Zum Fortsetzen schaltest du den Schalter wieder ein, das setzt auch den Zähler zurück.

## Download

Die signierte APK liegt bei den Releases als `alexa-icon-hider-<version>.apk`, daneben die `.sha256`-Datei. Die SHA-256-Prüfsumme jeder Version steht auch im [CHANGELOG](CHANGELOG.md).

```bash
sha256sum alexa-icon-hider-1.5.apk
```

Unter Windows geht das mit `certutil -hashfile alexa-icon-hider-1.5.apk SHA256`. Alle Versionen sind mit demselben Schlüssel signiert. Updates installieren sich nur, wenn der Schlüssel übereinstimmt. Fingerabdruck des Signaturzertifikats (`CN=minnallab`):

```
SHA-256: 24b85f093c4ddda288d8be50cd7484a7cbc04477fcfc2cd4db19a2ab5f21d33e
```

Prüfen lässt er sich mit `apksigner verify --print-certs alexa-icon-hider-1.5.apk` aus den Android Build-Tools.

## Installation

Voraussetzung: `adb` mit USB- oder WLAN-Debugging. Auf dem Alexa Tablet 12 Pro ist dein Nutzer die 10, das zeigt `adb shell am get-current-user`. Auf anderen Geräten ersetzt du `10` in allen Befehlen durch diese Zahl.

```bash
adb install --user 10 alexa-icon-hider-1.5.apk
```

```bash
adb shell pm grant --user 10 com.minnallab.alexaiconhider android.permission.WRITE_SECURE_SETTINGS
```

```bash
adb shell pm grant --user 10 com.minnallab.alexaiconhider android.permission.POST_NOTIFICATIONS
```

Weil die App nach der Einrichtung nie wieder geöffnet wird, nimmst du sie außerdem von der Pausierung ungenutzter Apps und von App Standby aus:

```bash
adb shell "cmd appops set --user 10 com.minnallab.alexaiconhider AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore && dumpsys deviceidle whitelist +com.minnallab.alexaiconhider"
```

Ohne den ersten Teil versetzt Android die App nach einigen Monaten ohne Öffnen in den Ruhezustand (App Hibernation). Sie ist dann gestoppt, erhält keine Boot-Broadcasts mehr, und das Symbol ist nach dem nächsten Neustart wieder da, ohne Hinweis. Außerdem entzieht Android dabei `POST_NOTIFICATIONS`. Der zweite Teil setzt die App auf die Akku-Ausnahmeliste (Bucket `EXEMPTED`), damit der Änderungs-Trigger auch in einem niedrigen Standby-Bucket nicht zurückgehalten wird. Fehlt eins davon, zeigt die App den Befehl mit der richtigen Nutzernummer zum Kopieren an.

Danach **„Alexa Icon Hider“ einmal öffnen**, auf dem Tablet oder per `adb shell am start --user 10 -n com.minnallab.alexaiconhider/.MainActivity`. Eine frisch installierte App ist im Zustand „stopped“ (`stopped=true notLaunched=true`) und erhält bis zum ersten Start keine Boot-Broadcasts. Die App zeigt den Ein/Aus-Schalter, den Status und, aufgeklappt, bis zu 20 Läufe seit dem letzten Neustart. Nach einem Neustart beginnt der Verlauf leer. Das Öffnen der App steht nur im Verlauf, wenn sie dabei den Wert setzen musste. Der Trigger-Lauf, den das eigene Schreiben der 1 auslöst, steht nicht darin, so belegt jedes Zurücksetzen nur eine Zeile. Fehlt `WRITE_SECURE_SETTINGS`, zeigt sie den `pm grant`-Befehl mit der richtigen Nutzernummer zum Kopieren an. Ein MacroDroid-Makro für das Alexa-Symbol kannst du danach löschen.

**Berechtigungen:**

- `WRITE_SECURE_SETTINGS` (per ADB): zum Schreiben der Setting.
- `RECEIVE_BOOT_COMPLETED`: wird bei der Installation automatisch erteilt, Android verlangt sie für Boot-Broadcasts.
- `POST_NOTIFICATIONS`: für den Hinweis, falls `WRITE_SECURE_SETTINGS` fehlt, und für den Schleifenschutz. Fehlt sie, erscheint der Hinweis nur in der App und im Log. Wird sie später erteilt, kommt die Benachrichtigung beim nächsten Lauf nach.

## Rückgängig machen

Zum vorübergehenden Abschalten reicht der Schalter in der App. Zum Entfernen:

Erst deinstallieren, dann die Setting löschen. In umgekehrter Reihenfolge würde der Job den gelöschten Wert sofort wieder setzen.

```bash
adb shell "pm uninstall --user 10 com.minnallab.alexaiconhider && settings delete --user 10 secure alexa_app_in_foreground; dumpsys deviceidle whitelist -com.minnallab.alexaiconhider"
```

Der letzte Teil entfernt die App von der Akku-Ausnahmeliste, falls Android das nicht schon beim Deinstallieren erledigt hat.

Das Symbol erscheint danach sofort wieder.

## Bauen

Java 17 Bytecode, AGP 9.4.1, Gradle 9.7.1 (Wrapper mit SHA-256), compileSdk/targetSdk/minSdk 36, R8 mit `isMinifyEnabled` und `isShrinkResources`. Das in AGP 9 eingebaute Kotlin ist abgeschaltet (`android.builtInKotlin=false`). Sonst kämen die Kotlin-Stdlib und 12 KB Builtins in die APK.

```bash
./gradlew assembleRelease
```

Die APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`.

- JDK 17 oder neuer, getestet mit der JBR 25 von Android Studio.
- `local.properties` mit `sdk.dir` (nicht im Repo).
- Signiert wird mit dem Keystore aus `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`, nicht im Repo). Die Umgebungsvariablen `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD` gehen vor. Updates müssen mit demselben Schlüssel signiert sein. Ohne Keystore entsteht eine unsignierte Release-APK.

## CI und Releases

GitHub Actions baut bei jedem Push auf `main` und jedem Pull Request (`.github/workflows/ci.yml`). Der Lauf startet Lint, baut die Debug-APK und dazu die Release-APK unsigniert, damit R8 und das Ressourcen-Shrinking mitgeprüft werden. Die Debug-APK und der Lint-Bericht liegen 14 Tage als Artefakt am Lauf. Alle Actions sind auf einen Commit-SHA festgelegt, `setup-gradle` prüft dabei auch `gradle-wrapper.jar`. Dependabot schlägt monatlich Updates für Actions und das Android-Gradle-Plugin vor.

Ein Release startet mit einem Tag (`.github/workflows/release.yml`):

```bash
git tag -a v1.5 -m "1.5" && git push origin v1.5
```

Der Workflow bricht ab, wenn der Tag nicht zu `versionName` passt oder im CHANGELOG kein Abschnitt `## 1.5 …` steht. Dann baut er ohne Gradle-Cache die signierte APK und prüft mit `apksigner`, dass sie mit dem oben genannten Zertifikat signiert ist. Mit jedem anderen Schlüssel bricht er ab. Danach legt er eine Build-Provenance-Attestation an und erstellt das GitHub-Release mit `alexa-icon-hider-<version>.apk`, der `.sha256`-Datei und dem CHANGELOG-Abschnitt als Text. Die Prüfsumme der in CI gebauten APK hängt er an. Sie gehört danach in den CHANGELOG.

Die Herkunft einer heruntergeladenen APK lässt sich so prüfen:

```bash
gh attestation verify alexa-icon-hider-1.5.apk --repo <owner>/<repo>
```

Einmalig einzurichten, unter Settings > Environments ein Environment `release` mit diesen Secrets:

| Secret | Inhalt |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Der Keystore als Base64, unter Windows `[Convert]::ToBase64String([IO.File]::ReadAllBytes("alexa-icon-hider-release.jks")) \| Set-Clipboard` |
| `SIGNING_STORE_PASSWORD` | `storePassword` |
| `SIGNING_KEY_ALIAS` | `keyAlias` |
| `SIGNING_KEY_PASSWORD` | `keyPassword` |

Mit „Required reviewers“ am Environment wartet jedes Release auf eine Freigabe, bevor die Secrets ausgelesen werden. Unter Settings > Tags schützt eine Tag-Regel für `v*` davor, dass jemand anderes Tags anlegt oder verschiebt.

## Testergebnisse (27.10.2026)

### Neustarts

Zeiten in Sekunden seit dem Boot. Die Systemereignisse stammen aus `logcat -b all`, umgerechnet über `boot_progress_start`. Die Läufe der App stehen in ihrem eigenen Verlauf, denn Logcat verwirft auf diesem Gerät die App-Logs in der frühen Boot-Phase.

| Boot | 1. Reset (SystemUI-Init) | App schreibt (`LOCKED_BOOT_COMPLETED`) | Entsperrt (`USER_UNLOCKED`) | 2. Reset abgefangen | Symbol sichtbar entsperrt |
|---|---|---|---|---|---|
| 2 | +10,06 | +12,9 (0→1) | +17,54 | +18,3 (0→1) | ≤ 0,8 s |
| 3 | +10,06 | +13,0 (0→1) | +15,61 | +16,3 (0→1) | ≤ 0,7 s |
| 4 | +10,13 | +13,1 (0→1) | +18,23 | +18,9 (0→1) | ≤ 0,7 s |
| 5 (1.4) | – | +13,2 (0→1) | +15,34 | +16,1 (0→1) | ≤ 0,8 s |

- Zwischen dem 1. Reset und dem Schreiben der App (etwa 3 s) ist das Tablet noch gesperrt. Die Taskleiste ist dann nicht zu sehen.
- Die Spalte „sichtbar“ ist eine Obergrenze: Zeit vom Entsperren bis zum Schreiben. Der 2. Reset kommt erst am Ende der Entsperr-Animation, etwa 0,2 s nach `USER_UNLOCKED`, und der Job läuft frühestens 0,5 s danach.
- Auf dem Startbildschirm (Lawnchair) zeigt der Fire Launcher keine Taskleiste.
- Boot 1 lief noch mit Version 1.0 ohne Verlauf. Die App startete 0,44 s nach `LOCKED_BOOT_COMPLETED`, am Ende stand `pkg:com.minnallab.alexaiconhider value:1`.
- Boot 5 lief mit Version 1.4. Der Zeitpunkt des 1. Resets ließ sich diesmal nicht im Log finden. Dass er stattfand, zeigt der Wert 0, den die App bei `LOCKED_BOOT_COMPLETED` vorfand. Zwei Schreibvorgänge pro Boot liegen weit unter der Schwelle des Schleifenschutzes, die App blieb eingeschaltet.
- Nach allen fünf Boots stand `alexa_app_in_foreground` mit `pkg:com.minnallab.alexaiconhider` auf `1`.

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
| Schalter aus / ein (1.4) | Aus: Wert 1→0, Trigger abgemeldet, Wert bleibt 0. Ein: Wert 0→1 innerhalb von 2 s, Trigger wieder geplant |
| Schleifenschutz (1.4): `settings put … 0` alle 1,5 s | Nach dem 10. Schreiben der App ausgeschaltet (zwei Läufe), Wert bleibt danach 0, Benachrichtigung erscheint. Eine offene App zeigt das ohne Neustart an |
| Update im ausgeschalteten Zustand (1.4) | `MY_PACKAGE_REPLACED` läuft mit `aus` und schreibt nichts, der Grund bleibt in der App sichtbar |
| `WRITE_SECURE_SETTINGS` fehlt | kein Absturz, genau eine Benachrichtigung mit dem `pm grant`-Befehl. Nach erneutem Erteilen und Öffnen ist sie weg und der Wert wieder 1 |
| Benachrichtigungen erst nachträglich erlaubt (1.4) | Bei verbotenen Benachrichtigungen und fehlender Berechtigung erscheint nichts. Nach dem Erlauben erscheint der Hinweis beim nächsten Zurücksetzen, mit `--user 10` im Befehl |
| App mehrmals öffnen (1.4) | Bei Wert 1 kein neuer Eintrag im Verlauf, Boot- und Trigger-Läufe bleiben erhalten |
| APK-Größe | 56.565 Bytes (lokaler Release-Build 1.5, R8, v2-signiert). 1.4 hatte 45.192 Bytes, 1.3 hatte 15.476 Bytes |
| Standby-Bucket | 10 (ACTIVE) direkt nach den Tests mit 1.4. Mit dem Ausnahme-Befehl 5 (EXEMPTED) |

### 1.5 im Emulator (API 37, Nutzer 0)

Die Änderungen in 1.5 sind bisher nur im Emulator getestet, nicht auf dem Tablet. Den Reset durch SystemUI gibt es dort nicht.

| Test | Ergebnis |
|---|---|
| Update 1.4 → 1.5 (`install -r`) | `MY_PACKAGE_REPLACED` kommt an, Wert 0→1, Trigger geplant |
| `settings put … 0` von Hand | nach 0,55 s bzw. 0,56 s wieder 1, je Zurücksetzen eine Zeile im Verlauf statt zwei |
| `am broadcast -n …/.BootReceiver` mit fremder Aktion und ohne Aktion | ignoriert, kein Eintrag im Verlauf |
| Ausnahme fehlt | Zeile „Ausnahme für ungenutzte Apps“ zeigt „fehlt“, Hinweis mit Befehl erscheint. Nach dem angezeigten Befehl und erneutem Öffnen „erteilt“, Hinweis weg, Bucket 5 |
| Aus, Wert trotzdem 1 | Überschrift „ausgeblendet“, passend zur Skizze (in 1.4 stand dort „sichtbar“) |
| Schleifenschutz | Nach dem 10. Schreiben ausgeschaltet, Wert bleibt 0, Benachrichtigung erscheint |
| Neustart | `LOCKED_BOOT_COMPLETED` und `BOOT_COMPLETED` im Verlauf, Wert 1, Trigger geplant, Bucket 5 |
| Deutsch, dunkles Theme | „Seit Boot“ mit Dezimalkomma, Schalter in beiden Zuständen und Themes deutlich erkennbar |

Noch nicht über mehrere Tage beobachtet: ob spätere Entsperrungen wirklich nicht mehr zurücksetzen (laut Code nicht). Das zeigt der Verlauf in der App.

Ein **Content-Trigger** wartet ohne `setTriggerContentUpdateDelay(0)` etwa 10 s auf Ruhe, bevor der Job startet (gemessen). Mit `0` greift die Untergrenze des Systems von etwa 0,5 s.

## Lizenz

[MIT](LICENSE), © 2026 minnallab. Nicht mit Amazon verbunden. „Alexa“ und „Fire“ sind Marken von Amazon.com, Inc. oder verbundenen Unternehmen.
