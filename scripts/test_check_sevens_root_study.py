"""Independent corruption controls for the original-model sevens root."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('sevens_root_checker',Path(__file__).with_name('check-sevens-root-study.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class SevensRootChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST,128*1024)
        cls.report=check.read(check.REPORT)
        cls.receipt=check.check()

    def reject(self,change):
        report=copy.deepcopy(self.report);change(report)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):
            check.check_values(self.request,report)

    def test_five_certificates_three_policies_nine_values_and_original_parent(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-sevens-root-study-independent-check.json'))
        self.assertTrue(self.receipt['completeOuterBounds']);self.assertTrue(self.receipt['feedbackSupported'])
        self.assertEqual('7d 7h',self.receipt['heroCards'])
        self.assertFalse(self.receipt['trainerAdmission']);self.assertFalse(self.receipt['gradingAuthority'])
        self.assertEqual(5,self.receipt['envelopeCheck']['originalEnvelopeCertificatesChecked'])
        self.assertEqual(3,self.receipt['policiesChecked']);self.assertEqual(9,self.receipt['forcedContinuationValuesChecked'])
        self.assertEqual(9,self.receipt['decisionLossesChecked'])
        self.assertEqual(0,self.receipt['freshLpSolves']);self.assertEqual(0,self.receipt['freshCfrRuns'])

    def test_wrong_hand_actor_source_pin_and_parent_model_are_rejected(self):
        for field,value in [('heroCards','9d 9h'),('hero',3),('parentStoredSha256','0'*64),('maximumLossBb',.02)]:
            request=copy.deepcopy(self.request);request[field]=value
            with self.assertRaises(ValueError):check.check_values(request,self.report)
        self.reject(lambda r:r['parent']['request']['input']['worlds'][0].__setitem__('weight',2.))
        self.reject(lambda r:r['request'].__setitem__('unknown',1))

    def test_six_seats_chips_and_raise_cost_are_reconstructed(self):
        self.reject(lambda r:r['context'].__setitem__('potBb',5.))
        self.reject(lambda r:r['context'].__setitem__('toCallBb',3.))
        self.reject(lambda r:r['context']['actions'][2].__setitem__('amountBb',9.))
        self.reject(lambda r:r['context']['players'][5].__setitem__('remainingStackBb',100.))

    def test_root_chance_and_sevens_support_cannot_be_replaced(self):
        self.reject(lambda r:r['envelope']['question'].__setitem__('informationSet',r['envelope']['question']['informationSet'].replace('7d 7h','9d 9h')))
        self.reject(lambda r:r['envelope'].__setitem__('questionChanceMass',.00001))
        self.reject(lambda r:r['envelope']['support'].pop())
        self.reject(lambda r:r['envelope']['support'][0].__setitem__('chanceWeight',.1))

    def test_all_owned_limits_and_typed_budget_fields_are_checked(self):
        self.reject(lambda r:r['maximumWork'].__setitem__('maximumParentLps',0))
        self.reject(lambda r:r['maximumWork'].__setitem__('freshIterations',1))
        self.reject(lambda r:r['maximumWork'].__setitem__('physicalGames',True))
        self.reject(lambda r:r['envelope']['work'].__setitem__('lpSolves',0))
        self.reject(lambda r:r['envelope']['work'].__setitem__('lpArithmeticWork',0))
        self.reject(lambda r:r['envelope']['work'].__setitem__('compilerLimit',9000000))

    def test_lp_vectors_original_flows_and_objectives_are_independent(self):
        self.reject(lambda r:r['envelope']['guaranteeWitness']['point'].__setitem__(0,99.))
        self.reject(lambda r:r['envelope']['questionUpperWitness']['dual'].__setitem__(0,99.))
        self.reject(lambda r:r['envelope']['actions'][2]['lowerWitness']['opponentFlow']['realization'].__setitem__(0,.5))
        self.reject(lambda r:r['envelope']['actions'][2]['lowerWitness'].__setitem__('optimalContinuationUtility',99.))

    def test_pure_policy_selection_and_terminal_utility_ceilings_are_recomputed(self):
        self.reject(lambda r:r['envelope'].__setitem__('fixedPolicyAlgorithm','UNDECLARED_SELECTION'))
        self.reject(lambda r:r['envelope']['fixedHeroChoices'].pop(next(iter(r['envelope']['fixedHeroChoices']))))
        self.reject(lambda r:r['envelope']['actions'][2].__setitem__('literalUtilityCeiling',99.))

    def test_conservative_allowance_and_full_action_bounds_are_recomputed(self):
        self.reject(lambda r:r['envelope'].__setitem__('securityAllowance',99.))
        self.reject(lambda r:r['envelope']['actions'][2].__setitem__('upperUtility',99.))
        self.reject(lambda r:r['envelope']['actions'][1].__setitem__('lowerUtility',99.))

    def test_no_frontend_commitment_shift_or_relabelled_ambiguous_raise(self):
        self.reject(lambda r:r['moves'][1].__setitem__('lowerEvBb',r['moves'][1]['lowerEvBb']+1.))
        self.reject(lambda r:r['moves'][2].__setitem__('classification','WITHIN_LIMIT'))
        self.reject(lambda r:r['moves'][2].__setitem__('upperLossBb',.001))
        self.reject(lambda r:r.__setitem__('evScope','CURRENT_DECISION_PROFIT'))

    def test_whole_public_game_quality_cannot_be_private_conditioned(self):
        self.reject(lambda r:r['policies'][0].__setitem__('publicRootQualityScope','PRIVATE_HAND_GAME'))
        self.reject(lambda r:r['policies'][0].__setitem__('publicRootReach',1/3))
        self.reject(lambda r:r['policies'][1]['fullParentQuality'].__setitem__('nashConvBb',99.))

    def test_policy_identity_and_security_face_membership_are_recomputed(self):
        self.reject(lambda r:r['policies'][1].__setitem__('solutionHash','0'*64))
        self.reject(lambda r:r['policies'][1].__setitem__('iterations',4000))
        self.reject(lambda r:r['policies'][1].__setitem__('insideDeclaredFace',False))
        self.reject(lambda r:r['policies'][1].__setitem__('inclusionMarginBb',99.))

    def test_forced_values_losses_and_regret_are_recomputed(self):
        self.reject(lambda r:r['policies'][1]['actionUtilitiesBb'].__setitem__('call',99.))
        self.reject(lambda r:r['policies'][1]['actionLossBb'].__setitem__('call',99.))
        self.reject(lambda r:r['policies'][1].__setitem__('decisionRegretBb',99.))
        self.reject(lambda r:r['policies'][1].__setitem__('maximumBoundViolationBb',99.))

    def test_all_references_and_qualification_reasons_are_required(self):
        self.reject(lambda r:r['policies'].pop())
        self.reject(lambda r:r.__setitem__('feedbackSupported',False))
        self.reject(lambda r:r.__setitem__('rejectionReasons',['invented']))
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))

    def test_rejected_evidence_is_retained_without_claiming_numerical_infeasibility(self):
        r=copy.deepcopy(self.report);r.update(envelope=None,rejection=dict(kind='LP',reason='WORK_LIMIT',detail='Synthetic parsing control'),
            moves=[],policies=[],completeOuterBounds=False,feedbackSupported=False,rejectionReasons=['INCOMPLETE_ROOT_ENVELOPE'])
        receipt=check.check_values(self.request,r)
        self.assertFalse(receipt['completeOuterBounds']);self.assertFalse(receipt['feedbackSupported'])
        self.assertEqual('WORK_LIMIT',receipt['retainedTypedFailure']['reason'])
        r['feedbackSupported']=True
        with self.assertRaises(ValueError):check.check_values(self.request,r)
        r['feedbackSupported']=False;r['rejection']['reason']='INFEASIBLE'
        with self.assertRaises(ValueError):check.check_values(self.request,r)

    def test_nonfinite_values_and_boolean_numeric_fields_are_rejected(self):
        self.reject(lambda r:r['policies'][1].__setitem__('decisionRegretBb',float('nan')))
        self.reject(lambda r:r['moves'][1].__setitem__('upperEvBb',float('inf')))
        self.reject(lambda r:r['context'].__setitem__('toCallBb',True))

    def test_json_duplicate_aliases_and_stored_expanded_caps_are_checked(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bad.json'
            for raw in ['{"a":1,"a":2}','{"a":1,"\\u0061":2}','{"a":NaN}','{"a":1e999}']:
                p.write_text(raw)
                with self.assertRaises(ValueError):check.read(p)
            p.write_bytes(b' '*33)
            with self.assertRaises(ValueError):check.read(p,32)
            p=Path(temp)/'bad.gz';p.write_bytes(gzip.compress(b' '*100))
            with self.assertRaises(ValueError):check.read(p,32)


if __name__=='__main__':unittest.main()
