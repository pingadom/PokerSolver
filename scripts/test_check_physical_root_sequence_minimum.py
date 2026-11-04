import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('physical_sequence_minimum_checker', Path(__file__).with_name('check-physical-root-sequence-minimum.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class PhysicalSequenceMinimumCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = checker.context()
        cls.request = checker.read(checker.REQUEST)
        cls.report = checker.read(checker.REPORT)

    def corrupt(self, change):
        request, report = copy.deepcopy(self.request), copy.deepcopy(self.report)
        change(request, report)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError)):
            checker.check_values(request, report, self.context)

    def audit(self, change): self.corrupt(lambda _, r: change(r['minimum']))

    def test_original_physics_full_plan_coverage_and_exact_certificate(self):
        result = checker.check_values(self.request, self.report, self.context)
        self.assertTrue(result['completeMinimum'] and result['referenceSupportedMinimum'])
        self.assertEqual(1, result['sequenceMinimumProof']['endpointCertificatesChecked'])
        self.assertEqual(2, result['sequenceMinimumProof']['parentCertificatesChecked'])
        self.assertTrue(result['legacyMinimumBracketsOverlap'])
        self.assertEqual(1296, result['legacyControlChecked']['completePlanCoverage']['cartesianPlansChecked'])
        self.assertEqual(52, result['legacyControlChecked']['completePlanCoverage']['reducedPlansChecked'])
        self.assertFalse(result['trainerAdmission'] or result['fullActionUpperEndpointClaim'])
        self.assertEqual((0, 0), (result['freshLpSolves'], result['freshCfrRuns']))

    def test_rejects_changed_request_pins_action_and_original_game(self):
        for field in ['schemaVersion', 'sourceReportHash', 'action', 'legacyMinimumStoredSha256']:
            self.corrupt(lambda q, _, k=field: q.__setitem__(k, 'wrong'))
        self.corrupt(lambda q, _: q['sourceRequest']['envelope'].__setitem__('heroCards', 'As Ah'))
        self.corrupt(lambda _, r: r['parent']['request']['flop'].__setitem__(0, 'As'))

    def test_rejects_changed_scope_admission_and_outcome(self):
        self.corrupt(lambda _, r: r.__setitem__('trainerAdmission', True))
        self.corrupt(lambda _, r: r.__setitem__('completeMinimum', False))
        self.audit(lambda a: a.__setitem__('scope', 'FULL_ACTION_INTERVAL'))
        self.audit(lambda a: a.__setitem__('trainerAdmission', True))

    def test_rejects_changed_parent_face_and_original_projections(self):
        self.audit(lambda a: a['firstParentWitness']['point'].__setitem__(0, 42.))
        self.audit(lambda a: a.__setitem__('securitySlack', .01))
        self.audit(lambda a: a.__setitem__('globalHeroUpperValue', 100.))
        self.audit(lambda a: a.__setitem__('securityFaceHash','0'*64))
        self.audit(lambda a: a['heroProjection']['offset'].__setitem__(0, .5))
        self.audit(lambda a: a['orientedPayoff'].__setitem__('constant', 42.))

    def test_rejects_changed_root_support_and_reach_floor(self):
        self.audit(lambda a: a.__setitem__('requiredMinimumReach', 0.))
        self.audit(lambda a: a.__setitem__('originalQuestionReach', 1.))
        self.audit(lambda a: a['support'][0].__setitem__('posteriorProbability', .9))
        self.audit(lambda a: a['support'][0].__setitem__('stateHash', '0'*64))

    def test_rejects_changed_forced_domain_and_conditional_payoff(self):
        self.audit(lambda a: a['heroDomain']['matrix'].pop())
        self.audit(lambda a: a['heroDomain'].__setitem__('forcedSequence',0))
        self.audit(lambda a: a['conditionalPayoff'].__setitem__('constant',42.))
        self.audit(lambda a: a['conditionalPayoff']['matrix'][0].__setitem__(0,42.))
        self.audit(lambda a: a['problem']['matrix'][0].__setitem__(0,42.))
        self.audit(lambda a: a['problem']['cost'].__setitem__(-1,1.))
        self.audit(lambda a: a['problem'].__setitem__('globalDualVariables',0))

    def test_rejects_changed_primal_dual_and_behavioral_best_response(self):
        self.audit(lambda a: a['witness']['point'].__setitem__(-1, -1.))
        self.audit(lambda a: a['witness']['dual'].__setitem__(0, -1.))
        self.audit(lambda a: a['witness']['opponentFlow']['realization'].__setitem__(0, .5))
        self.audit(lambda a: a['witness'].__setitem__('conditionalHeroBestResponse', 42.))
        self.audit(lambda a: a['continuationFlow']['realization'].__setitem__(0,.5))
        self.audit(lambda a: a.__setitem__('continuationUtility',42.))
        self.audit(lambda a: a.__setitem__('continuationPolicyHash','0'*64))

    def test_rejects_changed_minimum_bracket_comparison_and_work(self):
        self.audit(lambda a: a['minimum'].__setitem__('lowerBound', .5))
        self.audit(lambda a: a['minimum'].__setitem__('upperBound', 50.))
        self.corrupt(lambda _, r: r.__setitem__('lowerBoundDifferenceBb', 0.))
        self.audit(lambda a: a['work'].__setitem__('endpointLpSolves', 0))
        self.audit(lambda a: a['work'].__setitem__('compilerLimit', 8000001))
        self.audit(lambda a: a['work'].__setitem__('enumeratedContinuationPlans',1))
        self.audit(lambda a: a['work'].__setitem__('endpointPivots',0))
        self.corrupt(lambda _,r: r.__setitem__('legacyMinimumWitnessDifferenceBb',42.))
        self.corrupt(lambda _,r: r.__setitem__('legacyMinimumBracketsOverlap',False))
        self.corrupt(lambda _,r: r['legacyMinimum']['minimum']['plans'].pop())

    def test_legacy_and_new_brackets_do_not_replace_each_others_scope(self):
        self.assertNotEqual(self.report['minimumScope'],self.report['legacyMinimum']['minimumScope'])
        self.assertEqual(0,self.report['minimum']['work']['enumeratedContinuationPlans'])
        self.assertEqual(1296,self.report['legacyMinimum']['minimum']['cartesianPlans'])
        self.assertEqual(52,self.report['legacyMinimum']['minimum']['reducedPlans'])
        self.assertLess(self.report['minimum']['work']['endpointArithmeticWork'],self.report['legacyMinimum']['minimum']['work']['endpointArithmeticWork'])
        self.corrupt(lambda _,r:r.__setitem__('minimumScope',r['legacyMinimum']['minimumScope']))

    def test_primary_can_exceed_minimum_upper_bracket_without_failure(self):
        p = self.report['policies'][0]
        self.assertGreater(p['forcedActionUtilityBb'], self.report['minimum']['minimum']['upperBound'] + .5)
        self.assertTrue(p['minimumLowerSupported'])
        self.assertEqual([], p['failures'])

    def test_rejects_missing_references_and_false_policy_support(self):
        self.corrupt(lambda _, r: r['policies'].pop())
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('forcedActionUtilityBb', 42.))
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('minimumLowerViolationBb', .1))
        self.corrupt(lambda _, r: r.__setitem__('referenceSupportedMinimum', False))

    def test_typed_failure_has_no_minimum_or_support_claim(self):
        r = copy.deepcopy(self.report)
        r.update(minimum=None, rejection=dict(kind='LP', reason='WORK_LIMIT', detail='protocol control only'),
                 completeMinimum=False, referenceSupportedMinimum=False, previousConservativeLowerUtilityBb=None,
                 lowerBoundDifferenceBb=None, legacyMinimumWitnessDifferenceBb=None, legacyMinimumBracketsOverlap=None, rejectionReasons=['MINIMUM:LP:WORK_LIMIT'])
        for p in r['policies']: p.update(minimumLowerViolationBb=None, minimumLowerSupported=False)
        checked = checker.check_values(self.request, r, self.context)
        self.assertFalse(checked['completeMinimum'] or checked['referenceSupportedMinimum'])
        self.assertEqual(0, checked['sequenceMinimumProof']['endpointCertificatesChecked'])
        for key in ['legacyMinimumWitnessDifferenceBb','legacyMinimumBracketsOverlap','previousConservativeLowerUtilityBb','lowerBoundDifferenceBb']:
            edited=copy.deepcopy(r);edited[key]=False if key=='legacyMinimumBracketsOverlap' else 0.
            with self.assertRaises(ValueError):checker.check_values(self.request,edited,self.context)
        r['referenceSupportedMinimum'] = True
        with self.assertRaises(ValueError): checker.check_values(self.request, r, self.context)


if __name__ == '__main__': unittest.main()
