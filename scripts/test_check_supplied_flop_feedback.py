"""Independent physical context, policy, classification and saved grading corruption controls."""
import gzip
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / 'scripts/check-supplied-flop-feedback.py'
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-flop-feedback-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-flop-feedback-report.json.gz'
DRILL = ROOT / 'docs/data/supplied-four-button-hand-flop-drill.json'


class FlopFeedbackCheckerTest(unittest.TestCase):
    def run_checker(self, report, drill, output, request=REQUEST):
        return subprocess.run([sys.executable, str(CHECKER), str(request), str(report), str(drill),
                               '--output', str(output)], capture_output=True, text=True, timeout=60)

    def corrupt(self, mutation, expected, *, drill=False):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            data = json.loads(DRILL.read_bytes() if drill else gzip.decompress(REPORT.read_bytes()))
            mutation(data)
            changed = folder / 'changed.json'
            changed.write_text(json.dumps(data), encoding='utf-8')
            output = folder / 'receipt.json'
            result = self.run_checker(REPORT if drill else changed, changed if drill else DRILL, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(expected, result.stderr)
            self.assertFalse(output.exists())

    def test_full_qualification_physics_and_saved_review_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'receipt.json'
            result = self.run_checker(REPORT, DRILL, output)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads(output.read_text())
            self.assertEqual(27, receipt['originalEndpointCertificatesChecked'])
            self.assertEqual(3, receipt['primaryAndFreshPoliciesChecked'])
            self.assertEqual(6, receipt['optimalContinuationActionValuesChecked'])
            self.assertEqual(2, receipt['attemptsChecked'])
            self.assertEqual(0, receipt['freshLpSolves'])
            self.assertFalse(receipt['trainerAdmission'])
            before = output.read_bytes()
            self.assertNotEqual(0, self.run_checker(REPORT, DRILL, output).returncode)
            self.assertEqual(before, output.read_bytes())

    def test_classification_cannot_turn_costly_bet_into_good_move(self):
        self.corrupt(lambda d: d['moves'][1].update(classification='WITHIN_LIMIT'), 'Conservative interval classification differs')

    def test_board_context_is_owned(self):
        self.corrupt(lambda d: d['context'].update(board=['2d', '8s', 'Ts']), 'Owned physical table context differs')

    def test_counterfactual_question_reach_cannot_use_raw_units(self):
        self.corrupt(lambda d: d['references'][0].update(counterfactualQuestionReachGivenPhysicalFlop=1/29640), 'Hero past was not cancelled')

    def test_actual_question_reach_is_separate_and_recomputed(self):
        self.corrupt(lambda d: d['references'][1].update(actualQuestionRootReach=.333333), 'Actual raw question reach differs')

    def test_full_parent_face_and_checked_status_are_computed(self):
        self.corrupt(lambda d: d['references'][0].update(insideDeclaredFace=False), 'Reference face/check status differs')

    def test_optimal_future_action_values_are_recomputed(self):
        self.corrupt(lambda d: d['references'][1]['actionUtilitiesBb'].update(bet=-3), 'Forced-action continuation value differs')

    def test_primary_best_response_gap_is_recomputed(self):
        self.corrupt(lambda d: d['primary']['fullParentQuality'].update(nashConvBb=1), 'Independent literal best-response gap differs')

    def test_complete_endpoint_evidence_is_required(self):
        self.corrupt(lambda d: d['bounds'].update(physicalFlopMass=1), 'Complete physical bounds lineage differs')

    def test_qualification_cannot_hide_reference_rejection(self):
        self.corrupt(lambda d: d.update(rejectionReasons=['HIDDEN']), 'Qualification rejection reasons differ')

    def test_drill_question_report_pin_and_pot_are_checked(self):
        self.corrupt(lambda d: d['question'].update(reportHash='0'*64), 'Owned drill question differs', drill=True)
        self.corrupt(lambda d: d['question']['context'].update(potBb=100), 'Owned drill question differs', drill=True)

    def test_saved_grade_counts_and_index_are_replayed(self):
        self.corrupt(lambda d: d['attempts'][1]['feedback']['selected'].update(classification='WITHIN_LIMIT'), 'Saved drill feedback differs', drill=True)
        self.corrupt(lambda d: d['counts'].update(withinLimit=2, outsideLimit=0), 'Attempt counts differ', drill=True)
        self.corrupt(lambda d: d['attempts'][0].update(index=1), 'Saved drill feedback differs', drill=True)

    def test_illegal_actions_and_attempt_cap_rejected(self):
        self.corrupt(lambda d: d['attempts'][0].update(action='call'), 'Illegal drill action', drill=True)
        self.corrupt(lambda d: d.update(attempts=d['attempts']*51), 'Owned drill question differs', drill=True)

    def test_admission_and_missing_fresh_reference_rejected(self):
        self.corrupt(lambda d: d.update(trainerAdmission=True), 'Feedback identity differs')
        self.corrupt(lambda d: d['references'].pop(), 'Fresh reference coverage differs')

    def test_nonfinite_duplicate_and_expanded_cap_reject_without_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for name, data in [('nan.json', b'{"x":NaN}'), ('duplicate.json', b'{"x":1,"x":2}'),
                               ('large.json.gz', gzip.compress(b' ' * (8*1024*1024+1)))]:
                path = folder / name
                path.write_bytes(data)
                output = folder / 'receipt.json'
                self.assertNotEqual(0, self.run_checker(path, DRILL, output).returncode)
                self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
