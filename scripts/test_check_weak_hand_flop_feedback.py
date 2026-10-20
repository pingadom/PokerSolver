"""Fresh qualification, pruning invariants and independent semantic corruption checks."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('weak_hand_feedback_checker',ROOT/'scripts/check-weak-hand-flop-feedback.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)
REQUEST=ROOT/'docs/data/supplied-weak-hand-qualified-flop-request.json'
REPORT=ROOT/'docs/data/supplied-weak-hand-qualified-flop-report.json.gz'

class WeakHandFeedbackCheckTest(unittest.TestCase):
    def test_full_physical_qualification_matches_saved_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-weak-hand-qualified-flop-independent-check.json').read_text())
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
                elif kind=='move':value['moves'][0]['classification']='OUTSIDE_LIMIT'
                elif kind=='primary-value':value['primary']['actionUtilitiesBb']['call']+=.1
                elif kind=='actual-reach':c['actualQuestionRootReach']*=2
                elif kind=='cf-reach':c['counterfactualQuestionReachGivenPhysicalFlop']=0
                elif kind=='margin':c['inclusionMarginBb']+=.01
                elif kind=='regret':c['decisionRegretBb']+=.1
                elif kind=='inclusion':c['insideDeclaredFace']=False
                elif kind=='gap':c['fullParentQuality']['nashConvBb']+=.1
                elif kind=='reference-row':r['policy']['strategy'][checker.KEY]={'fold':0,'call':1}
                elif kind=='iterations':r['policy']['iterations']=1000
                elif kind=='constant':r['work']['inactiveUtilities']['4']=0
                elif kind=='work':r['work']['statistics']['visitedNodes']+=1
                elif kind=='pruned-roots':r['work']['prunedSuffixRoots']+=1
                elif kind=='budget':value['request']['referenceBudget']['totalIterations']+=1
                path.write_text(json.dumps(value),encoding='utf-8')
                with self.subTest(kind=kind),self.assertRaises(ValueError):checker.check(REQUEST,path,bind_saved_bytes=False)
    def test_every_primary_and_reference_policy_rejects_semantic_corruption(self):
        report=json.loads(gzip.decompress(REPORT.read_bytes()))
        source=report['conditional'];parent=source['parent']
        root,_=checker.conditional.checked_parent(parent,source['request']['parent'])
        selected={i:dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(root.edges)}
        context=dict(root=root,prior=parent['originalJointPrior'],selected={(i,'|BB:call'):node for i,node in selected.items()})
        roots=[(i,w['conditionalProbability'],selected[i],dict(selected[i].edges)['bet']) for i,w in enumerate(context['prior']) if w['dealtCombos'][3]=='2c 3c']
        policies=[dict(policy=parent['candidate'],check=report['primary']),*report['references']]
        rejected=0
        for item in policies:
            original=item['check'];policy=item['policy'];pin=original['solutionHash']
            self.assertEqual([],checker.policy_check(original,policy,pin,context,source['actions'],roots))
            for kind in ['hash','full-gap','local-gap','reach','counterfactual','regret','margin','inclusion','bounds-status','bound-violation','action-ev','action-loss','failures','posterior','policy']:
                actual=copy.deepcopy(original);changed=copy.deepcopy(policy)
                if kind=='hash':actual['solutionHash']='0'*64
                elif kind=='full-gap':actual['fullParentQuality']['nashConvBb']+=.1
                elif kind=='local-gap':actual['reachedFlopQuality']['conditionalBestResponse']['nashConvBb']+=.1
                elif kind=='reach':actual['actualQuestionRootReach']+=.1
                elif kind=='counterfactual':actual['counterfactualQuestionReachGivenPhysicalFlop']+=.1
                elif kind=='regret':actual['decisionRegretBb']+=.1
                elif kind=='margin':actual['inclusionMarginBb']+=.1
                elif kind=='inclusion':actual['insideDeclaredFace']=False
                elif kind=='bounds-status':actual['boundsChecked']=False
                elif kind=='bound-violation':actual['maximumBoundViolationBb']+=.1
                elif kind=='action-ev':actual['actionUtilitiesBb']['call']+=.1
                elif kind=='action-loss':actual['actionLossBb']['call']+=.1
                elif kind=='failures':actual['failures']=['unexpected']
                elif kind=='posterior':actual['reachedFlopQuality']['reachedWorlds'][0]['conditionalWeight']+=.1
                elif kind=='policy':changed['strategy'][checker.KEY]={'fold':0.0,'call':1.0}
                with self.subTest(iterations=original['iterations'],kind=kind),self.assertRaises(ValueError):
                    checker.policy_check(actual,changed,pin,context,source['actions'],roots)
                rejected+=1
        self.assertEqual(45,rejected)

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
