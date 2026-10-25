"""Composition, source sharing and canonical cross-question review corruption controls."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('unpaired_collection_checker',ROOT/'scripts/check-unpaired-flop-collection.py')
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class UnpairedCollectionCheckTest(unittest.TestCase):
    def test_complete_original_sources_and_all_eighteen_questions_match_receipt(self):
        expected=json.loads((ROOT/'docs/data/supplied-eighteen-question-flop-collection-independent-check.json').read_bytes())
        self.assertEqual(expected,checker.check())
        self.assertEqual(18,expected['sourceQuestionsChecked'])
        self.assertEqual(2,expected['independentInputSets'])
        self.assertEqual(2,expected['sharedReferencePoliciesForFourAddedQuestions'])
        self.assertEqual(36,expected['reviewCheck']['attemptsChecked'])
    def test_inventory_mapping_and_every_feedback_field_are_owned(self):
        old,new=checker.source_review();original=json.loads(checker.REVIEW.read_bytes())
        for kind in ['missing-question','reorder','source-kind','local-index','old-question','shared-hand','foreign-board','foreign-report','hash','ev','classification','coverage','counts','attempt-index','illegal','too-many']:
            value=copy.deepcopy(original)
            if kind=='missing-question':value['questions'].pop()
            elif kind=='reorder':value['questions'].reverse()
            elif kind=='source-kind':value['questions'][14]['decision']['kind']=checker.KINDS[0]
            elif kind=='local-index':value['questions'][14]['sourceQuestionIndex']=14
            elif kind=='old-question':value['questions'][0]['decision']['legacy']['index']=1
            elif kind=='shared-hand':value['questions'][14]['decision']['shared']['decision']['heroCards']='As Qs'
            elif kind=='foreign-board':value['questions'][15]['decision']['shared']['decision']['context']['board']=['8d','Kc','Kd']
            elif kind=='foreign-report':value['questions'][15]['decision']['shared']['decision']['reportHash']=checker.legacy.shared.REPORT_HASH
            elif kind=='hash':value['collectionHash']='0'*64
            elif kind=='ev':value['attempts'][-1]['feedback']['decision']['shared']['selected']['lowerEvBb']=99
            elif kind=='classification':value['attempts'][-1]['feedback']['decision']['shared']['selected']['classification']='OUTSIDE_LIMIT'
            elif kind=='coverage':value['counts']['distinctQuestionsAttempted']=36
            elif kind=='counts':value['counts']['withinLimit']=36
            elif kind=='attempt-index':value['attempts'][0]['index']=2
            elif kind=='illegal':value['attempts'][-1]['action']='check'
            elif kind=='too-many':value['attempts']=[copy.deepcopy(value['attempts'][0]) for _ in range(101)]
            with self.subTest(kind=kind),self.assertRaises(ValueError):checker.verify_review(value,old,new)
    def test_empty_repeated_and_subset_coverage_have_correct_denominators(self):
        old,new=checker.source_review();original=json.loads(checker.REVIEW.read_bytes())
        empty=copy.deepcopy(original);empty['attempts']=[];empty['counts']=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
        self.assertEqual(0,checker.verify_review(empty,old,new)['attemptsChecked'])
        repeated=copy.deepcopy(original);attempt=repeated['attempts'][-1]
        repeated['attempts']=[dict(copy.deepcopy(attempt),index=i) for i in range(100)]
        repeated['counts']=dict(distinctQuestionsAttempted=1,withinLimit=100,outsideLimit=0,strategyDependent=0)
        self.assertEqual(100,checker.verify_review(repeated,old,new)['attemptsChecked'])
    def test_manifest_cannot_relabel_work_or_sources(self):
        original=json.loads(checker.MANIFEST.read_bytes())
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'manifest.json'
            for field,value in [('collectionHash','0'*64),('sharedInputHash','0'*64),('batchReportHash',checker.legacy.shared.REPORT_HASH),
                ('legacyManifestFile','../source.json'),('batchRequestSha256','0'*64),('startupBudget',dict(sourceReplays=5,freshReferenceRuns=10,freshIterations=732000))]:
                changed=copy.deepcopy(original);changed[field]=value;path.write_text(json.dumps(changed),encoding='utf-8')
                with self.subTest(field=field),self.assertRaises(ValueError):checker.check(path,checker.REVIEW)

if __name__=='__main__':unittest.main()
