"""Full independent check once, then corruption controls reuse only that exact checked parent."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

PATH=Path(__file__).with_name('check-certified-reach-coverage.py')
spec=importlib.util.spec_from_file_location('test_certified_reach_checker',PATH)
checker=importlib.util.module_from_spec(spec);sys.modules[spec.name]=checker;spec.loader.exec_module(checker)


class CertifiedReachCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report,_,_=checker.read(checker.REPORT)
        original=checker.weak.checked_parent
        cls.parent=cls.report['cases'][1]['conditional']['parent']
        cls.request=checker.read(checker.REQUEST)[0]['parent']
        cls.root,cls.parent_receipt=original(cls.parent,cls.request)
        def exact_checked_parent(parent,request):
            if parent!=cls.parent or request!=cls.request:raise ValueError('Verified parent changed')
            return cls.root,cls.parent_receipt
        cls.original=original;checker.weak.checked_parent=exact_checked_parent

    @classmethod
    def tearDownClass(cls):checker.weak.checked_parent=cls.original

    def check_mutation(self,node):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'report.json';path.write_text(json.dumps(node),encoding='utf-8')
            with self.assertRaises((ValueError,AssertionError,KeyError,TypeError)):
                checker.check(report_path=path,bind_saved_bytes=False)

    def test_complete_physical_evidence_and_original_exact_certificate(self):
        result=checker.check()
        saved,_,_=checker.read(checker.DATA/'supplied-certified-reach-flop-coverage-independent-check.json')
        self.assertEqual(saved,result)
        self.assertEqual(4,result['negativeReachWitnessesChecked'])
        self.assertEqual(32,result['positiveEndpointCertificatesChecked'])
        self.assertTrue(all(r['questionReach']==0 for r in result['rejectedCases']))

    def test_negative_reach_and_global_security_corruptions_are_rejected(self):
        for mutation in ['algorithm','admission','floor','reach','global','flow','point','dual','posterior','world','affine','work','status']:
            with self.subTest(mutation=mutation):
                node=copy.deepcopy(self.report);audit=node['cases'][2]['rejection']['reachFailure'];w=audit['minimumReachWitness']
                if mutation=='algorithm':audit['algorithm']='old/v1'
                elif mutation=='admission':audit['trainerAdmission']=True
                elif mutation=='floor':audit['requiredMinimumReach']=0
                elif mutation=='reach':w['questionReach']=.2
                elif mutation=='global':w['globalHeroBestResponse']+=.01
                elif mutation=='flow':w['opponentFlow']['realization'][1]=-.01
                elif mutation=='point':w['point'][0]+=.1
                elif mutation=='dual':w['dual'][0]=-.01
                elif mutation=='posterior':w['posterior']=[]
                elif mutation=='world':w['masses'][0]['behavioralWeight']=.1
                elif mutation=='affine':audit['denominator']['cost'][0]+=.1
                elif mutation=='work':audit['work']['intervalLpSolves']=2
                elif mutation=='status':audit['status']='CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR'
                self.check_mutation(node)

    def test_complete_order_positive_evidence_and_no_admission_are_required(self):
        for mutation in ['order','missing','admission','positive','policy','moves','count','mode']:
            with self.subTest(mutation=mutation):
                node=copy.deepcopy(self.report)
                if mutation=='order':node['cases'][0]['index']=1
                elif mutation=='missing':node['cases'].pop()
                elif mutation=='admission':node['trainerAdmission']=True
                elif mutation=='positive':node['cases'][1]['conditional']['actions'][0]['lowerUtilityBb']+=.1
                elif mutation=='policy':node['cases'][1]['conditional']['parent']['candidate']['strategy']={}
                elif mutation=='moves':node['cases'][0]['moves']=[{}]
                elif mutation=='count':node['counts']['solved']=8
                elif mutation=='mode':node['request']['intervalAlgorithm']=checker.coverage.INTERVAL_ALGORITHM
                self.check_mutation(node)

    def test_exact_certificate_detects_material_original_lp_corruption(self):
        capture,_,_=checker.read(checker.CAPTURE)
        x=copy.deepcopy(capture['solved']);x['point'][0]=-.01
        with self.assertRaises(ValueError):checker.exact_certificate(capture['matrix'],capture['rhs'],capture['cost'],x)


if __name__=='__main__':unittest.main()
