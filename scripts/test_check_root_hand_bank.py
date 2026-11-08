"""Independent three-hand inventory, physical/certificate and corruption controls."""
import copy
import gzip
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('root_bank_checker',Path(__file__).with_name('check-root-hand-bank.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class RootHandBankChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST,128*1024)
        cls.report=check.read(check.REPORT)
        cls.receipt=check.check()

    def reject(self,change):
        r=copy.deepcopy(self.report);change(r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):
            check.check_values(self.request,r)

    def test_full_receipt_fifteen_certificates_and_twenty_seven_values(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-root-hand-bank-independent-check.json'))
        self.assertEqual(check.HANDS,self.receipt['originalPrivateHands'])
        self.assertEqual(15,self.receipt['originalEnvelopeCertificatesChecked'])
        self.assertEqual(27,self.receipt['forcedContinuationValuesChecked'])
        self.assertEqual(27,self.receipt['decisionLossesChecked'])
        self.assertEqual([0,1,2],self.receipt['supportedQuestionIndices'])
        self.assertFalse(self.receipt['trainerAdmission']);self.assertFalse(self.receipt['gradingAuthority'])
        self.assertEqual(0,self.receipt['freshLpSolves']);self.assertEqual(0,self.receipt['freshCfrRuns'])

    def test_request_cannot_drop_reorder_duplicate_or_invent_hands(self):
        for value in [check.HANDS[:2],list(reversed(check.HANDS)),[check.HANDS[0]]*3,check.HANDS+['As Ah']]:
            request=copy.deepcopy(self.request);request['heroCards']=value
            with self.assertRaises(ValueError):check.check_values(request,self.report)
        for field,value in [('hero',True),('hero',3),('maximumLossBb',.02),('parentStoredSha256','0'*64)]:
            request=copy.deepcopy(self.request);request[field]=value
            with self.assertRaises(ValueError):check.check_values(request,self.report)

    def test_sevens_and_nines_results_exactly_match_existing_standalone_sources(self):
        previous=check.read(check.DATA/'supplied-sevens-root-study-report.json.gz')
        for field in ['context','envelope','moves','policies','rejectionReasons']:
            self.assertEqual(previous[field],self.report['assessments'][0][field])
        previous=check.read(check.DATA/'supplied-root-envelope-feedback-report.json.gz')
        current=self.report['assessments'][1]
        self.assertEqual(previous['envelope']['envelope'],current['envelope'])
        self.assertEqual(previous['moves'],current['moves'])
        self.assertEqual([previous['primary'],*previous['references']],current['policies'])

    def test_complete_shared_parent_and_original_physics_are_preserved(self):
        self.assertEqual(24,self.receipt['parentCheck']['physicalWorlds'])
        self.assertEqual(15792192,self.receipt['parentCheck']['fullBoardsRegenerated'])
        self.reject(lambda r:r['parent']['request']['input']['worlds'][0].__setitem__('weight',2.))
        self.reject(lambda r:r['parent']['references'].pop())
        self.reject(lambda r:r['request'].__setitem__('unknown',0))

    def test_assessment_slots_cannot_be_dropped_swapped_or_relabelled(self):
        self.reject(lambda r:r['assessments'].pop())
        self.reject(lambda r:r['assessments'].reverse())
        self.reject(lambda r:r['assessments'][2].__setitem__('questionIndex',0))
        self.reject(lambda r:r['assessments'][0].__setitem__('questionIndex',False))
        self.reject(lambda r:r['assessments'][2].__setitem__('heroCards','7d 7h'))

    def test_each_hand_retains_six_seat_chips_and_full_action_costs(self):
        for i in range(3):
            self.reject(lambda r:r['assessments'][i]['context'].__setitem__('potBb',5.))
            self.reject(lambda r:r['assessments'][i]['context']['actions'][2].__setitem__('amountBb',9.))
            self.reject(lambda r:r['assessments'][i]['context']['players'][5].__setitem__('remainingStackBb',100.))

    def test_cross_hand_envelopes_and_chance_renormalization_are_rejected(self):
        self.reject(lambda r:r['assessments'][2].__setitem__('envelope',r['assessments'][0]['envelope']))
        self.reject(lambda r:r['assessments'][2]['envelope'].__setitem__('questionChanceMass',1.))
        self.reject(lambda r:r['assessments'][1]['envelope']['support'].pop())
        self.reject(lambda r:r['assessments'][2]['envelope']['support'][0].__setitem__('chanceWeight',.1))

    def test_per_hand_limits_and_cumulative_reservations_are_independent(self):
        self.reject(lambda r:r['maximumWork'].__setitem__('freshIterations',720000))
        self.reject(lambda r:r['maximumWork'].__setitem__('physicalGames',True))
        self.reject(lambda r:r['maximumWork'].__setitem__('maximumQuestions',2))
        self.reject(lambda r:r['assessments'][2]['envelope']['work'].__setitem__('compilerLimit',24000000))
        self.reject(lambda r:r['assessments'][2]['envelope']['work'].__setitem__('lpArithmeticWork',0))

    def test_completed_work_totals_never_claim_unreturned_failed_work(self):
        for key,value in [('scope','ALL_ATTEMPTED_WORK'),('completedEnvelopes',2),('envelopeLps',10),
                          ('compilerUnits',0),('pivots',0),('arithmetic',0)]:
            self.reject(lambda r:r['completedWork'].__setitem__(key,value))
        self.reject(lambda r:r.__setitem__('supportedQuestionIndices',[0,1]))
        self.reject(lambda r:r.__setitem__('completeOuterBounds',False))

    def test_certificate_vectors_flows_and_objectives_are_checked_for_every_hand(self):
        for i in range(3):
            self.reject(lambda r:r['assessments'][i]['envelope']['guaranteeWitness']['point'].__setitem__(0,99.))
            self.reject(lambda r:r['assessments'][i]['envelope']['questionUpperWitness']['dual'].__setitem__(0,99.))
            self.reject(lambda r:r['assessments'][i]['envelope']['actions'][2]['lowerWitness'].__setitem__('optimalContinuationUtility',99.))

    def test_complete_pure_policy_and_conservative_action_bounds_are_reconstructed(self):
        self.reject(lambda r:r['assessments'][2]['envelope']['fixedHeroChoices'].pop(next(iter(r['assessments'][2]['envelope']['fixedHeroChoices']))))
        self.reject(lambda r:r['assessments'][1]['envelope'].__setitem__('securityAllowance',99.))
        self.reject(lambda r:r['assessments'][2]['envelope']['actions'][1].__setitem__('lowerUtility',99.))
        self.reject(lambda r:r['assessments'][0]['envelope']['actions'][2].__setitem__('literalUtilityCeiling',99.))

    def test_twenty_seven_forced_values_losses_and_terminal_ev_scope_are_checked(self):
        for i in range(3):
            self.reject(lambda r:r['assessments'][i]['policies'][1]['actionUtilitiesBb'].__setitem__('call',99.))
            self.reject(lambda r:r['assessments'][i]['policies'][1]['actionLossBb'].__setitem__('call',99.))
        self.reject(lambda r:r['assessments'][2]['moves'][1].__setitem__('lowerEvBb',1.1531738258424213))
        self.reject(lambda r:r.__setitem__('evScope','CURRENT_DECISION_PROFIT'))

    def test_public_game_quality_face_membership_and_policy_identity_are_not_relabelled(self):
        for field,value in [('solutionHash','0'*64),('publicRootQualityScope','PRIVATE_HAND_GAME'),
                            ('publicRootReach',1/3),('insideDeclaredFace',False),('inclusionMarginBb',99.)]:
            self.reject(lambda r:r['assessments'][2]['policies'][1].__setitem__(field,value))
        self.reject(lambda r:r['assessments'][1]['policies'].pop())
        self.reject(lambda r:r['assessments'][0].__setitem__('feedbackSupported',False))
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))

    def test_partial_failure_keeps_all_slots_and_original_reservation(self):
        r=copy.deepcopy(self.report);a=r['assessments'][2];removed=a['envelope']['work']
        a.update(envelope=None,rejection=dict(kind='LP',reason='WORK_LIMIT',detail='Synthetic parser control only'),
                 moves=[],policies=[],completeOuterBounds=False,feedbackSupported=False,rejectionReasons=['INCOMPLETE_ROOT_ENVELOPE'])
        r.update(completeOuterBounds=False,supportedQuestionIndices=[0,1]);w=r['completedWork']
        w.update(completedEnvelopes=2,envelopeLps=10,compilerUnits=w['compilerUnits']-removed['compilerUnits'],
                 pivots=w['pivots']-removed['lpPivots'],arithmetic=w['arithmetic']-removed['lpArithmeticWork'])
        receipt=check.check_values(self.request,r)
        self.assertEqual(3,len(receipt['assessments']));self.assertFalse(receipt['completeOuterBounds'])
        self.assertEqual('WORK_LIMIT',receipt['assessments'][2]['retainedTypedFailure']['reason'])
        self.assertEqual(check.BUDGET,r['maximumWork'])
        a['feedbackSupported']=True
        with self.assertRaises(ValueError):check.check_values(self.request,r)
        a['feedbackSupported']=False;a['rejection']['reason']='INFEASIBLE'
        with self.assertRaises(ValueError):check.check_values(self.request,r)

    def test_duplicate_aliases_nonfinite_numbers_and_byte_caps_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bad.json'
            for raw in ['{"a":1,"a":2}','{"a":1,"\\u0061":2}','{"a":NaN}','{"a":1e999}']:
                p.write_text(raw)
                with self.assertRaises(ValueError):check.read(p)
            p.write_bytes(b' '*33)
            with self.assertRaises(ValueError):check.read(p,32)
            p=Path(temp)/'bad.gz';p.write_bytes(gzip.compress(b' '*100))
            with self.assertRaises(ValueError):check.read(p,32)
        self.reject(lambda r:r['assessments'][2]['policies'][1].__setitem__('decisionRegretBb',float('nan')))


if __name__=='__main__':unittest.main()
