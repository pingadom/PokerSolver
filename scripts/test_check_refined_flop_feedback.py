"""Fresh qualification, pruning invariants and independent semantic corruption checks."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('refined_feedback_checker',ROOT/'scripts/check-refined-flop-feedback.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
REQUEST=ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-request.json'
REPORT=ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-report.json.gz'

class RefinedFeedbackCheckTest(unittest.TestCase):
    def test_full_physical_qualification_matches_saved_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-independent-check.json').read_text())
        self.assertEqual(expected,checker.check(REQUEST,REPORT))
        self.assertTrue(expected['qualifiedForOfflineDrill'])
        self.assertEqual(240000,expected['declaredFreshReferenceIterations'])
    def test_semantic_corruption_cannot_hide_behind_fixed_byte_check(self):
        original=json.loads(gzip.decompress(REPORT.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'changed.json'
            for kind in ['qualification','admission','move','primary-value','actual-reach','cf-reach','margin','regret','inclusion','gap','reference-row','iterations','constant','work','pruned-roots','budget']:
                value=copy.deepcopy(original);r=value['references'][0];c=r['check']
                if kind=='qualification':value['qualifiedForOfflineDrill']=False
                elif kind=='admission':value['trainerAdmission']=True
                elif kind=='move':value['moves'][0]['classification']='WITHIN_LIMIT'
                elif kind=='primary-value':value['primary']['actionUtilitiesBb']['call']+=.1
                elif kind=='actual-reach':c['actualQuestionRootReach']*=2
                elif kind=='cf-reach':c['counterfactualQuestionReachGivenPhysicalFlop']=0
                elif kind=='margin':c['inclusionMarginBb']+=.01
                elif kind=='regret':c['decisionRegretBb']+=.1
                elif kind=='inclusion':c['insideDeclaredFace']=False
                elif kind=='gap':c['fullParentQuality']['nashConvBb']+=.1
                elif kind=='reference-row':r['policy']['strategy'][checker.KEY]={'fold':1,'call':0}
                elif kind=='iterations':r['policy']['iterations']=1000
                elif kind=='constant':r['work']['inactiveUtilities']['4']=0
                elif kind=='work':r['work']['statistics']['visitedNodes']+=1
                elif kind=='pruned-roots':r['work']['prunedSuffixRoots']+=1
                elif kind=='budget':value['request']['referenceBudget']['totalIterations']+=1
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(REQUEST,path,bind_saved_bytes=False)
    def test_bad_budget_and_strict_byte_limits_reject(self):
        original=json.loads(REQUEST.read_text())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'input.json';value=copy.deepcopy(original);value['referenceBudget']['iterations']=[80000,320000]
            path.write_text(json.dumps(value),encoding='utf-8')
            with self.assertRaises(ValueError):checker.check(path,REPORT,bind_saved_bytes=False)
            path.write_text('{"schemaVersion":"one","schemaVersion":"two"}',encoding='utf-8')
            with self.assertRaises(ValueError):checker.check(path,REPORT)
            path.write_bytes(b'x'*(8*1024*1024+1))
            with self.assertRaises(ValueError):checker.check(path,REPORT)

if __name__=='__main__':unittest.main()
