"""Exact rational controls for the recorded conditional and parent pivot errors."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('pivot_exact_check', ROOT / 'scripts/check-lp-pivot-regressions.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
FILES = [ROOT / f'solver/src/test/resources/lp-btn-pivot-{label}.json.gz' for label in ('ak', 'qt')] + [ROOT / 'solver/src/test/resources/lp-parent-pivot-paired.json.gz']


class PivotRegressionTest(unittest.TestCase):
    def test_both_previous_bases_are_full_rank_but_chosen_exact_direction_is_zero(self):
        for path, size in zip(FILES, (85, 86, 81), strict=True):
            with self.subTest(path=path):
                result = checker.check(path)
                self.assertEqual(size, result['priorBasisExactRank'])
                self.assertEqual(size - 1, result['exactRank'])
                self.assertEqual('0', result['exactLeavingDirection'])
                self.assertGreater(result['computedLeavingDirection'], result['eligibilityEps'])
                self.assertEqual(size, result['exactNullProductsChecked'])
                self.assertEqual(size, result['exactDirectionResidualRowsChecked'])

    def test_changed_direction_or_threshold_cannot_claim_the_recorded_failure(self):
        original = json.loads(gzip.decompress(FILES[0].read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'changed.json'
            for kind in ('direction', 'threshold', 'matrix'):
                changed = copy.deepcopy(original)
                if kind == 'direction':
                    changed['computedDirection'][changed['leavingRow']] = 0
                elif kind == 'threshold':
                    changed['directionEligibilityEps'] = 1
                else:
                    n = len(changed['originalBasis'])
                    changed['originalBasis'] = [[int(i == j) for j in range(n)] for i in range(n)]
                path.write_text(json.dumps(changed), encoding='utf-8')
                with self.subTest(kind=kind), self.assertRaises(ValueError):
                    checker.check(path)

    def test_stored_and_expanded_byte_caps_are_independent(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'large.json'
            path.write_bytes(b'x' * (8 * 1024 * 1024 + 1))
            with self.assertRaisesRegex(ValueError, 'input exceeds cap'):
                checker.check(path)
            path = Path(folder) / 'large.json.gz'
            path.write_bytes(gzip.compress(b' ' * (8 * 1024 * 1024 + 1)))
            with self.assertRaisesRegex(ValueError, 'Expanded'):
                checker.check(path)


if __name__ == '__main__':
    unittest.main()
