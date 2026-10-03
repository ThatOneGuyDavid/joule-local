# Joule Local

**Control your original ChefSteps Joule over Bluetooth. No account. No cloud.**

Initial community release: **v0.1**.

## Features

- Find and pair directly with Joule using its physical button.
- Save the pairing key on the phone.
- Display live water temperature and set a target in °F.
- Start and stop cooking with command acknowledgement.
- Use a separate phone reminder timer.
- Copy diagnostic logs for troubleshooting.

## Compatibility and test status

The first tester reported that v0.1 worked with their Mudita Kompakt and
original ChefSteps Joule on October 2, 2026. Exact Joule model, firmware,
and individual feature-test results have not yet been recorded.
Joule Turbo and other Android devices have not been validated.

Compilation, APK signature verification, and all 33 protocol checks passed.
Seven outgoing packets were compared byte-for-byte with upstream.

## Download

Download the [v0.1 APK](releases/v0.1/Joule-Local-v0.1.apk?raw=true), or visit
the [GitHub release](https://github.com/ThatOneGuyDavid/joule-local/releases/tag/v0.1).
The APK SHA-256 is recorded in [SHA256SUMS.txt](releases/v0.1/SHA256SUMS.txt).
This is a manually sideloaded APK; there is no app store or automatic updater.


Native, offline Android BLE controller for an original ChefSteps Joule
(CS10001 / CS20001), designed for the Mudita Kompakt's monochrome screen.
Package: `com.local.joulekompakt`. Android 12 or newer. No Google services.

## Install and first test

1. Sideload `Joule-Local-v0.1.apk` using your usual APK installation method.
2. Fill a pot within Joule's marked water limits and power on Joule.
   Close other Joule / Breville apps so they release its Bluetooth connection.
3. Open **Joule Local**, tap **Find Joule**, allow Nearby devices, then tap
   **Find Joule** again and select the matching device from the results.
4. When prompted, press Joule's physical top button within 60 seconds.
   A successfully accepted pairing key is saved privately on the phone.
5. Wait for a live water temperature. Set a target in °F and tap **START / SET**.
   The command-status line reports an acknowledged command or an explicit
   rejection / missing confirmation. Check that the actual Joule heats.
6. Tap **STOP** and verify the unit stops. A Bluetooth write alone is not
   treated as a successful stop.

If the first attempt fails, open **More / diagnostics → Copy diagnostic log**
and include the relevant portion in a GitHub issue. Keys are excluded from logs.
Remove device addresses or other identifying information before posting publicly.
Logs contain the selected Bluetooth address, model, and protocol status codes.
Use **Full start format** only if the default compact start is rejected.
Changing this option does not send a command; tap START / SET yourself to try it.

## Timer behavior

The duration is a **phone reminder**, started separately when you add food.
It does **not** stop Joule or claim to configure an appliance-side timer.
This reflects the upstream implementation: its working manual-cook messages
omit the cook-time field, even though its public API accepts cook time.
The countdown persists across closing/reopening the app. Android sleep,
notification permissions, reboot, or force-stop can delay/prevent an alert.
Keep notifications enabled. The reminder has no effect on the heater.

Disconnecting, closing the app, losing Bluetooth, or uninstalling the app does
not stop Joule. No heating command is retried automatically or sent on reconnect.
The initial tester reported success; broader hardware and firmware coverage
is still needed. Please report your model, firmware if known, and results.

## Privacy and permissions

No Internet permission, accounts, cloud requests, analytics, external storage,
or location permission. Uses Nearby devices (Bluetooth scan/connect), a normal
foreground-service permission to maintain the connection, and optional
notifications on Android 13+. Pairing keys use private app preferences, with
Android app backup disabled. The only exported activity is the launcher;
the controller service and timer receiver are private to the app.

## Build

Requires Linux, Java 17 runtime, keytool, zip/unzip, Android build-tools 35.0.0,
Android API 35 platform, and Eclipse Java compiler 3.39.0. No Gradle, Android
Studio, Kotlin runtime, third-party Android libraries, or remote build required.

Download build dependencies from their official distributors:

- https://dl.google.com/android/repository/build-tools_r35_linux.zip
- https://dl.google.com/android/repository/platform-35_r02.zip
- https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar

After unpacking, run:

```bash
JOULE_BUILD_TOOLS=/absolute/path/android-15 \
JOULE_ANDROID_JAR=/absolute/path/android-35/android.jar \
JOULE_ECJ_JAR=/absolute/path/ecj-3.39.0.jar \
bash tools/build.sh
```

The build checks 33 protocol assertions, including byte-for-byte parity with
seven packets from the pinned upstream Python implementation, compiles Java,
converts classes to DEX, aligns the APK, signs it, and verifies the signature.

Signing keys are not included in this public repository. On your first local
build, the script creates a development key under the ignored `signing/` folder
(alias `joule`, development passwords `android`). Keep your own key private
and back it up if you want to update your own builds in place.

The downloadable v0.1 APK is signed with the original project test certificate.
A locally rebuilt APK signed with a different key cannot update that installed
APK in place. The maintainer retains the original key privately for continuity.

## Protocol provenance

Apache-2.0 protocol port from https://github.com/acato/ha-joule,
commit `4c950a68d22b059be09fb681afa83339408da6d0`.
Upstream license and notices are included in both source and APK assets.

This port checks reply handles and result codes, serializes Android GATT
operations, reads characteristic 4323 when notified on 4325, handles original
Joule key exchange, and renews live telemetry. The full command option adds
upstream iOS-style cook metadata; the default compact command matches the
upstream first attempt. Both are manual-mode programs.

Joule Local is an independent project and is not affiliated with Breville.

## Contributing

Bug reports, compatibility reports, and pull requests are welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md). This project uses the Apache-2.0 license;
see [LICENSE](LICENSE) and [NOTICE](NOTICE) for upstream attribution.

## Version history

See [reconstructed version history](docs/VERSION-HISTORY.md) for recovery
provenance, binary identity, and the distinction between reported hardware
results and automated checks. v0.1 is the only recoverable released version.
