# Reconstructed version history

This repository was populated on October 3, 2026 from the source and binaries
preserved during development. Its publication commits are reconstructed
snapshots, not the original development-time Git history. Dates below describe
the original work; Git commit timestamps describe publication.

| Version | Original date | Recovered material | Status |
| --- | --- | --- | --- |
| v0.1 | October 2, 2026 | Complete app source, build script, protocol tests, and signed APK | First and latest recoverable release |

Only v0.1 was delivered and reported working in the available conversation and
saved project artifacts. No earlier numbered releases or binary-only historical
releases were found. Intermediate unsigned build outputs are not releases.

The public app source and resource files match the preserved v0.1 source
archive byte-for-byte. Publication adds documentation, a root license,
ignore rules, checksums, and release automation. The build script also creates
the initially absent signing directory so a clean source checkout can build.
The released APK is the original preserved binary; it has not been rebuilt.

## Binary identity

- File: `Joule-Local-v0.1.apk`
- Size: 41,587 bytes
- SHA-256: `3d3061544c6fb72914fc5f78cf51467cbb7a89e7480f2dd832affe1e4608b2c5`
- Application ID: `com.local.joulekompakt`
- Version name / code: `0.1` / `1`
- Minimum Android API: 31 (Android 12)

## What was tested

The initial user reported **“it worked”** after sideloading v0.1 on a Mudita
Kompakt for their original ChefSteps Joule. Exact Joule model and firmware,
and individual START, STOP, telemetry, timer, and reconnection results were not
separately reported. Compatibility across other devices is not established.

The build environment completed Java compilation, DEX packaging, APK signing
and signature verification, and 33 protocol checks, including seven
byte-for-byte comparisons against the pinned upstream Python implementation.
No physical appliance was attached to the build environment. The original
build-verification output is retained in this repository.

## Signing continuity

The original signing key is retained privately for compatible future updates.
No keystore, private key, device pairing key, personal account data, or private
development archive is included in this public repository or its releases.
