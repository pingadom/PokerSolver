import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('root_minimax_checker', Path(__file__).with_name('check-root-minimax.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class RootMinimaxCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.controls = checker.read(checker.FIXTURE)[0]

    def corrupt(self, change):
        data = copy.deepcopy(self.controls); change(data)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError)): checker.check_values(data)

    def test_all_original_certificates_and_literal_control_trees(self):
        result = checker.check_values(self.controls)
        self.assertEqual((10, 5, 9), (result['exactParentCertificates'], result['exactMinimaxCertificates'], result['reducedPlansChecked']))
        self.assertEqual(0, result['physicalPokerGames'])

    def test_rejects_removed_or_duplicated_controls(self):
        self.corrupt(lambda d: d.pop())
        self.corrupt(lambda d: d.__setitem__(1, d[0]))

    def test_rejects_false_full_interval_or_admission_claim(self):
        self.corrupt(lambda d: d[0]['audit'].__setitem__('scope', 'FULL_ACTION_INTERVAL'))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('trainerAdmission', True))

    def test_rejects_changed_literal_game_and_oriented_payoff(self):
        self.corrupt(lambda d: d[0]['game']['initialState']['children'][1]['children'][0]['utilities'].__setitem__(3, 42.))
        self.corrupt(lambda d: d[0]['audit']['orientedPayoff'].__setitem__('constant', 99.))

    def test_rejects_changed_parent_security_certificate(self):
        self.corrupt(lambda d: d[0]['audit']['firstParentWitness']['point'].__setitem__(0, 999.))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('globalHeroUpperValue', 100.))
        self.corrupt(lambda d: d[0]['audit'].__setitem__('securitySlack', .01))

    def test_rejects_changed_matrix_objective_primal_and_dual(self):
        self.corrupt(lambda d: d[0]['audit']['problem']['matrix'][0].__setitem__(0, 99.))
        self.corrupt(lambda d: d[0]['audit']['problem']['cost'].__setitem__(-1, 1.))
        self.corrupt(lambda d: d[0]['audit']['witness']['point'].__setitem__(-1, -1.))
        self.corrupt(lambda d: d[0]['audit']['witness']['dual'].__setitem__(0, -1.))

    def test_rejects_missing_or_forged_reduced_continuations(self):
        self.corrupt(lambda d: d[-1]['audit']['plans'].pop())
        self.corrupt(lambda d: d[-1]['audit']['plans'][0].__setitem__('constant', 42.))
        self.corrupt(lambda d: d[-1]['audit'].__setitem__('reducedPlans', 65))

    def test_rejects_changed_minimum_bracket_and_work_claims(self):
        self.corrupt(lambda d: d[0]['audit']['minimum'].__setitem__('upperBound', 50.))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('endpointLpSolves', 0))
        self.corrupt(lambda d: d[0]['audit']['work'].__setitem__('compilerLimit', 8000001))

    def test_rejects_posterior_flow_and_best_response_changes(self):
        self.corrupt(lambda d: d[0]['audit']['support'][0].__setitem__('posteriorProbability', .5))
        self.corrupt(lambda d: d[0]['audit']['witness']['opponentFlow']['realization'].__setitem__(0, .5))
        self.corrupt(lambda d: d[0]['audit']['witness'].__setitem__('conditionalHeroBestResponse', 100.))


if __name__ == '__main__': unittest.main()
