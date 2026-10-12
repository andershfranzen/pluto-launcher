import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('notifier',Path(__file__).with_name('notify-downloads.py'))
notifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(notifier)

class NotificationsTest(unittest.TestCase):
    def snapshot(self,count,asset=20):
        return {'repository':'test/app','releases':[{'tag':'v0.1.5','assets':[{'id':asset,'name':'app.apk','downloads':count}]}]}
    def test_baseline_increase_and_restart_do_not_repeat(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'state.json';messages=[]
            send=lambda message,title: messages.append((message,title))
            self.assertEqual(notifier.notify(self.snapshot(52),path,send),0)
            self.assertEqual(notifier.notify(self.snapshot(54),path,send),2)
            self.assertEqual(notifier.notify(self.snapshot(54),path,send),0)
            self.assertIn('#53',messages[0][0]);self.assertIn('#54',messages[1][0])
    def test_failed_publish_does_not_advance_unsent_cursor(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'state.json';notifier.notify(self.snapshot(1),path)
            def fail(message,title): raise OSError('ntfy offline')
            with self.assertRaises(OSError):notifier.notify(self.snapshot(2),path,fail)
            self.assertEqual(json.loads(path.read_text())['assets']['20'],1)
            self.assertEqual(notifier.notify(self.snapshot(2),path,lambda *args:None),1)
    def test_new_asset_and_reset(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'state.json';notifier.notify(self.snapshot(52),path)
            self.assertEqual(notifier.notify(self.snapshot(1),path,lambda *args:None),0)
            self.assertEqual(notifier.notify(self.snapshot(2,asset=21),path,lambda *args:None),2)
    def test_backlog_keeps_every_unsent_download(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'state.json';notifier.notify(self.snapshot(0),path)
            self.assertEqual(notifier.notify(self.snapshot(5),path,lambda *args:None,maximum=2),2)
            self.assertEqual(notifier.notify(self.snapshot(5),path,lambda *args:None,maximum=2),2)
            self.assertEqual(notifier.notify(self.snapshot(5),path,lambda *args:None,maximum=2),1)

if __name__=='__main__':unittest.main()
