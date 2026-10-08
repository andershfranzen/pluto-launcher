# Known issues and limitations (0.1)

Pluto 0.1 is a prototype. This list is deliberately candid; please add to it when you find
something.

## Not yet validated

- **Hardware validation is pending.** The acceptance suite (A01–A10) has not been run on the
  reference OnePlus Nord 5 + GameSir setup, and no controller key codes or axis behaviour
  have been recorded. See [ACCEPTANCE.md](ACCEPTANCE.md) and
  [CONTROLLER_TESTS.md](CONTROLLER_TESTS.md).
- **Performance targets are unmeasured.** Home return, cold start, search latency, focus
  latency, reflow time and frame-time targets are goals, not results.
- **Stick defaults are untuned.** Dead zone 0.25 and repeat 350/100 ms come from the spec and
  may need adjusting per controller.
- **OxygenOS specifics** (Home transition animations, the wording of default-app settings,
  gesture navigation edge cases) have not been checked.
- Only emulator/JVM testing is possible in CI; migration tests need a device or emulator
  (`connectedDebugAndroidTest`).

## Unsupported in 0.1

- **Work profile and Private Space are not supported.** Their apps are not shown anywhere
  (home, drawer, search, categories, history) rather than being mixed into personal lists.
  Pluto discloses this when such profiles exist. Use the system or another launcher to reach
  those apps. Because of this, Pluto should not be described as a fully compatible launcher
  yet.
- **No widgets.**
- **No drag and drop.** Organising uses the explicit Edit controls (move up / down / before /
  after), which work with both touch and controller.
- **No backup or import/export.** Android backup is disabled; uninstalling Pluto or clearing
  its data loses your organisation.
- **No icon packs**, custom cover artwork, configurable grid sizes, app shortcuts or
  notification dots.
- **Games are not detected automatically**; add apps to the Games category yourself.
- **Playtime tracking, ROM indexing, emulator configuration and cloud libraries** are out of
  scope.

## Limitations by design or by platform

- **Controller text entry uses the system keyboard.** There is no on-screen keyboard tailored
  for controllers; how well the system keyboard works with a D-pad depends on the keyboard
  app.
- **Landscape lock screen** is a system capability controlled by Android / OxygenOS; Pluto
  cannot rotate the lock screen.
- **Recents, system gestures, the lock screen and launched apps' orientation** belong to the
  system. Pluto's rotation preference only affects its own window.
- **Home and Recents controller buttons are never remapped**, and Uninstall is never bound to a
  controller button.
- **Hiding apps is organisation, not security.** Hidden apps are still installed, still
  appear in system Settings and other launchers, and can be restored from the Hidden apps
  screen.
- **Recent launches only covers launches made from Pluto**; it is not system Recents and not
  playtime.
- **Controller identity**: Android's input-device descriptor is usually stable across
  reconnects, but some controllers may present differently over Bluetooth and USB, in which
  case Pluto falls back to vendor/product ID and then the default mapping.
- **Ordinary keyboards never trigger Handheld mode**, by design. Unusual devices that report
  themselves only as keyboards with game buttons may not be recognised as controllers.
- **Landscape dock**: on short landscape windows the side dock shrinks its slots to fit
  (never below 48 dp); when even that does not fit (very large text or icons) the dock moves
  to the bottom instead.
- **Search in landscape with the keyboard up**: category tabs, the hint text and the button
  legend step aside and results show as icons only, so at least one row stays visible. At
  the maximum Android font size (200%) on a phone-height landscape window the larger search
  field leaves room for only part of that row (icons about half visible on the emulator);
  hide the keyboard (Back or B) to browse the results.
- **Very large text in Handheld mode**: when the category tabs do not all fit beside the
  clock, the tab row scrolls sideways (the last tab can appear cut off at its edge) and the
  selected app's name beside the button legend may be shortened with an ellipsis.
- **Long app names at large text**: a tile label whose longest word is wider than the tile
  is drawn up to 30% smaller so the word is not split; longer names are still ellipsized.
- **Reduce motion** also affects Compose animations that are not strictly decorative (for
  example the switch thumb jumps instead of sliding). This is intended.
- **Home role request**: if Android refuses the request without asking (an earlier "Don't
  ask again"), Pluto opens Default apps settings instead; if you decline the dialog, Pluto
  explains where to choose it later.
- **Home while Pluto is in front** closes open layers (drawer, search, folder, menus). Home
  pressed while returning from another app keeps them, so you come back to the same place.
  Verified on the API 36 emulator (`HomeIntentTest`); still to be checked with the OxygenOS
  Home gesture.
- **Damaged settings**: if Pluto's settings file is corrupt it is replaced, Pluto tells you so,
  and Recent launches stays off until you turn it on again. Theme, rotation and controller
  settings return to their defaults in that case. While the file cannot be read at all, Pluto
  uses defaults with history paused and keeps retrying.
- **Unreadable database** (for example after installing an older build over a newer one):
  Pluto shows a recovery screen instead of the launcher, with Try again and a shortcut to
  choose another Home app. Your data is left untouched; installing the newer build again
  restores it.
- Database migrations are tested only for the current schema (version 1); there is no
  migration history yet to exercise.
