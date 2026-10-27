"""Corruption controls for all three original-face conditional intervals."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('conditional_size_test', Path(__file__).with_name('check-size-menu-conditional.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class SizeMenuConditionalEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request = check.read(check.REQUEST)
        cls.report = check.read(check.REPORT)
        cls.receipt = check.check()

    def reject(self, mutation):
        report = copy.deepcopy(self.report)
        mutation(report)
        with self.assertRaises((ValueError, KeyError, IndexError)):
            check.check_values(self.request, report)

    def test_complete_original_face_certificates_and_continuations_match_receipt(self):
        self.assertEqual(self.receipt, check.read(check.DATA / 'supplied-size-menu-99-conditional-independent-check.json'))
        self.assertEqual(self.receipt['conditionalCertificatesChecked'], 13)
        self.assertEqual(self.receipt['continuationPlans'], [2, 1, 1])
        self.assertFalse(self.receipt['trainerAdmission'])

    def test_complete_menu_cannot_drop_or_replace_size(self):
        self.reject(lambda r: r['actions'].pop())
        self.reject(lambda r: r['actions'][1].__setitem__('action', 'bet'))
        self.reject(lambda r: r['context']['actions'][1].__setitem__('amountBb', 3.25))
        self.reject(lambda r: r['context'].__setitem__('actingSeat', 'BTN'))

    def test_hero_reach_physical_scaling_and_original_posterior_are_checked(self):
        self.reject(lambda r: r['actions'][0]['diagnostic']['fixedHeroPast'].clear())
        self.reject(lambda r: r['actions'][0]['diagnostic']['denominator'].__setitem__('constant', 1))
        self.reject(lambda r: r['actions'][0].__setitem__('rawCertifiedReachLowerBound', 1/3))
        self.reject(lambda r: r['actions'][0]['diagnostic']['minimumReachWitness']['posterior'][0].__setitem__('probability', 1))
        self.reject(lambda r: r['actions'][0]['diagnostic'].__setitem__('requiredMinimumReach', 1e-12))

    def test_parent_security_face_and_policy_provenance_cannot_be_replaced(self):
        self.reject(lambda r: r['parent']['binding']['menu']['firstToAct'].pop())
        self.reject(lambda r: r['actions'][0]['diagnostic'].__setitem__('securitySlack', 1e-4))
        self.reject(lambda r: r['actions'][0]['diagnostic'].__setitem__('globalHeroUpperValue', 100))
        self.reject(lambda r: r['parent']['references'][0]['quality'].__setitem__('nashConvBb', 1))

    def test_all_lp_points_duals_and_work_counters_are_independently_checked(self):
        self.reject(lambda r: r['actions'][1]['diagnostic']['lowerWitness']['point'].__setitem__(0, 999))
        self.reject(lambda r: r['actions'][2]['diagnostic']['upperWitnesses'][0]['dual'].__setitem__(0, 999))
        self.reject(lambda r: r['actions'][0]['diagnostic']['work'].__setitem__('intervalLpSolves', 1))
        self.reject(lambda r: r['actions'][0]['diagnostic']['minimumReachWitness'].__setitem__('globalHeroBestResponse', 999))

    def test_ev_loss_and_continuation_envelopes_cannot_be_forged(self):
        self.reject(lambda r: r['actions'][0].__setitem__('lowerUtilityBb', 100))
        self.reject(lambda r: r['actions'][0].__setitem__('upperLossBb', 0))
        self.reject(lambda r: r['actions'][0]['diagnostic']['numeratorPlans'].pop())
        self.reject(lambda r: r['actions'][0]['diagnostic']['numeratorPlans'][0]['cost'].__setitem__(0, 100))

    def test_ambiguous_actions_keep_their_intervals_and_no_trainer_authority(self):
        moves = {m['action']: m for m in self.receipt['moves']}
        self.assertEqual(moves['bet:0.25']['classification'], 'WITHIN_LIMIT')
        self.assertLess(moves['bet:0.25']['upperLossBb'], .001)
        self.assertEqual(moves['check']['classification'], 'STRATEGY_DEPENDENT')
        self.assertEqual(moves['bet:0.5']['classification'], 'STRATEGY_DEPENDENT')
        self.reject(lambda r: r.__setitem__('trainerAdmission', True))


if __name__ == '__main__':
    unittest.main()
