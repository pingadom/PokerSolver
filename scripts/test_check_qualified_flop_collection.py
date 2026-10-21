"""Real composed source evidence and adversarial outer identity/grade/review controls."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('qualified_collection_check', Path(__file__).with_name('check-qualified-flop-collection.py'))
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)
DATA = checker.DATA


class QualifiedCollectionCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.review = checker.read(DATA/'supplied-eleven-question-flop-collection-review.json', 1024*1024)[2]
        cls.old = checker.read(DATA/'supplied-ten-question-flop-collection-review.json', 1024*1024)[2]
        cls.weak = checker.read(DATA/'supplied-weak-hand-qualified-flop-drill.json', 256*1024)[2]

    def test_every_original_physical_proof_and_new_weak_proof_passes(self):
        proof = checker.check(DATA/'supplied-eleven-question-flop-collection-manifest.json', DATA/'supplied-eleven-question-flop-collection-review.json')
        self.assertEqual(11, proof['sourceQuestionsChecked'])
        self.assertEqual(22, proof['attemptsChecked'])
        self.assertEqual(2, proof['independentInputSets'])
        self.assertFalse(proof['trainerAdmission'])
        self.assertEqual(0, proof['freshLpSolves']+proof['freshCfrRuns'])
        self.assertEqual(json.loads((DATA/'supplied-eleven-question-flop-collection-independent-check.json').read_bytes()), proof)

    def test_foreign_sources_hidden_fields_false_counts_and_mutated_ev_are_rejected(self):
        mutations = [(['collectionHash'], '0'*64), (['selection'], 'same-ranges'),
                     (['questions'], self.review['questions'][:-1]),
                     (['counts', 'distinctQuestionsAttempted'], 22), (['counts', 'withinLimit'], True),
                     (['attempts', 0, 'index'], False), (['attempts', 0, 'questionIndex'], True),
                     (['attempts', 20, 'action'], 'check'), (['attempts', 20, 'feedback', 'decision', 'weak', 'reportHash'], '0'*64),
                     (['questions', 10, 'decision', 'weak', 'heroCards'], 'As Qs'),
                     (['questions', 10, 'decision', 'weak', 'context', 'board'], ['2s', '8s', 'Ts']),
                     (['questions', 10, 'decision', 'weak', 'hiddenCards'], 'secret')]
        for i in range(11):
            mutations.extend([(['questions', i, 'sourceQuestionIndex'], 100),
                              (['questions', i, 'decision', 'kind'], 'UNKNOWN'),
                              (['attempts', 2*i, 'feedback', 'questionIndex'], (i+1)%11),
                              (['attempts', 2*i, 'feedback', 'sourceQuestionIndex'], 100)])
        for i in (20, 21):
            for key in ('lowerEvBb', 'upperEvBb', 'lowerLossBb', 'upperLossBb', 'classification'):
                mutations.append((['attempts', i, 'feedback', 'decision', 'weak', 'selected', key], 'forged'))
        for keys, value in mutations:
            with self.subTest(keys=keys):
                changed = copy.deepcopy(self.review)
                cursor = changed
                for key in keys[:-1]: cursor = cursor[key]
                cursor[keys[-1]] = value
                with self.assertRaises(ValueError): checker.verify_review(changed, self.old, self.weak)

    def test_zero_hundred_and_excess_attempts_keep_distinct_coverage_separate(self):
        changed = copy.deepcopy(self.review)
        changed['attempts'] = []
        changed['counts'] = dict(distinctQuestionsAttempted=0, withinLimit=0, outsideLimit=0, strategyDependent=0)
        self.assertEqual(0, checker.verify_review(changed, self.old, self.weak))
        changed['attempts'] = [dict(self.review['attempts'][20], index=i) for i in range(100)]
        changed['counts'] = dict(distinctQuestionsAttempted=1, withinLimit=100, outsideLimit=0, strategyDependent=0)
        self.assertEqual(100, checker.verify_review(changed, self.old, self.weak))
        changed['attempts'].append(dict(changed['attempts'][0], index=100))
        with self.assertRaises(ValueError): checker.verify_review(changed, self.old, self.weak)

    def test_manifest_mutations_reject_before_any_physical_checker(self):
        manifest = checker.expected_manifest()
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'manifest.json'
            for field, value in [('weakInputHash', manifest['legacyInputHash']), ('legacyManifestFile', '../outside.json'),
                                 ('collectionHash', '0'*64), ('weakReportHash', '0'*64), ('trainerAdmission', True),
                                 ('startupBudget', dict(checker.BUDGET, freshIterations=731999))]:
                with self.subTest(field=field):
                    changed = dict(manifest, **{field: value})
                    path.write_text(json.dumps(changed), encoding='utf-8')
                    with patch.object(checker.legacy, 'check', side_effect=AssertionError('must reject before replay')):
                        with self.assertRaisesRegex(ValueError, 'Fixed qualified manifest'):
                            checker.check(path, DATA/'supplied-eleven-question-flop-collection-review.json')


if __name__ == '__main__':
    unittest.main()
