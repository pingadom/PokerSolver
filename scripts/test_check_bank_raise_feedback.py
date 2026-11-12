import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('bank_raise_feedback_checker',Path(__file__).with_name('check-bank-raise-feedback.py'))
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class BankRaiseFeedbackCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context=checker.context();cls.request=checker.read(checker.REQUEST);cls.report=checker.read(checker.REPORT)
    def corrupt(self,change):
        q,r=copy.deepcopy(self.request),copy.deepcopy(self.report);change(q,r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):
            checker.check_values(q,r,self.context)
    def test_original_two_hands_six_moves_and_seventy_two_inequalities(self):
        p=checker.check_values(self.request,self.report,self.context)
        self.assertTrue(p['qualifiedForOfflineDrill']);self.assertEqual((2,6,6,6,72),(p['handsChecked'],p['movesChecked'],p['policyChecks'],p['minimumLowerChecks'],p['utilityLossInequalitiesChecked']))
        self.assertEqual((False,False,0,0),(p['trainerAdmission'],p['gradingAuthority'],p['freshLpSolves'],p['freshCfrRuns']))
        self.assertFalse(p['minimumUpperUsedAsFullActionCeiling'] or p['currentDecisionEvShiftApplied'])
    def test_source_report_and_original_checker_remain_pinned(self):
        self.assertEqual(checker.SOURCE_SHA,hashlib.sha256(checker.source.REPORT.read_bytes()).hexdigest())
        self.assertEqual(checker.SOURCE_HELPER_SHA,hashlib.sha256((checker.ROOT/'scripts/check-bank-raise-sequence.py').read_bytes()).hexdigest())
    def test_rejects_request_threshold_source_and_extra_fields(self):
        for k,v in [('schemaVersion','wrong'),('minimumStoredSha256','wrong'),('maximumLossBb',.1),('maximumLossBb',True),('extra',1)]:
            self.corrupt(lambda q,_,k=k,v=v:q.__setitem__(k,v))
        self.corrupt(lambda q,_:q['minimumRequest']['heroCards'].reverse())
    def test_rejects_forged_source_admission_scope_and_qualification(self):
        self.corrupt(lambda _,r:r['sourceMinimum'].__setitem__('referenceSupportedMinima',False))
        self.corrupt(lambda _,r:r.__setitem__('trainerAdmission',True))
        self.corrupt(lambda _,r:r.__setitem__('boundsScope','CURRENT_DECISION_EV'))
        self.corrupt(lambda _,r:r.__setitem__('qualifiedForOfflineDrill',False))
    def test_rejects_missing_reordered_or_borrowed_hand_assessments(self):
        self.corrupt(lambda _,r:r['assessments'].pop())
        self.corrupt(lambda _,r:r['assessments'].reverse())
        self.corrupt(lambda _,r:r['assessments'][0].__setitem__('sourceQuestionIndex',2))
        self.corrupt(lambda _,r:r['assessments'][0].__setitem__('heroCards','9d 9h'))
    def test_rejects_changed_public_context_menu_and_call_price(self):
        self.corrupt(lambda _,r:r['assessments'][0]['context'].__setitem__('actingSeat','BTN'))
        self.corrupt(lambda _,r:r['assessments'][0]['context'].__setitem__('toCallBb',8.))
        self.corrupt(lambda _,r:r['assessments'][0]['moves'].pop())
    def test_minimum_upper_is_never_a_full_action_ceiling(self):
        self.assertTrue(all(a['moves'][2]['upperEvBb']>m['minimum']['upperBound']+1
                            for a,m in zip(self.report['assessments'],self.report['sourceMinimum']['batch']['minima'],strict=True)))
        self.corrupt(lambda _,r:r['assessments'][0]['moves'][2].__setitem__('upperEvBb',r['sourceMinimum']['batch']['minima'][0]['minimum']['upperBound']))
    def test_weaker_candidate_cannot_reduce_the_original_lower_bound(self):
        original=self.report['sourceMinimum']['sourceBank']['assessments'][0]
        bounds=checker.inputs(original,-9.)
        self.assertEqual(original['moves'][2]['lowerEvBb'],bounds[2]['selectedLowerBb'])
        self.assertTrue(all(b['sequenceMinimumLowerBb'] is None for b in bounds[:2]))
    def test_rejects_changed_utility_loss_and_classification(self):
        for field,value in [('lowerEvBb',0.),('upperLossBb',0.),('classification','WITHIN_LIMIT')]:
            self.corrupt(lambda _,r,f=field,v=value:r['assessments'][0]['moves'][2].__setitem__(f,v))
        self.corrupt(lambda _,r:r['assessments'][0]['lowerBoundSources'][0].__setitem__('sequenceMinimumLowerBb',0.))
    def test_rejects_false_previous_menu_comparison(self):
        self.corrupt(lambda _,r:r['assessments'][0]['previousMenuComparison'].pop())
        self.corrupt(lambda _,r:r['assessments'][0]['previousMenuComparison'][2].__setitem__('upperLossDifferenceBb',0.))
    def test_rejects_dropped_policies_false_counts_and_boolean_counters(self):
        self.corrupt(lambda _,r:r['assessments'][0]['policies'].pop())
        self.corrupt(lambda _,r:r['assessments'][0]['policies'][0].__setitem__('utilityLossInequalitiesChecked',0))
        self.corrupt(lambda _,r:r['assessments'][0]['policies'][0].__setitem__('minimumLowerChecks',True))
        self.corrupt(lambda _,r:r['assessments'][0]['policies'][0].__setitem__('maximumBoundViolationBb',.1))
    def test_outside_face_control_records_no_bound_checks_and_failure(self):
        minimum=self.report['sourceMinimum'];original=copy.deepcopy(minimum['sourceBank']['assessments'][0]['policies'][0]);original['insideDeclaredFace']=False
        p=checker.assess(original,minimum['policySupports'][0],self.report['assessments'][0]['moves'])
        self.assertFalse(p['boundsChecked']);self.assertIsNone(p['maximumBoundViolationBb']);self.assertEqual(0,p['utilityLossInequalitiesChecked'])
        self.assertIn('BANK_MENU_REFERENCE_OUTSIDE_FACE',p['failures'])
    def test_unsupported_minimum_control_cannot_pass(self):
        minimum=self.report['sourceMinimum'];support=copy.deepcopy(minimum['policySupports'][0]);support['supported']=False
        p=checker.assess(minimum['sourceBank']['assessments'][0]['policies'][0],support,self.report['assessments'][0]['moves'])
        self.assertFalse(p['sourceMinimumSupported']);self.assertIn('RAISE_MINIMUM_NOT_SUPPORTED',p['failures'])
    def test_policy_utility_outside_new_bounds_is_explicitly_rejected(self):
        minimum=self.report['sourceMinimum'];original=copy.deepcopy(minimum['sourceBank']['assessments'][0]['policies'][0]);original['actionUtilitiesBb']['raise:9.0']=-10.
        p=checker.assess(original,minimum['policySupports'][0],self.report['assessments'][0]['moves'])
        self.assertGreater(p['maximumBoundViolationBb'],1);self.assertIn('REFERENCE_OUTSIDE_BANK_MENU_BOUNDS',p['failures'])
    def test_original_reach_and_quality_failures_remain_qualification_failures(self):
        minimum=copy.deepcopy(self.report['sourceMinimum'])
        minimum['sourceBank']['assessments'][0]['policies'][0]['failures']=['INSUFFICIENT_ACTUAL_QUESTION_REACH','FULL_PARENT_GAP']
        a=checker.assessment(minimum,0,0)
        self.assertFalse(a['qualifiedForOfflineDrill']);self.assertIn('PRIMARY:INSUFFICIENT_ACTUAL_QUESTION_REACH',a['rejectionReasons'])
        self.assertIn('PRIMARY:FULL_PARENT_GAP',a['rejectionReasons'])
    def test_raises_remain_unresolved_even_after_loss_bounds_improve(self):
        for a in self.report['assessments']:
            raise_move=a['moves'][2];self.assertEqual('STRATEGY_DEPENDENT',raise_move['classification'])
            self.assertEqual(0,raise_move['lowerLossBb']);self.assertGreater(raise_move['upperLossBb'],.01)
            self.assertLess(raise_move['upperLossBb'],a['previousMenuComparison'][2]['previousMove']['upperLossBb'])

if __name__=='__main__':unittest.main()
