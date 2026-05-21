#!/usr/bin/env bash
#
# measure-apk-size.sh -- size cost of the SmithNet WASM core ("ROM"), the M1.5
# go/no-go input. Diffs two APKs that should differ ONLY by the WASM core:
#
#   baseline = the experiment branch with the core commits removed (HEAD~N)
#   variant  = the experiment branch tip, with WAMR vendored + smithcore built
#
# Reports the three chosen metrics:
#   1. download size   (compressed)   -- the .apk bytes a user pulls over 2G
#   2. install size    (uncompressed) -- on-device storage after install
#   3. native footprint per ABI       -- libsmithcore_jni.so + assets/smithcore.wasm
#
# Stock tools only (apkanalyzer is NOT installed): bash + unzip + stat.
#
# Build the two APKs from CLEAN worktrees so the only difference is the core
# (this sidesteps any unrelated uncommitted work in the main tree):
#
#   # variant = experiment tip (core present); vendor WAMR for a real native number
#   git worktree add /tmp/wt-variant experiment/smithcore-rom
#   git clone --depth 1 https://github.com/bytecodealliance/wasm-micro-runtime \
#       /tmp/wt-variant/android/app/src/main/cpp/wamr
#   ( cd /tmp/wt-variant/android && ./gradlew :app:assembleRelease )
#
#   # baseline = the commit BEFORE the core commit (no core at all)
#   git worktree add /tmp/wt-baseline experiment/smithcore-rom~1
#   ( cd /tmp/wt-baseline/android && ./gradlew :app:assembleRelease )
#
#   tools/measure-apk-size.sh \
#     /tmp/wt-baseline/android/app/build/outputs/apk/release/app-release-unsigned.apk \
#     /tmp/wt-variant/android/app/build/outputs/apk/release/app-release-unsigned.apk
#
#   git worktree remove /tmp/wt-variant && git worktree remove /tmp/wt-baseline
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <baseline.apk> <variant.apk>" >&2
  exit 2
fi
BASE="$1"; VAR="$2"
for f in "$BASE" "$VAR"; do
  [[ -f "$f" ]] || { echo "ERROR: not found: $f" >&2; exit 1; }
done

ABIS=("arm64-v8a" "armeabi-v7a")

# portable file size in bytes
fsize() { wc -c < "$1" | tr -d ' '; }

# sum uncompressed (col1) + compressed (col3) of `unzip -v` entries whose name
# (col8) matches the awk regex; prints "<uncompressed> <compressed>"
sum_match() { # apk regex
  unzip -v "$1" 2>/dev/null | awk -v pat="$2" '$8 ~ pat { u += $1; c += $3 } END { printf "%d %d", u+0, c+0 }'
}

# total uncompressed (install size) from the unzip -l footer
install_size() { unzip -l "$1" 2>/dev/null | tail -1 | awk '{print $1+0}'; }

human() { # bytes -> human
  awk -v b="$1" 'BEGIN{ s="B K M G"; split(s,u," "); i=1; v=b; while(v>=1024 && i<4){v/=1024;i++} printf (i==1?"%d %s":"%.1f %s"), v, u[i] }'
}

row() { printf "  %-34s %12s  %12s\n" "$1" "$2" "$3"; }
delta() { # label base var
  local d=$(( $3 - $2 )); local sign="+"; (( d < 0 )) && sign=""
  local pct="n/a"; (( $2 > 0 )) && pct=$(awk -v d="$d" -v b="$2" 'BEGIN{printf "%+.2f%%", 100*d/b}')
  printf "  %-34s %12s  %12s   %s%s (%s)\n" "$1" "$(human $2)" "$(human $3)" "$sign" "$(human $d | sed 's/^/ /;s/^ //')" "$pct"
}

echo "==================================================================="
echo " SmithNet WASM core -- APK size delta"
echo "   baseline: $BASE"
echo "   variant : $VAR"
echo "==================================================================="

# ---- 1 & 2: whole-APK download + install ----
b_dl=$(fsize "$BASE"); v_dl=$(fsize "$VAR")
b_in=$(install_size "$BASE"); v_in=$(install_size "$VAR")

echo
echo "WHOLE APK                                  baseline       variant      delta"
echo "-------------------------------------------------------------------------------"
delta "download size (compressed)"   "$b_dl" "$v_dl"
delta "install size  (uncompressed)" "$b_in" "$v_in"

# ---- 3: native footprint per ABI ----
echo
echo "NATIVE LIBS (uncompressed, per ABI)        baseline       variant      delta"
echo "-------------------------------------------------------------------------------"
for abi in "${ABIS[@]}"; do
  read bu _ <<<"$(sum_match "$BASE" "lib/${abi}/")"
  read vu _ <<<"$(sum_match "$VAR"  "lib/${abi}/")"
  delta "lib/${abi}/* total" "$bu" "$vu"
  read bs _ <<<"$(sum_match "$BASE" "lib/${abi}/libsmithcore_jni.so")"
  read vs _ <<<"$(sum_match "$VAR"  "lib/${abi}/libsmithcore_jni.so")"
  delta "  - libsmithcore_jni.so" "$bs" "$vs"
done

# ---- the ROM asset (shared, counted once) ----
echo
echo "ROM ASSET                                  baseline       variant      delta"
echo "-------------------------------------------------------------------------------"
read bw _ <<<"$(sum_match "$BASE" "assets/smithcore.wasm")"
read vw _ <<<"$(sum_match "$VAR"  "assets/smithcore.wasm")"
delta "assets/smithcore.wasm" "$bw" "$vw"

echo
echo "Note: a meaningful native delta requires WAMR vendored in the variant"
echo "      (android/app/src/main/cpp/wamr); without it libsmithcore_jni.so is"
echo "      only the JNI stub. Baseline must be the variant MINUS the core."
echo "==================================================================="
