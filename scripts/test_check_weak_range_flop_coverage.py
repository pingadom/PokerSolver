import copy
import gzip
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('coverage_checker',HERE/'check-weak-range-flop-coverage.py')
checker=importlib.util.module_from_spec(spec);sys.modules[spec.name]=checker;spec.loader.exec_module(checker)


class WeakRangeCoverageProofTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report=json.loads(gzip.decompress(checker.REPORT.read_bytes()))
        cls.original_parent=checker.weak.checked_parent
        cls.cache={}
        # Only identical, previously fully verified parents reuse physics in mutation controls.
        def checked(parent,request):
            key=json.dumps([parent,request],sort_keys=True)
            if key not in cls.cache: cls.cache[key]=cls.original_parent(parent,request)
            return cls.cache[key]
        cls.parent_patch=patch.object(checker.weak,'checked_parent',side_effect=checked)
        cls.parent_patch.start()
        cls.receipt=checker.check()

    @classmethod
    def tearDownClass(cls): cls.parent_patch.stop()

    def reject(self,mutate):
        report=copy.deepcopy(self.report);mutate(report)
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'report.json.gz'
            path.write_bytes(gzip.compress(json.dumps(report).encode()))
            with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):
                checker.check(checker.REQUEST,path,bind_saved_bytes=False)

    def test_actual_parent_and_all_32_endpoint_certificates_match_the_frozen_receipt(self):
        frozen=json.loads((checker.DATA/'supplied-weak-range-flop-coverage-independent-check.json').read_bytes())
        self.assertEqual(frozen,self.receipt)
        self.assertEqual(32,self.receipt['endpointCertificatesChecked'])
        self.assertEqual(15792192,self.receipt['parentCheck']['fullBoardsRegenerated'])
        self.assertFalse(self.receipt['numericalRejectionsIndependentlyCertified'])
        self.assertEqual(['fold','call','call','call'],[r['action'] for r in self.receipt['withinLimitMoves']])

    def test_inventory_reservation_identity_and_admission_corruptions_reject(self):
        controls=[
            lambda r:r.update(trainerAdmission=True),
            lambda r:r['reservation'].update(freshReferenceRuns=2),
            lambda r:r['reservation'].update(automaticRetries=1),
            lambda r:r['reservation'].update(caseAttempts=7),
            lambda r:r['counts'].update(solved=8),
            lambda r:r['cases'].pop(),
            lambda r:r['cases'].reverse(),
            lambda r:r['cases'][1].update(index=0),
            lambda r:r['cases'][1]['request'].update(heroCards='Ah Kh'),
            lambda r:r['request']['heroHands'].reverse(),
            lambda r:r['binding'].update(betPotFraction=1),
        ]
        for index,control in enumerate(controls):
            with self.subTest(index=index): self.reject(control)

    def test_numerical_rejections_cannot_be_hidden_or_relabelled_as_reach_proofs(self):
        controls=[
            lambda r:r['cases'][0].update(outcome='ACTION_SEPARATION'),
            lambda r:r['cases'][2]['rejection'].update(reason='INSUFFICIENT_REACH'),
            lambda r:r['cases'][4]['rejection'].update(detail='infeasible poker strategy'),
            lambda r:r['cases'][6]['rejection'].update(layer='LP'),
            lambda r:r['cases'][0]['rejection'].update(reachFailure={}),
            lambda r:r['cases'][2].update(moves=[r['cases'][1]['moves'][0]]),
            lambda r:r['cases'][0].update(conditional=r['cases'][1]['conditional']),
        ]
        for index,control in enumerate(controls):
            with self.subTest(index=index): self.reject(control)

    def test_new_strong_hand_original_certificates_and_posteriors_are_recomputed(self):
        def action(report): return report['cases'][3]['conditional']['actions'][1]
        def point(report): action(report)['diagnostic']['lowerWitness']['point'][0]+=1
        def dual(report): action(report)['diagnostic']['upperWitnesses'][0]['dual'][0]+=1
        def posterior(report): action(report)['diagnostic']['minimumReachWitness']['posterior'][0]['probability']+=.1
        def ratio(report): action(report)['diagnostic']['lowerWitness']['opponentVariables'][0]+=1
        controls=[point,dual,posterior,ratio,
            lambda r:action(r).update(lowerUtilityBb=100),
            lambda r:action(r)['diagnostic'].update(securitySlack=.01),
            lambda r:action(r)['diagnostic']['work'].update(intervalLpArithmeticWork=0),
            lambda r:r['cases'][5]['moves'][0].update(classification='WITHIN_LIMIT'),
            lambda r:r['cases'][7]['conditional']['parent']['binding'].update(flop=['2s','8s','Ts']),
        ]
        for index,control in enumerate(controls):
            with self.subTest(index=index): self.reject(control)

    def test_checker_cli_refuses_to_overwrite_an_existing_receipt(self):
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)/'keep.json';output.write_text('keep')
            with patch.object(sys,'argv',['checker',str(checker.REQUEST),str(checker.REPORT),'--output',str(output)]):
                with self.assertRaises(ValueError): checker.main()
            self.assertEqual('keep',output.read_text())


if __name__=='__main__': unittest.main()
