import datetime as dt
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("downloads", Path(__file__).with_name("track-downloads.py"))
downloads = importlib.util.module_from_spec(spec)
spec.loader.exec_module(downloads)


class DownloadStatsTest(unittest.TestCase):
    def release(self):
        return {"id": 10, "tag_name": "v0.1.5", "html_url": "https://github.com/test/app/releases/tag/v0.1.5",
                "published_at": "2026-10-11T12:00:00Z", "prerelease": False,
                "assets": [{"id": 20, "name": "app-release.apk", "download_count": 22},
                           {"id": 21, "name": "app-release.apk.sha256", "download_count": 100}]}

    def snapshot(self, day):
        return downloads.make_snapshot("test/app", [self.release()], dt.datetime(2026, 10, day, tzinfo=dt.timezone.utc))

    def test_only_public_apks(self):
        draft = self.release()
        draft["draft"] = True
        snapshot = downloads.make_snapshot("test/app", [self.release(), draft], dt.datetime.now(dt.timezone.utc))
        self.assertEqual(len(snapshot["releases"]), 1)
        self.assertEqual(snapshot["releases"][0]["assets"], [{"id": 20, "name": "app-release.apk", "downloads": 22}])

    def test_reruns_compare_previous_day_and_keep_history(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            downloads.save_snapshot(output, self.snapshot(11))
            today = self.snapshot(12)
            today["releases"][0]["assets"][0]["downloads"] = 25
            downloads.save_snapshot(output, today)
            today["releases"][0]["assets"][0]["downloads"] = 27
            downloads.save_snapshot(output, today)
            self.assertEqual(len(list((output / "snapshots").glob("*.json"))), 2)
            self.assertIn("| 27 | +5 |", (output / "README.md").read_text())
            self.assertEqual(json.loads((output / "snapshots/2026-10-11.json").read_text()), self.snapshot(11))

    def test_replaced_or_reset_asset_has_no_false_delta(self):
        previous = self.snapshot(11)
        today = self.snapshot(12)
        today["releases"][0]["assets"][0]["id"] = 30
        self.assertIn("| 22 | — |", downloads.render_summary(today, previous))
        today = self.snapshot(12)
        today["releases"][0]["assets"][0]["downloads"] = 1
        self.assertIn("| 1 | — |", downloads.render_summary(today, previous))

    def test_api_pagination(self):
        class Response:
            def __init__(self, batch):
                self.batch = batch
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass
            def read(self):
                return json.dumps(self.batch).encode()
        with patch.object(downloads.urllib.request, "urlopen", side_effect=[Response([self.release()] * 100), Response([self.release()])]) as api:
            self.assertEqual(len(downloads.fetch_releases("test/app")), 101)
            self.assertIn("page=2", api.call_args.args[0].full_url)


if __name__ == "__main__":
    unittest.main()
