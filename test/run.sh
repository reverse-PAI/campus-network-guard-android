#!/bin/sh
# 在桌面 JVM 上跑纯逻辑单测（不需要手机）。用法：sh test/run.sh
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOL="${CNG_TOOLCHAIN:-/opt/cng-toolchain}"
WORK="${CNG_TESTDIR:-/tmp/cng-test}"
rm -rf "$WORK"
mkdir -p "$WORK/src/com/cng/guard" "$WORK/out"
cp "$ROOT"/src/com/cng/guard/*.java "$WORK/src/com/cng/guard/"
cp "$ROOT"/test/com/cng/guard/*.java "$WORK/src/com/cng/guard/"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$TOOL/android.jar" \
      -d "$WORK/out" $(find "$WORK/src" -name '*.java') 2>&1 | grep -v '^Note:' || true
CP="$WORK/out:$TOOL/android.jar"
fail=0
for t in NetTest LogicTest SrunTest; do
    echo "--- $t ---"
    java -Dfile.encoding=UTF-8 -cp "$CP" "com.cng.guard.$t" || fail=1
done
[ "$fail" -eq 0 ] && echo "全部单测通过 ✅" || { echo "存在失败 ❌"; exit 1; }
