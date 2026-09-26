"""Synthetic tests of the reviewer utility; NOT SwipeRise app tests."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from audit_junit import audit

class AuditTests(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.root = Path(self.tmp.name)
    def tearDown(self): self.tmp.cleanup()
    def report(self, variant="play", skipped=False, failure=False, error=False, file="TEST-x.xml"):
        inner = '<skipped/>' if skipped else '<failure/>' if failure else '<error/>' if error else ''
        raw = (f'<testsuite tests="1" skipped="{int(skipped)}" failures="{int(failure)}" errors="{int(error)}">'
               f'<testcase classname="Example" name="scenario">{inner}</testcase></testsuite>')
        p = self.root / variant / file
        p.parent.mkdir(exist_ok=True); p.write_text(raw); return p
    def policy(self):
        return [{"variant":"play", "classname":"Example", "name":"scenario",
                 "reason":"Offline-only behavior", "covered_by_variant":"fdroid"}]
    def test_pass(self):
        self.report(); self.assertEqual('XML_COUNTS_VALID',audit(self.root,[])['status'])
    def test_no_files(self):
        with self.assertRaises(ValueError): audit(self.root,[])
    def test_approved_skip_preserved(self):
        self.report(skipped=True); self.report('fdroid')
        out=audit(self.root,self.policy())
        self.assertEqual('XML_COUNTS_VALID',out['status'])
        self.assertEqual({'total':2,'passed':1,'failed':0,'errors':0,'skipped':1},out['totals'])
    def test_unapproved_skip_blocked(self):
        self.report(skipped=True); self.assertEqual('BLOCKED',audit(self.root,[])['status'])
    def test_skip_missing_counterpart_blocked(self):
        self.report(skipped=True); self.assertEqual('BLOCKED',audit(self.root,self.policy())['status'])
    def test_mismatch_header_rejected(self):
        p=self.report(); p.write_text(p.read_text().replace('tests="1"','tests="9"'))
        with self.assertRaises(ValueError): audit(self.root,[])
    def test_duplicate_rejected(self):
        self.report(); self.report(file='TEST-copy.xml')
        with self.assertRaises(ValueError): audit(self.root,[])
    def test_failures_preserved(self):
        self.report(failure=True); out=audit(self.root,[])
        self.assertEqual(1,out['totals']['failed']); self.assertEqual('BLOCKED',out['status'])
    def test_errors_preserved(self):
        self.report(error=True); self.assertEqual(1,audit(self.root,[])['totals']['errors'])
    def test_zeroed_handoff_skips_rejected(self):
        self.report(skipped=True); self.report('fdroid')
        h={'tests':[{'id':'BASE-01/suite','passed':1,'failed':0,'skipped':0}]}
        self.assertEqual('BLOCKED',audit(self.root,self.policy(),h)['status'])
    def test_honest_handoff_skips_allowed(self):
        self.report(skipped=True); self.report('fdroid')
        h={'tests':[{'id':'BASE-01/suite','passed':1,'failed':0,'skipped':1}]}
        self.assertEqual('XML_COUNTS_VALID',audit(self.root,self.policy(),h)['status'])
    def test_input_not_mutated(self):
        p=self.report(); raw=p.read_bytes(); audit(self.root,[]); self.assertEqual(raw,p.read_bytes())
    def test_stale_policy_blocked(self):
        self.report(); self.assertEqual('BLOCKED',audit(self.root,self.policy())['status'])
    def test_dtd_rejected(self):
        p=self.report(); p.write_text('<!DOCTYPE example>'+p.read_text())
        with self.assertRaises(ValueError): audit(self.root,[])

if __name__=='__main__': unittest.main()
