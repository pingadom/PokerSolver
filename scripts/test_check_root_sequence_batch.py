import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('root_batch_checker', Path(__file__).with_name('check-root-sequence-batch.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class RootSequenceBatchCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.controls = checker.old.read(checker.FIXTURE)[0]

    def corrupt(self, change):
        data = copy.deepcopy(self.controls); change(data)
        with self.assertRaises((ValueError, KeyError, TypeError, IndexError, ZeroDivisionError)):
            checker.check_values(data)

    def test_all_literal_games_original_certificates_and_closed_forms(self):
        r = checker.check_values(self.controls)
        self.assertEqual((2, 4, 10), (r['batches'], r['distinctParentCertificates'], r['endpointCertificates']))
        self.assertEqual(2**32, r['largeControlCompletePlanCount'])
        self.assertEqual((0, 0, 0, False), (r['freshLpSolves'], r['freshCfrRuns'], r['physicalPokerGames'], r['trainerAdmission']))

    def test_frozen_fixture_and_original_immutable_helpers(self):
        r = checker.check()
        self.assertEqual(checker.FIXTURE_SHA, r['fixtureSha256'])
        self.assertEqual(checker.HELPER_SHA, r['immutableHelpersSha256']['check-root-sequence-minimax.py'])

    def test_rejects_missing_duplicate_reordered_or_unknown_batches(self):
        self.corrupt(lambda d: d.pop())
        self.corrupt(lambda d: d.__setitem__(1, d[0]))
        self.corrupt(lambda d: d.reverse())
        self.corrupt(lambda d: d[0].__setitem__('unexpected', True))

    def test_rejects_missing_reordered_or_changed_root_choices(self):
        self.corrupt(lambda d: d[0]['audit']['minima'].pop())
        self.corrupt(lambda d: d[0]['audit']['minima'].reverse())
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['question'].__setitem__('action', 'safe'))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['question'].__setitem__('hero', 5))
        self.corrupt(lambda d: d[0]['expected'].__setitem__(0, 42.))

    def test_rejects_changed_literal_payoffs_hidden_information_or_chance(self):
        self.corrupt(lambda d: d[0]['game']['initialState']['probabilities'].__setitem__(0, .5))
        self.corrupt(lambda d: d[0]['game']['initialState']['children'][0]['children'][0]['utilities'].__setitem__(3, 9.))
        self.corrupt(lambda d: d[0]['game']['initialState']['children'][0]['children'][1]['children'][0].__setitem__('key', '3:leaked-world'))
        self.corrupt(lambda d: d[0]['game']['initialState']['probabilities'].__setitem__(0, True))

    def test_rejects_false_admission_full_interval_claims_and_relaxed_gates(self):
        for key, value in [('trainerAdmission', True), ('scope', 'FULL_ACTION_INTERVALS'), ('algorithm', 'UNOWNED')]:
            with self.subTest(key=key): self.corrupt(lambda d: d[0]['audit'].__setitem__(key, value))
        self.corrupt(lambda d: d[0]['audit']['minima'][0].__setitem__('securitySlack', .01))
        self.corrupt(lambda d: d[0]['audit']['minima'][0].__setitem__('requiredMinimumReach', .000001))

    def test_rejects_forged_shared_parent_or_game_identity(self):
        self.corrupt(lambda d: d[0]['audit'].__setitem__('snapshotHash', '0'*64))
        self.corrupt(lambda d: d[0]['audit']['minima'][1]['baseline'].__setitem__('snapshotHash', '0'*64))
        self.corrupt(lambda d: d[0]['audit']['minima'][1]['firstParentWitness']['point'].__setitem__(0, 20.))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('parentLpSolves', 16))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('parentSolveCalls', True))

    def test_rejects_forgotten_preflight_or_reset_per_question_budgets(self):
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('preflightCompilerUnits', 0))
        self.corrupt(lambda d: d[0]['audit']['minima'][1]['work'].__setitem__('compilerLimit', 8_000_000))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('compilerLimit', 16_000_000))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointPivotLimit', 20_000))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointArithmeticLimit', 8_000_000_000))

    def test_rejects_forged_aggregate_parent_and_endpoint_work(self):
        for key in ['parentCompilerUnits', 'parentLpArithmeticWork', 'compilerUnits', 'endpointLpSolves', 'endpointPivots', 'endpointArithmeticWork']:
            with self.subTest(key=key): self.corrupt(lambda d: d[0]['audit']['work'].__setitem__(key, 0))
        self.corrupt(lambda d: d[1]['audit']['work'].__setitem__('enumeratedContinuationPlans', 2**32))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointPivots', False))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['witness']['certificate']['work'].__setitem__('phaseOnePivots', True))

    def test_rejects_changed_global_security_and_forced_action_domain(self):
        self.corrupt(lambda d: d[0]['audit']['minima'][0].__setitem__('globalHeroUpperValue', 100.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['problem']['matrix'][0].__setitem__(0, 99.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['heroDomain']['rhs'].__setitem__(-1, 1.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['heroDomain']['matrix'].pop())

    def test_rejects_changed_posterior_support_original_flow_and_response(self):
        self.corrupt(lambda d: d[0]['audit']['minima'][0].__setitem__('originalQuestionReach', 1.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['support'][0].__setitem__('stateHash', '0'*64))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['support'][0].__setitem__('posteriorProbability', .5))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['continuationFlow']['realization'].__setitem__(0, .5))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['witness'].__setitem__('conditionalHeroBestResponse', 100.))

    def test_rejects_primal_dual_bracket_and_scope_metadata_corruptions(self):
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['witness']['point'].__setitem__(0, -1.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['witness']['dual'].__setitem__(0, -1.))
        self.corrupt(lambda d: d[0]['audit']['minima'][0]['minimum'].__setitem__('lowerBound', float('nan')))
        self.corrupt(lambda d: d[0]['audit']['minima'][0].__setitem__('continuationPolicyHash', '0'*64))


if __name__ == '__main__': unittest.main()
