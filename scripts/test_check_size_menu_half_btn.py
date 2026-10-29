"""Negative-reach certificate, likelihood, inventory and authority corruption controls."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('half_btn_test',Path(__file__).with_name('check-size-menu-half-btn.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)

class HalfBtnEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT);cls.receipt=check.check()
    def reject(self,change):
        value=copy.deepcopy(self.report);change(value)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):check.check_values(self.request,value)
    def audit(self,r,index=0):return r['cases'][index]['endpoints'][0]['rejection']['reachFailure']
    def test_complete_negative_inventory_matches_published_receipt(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-size-menu-btn-half-independent-check.json'))
        self.assertEqual(8,self.receipt['negativeReachCertificatesChecked']);self.assertEqual(48,self.receipt['rejectedWorldMassesChecked'])
        self.assertEqual([],self.receipt['qualifiedIndices']);self.assertEqual(0,self.receipt['conditionalUtilityCertificatesChecked'])
        self.assertEqual(0,self.receipt['freshLpSolves']);self.assertEqual(0,self.receipt['freshCfrRuns'])
    def test_no_hand_action_or_public_price_can_be_replaced(self):
        self.reject(lambda r:r['cases'].pop())
        self.reject(lambda r:r['cases'][0]['endpoints'].pop())
        self.reject(lambda r:r['cases'][0]['context'].__setitem__('toCallBb',1.625))
        self.reject(lambda r:r['cases'][1]['context']['flopHistory'][0].__setitem__('action','bet:0.25'))
    def test_original_certificate_point_dual_and_algorithm_are_checked(self):
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['point'].__setitem__(0,999))
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['dual'].__setitem__(0,999))
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['certificate'].__setitem__('algorithm','wrong'))
    def test_opponent_call_half_bet_flow_and_all_unnormalized_worlds_are_checked(self):
        self.reject(lambda r:self.audit(r)['denominator'].__setitem__('constant',1))
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['masses'][0].__setitem__('behavioralWeight',1))
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['masses'].pop())
        self.reject(lambda r:self.audit(r)['minimumReachWitness']['opponentFlow']['realization'].__setitem__(0,0))
    def test_global_security_reach_status_and_work_cannot_be_forged(self):
        self.reject(lambda r:self.audit(r)['minimumReachWitness'].__setitem__('globalHeroBestResponse',99))
        self.reject(lambda r:self.audit(r).__setitem__('certifiedReachLowerBound',.001))
        self.reject(lambda r:self.audit(r).__setitem__('status','CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR'))
        self.reject(lambda r:self.audit(r)['work'].__setitem__('intervalLpSolves',0))
    def test_no_conditional_posterior_grade_or_authority_can_be_created(self):
        self.reject(lambda r:self.audit(r)['minimumReachWitness'].__setitem__('posterior',[]))
        self.reject(lambda r:r['cases'][0].__setitem__('moves',[{}]))
        self.reject(lambda r:r['cases'][0].__setitem__('qualifiedForOfflineDrill',True))
        self.reject(lambda r:r.__setitem__('qualifiedIndices',[0]))
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))

if __name__=='__main__':unittest.main()
