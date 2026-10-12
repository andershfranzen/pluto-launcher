# Homelab download dashboard

[Open Pluto · APK downloads](https://grafana.taile2ec74.ts.net/d/pluto-downloads/pluto-c2b7-apk-downloads)
under Grafana's **Homelab** folder. Uses the existing `prometheus` datasource.

The dashboard shows cumulative APK downloads, the latest stable release count,
24-hour net change, collection freshness, and totals and trends by release. These
are download counters, not unique users or active installations. The 24-hour
comparison needs a full day of samples. Asset deletion can reduce the current total.

## Deployment

On `homelab`, the collector scripts are in `/srv/docker/monitoring/pluto/collect`.
`pluto-download-stats.timer` starts the one-shot service daily at 08:00
Europe/Copenhagen, with up to two minutes of jitter. `Persistent=true` catches up
once after downtime. It runs independently of T3, using Python's standard library
and GitHub's public release API; no token or persistent exporter process is needed.

The service atomically publishes `/var/lib/node_exporter/textfile_collector/pluto-downloads.prom`.
Node-exporter is configured with
`--collector.textfile.directory=/host/var/lib/node_exporter/textfile_collector`
through its existing host root mount. Prometheus scrapes it every 15 seconds.

Daily snapshots are saved in `/srv/docker/monitoring/pluto/history/snapshots` and
are retained independently of Prometheus's existing 30-day retention. Failed
collection leaves the last good metrics intact; the freshness panel turns orange
after 26 hours and red after 36 hours. Check failures with:

```sh
ssh homelab 'systemctl status pluto-download-stats.service --no-pager'
ssh homelab 'journalctl -u pluto-download-stats.service -n 30 --no-pager'
ssh homelab 'sudo systemctl start pluto-download-stats.service'
```

The dashboard JSON is provisioned from
`/srv/docker/monitoring/grafana/dashboards/pluto-downloads.json` and reloaded by
Grafana within 30 seconds. Source copies of the dashboard and units live beside
this document. Collector sources are in `scripts/export-download-metrics.py` and
`scripts/track-downloads.py`.

The existing T3 schedule still maintains the separate public GitHub report; it is
not needed for this Grafana dashboard.

## ntfy download notifications

`pluto-download-notify.timer` checks all public release APK assets once a minute
on the homelab. It publishes one notification per new download to the existing
`homelab-alerts` topic, which the iPhone account already has permission to read.
Notifications include the release tag and per-asset download number, and link to
Grafana. In-app updates and repeat downloads also generate notifications. GitHub
provides aggregate counters, not user identities or download webhooks; delivery
follows GitHub's counter updates and the next poll.

The first run saves existing counts without sending historical notifications.
New release assets are counted from zero. Download cursors are saved atomically
in `/srv/docker/monitoring/pluto/notify-state/counts.json` after ntfy acknowledges
each message. Failed deliveries retain the unsent cursor for retry. Up to 30
notifications are sent per run, with any remaining backlog delivered on later
polls. Removed assets remain in the cursor map to avoid resending their history.

The service loads existing GitHub and ntfy credentials from the private,
mode-0600 `/srv/docker/monitoring/pluto/notify.env`; that file and notification
state are excluded from Git. No credentials appear in the unit files or command
arguments. The systemd timer is independent of T3 and the daily Grafana collector.

```sh
ssh homelab 'systemctl list-timers pluto-download-notify.timer --no-pager'
ssh homelab 'journalctl -u pluto-download-notify.service -n 20 --no-pager'
ssh homelab 'sudo systemctl start pluto-download-notify.service'
```
