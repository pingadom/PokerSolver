"""Independent full report replay and adversarial controls for the later offline drill."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('later_drill_check', ROOT / 'scripts/check-later-bb-flop-drill.py')
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
spec.loader.exec_module(checker)
PREFIX = ROOT / 'docs/data/supplied-four-button-hand-later-bb-flop'
REQUEST = Path(str(PREFIX) + '-feedback-request.json')
REPORT = Path(str(PREFIX) + '-feedback-report.json.gz')
DRILL = Path(str(PREFIX) + '-drill.json')


class LaterDrillCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads(gzip.decompress(REPORT.read_bytes()))
        cls.review = json.loads(DRILL.read_bytes())

    def test_saved_full_report_and_drill_match_byte_bound_receipt(self):
        receipt = checker.check(REQUEST, REPORT, DRILL)
        self.assertEqual(json.loads(Path(str(PREFIX) + '-drill-independent-check.json').read_bytes()), receipt)
        self.assertEqual(8, receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(2, receipt['attemptsChecked'])
        self.assertFalse(receipt['trainerAdmission'])
        self.assertEqual(0, receipt['freshLpSolves'])

    def test_question_grades_context_and_counts_cannot_be_supplied_by_caller(self):
        cases = [
            (['schemaVersion'], 'pokerlab-supplied-flop-drill/v1'),
            (['question', 'reportHash'], '0' * 64),
            (['question', 'trainerAdmission'], True),
            (['question', 'heroCards'], 'Ah Ad'),
            (['question', 'model'], 'unrestricted'),
            (['question', 'beliefs'], 'independent'),
            (['question', 'evScope'], 'incremental'),
            (['question', 'reachUnits'], 'raw'),
            (['question', 'minimumReach'], 0),
            (['question', 'maximumLossBb'], 1),
            (['question', 'context', 'toCallBb'], 0),
            (['question', 'context', 'flopHistory', 1, 'action'], 'check'),
            (['question', 'context', 'opponentCards'], '9s Js'),
            (['attempts', 0, 'index'], 1),
            (['attempts', 0, 'action'], 'check'),
            (['attempts', 0, 'action'], 'call'),
            (['attempts', 0, 'feedback', 'selected', 'upperLossBb'], .5),
            (['attempts', 1, 'feedback', 'selected', 'classification'], 'WITHIN_LIMIT'),
            (['attempts', 0, 'feedback', 'alternatives', 1, 'lowerEvBb'], 5),
            (['attempts', 0, 'feedback', 'reportHash'], '0' * 64),
            (['attempts', 0, 'feedback', 'trainerAdmission'], True),
            (['attempts', 0, 'feedback', 'extra'], 'hidden'),
            (['counts', 'withinLimit'], 2),
            (['counts', 'accuracy'], 1),
            (['attempts'], [self.review['attempts'][0]] * 101),
        ]
        for keys, value in cases:
            with self.subTest(keys=keys):
                changed = copy.deepcopy(self.review)
                cursor = changed
                for key in keys[:-1]:
                    cursor = cursor[key]
                cursor[keys[-1]] = value
                with self.assertRaises(ValueError):
                    checker.verify_review(self.report, changed)

    def test_empty_and_hundred_explicit_attempts_are_valid(self):
        empty = dict(self.review, attempts=[], counts=dict(withinLimit=0, outsideLimit=0, strategyDependent=0))
        self.assertEqual(0, checker.verify_review(self.report, empty))
        attempts = [dict(self.review['attempts'][0], index=i) for i in range(100)]
        full = dict(self.review, attempts=attempts, counts=dict(withinLimit=100, outsideLimit=0, strategyDependent=0))
        self.assertEqual(100, checker.verify_review(self.report, full))

    def test_stored_and_expanded_drill_caps_and_gzip(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'review.json'
            path.write_bytes(b' ' * (checker.MAX_BYTES + 1))
            with self.assertRaisesRegex(ValueError, 'Stored later drill exceeds cap'):
                checker.read_drill(path)
            path = path.with_suffix('.gz')
            path.write_bytes(gzip.compress(b' ' * (checker.MAX_BYTES + 1)))
            with self.assertRaisesRegex(ValueError, 'Expanded later drill exceeds cap'):
                checker.read_drill(path)
            path.write_bytes(gzip.compress(DRILL.read_bytes()))
            self.assertEqual(self.review, checker.read_drill(path)[1])


if __name__ == '__main__':
    unittest.main()
