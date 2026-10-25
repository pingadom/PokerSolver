"""New-board whole-batch qualification and review corruption controls."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('unpaired_shared_checker',ROOT/'scripts/check-unpaired-shared-flop.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)


class UnpairedSharedFlopCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source=json.loads(gzip.decompress(checker.REPORT.read_bytes()))
        cls.review=json.loads(checker.REVIEW.read_bytes())
        cls.receipt=checker.check()

    def test_complete_source_and_original_gates_match_saved_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-unpaired-shared-flop-independent-check.json').read_bytes())
        self.assertEqual(expected,self.receipt)
        self.assertEqual((4,32,12,2,240000),(expected['questionsChecked'],expected['endpointCertificatesChecked'],
            expected['selectedPolicyAssessments'],expected['sharedReferencePolicies'],expected['declaredFreshReferenceIterations']))
        self.assertTrue(expected['qualifiedForOfflineDrill'])
        self.assertFalse(expected['trainerAdmission'])
        self.assertIs(checker.policy_check,checker.legacy.policy_check)
        self.assertEqual(dict(withinLimit=4,outsideLimit=4,strategyDependent=0,distinctQuestions=4,totalQuestions=4),
            expected['reviewCheck']['counts'])

    def test_semantic_corruption_is_rejected_without_the_final_byte_pin(self):
        original=json.loads(gzip.decompress(checker.REPORT.read_bytes()))
        kinds=['omitted-question','duplicate-question','reordered-question','wrong-hand','wrong-key','wrong-game',
            'missing-check','swapped-checks','cf-reach','actual-reach','value','regret','margin','gap','inclusion',
            'primary-row','reference-row','iterations','reference-work','pruning','reservation','sharing',
            'move','endpoint','qualification','admission','failures']
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in kinds:
                value=copy.deepcopy(original);item=value['cases'][1];check=item['references'][0]
                if kind=='omitted-question':value['cases'].pop()
                elif kind=='duplicate-question':value['cases'][1]=copy.deepcopy(value['cases'][0])
                elif kind=='reordered-question':value['cases'].reverse()
                elif kind=='wrong-hand':item['conditional']['request']['heroCards']='2c 3c'
                elif kind=='wrong-key':item['conditional']['informationSet']='foreign'
                elif kind=='wrong-game':item['conditional']['parent']['binding']['flop']=['2d','3h','4s']
                elif kind=='missing-check':item['references'].pop()
                elif kind=='swapped-checks':item['references'].reverse()
                elif kind=='cf-reach':check['counterfactualQuestionReachGivenPhysicalFlop']=.5
                elif kind=='actual-reach':check['actualQuestionRootReach']=.5
                elif kind=='value':check['actionUtilitiesBb']['call']=99
                elif kind=='regret':check['decisionRegretBb']=.5
                elif kind=='margin':check['inclusionMarginBb']=.5
                elif kind=='gap':check['fullParentQuality']['nashConvBb']=.5
                elif kind=='inclusion':check['insideDeclaredFace']=False
                elif kind=='primary-row':
                    key=next(iter(item['conditional']['parent']['candidate']['strategy']))
                    row=item['conditional']['parent']['candidate']['strategy'][key];a=next(iter(row));row[a]=.5
                elif kind=='reference-row':
                    key=next(iter(value['references'][0]['policy']['strategy']))
                    row=value['references'][0]['policy']['strategy'][key];a=next(iter(row));row[a]=.5
                elif kind=='iterations':value['references'][0]['policy']['iterations']=160000
                elif kind=='reference-work':value['references'][0]['work']['statistics']['visitedNodes']=0
                elif kind=='pruning':value['references'][0]['work']['inactiveUtilities']['0']=1
                elif kind=='reservation':value['reservation']['totalReferenceIterations']=720000
                elif kind=='sharing':value['referenceSharing']='SIX_INDEPENDENT_POLICIES'
                elif kind=='move':item['moves'][0]['classification']='WITHIN_LIMIT'
                elif kind=='endpoint':item['conditional']['actions'][0]['upperUtilityBb']+=1
                elif kind=='qualification':value['qualifiedForOfflineDrill']=False
                elif kind=='admission':value['trainerAdmission']=True
                elif kind=='failures':item['rejectionReasons']=['REFERENCE_OUTSIDE_DECLARED_FACE']
                with path.open('w',encoding='utf-8',newline='\n') as out:json.dump(value,out)
                with self.subTest(kind=kind),self.assertRaises(ValueError):
                    checker.check(checker.REQUEST,path,bind_saved_bytes=False)

    def test_review_context_grades_and_coverage_cannot_be_forged(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json'
            for kind in ['board','old-pin','hand','ev','illegal','count','coverage','question','total','order','limit']:
                value=copy.deepcopy(self.review);attempt=value['attempts'][0]
                if kind=='board':attempt['question']['decision']['context']['board']=['8d','Kc','Kd']
                elif kind=='old-pin':value['reportHash']='55902b45'+'0'*56
                elif kind=='hand':attempt['question']['decision']['heroCards']='As Qs'
                elif kind=='ev':attempt['feedback']['selected']['lowerEvBb']=99
                elif kind=='illegal':attempt['action']='check'
                elif kind=='count':value['counts']['withinLimit']=8
                elif kind=='coverage':value['counts']['distinctQuestions']=8
                elif kind=='question':attempt['question']['index']=4
                elif kind=='total':attempt['question']['totalQuestions']=3
                elif kind=='order':value['attempts'].reverse()
                elif kind=='limit':value['attempts']=[copy.deepcopy(attempt) for _ in range(101)]
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check_review(self.source,path,bind_saved_bytes=False)

    def test_empty_and_repeated_attempts_preserve_distinct_coverage(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json'
            for count in [0,100]:
                value=copy.deepcopy(self.review);first=value['attempts'][0]
                self.assertEqual('WITHIN_LIMIT',first['feedback']['selected']['classification'])
                value['attempts']=[dict(copy.deepcopy(first),index=i) for i in range(count)]
                value['counts']=dict(withinLimit=count,outsideLimit=0,strategyDependent=0,
                    distinctQuestions=int(count>0),totalQuestions=4)
                path.write_text(json.dumps(value),encoding='utf-8')
                checked=checker.check_review(self.source,path,bind_saved_bytes=False)
                self.assertEqual(count,checked['attemptsChecked'])
                self.assertEqual(int(count>0),checked['counts']['distinctQuestions'])


if __name__=='__main__':unittest.main()
