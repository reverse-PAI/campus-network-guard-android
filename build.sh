#!/bin/sh
# 校园网守护 · 安卓纯应用版 —— 手工构建（无 Gradle / 无 AndroidX / 无第三方依赖）
# /sdcard 是 FUSE，JVM 读不了源码与 jar，所以工程复制到 /tmp 编译，成品再拷回 dist/
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
TOOL="${CNG_TOOLCHAIN:-/opt/cng-toolchain}"
AJAR="$TOOL/android.jar"
D8JAR="$TOOL/d8.jar"
WORK="${CNG_WORKDIR:-/tmp/cngbuild}"
OUT="campus-guard-v3.0.1.apk"

[ -f "$AJAR" ] || { echo "缺少 android.jar：$AJAR"; exit 1; }
[ -f "$D8JAR" ] || { echo "缺少 d8.jar：$D8JAR"; exit 1; }

echo "[0/7] 准备 /tmp 工作区"
rm -rf "$WORK"
mkdir -p "$WORK"
cp -a "$PROJ/src" "$PROJ/res" "$PROJ/AndroidManifest.xml" "$WORK"/
cd "$WORK"
mkdir -p build/classes build/dex

echo "[1/7] aapt 打包清单"
aapt package -f -M AndroidManifest.xml -S res -I "$AJAR" -F build/base.apk

echo "[2/7] javac 编译（无调试信息）"
javac -encoding UTF-8 -g:none -source 8 -target 8 -bootclasspath "$AJAR" \
      -d build/classes $(find src -name '*.java')
[ -f build/classes/com/cng/guard/MainActivity.class ] || { echo "编译失败"; exit 1; }

echo "[3/7] R8 裁剪 + 转 dex"
cat > build/keep.pro <<'KEEP'
-keep class com.cng.guard.MainActivity { <init>(...); }
-keep class com.cng.guard.GuardService { <init>(...); }
-keep class com.cng.guard.BootReceiver { <init>(...); }
-keep class com.cng.guard.TickReceiver { <init>(...); }
-dontwarn android.**
-dontwarn org.json.**
KEEP
java -cp "$D8JAR" com.android.tools.r8.R8 --release --min-api 29 --lib "$AJAR" \
     --pg-conf build/keep.pro --output build/dex $(find build/classes -name '*.class')

echo "[4/7] 合成 APK"
python3 - "$WORK" <<'PY'
import os, shutil, sys, zipfile
root = sys.argv[1]
shutil.copyfile(os.path.join(root, 'build', 'base.apk'), os.path.join(root, 'build', 'unsigned.apk'))
with zipfile.ZipFile(os.path.join(root, 'build', 'unsigned.apk'), 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(root, 'build', 'dex', 'classes.dex'), 'classes.dex')
print('   classes.dex 已加入')
PY

echo "[5/7] zipalign"
zipalign -f 4 build/unsigned.apk build/aligned.apk

echo "[6/7] 签名"
KS="$WORK/keystore.jks"
if [ -f "$PROJ/keystore.jks" ]; then cp "$PROJ/keystore.jks" "$KS"; fi
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias cng -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass campusguard -keypass campusguard \
    -dname "CN=CampusNetworkGuard, O=CNG, C=CN" >/dev/null 2>&1
  cp "$KS" "$PROJ/keystore.jks"
  echo "   已生成 keystore.jks（口令 campusguard）"
fi
apksigner sign --ks "$KS" --ks-pass pass:campusguard --key-pass pass:campusguard \
    --v4-signing-enabled false --out "$WORK/cng-guard.apk" build/aligned.apk
apksigner verify "$WORK/cng-guard.apk"

echo "[7/7] 拷回 dist/"
mkdir -p "$PROJ/dist"
cp "$WORK/cng-guard.apk" "$PROJ/dist/$OUT"
ls -l "$PROJ/dist/$OUT"
echo "完成：dist/$OUT"
