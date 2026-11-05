import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('sequence_menu_checker', Path(__file__).with_name('check-root-sequence-feedback.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class RootSequenceFeedbackCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.verified = checker.context()
        cls.request, cls.report = checker.read(checker.REQUEST), checker.read(checker.REPORT)

    def corrupt(self, change):
        q, r = copy.deepcopy(self.request), copy.deepcopy(self.report); change(q, r)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError)): checker.check_values(q, r, self.verified)

    def test_complete_sources_menu_and_qualification(self):
        r = checker.check_values(self.request, self.report, self.verified)
        self.assertTrue(r['qualifiedForOfflineDrill']); self.assertEqual(36, r['utilityAndLossBoundsChecked'])
        self.assertAlmostEqual(.574637464148319, self.report['moves'][2]['upperLossBb'])
        self.assertEqual('STRATEGY_DEPENDENT', self.report['moves'][2]['classification'])
        self.assertFalse(r['trainerAdmission'] or r['minimumUpperUsedAsActionCeiling'] or r['currentDecisionEvShiftApplied'])
        self.assertEqual((0, 0), (r['freshLpSolves'], r['freshCfrRuns']))

    def test_rejects_changed_request_source_pin_and_threshold(self):
        self.corrupt(lambda q, _: q.__setitem__('minimumStoredSha256', '0'*64))
        self.corrupt(lambda q, _: q.__setitem__('maximumLossBb', .02))
        self.corrupt(lambda q, _: q['minimumRequest'].__setitem__('action', 'call'))

    def test_rejects_false_scope_admission_and_qualification(self):
        self.corrupt(lambda _, r: r.__setitem__('boundsScope', 'EXACT_ACTION_EVS'))
        self.corrupt(lambda _, r: r.__setitem__('trainerAdmission', True))
        self.corrupt(lambda _, r: r.__setitem__('qualifiedForOfflineDrill', False))

    def test_rejects_changed_original_parent_or_minimum_certificate(self):
        self.corrupt(lambda _, r: r['originalEnvelope']['moves'][2].__setitem__('upperEvBb', 100.))
        self.corrupt(lambda _, r: r['sourceMinimum']['minimum']['minimum'].__setitem__('lowerBound', 1.))
        self.corrupt(lambda _, r: r['sourceMinimum']['minimum']['witness']['point'].__setitem__(0, 100.))

    def test_rejects_using_minimum_upper_as_action_ceiling(self):
        self.corrupt(lambda _, r: r['moves'][2].__setitem__('upperEvBb', r['sourceMinimum']['minimum']['minimum']['upperBound']))

    def test_rejects_changed_loss_bounds_and_double_ev_shift(self):
        self.corrupt(lambda _, r: r['moves'][2].__setitem__('upperLossBb', 0.))
        self.corrupt(lambda _, r: r['moves'][0].__setitem__('lowerLossBb', 0.))
        self.corrupt(lambda _, r: r['moves'][0].__setitem__('lowerEvBb', r['moves'][0]['lowerEvBb']+1))

    def test_rejects_missing_duplicated_and_reordered_moves(self):
        self.corrupt(lambda _, r: r['moves'].pop())
        self.corrupt(lambda _, r: r['moves'].__setitem__(2, r['moves'][1]))
        self.corrupt(lambda _, r: r['moves'].reverse())

    def test_rejects_missing_reference_or_false_policy_bounds(self):
        self.corrupt(lambda _, r: r['policies'].pop())
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('maximumBoundViolationBb', .1))
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('boundsChecked', False))
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('iterations', 160000))

    def test_rejects_changed_quality_or_hidden_rejection_reasons(self):
        self.corrupt(lambda _, r: r['sourceMinimum']['policies'][0]['original'].__setitem__('publicRootNashConvBb', .1))
        self.corrupt(lambda _, r: r['policies'][0]['failures'].append('FULL_PARENT_GAP'))
        self.corrupt(lambda _, r: r['rejectionReasons'].append('NO_ROBUST_MOVE_WITHIN_LIMIT'))

    def test_outside_face_and_prior_failures_cannot_be_dropped(self):
        p = copy.deepcopy(self.report['sourceMinimum']['policies'][0])
        p['original']['insideDeclaredFace'] = False; p['minimumLowerSupported'] = False
        p['failures'] = ['FULL_PARENT_GAP', 'REFERENCE_OUTSIDE_DECLARED_FACE']
        r = checker.menu.assess(p, self.report['moves'])
        self.assertFalse(r['boundsChecked']); self.assertIsNone(r['maximumBoundViolationBb'])
        self.assertTrue(set(p['failures']).issubset(r['failures']))
        self.assertIn('MINIMUM_NOT_SUPPORTED', r['failures'])
        self.assertIn('MINIMUM_MENU_REFERENCE_OUTSIDE_FACE', r['failures'])


    def test_rejects_erased_tiny_legacy_difference_and_changed_classification(self):
        self.corrupt(lambda _, r: r['legacyMenuComparison'][2].__setitem__('lowerEvDifferenceBb', 0.))
        self.corrupt(lambda _, r: r['legacyMenuComparison'][2].__setitem__('classificationUnchanged', False))
        self.corrupt(lambda _, r: r['legacyMenuComparison'][2]['legacyMove'].__setitem__('lowerEvBb', 0.))
        self.corrupt(lambda _, r: r['legacyMenuComparison'].reverse())

    def test_exact_derived_fields_reject_boolean_numbers_and_nonfinite_values(self):
        self.corrupt(lambda _, r: r['moves'][1].__setitem__('lowerLossBb', False))
        self.corrupt(lambda _, r: r['moves'][1].__setitem__('lowerLossBb', float('nan')))
        self.corrupt(lambda _, r: r['moves'][1].__setitem__('upperLossBb', float('inf')))
        self.corrupt(lambda _, r: r['policies'][0].__setitem__('maximumBoundViolationBb', False))

    def test_rejects_extra_report_or_request_fields_and_missing_comparison(self):
        self.corrupt(lambda q, _: q.__setitem__('extra', 0))
        self.corrupt(lambda _, r: r.__setitem__('extra', 0))
        self.corrupt(lambda _, r: r.pop('legacyMenuComparison'))


if __name__ == '__main__': unittest.main()
