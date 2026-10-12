#!/usr/bin/env python3
"""Save a daily snapshot of public GitHub release APK download counters."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import urllib.request


def fetch_releases(repository):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected repository in owner/name form")
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "Pluto-download-stats",
               "X-GitHub-Api-Version": "2022-11-28"}
    if os.environ.get("GH_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
    releases = []
    page = 1
    while True:
        request = urllib.request.Request(
            f"https://api.github.com/repos/{repository}/releases?per_page=100&page={page}",
            headers=headers,
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            batch = json.load(response)
        releases.extend(batch)
        if len(batch) < 100:
            return releases
        page += 1


def make_snapshot(repository, releases, now):
    rows = []
    for release in releases:
        if release.get("draft"):
            continue
        assets = [{"id": a["id"], "name": a["name"], "downloads": a["download_count"]}
                  for a in release["assets"] if a["name"].lower().endswith(".apk")]
        if assets:
            rows.append({"id": release["id"], "tag": release["tag_name"],
                         "url": release["html_url"], "published_at": release["published_at"],
                         "prerelease": release["prerelease"], "assets": assets})
    rows.sort(key=lambda row: row["published_at"] or "", reverse=True)
    return {"repository": repository, "captured_at": now.isoformat(), "releases": rows}


def render_summary(snapshot, previous, collection_note=None):
    old = {a["id"]: a["downloads"] for r in (previous or {}).get("releases", [])
           for a in r["assets"]}
    total = sum(a["downloads"] for r in snapshot["releases"] for a in r["assets"])
    lines = ["# Pluto APK downloads", "",
             f"Last captured: **{snapshot['captured_at']}** (UTC).", "",
             f"**{total:,} total downloads** across currently published APK assets.", "",
             "These are downloads, not unique users, installations, or active users. Repeat downloads "
             "and updates count again; shared APKs and other distribution sources are absent.", ""]
    if previous:
        lines += [f"Change is since the previous saved snapshot: {previous['captured_at']}.", ""]
    else:
        lines += ["This is the first snapshot. Daily changes will appear after the next capture.", ""]
    lines += ["| Release | APK downloads | Change |", "| --- | ---: | ---: |"]
    for release in snapshot["releases"]:
        count = sum(a["downloads"] for a in release["assets"])
        # Asset replacement resets its counter. Do not present that as a usage decline.
        comparable = previous and all(a["id"] in old and a["downloads"] >= old[a["id"]]
                                      for a in release["assets"])
        delta = ("+" + str(sum(a["downloads"] - old[a["id"]] for a in release["assets"]))) if comparable else "—"
        tag = release["tag"].replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
        label = tag + (" (prerelease)" if release["prerelease"] else "")
        lines.append(f"| [{label}]({release['url']}) | {count:,} | {delta} |")
    lines += ["", "Daily raw snapshots are in [snapshots/](snapshots/). Each includes release and asset IDs, "
              "so replaced assets can be distinguished from their predecessors.", "",
              "Only APK assets are counted; checksum files and other attachments are excluded. "
              "A dash means there is no comparable earlier counter. Deleted releases or assets disappear "
              "from the current total but remain in historical snapshots. GitHub does not provide historical "
              "daily counts before tracking began.", "",
              collection_note or "Collection is scheduled daily at 08:00 Europe/Copenhagen through T3 Code while T3 is running, and can also be "
              "run manually. A repeat run replaces that UTC day's snapshot. No app telemetry is collected.", ""]
    return "\n".join(lines)


def save_snapshot(output, snapshot, collection_note=None):
    directory = output / "snapshots"
    directory.mkdir(parents=True, exist_ok=True)
    day = snapshot["captured_at"][:10]
    # Compare with the most recent earlier day, even when rerunning today's capture.
    earlier = sorted(p for p in directory.glob("????-??-??.json") if p.stem < day)
    previous = json.loads(earlier[-1].read_text()) if earlier else None
    (directory / f"{day}.json").write_text(json.dumps(snapshot, indent=2) + "\n")
    (output / "README.md").write_text(render_summary(snapshot, previous, collection_note))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default="andershfranzen/pluto-launcher")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    save_snapshot(args.output, make_snapshot(args.repository, fetch_releases(args.repository),
                                             dt.datetime.now(dt.timezone.utc)))
