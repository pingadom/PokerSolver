"""Corruption controls for the joint size-menu tree and independently checked evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('size_menu_test', Path(__file__).with_name('check-flop-size-menu.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class SizeMenuEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request = check.read(check.REQUEST)
        cls.report = check.read(check.REPORT)
        cls.receipt = check.check()

    def reject(self, mutate):
        report = copy.deepcopy(self.report)
        mutate(report)
        with self.assertRaises((ValueError, KeyError, IndexError)):
            check.check_values(self.request, report)

    def test_receipt_covers_physics_perfect_recall_both_certificates_and_all_policies(self):
        self.assertEqual(self.receipt, check.read(check.DATA / 'supplied-flop-size-menu-independent-check.json'))
        self.assertEqual(self.receipt['literalTreeStates'], 1609)
        self.assertEqual(self.receipt['policiesChecked'], 3)
        self.assertEqual(self.receipt['originalLpCertificatesChecked'], 2)
        self.assertFalse(self.receipt['trainerAdmission'])

    def test_menu_cannot_be_replaced_by_separate_fixed_size_games(self):
        self.reject(lambda r: r['binding']['menu']['firstToAct'].pop())
        self.reject(lambda r: r['request']['menu']['firstToAct'].__setitem__(0, .5))
        self.reject(lambda r: r.__setitem__('trainerAdmission', True))
        self.reject(lambda r: r['binding']['budget'].__setitem__('completeTreeStates', 1321))

    def test_size_actions_and_recall_history_cannot_be_collapsed(self):
        def drop(r):
            row = next(row for key, row in r['candidate']['strategy'].items() if 'bet:0.25' in row)
            row['bet'] = row.pop('bet:0.25')
        self.reject(drop)
        self.reject(lambda r: r['solve']['firstFlow']['conservation'][0].__setitem__('parentSequence', 999))
        self.reject(lambda r: r['candidate']['strategy'].pop(next(iter(r['candidate']['strategy']))))

    def test_prior_dead_cards_full_counts_residuals_and_physical_mass_cannot_change(self):
        self.reject(lambda r: r['originalJointPrior'][0].__setitem__('conditionalProbability', 1/23))
        self.reject(lambda r: r['sourcePayoffs'][0]['counts'].__setitem__('firstWins', 1))
        self.reject(lambda r: r['handoffs'][0]['worlds'][0]['sixDealtCombos'].__setitem__(0, '6d 2c'))
        self.reject(lambda r: r['handoffs'][0].__setitem__('physicalFlopMass', 1))
        self.reject(lambda r: r['residuals'][0]['counts'].__setitem__('firstWins', 0))

    def test_lp_witness_payoff_and_reported_quality_corruptions_fail(self):
        self.reject(lambda r: r['firstLpWitness']['point'].__setitem__(0, 999))
        self.reject(lambda r: r['secondLpWitness']['dual'].__setitem__(0, 999))
        self.reject(lambda r: r['solve']['projectedPayoff'].__setitem__('constant', 99))
        self.reject(lambda r: r['solve']['behavioralQuality'].__setitem__('nashConvBb', 1))
        self.reject(lambda r: r['solve']['firstLp']['work'].__setitem__('arithmeticWork', 4_000_000_001))

    def test_fresh_policies_work_and_inactive_proofs_cannot_be_forged(self):
        self.reject(lambda r: r['references'].pop())
        self.reject(lambda r: r['references'][0]['policy'].__setitem__('iterations', 160000))
        self.reject(lambda r: r['references'][0]['statistics'].__setitem__('visitedNodes', 0))
        self.reject(lambda r: r['references'][0]['inactiveUtilities'].__setitem__('4', 0))
        self.reject(lambda r: r['references'][0]['quality'].__setitem__('nashConvBb', 0))
        def negative(r):
            row = next(iter(r['references'][0]['policy']['strategy'].values()))
            row[next(iter(row))] = -.1
        self.reject(negative)

    def test_strict_bounded_json_rejects_duplicate_nonfinite_trailing_and_oversized(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bad.json'
            for value in ['{"x":1,"x":2}', '{"x":1e999}', '{"x":NaN}', '{} {}', ' ' * (8*1024*1024+1)]:
                path.write_text(value)
                with self.assertRaises(ValueError):
                    check.read(path)


if __name__ == '__main__':
    unittest.main()
