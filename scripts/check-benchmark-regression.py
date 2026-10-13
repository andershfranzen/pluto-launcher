#!/usr/bin/env python3
"""Compare Macrobenchmark results with the committed emulator baseline.

Macrobenchmark writes one JSON file per test class under the benchmark module's build
directory. This reads those files (or a single file), pairs each benchmark with the
same-named one in the committed baseline, and fails when a median metric grows by more
than the tolerance.

Emulator numbers only compare like with like: same API level, architecture and fixtures.
A different device is reported, never treated as a pass. This is the regression net, not
the acceptance evidence described in docs/ACCEPTANCE.md.
"""
import argparse
import json
import sys
from pathlib import Path

DEFAULT_METRIC = "timeToInitialDisplayMs"


def benchmarks_of(data):
    """The benchmark list of a Macrobenchmark file, whether wrapped in rawBenchmarkData or not."""
    if isinstance(data.get("benchmarks"), list):
        return data["benchmarks"]
    raw = data.get("rawBenchmarkData")
    if isinstance(raw, dict) and isinstance(raw.get("benchmarks"), list):
        return raw["benchmarks"]
    return None


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def discover(results):
    """Every JSON file under the given files/directories that looks like Macrobenchmark output."""
    found = []
    for entry in results:
        path = Path(entry)
        candidates = sorted(path.rglob("*.json")) if path.is_dir() else [path]
        for candidate in candidates:
            try:
                data = load(candidate)
            except (OSError, ValueError):
                continue
            if benchmarks_of(data) is not None:
                found.append((candidate, data))
    return found


def medians(files, metric):
    """benchmark name -> (median, file) for every benchmark that reported the metric."""
    values = {}
    for path, data in files:
        for bench in benchmarks_of(data):
            statistics = (bench.get("metrics") or {}).get(metric) or {}
            median = statistics.get("median")
            if isinstance(median, (int, float)):
                values.setdefault(bench.get("name"), (float(median), path))
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, help="committed baseline JSON")
    parser.add_argument("--results", required=True, action="append",
                        help="benchmark output file or directory; repeatable")
    parser.add_argument("--metric", default=DEFAULT_METRIC)
    parser.add_argument("--tolerance", type=float, default=0.25,
                        help="allowed median growth, e.g. 0.25 for +25%%")
    parser.add_argument("--out", help="write a summary JSON here")
    args = parser.parse_args()

    baseline_file = load(args.baseline)
    baseline = medians([(Path(args.baseline), baseline_file)], args.metric)
    if not baseline:
        print(f"error: the baseline has no {args.metric} medians", file=sys.stderr)
        return 2

    results = discover(args.results)
    if not results:
        print(f"error: no Macrobenchmark output found under {', '.join(args.results)}", file=sys.stderr)
        return 2
    measured = medians(results, args.metric)
    if not measured:
        print(f"error: benchmark output has no {args.metric} medians", file=sys.stderr)
        return 2

    device = (results[0][1].get("context") or {}).get("build", {})
    print(f"baseline: {args.baseline}")
    print(f"device:   {device.get('fingerprint', 'unknown')}")
    print(f"metric:   {args.metric} (tolerance +{args.tolerance * 100:.0f}%)\n")

    regressions, rows = [], []
    for name in sorted(set(baseline) | set(measured)):
        if name not in measured:
            print(f"MISSING  {name}: in the baseline but not in this run")
            continue
        if name not in baseline:
            print(f"NEW      {name}: {measured[name][0]:.1f} ms (no baseline to compare)")
            continue
        old, _ = baseline[name]
        new, _ = measured[name]
        ratio = new / old if old else float("inf")
        flag = "FAIL   " if ratio > 1 + args.tolerance else "ok     "
        if ratio > 1 + args.tolerance:
            regressions.append(name)
        rows.append({"name": name, "baselineMs": round(old, 2), "medianMs": round(new, 2), "ratio": round(ratio, 3)})
        print(f"{flag} {name}: {old:.1f} ms -> {new:.1f} ms ({ratio:+.0%})")

    summary = {
        "baseline": args.baseline,
        "metric": args.metric,
        "tolerance": args.tolerance,
        "device": device.get("fingerprint"),
        "benchmarks": rows,
        "regressions": regressions,
    }
    if args.out:
        Path(args.out).parent.mkdir(parents=True, exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(summary, handle, indent=2)
            handle.write("\n")
    if regressions:
        print(f"\n{len(regressions)} regression(s): {', '.join(regressions)}", file=sys.stderr)
        return 1
    print("\nNo regression beyond the tolerance.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
