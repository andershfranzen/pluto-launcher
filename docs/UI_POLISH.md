# Interface polish — 2026-10-09

Working-tree revision based on `0500cd3`. This is a focused drawer/search/settings pass,
not a claim that every UI issue or phone performance target is solved.

## Changed

- Drawer sheet alpha is 0.58 rather than the shared near-opaque sheet treatment. Tile
  labels keep their wallpaper contrast shadow. Scrolling edges mask the tiles' alpha
  rather than painting opaque gradients over the background.
- Search has a permanent, fixed-width field. Title/actions, search and category context
  no longer replace one another during keyboard entry. The keyboard-compact landscape
  layout retains a full-width search field. Clear/Close reserves the same trailing space.
  The editor uses atomic TextFieldState edits, so rapid native keys do not duplicate or
  lose characters while query results recompose. Older query echoes cannot rewind the
  editor or undo an immediate Clear.
- Large result changes dispose outgoing cells immediately, then fade in new cells;
  interrupted drawer row entrances finish instead of leaving invisible focusable apps.
- Settings has pinned Appearance / Layout / Apps / General navigation and grouped cards.
  Section switches start at the top. Choice checks reserve their width; steppers put
  supporting text above their controls instead of squeezing it beside them.
- Empty active search owns a Back step; predictive Back agrees. Predictive scrubbing
  starts from the actual entry position, respects Reduce motion, and owns cancellation
  of its restoration job. Moving layers guard mismatched pointer targets; their focus
  ring is hidden until the surface is essentially at rest.
- Drawer motion is bounded to [0, 1]. Reused focus nodes unregister their old ID before
  replacing it, in both focus-node implementations.

## Verification

- Debug and R8-optimised release APKs built successfully; release signature verified.
- **220 JVM tests passed**, including count-label copy and delayed search-echo / Clear regressions.
- **40 instrumented tests passed** on the API 36 x86_64 emulator, including eight new
  search, settings navigation, interrupted stagger, strong-fling and focus-ID regressions.
  The native-input regression sends a whole burst of Android key events rather than
  inserting an atomic string via Compose semantics. The existing Home-return test now
  waits for the STOPPED lifecycle state it requires instead of relying on a fixed sleep.
- Release smoke checks confirmed portrait search/filtering/clearing, Back, settings
  sections, swipe opening, and landscape drawer reflow via live hierarchy bounds.
  After the editor fix, the R8 release also accepted the native `input text settings`
  burst exactly once and displayed `1 result`. The final release remains installed,
  and Pluto is now the emulator's default Home app (no phone role was changed).
- The emulator's direct SwiftShader renderer segfaulted during Coverflow tests. ANGLE
  (`-gpu swangle`, guest Vulkan disabled) completed the entire suite, so the development
  helper now defaults to it. Later landscape keyboard smoke automation stalled in the
  software graphics session; no app crash/ANR was reported. That keyboard flow still
  needs real-device confirmation; the emulator was reset afterwards.
- Screenshot image input is still blocked by the harness model-route capability config.
  Hierarchy/layout checks are not a substitute for a visual review. Reference-phone
  appearance, controller hardware, OxygenOS transitions and frame-time measurements
  remain outstanding.

## Repeat

```sh
scripts/dev.sh run release
scripts/dev.sh test
scripts/dev.sh atest                 # disposable emulator only: can delete its app data
scripts/dev.sh ui
scripts/dev.sh shot
```

The release APK is `app/build/outputs/apk/release/app-release.apk`, signed with the local
Android debug key as configured by this project. Installing over a build signed with a
*different* key will be refused; do not uninstall a daily-use build without backing up
anything you want to keep.
