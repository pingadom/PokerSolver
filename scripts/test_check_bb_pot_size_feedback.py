"""Controls for both newly qualified responses, all eighteen assessments and source identity."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
spec=importlib.util.spec_from_file_location('pot_source_test',Path(__file__).with_name('check-bb-pot-size-feedback.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)

class PotSizeFeedbackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check_source();cls.report=check.read(check.REPORT)[0]

    def test_exact_two_survivors_and_every_gate_are_independently_checked(self):
        self.assertEqual(self.receipt['qualifiedIndices'],[3,5])
        self.assertEqual(self.receipt['policyAssessmentsChecked'],18)
        self.assertEqual(self.receipt['referenceVisitedNodes'],635040000)
        for i in [3,5]:
            self.assertTrue(self.receipt['outcomes'][i]['qualified'])
            self.assertTrue(all(r>=.0001 for r in self.receipt['outcomes'][i]['actualReach']))

    def test_rejected_contexts_cannot_gain_authority_and_survivors_cannot_hide_failures(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bad.json'
            for kind in ['qualified','failure','actual','outcome','primary','drop','admission']:
                r=copy.deepcopy(self.report)
                if kind=='qualified':r['qualifiedIndices']=[0,3,5]
                if kind=='failure':r['cases'][0]['rejectionReasons']=[]
                if kind=='actual':r['cases'][3]['references'][0]['actualQuestionReachGivenPhysicalFlop']=0
                if kind=='outcome':r['cases'][3]['qualifiedForOfflineDrill']=False
                if kind=='primary':r['cases'][3]['primary']['inclusionMarginBb']=1
                if kind=='drop':r['cases'].pop()
                if kind=='admission':r['trainerAdmission']=True
                path.write_text(json.dumps(r),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):check.check_source(path,verify_screens=False,bind_saved_bytes=False)

    def test_policy_and_betting_model_identity_are_not_just_card_labels(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'bad.json'
            for kind in ['policy','price','fraction','pin','work']:
                r=copy.deepcopy(self.report)
                if kind=='policy':
                    row=next(iter(r['references'][0]['policy']['strategy'].values()));row[next(iter(row))]=.5
                if kind=='price':r['study']['source']['cases'][3]['conditional']['context']['toCallBb']=3.25
                if kind=='fraction':r['request']['coverage']['parent']['connected']['betPotFraction']=.5
                if kind=='pin':r['cases'][3]['references'][0]['solutionHash']='0'*64
                if kind=='work':r['references'][0]['work']['prunedSuffixRoots']-=1
                path.write_text(json.dumps(r),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):check.check_source(path,verify_screens=False,bind_saved_bytes=False)

    def test_correct_actions_are_call99_and_fold_acjc_without_dropping_other_cases(self):
        self.assertEqual(len(self.report['cases']),6)
        for i,correct in [(3,'call'),(5,'fold')]:
            moves=self.report['study']['cases'][i]['moves']
            self.assertEqual(next(m['action'] for m in moves if m['classification']=='WITHIN_LIMIT'),correct)
            self.assertTrue(self.report['cases'][i]['qualifiedForOfflineDrill'])

if __name__=='__main__':unittest.main()
