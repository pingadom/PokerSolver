import copy
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

spec=importlib.util.spec_from_file_location('bank_raise_smoke',Path(__file__).with_name('smoke-bank-raise-feedback.py'))
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)

class BankRaiseSmokeTest(unittest.TestCase):
    def test_mixed_repeated_empty_and_maximum_reviews_keep_sparse_ids(self):
        choices=[dict(sourceCaseIndex=2,action='call'),dict(sourceCaseIndex=0,action='raise:9.0'),dict(sourceCaseIndex=2,action='fold'),dict(sourceCaseIndex=2,action='raise:9.0')]
        review=smoke.canonical_review(choices)
        self.assertEqual(dict(distinctQuestions=2,withinLimit=1,outsideLimit=1,strategyDependent=2),review['counts'])
        self.assertEqual(choices,[a['attempt'] for a in review['attempts']])
        self.assertEqual(list(range(4)),[a['ordinal'] for a in review['attempts']])
        self.assertEqual(0,smoke.canonical_review([])['counts']['distinctQuestions'])
        self.assertEqual(128,smoke.canonical_review([choices[-1]]*128)['counts']['strategyDependent'])
    def test_unqualified_ids_illegal_actions_extra_fields_and_overflow_reject(self):
        for choice in [dict(sourceCaseIndex=i,action='fold') for i in [-1,1,3,'0',False]]+[dict(sourceCaseIndex=0,action='check'),dict(sourceCaseIndex=0,action='fold',ev=99)]:
            with self.assertRaises(AssertionError):smoke.canonical_review([choice])
        with self.assertRaises(AssertionError):smoke.canonical_review([dict(sourceCaseIndex=2,action='call')]*129)
    def test_readiness_has_one_fixed_deadline(self):
        with patch.object(smoke,'call',side_effect=[urllib.error.URLError('starting'),smoke.METADATA]),patch.object(smoke.time,'sleep') as sleep:
            self.assertEqual(smoke.METADATA,smoke.wait_for_metadata());sleep.assert_called_once_with(2)
        with patch.object(smoke,'call',side_effect=TimeoutError),patch.object(smoke.time,'monotonic',side_effect=[0,601]),patch.object(smoke.time,'sleep') as sleep:
            with self.assertRaises(TimeoutError):smoke.wait_for_metadata()
            sleep.assert_not_called()
    def test_disabled_mode_requires_all_five_routes_absent(self):
        with patch.object(smoke,'call') as call:
            smoke.run(True);self.assertEqual(5,call.call_count);self.assertTrue(all(c.args[2]==404 for c in call.call_args_list))
    def test_any_metadata_change_blocks_enabled_smoke_before_requests(self):
        for field,value in [('studyHash','0'*64),('sourceCaseIndices',[0,1]),('trainerAdmission',True),('cachedLegalGrades',9)]:
            metadata=copy.deepcopy(smoke.METADATA);metadata[field]=value
            with patch.object(smoke,'wait_for_metadata',return_value=metadata),patch.object(smoke,'call') as call:
                with self.assertRaises(AssertionError):smoke.run()
                call.assert_not_called()
    def test_enabled_flow_checks_questions_grades_reviews_exports_and_openapi(self):
        schemas={};paths={}
        for route,name,fields in [('grade','BankRaiseGradeRequest',['studyHash','sourceCaseIndex','action']),('review','BankRaiseReviewRequest',['studyHash','choices']),('review/export','BankRaiseReviewRequest',['studyHash','choices'])]:
            schemas[name]=dict(properties={f:({'maximum':2} if f=='sourceCaseIndex' else {}) for f in fields})
            paths[smoke.PREFIX+'/'+route]=dict(post=dict(requestBody=dict(required=True,content={'application/json':dict(schema={'$ref':'#/components/schemas/'+name})})))
        schemas['BankRaiseSubmittedChoice']=dict(properties=dict(sourceCaseIndex=dict(maximum=2)))
        docs=dict(components=dict(schemas=schemas),paths=paths)
        checked=[]
        def call(path='',body=None,expected_status=200,raw=False):
            checked.append((path,expected_status))
            if expected_status!=200:return {}
            if path.startswith('/questions/'):
                index=int(path.split('/questions/')[1].split('?')[0]);return next(q for q in smoke.FIXTURE['snapshot']['questions'] if q['sourceCaseIndex']==index)
            if path=='/grade':return next(g for g in smoke.FIXTURE['grades'] if g['sourceCaseIndex']==body['sourceCaseIndex'] and g['move']['action']==body['action'])
            review=smoke.canonical_review(body['choices'])
            return (json.dumps(review)+'\n').encode() if raw else review
        with patch.object(smoke,'wait_for_metadata',return_value=smoke.METADATA),patch.object(smoke.urllib.request,'urlopen',return_value=io.BytesIO(json.dumps(docs).encode())),patch.object(smoke,'call',side_effect=call):smoke.run()
        self.assertEqual(6,sum(path=='/grade' and status==200 for path,status in checked))
        self.assertIn(('/questions/1?studyHash='+smoke.PIN,400),checked)
        self.assertEqual(10,sum(path=='/review/export' and status==200 for path,status in checked))

if __name__=='__main__':unittest.main()
