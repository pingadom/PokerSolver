"""Semantic corruption controls for complete BB discovery and the phase-I LP regression."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('bb_discovery_check', Path(__file__).with_name('check-big-blind-flop-discovery.py'))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class BigBlindFlopDiscoveryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.receipt = check.check()
        cls.report = check.read(check.REPORT)[0]
        cls.parent = check.read(check.source.PARENT_REPORT)[0]
        cls.tree = check.source.literal_tree(cls.parent, dict(binding=cls.parent['binding']['sourceBinding']),
                                             cls.parent['request']['connected'])

    def test_complete_inventory_physics_and_original_certificates(self):
        self.assertEqual(self.receipt['contextsChecked'], 6)
        self.assertEqual(self.receipt['actionsChecked'], 12)
        self.assertEqual(self.receipt['originalFailuresPreserved'], 6)
        self.assertEqual(self.receipt['originalEndpointCertificatesChecked'], 51)
        self.assertEqual(self.receipt['parentCheck']['literalTreeStates'], 1321)
        self.assertEqual(self.receipt['parentCheck']['fullBoardsRegenerated'], 15792192)
        self.assertEqual(self.receipt['qualifiedTrainerQuestions'], 0)
        self.assertEqual(self.receipt['freshLpSolves'], 0)
        self.assertFalse(self.receipt['trainerAdmission'])

    def test_first_move_context_recall_continuation_and_ev_cannot_be_edited(self):
        for kind in ['actor', 'board', 'pot', 'past', 'plan', 'ev', 'loss', 'classification', 'separation']:
            with self.subTest(kind=kind):
                item = copy.deepcopy(self.report['cases'][0])
                report = item['conditional']
                action = report['actions'][0]
                audit = action['diagnostic']
                if kind == 'actor': report['context']['actingSeat'] = 'BTN'
                if kind == 'board': report['context']['board'][0] = '8d'
                if kind == 'pot': report['context']['potBb'] += 1
                if kind == 'past': audit['fixedHeroPast'] = []
                if kind == 'plan': audit['numeratorPlans'].pop()
                if kind == 'ev': action['lowerUtilityBb'] += 1
                if kind == 'loss': action['upperLossBb'] = 0
                if kind == 'classification': item['moves'][0]['classification'] = 'WITHIN_LIMIT'
                if kind == 'separation': item['outcome'] = 'ACTION_SEPARATION'
                with self.assertRaises((ValueError, KeyError)):
                    check.check_case(item, self.parent, self.tree)

    def test_later_opponent_bet_does_not_cancel_with_hero_past(self):
        for kind in ['denominator', 'past', 'reach', 'posterior', 'sequence', 'flow', 'global', 'tv']:
            with self.subTest(kind=kind):
                item = copy.deepcopy(self.report['cases'][1])
                audit = item['conditional']['actions'][1]['diagnostic']
                witness = audit['upperWitnesses'][0]
                if kind == 'denominator': audit['denominator']['constant'] = 0
                if kind == 'past': audit['fixedHeroPast'].pop()
                if kind == 'reach': witness['questionReach'] = 1
                if kind == 'posterior': witness['posterior'][0]['probability'] = 1
                if kind == 'sequence': witness['posterior'][0]['opponentSequence'] = 0
                if kind == 'flow': witness['opponentFlow']['realization'][0] = 0
                if kind == 'global': witness['globalHeroBestResponse'] += 1
                if kind == 'tv': witness['posteriorTotalVariation'] = .1
                with self.assertRaises((ValueError, KeyError)):
                    check.check_case(item, self.parent, self.tree)

    def test_every_original_endpoint_is_checked_without_using_snapshot_hashes(self):
        for kind in ['dual', 'point', 'objective', 'lp-mode', 'work', 'baseline', 'projection', 'floor']:
            with self.subTest(kind=kind):
                item = copy.deepcopy(self.report['cases'][4])
                audit = item['conditional']['actions'][0]['diagnostic']
                witness = audit['lowerWitness']
                if kind == 'dual': witness['dual'][0] += 1
                if kind == 'point': witness['point'][0] += 1
                if kind == 'objective': witness['certificate']['dualValue'] += 1
                if kind == 'lp-mode': witness['certificate']['algorithm'] = 'old'
                if kind == 'work': audit['work']['intervalLpArithmeticWork'] += 1
                if kind == 'baseline': audit['baseline']['upperValue'] += 1
                if kind == 'projection': audit['opponentProjection']['offset'][0] = 0
                if kind == 'floor': audit['requiredMinimumReach'] = 0
                with self.assertRaises((ValueError, KeyError)):
                    check.check_case(item, self.parent, self.tree)

    def test_captured_lp_is_bound_to_the_literal_first_bb_endpoint(self):
        fixture = check.read(check.CAPTURE)[0]
        for kind in ['matrix', 'rhs', 'cost']:
            with self.subTest(kind=kind):
                value = copy.deepcopy(fixture)
                if kind == 'matrix': value['matrix'][0][0] += 1
                else: value[kind][0] += 1
                with self.assertRaises(ValueError):
                    check.check_case(self.report['cases'][0], self.parent, self.tree, value)

    def test_batch_admission_budget_and_complete_inventory_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'changed.json'
            for kind in ['admission', 'drop', 'order', 'count', 'reservation']:
                with self.subTest(kind=kind):
                    report = copy.deepcopy(self.report)
                    if kind == 'admission': report['trainerAdmission'] = True
                    if kind == 'drop': report['cases'].pop()
                    if kind == 'order': report['cases'][0]['index'] = 1
                    if kind == 'count': report['counts']['actionSeparated'] = 6
                    if kind == 'reservation': report['reservation']['freshReferenceRuns'] = 2
                    path.write_text(json.dumps(report), encoding='utf-8')
                    with self.assertRaises(ValueError):
                        check.check(report_path=path, bind_saved_bytes=False)
            budget = check.read(check.BUDGET)[0]
            for key in ['maximumParentSolves', 'automaticRetries', 'requiredMinimumReach']:
                value = dict(budget)
                value[key] += 1
                path.write_text(json.dumps(value), encoding='utf-8')
                with patch.object(check, 'BUDGET', path), self.assertRaises(ValueError):
                    check.check()

    def test_strict_duplicate_and_expansion_guards(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'bad.json'
            for text in ['{"a":0,"\\u0061":1}', '{"a":NaN}', '{}{}']:
                path.write_text(text, encoding='utf-8')
                with self.assertRaises((ValueError, json.JSONDecodeError)):
                    check.read(path)
            path.write_bytes(b' ' * (8 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError):
                check.read(path)


if __name__ == '__main__':
    unittest.main()
