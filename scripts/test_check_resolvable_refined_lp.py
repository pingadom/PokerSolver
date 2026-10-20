"""Exact binary64 control evidence and corrupted certificate rejection."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('resolvable_refined_check',ROOT/'scripts/check-resolvable-refined-lp.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class ResolvableRefinedLpCheckTest(unittest.TestCase):
    def test_all_298_cases_match_the_independent_receipt(self):
        expected=json.loads((ROOT/'docs/data/resolvable-refined-lp-independent-check.json').read_text())
        self.assertEqual(expected,checker.check())
        self.assertEqual(298,expected['casesChecked'])
        self.assertFalse(expected['trainerAdmission'])

    def test_semantic_mutations_fail_without_a_saved_evidence_byte_pin(self):
        original=json.loads(gzip.decompress(checker.EVIDENCE.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['mode','admission','point','dual','objective','dimension','work','pivot','negative-work','missing','duplicate','unknown']:
                value=copy.deepcopy(original);row=value['cases'][0];c=row['certificate']
                if kind=='mode':c['algorithm']='legacy'
                elif kind=='admission':value['trainerAdmission']=True
                elif kind=='point':row['point'][0]=-100
                elif kind=='dual':row['dual'][0]=-100
                elif kind=='objective':c['primalValue']+=1
                elif kind=='dimension':row['dual'].pop()
                elif kind=='work':c['work']['arithmeticWork']=4_000_000_001
                elif kind=='pivot':c['work']['phaseTwoPivots']=10001
                elif kind=='negative-work':c['work']['arithmeticWork']=-1
                elif kind=='missing':value['cases'].pop()
                elif kind=='duplicate':value['cases'][1]=copy.deepcopy(row)
                elif kind=='unknown':row['id']='unknown'
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(path)

    def test_duplicate_nonfinite_and_stored_or_expanded_oversize_reject(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bad.json'
            for raw in [b'{"cases":[],"cases":[]}',b'{"cases":NaN}',b'{"cases":1e999}',b'x'*(checker.LIMIT+1)]:
                path.write_bytes(raw)
                with self.assertRaises(ValueError):checker.read(path)
            path=Path(folder)/'large.gz';path.write_bytes(gzip.compress(b' '*(checker.LIMIT+1)))
            with self.assertRaises(ValueError):checker.read(path)

if __name__=='__main__':unittest.main()
