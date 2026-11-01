"""Corruption controls for conservative complete-menu root qualification."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('root_feedback_test',Path(__file__).with_name('check-root-envelope-feedback.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class RootFeedbackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT);cls.receipt=check.check()

    def reject(self,change):
        report=copy.deepcopy(self.report);change(report)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):
            check.check_values(self.request,report)

    def test_all_policies_nine_forced_values_losses_and_receipt_are_independent(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-root-envelope-feedback-independent-check.json'))
        self.assertTrue(self.receipt['qualifiedForOfflineDrill']);self.assertFalse(self.receipt['trainerAdmission'])
        self.assertEqual(3,self.receipt['policiesChecked']);self.assertEqual(9,self.receipt['forcedContinuationValuesChecked'])
        self.assertEqual(9,self.receipt['decisionLossesChecked']);self.assertEqual(0,self.receipt['freshLpSolves'])
        self.assertEqual(['OUTSIDE_LIMIT','WITHIN_LIMIT','STRATEGY_DEPENDENT'],[m['classification'] for m in self.receipt['moveClassifications']])

    def test_public_quality_cannot_be_replaced_by_private_hand_conditioning(self):
        self.reject(lambda r:r['primary'].__setitem__('publicRootQualityScope','PRIVATE_HAND_GAME'))
        self.reject(lambda r:r['primary'].__setitem__('publicRootReach',1/3))
        self.reject(lambda r:r['references'][0].__setitem__('publicRootNashConvBb',99.))
        self.reject(lambda r:r['references'][0]['fullParentQuality'].__setitem__('nashConvBb',99.))

    def test_chance_mass_and_face_membership_are_recomputed(self):
        self.reject(lambda r:r['primary'].__setitem__('actualQuestionReach',.00003))
        self.reject(lambda r:r['primary'].__setitem__('counterfactualQuestionReach',.00003))
        self.reject(lambda r:r['primary'].__setitem__('insideDeclaredFace',False))
        self.reject(lambda r:r['primary'].__setitem__('inclusionMarginBb',99.))
        self.reject(lambda r:r['primary'].__setitem__('requiredSecuritySlackBb',.001))

    def test_every_move_ev_loss_and_classification_is_checked(self):
        self.reject(lambda r:r['moves'].pop())
        self.reject(lambda r:r['moves'][2].__setitem__('classification','WITHIN_LIMIT'))
        self.reject(lambda r:r['moves'][2].__setitem__('upperLossBb',0.))
        self.reject(lambda r:r['moves'][1].__setitem__('lowerEvBb',99.))

    def test_forced_values_regret_and_reference_inventory_cannot_be_forged(self):
        self.reject(lambda r:r['references'].pop())
        self.reject(lambda r:r['references'][1].__setitem__('iterations',80000))
        self.reject(lambda r:r['primary']['actionUtilitiesBb'].__setitem__('raise:9.0',99.))
        self.reject(lambda r:r['primary']['actionLossBb'].__setitem__('fold',0.))
        self.reject(lambda r:r['references'][1].__setitem__('decisionRegretBb',99.))

    def test_no_relabelling_source_failure_or_public_admission(self):
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))
        self.reject(lambda r:r.__setitem__('qualifiedForOfflineDrill',False))
        self.reject(lambda r:r.__setitem__('rejectionReasons',['NO_ROBUST_MOVE_WITHIN_LIMIT']))
        self.reject(lambda r:r['envelope']['envelope'].__setitem__('scope','EXACT_ENDPOINTS'))

    def test_work_limits_and_original_source_remain_separate_and_unchanged(self):
        self.reject(lambda r:r['envelope']['envelope']['work'].__setitem__('lpSolves',99))
        self.reject(lambda r:r['envelope']['parent']['references'].pop())
        self.reject(lambda r:r['primary'].__setitem__('boundsChecked',False))
        self.reject(lambda r:r['primary'].__setitem__('maximumBoundViolationBb',99.))

    def test_incomplete_numerical_source_is_retained_without_qualification(self):
        r=copy.deepcopy(self.report);s=r['envelope'];s['completeOuterBounds']=False;s['envelope']=None
        s['rejection']=dict(kind='LP',reason='WORK_LIMIT',detail='protocol control only')
        r.update(moves=[],primary=None,references=[],qualifiedForOfflineDrill=False,rejectionReasons=['INCOMPLETE_ROOT_ENVELOPE'])
        result=check.check_values(self.request,r)
        self.assertFalse(result['qualifiedForOfflineDrill']);self.assertFalse(result['completeOuterBounds'])
        r['qualifiedForOfflineDrill']=True
        with self.assertRaises(ValueError):check.check_values(self.request,r)


if __name__=='__main__':unittest.main()
