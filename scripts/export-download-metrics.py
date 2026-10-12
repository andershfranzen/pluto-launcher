#!/usr/bin/env python3
"""Collect GitHub APK counters into node-exporter's textfile directory."""
import argparse
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import tempfile

spec = importlib.util.spec_from_file_location("downloads", Path(__file__).with_name("track-downloads.py"))
downloads = importlib.util.module_from_spec(spec)
spec.loader.exec_module(downloads)


def labels(**values):
    return "{" + ",".join(f"{k}={json.dumps(str(v))}" for k, v in values.items()) + "}"


def render_metrics(snapshot):
    repo = snapshot["repository"]
    lines = ["# HELP pluto_github_apk_downloads_total GitHub APK download counter per asset; not unique users.",
             "# TYPE pluto_github_apk_downloads_total counter",
             "# HELP pluto_github_release_downloads Currently published APK download counts per release.",
             "# TYPE pluto_github_release_downloads gauge"]
    latest = next((r["id"] for r in snapshot["releases"] if not r["prerelease"]), None)
    for release in snapshot["releases"]:
        for asset in release["assets"]:
            tag = labels(repository=repo, release=release["tag"], asset=asset["name"], asset_id=asset["id"])
            lines.append(f"pluto_github_apk_downloads_total{tag} {asset['downloads']}")
        tag = labels(repository=repo, release=release["tag"], latest=str(release["id"] == latest).lower())
        lines.append(f"pluto_github_release_downloads{tag} {sum(a['downloads'] for a in release['assets'])}")
    timestamp = dt.datetime.fromisoformat(snapshot["captured_at"]).timestamp()
    lines += ["# HELP pluto_github_download_stats_last_success_timestamp_seconds Last successful GitHub collection.",
              "# TYPE pluto_github_download_stats_last_success_timestamp_seconds gauge",
              f"pluto_github_download_stats_last_success_timestamp_seconds{labels(repository=repo)} {timestamp}"]
    return "\n".join(lines) + "\n"


def atomic_write(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".pluto-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w") as stream:
            stream.write(content)
        os.chmod(temporary, 0o644)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default="andershfranzen/pluto-launcher")
    parser.add_argument("--history", required=True, type=Path)
    parser.add_argument("--metrics", required=True, type=Path)
    args = parser.parse_args()
    snapshot = downloads.make_snapshot(args.repository, downloads.fetch_releases(args.repository),
                                        dt.datetime.now(dt.timezone.utc))
    downloads.save_snapshot(args.history, snapshot, collection_note=(
        "Collection runs daily around 08:00 Europe/Copenhagen on the homelab, independently of T3. "
        "A repeat run replaces that UTC day's snapshot. No app telemetry is collected."))
    atomic_write(args.metrics, render_metrics(snapshot))
