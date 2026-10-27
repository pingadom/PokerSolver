"""Semantic corruption controls for both primary-screen models and necessary-condition gates."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('sizing_screen_test',Path(__file__).with_name('check-bb-sizing-primary-screen.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)

class SizingPrimaryScreenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check();cls.reports={};cls.requests={}
        for name in check.NAMES:
            cls.reports[name]=check.read(check.DATA/('supplied-unpaired-bb-'+name+'-primary-screen.json.gz'))[0]
            cls.requests[name]=check.read(check.DATA/('supplied-unpaired-bb-'+name+'-screen-request.json'))[0]

    def test_complete_physics_and_all12_contexts_need_no_fresh_references(self):
        self.assertEqual(self.receipt['contextsChecked'],12)
        self.assertEqual(self.receipt['primaryAssessmentsChecked'],12)
        self.assertEqual(self.receipt['originalEndpointCertificatesChecked'],102)
        self.assertEqual([m['primaryEligibleIndices'] for m in self.receipt['models']],[[],[3,5]])
        self.assertEqual(self.receipt['qualifiedTrainerQuestions'],0)
        self.assertEqual(self.receipt['freshCfrRuns'],0)

    def test_source_fraction_prices_identity_and_inventory_cannot_mix(self):
        for kind in ['fraction','price','drop','admission','index','candidate','ev']:
            with self.subTest(kind=kind):
                r=copy.deepcopy(self.reports['pot-size']);q=copy.deepcopy(self.requests['pot-size'])
                if kind=='fraction':q['parent']['connected']['betPotFraction']=.5
                if kind=='price':r['study']['source']['cases'][3]['conditional']['context']['betSizeBb']=3.25
                if kind=='drop':r['cases'].pop()
                if kind=='admission':r['trainerAdmission']=True
                if kind=='index':r['study']['cases'][3]['index']=5
                if kind=='candidate':r['primaryEligibleIndices']=[1,3,5]
                if kind=='ev':r['study']['source']['cases'][3]['conditional']['actions'][1]['upperUtilityBb']+=1
                with self.assertRaises(ValueError):check.check_model('pot-size',r,q)

    def test_primary_failures_cannot_be_replaced_with_counterfactual_success(self):
        for kind in ['actual','cf','failure','regret','outcome']:
            with self.subTest(kind=kind):
                r=copy.deepcopy(self.reports['quarter-pot']);c=r['cases'][1];p=c['primary']
                if kind=='actual':p['actualQuestionReachGivenPhysicalFlop']=p['counterfactualQuestionReachGivenPhysicalFlop']
                if kind=='cf':p['counterfactualQuestionReachGivenPhysicalFlop']=0
                if kind=='failure':c['rejectionReasons']=[]
                if kind=='regret':p['decisionRegretBb']=0
                if kind=='outcome':c['primaryEligible']=True
                with self.assertRaises(ValueError):check.check_model('quarter-pot',r,self.requests['quarter-pot'])

    def test_declared_caps_and_model_order_are_binding(self):
        budget=check.read(check.BUDGET)[0]
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'budget.json'
            for kind in ['freshReferenceRuns','maximumParentSolves','requiredMinimumReach','order']:
                b=copy.deepcopy(budget)
                if kind=='order':b['models'].reverse()
                else:b[kind]+=1
                path.write_text(json.dumps(b),encoding='utf-8')
                with patch.object(check,'BUDGET',path),self.assertRaises(ValueError):check.check()

if __name__=='__main__':unittest.main()
