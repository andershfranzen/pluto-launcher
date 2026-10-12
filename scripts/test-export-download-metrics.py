import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('exporter', Path(__file__).with_name('export-download-metrics.py'))
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)


class ExportMetricsTest(unittest.TestCase):
    def test_latest_stable_excludes_newer_prerelease(self):
        rows = [dict(id=2, tag='v0.2.0-beta', prerelease=True, assets=[dict(id=3, name='app.apk', downloads=8)]),
                dict(id=1, tag='v0.1.5', prerelease=False, assets=[dict(id=4, name='app.apk', downloads=22)])]
        text = exporter.render_metrics(dict(repository='test/app', captured_at='2026-10-12T00:00:00+00:00', releases=rows))
        self.assertIn('release="v0.2.0-beta",latest="false"} 8', text)
        self.assertIn('release="v0.1.5",latest="true"} 22', text)
        self.assertIn('asset_id="4"} 22', text)
        self.assertIn('} 1791763200.0', text)

    def test_labels_escape_quotes_and_newlines(self):
        self.assertEqual(exporter.labels(asset='a"b\nc\\d'), '{asset="a\\"b\\nc\\\\d"}')

    def test_failed_atomic_replace_preserves_previous_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'stats.prom'
            exporter.atomic_write(path, 'original\n')
            with patch.object(exporter.os, 'replace', side_effect=OSError('disk error')):
                with self.assertRaises(OSError):
                    exporter.atomic_write(path, 'replacement\n')
            self.assertEqual(path.read_text(), 'original\n')
            self.assertEqual(list(Path(directory).iterdir()), [path])


if __name__ == '__main__':
    unittest.main()
