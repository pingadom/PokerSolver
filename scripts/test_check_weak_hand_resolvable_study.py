"""New physical source, rank evaluator controls, full endpoint and corruption checks."""
import copy
import gzip
import importlib.util
import json
import os
from pathlib import Path
import random
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('weak_hand_resolvable_check',ROOT/'scripts/check-weak-hand-resolvable-study.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class WeakHandResolvableStudyCheckTest(unittest.TestCase):
    def test_all_physical_counts_parent_and_eight_endpoints_match_the_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-weak-hand-resolvable-conditional-independent-check.json').read_text())
        self.assertEqual(expected,checker.check())
        self.assertEqual(15_792_192,expected['parentCheck']['fullBoardsRegenerated'])
        self.assertEqual(8,expected['originalEndpointCertificatesChecked'])
        self.assertEqual('fold',expected['actualWithinLimitAction'])
        self.assertFalse(expected['trainerAdmission'])

    def test_semantic_mutations_reject_before_report_byte_binding(self):
        original=json.loads(gzip.decompress(checker.REPORT.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['admission','mode','schema','context','stack','bounds','loss','point','dual','posterior','scaling','numerator','reach','work','total-work','parent-flop-counts','parent-preflop-counts','parent-prior','parent-lp','parent-quality','parent-binding']:
                value=copy.deepcopy(original);a=value['actions'][1];d=a['diagnostic'];w=d['lowerWitness'];p=value['parent']
                if kind=='admission':value['trainerAdmission']=True
                elif kind=='mode':d['algorithm']='legacy'
                elif kind=='schema':value['schemaVersion']='pokerlab-refined-flop-conditional-report/v2'
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
                elif kind=='parent-flop-counts':p['handoffs'][0]['worlds'][0]['counts']['ties']+=1
                elif kind=='parent-preflop-counts':p['sourcePayoffs'][0]['counts']['ties']+=1
                elif kind=='parent-prior':p['originalJointPrior'][0]['conditionalProbability']*=2
                elif kind=='parent-lp':p['firstLpWitness']['dual'][0]+=.1
                elif kind=='parent-quality':p['solve']['behavioralQuality']['nashConvBb']+=.1
                elif kind=='parent-binding':p['binding']['sourceBinding']['inputHash']='0'*64
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(checker.REQUEST,path,bind_saved_bytes=False)

    def test_independent_mask_evaluator_matches_literal_best_of_21_rankings(self):
        rng=random.Random(261)
        deck=[r+s for r in '23456789TJQKA' for s in 'cdhs']
        fixed=['Ac Kc Qc Jc Tc 2d 3h','Ac Ad Ah As Kc 2d 3h','Ac Ad Ah Kc Kd 2d 3h',
               'Ac Kc Jc 9c 7c 2d 3h','Ac 2d 3h 4s 5c 9d Th','Ac Ad Ah Kc Qd 2d 3h',
               'Ac Ad Kh Ks Qd 2d 3h','Ac Ad Kh Qs Jd 2d 3h','Ac Kd Qh 9s 7d 2d 3h']
        cases=[s.split() for s in fixed]+[rng.sample(deck,7) for _ in range(512)]
        java=str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if 'JAVA_HOME' in os.environ else 'java'
        result=subprocess.run([java,str(checker.CARD_REFERENCE),'ranks'],input='\n'.join(' '.join(s) for s in cases)+'\n',text=True,capture_output=True,check=True,timeout=30)
        actual=list(map(int,result.stdout.splitlines()));self.assertEqual(len(cases),len(actual))
        for cards,value in zip(cases,actual,strict=True):
            rank=checker.physical.seven(tuple(checker.physical.card(c) for c in cards))
            expected=(rank[0]<<20)|sum(r<<(16-4*i) for i,r in enumerate(rank[1:]))
            self.assertEqual(expected,value,cards)

    def test_changed_request_and_strict_caps_reject(self):
        original=json.loads(checker.REQUEST.read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'request.json'
            for key,value in [('intervalAlgorithm','legacy'),('heroCards','As Qs'),('flopActions',['check'])]:
                changed=copy.deepcopy(original);changed[key]=value;path.write_text(json.dumps(changed),encoding='utf-8')
                with self.subTest(key=key),self.assertRaises(ValueError):checker.check(path,checker.REPORT,bind_saved_bytes=False)
            path.write_bytes(b'{"hero":3,"hero":5}')
            with self.assertRaises(ValueError):checker.check(path,checker.REPORT)
            path.write_bytes(b'x'*(8*1024*1024+1))
            with self.assertRaises(ValueError):checker.check(path,checker.REPORT)
            path=Path(folder)/'large.gz';path.write_bytes(gzip.compress(b' '*(8*1024*1024+1)))
            with self.assertRaises(ValueError):checker.check(checker.REQUEST,path)

if __name__=='__main__':unittest.main()
