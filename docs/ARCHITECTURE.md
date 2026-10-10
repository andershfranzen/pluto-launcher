# Pluto architecture

Pluto is a single-module Android app (`:app`, package `dev.pluto.launcher`) written in
Kotlin with Jetpack Compose. minSdk 30, compileSdk/targetSdk 36. It deliberately does not
inherit an existing launcher codebase and uses no DI framework: `AppContainer` in
`LauncherApplication.kt` wires the handful of long-lived objects by hand.

Sources live under `app/src/main/java/dev/pluto/launcher/`.

## Modules

| Package          | Responsibility | Android-free? |
|------------------|----------------|---------------|
| `model`          | Plain data: `AppKey` (component + user serial, with a stable string encoding), `AppEntry`, `HomeItem` (`App` / `FolderRef` with ids `app:<key>` / `folder:<id>`), `Folder`, `Category` + `BuiltInCategory` (ALL, GAMES, TOOLS), `RecentLaunch`, `ReorderOp`, `Organization`, `ControllerInfo`, and the `LauncherMode` / `RotationPreference` / `ThemePreference` / `HandheldAppearance` enums. | Yes |
| `data/db`        | Room: `LauncherDatabase`, entities (`home_item`, `dock_slot`, `folder`, `folder_app`, `category`, `category_app`, `hidden_app`, `recent_launch`) and `LauncherDao`, whose `@Transaction` helpers make multi-row edits atomic. | No |
| `data/prefs`     | DataStore Preferences: `LauncherSettings` (theme, scrim, icon/text scale, reduced motion, rotation, handheld appearance, history policy, onboarding, stick tuning, button mappings) and `SettingsRepository`. `ButtonMapping` maps Android key codes to `ControllerAction`s. | Settings model yes; repository no |
| `data`           | `OrganizationRepository`: the only writer of organisation. Exposes `Flow<Organization>` and enforces invariants (an app is on home at most once, directly or in one folder; dock max 5, one slot per app; ALL category cannot be deleted). | No |
| `domain`         | Pure, unit-tested logic: `ModeResolver`, `FocusAnchor`, `SearchMatcher`, `AppSorting`, `Reorder`, `RecentHistory`, `StickRepeater`, `InputDeduper`, `ControllerClassifier`. Android constants are inlined so these run on the JVM. | Yes |
| `apps`           | `AppCatalog` (LauncherApps discovery, callbacks, launching, App info, uninstall request; personal profile only, reports `otherProfilesPresent`) and `IconCache` (off-main-thread icon decoding into an `LruCache`). | No |
| `input`          | `ControllerMonitor` (InputManager enumeration + device listener -> `StateFlow<List<ControllerInfo>>`), `ControllerInputRouter` (KeyEvent/MotionEvent -> `LauncherAction`), and the `LauncherAction` / `Direction` / `MoveSource` types. | No |
| `system`         | `HomeRole`: RoleManager queries, the role-request intent, and the Default apps settings shortcut. | No |
| `ui`             | `LauncherViewModel`, `LauncherUiState`, `LauncherRoot`. | No |
| `ui/focus`       | `ControllerFocusController`, `Modifier.controllerFocusable`, `ProvideControllerFocus`. | No |
| `ui/components`  | Shared composables such as `AppIcon`. | No |
| `ui/home`        | Phone, Landscape and Handheld layouts: separate composables over the same state. | No |
| `ui/overlay`     | Layers drawn above home: drawer/search, folder, app actions, category membership, move to folder, edit, onboarding. | No |
| `ui/settings`    | Settings, hidden apps, categories, controller mapping and diagnostics. | No |
| root             | `LauncherApplication` / `AppContainer`, `MainActivity` (the Home activity). | No |

Dependency direction: `ui -> data, apps, input, system -> domain -> model`. `domain` and
`model` never depend on Android framework classes.

## Shared state model

```
AppCatalog.apps ─────────┐
OrganizationRepository ──┤
SettingsRepository ──────┼─► LauncherViewModel ──► StateFlow<LauncherUiState> ──► LauncherRoot
ControllerMonitor ───────┤          ▲                                              ├─ Phone / Landscape / Handheld layout
window size, HomeRole ───┘          │                                              └─ layer stack (overlay, settings)
                                    └── user intents (vm.launch, vm.pin, vm.back, ...)
```

- **One state object for every layout.** `LauncherViewModel.state` combines the app
  catalogue, organisation, settings, connected controllers, window size and session state
  into a single immutable `LauncherUiState`. Layout composables are pure views of it; all
  mutations go through ViewModel methods. Rotation therefore only changes which layout
  composable renders the same state.
- **Derived lists** are computed in the ViewModel, off the main thread where they are
  non-trivial: `apps` (visible, alphabetical), `allApps` (including hidden), `drawerApps`
  (category filter + search ranking), `homeTiles`, `dock`, `folders`, `categoryApps`,
  `recents`. Unavailable or uninstalled apps are filtered out, so no stale tile is
  launchable even if a persisted reference remains.
- **Persisted organisation** (Room) and **settings** (DataStore) are written immediately
  after each confirmed edit.
- **Session state** (`SessionState`: layer stack, search text and visibility, active
  category, selected app identity, focused control id, logical scroll anchors per surface,
  edit selection) is transient. It lives in the ViewModel (survives rotation; `MainActivity`
  also handles configuration changes in-process, so the activity is not recreated) and is
  mirrored into `SavedStateHandle` so it survives process death. Layers are serialised with
  `Layer.encode` / `Layer.decode`; apps with `AppKey.encode`.
- **Selection is by identity.** `selectedApp` is an `AppKey`, scroll anchors are item ids.
  A grid's anchor changes only when it leaves the first visible line; after a reflow
  (rotation, different column count) the grid scrolls back to the anchor's line, so
  repeated rotation never drifts the list. The layout mode is resolved in the same frame as
  the window size (`LauncherRoot`), so the previous mode's layout is never shown stretched.
  When an app list changes, `FocusAnchor.resolve` keeps the same item, or picks its nearest
  surviving neighbour (next first, then previous, expanding outward).
- **Package changes**: `AppCatalog.packageEvents` emits `Removed` only for full uninstalls;
  the ViewModel then calls `OrganizationRepository.forgetApp`. Updates and temporary
  unavailability never delete configuration.
- **Reconciliation**: changes no live callback saw (uninstalled while Pluto's process was
  dead, an update that removed or renamed a launcher activity) are caught on every
  *successful* catalog load (`AppCatalog.loads`; a failed or empty query is never used).
  Each persisted key missing from the catalog is checked with `AppCatalog.findStale`: it is
  forgotten only if its package is launchable but that activity is gone, or the package is
  not installed for this user (`PackageManager.getPackageInfo` throws `NameNotFoundException`).
  Disabled, suspended, unavailable or mid-update packages keep their configuration. A launch
  that finds its activity missing runs the same check.
- **One serialisation point for organisation writes**: every organisation mutation in the
  ViewModel holds `editLock` (read-modify-write reorders and the dock slot choice read the
  persisted organisation inside it), so a reorder computed from an earlier read can never
  overwrite a concurrent pin, folder change or uninstall cleanup. As a further guard,
  `setHomeOrder` keeps any current row the caller did not mention.
- **Storage errors** (a missing migration, schema mismatch, SQLiteException, or a settings
  failure other than I/O) are caught on the read flows and shown as a recovery screen with
  Try again and Choose Home app. The database file is never deleted or reset.

## Mode resolution

`ModeResolver.resolve(widthDp, heightDp, controllerConnected, appearance)` uses the
current **window** shape (so split-screen and freeform behave correctly), not the sensor
orientation. "Controller connected" means `ControllerMonitor` reports at least one device
that `ControllerClassifier` accepts (SOURCE_GAMEPAD, or SOURCE_JOYSTICK with gamepad
buttons; never virtual devices or ordinary keyboards).

| Window shape            | Controller    | Handheld appearance | Mode      |
|-------------------------|---------------|---------------------|-----------|
| Portrait (h >= w)       | any           | any                 | PHONE     |
| Landscape (w > h)       | disconnected  | AUTOMATIC           | LANDSCAPE |
| Landscape               | connected     | AUTOMATIC           | HANDHELD  |
| Landscape               | any           | ALWAYS              | HANDHELD  |
| Landscape               | any           | NEVER               | LANDSCAPE |

Rotation preference is applied separately by `MainActivity` to the launcher window's
`requestedOrientation` (`USER`, `USER_PORTRAIT`, `USER_LANDSCAPE`, or `USER_LANDSCAPE`
only while a controller is connected). It never affects launched apps. Layout sizes are
derived from available space and insets, never from orientation alone.

## Input and focus model

**Input pipeline**

1. `MainActivity.dispatchKeyEvent` / `dispatchGenericMotionEvent` offer events to
   `ControllerInputRouter` first. Non-controller sources (keyboard, touch) are not consumed.
2. The router converts D-pad keys, HAT axes and the left stick into `Move(direction)`.
   `StickRepeater` applies the dead zone (max of setting and device-reported flat),
   dominant-axis diagonal resolution with hysteresis, and repeat (delay 350 ms, interval
   100 ms by default). `InputDeduper` drops a move that arrives in the same direction from a
   different source within a short window, so a controller reporting both key and HAT does
   not double-step.
3. Buttons are mapped through `LauncherSettings.mappingFor(descriptor, vendor, product)`
   (per-controller override -> vendor/product -> default). Actions fire on key *up* after a
   seen *down*, so a press carried over from another app is ignored. Home, Recents and the
   controller Guide/Home button (`KEYCODE_HOME`, `KEYCODE_APP_SWITCH`, `KEYCODE_BUTTON_MODE`,
   `ButtonMapping.SYSTEM_RESERVED_KEYS`) are never consumed, captured or mapped, so the
   system's HOME fallback for the Guide button still fires.
4. Actions flow into `LauncherRoot`, which gives Move / Confirm / Actions to the focus
   controller and Back / Search / Settings / category switching to the ViewModel.

**Focus**

- Every focusable control uses `Modifier.controllerFocusable(id, ...)` with a stable id
  (app key, `dock:2`, `folder:5`, `settings:theme`, ...). Touch click, Confirm and Enter run
  the same `onActivate`; the Actions button runs `onSecondary`, which is also exposed as an
  accessibility custom action and as a touch long-press shortcut. Touch also has visible
  routes (the drawer's ⋮ actions mode beside search, Edit home's App actions…), so nothing depends on
  long-press.
- Movement uses Compose spatial focus search (`FocusManager.moveFocus`), so focus order
  follows on-screen geometry in each layout without per-layout tables.
- Focus is drawn as a thick outline plus contrast change outside the tile bounds; tile
  geometry never changes, and shape/contrast carry the state, not colour alone.
- `InputMode` switches to TOUCH on any touch; the focus outline hides but `lastFocusedId`
  is kept. The next controller movement restores that focus without moving or activating.
- Controller attach switches to controller presentation (ring, hints) without moving or
  activating anything; removal switches to touch presentation and keeps the current control
  as the selection. Handheld keeps its button legend while a controller is connected and
  outlines the selected tile while touch drives.
- Layers: opening a layer pushes the opener's id (`pushLayer`); closing pops it so focus
  returns to the control that opened the layer. While a layer or nested dialog is on top,
  the content beneath is focus-inert (`LocalFocusInert`) and hidden from accessibility.
- Lists report their ordered focus ids per surface (`TrackFocusOrder` -> `FocusOrders`).
  When the focused or remembered control vanishes (uninstall, hide, unpin, move to folder),
  focus goes to its nearest surviving neighbour in the same list, never to the top.
- Focused items are brought fully into view with content padding that clears the dock,
  keyboard and system bars.
- The ViewModel remembers `selectedApp` / `focusedControlId` in `SessionState`, so focus
  survives rotation, reflow and controller disconnect/reconnect.

## Persistence and migrations policy

**What is stored where**

| Data | Store | Notes |
|------|-------|-------|
| Favourites order, dock, folders, categories, membership, hidden apps | Room `launcher.db` | Written immediately per edit; multi-table edits in DAO transactions |
| Recent launches (max 12) | Room `recent_launch` | Not written when history is disabled or settings are unreadable; disabling deletes all rows before the flag is saved, and any rows found while history is off are deleted at start |
| Settings and button mappings | DataStore `settings` | Lenient decoding: unknown values fall back to defaults per key. A corrupt file is replaced with fail-closed values (history off, onboarding done, layout seeded) and the user is told once; while the file is unreadable, `SettingsRepository.UNREADABLE` (history off) is used and reading is retried with back-off |
| Session (layers, search, selection, anchors) | ViewModel + `SavedStateHandle` | Transient; not backed up |

App references are stored as `AppKey.encode()` strings (`<userSerial>|<package>/<activity>`)
and home items as `HomeItem.id`. Only personal-profile apps are ever written.
`android:allowBackup` is `false` for 0.1.

**Policy**

- Schemas are exported to `app/schemas/` (`room.schemaLocation`) and **must be committed**.
  The same directory is packaged as androidTest assets for migration tests.
- Every schema change ships a hand-written `Migration`. Destructive fallback
  (`fallbackToDestructiveMigration`) is deliberately **not** enabled: silently losing a
  user's organisation is worse than failing loudly in testing.
- Migrations must preserve existing configuration; if a value cannot be carried over, the
  migration maps it to a safe default explicitly (and the change is called out in release
  notes) rather than dropping the table.
- DataStore has its own `schema` key (`SettingsRepository.SCHEMA_VERSION`); bump it and add a
  migration step when a key's meaning changes.

**Migration tests** (`app/src/androidTest/java/dev/pluto/launcher/db/MigrationTest.kt`,
run with `./gradlew connectedDebugAndroidTest`):

- `version1DataSurvivesMigrationToLatest`: creates a version 1 database from `1.json`,
  inserts rows into every table with raw SQL, runs `MigrationTestHelper.runMigrationsAndValidate`
  to the latest version, then opens it with Room using `LauncherDatabase.MIGRATIONS` and checks
  each row through the DAO (and that foreign-key cascades still work).
- `migrationChainCoversEveryVersion`: asserts that a chain of migrations exists from every
  version below `LauncherDatabase.VERSION` to the latest.
- `allExportedVersionsMigrateToLatest`: for every version 1..`VERSION`, creates that schema
  from its exported JSON, migrates and validates, and opens it with Room.

With `VERSION = 1` these are trivially satisfied, but they validate the exported schema and
are in place for the first real migration.

### Adding schema version 2

1. Change the entities, then bump `LauncherDatabase.VERSION` to `2`.
2. Build (`./gradlew :app:kspDebugKotlin` or any build) so Room exports
   `app/schemas/dev.pluto.launcher.data.db.LauncherDatabase/2.json`. Commit it alongside
   `1.json`; never edit or delete an exported schema.
3. Add the migration to `LauncherDatabase.MIGRATIONS`:

   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(db: SupportSQLiteDatabase) {
           // Example: a new nullable column keeps every existing row.
           db.execSQL("ALTER TABLE folder ADD COLUMN colour TEXT")
       }
   }
   val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
   ```

   For changes SQLite cannot `ALTER` (constraints, column types), create the new table,
   `INSERT INTO new SELECT ... FROM old`, drop the old table, rename, and recreate indices
   exactly as in `2.json`.
4. Add a test case to `MigrationTest`, e.g. `migrate1To2_keepsFoldersAndAddsColour`: create
   version 1 with `helper.createDatabase(name, 1)`, seed with raw SQL, call
   `helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)`, and assert both the old data
   and the new column's default. `allExportedVersionsMigrateToLatest` and
   `migrationChainCoversEveryVersion` cover version 2 automatically.
5. Run `./gradlew connectedDebugAndroidTest` on a device or emulator before merging.

## Threading and performance rules

- No database work or icon decoding on the main thread. Room DAO calls are `suspend`;
  `IconCache.load` decodes on a background dispatcher and `peek` only reads the cache.
- `AppCatalog` loads on a background dispatcher and reloads only the affected package on
  LauncherApps callbacks. On resume it reloads only when the list may be stale
  (`refreshIfStale`: callback not registered, last query failed, or the locale changed), so a
  warm Home return does not re-read every app label.
- `IconCache` keeps a per-package invalidation counter (`versions`); `AppIcon` keys its bitmap
  on it, so an updated app's new icon (or an icon whose decode failed mid-install) is
  reloaded even though its `AppKey` is unchanged.
- No wake locks, polling or background work: controller state comes from InputManager
  callbacks and app state from LauncherApps callbacks.
- Platform exceptions (missing activities, removed profiles, SecurityException from
  LauncherApps) are caught at the `apps` / `system` boundary and turned into
  `LaunchResult.Failure` messages rather than crashes.

### Grids, tiles and the drawer

- **Tiles are cheap by construction.** `AppTile` is a column of two single-node children:
  `AppIcon` draws a cached bitmap synchronously with one draw node (only an icon not yet
  decoded gets state, a coroutine and a fade), and `TileLabel` lays its text out once in its
  own measure pass with one shared `TextMeasurer` (whose cache also serves tiles scrolling
  back into view). No `BoxWithConstraints`, no per-tile text measurer, no per-tile flow:
  `IconCache.versions` is mirrored once into snapshot state. A tile reports its window
  bounds to `OriginRegistry` only when it is activated (and keeps them current afterwards),
  not on every scroll frame. Lazy items declare a `contentType` so compositions are reused.
- **Narrow, stable inputs.** `LauncherUiState` is rebuilt on every change, so layouts pass
  lists through `rememberEqual`: an equal new list keeps the previous instance and children
  (grid, dock, chips) skip. The drawer's search bar reads only its own field state; the grid
  reads only its list.
- **The drawer** (`DrawerScreen`) is a full-screen page, edge to edge behind the status bar:
  a translucent brand-tinted surface (`ui/theme/Surfaces.kt`) whose top row holds Close, the
  category chips (with a sliding indicator) and ⋮, then a pinned search pill, one `LazyVerticalGrid` for every category (switching swaps
  its list, apps in both stay composed, the new list slides in from its side), an A–Z
  `FastScrollRail`, and edge fades drawn in the sheet colour (no offscreen layer). Large
  result changes (Clear) are fed in over frames (`ResultsFeed`), and heavy changes
  cross-fade in place instead of sending tiles on long diagonals. Nothing composes inside a
  measure pass: the keyboard-compact layout is a derived state of the window insets.
- **Scroll anchors** are read when a grid comes to rest (or jumps, or reflows), never per
  scroll frame.
