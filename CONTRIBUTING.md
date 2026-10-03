# Contributing

Please open an issue before a substantial change so the intended behavior can
be discussed. Compatibility reports and small fixes are welcome.

## Reporting a problem

Include:

- App version, phone model, and Android version.
- Joule model and firmware, if available.
- What you tried, what you expected, and what happened.
- Whether pairing, temperature updates, START, and STOP each worked.
- Relevant output from **More / diagnostics → Copy diagnostic log**.

Pairing keys are excluded from app logs. Remove Bluetooth addresses and other
identifying details before publishing a log. Never upload a signing keystore,
private app preferences, credentials, or unrelated personal data.

## Making changes

Follow the local build instructions in README.md. Run the protocol checks
through `tools/build.sh`. Use tabs for Java indentation and retain upstream
license notices. Explain the change and what you tested in your pull request.

Protocol changes should include packet tests. Keep heating commands explicit;
do not automatically replay START after a connection failure or treat a
successful Bluetooth write as proof that Joule accepted a command.

Test hardware behavior with the circulator properly submerged within its
marked water limits. Report actual observations separately from automated
test results. Do not claim compatibility with untested models or firmware.
