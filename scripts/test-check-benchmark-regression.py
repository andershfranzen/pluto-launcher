#!/usr/bin/env python3
"""Self-test for check-benchmark-regression.py: run it against crafted fixtures.

Runs with plain python3, no dependencies, so CI can call it next to the other script tests.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

CHECKER = Path(__file__).with_name("check-benchmark-regression.py")


def write(path, benchmarks):
    data = {"context": {"build": {"fingerprint": "test/fixture"}}, "benchmarks": benchmarks}
    path.write_text(json.dumps(data), encoding="utf-8")


def bench(name, median):
    return {"name": name, "params": {}, "metrics": {"timeToInitialDisplayMs": {"median": median}}}


def run(*args):
    return subprocess.run([sys.executable, str(CHECKER), *args], capture_output=True, text=True)


def main():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        baseline = root / "baseline.json"
        write(baseline, [bench("startupNoCompilation", 890.0), bench("startupBaselineProfile", 880.0)])

        same = root / "same"
        same.mkdir()
        write(same / "results.json", [bench("startupNoCompilation", 900.0), bench("startupBaselineProfile", 870.0)])
        result = run("--baseline", str(baseline), "--results", str(same))
        assert result.returncode == 0, f"a small change must pass: {result.stdout}{result.stderr}"
        assert "No regression" in result.stdout, result.stdout

        slow = root / "slow"
        slow.mkdir()
        write(slow / "results.json", [bench("startupNoCompilation", 1400.0), bench("startupBaselineProfile", 880.0)])
        summary = root / "summary.json"
        result = run("--baseline", str(baseline), "--results", str(slow), "--out", str(summary))
        assert result.returncode == 1, f"a +57% median must fail: {result.stdout}{result.stderr}"
        assert "startupNoCompilation" in result.stderr, result.stderr
        written = json.loads(summary.read_text(encoding="utf-8"))
        assert written["regressions"] == ["startupNoCompilation"], written

        empty = root / "empty"
        empty.mkdir()
        result = run("--baseline", str(baseline), "--results", str(empty))
        assert result.returncode == 2, "no output at all is a harness error, not a pass"

        # The committed baseline has rawBenchmarkData around its benchmark list; the checker reads both.
        wrapped = root / "wrapped.json"
        wrapped.write_text(json.dumps({"rawBenchmarkData": {"benchmarks": [bench("startupNoCompilation", 890.0)]}}), encoding="utf-8")
        result = run("--baseline", str(wrapped), "--results", str(wrapped))
        assert result.returncode == 0, f"rawBenchmarkData fixtures must pass: {result.stdout}{result.stderr}"

    print("check-benchmark-regression: self-test passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
