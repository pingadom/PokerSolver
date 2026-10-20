"""Complete paired curriculum proof and adversarial source, budget and policy changes."""
import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('test_paired_curriculum', ROOT/'scripts/check-paired-flop-curriculum.py')
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)
REQUEST = ROOT/'docs/data/supplied-paired-four-hand-curriculum-request.json'
REPORT = ROOT/'docs/data/supplied-paired-four-hand-curriculum-report.json.gz'


class PairedCurriculumCheckTest(unittest.TestCase):
    def test_complete_four_hand_receipt_and_full_work_accounting(self):
        actual = checker.check(REQUEST, REPORT)
        saved = json.loads((ROOT/'docs/data/supplied-paired-four-hand-curriculum-independent-check.json').read_text())
        self.assertEqual(saved, actual)
        self.assertEqual(32, actual['originalEndpointCertificatesChecked'])
        self.assertEqual(12, actual['policyContextChecks'])
        self.assertEqual(12, actual['work']['parentSolves'])
        self.assertEqual(24, actual['work']['parentLpSolves'])
        self.assertEqual(32, actual['work']['endpointLpSolves'])
        self.assertEqual(635040000, actual['work']['referenceVisitedNodes'])
        self.assertFalse(actual['trainerAdmission'])
        self.assertEqual(0, actual['freshCfrRuns'])

    def test_semantic_edits_fail_with_byte_binding_disabled(self):
        original = json.loads(gzip.decompress(REPORT.read_bytes()))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'changed.json'
            for kind in ['admission','partial','order','value','posterior','context','classification','primary','reference','reach','regret','reference-work','work','parent-work','fresh-count','qualified']:
                value = copy.deepcopy(original)
                d = value['decisions'][0]
                if kind == 'admission': value['trainerAdmission'] = True
                elif kind == 'partial': value['decisions'].pop()
                elif kind == 'order': value['decisions'].reverse()
                elif kind == 'value': d['conditional']['actions'][1]['lowerUtilityBb'] += .1
                elif kind == 'posterior': d['conditional']['actions'][1]['diagnostic']['lowerWitness']['posterior'][0]['probability'] += .1
                elif kind == 'context': d['conditional']['context']['toCallBb'] = 0
                elif kind == 'classification': d['moves'][0]['classification'] = 'WITHIN_LIMIT'
                elif kind == 'primary': d['primary']['actionUtilitiesBb']['call'] += .1
                elif kind == 'reference': value['references'][0]['policy']['strategy'][next(iter(value['references'][0]['policy']['strategy']))]['call'] = .1
                elif kind == 'reach': d['references'][0]['counterfactualQuestionReachGivenPhysicalFlop'] = .1
                elif kind == 'regret': d['references'][1]['decisionRegretBb'] = .1
                elif kind == 'reference-work': value['references'][0]['work']['statistics']['visitedNodes'] -= 1
                elif kind == 'work': value['work']['lpArithmeticWork'] -= 1
                elif kind == 'parent-work': value['work']['parentSolves'] = 4
                elif kind == 'fresh-count': value['work']['freshCfrRuns'] = 8
                elif kind == 'qualified': value['qualifiedForOfflineDrill'] = False
                path.write_text(json.dumps(value), encoding='utf-8')
                with self.subTest(kind=kind), self.assertRaises(ValueError):
                    checker.check(REQUEST, path, bind_saved_bytes=False)

    def test_request_caps_shared_identity_and_fixed_order_are_mandatory(self):
        original = json.loads(REQUEST.read_text())
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'request.json'
            for kind in ['count','order','mode','parent','budget','reference','loss','unknown']:
                value = copy.deepcopy(original)
                if kind == 'count': value['questions'].pop()
                elif kind == 'order': value['questions'].reverse()
                elif kind == 'mode': value['questions'][0]['intervalAlgorithm'] = 'legacy'
                elif kind == 'parent': value['questions'][2]['parent']['connected']['betPotFraction'] = .25
                elif kind == 'budget': value['workBudget']['lpArithmeticWork'] += 1
                elif kind == 'reference': value['referenceBudget']['totalIterations'] *= 4
                elif kind == 'loss': value['maximumLossBb'] = .005
                elif kind == 'unknown': value['checkpoint'] = 'imported'
                path.write_text(json.dumps(value), encoding='utf-8')
                with self.subTest(kind=kind), self.assertRaises(ValueError):
                    checker.check(path, REPORT, bind_saved_bytes=False)
            path.write_text('{"selection":1,"selection":2}', encoding='utf-8')
            with self.assertRaises(ValueError): checker.check(path, REPORT)
            path.write_bytes(b' '*(8*1024*1024+1))
            with self.assertRaises(ValueError): checker.check(path, REPORT)
            zipped = Path(folder)/'too-large.json.gz'
            zipped.write_bytes(gzip.compress(b' '*(8*1024*1024+1)))
            with self.assertRaises(ValueError): checker.check(REQUEST, zipped)


if __name__ == '__main__': unittest.main()
