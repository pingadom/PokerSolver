"""Independent parent physics/certificate proof and semantic corruption controls."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('refined_parent_check',ROOT/'scripts/check-refined-parent-study.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
REQUEST=ROOT/'docs/data/supplied-paired-flop-refined-parent-request.json'
REPORT=ROOT/'docs/data/supplied-paired-flop-refined-parent-report.json.gz'

class RefinedParentCheckTest(unittest.TestCase):
    def test_complete_fixed_case_matches_the_saved_independent_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-paired-flop-refined-parent-independent-check.json').read_text())
        actual=checker.check(REQUEST,REPORT)
        self.assertEqual(expected,actual)
        self.assertEqual(2,actual['originalLpCertificatesIndependentlyChecked'])
        self.assertEqual(1321,actual['literalTreeStates'])
        self.assertLess(actual['nashConvBb'],1e-8)
        self.assertFalse(actual['trainerAdmission'])

    def test_captured_failure_is_the_exact_sorted_column_lp_of_this_physical_parent(self):
        report=json.loads(gzip.decompress(REPORT.read_bytes()))
        captured=json.loads(gzip.decompress((ROOT/'solver/src/test/resources/lp-parent-problem-paired.json.gz').read_bytes()))
        matrix,rhs,cost=checker.lp_problem(report['solve'],False)
        def original_metric(i):
            total=0.0
            for value in matrix[i]:total+=abs(value)
            return total,i
        order=sorted(range(len(rhs)),key=original_metric)
        self.assertEqual(captured['matrix'],[matrix[i] for i in order])
        self.assertEqual(captured['rhs'],[rhs[i] for i in order])
        self.assertEqual(captured['cost'],cost)

    def test_semantic_corruptions_fail_before_the_final_byte_binding(self):
        original=json.loads(gzip.decompress(REPORT.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['admission','mode','payoff','dead-card','flop-count','residual','policy','point','dual-certificate','work-cap','value','flow','policy-pin']:
                value=copy.deepcopy(original)
                if kind=='admission':value['trainerAdmission']=True
                elif kind=='mode':value['solve']['algorithm']='legacy'
                elif kind=='payoff':value['solve']['projectedPayoff']['constant']+=1
                elif kind=='dead-card':value['originalJointPrior'][0]['dealtCombos'][0]='4c 4h'
                elif kind=='flop-count':value['handoffs'][0]['worlds'][0]['counts']['firstWins']+=1
                elif kind=='residual':value['residuals'][0]['counts']['firstWins']+=1
                elif kind=='policy':
                    row=next(iter(value['candidate']['strategy'].values()));keys=list(row);row.update({k:(.2 if i==0 else .8/(len(keys)-1)) for i,k in enumerate(keys)})
                elif kind=='point':value['firstLpWitness']['point'][0]+=.1
                elif kind=='dual-certificate':value['firstLpWitness']['certificate']['dualValue']+=1
                elif kind=='work-cap':
                    value['firstLpWitness']['certificate']['work']['arithmeticWork']=4_000_000_001
                    value['solve']['firstLp']=copy.deepcopy(value['firstLpWitness']['certificate'])
                elif kind=='value':value['solve']['lowerValue']+=.01
                elif kind=='flow':value['solve']['firstFlow']['realization'][0]=.9
                elif kind=='policy-pin':value['solutionHash']='0'*64
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(REQUEST,path,bind_saved_bytes=False)

    def test_unsupported_requests_and_input_caps_reject(self):
        original=json.loads(REQUEST.read_text())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'request.json'
            for key,new in [('algorithm','old-parent'),('schemaVersion','old-schema')]:
                value=copy.deepcopy(original);value[key]=new;path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(key=key),self.assertRaises(ValueError):checker.check(path,REPORT,bind_saved_bytes=False)
            path.write_text('{"algorithm":"one","algorithm":"two"}',encoding='utf-8')
            with self.assertRaises(ValueError):checker.check(path,REPORT)
            path.write_bytes(b'x'*(8*1024*1024+1))
            with self.assertRaises(ValueError):checker.check(path,REPORT)
            path=Path(folder)/'large.gz';path.write_bytes(gzip.compress(b' '*(8*1024*1024+1)))
            with self.assertRaises(ValueError):checker.check(REQUEST,path)

if __name__=='__main__':unittest.main()
