"""Exercise the independent checker against real saved evidence and corruptions."""
import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-reach-bounds-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-reach-bounds-report.json.gz'
CHECKER = ROOT / 'scripts/check-supplied-reach-bounds.py'


class ReachBoundsCheckerTest(unittest.TestCase):
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
            self.assertEqual(2, receipt['originalLpCertificatesChecked'])
            self.assertEqual(0, receipt['freshLpSolves'])
            self.assertEqual('315337/607392', receipt['exactGameValueRational'])
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
                     'Unsupported saved reach-bounds identity')
        for key in ('model', 'beliefs', 'payoffAlgorithm'):
            self.corrupt(lambda report, field=key: report['binding'].update({field: 'alien'}),
                         'Unsupported saved reach-bounds identity')

    def test_maximum_point_and_dual_corruptions_fail_without_receipt(self):
        def point(report):
            report['diagnostic']['maximumReachWitness']['point'][0] += .25
        self.corrupt(point, 'Original LP primal/dual certificate failed')
        def dual(report):
            report['diagnostic']['maximumReachWitness']['certificate']['dualValue'] += .25
        self.corrupt(dual, 'Independent disagreement')

    def test_maximum_mass_and_classification_corruptions_fail(self):
        def mass(report):
            report['diagnostic']['maximumReachWitness']['masses'][0]['behavioralWeight'] += .25
        self.corrupt(mass, 'Independent disagreement')
        def status(report):
            report['diagnostic']['status'] = 'VERIFIED_WITNESSES_ON_BOTH_SIDES'
        self.corrupt(status, 'Unsupported saved reach-bounds identity')

    def test_changed_physical_integer_counts_fail_exact_literal_saddle(self):
        def counts(report):
            report['payoffs'][0]['counts']['firstWins'] += 1
            report['payoffs'][0]['counts']['secondWins'] -= 1
        self.corrupt(counts, 'Exact literal saddle value differs')

    def test_other_pot_rules_cannot_reuse_the_literal_payout_proof(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            request = json.loads(REQUEST.read_text())
            report = json.loads(gzip.decompress(REPORT.read_bytes()))
            request['input']['specification']['rules']['stackBb'] = 101
            report['request']['input']['specification']['rules']['stackBb'] = 101
            changed_request = folder / 'request.json'
            changed_report = folder / 'report.json'
            changed_request.write_text(json.dumps(request))
            changed_report.write_text(json.dumps(report))
            output = folder / 'receipt.json'
            result = self.run_checker(changed_request, changed_report, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Unsupported saved reach-bounds identity', result.stderr)
            self.assertFalse(output.exists())

    def test_stored_and_expanded_byte_caps_reject_without_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for suffix, data, expected in [('json', b'x' * (8 * 1024 * 1024 + 1), 'Stored byte cap'),
                    ('json.gz', gzip.compress(b' ' * (8 * 1024 * 1024 + 1)), 'Expanded byte cap')]:
                changed = folder / ('oversize.' + suffix)
                changed.write_bytes(data)
                output = folder / 'receipt.json'
                result = self.run_checker(REQUEST, changed, output)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(expected, result.stderr)
                self.assertFalse(output.exists())

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
