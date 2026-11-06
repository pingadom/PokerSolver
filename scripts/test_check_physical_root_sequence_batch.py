import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('physical_sequence_batch_checker', Path(__file__).with_name('check-physical-root-sequence-batch.py'))
checker = importlib.util.module_from_spec(spec); spec.loader.exec_module(checker)


class PhysicalSequenceBatchCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = checker.context()
        cls.request = checker.read(checker.REQUEST)
        cls.report = checker.read(checker.REPORT)

    def corrupt(self, change):
        request, report = copy.deepcopy(self.request), copy.deepcopy(self.report)
        change(request, report)
        with self.assertRaises((ValueError, KeyError, IndexError, TypeError)):
            checker.check_values(request, report, self.context)

    def audit(self, change, index=0): self.corrupt(lambda _, r: change(r['batch']['minima'][index]))

    def test_original_physical_tree_all_certificates_and_nine_reference_checks(self):
        p = checker.check_values(self.request, self.report, self.context)
        self.assertTrue(p['completeMinima'] and p['referenceSupportedMinima'])
        self.assertEqual((2, 3, 9), (p['distinctBatchParentCertificatesChecked'], p['endpointCertificatesChecked'], p['actionLowerChecks']))
        self.assertEqual((0, 0, False), (p['freshLpSolves'], p['freshCfrRuns'], p['trainerAdmission']))
        self.assertFalse(p['fullActionUpperEndpointClaim'] or p['coherentMinimumPolicyClaim'])

    def test_frozen_original_sources_and_batch_identity(self):
        self.assertEqual(checker.REPORT_SHA, hashlib.sha256(checker.REPORT.read_bytes()).hexdigest())
        self.assertEqual(checker.PRIOR_SHA, hashlib.sha256(checker.prior.REPORT.read_bytes()).hexdigest())
        self.assertTrue(self.report['comparisons'][-1]['previousRaiseBracketsOverlap'])
        self.assertTrue(checker.same_endpoint(self.report['batch']['minima'][-1], self.report['previousMinimum']['minimum']))

    def test_rejects_changed_original_request_menu_and_source(self):
        for key in ['schemaVersion','sourceReportHash','previousMinimumStoredSha256']:
            self.corrupt(lambda q, _, k=key: q.__setitem__(k,'wrong'))
        self.corrupt(lambda q, _: q['actions'].reverse())
        self.corrupt(lambda q, _: q['sourceRequest']['envelope'].__setitem__('heroCards','As Ah'))
        self.corrupt(lambda _, r: r['previousMinimum']['minimum']['support'].pop())
        self.corrupt(lambda _, r: r['originalEnvelope']['actions'].pop())

    def test_rejects_admission_scope_incomplete_menu_and_forged_parent(self):
        self.corrupt(lambda _, r: r.__setitem__('trainerAdmission',True))
        self.corrupt(lambda _, r: r['batch'].__setitem__('scope','FULL_INTERVALS'))
        self.corrupt(lambda _, r: r['batch']['minima'].pop())
        self.corrupt(lambda _, r: r['batch']['minima'].reverse())
        self.audit(lambda a: a['firstParentWitness']['point'].__setitem__(0,42.))
        self.audit(lambda a: a['baseline'].__setitem__('snapshotHash','0'*64))

    def test_rejects_reset_or_increased_cumulative_limits_and_boolean_counters(self):
        for key, value in [('parentSolveCalls',3),('parentLpSolves',6),('endpointLpSolves',1),('preflightCompilerUnits',0),
                           ('compilerLimit',8000001),('endpointPivotLimit',10001),('endpointArithmeticLimit',4000000001),('parentSolveCalls',True)]:
            self.corrupt(lambda _, r, k=key, v=value: r['batch']['work'].__setitem__(k,v))
        self.audit(lambda a: a['work'].__setitem__('compilerLimit',8000000),1)
        self.audit(lambda a: a['work'].__setitem__('endpointPivots',False))

    def test_rejects_forged_aggregate_and_endpoint_work(self):
        for key in ['parentCompilerUnits','parentLpArithmeticWork','compilerUnits','endpointPivots','endpointArithmeticWork']:
            self.corrupt(lambda _, r, k=key: r['batch']['work'].__setitem__(k,0))
        self.audit(lambda a: a['work'].__setitem__('enumeratedContinuationPlans',52))
        self.audit(lambda a: a['work'].__setitem__('endpointLpSolves',0))

    def test_rejects_changed_global_face_original_reach_and_root_support(self):
        self.audit(lambda a: a.__setitem__('securitySlack',.01))
        self.audit(lambda a: a.__setitem__('securityFaceHash','0'*64))
        self.audit(lambda a: a.__setitem__('requiredMinimumReach',0.))
        self.audit(lambda a: a.__setitem__('originalQuestionReach',1.))
        self.audit(lambda a: a['support'][0].__setitem__('stateHash','0'*64))
        self.audit(lambda a: a['support'][0].__setitem__('posteriorProbability',.9))

    def test_rejects_changed_forced_action_domain_and_endpoint_problem(self):
        self.audit(lambda a: a['heroDomain'].__setitem__('forcedSequence',0))
        self.audit(lambda a: a['conditionalPayoff'].__setitem__('constant',42.))
        self.audit(lambda a: a['problem']['matrix'][0].__setitem__(0,42.))
        self.audit(lambda a: a['problem']['cost'].__setitem__(-1,42.))

    def test_rejects_changed_certificate_original_flow_and_best_response(self):
        self.audit(lambda a: a['witness']['point'].__setitem__(-1,-1.))
        self.audit(lambda a: a['witness']['dual'].__setitem__(0,-1.))
        self.audit(lambda a: a['continuationFlow']['realization'].__setitem__(0,.5))
        self.audit(lambda a: a['witness'].__setitem__('conditionalHeroBestResponse',42.))
        self.audit(lambda a: a.__setitem__('continuationPolicyHash','0'*64))

    def test_rejects_changed_brackets_comparisons_and_missing_references(self):
        self.audit(lambda a: a['minimum'].__setitem__('lowerBound',.5))
        self.audit(lambda a: a['minimum'].__setitem__('upperBound',50.))
        self.corrupt(lambda _, r: r['comparisons'][0].__setitem__('lowerBoundDifferenceBb',42.))
        self.corrupt(lambda _, r: r['comparisons'][-1].__setitem__('exactPreviousRaiseExceptRemainingCompilerLimit',False))
        self.corrupt(lambda _, r: r['policies'].pop())
        self.corrupt(lambda _, r: r['policies'][0]['minimumLowerViolationsBb'].__setitem__('call',.1))
        self.corrupt(lambda _, r: r.__setitem__('referenceSupportedMinima',False))

    def test_primary_above_minimum_upper_is_supported_and_only_lower_bounds_are_checked(self):
        r=self.report;primary=r['policies'][0]
        self.assertGreater(primary['original']['actionUtilitiesBb']['raise:9.0'],r['batch']['minima'][-1]['minimum']['upperBound']+.5)
        self.assertTrue(primary['allMinimumLowerBoundsSupported'])
        self.assertEqual(dict.fromkeys(checker.ACTIONS,0.),primary['minimumLowerViolationsBb'])
        self.assertAlmostEqual(-1.,r['batch']['minima'][0]['minimum']['witnessUtility'],places=7)

    def test_typed_failure_retains_evidence_without_partial_results_or_support(self):
        r=copy.deepcopy(self.report)
        r.update(batch=None,rejection=dict(kind='LP',reason='WORK_LIMIT',detail='protocol control only'),comparisons=[],
                 completeMinima=False,referenceSupportedMinima=False,rejectionReasons=['BATCH:LP:WORK_LIMIT'])
        for p in r['policies']:p.update(minimumLowerViolationsBb={},allMinimumLowerBoundsSupported=False)
        result=checker.check_values(self.request,r,self.context)
        self.assertFalse(result['completeMinima'] or result['referenceSupportedMinima'])
        self.assertEqual(0,result['endpointCertificatesChecked'])
        r['referenceSupportedMinima']=True
        with self.assertRaises(ValueError):checker.check_values(self.request,r,self.context)


if __name__ == '__main__': unittest.main()
