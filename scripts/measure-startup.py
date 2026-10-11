#!/usr/bin/env python3
"""Measure Android am start -W first-display timings; this is not fully-drawn latency.
Run against a release build with identical compilation, device and settings for both samples.
Never clears app data. Cold samples force-stop only the supplied package.
"""
import argparse
import json
import math
import re
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--runs', type=int, default=10)
parser.add_argument('--output', required=True)
args = parser.parse_args()
if args.runs < 1:
    parser.error('--runs must be positive')

def shell(*command):
    return subprocess.check_output(['adb', '-s', args.serial, 'shell', *command], text=True)

def launch():
    output = shell('am', 'start', '-W', '-n', 'dev.pluto.launcher/.MainActivity',
                   '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER')
    values = {key: int(value) for key, value in re.findall(r'^(TotalTime|WaitTime):\s*(\d+)', output, re.M)}
    if 'WaitTime' not in values:
        raise RuntimeError(output)
    state = re.search(r'^LaunchState:\s*(\w+)', output, re.M)
    values['launchState'] = state.group(1) if state else 'unknown'
    return values

# One untimed run ensures install/first-launch work does not dominate the comparison.
launch()
time.sleep(1)
result = {'serial': args.serial, 'runs': args.runs,
          'method': 'am start -W; cold force-stop; warm return from Android Settings; first-display, not fully-drawn',
          'cold': [], 'warm': []}
for _ in range(args.runs):
    shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
    time.sleep(.7)
    shell('am', 'force-stop', 'dev.pluto.launcher')
    result['cold'].append(launch())
    time.sleep(.5)
for _ in range(args.runs):
    shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
    time.sleep(.7)
    result['warm'].append(launch())
    time.sleep(.5)
for mode in ['cold', 'warm']:
    expected_state = 'COLD' if mode == 'cold' else 'HOT'
    samples = [sample for sample in result[mode] if sample['launchState'] == expected_state]
    values = [sample.get('TotalTime', sample['WaitTime']) for sample in samples]
    if not values:
        result[mode + '_summary_ms'] = {'valid_samples': 0, 'excluded_samples': args.runs}
        continue
    ordered = sorted(values)
    result[mode + '_summary_ms'] = {'valid_samples': len(values), 'excluded_samples': args.runs - len(values), 'p50': statistics.median(values),
        'p95': ordered[math.ceil(.95 * len(ordered)) - 1], 'min': min(values), 'max': max(values)}
with open(args.output, 'w') as stream:
    json.dump(result, stream, indent=2)
print(json.dumps({key: value for key, value in result.items() if key.endswith('_summary_ms')}, indent=2))
