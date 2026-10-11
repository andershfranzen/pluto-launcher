# Interface polish — 2026-10-09

## Follow-up — 2026-10-11

- The alphabetical drawer now follows Home's dark, translucent presentation: thin
  charcoal frosting over the blurred background, light labels, softer category pills,
  a glass capsule for category/actions controls and a rounded floating search bar.
  Grid rows have slightly more breathing room. The drawer uses light system-bar icons;
  opening another page restores the normal theme's system-bar treatment.
- Drawer search now uses the shared glass surface and names the active category in
  its placeholder and accessibility state. Empty categories open their app picker
  directly through Add apps. A category search with no matches can switch to All
  apps without losing its query, including above the landscape keyboard.
  Actions mode has shorter guidance; in keyboard-compact layouts its mode indicator
  and Done control sit beside search so results retain their space. Portrait and
  landscape keyboard recovery paths were visually checked. Three focused Android
  regression tests cover the app picker, query-preserving scope switch and exiting
  actions mode with the landscape keyboard open.
- Glass over XMB now takes a restrained tint from its selected colour, with lower
  light-theme fill opacity and a softer rim. Settings cards use a lighter overlay
  instead of stacking pale fills, while stronger secondary text preserves contrast.
  Orange and blue XMB settings were visually checked; the user's Auto colour was
  restored afterward. Wallpaper and Pluto surfaces retain their neutral treatment.
- GitHub update checks ask for a first-launch choice before any request. Automatic
  checking is the default choice; opting out leaves manual checks available under
  General → Updates. Allowed checks run on resume with a daily throttle, including
  failed requests. Explicit downloads show progress and can be cancelled. APK size,
  checksum, package, version and the installed signing identity are checked before
  Android's installer receives a private read-only content URI. Completed downloads
  and the pending unknown-source permission handoff survive process death.
  The first-launch question is a content-sized glass dialog with the existing Pluto
  planet logo, a compact landscape header and brief explanations. Portrait and
  landscape were visually reviewed; a live GitHub check correctly detected 0.1.3
  as current. 251 JVM tests and 5 focused updater/consent Android tests passed.
- ROM setup now presents folder, default emulator and tab name in that order. The
  folder supplies an editable initial name, and Create/Save stays pinned below the
  scrollable form. Launch-method overrides are collapsed under Advanced options.
  The emulator chooser suggests installed emulators and previously used apps, supports
  search and allows any installed app. Its search header compacts for the landscape IME.
  An Open emulator setup action preserves the draft while BIOS/first-run setup is done
  in the chosen emulator. Unsaved changes require explicit discard.
  Successful creation reports where the tab appears and offers View tab in console mode.
  Collections refresh on resume with a 30-second per-tab throttle; Refresh games is
  immediate. Inline errors, empty-folder guidance and an Add games/box art help panel
  explain the next action without showing Android launch-interface details by default.
- Shared frosted surfaces now cover folder popups, Edit Home, pickers, app actions,
  settings and nested dialogs. Neutral translucent controls replace the blue-grey
  secondary fill. Built-in backgrounds and Home blur behind open layers.
- Centered pages, dialogs and folders reserve equal horizontal space on both sides
  when a landscape camera cutout occupies one edge. Landscape text entry uses a
  compact layout with a complete name field and keyboard Done to save; portrait
  retains the regular dialog actions. The system keyboard controls its own width.
- Startup uses the Pluto planet centered on black, matching Android's native splash.
  A small progress cue appears only for loads longer than 700 ms; Reduce motion uses
  a static cue. No tagline or artificial loading delay is added.
- Apps inside folders can be dragged onto Home, the dock or another folder.
- ROM shelves create named console tabs and share the app carousel, reflection and
  cover rendering. Folder access uses persistent read-only Android document grants;
  scanning stores metadata and never moves or deletes the user's ROMs. CUE/M3U
  referenced tracks/discs are hidden from the top-level game list.
- ROM display names remove known region, language, revision, website and dump tags,
  while retaining disc numbers and edition names. The local Spyro test has matching
  PAL cover art beside its CUE; cover art is not bundled with Pluto. Artwork lookup
  currently uses adjacent images, without an online metadata/cover scraper. ROM covers
  occupy the card with a small rim, preserve their aspect ratio and reflect below it;
  the app-icon inset is reserved for apps and the generic disc fallback.
- DuckStation's direct launch was checked with the user's Spyro disc dump and
  SCPH1001 BIOS. The test CUE filename was corrected, and matching PAL SBI metadata
  was obtained from Redump. Both explicit DuckStation and Automatic with DuckStation
  selected booted the game from Pluto. Other emulators use Android Open file requests
  or the app chooser and need their own compatibility checks.
- 247 JVM tests passed. Focused emulator coverage includes database migration and
  shelf persistence, read-only launch/tree grants, carousel controller focus,
  landscape folder/shelf-name entry and Always → Automatic rotation restoration.
  Real controllers, drag haptics and the OnePlus still need hardware verification.
- Release visual checks covered portrait/landscape name dialogs, category membership
  and shelf selection, widget picker/configuration/actions, hidden-app restore,
  Edit Home, Apps/General settings, the full app actions sheet and drawer actions/search
  empty states. Nested pages and sheets blur beneath dialogs. The final keyboard/name,
  default-emulator search/save, draft-discard and launch-grant run passed all 9 tests;
  migration and controller-focus checks passed
  earlier in the same pass.
- The final R8 release walkthrough created PlayStation 1 from the real Android
  folder picker, confirmed the folder-derived name, searched for DuckStation with
  the landscape keyboard and preserved the draft after opening emulator setup.
  Portrait and landscape retained the pinned Create action. The completed tab
  contained one Spyro entry with its cleaned title and adjacent cover image;
  launching it with the default emulator reached the game's intro.
- The live hub's generic landscape frame places its camera on the right. For review,
  Android reverse landscape (`adb shell wm user-rotation lock 3`) matches that frame.
  This is a preview-frame convention, not a launcher orientation restriction.

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
  helper used it at that time. Later landscape keyboard smoke automation stalled in the
  software graphics session; no app crash/ANR was reported. That keyboard flow still
  needs real-device confirmation; the emulator was reset afterwards.
- Screenshot image input is still blocked by the harness model-route capability config.
  Hierarchy/layout checks are not a substitute for a visual review. Reference-phone
  appearance, controller hardware, OxygenOS transitions and frame-time measurements
  remain outstanding.

## Repeat

The development helper now defaults to host GPU rendering, with NVIDIA PRIME
offload on hosts with a loaded NVIDIA driver. Use `PLUTO_GPU=swangle scripts/dev.sh
emu start` as a software fallback. On the current RTX 4070 host, switching from
software rendering to the discrete GPU reduced median console frame time from
350 ms to 16 ms. T3's live stream was also switched to hardware H.264 encoding
(`h264_nvenc`), with 1280-pixel capture and 60 fps requested; its measured capture
rate was approximately 30 fps with no dropped frames. The hub's encoder selection
is separate from the emulator and may need selecting again after a hub restart.

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
