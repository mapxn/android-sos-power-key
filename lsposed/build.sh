#!/bin/sh
# Build build/a89-power-fix.apk (LSPosed module). Needs a JDK >= 11, the Android SDK
# (build-tools + a platform android.jar) and keytool on PATH.
#   JAVA_HOME      JDK 11+            (default: the one on PATH)
#   ANDROID_HOME   Android SDK root   (default: ~/Library/Android/sdk)
#   BUILD_TOOLS    build-tools version (default: newest installed)
#   PLATFORM       platform folder     (default: newest installed)
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$(ls "$SDK/build-tools" | sort -V | tail -1)}"
PLATFORM="${PLATFORM:-$(ls "$SDK/platforms" | sort -V | tail -1)}"
BT="$SDK/build-tools/$BUILD_TOOLS"
AJ="$SDK/platforms/$PLATFORM/android.jar"
[ -f "$AJ" ] || { echo "android.jar not found: $AJ" >&2; exit 1; }
[ -n "$JAVA_HOME" ] && PATH="$JAVA_HOME/bin:$PATH"

rm -rf build/stubs build/classes build/dex build/res.zip build/*.apk
mkdir -p build/stubs build/classes build/dex

javac -nowarn -source 8 -target 8 -d build/stubs $(find stubs -name '*.java')
javac -nowarn -source 8 -target 8 -cp "$AJ:build/stubs" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex $(find build/classes -name '*.class')

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$AJ" --manifest AndroidManifest.xml -A assets build/res.zip
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Throw-away signing key, generated locally on first build and never committed.
[ -f build/debug.keystore ] || keytool -genkeypair -keystore build/debug.keystore -storepass android \
    -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=a89-power-fix"
"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --out build/a89-power-fix.apk build/aligned.apk
"$BT/apksigner" verify build/a89-power-fix.apk
echo "built: $(pwd)/build/a89-power-fix.apk"
