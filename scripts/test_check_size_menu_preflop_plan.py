"""Preflop inventory, hidden-world plan aggregation, prices, provenance and zero-work controls."""
import copy
import importlib.util
from pathlib import Path
import sys
import unittest

spec=importlib.util.spec_from_file_location('preflop_plan_check',Path(__file__).with_name('check-size-menu-preflop-plan.py'))
check=importlib.util.module_from_spec(spec);sys.modules[spec.name]=check;spec.loader.exec_module(check)

class PreflopPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT)
    def reject(self,change):
        r=copy.deepcopy(self.report);change(r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):check.check_values(self.request,r)
    def test_complete_independent_receipt_and_zero_numerical_work(self):
        r=check.check();self.assertEqual(r,check.read(check.DATA/'supplied-size-menu-preflop-plan-independent-check.json'))
        self.assertEqual((17,48,41,7,1296),(r['preflopQuestionsChecked'],r['preflopActionsChecked'],r['withinPlanLimitActions'],r['overPlanLimitActions'],r['maximumCartesianPlans']))
        self.assertEqual(10,len(r['completeMenuQuestionIndices']));self.assertEqual(0,r['freshLpSolves']);self.assertEqual(0,r['enumeratedBoards'])
    def test_every_hand_move_and_world_stays_in_the_inventory(self):
        self.reject(lambda r:r['questions'].pop())
        self.reject(lambda r:r['questions'][0]['actions'].pop())
        self.reject(lambda r:r['questions'][8]['originalWorldIndices'].pop())
        self.reject(lambda r:r['questions'].reverse())
    def test_exact_hidden_information_set_plan_product_is_not_per_deal(self):
        self.reject(lambda r:r['questions'][8]['actions'][2].__setitem__('cartesianPlans',64))
        self.reject(lambda r:r['questions'][8]['actions'][2]['futureHeroDecisions'].pop())
        self.reject(lambda r:r['questions'][8]['actions'][2]['futureHeroDecisions'][0]['actions'].reverse())
        self.reject(lambda r:r['questions'][0]['actions'][1]['futureHeroDecisions'][0].__setitem__('informationSet','private-world-0'))
    def test_public_prices_full_history_and_six_seat_context_are_checked(self):
        self.reject(lambda r:r['questions'][8]['context'].__setitem__('potBb',6.5))
        self.reject(lambda r:r['questions'][8]['context']['actions'][2].__setitem__('amountBb',9.))
        self.reject(lambda r:r['questions'][0]['context']['history'].pop())
        self.reject(lambda r:r['questions'][0]['context']['players'][0].__setitem__('cards','Ac Ad'))
    def test_capacity_and_chance_mass_cannot_be_relabelled_as_qualification(self):
        self.reject(lambda r:r.__setitem__('continuationPlanLimit',1296))
        self.reject(lambda r:r['questions'][8]['actions'][2].__setitem__('withinPlanLimit',True))
        self.reject(lambda r:r['questions'][8].__setitem__('allMovesWithinPlanLimit',True))
        self.reject(lambda r:r.__setitem__('chanceMassScope','ON_POLICY_QUESTION_REACH'))
        self.reject(lambda r:r.__setitem__('trainerAdmission',True))
    def test_source_hash_future_work_and_performed_work_are_distinct(self):
        self.reject(lambda r:r.__setitem__('sourceRequestHash','0'*64))
        self.reject(lambda r:r['work'].__setitem__('lpSolves',1))
        self.reject(lambda r:r['estimatedPhysicalBudget'].__setitem__('additionalEnumeratedRunouts',0))
        self.reject(lambda r:r['questions'][8]['actions'][2].__setitem__('enumerationPrechargeUnits',1296))
        self.reject(lambda r:r['questions'][8].__setitem__('priorChanceMass',1.))

if __name__=='__main__':unittest.main()
