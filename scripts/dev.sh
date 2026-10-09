#!/usr/bin/env bash
# Pluto dev loop on a local, headless Android emulator.
#
#   scripts/dev.sh doctor              check SDK, KVM, AVD, emulator state
#   scripts/dev.sh emu [start|stop|restart|status] [--wipe]
#   scripts/dev.sh build [debug|release]
#   scripts/dev.sh install [debug|release]   (builds first)
#   scripts/dev.sh run [debug|release]       build + install + launch
#   scripts/dev.sh launch | stop | home | clear
#   scripts/dev.sh shot [file.png]     screenshot (default: captures/shot-<time>.png)
#   scripts/dev.sh rotate [portrait|landscape|auto]
#   scripts/dev.sh key <name...>       send key events, e.g. key DPAD_DOWN BUTTON_A
#   scripts/dev.sh pad <name...>       same, injected with the gamepad input source
#   scripts/dev.sh tap <x> <y> | swipe <x1> <y1> <x2> <y2> [ms] | text <string>
#   scripts/dev.sh ui                  on-screen text + bounds (uiautomator dump)
#   scripts/dev.sh logcat              Pluto's process only
#   scripts/dev.sh setup               (re)install SDK packages, JDK 17, KVM access, AVD
#   scripts/dev.sh test | atest        JVM unit tests | instrumented tests on the emulator
#
# Everything targets the emulator serial ($PLUTO_SERIAL, default emulator-5554),
# never a plugged-in phone: connected tests uninstall the app and delete its data.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD="${PLUTO_AVD:-pluto36}"
PORT="${PLUTO_EMU_PORT:-5554}"
export ANDROID_SERIAL="${PLUTO_SERIAL:-emulator-$PORT}"
PKG="dev.pluto.launcher"
EMU_LOG="${TMPDIR:-/tmp}/pluto-emulator-$PORT.log"
ADB=(adb -s "$ANDROID_SERIAL")

die() { echo "error: $*" >&2; exit 1; }
running() { "${ADB[@]}" get-state >/dev/null 2>&1; }
booted() { [[ "$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; }

wait_boot() {
  local t=0
  timeout 180 "${ADB[@]}" wait-for-device || die "device never connected (see $EMU_LOG)"
  until booted; do
    (( t++ > 180 )) && die "emulator did not finish booting (see $EMU_LOG)"
    sleep 1
  done
  # Keep the screen awake and animations predictable for testing.
  "${ADB[@]}" shell svc power stayon true >/dev/null 2>&1 || true
  "${ADB[@]}" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "${ADB[@]}" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

emu_start() {
  if running; then echo "$ANDROID_SERIAL already running"; wait_boot; return; fi
  [[ -r /dev/kvm && -w /dev/kvm ]] || die "/dev/kvm not accessible (sudo setfacl -m u:$USER:rw /dev/kvm)"
  local extra=()
  [[ "${1:-}" == "--wipe" ]] && extra+=(-wipe-data)
  echo "starting $AVD on port $PORT (log: $EMU_LOG)"
  setsid nohup emulator -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
    -gpu "${PLUTO_GPU:-swangle}" -feature -Vulkan -no-snapshot-save "${extra[@]}" >"$EMU_LOG" 2>&1 < /dev/null &
  wait_boot
  echo "booted: $("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r') (API $("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r'))"
}

emu_stop() {
  running || { echo "not running"; return; }
  "${ADB[@]}" emu kill >/dev/null 2>&1 || true
  for _ in $(seq 30); do running || { echo "stopped"; return; }; sleep 1; done
  pkill -f "emulator.*-avd $AVD.*-port $PORT" || true
}

variant() { case "${1:-debug}" in debug) echo Debug;; release) echo Release;; *) die "variant: debug|release";; esac; }
apk() { echo "$ROOT/app/build/outputs/apk/${1:-debug}/app-${1:-debug}.apk"; }

build() { (cd "$ROOT" && ./gradlew --console=plain ":app:assemble$(variant "${1:-debug}")"); }

install() {
  build "${1:-debug}"
  running || emu_start
  "${ADB[@]}" install -r "$(apk "${1:-debug}")"
}

launch() {
  "${ADB[@]}" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 \
    || die "could not launch $PKG"
}

cmd="${1:-help}"; shift || true
case "$cmd" in
  doctor)
    echo "ANDROID_HOME=$ANDROID_HOME"
    java -version 2>&1 | head -1
    echo "adb: $(adb version | head -1)"
    echo "emulator: $(emulator -version 2>/dev/null | head -1)"
    [[ -r /dev/kvm && -w /dev/kvm ]] && echo "kvm: ok" || echo "kvm: NOT accessible"
    echo "avds: $(emulator -list-avds | tr '\n' ' ')"
    [[ -f "$ROOT/local.properties" ]] && echo "local.properties: $(cat "$ROOT/local.properties")" || echo "local.properties: missing"
    running && echo "device $ANDROID_SERIAL: $(booted && echo booted || echo booting)" || echo "device $ANDROID_SERIAL: not running"
    ;;
  emu)
    case "${1:-start}" in
      start) emu_start "${2:-}";;
      stop) emu_stop;;
      restart) emu_stop; emu_start "${2:-}";;
      status) adb devices -l;;
      *) die "emu start|stop|restart|status";;
    esac;;
  build) build "${1:-debug}";;
  install) install "${1:-debug}";;
  run) install "${1:-debug}"; "${ADB[@]}" shell am force-stop "$PKG"; launch;;
  launch) launch;;
  stop) "${ADB[@]}" shell am force-stop "$PKG";;
  clear) "${ADB[@]}" shell pm clear "$PKG";;
  home) "${ADB[@]}" shell input keyevent KEYCODE_HOME;;
  shot)
    out="${1:-$ROOT/captures/shot-$(date +%Y%m%d-%H%M%S).png}"
    mkdir -p "$(dirname "$out")"
    "${ADB[@]}" exec-out screencap -p > "$out"
    echo "$out";;
  rotate)
    case "${1:-}" in
      portrait)  "${ADB[@]}" shell settings put system accelerometer_rotation 0; "${ADB[@]}" shell settings put system user_rotation 0;;
      landscape) "${ADB[@]}" shell settings put system accelerometer_rotation 0; "${ADB[@]}" shell settings put system user_rotation 1;;
      auto)      "${ADB[@]}" shell settings put system accelerometer_rotation 1;;
      *) die "rotate portrait|landscape|auto";;
    esac;;
  key)
    [[ $# -gt 0 ]] || die "key <KEYCODE...>"
    for k in "$@"; do k="${k#KEYCODE_}"; "${ADB[@]}" shell input keyevent "KEYCODE_$k"; done;;
  pad)
    # Same as key, but injected with the gamepad input source.
    [[ $# -gt 0 ]] || die "pad <KEYCODE...>"
    for k in "$@"; do k="${k#KEYCODE_}"; "${ADB[@]}" shell input gamepad keyevent "KEYCODE_$k"; done;;
  tap) "${ADB[@]}" shell input tap "$@";;
  swipe) "${ADB[@]}" shell input swipe "$@";;
  text) "${ADB[@]}" shell input text "${1// /%s}";;
  logcat)
    pid="$("${ADB[@]}" shell pidof "$PKG" | tr -d '\r')"
    [[ -n "$pid" ]] || die "$PKG is not running"
    "${ADB[@]}" logcat --pid="$pid";;
  ui)
    # Visible text / content descriptions with their bounds (tap targets).
    # Never read the previous dump if UiAutomator couldn't get a live root.
    dumpPath="/sdcard/pluto-ui-${BASHPID}-${RANDOM}.xml"
    dumpResponse="$("${ADB[@]}" shell uiautomator dump "$dumpPath" 2>&1)"
    [[ "$dumpResponse" == *"$dumpPath"* ]] || die "UiAutomator could not get a live root: $dumpResponse"
    xml="$("${ADB[@]}" exec-out cat "$dumpPath")"
    [[ "$xml" == *'<hierarchy'* ]] || die "UiAutomator returned no valid hierarchy"
    printf '%s' "$xml" | tr '>' '\n' | perl -ne '
      next unless /bounds="([^"]*)"/; my $b = $1;
      my ($t) = / text="([^"]*)"/; my ($d) = / content-desc="([^"]*)"/;
      my $l = join(" | ", grep { defined && length } $t, $d);
      print "$b  $l\n" if length $l and !$seen{"$b$l"}++;';;
  setup)
    # Reproduce the SDK + AVD install (idempotent). Needs sudo for JDK 17 and /dev/kvm.
    command -v sdkmanager >/dev/null || die "install cmdline-tools to $ANDROID_HOME/cmdline-tools/latest first"
    [[ -d /usr/lib/jvm/java-17-openjdk-amd64 ]] || sudo apt-get install -y openjdk-17-jdk-headless
    [[ -r /dev/kvm && -w /dev/kvm ]] || { sudo usermod -aG kvm "$USER"; sudo setfacl -m "u:$USER:rw" /dev/kvm; }
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0" \
      "build-tools;35.0.0" "system-images;android-36;google_apis;x86_64"
    echo "sdk.dir=$ANDROID_HOME" > "$ROOT/local.properties"
    if ! emulator -list-avds | grep -qx "$AVD"; then
      echo no | avdmanager create avd -n "$AVD" -k "system-images;android-36;google_apis;x86_64" -d pixel_7
      cat >> "$HOME/.android/avd/$AVD.avd/config.ini" <<'EOF'
hw.lcd.width=1080
hw.lcd.height=2412
hw.lcd.density=440
hw.ramSize=4096
hw.cpu.ncore=4
hw.keyboard=yes
hw.dPad=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
disk.dataPartition.size=6G
EOF
    fi
    echo "setup done; run: scripts/dev.sh emu start";;
  test) (cd "$ROOT" && ./gradlew --console=plain testDebugUnitTest);;
  atest) running || emu_start; (cd "$ROOT" && ./gradlew --console=plain connectedDebugAndroidTest);;
  help|-h|--help) sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//';;
  *) die "unknown command '$cmd' (try: scripts/dev.sh help)";;
esac
