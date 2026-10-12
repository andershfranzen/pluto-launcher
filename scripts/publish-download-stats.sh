#!/usr/bin/env bash
# Update only the download-stats branch; leave the app checkout and devices alone.
set -euo pipefail
stats_script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
stats_repo_root="$(dirname "$stats_script_root")"
stats_workdir="$(mktemp -d)"
trap 'rm -rf "$stats_workdir"' EXIT
git clone --quiet --single-branch --branch download-stats \
  https://github.com/andershfranzen/pluto-launcher.git "$stats_workdir"
python "$stats_script_root/track-downloads.py" --output "$stats_workdir"
git -C "$stats_workdir" config user.name "$(git -C "$stats_repo_root" config user.name)"
git -C "$stats_workdir" config user.email "$(git -C "$stats_repo_root" config user.email)"
git -C "$stats_workdir" add README.md snapshots/
if ! git -C "$stats_workdir" diff --cached --quiet; then
  git -C "$stats_workdir" commit -m 'Record daily APK downloads'
  git -C "$stats_workdir" push origin HEAD:download-stats
fi
