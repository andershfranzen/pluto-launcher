#!/usr/bin/env python3
"""Send one ntfy notification per newly observed GitHub APK download."""
import argparse
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import urllib.request

spec = importlib.util.spec_from_file_location('exporter', Path(__file__).with_name('export-download-metrics.py'))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


def publish(message, title):
    payload = {'topic': os.environ['NTFY_TOPIC'], 'title': title, 'message': message,
               'tags': ['package'], 'click': 'https://grafana.taile2ec74.ts.net/d/pluto-downloads/pluto-c2b7-apk-downloads'}
    request = urllib.request.Request(os.environ['NTFY_BASE_URL'], data=json.dumps(payload).encode(),
        headers={'Authorization': 'Bearer ' + os.environ['NTFY_TOKEN'], 'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    if not result.get('id') or result.get('event') != 'message':
        raise RuntimeError('ntfy did not acknowledge the notification')


def notify(snapshot, state_path, send=publish, maximum=30):
    current = [(str(a['id']), r['tag'], a['name'], a['downloads'])
               for r in snapshot['releases'] for a in r['assets']]
    if not state_path.exists():
        state = {'repository': snapshot['repository'], 'assets': {key: count for key, _, _, count in current}}
        exporter.atomic_write(state_path, json.dumps(state, indent=2) + '\n')
        return 0  # Baseline existing counters; never send historical downloads at setup.
    state = json.loads(state_path.read_text())
    if state['repository'] != snapshot['repository']:
        raise ValueError('Notification state belongs to another repository')
    sent = 0
    for key, release, name, count in current:
        previous = state['assets'].get(key, 0)
        if count < previous:
            state['assets'][key] = count  # Reset counter, not a new download.
            exporter.atomic_write(state_path, json.dumps(state, indent=2) + '\n')
            continue
        for sequence in range(previous + 1, count + 1):
            if sent >= maximum:
                return sent  # Continue any backlog next minute, respecting ntfy limits.
            send(f'{release}: {name} was downloaded from GitHub.\n'
                 f'APK download #{sequence} for this asset. This may be a new install or an update.',
                 f'Pluto APK downloaded · {release}')
            state['assets'][key] = sequence
            # Only advance after a successful publish, so failed deliveries are retried.
            exporter.atomic_write(state_path, json.dumps(state, indent=2) + '\n')
            sent += 1
    return sent


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', default='andershfranzen/pluto-launcher')
    parser.add_argument('--state', required=True, type=Path)
    parser.add_argument('--test', action='store_true')
    args = parser.parse_args()
    if args.test:
        publish('Pluto APK download alerts are enabled. New GitHub APK downloads will be checked once a minute. '
                'This is a setup test, not a new download.', 'Pluto download alerts enabled')
        print('Setup test acknowledged by ntfy.')
    else:
        snapshot = exporter.downloads.make_snapshot(args.repository, exporter.downloads.fetch_releases(args.repository),
                                                    dt.datetime.now(dt.timezone.utc))
        count = notify(snapshot, args.state)
        print(f'Published {count} new APK download notifications.')
