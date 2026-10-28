"""All-four-hand, posterior-likelihood and qualification corruption controls."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('btn_size_test',Path(__file__).with_name('check-size-menu-btn.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class SizeMenuBtnEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT);cls.receipt=check.check()
    def reject(self,mutate):
        report=copy.deepcopy(self.report);mutate(report)
        with self.assertRaises((ValueError,KeyError,IndexError)):
            check.check_values(self.request,report)
    def test_all_hands_actions_certificates_and_policy_assessments_match_receipt(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-size-menu-btn-quarter-independent-check.json'))
        self.assertEqual(self.receipt['qualifiedIndices'],[0,1,2,3]);self.assertEqual(self.receipt['handsChecked'],4)
        self.assertEqual(self.receipt['actionAttemptsChecked'],8);self.assertEqual(self.receipt['conditionalCertificatesChecked'],32)
        self.assertEqual(self.receipt['policyAssessmentsChecked'],12);self.assertEqual(self.receipt['retainedEndpointRejections'],0)
        self.assertFalse(self.receipt['trainerAdmission'])
    def test_complete_hand_inventory_and_size_specific_context_cannot_be_changed(self):
        self.reject(lambda r:r['cases'].pop())
        self.reject(lambda r:r['cases'][1].__setitem__('heroCards','2c 3c'))
        self.reject(lambda r:r['cases'][0]['context'].__setitem__('toCallBb',3.25))
        self.reject(lambda r:r['cases'][0]['context']['flopHistory'][0].__setitem__('action','bet:0.5'))
    def test_opponent_call_and_quarter_bet_likelihood_are_checked_in_posteriors(self):
        self.reject(lambda r:r['cases'][0]['endpoints'][0]['utility']['diagnostic']['denominator'].__setitem__('constant',1))
        self.reject(lambda r:r['cases'][0]['endpoints'][1]['utility']['diagnostic']['lowerWitness']['posterior'][0].__setitem__('probability',1))
        self.reject(lambda r:r['cases'][1]['primary'].__setitem__('actualQuestionRootReach',.25))
        self.reject(lambda r:r['cases'][1]['references'][0].__setitem__('counterfactualQuestionReachGivenPhysicalFlop',1))
    def test_original_lp_witnesses_plans_and_work_counters_are_verified(self):
        self.reject(lambda r:r['cases'][2]['endpoints'][0]['utility']['diagnostic']['minimumReachWitness']['point'].__setitem__(0,999))
        self.reject(lambda r:r['cases'][2]['endpoints'][1]['utility']['diagnostic']['upperWitnesses'][0]['dual'].__setitem__(0,999))
        self.reject(lambda r:r['cases'][2]['endpoints'][1]['utility']['diagnostic']['numeratorPlans'].clear())
        self.reject(lambda r:r['cases'][2]['endpoints'][1]['utility']['diagnostic']['work'].__setitem__('intervalLpSolves',0))
    def test_policy_face_quality_regret_and_all_bounds_are_recomputed(self):
        self.reject(lambda r:r['cases'][0]['primary'].__setitem__('insideDeclaredFace',False))
        self.reject(lambda r:r['cases'][1]['references'][1]['reachedFlopQuality']['conditionalBestResponse'].__setitem__('nashConvBb',1))
        self.reject(lambda r:r['cases'][2]['primary']['actionLossBb'].__setitem__('fold',0))
        self.reject(lambda r:r['cases'][3]['primary'].__setitem__('decisionRegretBb',1))
    def test_classifications_qualified_indices_and_authority_cannot_be_forged(self):
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))
        self.reject(lambda r:r['qualifiedIndices'].pop())
        self.reject(lambda r:r['cases'][0]['moves'][1].__setitem__('classification','WITHIN_LIMIT'))
        self.reject(lambda r:r['cases'][1]['rejectionReasons'].append('FALSE_REASON'))
    def test_partial_rejection_cannot_keep_grades_or_qualification(self):
        def partial(r):
            e=r['cases'][0]['endpoints'][1];e['utility']=None;e['rejection']=dict(kind='LP',reason='NUMERICAL_FAILURE',detail='controlled',reachFailure=None)
        self.reject(partial)
        self.reject(lambda r:r['cases'][3]['endpoints'].pop())
        self.reject(lambda r:r['cases'][3]['endpoints'][0]['utility'].__setitem__('lowerBb',100))
        self.reject(lambda r:r['parent']['references'][0]['policy'].__setitem__('iterations',2000))


if __name__=='__main__':unittest.main()
