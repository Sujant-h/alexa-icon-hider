# Changelog

## 1.4 (versionCode 5), unveröffentlicht

- Ein/Aus-Schalter in der App. „Aus“ hebt den Änderungs-Trigger auf und setzt `alexa_app_in_foreground` auf 0, das Symbol erscheint sofort wieder. Der Zustand übersteht Neustarts und Updates.
- Schleifenschutz: Muss die App den Wert 10-mal innerhalb von 60 s neu setzen, schaltet sie sich selbst aus und meldet das per Benachrichtigung. Den Wert lässt sie dabei, wie er ist. Mit dem Schalter geht es weiter.
- Der Verlauf zeigt ausgeschaltete Läufe mit `aus` und das automatische Ausschalten mit `pausiert`.
- Die Statusanzeige aktualisiert sich, solange die App offen ist, sobald sich der Wert ändert.

## 1.3 (versionCode 4), 27.10.2026

Erste veröffentlichte Version.

- Setzt `alexa_app_in_foreground = 1` für den eigenen Nutzer bei `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`. Geschrieben wird nur, wenn der Wert abweicht.
- Ein Content-Trigger-Job setzt den Wert nach jedem Zurücksetzen wieder auf 1, auch nach dem Reset durch SystemUI beim ersten Entsperren. Reaktionszeit etwa 0,5 s.
- Fehlt `WRITE_SECURE_SETTINGS`, erscheint einmalig eine Benachrichtigung mit dem `pm grant`-Befehl. Sie verschwindet, sobald die Berechtigung erteilt ist.
- Statusanzeige mit den letzten 20 Läufen.

SHA-256 von `alexa-icon-hider-1.3.apk`: `13663333948936e7cf2f6cd32779707e4cb9436e171c463e1d7094f8139e4e53`
