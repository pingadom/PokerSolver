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
spec = importlib.util.spec_from_file_location('btn_curriculum_check', ROOT / 'scripts/check-btn-flop-curriculum.py')
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
spec.loader.exec_module(checker)
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-btn-curriculum-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-btn-curriculum-report.json.gz'


class BtnCurriculumCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.saved = json.loads(gzip.decompress(REPORT.read_bytes()))

    def test_saved_original_certificates_full_path_and_references_pass(self):
        receipt = checker.check(REQUEST, REPORT)
        self.assertEqual(32, receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(3, receipt['uniquePoliciesChecked'])
        self.assertEqual(12, receipt['policyContextChecks'])
        self.assertEqual(4, receipt['contextsChecked'])
        self.assertFalse(receipt['trainerAdmission'])
        self.assertEqual(0, receipt['freshLpSolves'])
        self.assertEqual(0, receipt['freshCfrRuns'])
        self.assertEqual(240000, receipt['declaredSharedFreshReferenceIterations'])

    def test_semantic_corruptions_fail_before_fixed_byte_binding(self):
        cases = [
            (['uniquePoliciesChecked'], 12, 'Four-hand report identity differs'),
            (['policyContextChecks'], 3, 'Four-hand report identity differs'),
            (['references',0,'iterations'], 2000, 'Shared fresh reference budget differs'),
            (['references'], [], 'Shared fresh reference budget differs'),
            (['decisions',0,'heroCards'], 'Ah Kh', 'Four-hand report identity differs'),
            (['decisions',1,'informationSet'], 'forged', 'Literal BTN question differs'),
            (['decisions',1,'actions',0,'diagnostic','algorithm'], 'OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_EXACT_POWER_TWO_ENDPOINTS/v4', 'BTN interval scope differs'),
            (['decisions',1,'actions',0,'diagnostic','lowerWitness','certificate','algorithm'], 'OWNED_EXACT_POWER_TWO_L1_RHS_RESOLVABLE_COST_ORIGINAL_CERTIFICATE/v1', 'Original LP identity differs'),
            (['decisions',1,'actions',0,'diagnostic','denominator','constant'], .1, 'denominator constant differs'),
            (['decisions',1,'actions',0,'diagnostic','numeratorPlans',0,'constant'], .1, 'numerator constant differs'),
            (['decisions',1,'actions',0,'diagnostic','lowerWitness','point',0], 1000000, 'Original LP certificate fails'),
            (['decisions',1,'actions',0,'diagnostic','lowerWitness','certificate','work','arithmeticWork'], 4000000001, 'Original LP work cap differs'),
            (['decisions',2,'actions',0,'diagnostic','minimumReachWitness','posterior',0,'opponentSequence'], 0, 'Opponent past sequence differs'),
            (['decisions',2,'actions',0,'diagnostic','fixedHeroPast'], [dict(action='call',key='forged')], 'BTN interval scope differs'),
            (['decisions',2,'actions',0,'diagnostic','lowerWitness','scaling'], 100, 'BTN fractional scaling differs'),
            (['decisions',2,'actions',0,'diagnostic','work','intervalLpArithmeticWork'], 0, 'Cumulative BTN LP work differs'),
            (['decisions',2,'references',0,'iterations'], 2000, 'Reference-context coverage differs'),
            (['decisions',3,'primary','actionUtilitiesBb','bet'], 0, 'BTN forced action value differs'),
            (['decisions',3,'references',0,'insideDeclaredFace'], False, 'Reference face status differs'),
            (['decisions',3,'references',0,'fullParentQuality','nashConvBb'], .1, 'best-response gap differs'),
            (['decisions',3,'references',0,'decisionRegretBb'], .5, 'BTN selected decision regret differs'),
            (['decisions',3,'qualified'], False, 'Decision qualification differs'),
            (['qualifiedForOfflineDrill'], False, 'Four-hand qualification differs'),
            (['request','referenceBudget','iterations'], [2000,4000], 'Four-hand report identity differs'),
        ]
        for i in range(4):
            cases += [
                (['decisions',i,'context','toCallBb'],1,'Owned BTN table context differs'),
                (['decisions',i,'primary','actualQuestionRootReach'],.1,'Actual full-path root reach differs'),
                (['decisions',i,'references',0,'counterfactualQuestionReachGivenPhysicalFlop'],.1,'Hero past cancellation'),
                (['decisions',i,'moves',0,'classification'],'WITHIN_LIMIT','BTN interval classification differs'),
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
            changed['decisions'][0]['context']['opponentCards'] = '7d 7h'
            path.write_text(json.dumps(changed), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Owned BTN table context differs'):
                checker.check(REQUEST, path)
            request = json.loads(REQUEST.read_text(encoding='utf-8'))
            request['referenceBudget']['totalIterations'] = 239999
            path.write_text(json.dumps(request), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Unsupported four-hand request'):
                checker.check(path, REPORT)


if __name__ == '__main__':
    unittest.main()
