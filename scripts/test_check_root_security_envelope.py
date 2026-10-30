"""Corruption controls for separate global guarantees and conservative root bounds."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('root_envelope_test', Path(__file__).with_name('check-root-security-envelope.py'))
check = importlib.util.module_from_spec(spec); spec.loader.exec_module(check)


class RootEnvelopeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request = check.read(check.REQUEST); cls.report = check.read(check.REPORT); cls.receipt = check.check()

    def reject(self, change):
        report = copy.deepcopy(self.report); change(report)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError)):
            check.check_values(self.request, report)

    def test_complete_literal_coefficients_five_certificates_and_stored_receipt(self):
        self.assertEqual(self.receipt, check.read(check.DATA/'supplied-root-envelope-independent-check.json'))
        self.assertEqual(5, self.receipt['originalEnvelopeCertificatesChecked'])
        self.assertEqual(33, self.receipt['completePureHeroRows'])
        self.assertFalse(self.receipt['attainedEndpointClaims']); self.assertFalse(self.receipt['gradingAuthority'])
        self.assertEqual(0, self.receipt['freshLpSolves']); self.assertEqual(0, self.receipt['freshCfrRuns'])

    def test_global_guarantee_domain_and_original_certificate_cannot_be_replaced(self):
        self.reject(lambda r: r['envelope']['guaranteeWitness'].__setitem__('domain', 'FULL_OPPONENT_SECURITY_FACE'))
        self.reject(lambda r: r['envelope']['guaranteeWitness']['point'].__setitem__(0, 99.))
        self.reject(lambda r: r['envelope']['guaranteeWitness']['dual'].__setitem__(0, 99.))
        self.reject(lambda r: r['envelope']['questionUpperWitness']['dual'].__setitem__(0, 99.))

    def test_policy_selection_requires_every_original_hero_information_set(self):
        self.reject(lambda r: r['envelope']['fixedHeroChoices'].pop(next(iter(r['envelope']['fixedHeroChoices']))))
        self.reject(lambda r: r['envelope']['fixedHeroChoices'].__setitem__('invented-row', 'call'))
        self.reject(lambda r: r['envelope'].__setitem__('fixedPolicyAlgorithm', 'PARENT_MIXED_POLICY'))
        self.reject(lambda r: r['envelope']['globalFixedPolicy']['cost'].__setitem__(0, 99.))

    def test_root_support_probability_and_action_prices_use_original_joint_game(self):
        self.reject(lambda r: r['envelope'].__setitem__('questionChanceMass', .00003))
        self.reject(lambda r: r['envelope']['support'].pop())
        self.reject(lambda r: r['envelope']['support'][0].__setitem__('chanceWeight', .1))
        self.reject(lambda r: r['context']['actions'][2].__setitem__('amountBb', 9.))

    def test_global_allowance_and_literal_action_bounds_are_recomputed(self):
        self.reject(lambda r: r['envelope'].__setitem__('guaranteedGlobalLower', 99.))
        self.reject(lambda r: r['envelope'].__setitem__('securityAllowance', 99.))
        self.reject(lambda r: r['envelope']['actions'][2].__setitem__('upperUtility', 99.))
        self.reject(lambda r: r['envelope']['actions'][2].__setitem__('lowerUtility', 99.))
        self.reject(lambda r: r['envelope']['actions'][0].__setitem__('literalUtilityCeiling', 99.))

    def test_forced_continuations_and_original_flow_are_independent_of_linear_claims(self):
        self.reject(lambda r: r['envelope']['actions'][2]['fixedContinuation']['cost'].__setitem__(0, 99.))
        self.reject(lambda r: r['envelope']['actions'][2]['lowerWitness'].__setitem__('optimalContinuationUtility', 99.))
        self.reject(lambda r: r['envelope']['actions'][1]['lowerWitness'].__setitem__('globalHeroBestResponse', 99.))
        self.reject(lambda r: r['envelope']['questionUpperWitness']['opponentFlow']['realization'].__setitem__(0, 0.))

    def test_no_exact_endpoint_or_grading_authority_or_missing_move(self):
        self.reject(lambda r: r.__setitem__('trainerAdmission', True))
        self.reject(lambda r: r['envelope'].__setitem__('scope', 'ATTAINED_ENDPOINTS'))
        self.reject(lambda r: r['envelope']['actions'].pop())
        self.reject(lambda r: r.__setitem__('completeOuterBounds', False))
        self.reject(lambda r: r['envelope'].__setitem__('securitySlack', .001))

    def test_work_sums_parent_lineage_and_shared_caps_are_preserved(self):
        self.reject(lambda r: r['envelope']['work'].__setitem__('lpSolves', 3))
        self.reject(lambda r: r['envelope']['work'].__setitem__('lpArithmeticWork', 0))
        self.reject(lambda r: r['envelope']['work'].__setitem__('compilerLimit', 16_000_000))
        self.reject(lambda r: r['parent']['references'].pop())


if __name__ == '__main__':
    unittest.main()
