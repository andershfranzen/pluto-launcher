# Controller test plan and results

Hardware testing is mandatory for controller mappings, reconnect behaviour and Handheld mode;
an emulator cannot certify them. This document is the plan plus a results template for the
reference setup: **OnePlus Nord 5 + GameSir controller**.

**Status: no hardware results recorded yet.** Every observation below is blank or "to be
recorded on hardware". Do not fill in values from datasheets or other devices; record what
the device actually reports.

## 1. Setup record

| Item | Value |
|------|-------|
| Date | _to be recorded on hardware_ |
| Tester | _to be recorded on hardware_ |
| Phone model | OnePlus Nord 5 |
| OxygenOS build (`adb shell getprop ro.build.display.id`) | _to be recorded on hardware_ |
| Android version / API level | _to be recorded on hardware_ |
| Pluto build (versionName + git sha) | _to be recorded on hardware_ |
| Controller model (exact name on box / device) | _to be recorded on hardware_ |
| Controller firmware version | _to be recorded on hardware_ |
| Controller mode switch (e.g. Android / X-input / iOS / Switch) | _to be recorded on hardware_ |
| Connection type (Bluetooth / USB-C direct / clip) | _to be recorded on hardware_ |
| Name reported by Android | _to be recorded on hardware_ |
| Vendor ID / Product ID | _to be recorded on hardware_ |
| InputDevice descriptor (stable across reconnects?) | _to be recorded on hardware_ |
| Reported sources (hex) | _to be recorded on hardware_ |
| Classified as controller by Pluto (yes/no) | _to be recorded on hardware_ |

How to collect device details:

```sh
adb shell dumpsys input | sed -n '/Input Device/,/^$/p'   # name, descriptor, sources, vendor/product, axes + flat/fuzz
adb shell getevent -lt                                    # raw kernel events while pressing buttons
```

Pluto's own controller diagnostics screen (in Settings) shows the Android key code for each
press and live axis values; use it as the primary source for the tables below and
`getevent` as a cross-check.

## 2. Button key codes

Record the Android key code Pluto receives (name and number) for each physical control.
Physical labels vary by vendor, so note what is printed on the button.

| Physical control (label printed) | Expected default action | Observed KeyEvent code | Repeats while held? | Also produces axis events? | Notes |
|----------------------------------|-------------------------|------------------------|---------------------|----------------------------|-------|
| Bottom face button | Confirm (`BUTTON_A`) | _to be recorded on hardware_ | | | |
| Right face button | Back (`BUTTON_B`) | _to be recorded on hardware_ | | | |
| Left face button | App actions (`BUTTON_X`) | _to be recorded on hardware_ | | | |
| Top face button | Search (`BUTTON_Y`) | _to be recorded on hardware_ | | | |
| Left shoulder (L1/LB) | Previous category (`BUTTON_L1`) | _to be recorded on hardware_ | | | |
| Right shoulder (R1/RB) | Next category (`BUTTON_R1`) | _to be recorded on hardware_ | | | |
| Left trigger (L2/LT) | none | _to be recorded on hardware_ | | | |
| Right trigger (R2/RT) | none | _to be recorded on hardware_ | | | |
| Start / Menu / Options | Settings (`BUTTON_START`) | _to be recorded on hardware_ | | | |
| Select / Back / View | none | _to be recorded on hardware_ | | | |
| Home / Guide / Logo | system (`BUTTON_MODE`, not consumed, cannot be remapped) | _to be recorded on hardware_ | | | |
| Left stick click (L3) | none | _to be recorded on hardware_ | | | |
| Right stick click (R3) | none | _to be recorded on hardware_ | | | |
| D-pad up | Move up | _to be recorded on hardware_ | | | |
| D-pad down | Move down | _to be recorded on hardware_ | | | |
| D-pad left | Move left | _to be recorded on hardware_ | | | |
| D-pad right | Move right | _to be recorded on hardware_ | | | |
| Extra buttons (screenshot, M1/M2, turbo, ...) | none | _to be recorded on hardware_ | | | |

## 3. Axes

| Axis | Physical control | Observed range | Reported flat (neutral) | Resting noise observed | Notes |
|------|------------------|----------------|-------------------------|------------------------|-------|
| `AXIS_X` | Left stick horizontal | _to be recorded on hardware_ | | | |
| `AXIS_Y` | Left stick vertical | _to be recorded on hardware_ | | | |
| `AXIS_Z` / `AXIS_RX` | Right stick horizontal | _to be recorded on hardware_ | | | |
| `AXIS_RZ` / `AXIS_RY` | Right stick vertical | _to be recorded on hardware_ | | | |
| `AXIS_HAT_X` | D-pad horizontal (if reported as axis) | _to be recorded on hardware_ | | | |
| `AXIS_HAT_Y` | D-pad vertical (if reported as axis) | _to be recorded on hardware_ | | | |
| `AXIS_LTRIGGER` / `AXIS_BRAKE` | Left trigger | _to be recorded on hardware_ | | | |
| `AXIS_RTRIGGER` / `AXIS_GAS` | Right trigger | _to be recorded on hardware_ | | | |

Does the D-pad arrive as keys, as HAT axes, or both? _to be recorded on hardware_

## 4. Dead zone and repeat tuning

Defaults: dead zone 0.25, repeat delay 350 ms, repeat interval 100 ms. Pluto uses the larger
of the configured dead zone and the device-reported flat.

| Check | Procedure | Observation |
|-------|-----------|-------------|
| Drift at rest | Leave the stick untouched for 60 s on the home grid | _to be recorded on hardware_ |
| Smallest deliberate push that moves | Push slowly until one step happens; read magnitude in diagnostics | _to be recorded on hardware_ |
| Single flick = single step | 20 quick flicks in each direction | _to be recorded on hardware_ |
| Hold to repeat | Hold for 3 s; count steps (expect about 1 + (3000-350)/100) | _to be recorded on hardware_ |
| Diagonals | Push to each diagonal 10 times; record which direction wins and any flicker | _to be recorded on hardware_ |
| Stick near the edge of a list | Hold toward the end of a row/list; no wrap or overshoot | _to be recorded on hardware_ |
| Recommended dead zone for this controller | | _to be recorded on hardware_ |
| Recommended repeat delay / interval | | _to be recorded on hardware_ |

## 5. Behaviour tests

| # | Test | Procedure | Expected | Observed | Pass/Fail |
|---|------|-----------|----------|----------|-----------|
| C01 | Detection at startup | Connect, then force-stop and open Pluto | Controller listed; Handheld in landscape | _to be recorded on hardware_ | |
| C02 | Hot attach | Connect while Pluto is in front (landscape) | Switches to Handheld without restart; selection kept | _to be recorded on hardware_ | |
| C03 | Hot detach | Disconnect while in Handheld | Touch Landscape layout immediately; hints dismissed; selection kept | _to be recorded on hardware_ | |
| C04 | Reconnect x20 | Toggle connection 20 times | Correct mode every time; no restart (pid unchanged) | _to be recorded on hardware_ | |
| C05 | Attach during a game | Connect while a game is in front | Game not interrupted or rotated | _to be recorded on hardware_ | |
| C06 | Portrait with controller | Connect in portrait | Phone mode, controller navigation works | _to be recorded on hardware_ | |
| C07 | Keyboard is not a controller | Pair a Bluetooth/USB keyboard | No Handheld mode; arrow keys behave as keyboard | _to be recorded on hardware_ | |
| C08 | Key + axis duplicate | Press each D-pad direction 20 times | Exactly one step per press | _to be recorded on hardware_ | |
| C09 | Release-to-confirm | Hold A in a game, press Home, release A in Pluto | Nothing launches | _to be recorded on hardware_ | |
| C10 | Back layering | Open drawer > search > keyboard, press B repeatedly | Keyboard, search, drawer close in order; then stays at home | _to be recorded on hardware_ | |
| C11 | Remap | Remap Confirm to another button, reconnect | Mapping persists and follows the controller after reconnect | _to be recorded on hardware_ | |
| C12 | Descriptor stability | Reconnect over Bluetooth and over USB | Same descriptor? Per-controller mapping still applied? | _to be recorded on hardware_ | |
| C13 | Home / Recents buttons | Press the controller's system buttons | Never consumed or remapped by Pluto | _to be recorded on hardware_ | |
| C14 | Touch / controller switching | Alternate 50 times | No focus trap, no unintended activation | _to be recorded on hardware_ | |
| C15 | Focus visibility latency | Observe focus after each D-pad press | Visible within 100 ms (measure per ACCEPTANCE.md) | _to be recorded on hardware_ | |
| C16 | Rotation preference "Landscape when controller connected" | Enable, connect / disconnect | Pluto window rotates to landscape only while connected; launched apps unaffected | _to be recorded on hardware_ | |

## 6. Issues found

| Date | Test | Description | Severity | Workaround | Tracking |
|------|------|-------------|----------|------------|----------|
| | | _to be recorded on hardware_ | | | |
