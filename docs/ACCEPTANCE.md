# Release 0.1 acceptance checklist

These tests gate release 0.1. They must be run on the **reference hardware** (OnePlus Nord 5
with a GameSir controller) plus at least one emulator configuration. Emulator results alone
cannot certify controller, OxygenOS Home transition, rotation or reconnect behaviour.

**No results have been recorded yet.** Every Result below is "Not yet run". When you run a
test, replace it with Pass / Fail plus the date, build (`versionName` and git sha) and a
short note or link to evidence. Report what was observed; do not round up.

## Test environment (fill in per run)

| Item | Value |
|------|-------|
| Device | OnePlus Nord 5 |
| OxygenOS / Android build (`adb shell getprop ro.build.display.id`) | _to be recorded_ |
| Android version / API (`adb shell getprop ro.build.version.release` / `.sdk`) | _to be recorded_ |
| Pluto build (versionName, git sha) | _to be recorded_ |
| Controller model and firmware | _to be recorded_ |
| Connection type (Bluetooth / USB) | _to be recorded_ |
| Installed launchable apps (approx. 200 for performance runs) | _to be recorded_ |
| Display refresh rate during measurement | _to be recorded_ |
| Emulator configuration (image, API, size) | _to be recorded_ |

Count launchable apps:

```sh
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep -c 'packageName='
```

## Functional acceptance tests

| ID | Acceptance test | How to run | Result |
|----|-----------------|------------|--------|
| A01 | Set as default Home; press Home from another app and return successfully after reboot. | Complete onboarding and select Pluto in the system Home chooser. Open another app, press Home: Pluto appears. `adb reboot`; after unlock Pluto is the Home screen; open an app and press Home again. Repeat on gesture and 3-button navigation. Check `adb shell cmd role get-role-holders android.app.role.HOME` lists `dev.pluto.launcher`. | Not yet run |
| A02 | Rotate both directions 20 times across home, drawer, search, and folder; preserve state and avoid clipping. | With auto-rotate on, open each surface in turn (home with a selected app, drawer scrolled halfway, search with text typed, an open folder). Rotate portrait -> landscape (left) -> portrait -> landscape (right) 20 times per surface. After each rotation verify: same layer open, search text kept, active category kept, same app selected, scroll position near the same item, nothing clipped behind cutout / nav bar, no stretched layout. Optionally script: `adb shell settings put system accelerometer_rotation 0` then `adb shell settings put system user_rotation 0/1/2/3`. | Not yet run |
| A03 | Connect and disconnect the GameSir 20 times; select expected mode without interrupting apps or losing focus. | In landscape: toggle the controller (Bluetooth power or USB cable) 20 times while Pluto is in front, and 5 times while a game is in front. Expect Handheld when connected and Landscape when not, no Pluto restart (check `adb shell pidof dev.pluto.launcher` is unchanged), selection kept, button legend shown/hidden. The foreground game must not be interrupted or rotated. Repeat 5 times in portrait (stays Phone). | Not yet run |
| A04 | Launch apps, open folders, change category, search, edit favourites, and exit menus with controller alone. | Without touching the screen: move focus, launch an app (A), return with Home, open a folder and launch from it, change category with L1/R1, open search (Y) and type with the system keyboard, open app actions (X) and pin/unpin, enter Edit and move an item up/down/before/after, open Settings (Start) and change a setting, and leave every layer with B. Note any control that cannot be reached. | Not yet run |
| A05 | Install, update, and uninstall apps while launcher is running and suspended; reconcile without stale launch targets. | With Pluto in front: `adb install` a test APK (appears), `adb install -r` an updated build (stays, keeps pins/dock/folder/categories), `adb uninstall` it (disappears from grid, dock, folders, categories, recents, search; focus moves to a neighbour). Repeat each while another app is in front, then return Home. Also `adb shell pm suspend <pkg>` / `unsuspend` and `pm disable-user` / `enable`. No tile may launch a missing app; a failed launch shows a readable message. | Not yet run |
| A06 | Kill the process and restart the phone; preserve dock, folders, categories, preferences, and history policy. | Set up a dock, two folders, a custom category with members, a hidden app, non-default theme/icon size/rotation, and history disabled. `adb shell am force-stop dev.pluto.launcher`, press Home: all preserved. Background Pluto and run `adb shell am kill dev.pluto.launcher` to check session restore (open layer, search text). Reboot: all preserved, history still disabled and empty. | Not yet run |
| A07 | Switch repeatedly between touch and controller; no focus trap, duplicate launch, or unintended activation. | Alternate touch and controller input at least 50 times across home, drawer, folder, settings. After touch, the first controller movement must only restore focus (no movement, no activation). Press A once per launch and verify exactly one app start (`adb logcat -s ActivityTaskManager` shows one `START`). Hold A while leaving a game with Home: nothing launches. Press D-pad diagonals and stick + D-pad together: one step per press. | Not yet run |
| A08 | Test large text, TalkBack, dark/light themes, keyboard visibility, cutout insets, and gesture/button navigation. | Set font size and display size to maximum (`adb shell settings put system font_scale 1.3`, or larger where supported) plus Pluto text scale 1.5: no clipped labels. Enable TalkBack: every app, folder, dock slot and control has a label and a logical reading order; all actions reachable without long-press. Switch Pluto and system theme dark/light. Open search with the keyboard: focused field and results not hidden. Check both landscape directions against the camera cutout. Repeat with gesture and 3-button navigation. | Not yet run |
| A09 | Disable history and confirm removal and no further collection; unsupported private profiles never leak into lists. | Launch several apps; Recent launches shows them. Disable history: row disappears; launch more apps; re-enable: list is empty (no collection while disabled). On a debuggable build: `adb shell run-as dev.pluto.launcher sqlite3 databases/launcher.db 'select count(*) from recent_launch'` returns 0 after disabling (if sqlite3 is available). Create a work profile (e.g. Test DPC) and, on Android 15+, a Private Space; confirm none of their apps appear in home, drawer, search, categories, history, and that the unsupported-profile notice is shown. | Not yet run |
| A10 | Complete seven days of mixed phone and handheld use; record failures and resolve blocking issues. | Use Pluto as the only Home app for seven consecutive days including daily phone use and controller gaming. Keep a daily log (date, what was done, failures, workarounds needed). Any task needing another launcher counts as a failure. Collect `adb bugreport` for any crash or ANR. | Not yet run |

## Performance and reliability targets

Provisional engineering targets on the reference phone with about 200 app entries. Report
observed values with the measurement method; these are not claims of existing performance.

| Target | Threshold | How to measure | Observed | Result |
|--------|-----------|----------------|----------|--------|
| Warm Home return to interactive layout | <= 300 ms | Open another app, then `adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME` (record `TotalTime` / `WaitTime`), 30 runs. Cross-check with a Perfetto trace from Home key press to first frame after `reportFullyDrawn` / the first fully drawn frame. | _to be recorded_ | Not yet run |
| Cold process start | <= 1.5 s in at least 95% of 30 runs (excluding system boot) | `adb shell am force-stop dev.pluto.launcher` then `adb shell am start -W -n dev.pluto.launcher/.MainActivity`, 30 runs; report p50/p95 of `TotalTime`. | _to be recorded_ | Not yet run |
| Local search update | <= 100 ms p95 after input | Perfetto trace with app tracing enabled while typing 30 queries; measure from input event to the frame showing updated results. | _to be recorded_ | Not yet run |
| Controller focus response | visible within 100 ms under ordinary browsing | Perfetto trace including input events (`android.input.inputevent` data source or `input` atrace category): key event to frame showing the new focus outline; 50 presses. A high-speed camera is an acceptable cross-check. | _to be recorded_ | Not yet run |
| Mode reflow | settles within 250 ms after configuration delivery | Perfetto trace across rotation and controller attach: from configuration change / input device added to the last frame of the new layout. | _to be recorded_ | Not yet run |
| No main-thread DB or icon decoding during navigation | none observed | Enable StrictMode in a debug build or inspect Perfetto main-thread slices for SQLite / bitmap decode while navigating. | _to be recorded_ | Not yet run |
| Ten-minute browsing and rotation run | < 1% frames above 32 ms | `adb shell dumpsys gfxinfo dev.pluto.launcher reset`, run the scripted session (scrolling, focus moves, 20+ rotations), then `adb shell dumpsys gfxinfo dev.pluto.launcher framestats`. Compute the share of frames with total duration > 32 ms. Record the refresh rate (`adb shell dumpsys display \| grep -i refresh`) and the method. | _to be recorded_ | Not yet run |
| No crashes, ANRs, persistent focus traps or configuration loss across the suite | zero | `adb logcat -b crash`, `adb shell dumpsys dropbox --print \| grep -A3 dev.pluto.launcher`, plus the A01-A10 notes. | _to be recorded_ | Not yet run |
| No unnecessary wake locks or background polling | none | `adb shell dumpsys power \| grep -i pluto` (no wake locks), `adb shell dumpsys alarm \| grep pluto` and `adb shell dumpsys jobscheduler \| grep pluto` (no alarms/jobs); Battery Historian on a bugreport for a day of use. | _to be recorded_ | Not yet run |

### Capturing a Perfetto trace

```sh
# 20 s trace with graphics, input, view and app sections
adb shell perfetto -o /data/misc/perfetto-traces/pluto.perfetto-trace -t 20s \
    sched freq gfx view input am wm dalvik
adb pull /data/misc/perfetto-traces/pluto.perfetto-trace
# Open in https://ui.perfetto.dev (runs locally in the browser)
```

### Automated checks

These do not replace the hardware tests above, but should pass before any run:

```sh
./gradlew testDebugUnitTest            # mode resolution, search, organisation rules, input repeat, dedupe
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest    # emulator only: uninstalls the app (and its data) afterwards
```
