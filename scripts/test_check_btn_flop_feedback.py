"""Corruption controls for the independently checked BTN flop report."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('btn_feedback_check', ROOT / 'scripts/check-btn-flop-feedback.py')
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
spec.loader.exec_module(checker)
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-btn-flop-feedback-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-btn-flop-feedback-report.json.gz'


class BtnFeedbackCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.saved = json.loads(gzip.decompress(REPORT.read_bytes()))

    def test_saved_original_certificates_full_path_and_references_pass(self):
        receipt = checker.check(REQUEST, REPORT)
        self.assertEqual(8, receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(3, receipt['policiesChecked'])
        self.assertFalse(receipt['trainerAdmission'])
        self.assertEqual(0, receipt['freshLpSolves'])
        self.assertEqual(0, receipt['freshCfrRuns'])
        self.assertEqual(240000, receipt['declaredFreshReferenceIterations'])

    def test_semantic_corruptions_fail_before_fixed_byte_binding(self):
        cases = [
            (['context', 'toCallBb'], 1, 'Owned BTN table context differs'),
            (['primary', 'actualQuestionRootReach'], .1, 'Actual full-path root reach differs'),
            (['references', 0, 'check', 'counterfactualQuestionReachGivenPhysicalFlop'], .1, 'Hero past cancellation'),
            (['references', 0, 'check', 'insideDeclaredFace'], False, 'Reference face status differs'),
            (['references', 0, 'check', 'fullParentQuality', 'nashConvBb'], .1, 'best-response gap differs'),
            (['references', 0, 'check', 'reachedFlopQuality', 'rawWorldMasses', 0, 'jointRootFlopReach'], .1, 'Raw world reach differs'),
            (['actions', 0, 'diagnostic', 'denominator', 'constant'], .1, 'denominator constant differs'),
            (['actions', 0, 'diagnostic', 'numeratorPlans', 0, 'constant'], .1, 'numerator constant differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'point', 0], 1_000_000, 'Original LP certificate fails'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'certificate', 'algorithm'], 'automatic', 'Original LP identity differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'certificate', 'work', 'arithmeticWork'], 4_000_000_001, 'Original LP work cap differs'),
            (['actions', 0, 'diagnostic', 'minimumReachWitness', 'posterior', 0, 'opponentSequence'], 0, 'Opponent past sequence differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'scaling'], 100, 'BTN fractional scaling differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'opponentVariables', 0], 100, 'Independent vector differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'posteriorTotalVariation'], .1, 'BTN posterior total variation differs'),
            (['actions', 0, 'diagnostic', 'fixedHeroPast'], [dict(action='check',key='invented')], 'BTN interval scope differs'),
            (['actions', 0, 'diagnostic', 'work', 'intervalLpArithmeticWork'], 0, 'Cumulative BTN LP work differs'),
            (['actions', 0, 'diagnostic', 'work', 'intervalLpPivots'], 10001, 'Cumulative BTN LP work differs'),
            (['actions', 0, 'lowerLossBb'], 0, 'BTN loss lower differs'),
            (['moves', 0, 'classification'], 'WITHIN_LIMIT', 'BTN interval classification differs'),
            (['references'], [], 'Fresh BTN reference coverage differs'),
            (['qualifiedForOfflineDrill'], False, 'BTN qualification differs'),
            (['request', 'referenceBudget', 'iterations'], [2000,4000], 'BTN report identity differs'),
            (['actions', 1, 'diagnostic', 'heroProjection', 'rhs', 0], .5, 'Original BTN projections changed'),
            (['actions', 1, 'diagnostic', 'orientedPayoff', 'constant'], 0, 'Original BTN payoff orientation changed'),
            (['references', 0, 'check', 'actionUtilitiesBb', 'bet'], 0, 'BTN forced action value differs'),
            (['references', 0, 'check', 'decisionRegretBb'], .5, 'BTN selected decision regret differs'),
        ]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'changed.json'
            for keys, value, message in cases:
                with self.subTest(path=keys):
                    changed = copy.deepcopy(self.saved)
                    cursor = changed
                    for key in keys[:-1]:
                        cursor = cursor[key]
                    cursor[keys[-1]] = value
                    path.write_text(json.dumps(changed), encoding='utf-8')
                    with self.assertRaisesRegex(ValueError, message):
                        checker.check(REQUEST, path)

    def test_extra_hidden_context_and_relabelled_request_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'changed.json'
            changed = copy.deepcopy(self.saved)
            changed['context']['opponentCards'] = '9s Js'
            path.write_text(json.dumps(changed), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Owned BTN table context differs'):
                checker.check(REQUEST, path)
            request = json.loads(REQUEST.read_text(encoding='utf-8'))
            request['referenceBudget']['totalIterations'] = 239999
            path.write_text(json.dumps(request), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Unsupported fixed BTN request'):
                checker.check(path, REPORT)


if __name__ == '__main__':
    unittest.main()
