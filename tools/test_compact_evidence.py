import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("compact", ROOT / "scripts/ci_compact_evidence.py")
compact = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compact)


class CompactEvidenceTest(unittest.TestCase):
    def test_allowlist_excludes_binaries_and_unselected_screens(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            for name in ("dashboard.png", "secret.png", "app.apk", "bundle.aab", "review-journey.mp4"):
                (root / name).write_bytes(b"fixture")
            (root / "staged-kept-counts.txt").write_text("15\n15\n")
            compact.package("runtime", root, root / "out.zip", {"checkout_sha": "a" * 40})
            with zipfile.ZipFile(root / "out.zip") as archive:
                self.assertEqual({"dashboard.png", "staged-kept-counts.txt", "COMPACT-MANIFEST.json"}, set(archive.namelist()))
                manifest = json.loads(archive.read("COMPACT-MANIFEST.json"))
                self.assertEqual(15, manifest["observed_checkpoint"]["staged"])
                self.assertEqual("a" * 40, manifest["identity"]["checkout_sha"])
                self.assertEqual(64, len(manifest["files"][0]["sha256"]))
                self.assertIn("instrumentation.txt", manifest["missing"])

    def test_oversized_selected_evidence_fails_before_archive(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            (root / "dashboard.png").write_bytes(b"x" * 65537)
            with self.assertRaises(ValueError):
                compact.package("runtime", root, root / "out.zip", {}, cap=131072)
            self.assertFalse((root / "out.zip").exists())

    def test_validation_xml_scope_and_archive_reuse_are_bounded(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            (root / "test-results/play").mkdir(parents=True)
            for name in ("TEST-real.xml", "unrelated.xml"):
                (root / "test-results/play" / name).write_text("<testsuite/>")
            compact.package("validation", root, root / "out.zip", {})
            with zipfile.ZipFile(root / "out.zip") as archive:
                self.assertIn("test-results/play/TEST-real.xml", archive.namelist())
                self.assertNotIn("test-results/play/unrelated.xml", archive.namelist())
            with self.assertRaises(ValueError):
                compact.package("validation", root, root / "out.zip", {})

    def test_malformed_checkpoint_never_becomes_claimed_counts(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            root = Path(temp)
            (root / "staged-kept-counts.txt").write_text("15\ninvalid\n")
            with self.assertRaises(ValueError):
                compact.package("runtime", root, root / "out.zip", {})
