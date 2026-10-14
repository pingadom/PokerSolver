"""Physical source checks, byte-bound receipt and mixed-question corruption controls."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('flop_collection_check',ROOT/'scripts/check-flop-interval-collection.py')
checker=importlib.util.module_from_spec(spec)
sys.modules[spec.name]=checker
spec.loader.exec_module(checker)
PREFIX=ROOT/'docs/data/supplied-two-flop-interval-collection-'
def data(suffix): return Path(str(PREFIX)+suffix+'.json')

class FlopCollectionCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.review=json.loads(data('review').read_bytes())
        cls.drills=[json.loads(p.read_bytes()) for p in checker.DRILLS]
    def test_full_source_physics_and_every_saved_attempt_match_receipt(self):
        receipt=checker.check(data('manifest'),data('review'))
        self.assertEqual(json.loads(data('independent-check').read_bytes()),receipt)
        self.assertEqual(35,receipt['originalEndpointCertificatesChecked'])
        self.assertEqual(6,receipt['sourceDecisionPolicyChecks'])
        self.assertEqual(4,receipt['attemptsChecked'])
        self.assertFalse(receipt['trainerAdmission'])
        self.assertEqual(0,receipt['freshLpSolves'])
    def test_typed_inventory_grades_indices_and_counts_fail_closed(self):
        cases=[
            (['collectionHash'],'0'*64),(['schemaVersion'],'old'),(['selection'],'random'),
            (['questions',0,'index'],1),(['questions',0,'index'],False),(['questions',0,'totalQuestions'],2.0),
            (['questions',0,'decision','kind'],'BB_FACING_BET'),
            (['questions',0,'decision','later'],self.drills[1]['question']),
            (['questions',0,'decision','initial'],None),
            (['questions',1,'decision','later','context','toCallBb'],0),
            (['questions',0,'decision','initial','context','opponentCards'],'9s Js'),
            (['questions',1,'decision','later','reportHash'],'0'*64),
            (['attempts',0,'index'],1),(['attempts',0,'questionIndex'],True),(['attempts',0,'questionIndex'],1),
            (['attempts',1,'action'],'check'),(['attempts',2,'action'],'fold'),
            (['attempts',0,'feedback','collectionHash'],'0'*64),
            (['attempts',1,'feedback','questionIndex'],0),
            (['attempts',0,'feedback','decision','kind'],'BB_FACING_BET'),
            (['attempts',0,'feedback','decision','later'],self.drills[1]['attempts'][0]['feedback']),
            (['attempts',0,'feedback','decision','initial','selected','upperEvBb'],100),
            (['attempts',3,'feedback','decision','later','alternatives',1,'classification'],'WITHIN_LIMIT'),
            (['attempts',1,'feedback','decision','later','reportHash'],'0'*64),
            (['attempts',0,'feedback','extra'],0),(['counts','distinctQuestionsAttempted'],1),
            (['counts','withinLimit'],3),(['counts','outsideLimit'],2.0),(['counts','accuracy'],1),
            (['attempts'],[self.review['attempts'][0]]*101),
        ]
        for keys,value in cases:
            with self.subTest(keys=keys):
                review=copy.deepcopy(self.review); cursor=review
                for key in keys[:-1]: cursor=cursor[key]
                cursor[keys[-1]]=value
                with self.assertRaises(ValueError): checker.verify_review(review,self.drills)
    def test_empty_and_hundred_retries_have_honest_context_counts(self):
        review=copy.deepcopy(self.review); review['attempts']=[]
        review['counts']=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
        self.assertEqual(0,checker.verify_review(review,self.drills))
        review['attempts']=[dict(self.review['attempts'][1],index=i) for i in range(100)]
        review['counts']=dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
        self.assertEqual(100,checker.verify_review(review,self.drills))
    def test_manifest_corruptions_reject_before_source_checks(self):
        manifest=json.loads(data('manifest').read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'manifest.json'
            for change in ['order','pin','path','kind']:
                value=copy.deepcopy(manifest)
                if change=='order': value['sources'].reverse()
                if change=='pin': value['collectionHash']='0'*64
                if change=='path': value['sources'][0]['requestFile']='../other.json'
                if change=='kind': value['sources'][0]['kind']='BB_FACING_BET'
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.assertRaisesRegex(ValueError,'Fixed typed manifest'): checker.check(path,data('review'))
    def test_bounded_plain_gzip_and_duplicate_parsing(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json'
            path.write_bytes(b' '*(1024*1024+1))
            with self.assertRaisesRegex(ValueError,'Stored collection'): checker.read(path,1024*1024)
            path=path.with_suffix('.gz'); path.write_bytes(gzip.compress(b' '*(1024*1024+1)))
            with self.assertRaisesRegex(ValueError,'Expanded collection'): checker.read(path,1024*1024)
            path.write_bytes(gzip.compress(data('review').read_bytes()))
            self.assertEqual(self.review,checker.read(path,1024*1024)[1])
            path=Path(folder)/'duplicate.json'; path.write_bytes(b'{"index":0,"index":1}')
            with self.assertRaisesRegex(ValueError,'Duplicate'): checker.read(path,64*1024)

if __name__=='__main__': unittest.main()
