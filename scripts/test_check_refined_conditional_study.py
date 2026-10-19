"""Independent paired conditional certificates, physical posteriors and corruption controls."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('refined_conditional_check',ROOT/'scripts/check-refined-conditional-study.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
REQUEST=ROOT/'docs/data/supplied-paired-btn-facing-bet-conditional-request.json'
REPORT=ROOT/'docs/data/supplied-paired-btn-facing-bet-conditional-report.json.gz'


class RefinedConditionalCheckTest(unittest.TestCase):
    def test_complete_fixed_case_matches_the_independent_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-paired-btn-facing-bet-conditional-independent-check.json').read_text())
        actual=checker.check(REQUEST,REPORT)
        self.assertEqual(expected,actual)
        self.assertEqual(8,actual['originalEndpointCertificatesChecked'])
        self.assertEqual(2,actual['parentCheck']['originalLpCertificatesIndependentlyChecked'])
        self.assertFalse(actual['trainerAdmission'])

    def test_semantic_changes_reject_before_final_byte_binding(self):
        original=json.loads(gzip.decompress(REPORT.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['admission','mode','context','stack','bounds','loss','point','dual','posterior','scaling','numerator','reach','work','total-work','parent']:
                value=copy.deepcopy(original);a=value['actions'][1];d=a['diagnostic'];w=d['lowerWitness']
                if kind=='admission':value['trainerAdmission']=True
                elif kind=='mode':d['algorithm']='legacy'
                elif kind=='context':value['context']['toCallBb']=0
                elif kind=='stack':value['context']['players'][5]['remainingStackBb']+=1
                elif kind=='bounds':a['lowerUtilityBb']+=.1
                elif kind=='loss':a['upperLossBb']+=.1
                elif kind=='point':w['point'][0]+=.1
                elif kind=='dual':w['dual'][0]+=.1
                elif kind=='posterior':w['posterior'][0]['probability']+=.1
                elif kind=='scaling':w['scaling']*=2
                elif kind=='numerator':d['numeratorPlans'][0]['constant']+=.1
                elif kind=='reach':d['certifiedReachLowerBound']+=.01
                elif kind=='work':w['certificate']['work']['arithmeticWork']=4_000_000_001
                elif kind=='total-work':d['work']['intervalLpArithmeticWork']+=1
                elif kind=='parent':value['parent']['handoffs'][0]['worlds'][0]['counts']['ties']+=1
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(REQUEST,path,bind_saved_bytes=False)

    def test_unsupported_request_and_strict_input_caps_reject(self):
        original=json.loads(REQUEST.read_text())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'request.json'
            for key,new in [('intervalAlgorithm','legacy'),('heroCards','9s Js'),('flopActions',['check'])]:
                value=copy.deepcopy(original);value[key]=new;path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(key=key),self.assertRaises(ValueError):checker.check(path,REPORT,bind_saved_bytes=False)
            path.write_text('{"hero":3,"hero":5}',encoding='utf-8')
            with self.assertRaises(ValueError):checker.check(path,REPORT)
            path.write_bytes(b'x'*(8*1024*1024+1))
            with self.assertRaises(ValueError):checker.check(path,REPORT)
            path=Path(folder)/'large.gz';path.write_bytes(gzip.compress(b' '*(8*1024*1024+1)))
            with self.assertRaises(ValueError):checker.check(REQUEST,path)


if __name__=='__main__':unittest.main()
