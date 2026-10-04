import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("wait_verify", pathlib.Path(__file__).resolve().parents[1] / "scripts/ci_wait_verify_job.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class WaitVerifyTests(unittest.TestCase):
    def test_other_head_cannot_release_slot(self):
        self.assertIsNone(gate.choose_run([{"head_sha": "other", "run_number": 100}], "wanted"))

    def test_latest_attempt_wins(self):
        runs = [{"head_sha": "wanted", "run_number": 1, "run_attempt": attempt} for attempt in (1, 2)]
        self.assertEqual(2, gate.choose_run(runs, "wanted")["run_attempt"])

    def test_only_successful_verify_releases_slot(self):
        self.assertEqual("pending", gate.verify_status([]))
        self.assertEqual("pending", gate.verify_status([{"name": "bundle", "status": "completed", "conclusion": "success"}]))
        self.assertEqual("failed", gate.verify_status([{"name": "verify", "status": "completed", "conclusion": "cancelled"}]))
        self.assertEqual("success", gate.verify_status([{"name": "verify", "status": "completed", "conclusion": "success"}]))
