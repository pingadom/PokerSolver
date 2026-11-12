import copy
import hashlib
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('bank_raise_checker',Path(__file__).with_name('check-bank-raise-sequence.py'))
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class BankRaiseSequenceCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context=checker.context();cls.request=checker.read(checker.REQUEST);cls.report=checker.read(checker.REPORT)
    def corrupt(self,change):
        q,r=copy.deepcopy(self.request),copy.deepcopy(self.report);change(q,r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):
            checker.check_values(q,r,self.context)
    def endpoint(self,change,index=0):self.corrupt(lambda _,r:change(r['batch']['minima'][index]))
    def test_both_physical_endpoints_and_six_policy_lower_checks(self):
        p=checker.check_values(self.request,self.report,self.context)
        self.assertTrue(p['completeMinima'] and p['referenceSupportedMinima'])
        self.assertEqual((2,2,6),(p['endpointCertificatesChecked'],p['distinctBatchParentCertificatesChecked'],p['policyLowerBoundChecks']))
        self.assertEqual((False,False,0,0),(p['trainerAdmission'],p['gradingAuthority'],p['freshLpSolves'],p['freshCfrRuns']))
        self.assertFalse(p['fullActionUpperEndpointClaim'] or p['coherentMinimumPolicyClaim'])
    def test_original_helper_and_bank_bytes_remain_pinned(self):
        for name,pin in checker.IMMUTABLE_HELPERS.items():self.assertEqual(pin,hashlib.sha256((checker.ROOT/'scripts'/name).read_bytes()).hexdigest())
        self.assertEqual(checker.BANK_SHA,hashlib.sha256(checker.bank_checker.REPORT.read_bytes()).hexdigest())
    def test_rejects_changed_request_pins_hands_and_action(self):
        for k in ['schemaVersion','bankStoredSha256','bankStudyHash','action']:self.corrupt(lambda q,_,k=k:q.__setitem__(k,'wrong'))
        self.corrupt(lambda q,_:q['heroCards'].reverse())
    def test_rejects_admission_scope_and_forged_original_bank(self):
        self.corrupt(lambda _,r:r.__setitem__('trainerAdmission',True))
        self.corrupt(lambda _,r:r.__setitem__('minimumScope','FULL_INTERVALS'))
        self.corrupt(lambda _,r:r['sourceBank']['assessments'].pop())
        self.corrupt(lambda _,r:r['parent']['solve'].__setitem__('snapshotHash','wrong'))
    def test_rejects_missing_reordered_or_borrowed_hand_minimum(self):
        self.corrupt(lambda _,r:r['batch']['minima'].pop())
        self.corrupt(lambda _,r:r['batch']['minima'].reverse())
        self.corrupt(lambda _,r:r['batch']['minima'].__setitem__(1,copy.deepcopy(r['batch']['minima'][0])))
    def test_rejects_original_reach_posterior_and_state_identity_forgery(self):
        self.endpoint(lambda a:a.__setitem__('originalQuestionReach',1.))
        self.endpoint(lambda a:a['support'][0].__setitem__('posteriorProbability',.5))
        self.endpoint(lambda a:a['support'][0].__setitem__('stateHash','wrong'))
    def test_rejects_changed_face_slack_or_parent_certificate(self):
        self.endpoint(lambda a:a.__setitem__('securitySlack',.01))
        self.endpoint(lambda a:a.__setitem__('securityFaceHash','wrong'))
        self.endpoint(lambda a:a['firstParentWitness']['point'].__setitem__(0,42.))
    def test_rejects_changed_physical_payoff_and_forced_action_domain(self):
        self.endpoint(lambda a:a['conditionalPayoff'].__setitem__('constant',42.))
        self.endpoint(lambda a:a['heroDomain'].__setitem__('forcedSequence',0))
        self.endpoint(lambda a:a['problem']['cost'].__setitem__(-1,42.))
    def test_rejects_changed_endpoint_certificate_flow_and_best_response(self):
        self.endpoint(lambda a:a['witness']['point'].__setitem__(-1,-1.))
        self.endpoint(lambda a:a['witness']['dual'].__setitem__(0,-1.))
        self.endpoint(lambda a:a['continuationFlow']['realization'].__setitem__(0,.5))
        self.endpoint(lambda a:a['witness'].__setitem__('conditionalHeroBestResponse',42.))
    def test_rejects_changed_minimum_brackets_and_comparison(self):
        self.endpoint(lambda a:a['minimum'].__setitem__('lowerBound',.5))
        self.endpoint(lambda a:a['minimum'].__setitem__('upperBound',50.))
        self.corrupt(lambda _,r:r['comparisons'][0].__setitem__('lowerBoundImprovementBb',42.))
    def test_rejects_missing_forged_or_boolean_reference_assessments(self):
        self.corrupt(lambda _,r:r['policySupports'].pop())
        self.corrupt(lambda _,r:r['policySupports'][0].__setitem__('sourceQuestionIndex',True))
        self.corrupt(lambda _,r:r['policySupports'][0].__setitem__('minimumLowerViolationBb',.1))
        self.corrupt(lambda _,r:r['policySupports'][0].__setitem__('supported',False))
    def test_rejects_increased_reset_forged_or_boolean_cumulative_work(self):
        for k,v in [('compilerLimit',8000001),('endpointPivotLimit',10001),('endpointArithmeticLimit',4000000001),('parentSolveCalls',True),('compilerUnits',0),('preflightCompilerUnits',0)]:
            self.corrupt(lambda _,r,k=k,v=v:r['batch']['work'].__setitem__(k,v))
        self.endpoint(lambda a:a['work'].__setitem__('compilerLimit',8000000),1)
        self.endpoint(lambda a:a['work'].__setitem__('endpointPivots',False))
    def test_minimum_upper_bracket_is_not_an_upper_bound_for_all_policies(self):
        self.assertTrue(any(p['actionUtilitiesBb']['raise:9.0']>a['minimum']['upperBound']+.1
                            for a,i in zip(self.report['batch']['minima'],[0,2],strict=True) for p in self.report['sourceBank']['assessments'][i]['policies']))
        self.assertTrue(self.report['referenceSupportedMinima'])
    def test_retains_typed_rejection_without_partial_success(self):
        r=copy.deepcopy(self.report);r.update(batch=None,rejection=dict(kind='LP',reason='PIVOT_LIMIT',detail='declared limit'),comparisons=[],policySupports=[],
                                             completeMinima=False,referenceSupportedMinima=False,rejectionReasons=['BATCH:LP:PIVOT_LIMIT'])
        p=checker.check_values(self.request,r,self.context)
        self.assertEqual(0,p['endpointCertificatesChecked']);self.assertFalse(p['completeMinima'] or p['referenceSupportedMinima'])

if __name__=='__main__':unittest.main()
