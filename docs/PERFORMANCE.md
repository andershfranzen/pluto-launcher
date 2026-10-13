# Performance harness

Pluto's performance work used to be described, not measured: the docs said "unmeasured" and
the acceptance table's rows said "Not yet run". This is the harness that turns those rows
into numbers and keeps them from silently getting worse.

**What it is not.** Emulator timings are comparisons between two builds on the same emulator,
never the phone numbers in `docs/ACCEPTANCE.md`. A green benchmark run does not prove the
phone hit 300 ms; it proves the change did not make the emulator slower than the recorded
emulator baseline.

## Pieces

| Piece | What it does |
| --- | --- |
| `:baselineprofile` (`baselineprofile/src`) | Generates the committed baseline/startup profiles (`BaselineProfileGenerator`), measures cold start and the drawer with and without the profile (`StartupBenchmarks`), and smoke-tests the strict journeys (`JourneySmokeTest`). Fixtures come from the profile-only source set, so numbers do not depend on the device's installed apps. |
| `scripts/check-benchmark-regression.py` | Compares a benchmark run with `docs/performance/emulator-baseline-startup.json` and fails when a median grows past the tolerance (default +25%). Reads Macrobenchmark's JSON whether or not it is wrapped in `rawBenchmarkData`. |
| `scripts/perfetto-capture.sh` | Traces the device (`gfx view wm am input binder_driver sched freq idle`) while Pluto starts and settles, and pulls the trace. Open it at <https://ui.perfetto.dev>. Never clears app data. |
| `scripts/measure-startup.py` | The phone diagnostic: `am start -W` cold and warm samples, with launch states. This is the cross-check for the acceptance table. |
| `.github/workflows/ci.yml` | Unit tests, both distribution flavours, the script self-tests and lint on every push and pull request. |
| `.github/workflows/performance.yml` | Creates an emulator AVD (cached), runs the macrobenchmarks, captures a Perfetto trace, runs the regression check and uploads all of it as the `performance` artifact. Manual (`workflow_dispatch`) or on a pull request labelled `performance`. |

## Running it

On the phone (harness of record for `docs/ACCEPTANCE.md`; never clears app data):

```sh
python3 scripts/measure-startup.py --serial <serial> --runs 30 --output docs/performance/oneplus-startup.json
scripts/perfetto-capture.sh --serial <serial> --out artifacts/perfetto
```

On an emulator (what CI does; installs and then uninstalls Pluto, with its data):

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
python3 scripts/check-benchmark-regression.py \
  --baseline docs/performance/emulator-baseline-startup.json \
  --results baselineprofile/build/outputs
```

Refreshing the profiles themselves (also installs and uninstalls Pluto):

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
```

## The regression gate

`check-benchmark-regression.py` exits 1 when a benchmark median grows by more than
`--tolerance` (a quarter by default), 2 when the run produced nothing to compare, and 0 when
everything is within tolerance. It also prints benchmarks that are new or missing from either
side rather than pretending they are fine.

A genuine improvement should move the baseline: re-record it deliberately, in a commit that
says so, instead of widening the tolerance. The committed baseline is
`docs/performance/emulator-baseline-startup.json`, which records the device fingerprint, the
fixture set and the profiles' SHA-256 values for exactly this reason.

## Reading a trace

The acceptance table's questions are all about Pluto's own main-thread work, so in the trace
look for:

- **Startup:** `android.os.Process.start` → first frame after `reportFullyDrawn`; the wait is
  usually binder or activity-manager, not Pluto's own slices.
- **Search:** slices under the input event while the drawer is typing; SQLite or bitmap decode
  there is a bug, not a budget.
- **Controller focus:** input event → the frame that draws the moved focus ring.
- **Mode reflow:** the configuration delivery → the last frame of the new layout.

## Limits

- An emulator under CI is noisier than a phone, and its numbers are only comparable to the
  emulator baseline recorded on the same API level and architecture.
- `am start -W` measures first display, not interactive readiness.
- ANRs are not captured automatically: Android offers no general callback for them, and a
  watchdog thread in a launcher is a bigger risk than the problem. Crash *exceptions* are
  recorded locally (see `CrashLog` and Settings → General → Crash reports), which is often the
  same information.
