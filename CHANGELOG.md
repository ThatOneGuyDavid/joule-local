# Changelog

## v0.1 — 2026-10-02

Initial community version of Joule Local for Android 12 and newer.

- Direct BLE scanning and button-assisted Joule pairing.
- Private, persistent pairing-key storage.
- Live water temperature and target temperature in Fahrenheit.
- START / SET and STOP with device-response checks.
- Separate phone reminder timer; it never stops the heater.
- Monochrome interface designed for Mudita Kompakt.
- Copyable diagnostics without pairing keys.
- Protocol tests and a local Linux APK build script.

Initial tester reported success on Mudita Kompakt with an original ChefSteps
Joule. Exact model, firmware, and per-feature validation remain unrecorded.

Known limitations: original Joule only; no Turbo support claimed; Fahrenheit
only; no automatic heater shutoff or automatic reconnect; phone timer alerts
can be affected by Android power management and notification permissions.
