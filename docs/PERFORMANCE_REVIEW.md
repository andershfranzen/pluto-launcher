# Performance review — 2026-10-11

Three implementation agents reviewed assets, rendering, and search/data; each then reviewed
another agent's changes. The integration review covered startup, lifecycle work, icon
prewarming, invalidation and the existing performance targets. Changes preserve the glass
styling, notification dots and 108% drawer/shelf entrance.

## Improvements

- **Search:** an immutable source index prepares normalized app, ROM and shortcut text off
  the main thread only when needed, then reuses it across queries. Stable result keys are
  prepared once, and four app ranks use buckets instead of a comparison sort. Category,
  hidden-app and stale-query guards remain intact. Shortcut activation revalidates one
  package/action rather than querying every app; overlapping metadata refreshes deduplicate.
- **XMB:** spatial sine/cosine terms are cached. The wave sampler uses about 150 trig calls
  per frame instead of 4,275, preserving curves within 0.25 pixels at 2160px height after an
  hour. Animation now honors the background rate preference: 30fps cap in Battery saver,
  60fps cap in Smooth, also limited by half the display refresh.
- **Console:** distant selections/flings compose two small neighborhoods, at most 18 cards,
  instead of every intervening index. Art readiness invalidates only its matching card and
  backdrop. Stale prewarm loops cancel; entry/focus identities remain stable.
- **Assets:** simultaneous cover requests share a decode on a pool of three workers.
  Covers decode directly to their displayed bounds, with one provider open. A 1024-square
  cover at 256px now retains 256-square pixels rather than 512-square: one quarter of the
  decoded pixel memory. Caches count actual allocations, including wide-color pixels.
  Generation-tagged icon cache/in-flight keys prevent outdated decodes from becoming
  current after package or icon-pack changes. Temporary bitmap copies are released.
- **Background work:** only visible apps at the primary Home/drawer size are prewarmed;
  management/Console sizes load on demand. Queued warming stops when the launcher is no
  longer started. The redundant process-wide icon-version collector was removed in favor
  of the cache's existing snapshot mirror. Notification composition locals invalidate
  readers instead of the whole UI subtree.
- **Data:** selection/pruning observers avoid rebuilding full app-key collections when
  their relevant sources did not change. Notification bursts coalesce; active-list and
  profile reads run on IO, with connection epochs preventing stale reconnect publication.

## Measurements and limits

The diagnostic JVM workloads use three warmups and five alternating measurements. Results
are medians of complete workloads, not Android input-to-display latency:

- Search: 200 apps, 2,000 ROMs, 400 shortcuts, ten mixed queries with exact before/after
  result equality. Previous algorithm **183.58ms**, retained index **7.93ms** (~23× less
  matching time). Fresh source-index preparation was **6.21ms** and is excluded from query
  timing. An earlier run was 174.47/6.51ms; host/JIT/GC variation is expected.
- Wave sampling: 200 frames, 25 curves, 57 samples each. Previous equations **36.19ms**,
  cached sampler **1.83ms** (~20× less CPU sampling time). Canvas, compositing and GPU
  work are excluded; this is not a 20× frame-rate claim.

The search and wave benchmarks live alongside correctness tests. Run them with Java17:

```sh
./gradlew :app:testDebugUnitTest --tests '*RicherSearchPerformanceTest' --tests '*XmbWavePerformanceTest'
```

Release startup samples use `scripts/measure-startup.py`, Android `am start -W`, unchanged
app data and settings, and return from Android Settings. Cold samples background Pluto
before force-stop. Unknown launch states are excluded. These measure first display rather
than fully drawn/interactive readiness. These phone samples are diagnostic; they do not
satisfy the acceptance document's 30-run requirement. Emulator timings are noisier and do
not represent the OnePlus GPU.

On the OnePlus CPH2709 (Android 16), two alternating five-run batches per build used
identical `speed` compilation. Screen/keyguard checks confirmed awake/unlocked at batch
boundaries. Earlier locked/dozing samples were discarded, including a repeat of the old
build that showed the same slowdown.

| First-display timing | Before p50 / p95 | After p50 / p95 |
| --- | --- | --- |
| Cold, 10 samples per build | 179 / 190ms | 181 / 204ms |
| Return from Settings, 10 samples per build | 64.5 / 92ms | 60.5 / 95ms |

Cold medians changed direction between batches (179→186ms, then 181→178ms). These short
samples support broadly unchanged startup, not a demonstrated startup improvement or
regression. Raw samples: [before](performance/oneplus-startup-before.json) and
[after](performance/oneplus-startup-after.json). No unknown launch states were included.

## Validation

All **277 JVM tests** and **18 focused Android tests** passed. Device coverage includes
cover sharing, decode dimensions and wide-color cache budgets; Console focus/refinements;
richer-search scope and stale actions; drawer behavior; and notification-dot rendering and
listener behavior. Debug, instrumentation and optimized release builds succeeded, including
release lint. Test-only notification access was revoked afterwards.

The matching-key optimized release APK and dex metadata were installed over the existing
OnePlus installation without clearing app data.

## Remaining considerations

- Asset consumers cancel independently; shared decodes already submitted finish for
  other consumers. Concurrent execution is bounded, but the shared request queue is not
  claimed to be bounded.
- Matching cancellation prevents stale publication; very large CPU loops finish their
  current operation before cancellation is observed.
- Full frame-time, GPU, controller latency and day-long battery improvements need longer
  device traces. The acceptance thresholds remain targets unless independently measured.

## Follow-up audit: additional opportunities

The audit below identified the next implementation pass. Its original findings are retained
for context; the follow-up implementation is described afterwards.
Priority reflects likely frequency and impact, with particular attention to large ROM libraries.

1. **Remove duplicate search matching.** `LauncherStateBuilder.build` still invokes
   `SearchMatcher.filter` on each query, normalizing app labels/packages again. The drawer
   subsequently runs the retained richer-search index and uses the old results as its
   interim fallback. Reuse an indexed app projection for that fallback, or unify result
   ownership while preserving atomic query/category publication and controller focus.
2. **Move ROM projection off Main and retain unchanged shelves.** `RomRepository.libraries`
   maps and sorts every library on each Room emission; its `stateIn(viewModelScope)` collector
   uses Main, with no upstream dispatcher boundary for that mapping. Move projection to
   Default, then retain unchanged per-library game lists so an edit/rescan of one shelf
   does not force all shelves to remap or the search index to rebuild unnecessarily.
3. **Avoid unchanged ROM database rewrites.** Every initial shelf observation starts a
   scan, and resume rescans after a 30-second throttle. `RomDao.replaceGames` always
   deletes/inserts the entire shelf, even when scanned metadata is unchanged. Compare
   against the cached snapshot inside the existing transaction before writing; preserve
   tree-URI checks, atomic replacement, failure semantics and cover-change handling.
   Next consider prioritizing/bounding automatic shelf scans, without relying on provider
   notifications or directory timestamps as universally reliable change signals.
4. **Make icon prewarming depend on the active surface and viewport.** `PrewarmIcons`
   still walks every non-hidden app at Home/drawer size after 300ms, even while Console
   is active and uses different artwork sizes. Prioritize visible Home/dock or drawer
   items and a small look-ahead, then cap optional warming to the cache budget. Verify
   fast drawer opening and flinging do not lose responsiveness.
5. **Respond to memory pressure and deprioritize abandoned asset work.** Icon and Console
   caches live for the process lifetime without application trim callbacks; their separate
   byte budgets also exclude live composition references and the Pluto texture. Add
   pressure-aware trimming that never recycles bitmaps still in use. Shared asset loads
   isolate consumer cancellation but retain queued work after consumers leave; track
   consumers and cancel/deprioritize unused queued jobs while retaining shared requests.
6. **Refresh profiles and make benchmark journeys assert coverage.** Current profile
   journeys have no ROM fixture or shortcut fixture, and silently skip missing controls.
   Explicitly exercise indexed search, ROM covers, Console overview and rapid shelf moves;
   assert each journey reaches its intended screen before regenerating profiles. Compare
   `speed-profile` installs as well as `speed`: the earlier full-compilation startup
   comparison cannot establish the shipped profile's effectiveness.

All six findings are addressed below. Frame traces during drawer flings and Console
transitions should guide subsequent rendering changes; the static Pluto globe is already cached in its own layer,
and glass surfaces already avoid live backdrop blur.

## Follow-up implementation

- The atomic drawer/controller fallback retains a lazily built, category-scoped app index.
  Repeated queries no longer normalize every app label/package again. The richer-search
  query and the app-only fallback still both match their respective indexes, preserving
  immediate fallback behavior while richer results are being prepared.
- ROM projection/sorting runs on Default with a collector-confined projector. Unchanged
  shelves and game objects keep their identity. Transactional scans compare all cached
  metadata before replacing rows, avoiding no-change delete/insert work and its downstream
  emissions. Folder-change checks and rollback remain inside the transaction. At most two
  library scans run concurrently; cancellation also removes scans waiting for a permit.
- Home, drawer and app/shortcut search warming follows the visible grid plus at most 12
  look-ahead items. Optional requests are capped at 64 icons and one quarter of the icon
  cache's byte budget at their actual pixel size. Fast flings cancel stale intervals after
  an 80ms settle delay. Optional warming is disabled for inert/parked surfaces; Console uses
  its own art loading. The drawer's small precomposed list still loads its icons on demand.
  Root warms only the active Home dock; folder thumbnails retain their on-demand sizes.
- Android memory callbacks drop unused icon/Console cache ownership: running-low/UI-hidden
  trims to half the budget, critical/background pressure evicts cache entries. Live UI
  references remain valid: displayed/shared bitmaps are never recycled. A retention epoch
  and lock prevent an already-running decode from undoing a concurrent trim.
- Both icon and Console artwork requests track consumers. Cancelling one consumer retains
  shared work; losing the last consumer cancels the request and removes it immediately so
  returning consumers can start afresh. Cancellation checks prevent abandoned decodes from
  entering caches and skip subsequent reflection/color preparation when possible. Blocking
  platform decoders may finish before cancellation is observed.
- Profile-generation-only fixtures provide 32 app aliases, eight Home apps, four dock
  apps, two 48-game ROM shelves, real cover images and a dynamic shortcut; strict journeys exercise those results and Console navigation/overview.
  Fixtures live under `src/nonMinifiedRelease` and are attached only to profiling/benchmark
  variants, excluded from shipping release APKs.

Profile regeneration uses a disposable emulator (generation installs/uninstalls its test
APK), explicitly selecting its serial and the generator class:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile \
  -Pandroid.injected.device.serial=emulator-5554 \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.pluto.launcher.baselineprofile.BaselineProfileGenerator
```

The benchmark APK also contains isolated fixtures. Its no-compilation versus required
baseline-profile results are comparative diagnostics, not shipping-APK startup targets.

## Follow-up validation

- All 290 JVM tests passed, including app-index reuse/scope, retained ROM projection,
  bounded viewport warming, shared-consumer cancellation and memory-pressure policy.
- All 23 focused Android regressions passed with their prerequisites satisfied. Four
  new ROM transaction tests verify no-change/reordered scans make zero game writes,
  changed cover/modification metadata is saved, removed games disappear, stale folders
  cannot overwrite current shelves, and invalid replacements roll back. The new cache
  trim test proves displayed bitmap ownership survives eviction and assets can reload.
- The initial combined device run passed 22/23; the shortcut test lacked default-HOME
  access after profile-test teardown reset the emulator to Nexus Launcher. Restoring
  Pluto as default HOME made that isolated test pass. Temporary notification listener
  access was revoked after testing.
- Full release lint passed. Independent cross-review found no remaining correctness
  blockers in fallback indexing, ROM projection/transactions, viewport warming or
  shared-load cancellation/trim synchronization.
- The strict one-iteration cold journey smoke passed (one executed test, no skips). It
  asserts populated portrait Home, app/ROM/shortcut search, settings/edit navigation,
  both 48-game Console shelves, exact-size decoded cover pixels in carousel and overview,
  overview selection return, and real gamepad-source shelf changes.
- Final profile generation passed both executed tests with no skips: interaction rules
  converged on iteration 5 and startup rules on iteration 3 (two consecutive matching
  captures required). The generated baseline contains 29,655 rules and startup contains
  22,508. Neither contains fixture-class rules. Coverage includes the retained fallback
  index, ROM projector, shared asset loading, icon cache and Console overview.
- Final optimized release build and full release lint passed. Shipping APK manifest and
  R8 output exclude fixture provider, aliases and classes; `POST_NOTIFICATIONS` remains
  debug-only. The packaged baseline profile and metadata are 8,526 and 413 bytes.
- Installed the matching-key release over the OnePlus app with data preserved. External
  dex metadata was transcoded from embedded ART010 to Android 16's ART015 format using
  AndroidX ProfileTranscoder (DEX checksums verified); installation then completed without
  warnings and compilation reports `speed-profile`. An untimed launch succeeded and
  AndroidRuntime showed no crash. Earlier phone timing comparisons remain the only
  hardware performance evidence; this follow-up was not benchmarked on the phone.
- The required-baseline versus no-compilation comparator passed both tests (10 cold
  launches each, zero warm launches). Median time to initial display was 896.14 ms
  uncompiled and 887.91 ms with the required profile. This roughly 0.9% difference is
  effectively unchanged in a noisy single emulator comparison; it establishes profile
  installation success, not a reliable startup speedup. The benchmark variant includes
  isolated fixtures and is not the shipping APK. Raw samples and environment metadata:
  [emulator baseline comparison](performance/emulator-baseline-startup.json).
- Restored the ordinary optimized release on the disposable emulator after profiling,
  completed onboarding, and visually verified real portrait Home and dock icons. The
  empty Home observed during earlier runs came from skipping first-run layout seeding
  in the profiling fixture; fixtures now populate and assert Home independently.
