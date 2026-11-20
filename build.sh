#!/usr/bin/env bash
# 从源码重新构建 QQ音乐轻听 APK
# 本地: 需 JDK 17（或设置 R8_JAR 指向新版 r8.jar 以兼容 JDK 25）
# GitHub Actions: 见 .github/workflows/build-apk.yml
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="${BUILD_TOOLS:-$ANDROID_HOME/build-tools/34.0.0}"
AJ="${ANDROID_JAR:-$ANDROID_HOME/platforms/android-35/android.jar}"

if [ ! -f "$AJ" ]; then echo "缺少 android.jar: $AJ" >&2; exit 1; fi
if [ ! -x "$BT/aapt2" ]; then echo "缺少 build-tools: $BT" >&2; exit 1; fi

rm -rf build output
mkdir -p build/gen build/classes build/dex output

echo "[1/6] 编译资源"
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml \
  -R build/res.zip --java build/gen --min-sdk-version 21 --target-sdk-version 33 \
  --version-code 1 --version-name 1.0 --auto-add-overlay

echo "[2/6] 编译 Java"
javac --release 8 -encoding UTF-8 -nowarn -cp "$AJ" -d build/classes \
  $(find src build/gen -name '*.java')

echo "[3/6] 生成 dex"
if [ -n "${R8_JAR:-}" ]; then
  D8CMD=(java -cp "$R8_JAR" com.android.tools.r8.D8)
else
  D8CMD=("$BT/d8")
fi
"${D8CMD[@]}" --release --lib "$AJ" --min-api 21 --output build/dex \
  $(find build/classes -name '*.class')

echo "[4/6] 打包"
cp build/base.apk build/work.apk
(cd build/dex && zip -q ../work.apk classes.dex)
"$BT/zipalign" -f -p 4 build/work.apk build/aligned.apk

echo "[5/6] 签名"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -alias qqmusic -keyalg RSA \
    -keysize 2048 -validity 10000 -storepass android -keypass android \
    -dname "CN=QQMusicLite,OU=Dev,O=Dev,L=SZ,ST=GD,C=CN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias qqmusic \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out output/QQMusicLite-v1.1.apk build/aligned.apk

echo "[6/6] 验证"
"$BT/apksigner" verify output/QQMusicLite-v1.1.apk
ls -lh output/QQMusicLite-v1.1.apk
