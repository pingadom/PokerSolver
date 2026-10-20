"""Four source proofs, ten typed contexts and strict mixed-review corruption controls."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('mixed_check',ROOT/'scripts/check-expanded-flop-interval-collection.py')
checker=importlib.util.module_from_spec(spec);sys.modules[spec.name]=checker;spec.loader.exec_module(checker)
def data(suffix):return ROOT/f'docs/data/supplied-ten-question-flop-collection-{suffix}.json'

class ExpandedCollectionCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.review=json.loads(data('review').read_bytes())
        cls.drills=[json.loads(p.read_bytes()) for p in checker.DRILLS]
    def test_full_independent_source_proofs_match_bound_receipt(self):
        receipt=checker.check(data('manifest'),data('review'))
        self.assertEqual(json.loads(data('independent-check').read_bytes()),receipt)
        self.assertEqual(99,receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(30,receipt['sourcePolicyContextChecks']);self.assertEqual(10,receipt['sourceQuestionsChecked'])
        self.assertEqual(20,receipt['attemptsChecked']);self.assertEqual(4,len(receipt['sourceReceipts']))
        self.assertEqual(0,receipt['freshLpSolves']+receipt['freshCfrRuns']);self.assertFalse(receipt['trainerAdmission'])
    def test_typed_inventory_and_all_six_original_protocols_are_exact(self):
        cases=[(['schemaVersion'],'pokerlab-flop-interval-collection-review/v1'),(['collectionHash'],'0'*64),(['selection'],'random'),
               (['questions'],self.review['questions'][:-1]),(['questions'],list(reversed(self.review['questions']))),
               (['questions',6,'decision','paired','context','board'],['2s','8s','Ts']),
               (['questions',6,'decision','paired'],self.review['questions'][2]['decision']['btn']),
               (['questions',7,'decision','paired','questionIndex'],0),
               (['attempts',0,'index'],1),(['attempts',0,'questionIndex'],False),(['attempts',0,'questionIndex'],10),
               (['attempts',0,'action'],'call'),(['attempts',1,'action'],'check'),
               (['counts','distinctQuestionsAttempted'],12),(['counts','outsideLimit'],6.0),(['counts','withinLimit'],True),
               (['counts','accuracy'],1),(['attempts'],[self.review['attempts'][0]]*101)]
        for i in range(10):
            member='initial' if i==0 else 'later' if i==1 else 'btn' if i<6 else 'paired'
            cases += [(['questions',i,'index'],False),(['questions',i,'sourceQuestionIndex'],4),
                      (['questions',i,'decision','kind'],'UNKNOWN'),(['questions',i,'decision',member],None),
                      (['questions',i,'decision',member,'trainerAdmission'],True),
                      (['questions',i,'decision',member,'context','potBb'],100),
                      (['questions',i,'decision',member,'context','opponentCards'],'secret'),
                      (['attempts',i,'feedback','sourceQuestionIndex'],4),(['attempts',i,'feedback','questionIndex'],(i+1)%10),
                      (['attempts',i,'feedback','decision',member,'reportHash'],'0'*64),
                      (['attempts',i,'feedback','decision',member,'selected','upperEvBb'],100),
                      (['attempts',i,'feedback','decision',member,'alternatives',0,'upperLossBb'],100),
                      (['attempts',i,'feedback','extra'],1)]
        for keys,value in cases:
            with self.subTest(keys=keys):
                changed=copy.deepcopy(self.review);cursor=changed
                for key in keys[:-1]:cursor=cursor[key]
                cursor[keys[-1]]=value
                with self.assertRaises(ValueError):checker.verify_review(changed,self.drills)
    def test_each_btn_local_index_must_match_the_collection_index(self):
        for i in range(2,6):
            changed=copy.deepcopy(self.review)
            changed['questions'][i]['decision']['btn']=copy.deepcopy(self.review['questions'][2+(i-1)%4]['decision']['btn'])
            with self.assertRaises(ValueError):checker.verify_review(changed,self.drills)
            changed=copy.deepcopy(self.review)
            changed['attempts'][i]['feedback']['decision']['btn']=copy.deepcopy(self.review['attempts'][2+(i-1)%4]['feedback']['decision']['btn'])
            with self.assertRaises(ValueError):checker.verify_review(changed,self.drills)
    def test_zero_and_hundred_attempts_count_distinct_contexts_honestly(self):
        changed=copy.deepcopy(self.review);changed['attempts']=[]
        changed['counts']=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
        self.assertEqual(0,checker.verify_review(changed,self.drills))
        changed['attempts']=[dict(self.review['attempts'][19],index=i) for i in range(100)]
        changed['counts']=dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
        self.assertEqual(100,checker.verify_review(changed,self.drills))
    def test_invalid_manifest_rejects_before_any_source_checker_runs(self):
        manifest=json.loads(data('manifest').read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'manifest.json'
            for kind in ['version','order','drop','path','pin','unknown','budget']:
                changed=copy.deepcopy(manifest)
                if kind=='version':changed['schemaVersion']='old'
                if kind=='order':changed['sources'].reverse()
                if kind=='drop':changed['sources'].pop()
                if kind=='path':changed['sources'][2]['requestFile']='../other.json'
                if kind=='pin':changed['sources'][2]['reportHash']='0'*64
                if kind=='unknown':changed['qualified']=True
                if kind=='budget':changed['startupBudget']['freshIterations']=491999
                path.write_text(json.dumps(changed),encoding='utf-8')
                with patch.object(checker.old.initial,'check',side_effect=AssertionError('source checker must not run')):
                    with self.assertRaisesRegex(ValueError,'Fixed mixed manifest'):checker.check(path,data('review'))
    def test_bounded_stored_expanded_duplicate_and_nonfinite_parsing(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json';path.write_bytes(b' '*(1024*1024+1))
            with self.assertRaisesRegex(ValueError,'Stored mixed'):checker.read(path,1024*1024)
            zipped=path.with_suffix('.gz');zipped.write_bytes(gzip.compress(b' '*(1024*1024+1)))
            with self.assertRaisesRegex(ValueError,'Expanded mixed'):checker.read(zipped,1024*1024)
            zipped.write_bytes(gzip.compress(data('review').read_bytes()));self.assertEqual(self.review,checker.read(zipped,1024*1024)[2])
            for raw in [b'{"index":0,"index":1}',b'{"value":NaN}',b'{"value":Infinity}',b'{} {}']:
                path.write_bytes(raw)
                with self.assertRaises(ValueError):checker.read(path,64*1024)

if __name__=='__main__':unittest.main()
