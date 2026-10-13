#!/usr/bin/env bash
# Capture a Perfetto trace from a connected device while Pluto starts and settles, so a
# startup or navigation problem can be read slice by slice. Never clears app data.
#
#   scripts/perfetto-capture.sh --serial emulator-5554 --out artifacts/perfetto
#
# Open the pulled trace at https://ui.perfetto.dev (it runs locally in the browser).
set -euo pipefail

serial=""
out="artifacts/perfetto"
seconds=20
runs=3
device_trace=/data/misc/perfetto-traces/pluto-startup.perfetto-trace

usage() {
    cat >&2 <<EOF
usage: $0 --serial <adb serial> [--out DIR] [--seconds N] [--runs N]

  --serial S    device to trace, e.g. emulator-5554 or 100.99.46.90:5555
  --out DIR     where to put the pulled trace (default $out)
  --seconds N   trace length in seconds (default $seconds)
  --runs N      Pluto launches inside the trace (default $runs)
EOF
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --serial) serial="$2"; shift 2 ;;
        --out) out="$2"; shift 2 ;;
        --seconds) seconds="$2"; shift 2 ;;
        --runs) runs="$2"; shift 2 ;;
        -h|--help) usage 0 ;;
        *) echo "unknown argument: $1" >&2; usage 1 ;;
    esac
done
[ -n "$serial" ] || usage 1

adb -s "$serial" wait-for-device
mkdir -p "$out"

# The categories the acceptance document asks for, plus binder/sched so wait time is visible.
adb -s "$serial" shell perfetto -o "$device_trace" -t "${seconds}s" -b 32mb \
    -a 'gfx view wm am input binder_driver sched freq idle' &
capture=$!
sleep 2
for _ in $(seq "$runs"); do
    adb -s "$serial" shell am start -W -n dev.pluto.launcher/.MainActivity \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null
    sleep 3
done
wait "$capture"
adb -s "$serial" pull "$device_trace" "$out/pluto-startup.perfetto-trace" >/dev/null
adb -s "$serial" shell rm -f "$device_trace"
echo "trace: $out/pluto-startup.perfetto-trace"
