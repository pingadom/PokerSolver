"""Physical, LP and behavioral corruption controls for the saved flop action bounds."""
import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / 'scripts/check-supplied-flop-action-bounds.py'
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-connected-flop-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-flop-action-bounds.json.gz'


class FlopBoundsCheckerTest(unittest.TestCase):
    def run_checker(self, report, output):
        return subprocess.run([sys.executable, str(CHECKER), str(REQUEST), str(report),
                               '--output', str(output)], capture_output=True, text=True, timeout=45)

    def corrupt(self, mutation, expected):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            data = json.loads(gzip.decompress(REPORT.read_bytes()))
            mutation(data)
            report = folder / 'changed.json.gz'
            report.write_bytes(gzip.compress(json.dumps(data).encode()))
            output = folder / 'receipt.json'
            result = self.run_checker(report, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(expected, result.stderr)
            self.assertFalse(output.exists())

    def test_complete_physical_compilation_and_all_27_certificates_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'receipt.json'
            result = self.run_checker(REPORT, output)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads(output.read_text())
            self.assertEqual(27, receipt['originalEndpointCertificatesChecked'])
            self.assertEqual(0, receipt['freshLpSolves'])
            self.assertEqual(6, receipt['actionsChecked'])
            self.assertTrue(receipt['physicalPayoffAndProjectionsIndependentlyChecked'])
            self.assertTrue(receipt['fullParentBehavioralSecurityChecked'])
            original = output.read_bytes()
            self.assertNotEqual(0, self.run_checker(REPORT, output).returncode)
            self.assertEqual(original, output.read_bytes())

    def test_arbitrary_physical_event_mass_rejected(self):
        self.corrupt(lambda d: d.update(physicalFlopMass=1), 'Physical event changed')

    def test_raw_floor_must_have_declared_units(self):
        self.corrupt(lambda d: d.update(rawRequiredMinimumReach=.0001), 'Raw floor units changed')

    def test_physical_incidence_cannot_redraw_worlds(self):
        self.corrupt(lambda d: d['physicalIncidence'][0].update(originalJointProbability=.5), 'Original physical incidence changed')

    def test_utility_matrix_is_compiled_from_physical_tree(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['orientedPayoff']['matrix'][0].__setitem__(0, 123), 'Independent vector differs')

    def test_numerator_plans_are_rebuilt_from_literal_continuations(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['numeratorPlans'][0].update(constant=123), 'Physical numerator constant differs')

    def test_original_dual_certificate_corruption_rejected(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['upperWitnesses'][0]['dual'].__setitem__(0, 123), 'Original endpoint certificate fails')

    def test_opponent_realization_must_match_original_projection(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['upperWitnesses'][0]['opponentFlow']['realization'].__setitem__(1, .25), 'Independent vector differs')

    def test_reported_conditional_response_is_independently_recomputed(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['upperWitnesses'][0].update(conditionalHeroBestResponse=123), 'Independent conditional response differs')

    def test_loss_envelope_cannot_hide_sevens_bet_loss(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][1].update(lowerLossBb=0), 'Conservative loss lower differs')

    def test_posterior_cannot_include_hero_past_policy(self):
        self.corrupt(lambda d: d['decisions'][0]['actions'][0]['diagnostic']['upperWitnesses'][0]['posterior'][0].update(probability=.25), 'Own reach included or posterior changed')

    def test_fresh_reference_quality_is_not_trusted(self):
        self.corrupt(lambda d: d['freshReferences'][0]['fullParentQuality'].update(nashConvBb=1), 'Independent literal best-response gap differs')

    def test_admission_and_changed_physical_parent_rejected(self):
        self.corrupt(lambda d: d.update(trainerAdmission=True), 'Action bounds scope differs')
        self.corrupt(lambda d: d['parent']['binding'].update(betPotFraction=1), 'Action bounds scope differs')

    def test_stored_expanded_duplicate_and_nonfinite_inputs_create_no_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            cases = [('large.json', b' '*(8*1024*1024+1)),
                     ('large.json.gz', gzip.compress(b' '*(8*1024*1024+1))),
                     ('duplicate.json', b'{"a":1,"a":2}'),
                     ('nan.json', b'{"a":NaN}'), ('overflow.json', b'{"a":1e999}')]
            for name, data in cases:
                report = folder / name; report.write_bytes(data)
                output = folder / 'receipt.json'
                self.assertNotEqual(0, self.run_checker(report, output).returncode)
                self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
