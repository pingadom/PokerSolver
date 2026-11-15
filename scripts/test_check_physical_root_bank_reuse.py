"""Corruption controls for the fixed physical hand-bank parent handoff."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('checked_physical_parent_reuse',Path(__file__).with_name('check-physical-root-bank-reuse.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)
class PhysicalReuseChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.request=check.read(check.REQUEST);cls.report=check.read(check.REPORT)
    def reject(self,change):
        q=copy.deepcopy(self.request);r=copy.deepcopy(self.report);change(q,r)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):check.check_values(q,r)
    def test_complete_physical_original_certificates_and_work(self):
        r=check.check_values(self.request,self.report)
        self.assertEqual((2,0,15,3,9),(r['actualParentLpSolves'],r['additionalBatchParentLpSolves'],r['endpointLpSolves'],r['originalMenus'],r['policyChecks']))
        self.assertEqual((3366363,468,1440054300),(r['cumulativeCompilerUnits'],r['endpointPivots'],r['endpointArithmeticWork']))
        self.assertFalse(r['trainerAdmission']);self.assertEqual(0,r['freshLpSolves']+r['freshCfrRuns'])
    def test_research_scope_or_admission_forgery(self):
        self.reject(lambda q,r:r.__setitem__('trainerAdmission',True))
        self.reject(lambda q,r:r.__setitem__('originalMenusReproduced',False))
        self.reject(lambda q,r:r['batch'].__setitem__('algorithm','ROOT_SECURITY_ENVELOPE_SHARED_OWNED_PARENT_BATCH/v1'))
    def test_request_wrong_pin_or_unknown_field(self):
        self.reject(lambda q,r:q.__setitem__('bankStoredSha256','f'*64))
        self.reject(lambda q,r:q.__setitem__('unknown',1))
    def test_threshold_or_ordered_hand_changes(self):
        self.reject(lambda q,r:q.__setitem__('maximumLossBb',.02))
        self.reject(lambda q,r:q['heroCards'].reverse())
    def test_source_physics_substitution(self):
        self.reject(lambda q,r:r['historicalSource']['request']['parent']['flop'].__setitem__(0,'As'))
    def test_physical_parent_policy_or_certificate_changes(self):
        self.reject(lambda q,r:r['physicalParent']['solve'].__setitem__('snapshotHash','e'*64))
        self.reject(lambda q,r:r['batch']['firstParentWitness']['point'].__setitem__(0,100.))
    def test_parent_references_cannot_be_omitted(self):
        self.reject(lambda q,r:r['physicalParent']['references'].pop())
    def test_every_source_id_and_menu_is_required(self):
        self.reject(lambda q,r:r['batch']['envelopes'].pop())
        self.reject(lambda q,r:r['assessments'][0].__setitem__('sourceQuestionIndex',2))
    def test_cumulative_limits_cannot_reset_per_hand(self):
        self.reject(lambda q,r:r['batch']['envelopes'][1]['work'].__setitem__('compilerLimit',8000000))
    def test_work_cannot_claim_unexecuted_parents(self):
        for k in ['parentSolveCalls','parentLpSolves','parentCompilerUnits','parentLpArithmeticWork']:
            self.reject(lambda q,r,key=k:r['batch']['work'].__setitem__(key,1))
    def test_work_limits_and_counters_are_exact(self):
        self.reject(lambda q,r:r['maximumWork'].__setitem__('endpointArithmetic',5000000000))
        self.reject(lambda q,r:r['batch']['work'].__setitem__('endpointLpSolves',14))
        self.reject(lambda q,r:r['batch']['work'].__setitem__('compilerUnits',1))
    def test_changed_bound_or_original_endpoint_certificate(self):
        self.reject(lambda q,r:r['batch']['envelopes'][0]['actions'][0].__setitem__('upperUtility',99.))
        self.reject(lambda q,r:r['batch']['envelopes'][0]['guaranteeWitness']['dual'].__setitem__(0,99.))
    def test_forged_comparison_hashes_cannot_vouch_for_themselves(self):
        def change(q,r):
            r['comparisons'][0]['originalEnvelopeHash']='0'*64;r['comparisons'][0]['adjustedEnvelopeHash']='0'*64
        self.reject(change)
    def test_wrong_action_grade_or_reference_support(self):
        self.reject(lambda q,r:r['assessments'][0]['moves'][0].__setitem__('classification','WITHIN_LIMIT'))
        self.reject(lambda q,r:r['assessments'][2]['policies'][0].__setitem__('insideDeclaredFace',False))
    def test_unknown_report_fields_and_number_coercions(self):
        self.reject(lambda q,r:r.__setitem__('unknown',0))
        self.reject(lambda q,r:r['batch']['work'].__setitem__('parentSolveCalls',False))
        self.reject(lambda q,r:q.__setitem__('maximumLossBb','0.01'))
    def test_nonfinite_and_signed_zero_are_not_original_binary64(self):
        self.reject(lambda q,r:r['batch']['envelopes'][0]['actions'][0].__setitem__('lowerUtility',float('nan')))
        def change(q,r):
            point=r['batch']['envelopes'][0]['guaranteeWitness']['point']
            i=next(i for i,x in enumerate(point) if type(x) is float and x==0. and check.struct.pack('>d',x)==check.struct.pack('>d',0.))
            point[i]=-0.
        self.reject(change)
    def test_lexical_compaction_preserves_numbers_and_string_contents(self):
        text=' { "x" : -0.0, "label": "a b\\\"c", "y": 1.0E-9 } '
        self.assertEqual(b'{"x":-0.0,"label":"a b\\\"c","y":1.0E-9}',check.compact(text))
    def test_strict_reader_rejects_duplicate_or_nonfinite_input(self):
        with tempfile.TemporaryDirectory() as directory:
            p=Path(directory)/'request.json';p.write_text('{"a":1,"a":2}')
            with self.assertRaises(ValueError):check.read(p)
            p.write_text('{"a":NaN}')
            with self.assertRaises(ValueError):check.read(p)

if __name__=='__main__':unittest.main()
