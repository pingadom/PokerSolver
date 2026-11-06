import copy
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('batch_feedback_checker',Path(__file__).with_name('check-root-sequence-batch-feedback.py'))
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)


class BatchFeedbackCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context=checker.context();cls.request=checker.read(checker.REQUEST);cls.report=checker.read(checker.REPORT)

    def corrupt(self,change):
        q,r=copy.deepcopy(self.request),copy.deepcopy(self.report);change(q,r)
        with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):checker.check_values(q,r,self.context)

    def test_original_physics_certificates_old_qualification_and_all_bounds(self):
        p=checker.check_values(self.request,self.report,self.context)
        self.assertTrue(p['qualifiedForOfflineDrill']);self.assertFalse(p['trainerAdmission'])
        self.assertEqual((3,9,36),(p['movesChecked'],p['minimumLowerChecks'],p['utilityLossInequalitiesChecked']))
        self.assertEqual(3,p['sourceBatchCheck']['endpointCertificatesChecked'])
        self.assertTrue(p['previousFeedbackCheck']['qualifiedForOfflineDrill'])
        self.assertEqual((0,0),(p['freshLpSolves'],p['freshCfrRuns']))
        self.assertFalse(p['minimumUpperUsedAsFullActionCeiling'] or p['currentDecisionEvShiftApplied'])
        self.assertEqual(self.report['previousFeedback']['moves'],self.report['moves'])
        primary=self.report['sourceBatch']['policies'][0]['original']
        self.assertGreater(primary['actionUtilitiesBb']['raise:9.0'],self.report['sourceBatch']['batch']['minima'][-1]['minimum']['upperBound']+.5)

    def test_frozen_sources_report_bytes_and_stored_expanded_caps(self):
        self.assertEqual(checker.REPORT_SHA,hashlib.sha256(checker.REPORT.read_bytes()).hexdigest())
        self.assertLess(checker.REPORT.stat().st_size,checker.MAX_BYTES)
        self.assertEqual(checker.BATCH_SHA,hashlib.sha256(checker.batch.REPORT.read_bytes()).hexdigest())
        self.assertEqual(checker.PREVIOUS_SHA,hashlib.sha256(checker.previous.REPORT.read_bytes()).hexdigest())
        call=self.report['lowerBoundSources'][1]
        self.assertLess(call['sequenceMinimumLowerBb'],call['originalConservativeLowerBb'])
        self.assertEqual(call['originalConservativeLowerBb'],call['selectedLowerBb'])

    def test_rejects_changed_request_schema_pins_threshold_and_batch(self):
        for key in ['schemaVersion','batchStoredSha256','previousFeedbackStoredSha256']:
            self.corrupt(lambda q,_,k=key:q.__setitem__(k,'wrong'))
        self.corrupt(lambda q,_:q.__setitem__('maximumLossBb',.0101))
        self.corrupt(lambda q,_:q['batchRequest']['actions'].reverse())
        self.corrupt(lambda q,_:q.__setitem__('extra',True))

    def test_rejects_false_scope_admission_qualification_and_incomplete_source(self):
        for key in ['schemaVersion','publicationStatus','boundsScope','qualificationScope']:
            self.corrupt(lambda _,r,k=key:r.__setitem__(k,'wrong'))
        self.corrupt(lambda _,r:r.__setitem__('trainerAdmission',True))
        self.corrupt(lambda _,r:r.__setitem__('qualifiedForOfflineDrill',False))
        self.corrupt(lambda _,r:r['sourceBatch'].__setitem__('completeMinima',False))
        self.corrupt(lambda _,r:r['sourceBatch'].__setitem__('referenceSupportedMinima',False))

    def test_rejects_changed_bound_inputs_selected_floors_and_original_ceilings(self):
        for key in ['originalConservativeLowerBb','sequenceMinimumLowerBb','selectedLowerBb','originalFullActionUpperBb']:
            self.corrupt(lambda _,r,k=key:r['lowerBoundSources'][1].__setitem__(k,42.))
        self.corrupt(lambda _,r:r['moves'][-1].__setitem__('upperEvBb',r['sourceBatch']['batch']['minima'][-1]['minimum']['upperBound']))
        self.corrupt(lambda _,r:r['lowerBoundSources'][1].__setitem__('sequenceMinimumLowerBb',r['lowerBoundSources'][1]['originalConservativeLowerBb']))

    def test_rejects_missing_duplicate_reordered_or_unknown_menu_records(self):
        self.corrupt(lambda _,r:r['moves'].pop())
        self.corrupt(lambda _,r:r['moves'].reverse())
        self.corrupt(lambda _,r:r['moves'].__setitem__(1,copy.deepcopy(r['moves'][0])))
        self.corrupt(lambda _,r:r['lowerBoundSources'].reverse())
        self.corrupt(lambda _,r:r['moves'][0].__setitem__('action','jam'))

    def test_rejects_each_changed_utility_loss_bound_and_classification(self):
        for key in ['lowerEvBb','upperEvBb','lowerLossBb','upperLossBb']:
            self.corrupt(lambda _,r,k=key:r['moves'][0].__setitem__(k,42.))
        self.corrupt(lambda _,r:r['moves'][1].__setitem__('classification','OUTSIDE_LIMIT'))
        self.corrupt(lambda _,r:r['moves'][-1].__setitem__('classification','WITHIN_LIMIT'))

    def test_rejects_missing_changed_or_falsely_supported_fresh_policies(self):
        self.corrupt(lambda _,r:r['policies'].pop())
        for key,value in [('iterations',2),('solutionHash','0'*64),('minimumLowerChecks',0),('utilityLossInequalitiesChecked',11),
                          ('sourceMinimaSupported',False),('boundsChecked',False),('maximumBoundViolationBb',1.)]:
            self.corrupt(lambda _,r,k=key,v=value:r['policies'][0].__setitem__(k,v))
        self.corrupt(lambda _,r:r['policies'][0]['failures'].append('WRONG'))

    def test_rejects_forged_original_certificates_faces_work_and_previous_qualification(self):
        self.corrupt(lambda _,r:r['sourceBatch']['batch']['minima'][0]['witness']['point'].__setitem__(0,42.))
        self.corrupt(lambda _,r:r['sourceBatch']['batch']['minima'][0].__setitem__('securityFaceHash','0'*64))
        self.corrupt(lambda _,r:r['sourceBatch']['batch']['work'].__setitem__('parentLpSolves',6))
        self.corrupt(lambda _,r:r['previousFeedback'].__setitem__('qualifiedForOfflineDrill',False))
        self.corrupt(lambda _,r:r['previousFeedback']['sourceMinimum']['minimum']['support'].pop())

    def test_rejects_erased_or_changed_previous_menu_comparisons(self):
        for key in ['lowerEvDifferenceBb','upperEvDifferenceBb','lowerLossDifferenceBb','upperLossDifferenceBb']:
            self.corrupt(lambda _,r,k=key:r['previousMenuComparison'][0].__setitem__(k,42.))
        self.corrupt(lambda _,r:r['previousMenuComparison'][0].__setitem__('classificationUnchanged',False))
        self.corrupt(lambda _,r:r['previousMenuComparison'][0]['previousMove'].__setitem__('upperEvBb',42.))
        self.corrupt(lambda _,r:r['previousMenuComparison'].pop())

    def test_rejects_boolean_counters_nonfinite_numbers_and_false_reason_lists(self):
        self.corrupt(lambda _,r:r['policies'][0].__setitem__('utilityLossInequalitiesChecked',True))
        self.corrupt(lambda _,r:r['policies'][0].__setitem__('minimumLowerChecks',True))
        self.corrupt(lambda _,r:r['previousMenuComparison'][0].__setitem__('lowerEvDifferenceBb',False))
        self.corrupt(lambda _,r:r['moves'][0].__setitem__('lowerEvBb',float('nan')))
        self.corrupt(lambda _,r:r['rejectionReasons'].append('FALSE_FAILURE'))

    def test_outside_face_and_unsupported_minima_keep_failures_and_no_checked_bounds(self):
        p=copy.deepcopy(self.report['sourceBatch']['policies'][0]);p['allMinimumLowerBoundsSupported']=False
        p['original']['insideDeclaredFace']=False;p['failures']=['ORIGINAL_QUALITY_FAILURE']
        check=checker.assess(p,self.report['moves'])
        self.assertFalse(check['boundsChecked'] or check['sourceMinimaSupported'])
        self.assertIsNone(check['maximumBoundViolationBb']);self.assertEqual(0,check['utilityLossInequalitiesChecked'])
        self.assertEqual(['BATCH_MENU_REFERENCE_OUTSIDE_FACE','BATCH_MINIMA_NOT_SUPPORTED','ORIGINAL_QUALITY_FAILURE'],check['failures'])
        self.assertTrue(checker.reasons(self.report['sourceBatch'],self.report['moves'],[check]))

    def test_each_utility_loss_inequality_detects_an_independent_policy_violation(self):
        p=self.report['sourceBatch']['policies'][0];action=self.report['moves'][0]['action']
        value=p['original']['actionUtilitiesBb'][action];loss=p['original']['actionLossBb'][action]
        for field,changed in [('lowerEvBb',value+1),('upperEvBb',value-1),('lowerLossBb',loss+1),('upperLossBb',loss-1)]:
            menu=copy.deepcopy(self.report['moves']);menu[0][field]=changed
            check=checker.assess(p,menu)
            self.assertGreater(check['maximumBoundViolationBb'],.9)
            self.assertIn('REFERENCE_OUTSIDE_BATCH_MENU_BOUNDS',check['failures'])

    def test_strict_duplicate_trailing_nonfinite_and_oversized_json_rejects(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'bad.json'
            for raw in [b'{"a":0,"a":1}',b'{} {}',b'{"a":NaN}']:
                p.write_bytes(raw)
                with self.assertRaises(ValueError):checker.read(p)
            p.write_bytes(b' '*(checker.MAX_BYTES+1))
            with self.assertRaises(ValueError):checker.read(p)


if __name__=='__main__':unittest.main()
