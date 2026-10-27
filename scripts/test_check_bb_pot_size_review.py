"""Strict review parsing and cached regrade controls for the two pot-size questions."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('pot_review_test',Path(__file__).with_name('check-bb-pot-size-review.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)

class PotSizeReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check();cls.review=check.read_review(check.REVIEW)[0]
        cls.report=check.source.read(check.source.REPORT)[0];cls.pins=check.source.read(check.source.PINS)[0]

    def test_all_four_public_grades_match_the_owned_source(self):
        r=self.receipt['reviewCheck']
        self.assertEqual(r['attempts'],4);self.assertEqual(r['questionsAttempted'],2)
        self.assertEqual(r['classifications'],dict(WITHIN_LIMIT=2,OUTSIDE_LIMIT=2,STRATEGY_DEPENDENT=0))
        self.assertFalse(self.receipt['trainerAdmission'])
        text=json.dumps(self.review)
        for hidden in ['originalJointPrior','dealtCombos','Ah Kh','2c 3c']:self.assertNotIn(hidden,text)

    def test_pin_prices_moves_contexts_and_counts_are_regraded(self):
        for kind in ['pin','price','ev','loss','cards','index','action','count','admission']:
            r=copy.deepcopy(self.review);f=r['attempts'][0]['feedback']
            if kind=='pin':r['reportHash']='0'*64
            if kind=='price':f['question']['table']['betSizeBb']=3.25
            if kind=='ev':f['selected']['lowerEvBb']=0
            if kind=='loss':f['selected']['upperLossBb']=0
            if kind=='cards':f['question']['heroCards']='Ac Jc'
            if kind=='index':f['question']['sourceIndex']=5
            if kind=='action':f['action']='check'
            if kind=='count':r['summary']['withinLimit']=4
            if kind=='admission':r['trainerAdmission']=True
            with self.subTest(kind=kind),self.assertRaises(ValueError):check.source.check_review(r,self.report,self.pins)

    def test_duplicate_nonfinite_trailing_and_expansion_inputs_reject(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bad.json'
            for text in ['{"a":0,"\\u0061":1}','{"a":NaN}','{"a":1e999}','{}{}']:
                path.write_text(text,encoding='utf-8')
                with self.assertRaises(ValueError):check.read_review(path)
            path.write_bytes(b' '*(check.LIMIT+1))
            with self.assertRaises(ValueError):check.read_review(path)
            zipped=Path(folder)/'bad.json.gz';zipped.write_bytes(gzip.compress(b' '*(check.LIMIT+1)))
            with self.assertRaises(ValueError):check.read_review(zipped)

    def test_gzip_round_trip_and_canonical_order_remain_distinct(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json.gz';path.write_bytes(gzip.compress(check.REVIEW.read_bytes()))
            self.assertEqual(check.read_review(path)[0],self.review)
            r=copy.deepcopy(self.review);r['attempts'][0],r['attempts'][1]=r['attempts'][1],r['attempts'][0]
            with self.assertRaises(ValueError):check.source.check_review(r,self.report,self.pins)

if __name__=='__main__':unittest.main()
