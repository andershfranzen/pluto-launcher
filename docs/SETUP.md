# Setting up Pluto

A short guide to installing Pluto, making it your Home app, switching back, and using a
game controller with it.

## 1. Install

Build the debug APK (see the [README](../README.md#building)) and install it:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` keeps your existing Pluto data when you install a newer build. Pluto needs Android 11
(API 30) or later. It does not ask for any runtime permissions.

## 2. First launch

Open Pluto from your current launcher's app list (it appears as "Pluto" with a small
planet icon). The first launch:

1. Loads your apps and shows a **usable default layout** straight away: alphabetical drawer,
   empty favourites, empty dock, and the built-in categories All apps, Games and Tools.
2. Offers **optional category setup**, for example ticking which apps are games. You can skip
   it and do this later from any app's actions menu (Category membership).
3. Shows a **controller navigation preview** with the default buttons (see below). It works
   with touch too; no controller is needed.
4. Only then offers to make Pluto your **default Home app**. Pluto does not ask before it has
   shown and launched your apps successfully, and you can skip this step.

## 3. Setting Pluto as the default Home app

When you accept, Pluto opens Android's own **default Home app chooser** (the system
role-request dialog). Select Pluto and confirm. Pluto cannot change this setting itself.

You can also do it any time:

- Press the Home button and pick Pluto, if Android asks which Home app to use, or
- Open Pluto Settings and choose the default Home option, or
- Use Android Settings as described in the next section.

## 4. Switching back to your previous launcher

Pluto never blocks this. In Android Settings:

**Settings ▸ Apps ▸ Default apps ▸ Home app**, then pick your previous launcher.

On OxygenOS (OnePlus) the wording and location can differ between builds, for example
*Settings ▸ Apps ▸ Default apps ▸ Home screen* or *Settings ▸ Home screen & lock screen*.
If you cannot find it, search Settings for "Home app" or "Default apps". Pluto's Settings
screen also has a shortcut that opens the system Default apps page.

Uninstalling Pluto also returns you to the system's previous or default launcher.

## 5. Pairing a controller

Pluto recognises any device Android reports as a **gamepad or joystick**, regardless of
brand. Ordinary keyboards (including ones with arrow keys) never switch Pluto into Handheld
mode.

**Bluetooth**

1. Put the controller in pairing mode (see its manual; on GameSir controllers this usually
   means holding the home/pair button until the LED flashes fast, and some models need the
   Android/"X-input" mode selected first).
2. On the phone: *Settings ▸ Bluetooth ▸ Pair new device*, then select the controller.
3. Go Home. In landscape, Pluto switches to Handheld mode within a moment; in portrait it
   stays in Phone mode with controller navigation available.

**USB / clip-on**

Connect the controller over USB-C (directly or via the clip). Android may ask whether to
allow the USB device; accept. No pairing is needed.

Connecting or disconnecting a controller never forces rotation, never restarts Pluto, and
never interrupts the app in the foreground. When the controller goes away, Pluto
immediately shows the touch presentation and keeps your selection.

Pluto Settings has a controller screen that shows connected controllers and live diagnostics (raw key codes and
axis values), which are useful when a controller behaves unexpectedly.

## 6. Controller buttons

Defaults use Android key codes, not printed letters, because face-button lettering differs
between vendors (some controllers swap A/B or X/Y).

| Input                      | Default key code(s)                       | Action                                |
|----------------------------|-------------------------------------------|---------------------------------------|
| D-pad or left stick        | DPAD_*, HAT axes, X/Y axes                | Move focus spatially                  |
| A / primary confirm        | `KEYCODE_BUTTON_A`                        | Open selected app, folder or control  |
| B / Back                   | `KEYCODE_BUTTON_B`, `KEYCODE_BACK`        | Close the current layer, or go home   |
| Left / right shoulder      | `KEYCODE_BUTTON_L1` / `KEYCODE_BUTTON_R1` | Previous / next category              |
| X / secondary              | `KEYCODE_BUTTON_X`                        | App actions for the selected app      |
| Y                          | `KEYCODE_BUTTON_Y`                        | Open search                           |
| Menu / Start               | `KEYCODE_BUTTON_START`, `KEYCODE_MENU`    | Open Pluto settings                   |

Notes:

- Buttons act when **released**, after a press that started in Pluto, so a button held while
  leaving a game does not launch something by accident. Long presses do nothing extra.
- Back closes the keyboard, search, a dialog or a folder before returning to home; at home
  Back keeps the launcher visible.
- The system Home and Recents buttons are never remapped.
- Uninstall is never bound to a controller button.
- Search opens the system keyboard; there is no on-screen controller keyboard yet.
- Touching the screen takes over immediately and hides the focus outline. The next
  controller movement brings the outline back on the last item without activating it.

### Remapping

In Pluto Settings, open the controller screen and pick an action, then press the button you want for
it. A button can only trigger one action, so assigning it removes it from any other action.
Mappings can be stored per controller (identified by Android's stable input-device
descriptor, falling back to vendor/product ID) or as the default for all controllers.
Movement (D-pad / stick) is not remappable.

### Stick tuning

Defaults: dead zone **0.25**, repeat delay **350 ms**, repeat interval **100 ms**. Pluto
also respects the neutral range the device reports. These can be adjusted in
the controller screen of Pluto Settings if the selection drifts or repeats too fast or too slowly.

## 7. Rotation options

The rotation preference in Pluto Settings affects the **launcher window only**; launched apps keep
their own orientation behaviour.

| Option                                | Behaviour                                                         |
|---------------------------------------|-------------------------------------------------------------------|
| Follow system (default)               | Uses the system auto-rotate setting                               |
| Portrait                              | Pluto stays upright (either portrait direction)                   |
| Landscape                             | Pluto stays sideways (both landscape directions)                  |
| Landscape when controller connected   | Landscape while a controller is connected, otherwise follow system |

*Handheld appearance* controls what landscape looks like:

- **Automatic** (default): Handheld with a controller, Landscape touch layout without.
- **Always**: Handheld whenever the window is landscape.
- **Never**: always the Landscape touch layout.

Portrait always uses Phone mode. The lock screen orientation is controlled by Android /
OxygenOS, not by Pluto.
