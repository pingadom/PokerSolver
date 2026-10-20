"""Semantic corruption, bounds and complete-source controls for the four-question BTN drill."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('paired_drill_check', ROOT / 'scripts/check-paired-flop-drill.py')
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
spec.loader.exec_module(checker)
REQUEST = ROOT / 'docs/data/supplied-paired-four-hand-curriculum-request.json'
REPORT = ROOT / 'docs/data/supplied-paired-four-hand-curriculum-report.json.gz'
REVIEW = ROOT / 'docs/data/supplied-paired-four-hand-flop-drill.json'


class PairedDrillCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(gzip.decompress(REPORT.read_bytes()))
        cls.review = json.loads(REVIEW.read_bytes())

    def test_complete_source_then_review_matches_saved_bound_receipt(self):
        receipt = checker.check(REQUEST, REPORT, REVIEW)
        self.assertEqual(32, receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(12, receipt['sourcePolicyContextChecks'])
        self.assertEqual(4, receipt['sourceQuestionsChecked'])
        self.assertEqual(8, receipt['attemptsChecked'])
        self.assertEqual(0, receipt['freshLpSolves'] + receipt['freshCfrRuns'])
        self.assertFalse(receipt['trainerAdmission'])
        saved = json.loads((ROOT / 'docs/data/supplied-paired-four-hand-flop-drill-independent-check.json').read_bytes())
        self.assertEqual(saved, receipt)

    def test_all_four_contexts_metadata_feedback_and_counts_are_semantically_bound(self):
        cases = [(['reportHash'], '0' * 64), (['selection'], 'other'),
                 (['counts', 'distinctQuestionsAttempted'], 8), (['counts', 'withinLimit'], 8),
                 (['counts', 'strategyDependent'], True), (['counts', 'outsideLimit'], 4.0),
                 (['attempts', 0, 'index'], 1), (['attempts', 0, 'questionIndex'], 4),
                 (['attempts', 0, 'questionIndex'], False), (['attempts', 0, 'action'], 'bet')]
        for i in range(4):
            cases += [(['questions', i, key], value) for key, value in [
                ('heroCards', 'forged'), ('hero', 'BB'), ('questionIndex', (i + 1) % 4),
                ('trainerAdmission', 0), ('publicationStatus', 'GENERAL_GTO'), ('reportHash', '0' * 64),
                ('selection', 'other'), ('informationSet', 'other'), ('model', 'other'),
                ('chanceModel', 'other'), ('beliefs', 'other'), ('sourceHistoryReachStatus', 'KNOWN'),
                ('maximumLossBb', .02), ('securitySlackBb', .01), ('minimumReach', 0),
                ('evScope', 'other'), ('reachUnits', 'other')]]
            cases += [(['questions', i, 'context', 'potBb'], 100),
                      (['questions', i, 'context', 'opponentCards'], '7d 7h'),
                      (['attempts', i, 'feedback', 'questionIndex'], (i + 1) % 4),
                      (['attempts', i, 'feedback', 'reportHash'], '0' * 64),
                      (['attempts', i, 'feedback', 'selected', 'upperEvBb'], 100),
                      (['attempts', i, 'feedback', 'alternatives', 1, 'upperLossBb'], 100),
                      (['attempts', i, 'feedback', 'trainerAdmission'], True)]
        for keys, value in cases:
            with self.subTest(path=keys):
                changed = copy.deepcopy(self.review)
                cursor = changed
                for key in keys[:-1]:
                    cursor = cursor[key]
                cursor[keys[-1]] = value
                with self.assertRaises(ValueError):
                    checker.verify_review(self.report, changed)
        changed = copy.deepcopy(self.review)
        changed['questions'].reverse()
        with self.assertRaises(ValueError):
            checker.verify_review(self.report, changed)
        changed = copy.deepcopy(self.review)
        changed['attempts'].reverse()
        with self.assertRaises(ValueError):
            checker.verify_review(self.report, changed)

    def test_empty_hundred_and_hundred_one_attempts_count_contexts_separately(self):
        changed = copy.deepcopy(self.review)
        changed['attempts'] = []
        changed['counts'] = dict(distinctQuestionsAttempted=0, withinLimit=0, outsideLimit=0, strategyDependent=0)
        self.assertEqual(0, checker.verify_review(self.report, changed))
        attempt = self.review['attempts'][4]
        changed['attempts'] = [dict(attempt, index=i) for i in range(100)]
        changed['counts'] = dict(distinctQuestionsAttempted=1, withinLimit=100, outsideLimit=0, strategyDependent=0)
        self.assertEqual(100, checker.verify_review(self.report, changed))
        changed['attempts'].append(dict(attempt, index=100))
        with self.assertRaisesRegex(ValueError, 'attempt cap'):
            checker.verify_review(self.report, changed)

    def test_review_parser_rejects_duplicates_nonfinite_trailing_and_stored_expanded_caps(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'review.json'
            for bad in [b'{"x":1,"x":2}', b'{"x":NaN}', b'{} {}', b' ' * (checker.MAX_BYTES + 1)]:
                path.write_bytes(bad)
                with self.assertRaises(ValueError):
                    checker.read_review(path)
            compressed = Path(folder) / 'review.json.gz'
            compressed.write_bytes(gzip.compress(b' ' * (checker.MAX_BYTES + 1)))
            with self.assertRaisesRegex(ValueError, 'Expanded'):
                checker.read_review(compressed)
            compressed.write_bytes(gzip.compress(REVIEW.read_bytes()))
            self.assertEqual(self.review, checker.read_review(compressed)[2])


if __name__ == '__main__':
    unittest.main()
