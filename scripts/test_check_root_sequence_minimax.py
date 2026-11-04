import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('sequence_minimum_checker', Path(__file__).with_name('check-root-sequence-minimax.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class RootSequenceMinimumCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.controls = checker.old.read(checker.FIXTURE)[0]

    def corrupt(self, change):
        data = copy.deepcopy(self.controls); change(data)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError, ZeroDivisionError)):
            checker.check_values(data)

    def test_all_original_certificates_closed_forms_and_large_flow_controls(self):
        result = checker.check_values(self.controls)
        self.assertEqual((8, 16, 8), (result['controls'], result['exactParentCertificates'], result['exactEndpointCertificates']))
        self.assertEqual(2**32, result['largeControlCompletePlanCount'])
        self.assertEqual((0, 0, 0), (result['freshLpSolves'], result['freshCfrRuns'], result['physicalPokerGames']))

    def test_frozen_fixture_and_immutable_helpers(self):
        result = checker.check()
        self.assertEqual(checker.FIXTURE_SHA, result['fixtureSha256'])
        self.assertEqual(checker.HELPER_SHA, result['immutableHelpersSha256']['check-root-minimax.py'])

    def test_rejects_removed_duplicate_or_changed_controls(self):
        self.corrupt(lambda d: d.pop())
        self.corrupt(lambda d: d.__setitem__(1, d[0]))
        self.corrupt(lambda d: d[0].__setitem__('expected', 42.))

    def test_rejects_full_interval_admission_or_relaxed_gate_claims(self):
        for key, value in [('scope', 'FULL_ACTION_INTERVAL'), ('trainerAdmission', True), ('algorithm', 'old'), ('securitySlack', .01), ('requiredMinimumReach', .000001)]:
            with self.subTest(key=key): self.corrupt(lambda d: d[0]['audit'].__setitem__(key, value))

    def test_rejects_changed_tree_hidden_information_or_orientation(self):
        self.corrupt(lambda d: d[0]['game']['initialState']['children'][1]['children'][0]['utilities'].__setitem__(3, 50.))
        self.corrupt(lambda d: d[5]['game']['initialState']['children'][1]['children'][0]['children'][0].__setitem__('key', '5:leaked-private-0'))
        self.corrupt(lambda d: d[2]['audit']['question'].__setitem__('hero', 3))
        self.corrupt(lambda d: d[0]['audit']['question'].__setitem__('action', 'safe'))
        self.corrupt(lambda d: d[5]['game']['initialState']['children'][1]['children'][0]['children'][0]['children'][0]['utilities'].__setitem__(3, True))

    def test_rejects_parent_certificate_or_global_face_changes(self):
        self.corrupt(lambda d: d[0]['audit']['firstParentWitness']['point'].__setitem__(0, 50.))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('globalHeroUpperValue', 100.))
        self.corrupt(lambda d: d[0]['audit']['orientedPayoff'].__setitem__('constant', 50.))
        self.corrupt(lambda d: d[0]['audit']['problem']['matrix'][0].__setitem__(0, 99.))

    def test_rejects_missing_or_weakened_forced_action_constraints(self):
        self.corrupt(lambda d: d[0]['audit']['heroDomain']['matrix'].pop())
        self.corrupt(lambda d: d[0]['audit']['heroDomain']['rhs'].__setitem__(-1, 1.))
        self.corrupt(lambda d: d[0]['audit']['heroDomain'].__setitem__('forcedSequence', 0))
        self.corrupt(lambda d: d[0]['audit']['problem']['cost'].__setitem__(-1, 1.))

    def test_rejects_wrong_conditional_matrix_reach_or_posterior(self):
        self.corrupt(lambda d: d[-1]['audit'].__setitem__('originalQuestionReach', 1.))
        self.corrupt(lambda d: d[-1]['audit']['support'][0].__setitem__('posteriorProbability', .25))
        self.corrupt(lambda d: d[0]['audit']['conditionalPayoff'].__setitem__('constant', 10.))
        self.corrupt(lambda d: d[0]['audit']['problem'].__setitem__('objectiveConstant', 10.))

    def test_rejects_endpoint_primal_dual_or_block_inventory_changes(self):
        self.corrupt(lambda d: d[0]['audit']['witness']['point'].__setitem__(0, -1.))
        self.corrupt(lambda d: d[0]['audit']['witness']['dual'].__setitem__(0, -1.))
        self.corrupt(lambda d: d[0]['audit']['problem'].__setitem__('securityRows', 0))
        self.corrupt(lambda d: d[0]['audit']['problem'].__setitem__('continuationDualVariables', 0))

    def test_rejects_opponent_flow_hero_flow_or_behavioral_response_changes(self):
        self.corrupt(lambda d: d[0]['audit']['witness']['opponentFlow']['realization'].__setitem__(0, .5))
        self.corrupt(lambda d: d[0]['audit']['continuationFlow']['realization'].__setitem__(d[0]['audit']['heroDomain']['forcedSequence'], 0.))
        self.corrupt(lambda d: d[0]['audit']['witness'].__setitem__('globalHeroBestResponse', 100.))
        self.corrupt(lambda d: d[0]['audit']['witness'].__setitem__('conditionalHeroBestResponse', 100.))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('continuationUtility', 100.))

    def test_rejects_forged_brackets_plan_enumeration_and_work_totals(self):
        self.corrupt(lambda d: d[0]['audit']['minimum'].__setitem__('upperBound', 100.))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('enumeratedContinuationPlans', 1))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointLpSolves', 0))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('compilerLimit', 8000001))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointPivots', 0))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointArithmeticWork', 0))

    def test_rejects_forged_actor_labels_joint_identity_or_coerced_counters(self):
        self.corrupt(lambda d: d[0]['audit']['baseline']['firstFlow'].__setitem__('actor', 5))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('continuationPolicyHash', '0'*64))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('enumeratedContinuationPlans', False))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('compilerLimit', 8_000_000.))


if __name__ == '__main__': unittest.main()
