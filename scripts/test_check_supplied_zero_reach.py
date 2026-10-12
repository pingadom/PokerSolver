"""Exercise the independent checker against real saved evidence and corruptions."""
import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-zero-reach-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-zero-reach-report.json.gz'
CHECKER = ROOT / 'scripts/check-supplied-zero-reach.py'


class ZeroReachCheckerTest(unittest.TestCase):
    def run_checker(self, request, report, output):
        return subprocess.run([sys.executable, str(CHECKER), str(request), str(report),
                               '--output', str(output)], capture_output=True, text=True, timeout=30)

    def corrupt(self, mutation, expected):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            report = json.loads(gzip.decompress(REPORT.read_bytes()))
            mutation(report)
            changed = folder / 'changed.json.gz'
            changed.write_bytes(gzip.compress(json.dumps(report).encode()))
            output = folder / 'receipt.json'
            result = self.run_checker(REQUEST, changed, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(expected, result.stderr)
            self.assertFalse(output.exists())

    def test_real_evidence_passes_and_never_overwrites_a_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'receipt.json'
            result = self.run_checker(REQUEST, REPORT, output)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads(output.read_text())
            self.assertEqual(1, receipt['originalLpCertificatesChecked'])
            self.assertEqual(0, receipt['freshLpSolves'])
            self.assertEqual('315337/607392', receipt['literalHeroBestResponseRational'])
            original = output.read_bytes()
            self.assertNotEqual(0, self.run_checker(REQUEST, REPORT, output).returncode)
            self.assertEqual(original, output.read_bytes())

    def test_original_point_corruption_fails_without_receipt(self):
        def mutate(report):
            report['diagnostic']['minimumReachWitness']['point'][0] += .25
        self.corrupt(mutate, 'Original LP primal/dual certificate failed')

    def test_reported_dual_corruption_fails_without_receipt(self):
        def mutate(report):
            report['diagnostic']['minimumReachWitness']['certificate']['dualValue'] += .25
        self.corrupt(mutate, 'Independent disagreement')

    def test_behavioral_mass_corruption_fails_without_receipt(self):
        def mutate(report):
            report['diagnostic']['minimumReachWitness']['masses'][0]['behavioralWeight'] += .25
        self.corrupt(mutate, 'Independent disagreement')

    def test_wrong_admission_identity_fails_without_receipt(self):
        self.corrupt(lambda report: report.update(trainerAdmission=True),
                     'Unsupported saved zero-reach diagnostic identity')

    def test_duplicate_keys_and_nonfinite_constants_reject(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for text, expected in [('{"x":0,"x":1}', 'Duplicate JSON key'),
                                   ('{"x":NaN}', 'Non-finite JSON constant'),
                                   ('{"x":1e309}', 'Non-finite JSON number')]:
                changed = folder / 'changed.json'
                changed.write_text(text)
                output = folder / 'receipt.json'
                result = self.run_checker(REQUEST, changed, output)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(expected, result.stderr)
                self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
