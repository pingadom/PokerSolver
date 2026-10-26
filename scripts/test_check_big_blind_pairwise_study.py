"""Corruption controls for the separately versioned BB pairwise regret study."""
import copy
import importlib.util
from itertools import product
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('pairwise_study_test',Path(__file__).with_name('check-big-blind-pairwise-study.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class BigBlindPairwiseStudyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check();cls.report=check.read(check.REPORT)[0];cls.coverage=check.read(check.source.REPORT)[0]

    def test_full_physical_evidence_and_two_candidates_have_no_trainer_authority(self):
        self.assertEqual(self.receipt['candidateIndices'],[1,3])
        self.assertEqual(self.receipt['qualifiedTrainerQuestions'],0)
        self.assertEqual(self.receipt['sourceCheck']['originalEndpointCertificatesChecked'],51)
        self.assertEqual(self.receipt['freshLpSolves'],0)
        self.assertFalse(self.receipt['trainerAdmission'])

    def test_all_vertices_obey_the_pairwise_regret_envelope(self):
        for item,original in zip(self.report['cases'],self.coverage['cases'],strict=True):
            actions=original['conditional']['actions']
            for values in product(*[(a['lowerUtilityBb'],a['upperUtilityBb']) for a in actions]):
                for i,move in enumerate(item['moves']):
                    regret=max(values)-values[i]
                    self.assertLessEqual(move['lowerLossBb'],regret)
                    self.assertLessEqual(regret,move['upperLossBb'])

    def test_corrupted_bounds_contexts_inventory_and_admission_reject_semantically(self):
        for kind in ['loss','ev','candidate','drop','index','separation','source','algorithm','admission']:
            with self.subTest(kind=kind):
                r=copy.deepcopy(self.report)
                if kind=='loss':r['cases'][1]['moves'][1]['upperLossBb']=.5
                if kind=='ev':r['cases'][1]['moves'][1]['lowerEvBb']=0
                if kind=='candidate':r['candidateIndices'].append(5)
                if kind=='drop':r['cases'].pop()
                if kind=='index':r['cases'][1]['index']=3
                if kind=='separation':r['cases'][1]['actionSeparated']=False
                if kind=='source':r['source']['cases'][1]['request']['heroCards']='9d 9h'
                if kind=='algorithm':r['lossAlgorithm']='legacy'
                if kind=='admission':r['trainerAdmission']=True
                with self.assertRaises(ValueError):check.check_report(r,self.coverage)

    def test_old_marginal_loss_evidence_is_preserved(self):
        for i in [1,3]:
            old=self.coverage['cases'][i]['moves'][1]
            new=self.report['cases'][i]['moves'][1]
            self.assertEqual(old['classification'],'STRATEGY_DEPENDENT')
            self.assertGreater(old['upperLossBb'],.01)
            self.assertEqual(new['upperLossBb'],0)
            self.assertEqual(new['classification'],'WITHIN_LIMIT')
            self.assertEqual((old['lowerEvBb'],old['upperEvBb']),(new['lowerEvBb'],new['upperEvBb']))


if __name__=='__main__':unittest.main()
