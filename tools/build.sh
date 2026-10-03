#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JOULE_BUILD_TOOLS:?Set JOULE_BUILD_TOOLS to Android build-tools 35.0.0}"
: "${JOULE_ANDROID_JAR:?Set JOULE_ANDROID_JAR to API 35 android.jar}"
: "${JOULE_ECJ_JAR:?Set JOULE_ECJ_JAR to Eclipse Java compiler jar}"
mkdir -p build/gen build/classes build/dex build/tests signing
"$JOULE_BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$JOULE_BUILD_TOOLS/aapt2" link -o build/base.apk -I "$JOULE_ANDROID_JAR" -A app/src/main/assets --manifest app/src/main/AndroidManifest.xml \
	--java build/gen --min-sdk-version 31 --target-sdk-version 35 --version-code 1 --version-name 0.1 build/resources.zip
mapfile -t sources < <(find app/src/main/java build/gen -name '*.java')
java -jar "$JOULE_ECJ_JAR" -8 -nowarn -cp "$JOULE_ANDROID_JAR" -d build/classes "${sources[@]}"
java -jar "$JOULE_ECJ_JAR" -8 -nowarn -cp "build/classes:$JOULE_ANDROID_JAR" -d build/tests tests/ProtocolTests.java
java -cp build/tests:build/classes com.local.joulekompakt.ProtocolTests
mapfile -t classes < <(find build/classes -name '*.class')
"$JOULE_BUILD_TOOLS/d8" --lib "$JOULE_ANDROID_JAR" --min-api 31 --output build/dex "${classes[@]}"
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$JOULE_BUILD_TOOLS/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing/joule-test.keystore ]]; then
	keytool -genkeypair -keystore signing/joule-test.keystore -storepass android -alias joule -keypass android \
		-dname 'CN=Joule Local Test,O=Local,C=US' -keyalg RSA -keysize 2048 -validity 10000
fi
"$JOULE_BUILD_TOOLS/apksigner" sign --ks signing/joule-test.keystore --ks-pass pass:android --key-pass pass:android \
	--out Joule-Local-v0.1.apk build/aligned.apk
"$JOULE_BUILD_TOOLS/apksigner" verify --verbose Joule-Local-v0.1.apk
"$JOULE_BUILD_TOOLS/aapt2" dump badging Joule-Local-v0.1.apk
