"""Corruption controls for the independently checked later BB flop report."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('later_bb_feedback_check', ROOT / 'scripts/check-later-bb-flop-feedback.py')
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
spec.loader.exec_module(checker)
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-later-bb-flop-feedback-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-later-bb-flop-feedback-report.json.gz'


class LaterBBFeedbackCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.saved = json.loads(gzip.decompress(REPORT.read_bytes()))

    def test_saved_original_certificates_full_path_and_references_pass(self):
        receipt = checker.check(REQUEST, REPORT)
        self.assertEqual(8, receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(3, receipt['policiesChecked'])
        self.assertFalse(receipt['trainerAdmission'])
        self.assertEqual(0, receipt['freshLpSolves'])

    def test_semantic_corruptions_fail_before_fixed_byte_binding(self):
        cases = [
            (['context', 'toCallBb'], 0, 'Owned later table context differs'),
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
            (['actions', 0, 'diagnostic', 'lowerWitness', 'scaling'], 100, 'Later fractional scaling differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'opponentVariables', 0], 100, 'Independent vector differs'),
            (['actions', 0, 'diagnostic', 'lowerWitness', 'posteriorTotalVariation'], .1, 'Later posterior total variation differs'),
            (['actions', 0, 'diagnostic', 'fixedHeroPast', 1, 'action'], 'bet', 'Later interval scope differs'),
            (['actions', 0, 'diagnostic', 'work', 'intervalLpArithmeticWork'], 0, 'Cumulative later LP work differs'),
            (['actions', 0, 'diagnostic', 'work', 'intervalLpPivots'], 10001, 'Cumulative later LP work differs'),
            (['actions', 1, 'lowerLossBb'], 0, 'Later loss lower differs'),
            (['moves', 1, 'classification'], 'WITHIN_LIMIT', 'Later interval classification differs'),
            (['references'], [], 'Fresh later reference coverage differs'),
            (['qualifiedForOfflineDrill'], False, 'Later qualification differs'),
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
            with self.assertRaisesRegex(ValueError, 'Owned later table context differs'):
                checker.check(REQUEST, path)
            request = json.loads(REQUEST.read_text(encoding='utf-8'))
            request['flopActions'] = ['bet']
            path.write_text(json.dumps(request), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Unsupported fixed later request'):
                checker.check(path, REPORT)


if __name__ == '__main__':
    unittest.main()
