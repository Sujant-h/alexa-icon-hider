# Changelog

## 1.5-beta.1 (versionCode 6), 27.10.2026

Beta: bisher nur im Emulator getestet, noch nicht auf dem Alexa Tablet 12 Pro. Die fertige 1.5 bekommt versionCode 7 und installiert sich über diese Beta.

- Die App ist jetzt auf Englisch, Deutsch bleibt als Übersetzung. Die Sprache folgt dem System oder der Einstellung unter Apps > Sprache.
- Neue Zeile „Ausnahme für ungenutzte Apps“. Fehlt die Ausnahme von App Hibernation und App Standby, zeigt die App den Befehl zum Kopieren. Ohne sie würde Android die App nach einigen Monaten ohne Öffnen stoppen, und das Symbol käme nach dem nächsten Neustart zurück.
- Der Verlauf zeigt nur noch die Läufe seit dem letzten Neustart und ist eingeklappt, bis man ihn antippt. Ältere Läufe erkennt die App am Boot-Zähler des Systems und verwirft sie.
- Der Trigger-Lauf, den das eigene Schreiben auslöst, steht nicht mehr im Verlauf. Jedes Zurücksetzen belegt eine Zeile statt zwei.
- Bei ausgeschalteter App passt die Überschrift jetzt zum tatsächlichen Wert. Bisher stand dort auch bei Wert 1 „sichtbar“.
- Der `BootReceiver` ignoriert Broadcasts mit fremden oder fehlenden Aktionen, statt sie zu verarbeiten und in den Verlauf zu schreiben.
- Der Schalter ist in beiden Zuständen und Themes deutlicher: ausgeschaltet mit Rand und grauem Knopf, eingeschaltet mit dunklem Knopf im dunklen Theme.
- „Seit Boot“ im Verlauf nutzt das Dezimalzeichen der Sprache. Der Schleifenschutz-Text sagt „innerhalb von 60 s“.
- Oben links steht jetzt das App-Symbol statt des Minnal-Lab-Zeichens. Das Zeichen steht in einer neuen Fußzeile.

## 1.4 (versionCode 5), 27.10.2026

- Ein/Aus-Schalter in der App. „Aus“ hebt den Änderungs-Trigger auf und setzt `alexa_app_in_foreground` auf 0, das Symbol erscheint sofort wieder. Der Zustand übersteht Neustarts und Updates.
- Schleifenschutz: Muss die App den Wert 10-mal innerhalb von 60 s neu setzen, schaltet sie sich selbst aus und meldet das per Benachrichtigung. Den Wert lässt sie dabei, wie er ist. Mit dem Schalter geht es weiter.
- Der Verlauf zeigt ausgeschaltete Läufe mit `aus` und das automatische Ausschalten mit `pausiert`.
- Neues Design im Stil von minnallab.com: Statuszeile, Schalter als Karte, Skizze der Taskleiste, Systemwerte und Verlauf als Tabelle, zweispaltig auf dem Tablet, helles und dunkles Theme. Fehlt die Berechtigung, lässt sich der `pm grant`-Befehl direkt kopieren. Dazu ein App-Symbol.
- Die Statusanzeige aktualisiert sich, solange die App offen ist, sobald sich der Wert ändert.
- Das Öffnen der App steht nur noch im Verlauf, wenn sie dabei schreiben musste. Bisher verdrängte jedes Öffnen einen Boot- oder Trigger-Lauf aus den letzten 20 Einträgen.
- Der `pm grant`-Befehl in App und Benachrichtigung enthält die tatsächliche Nutzernummer statt fest `10`.
- Der Hinweis auf die fehlende Berechtigung geht nicht mehr verloren, wenn Benachrichtigungen beim ersten Mal verboten waren. Er erscheint, sobald sie erlaubt sind.

SHA-256 von `alexa-icon-hider-1.4.apk`: `4711818a3d2011d5698b99a20e2ffa6ec2a39806117c8f0b58bb4be411d10b33`

## 1.3 (versionCode 4), 27.10.2026

Erste veröffentlichte Version.

- Setzt `alexa_app_in_foreground = 1` für den eigenen Nutzer bei `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`. Geschrieben wird nur, wenn der Wert abweicht.
- Ein Content-Trigger-Job setzt den Wert nach jedem Zurücksetzen wieder auf 1, auch nach dem Reset durch SystemUI beim ersten Entsperren. Reaktionszeit etwa 0,5 s.
- Fehlt `WRITE_SECURE_SETTINGS`, erscheint einmalig eine Benachrichtigung mit dem `pm grant`-Befehl. Sie verschwindet, sobald die Berechtigung erteilt ist.
- Statusanzeige mit den letzten 20 Läufen.

SHA-256 von `alexa-icon-hider-1.3.apk`: `13663333948936e7cf2f6cd32779707e4cb9436e171c463e1d7094f8139e4e53`
