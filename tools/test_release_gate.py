"""Portable failure fixtures for publication and mandatory visual gates."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gate = load("ci_release_gate")
visuals = load("ci_validate_visuals")


class ReleaseGateTest(unittest.TestCase):
    SHA = "a" * 40

    def test_wrong_commit_and_missing_run_block(self):
        self.assertFalse(gate.latest_success([], self.SHA))
        self.assertFalse(gate.latest_success([dict(head_sha="b" * 40, run_number=1,
                                                   status="completed", conclusion="success")], self.SHA))

    def test_latest_failed_cancelled_or_running_attempt_blocks_old_success(self):
        older = dict(head_sha=self.SHA, run_number=1, status="completed", conclusion="success")
        for status, conclusion in [("completed", "failure"), ("completed", "cancelled"),
                                   ("in_progress", None)]:
            newer = dict(head_sha=self.SHA, run_number=2, status=status, conclusion=conclusion)
            self.assertFalse(gate.latest_success([older, newer], self.SHA))
        self.assertTrue(gate.latest_success([older], self.SHA))

    def test_new_failed_rerun_blocks_old_success(self):
        self.assertFalse(gate.latest_success([
            dict(head_sha=self.SHA, run_number=1, run_attempt=1, status="completed", conclusion="success"),
            dict(head_sha=self.SHA, run_number=1, run_attempt=2, status="completed", conclusion="failure")], self.SHA))

    def test_missing_empty_corrupt_and_zero_frame_visuals_fail(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            with self.assertRaises(ValueError):
                visuals.validate(root)
            (root / "dashboard.png").touch()
            with self.assertRaises(ValueError):
                visuals.validate(root)
            (root / "dashboard.png").write_bytes(b"corrupt")
            with patch.object(visuals.subprocess, "run", side_effect=subprocess.CalledProcessError(1, "ffmpeg")):
                with self.assertRaises(subprocess.CalledProcessError):
                    visuals.validate(root)
            with patch.object(visuals.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="0,0")):
                with self.assertRaises(ValueError):
                    visuals.validate(root)

    def test_missing_final_screen_blocks_even_when_prior_assets_decode(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            (root / "journey-evidence").mkdir()
            for name in ("dashboard.png", "review-journey.mp4", "journey-evidence/cycle-0.png",
                         "journey-evidence/cycle-29.png", "journey-evidence/cleanup-locked.png",
                         "journey-evidence/permission-denied.png"):
                (root / name).write_bytes(b"fixture")
            with patch.object(visuals.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="320,640")):
                with self.assertRaises(ValueError):
                    visuals.validate(root)

    def test_all_mandatory_visuals_accept_only_after_decoding(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            (root / "journey-evidence").mkdir()
            names = ["dashboard.png", "review-journey.mp4"] + [
                f"journey-evidence/{name}.png" for name in
                ("cycle-0", "cycle-29", "cleanup-locked", "permission-denied", "permission-regranted")]
            for name in names:
                (root / name).write_bytes(b"fixture")
            with patch.object(visuals.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, stdout="320,640")) as decode:
                visuals.validate(root)
                self.assertEqual(14, decode.call_count)
