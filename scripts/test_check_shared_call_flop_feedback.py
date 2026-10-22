"""Every declared hand and shared policy must survive independent semantic checks."""
import copy
import gzip
import importlib.util
import inspect
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('shared_call_checker',ROOT/'scripts/check-shared-call-flop-feedback.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)


class SharedCallFlopCheckTest(unittest.TestCase):
    def test_policy_check_preserves_original_gates_with_an_explicit_hand_key(self):
        original=(ROOT/'scripts/check-weak-hand-flop-feedback.py').read_text(encoding='utf-8')
        function=original[original.index('def policy_check('):original.index('\ndef check(')]
        explicit=function.replace('context, actions, roots):','context, actions, roots, key):').replace('strategy[KEY]','strategy[key]')
        self.assertEqual(explicit.strip(),inspect.getsource(checker.policy_check).strip())

    def test_complete_physical_source_matches_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-shared-call-flop-independent-check.json').read_bytes())
        self.assertEqual(expected,checker.check())
        self.assertEqual((3,24,9,2,240000),(expected['questionsChecked'],expected['endpointCertificatesChecked'],
            expected['selectedPolicyAssessments'],expected['sharedReferencePolicies'],expected['declaredFreshReferenceIterations']))
        self.assertTrue(expected['qualifiedForOfflineDrill'])

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
                elif kind=='wrong-hand':item['conditional']['request']['heroCards']='Ah Kh'
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

    def test_ordered_request_and_budget_cannot_be_relabelled(self):
        original=json.loads(checker.REQUEST.read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'request.json'
            for hands in [[],['Ah Kh'],['Ah Kh','Ah Kh'],['Qh Th','As Qs','Ah Kh']]:
                value=copy.deepcopy(original);value['heroHands']=hands
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(hands=hands),self.assertRaises(ValueError):checker.check(path,checker.REPORT,bind_saved_bytes=False)

    def test_saved_review_reuses_the_complete_independently_verified_source(self):
        expected=json.loads((ROOT/'docs/data/supplied-shared-call-flop-review-independent-check.json').read_bytes())
        self.assertEqual(expected,checker.check(review_path=checker.REVIEW))
        self.assertEqual(7,expected['reviewCheck']['attemptsChecked'])
        self.assertEqual(3,expected['reviewCheck']['counts']['distinctQuestions'])

    def test_review_values_indices_and_coverage_cannot_be_forged(self):
        source=json.loads(gzip.decompress(checker.REPORT.read_bytes()))
        original=json.loads(checker.REVIEW.read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'review.json'
            for kind in ['question','total','pin','hand','ev','count','coverage','index','illegal','limit']:
                value=copy.deepcopy(original);attempt=value['attempts'][0]
                if kind=='question':attempt['question']['index']=1
                elif kind=='total':attempt['question']['totalQuestions']=4
                elif kind=='pin':value['reportHash']='0'*64
                elif kind=='hand':attempt['question']['decision']['heroCards']='As Qs'
                elif kind=='ev':attempt['feedback']['selected']['lowerEvBb']=99
                elif kind=='count':value['counts']['withinLimit']=7
                elif kind=='coverage':value['counts']['distinctQuestions']=7
                elif kind=='index':attempt['index']=3
                elif kind=='illegal':attempt['action']='raise'
                elif kind=='limit':value['attempts']=[copy.deepcopy(attempt) for _ in range(101)]
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check_review(source,path,bind_saved_bytes=False)


if __name__=='__main__':unittest.main()
