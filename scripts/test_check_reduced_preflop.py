"""Reduced-plan payoff equivalence, original certificates and retained budget failure corruption controls."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('reduced_preflop_test',Path(__file__).with_name('check-reduced-preflop.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
class ReducedPreflopTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT);cls.receipt=check.check()
    def reject(self,change):
        r=copy.deepcopy(self.report);change(r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):check.check_values(self.request,r)
    def audit(self,r):return r['endpoints'][1]['utility']['diagnostic']
    def test_complete_original_parent_erasure_oracle_and_successful_certificates(self):
        r=self.receipt;self.assertEqual(r,check.read(check.DATA/'supplied-reduced-preflop-independent-check.json'))
        self.assertEqual(1303,r['fullCartesianPlansChecked']);self.assertEqual(57,r['reducedPlansChecked']);self.assertEqual(11,r['originalEndpointCertificatesChecked'])
        self.assertEqual([1,4,52],[c['reducedPlansChecked'] for c in r['coverage']]);self.assertEqual(0,r['freshLpSolves']);self.assertFalse(r['completeMenu'])
    def test_no_action_or_original_raise_failure_can_be_discarded(self):
        self.reject(lambda r:r['endpoints'].pop())
        self.reject(lambda r:r['endpoints'][2].__setitem__('rejection',None))
        self.reject(lambda r:r['endpoints'][2]['rejection'].__setitem__('reason','INFEASIBLE'))
        self.reject(lambda r:r.__setitem__('complete',True))
    def test_every_reduced_plan_and_original_payoff_coefficient_is_checked(self):
        self.reject(lambda r:self.audit(r)['numeratorPlans'].pop())
        self.reject(lambda r:self.audit(r)['numeratorPlans'][0]['cost'].__setitem__(0,99))
        self.reject(lambda r:self.audit(r)['numeratorPlans'][0]['continuation'].__setitem__('invented-hero-row','fold'))
        self.reject(lambda r:r['cartesianPlanCounts'].__setitem__('raise:9.0',52))
    def test_original_certificate_point_dual_and_endpoint_bounds_are_checked(self):
        self.reject(lambda r:self.audit(r)['upperWitnesses'][0]['point'].__setitem__(0,99))
        self.reject(lambda r:self.audit(r)['lowerWitness']['dual'].__setitem__(0,99))
        self.reject(lambda r:r['endpoints'][1]['utility'].__setitem__('upperBb',99))
        self.reject(lambda r:self.audit(r).__setitem__('algorithm','legacy'))
    def test_root_reach_cannot_be_scaled_by_physical_flop_chance(self):
        self.reject(lambda r:r['endpoints'][1]['utility'].__setitem__('rawCertifiedReachLowerBound',.00003))
        self.reject(lambda r:self.audit(r)['lowerWitness']['posterior'][0].__setitem__('probability',.9))
        self.reject(lambda r:self.audit(r)['lowerWitness']['opponentFlow']['realization'].__setitem__(0,0))
        self.reject(lambda r:self.audit(r)['lowerWitness'].__setitem__('globalHeroBestResponse',99))
    def test_scope_prices_work_and_admission_cannot_be_relabelled(self):
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))
        self.reject(lambda r:r.__setitem__('reachUnits','GIVEN_FLOP'))
        self.reject(lambda r:r['context']['actions'][2].__setitem__('amountBb',9.))
        self.reject(lambda r:self.audit(r)['work'].__setitem__('intervalLpSolves',1))

if __name__=='__main__':unittest.main()
