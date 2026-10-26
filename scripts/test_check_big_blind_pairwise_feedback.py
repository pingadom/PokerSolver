"""Corruption controls for fresh BB policy checks, real reach and withheld grading authority."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('pairwise_feedback_test',Path(__file__).with_name('check-big-blind-pairwise-feedback.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class BigBlindPairwiseFeedbackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check();cls.report=check.read(check.REPORT)[0]
        cls.study=cls.report['study'];cls.parent=cls.study['source']['cases'][0]['conditional']['parent']
        root=check.base.literal_tree(cls.parent,dict(binding=cls.parent['binding']['sourceBinding']),cls.parent['request']['connected'])
        selected={(i,'|BB:call'):dict(n.edges)['call'].edges[0][1] for i,(_,n) in enumerate(root.edges)}
        cls.context=dict(root=root,prior=cls.parent['originalJointPrior'],selected=selected,parent=cls.parent)

    def test_all18_assessments_preserve_failed_actual_reach_and_zero_qualification(self):
        self.assertEqual(self.receipt['policyAssessmentsChecked'],18)
        self.assertEqual(self.receipt['qualifiedIndices'],[])
        self.assertEqual(self.receipt['referenceVisitedNodes'],635040000)
        self.assertFalse(self.receipt['trainerAdmission'])
        for i in [1,3]:
            item=self.receipt['outcomes'][i]
            self.assertTrue(item['actionSeparated'])
            self.assertTrue(all(r<.0001 for r in item['actualReach']))
            self.assertTrue(all(r>=.0001 for r in item['counterfactualReach']))
            self.assertFalse(item['qualifiedForOfflineDrill'])

    def test_actual_reach_cannot_be_replaced_with_counterfactual_reach_or_hidden(self):
        conditional=self.study['source']['cases'][1]['conditional'];moves=self.study['cases'][1]['moves']
        policy=self.report['references'][0]['policy'];original=self.report['cases'][1]['references'][0]
        for kind in ['actual','cf','ev','loss','regret','inclusion','checked','failures']:
            with self.subTest(kind=kind):
                actual=copy.deepcopy(original)
                if kind=='actual':actual['actualQuestionReachGivenPhysicalFlop']=actual['counterfactualQuestionReachGivenPhysicalFlop']
                if kind=='cf':actual['counterfactualQuestionReachGivenPhysicalFlop']=0
                if kind=='ev':actual['actionUtilitiesBb']['call']+=1
                if kind=='loss':actual['actionLossBb']['fold']=0
                if kind=='regret':actual['decisionRegretBb']=1
                if kind=='inclusion':actual['inclusionMarginBb']=1
                if kind=='checked':actual['boundsChecked']=False
                if kind=='failures':actual['failures']=[]
                with self.assertRaises(ValueError):check.policy_check(actual,policy,original['solutionHash'],self.context,conditional,moves)

    def test_complete_batch_reference_budget_and_qualification_edits_reject(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bad.json'
            for kind in ['drop','reference','outcome','reason','qualified','admission','work']:
                with self.subTest(kind=kind):
                    r=copy.deepcopy(self.report)
                    if kind=='drop':r['cases'].pop()
                    if kind=='reference':r['references'].pop()
                    if kind=='outcome':r['cases'][1]['qualifiedForOfflineDrill']=True
                    if kind=='reason':r['cases'][1]['rejectionReasons']=[]
                    if kind=='qualified':r['qualifiedIndices']=[1,3]
                    if kind=='admission':r['trainerAdmission']=True
                    if kind=='work':r['references'][0]['work']['prunedSuffixRoots']-=1
                    path.write_text(json.dumps(r),encoding='utf-8')
                    with self.assertRaises(ValueError):check.check(path,verify_source=False,bind_saved_bytes=False)
            budget=check.read(check.BUDGET)[0]
            for key in ['freshReferenceRuns','automaticRetries','requiredMinimumReach','fullParentAndReachedFlopGapBb']:
                value=dict(budget);value[key]+=1;path.write_text(json.dumps(value),encoding='utf-8')
                with patch.object(check,'BUDGET',path),self.assertRaises(ValueError):check.check(verify_source=False)

    def test_gate_replay_is_required_before_any_source_can_claim_a_new_grade(self):
        for item in self.report['cases']:
            self.assertFalse(item['qualifiedForOfflineDrill'])
            self.assertGreater(len(item['rejectionReasons']),0)
        for i in [1,3]:
            self.assertIn('PRIMARY:INSUFFICIENT_ACTUAL_QUESTION_REACH',self.report['cases'][i]['rejectionReasons'])
            self.assertIn('80000:INSUFFICIENT_ACTUAL_QUESTION_REACH',self.report['cases'][i]['rejectionReasons'])
            self.assertIn('160000:INSUFFICIENT_ACTUAL_QUESTION_REACH',self.report['cases'][i]['rejectionReasons'])


if __name__=='__main__':unittest.main()
