"""Corruption controls for complete policy qualification and negative admission."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('size_feedback_test',Path(__file__).with_name('check-size-menu-feedback.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class SizeMenuFeedbackEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT);cls.receipt=check.check()

    def reject(self,mutate):
        report=copy.deepcopy(self.report);mutate(report)
        with self.assertRaises((ValueError,KeyError,IndexError)):
            check.check_values(self.request,report)

    def test_complete_three_policy_nine_action_assessments_match_saved_receipt(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-size-menu-99-feedback-independent-check.json'))
        self.assertEqual(self.receipt['policyAssessmentsChecked'],3)
        self.assertEqual(self.receipt['forcedActionValuesChecked'],9)
        self.assertEqual(self.receipt['policyFailures'],[[],[],[]])
        self.assertEqual(self.receipt['qualificationAdditionalLpSolves'],0)
        self.assertEqual(self.receipt['qualificationAdditionalCfrRuns'],0)

    def test_passing_policies_cannot_bypass_required_robust_outside_move(self):
        self.assertFalse(self.receipt['qualifiedForOfflineDrill'])
        self.assertEqual(self.receipt['rejectionReasons'],['NO_ROBUST_MOVE_OUTSIDE_LIMIT'])
        self.reject(lambda r:r.__setitem__('qualifiedForOfflineDrill',True))
        self.reject(lambda r:r['rejectionReasons'].clear())
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))
        self.reject(lambda r:r['moves'][0].__setitem__('classification','OUTSIDE_LIMIT'))

    def test_actual_and_counterfactual_reach_must_not_be_confused(self):
        self.reject(lambda r:r['primary'].__setitem__('actualQuestionRootReach',1/3))
        self.reject(lambda r:r['references'][0].__setitem__('counterfactualQuestionReachGivenPhysicalFlop',0))
        self.reject(lambda r:r['primary']['reachedFlopQuality']['rawWorldMasses'].pop())
        self.reject(lambda r:r['primary']['reachedFlopQuality']['reachedWorlds'][0].__setitem__('conditionalWeight',1))

    def test_global_local_and_selected_decision_quality_are_separate_gates(self):
        self.reject(lambda r:r['primary']['fullParentQuality'].__setitem__('nashConvBb',1))
        self.reject(lambda r:r['references'][0]['reachedFlopQuality']['conditionalBestResponse'].__setitem__('nashConvBb',1))
        self.reject(lambda r:r['references'][1].__setitem__('decisionRegretBb',1))
        self.reject(lambda r:r['primary']['failures'].append('FULL_PARENT_GAP'))

    def test_face_membership_and_bound_check_claims_are_recomputed(self):
        self.reject(lambda r:r['primary'].__setitem__('inclusionMarginBb',1))
        self.reject(lambda r:r['references'][0].__setitem__('insideDeclaredFace',False))
        self.reject(lambda r:r['references'][1].__setitem__('boundsChecked',False))
        self.reject(lambda r:r['primary'].__setitem__('maximumBoundViolationBb',1))

    def test_every_action_utility_loss_and_policy_identity_are_checked(self):
        self.reject(lambda r:r['primary']['actionUtilitiesBb'].__setitem__('bet:0.25',100))
        self.reject(lambda r:r['references'][0]['actionLossBb'].__setitem__('check',100))
        self.reject(lambda r:r['references'][1].__setitem__('solutionHash','0'*64))
        self.reject(lambda r:r['references'].pop())

    def test_underlying_conditional_source_cannot_be_replaced_or_weakened(self):
        self.reject(lambda r:r['conditional']['actions'].pop())
        self.reject(lambda r:r['conditional']['parent']['binding']['menu']['firstToAct'].pop())
        self.reject(lambda r:r['request'].__setitem__('maximumLossBb',1))
        self.reject(lambda r:r['conditional']['actions'][0]['diagnostic'].__setitem__('securitySlack',1e-4))


if __name__=='__main__':unittest.main()
