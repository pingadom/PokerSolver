"""Corruption controls for the new board's complete physical and certified evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('unpaired_discovery_test',HERE/'check-unpaired-flop-discovery.py')
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class UnpairedFlopDiscoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt=check.check()
        cls.report=check.read(check.REPORT)[0]
        cls.parent=check.read(check.PARENT_REPORT)[0]

    def test_complete_physical_tree_and_all_endpoints(self):
        self.assertEqual(self.receipt['board'],['6d','Qc','Td'])
        self.assertEqual(self.receipt['originalActionEndpointCertificatesChecked'],32)
        self.assertEqual(self.receipt['parentCheck']['originalLpCertificatesIndependentlyChecked'],2)
        self.assertEqual(self.receipt['parentCheck']['fullBoardsRegenerated'],15792192)
        self.assertEqual(self.receipt['solvedCases'],4)
        self.assertFalse(self.receipt['trainerAdmission'])
        self.assertEqual(self.receipt['freshCfrRuns'],0)
        for rejected in self.receipt['certifiedRejectedCases']:
            self.assertEqual(rejected['questionReach'],0)
            self.assertLess(rejected['certifiedReachLowerBound'],.0001)

    def reject(self,report=None,parent=None):
        with tempfile.TemporaryDirectory() as folder:
            r=Path(folder)/'report.json';p=Path(folder)/'parent.json'
            r.write_text(json.dumps(report if report is not None else self.report),encoding='utf-8')
            p.write_text(json.dumps(parent if parent is not None else self.parent),encoding='utf-8')
            with self.assertRaises((ValueError,KeyError)):
                check.check(report_path=r,parent_path=p,bind_saved_bytes=False)

    def test_rejected_witnesses_cannot_hide_zero_reach_or_change_security(self):
        for kind in ['reach','lower','dual','flow','actor','question','scope','verdict','drop']:
            with self.subTest(kind=kind):
                r=copy.deepcopy(self.report);item=r['cases'][0];a=item['rejection']['reachFailure'];w=a['minimumReachWitness']
                if kind=='reach':w['questionReach']=.5
                if kind=='lower':a['certifiedReachLowerBound']=.1
                if kind=='dual':w['dual'][0]+=1
                if kind=='flow':w['opponentFlow']['realization'][0]=0
                if kind=='actor':a['question']['hero']=5
                if kind=='question':a['question']['informationSet']=a['question']['informationSet'].replace('6d Qc Td','8d Kc Kd')
                if kind=='scope':a['baseline']['upperValue']+=1
                if kind=='verdict':item['outcome']='ACTION_SEPARATION'
                if kind=='drop':r['cases'].pop(0)
                self.reject(report=r)

    def test_positive_cases_bind_cards_context_prices_and_every_endpoint(self):
        for kind in ['board','hand','pot','ev','loss','classification','certificate','index','parent','admission']:
            with self.subTest(kind=kind):
                r=copy.deepcopy(self.report);item=r['cases'][1];c=item['conditional'];a=c['actions'][1]
                if kind=='board':c['context']['board'][0]='8d'
                if kind=='hand':item['request']['heroCards']='Ah Kh'
                if kind=='pot':c['context']['potBb']=100
                if kind=='ev':a['upperUtilityBb']+=1
                if kind=='loss':a['lowerLossBb']=0
                if kind=='classification':item['moves'][0]['classification']='OUTSIDE_LIMIT'
                if kind=='certificate':a['diagnostic']['upperWitnesses'][0]['dual'][0]+=1
                if kind=='index':item['index']=3
                if kind=='parent':c['parent']['candidate']['strategy'].popitem()
                if kind=='admission':r['trainerAdmission']=True
                self.reject(report=r)

    def test_parent_requires_folded_card_physics_and_original_full_game(self):
        for kind in ['folded-card','payoff','residual','matrix','prior','policy','board','hash']:
            with self.subTest(kind=kind):
                p=copy.deepcopy(self.parent)
                if kind=='folded-card':p['originalJointPrior'][0]['dealtCombos'][0]='6d 4d'
                if kind=='payoff':p['sourcePayoffs'][0]['counts']['firstWins']+=1
                if kind=='residual':p['residuals'][0]['utilitiesBb'][3]+=1
                if kind=='matrix':p['solve']['projectedPayoff']['matrix'][0][0]+=1
                if kind=='prior':p['originalJointPrior'][0]['conditionalProbability']=.5
                if kind=='policy':p['candidate']['strategy'].popitem()
                if kind=='board':p['binding']['flop']=['8d','Kc','Kd']
                if kind=='hash':p['binding']['residualsHash']='0'*64
                self.reject(parent=p)

    def test_budget_and_duplicate_fields_cannot_change(self):
        budget=check.read(check.BUDGET)[0]
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'budget.json'
            for kind in ['freshReferenceRuns','maximumParentSolves','maximumExpandedBytesPerReport']:
                b=copy.deepcopy(budget);b[kind]+=1;p.write_text(json.dumps(b),encoding='utf-8')
                with patch.object(check,'BUDGET',p),self.assertRaises(ValueError):check.check()
            p.write_text('{"a":0,"\\u0061":1}',encoding='utf-8')
            with self.assertRaises(ValueError):check.read(p)


if __name__=='__main__':unittest.main()
