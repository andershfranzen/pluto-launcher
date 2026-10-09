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
- **OxygenOS specifics** (the wording of default-app settings, gesture navigation edge
  cases) have not been checked. Launch animations were checked: see below.
- Only emulator/JVM testing is possible in CI; migration tests need a device or emulator
  (`connectedDebugAndroidTest`).

## Motion and performance (measured on the Nord 5, 144 Hz)

- **App launch animation on OxygenOS**: OxygenOS 16 ignores the launch animation a
  third-party launcher asks for (`makeScaleUpAnimation` and `makeClipRevealAnimation` were
  both tried) and fades the app in full screen. Pluto grows a veil from the tapped tile
  first, which delays the launch by about 110 ms; the return-to-home animation is the
  system's own slide.
- **Debug builds are slow**: a debuggable build runs largely interpreted. On the reference
  phone the drawer's opening frame takes about 40 to 60 ms in a debug build and about
  15 to 20 ms (main thread) in a release build. Use a release build for daily use.
- **Search frames that bring many apps back** (deleting the last character, Clear) no
  longer compose the grid in one frame: tiles already showing stay, and the returning ones
  join 12 per frame, top first. Not yet re-measured on the phone after this change (the
  drawer rework below); the targets in the profiling notes still apply.
- **Drawer and tiles (rework, unmeasured)**: tiles are now a few nodes each (the label is
  laid out once in its own measure pass instead of a BoxWithConstraints subcomposition, a
  cached icon is one draw node, and a tile reads its window position only when activated);
  categories share one grid; the search field recomposes on its own; the drawer no longer
  composes inside a measure pass. These were built and unit-tested only; frame times on the
  Nord 5 still need a release-build measurement.
- **After rotation, closing a full-screen page or starting**, Pluto composes the closed
  drawer in the background once the screen has been idle for about 0.6 s. On a debug build
  the first of those frames takes about 100 ms. If you open the drawer before that, the
  first opening frame is slower, as in earlier versions.
- **Rotation** itself has one long relayout frame (about 150 to 180 ms in a debug build),
  hidden by the system's rotation animation.
- With an accessibility service enabled (for example a gesture app), Compose also updates
  accessibility information after large changes such as opening the drawer, which adds
  main-thread work.
- **Polish round 1 (unmeasured on the phone)**: search swaps no longer let a kept tile
  jump into a cell whose old tile is still fading (it gets a fresh grid key and fades in
  after SWAP_MS); tile labels are centred; drawer content fades in from 5% of the reveal;
  Back with an empty search field closes the drawer after the keyboard; console mode is a
  dark immersive stage (70% scrim, dark scheme, status bar hidden, solid white selected
  tab, shorter reflection kept clear of the title, sequenced shelf switches); the clock
  follows the system minute tick. The phone was locked during this round, so T1–T7 are
  still to be measured on a release build there.
- **Polish round 2** fixed two controller focus faults. (1) A held D-pad in the Coverflow
  moved one card and then wandered through the header. When the composed window of cards
  advanced, every card was disposed and recreated, the focused one included, because the
  keyed card was not the loop body's only content (`CoverflowFocusTest`). (2) Closing a layer
  (for example the drawer opened with Y) put focus on the first tab instead of the card that
  opened it. The opener was read after Compose had already moved focus into the layer. In
  console mode, moving along the tab row now selects the shelf, as on PS5 / Xbox. Search keystrokes
  always crossfade in place instead of gliding tiles across cells, and 1 to 2 character
  queries no longer match package names ("ch" found Google and Pluto through
  `googlequicksearchbox` / `launcher`).
- **Interface polish (2026-10-09)**: the drawer now uses a genuinely translucent sheet,
  with alpha-masked scroll edges instead of solid gradient strips. Search stays in a fixed
  field above the category chips; typing no longer replaces the header. Its state-based
  editor also prevents rapid native key events from duplicating/dropping characters while
  results recompose, and pending query echoes cannot undo Clear. Back first leaves an
  active search (including an empty one), then closes the drawer. Outgoing search cells drop
  immediately so rapid typing cannot leave overlapping icons/focus targets. Settings is
  divided into Appearance, Layout, Apps and General, with pinned navigation and grouped
  controls. Choice chips reserve their check-mark slot so selecting one does not reflow the
  row. Interrupted drawer entrances, high-velocity spring overshoot and stale focus IDs
  have regression tests. These changes still need release-build frame-time measurements
  and a visual/controller review on the reference phone; earlier timings above are not
  measurements of this revision.
- **Reorder in Edit**: rows that swap places slide past each other, so for a moment one row
  passes over the other's controls.

## Deliberate deviations from the product spec

- **No "All apps" button on Phone and Landscape home.** The spec says "Swipe up or tap All
  apps to open the drawer"; at the user's request the button (and the empty-home "All
  apps" button) is gone. The drawer stays reachable without gestures in one action through
  the header's **Search** button (it opens the drawer with the keyboard up), whose screen
  reader action and long press named "All apps" open it without the keyboard, and through
  controller Y. Swipe up anywhere on home still pulls the drawer up with the finger.
- **The drawer's "Actions" toggle is a ⋮ beside its title.** Long press (touch), X
  (controller) and the "App actions" screen-reader action also reach an app's actions.
  The ⋮ remains visible while searching (except in the keyboard-compact landscape header),
  and turns on the mode in which tapping an app opens its actions.
- **The alphabet fast-scroll rail is touch-only** and hidden from screen readers: it is a
  shortcut, and the grid itself remains fully reachable by scrolling, D-pad and TalkBack.

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

## Measured on the reference phone (2026-10-08)

OnePlus Nord 5, Android 16, display at 144 Hz (6.9 ms frame budget), release build installed
with its baseline profile (`speed-profile`), about 90 launcher apps, `dumpsys gfxinfo` reset
per scenario, input injected with `adb shell input`. Lawnchair on the same phone and script
is given for reference.

| Scenario | Janky frames | p95 | p99 | Target |
| --- | --- | --- | --- | --- |
| Drawer fling scroll (All apps) | 1.3–1.8% | 11–13 ms | 16–23 ms | < 1%, p99 ≤ 13.9 ms |
| Lawnchair drawer, same flings | 2.3–4.0% | 10–13 ms | 17 ms | (reference) |
| Drawer open + close | 4.4–5.0% | 14–17 ms | 18–19 ms | no frame > 13.9 ms |
| Search typing + Clear | 4.7–5.2% | 17 ms | 28 ms | p95 ≤ 13.9 ms |
| Console D-pad traversal | 0.8–1.5% | 17–19 ms | 22–24 ms | < 1%, p99 ≤ 13.9 ms |
| Console L1/R1 shelf switch | 1.4% | 14–15 ms | 32–38 ms | < 1%, p99 ≤ 13.9 ms |

A Perfetto trace of drawer flings shows Pluto's own work within budget (main thread
1.3 ms per frame on average, RenderThread 4.1 ms, 6 of 595 frames over 6.9 ms, none over
14 ms); the remaining spikes are GPU buffer allocations when the overscroll stretch starts
and about 1 ms per frame of accessibility-tree updates (an accessibility service is enabled
on the reference phone). Still to do: each console shelf switch composes the incoming shelf
in one ~30 ms main-thread frame, and search keystrokes that bring many apps back still cost
20–35 ms.

## Console mode (Coverflow) polish still to do

- Side cards are tilted 38° (was 55°) so round icons stay round; card faces still vary
  slightly in tone between apps.
- Shelf switches: see the measured ~30 ms frame above.
