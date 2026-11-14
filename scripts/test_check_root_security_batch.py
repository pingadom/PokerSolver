"""Corruption controls for independently verified shared-parent root envelope batches."""
import copy
import importlib.util
from pathlib import Path
import unittest

PATH=Path(__file__).with_name('check-root-security-batch.py')
spec=importlib.util.spec_from_file_location('checked_root_security_batch',PATH)
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)

class RootSecurityBatchChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.controls,_=check.old.read(check.FIXTURE)
    def reject(self,change):
        data=copy.deepcopy(self.controls);change(data)
        with self.assertRaises((ValueError,KeyError,IndexError,TypeError)):check.check_values(data)
    def test_complete_literal_games_certificates_and_parent_savings(self):
        result=check.check()
        self.assertEqual((3,8,6,32,10),(result['batches'],result['rootMenus'],result['parentCertificates'],result['endpointCertificates'],result['avoidedParentLpSolves']))
        self.assertFalse(result['trainerAdmission']);self.assertEqual(0,result['physicalPokerGames'])
    def test_missing_or_reordered_control_rejects(self):
        self.reject(lambda x:x.pop());self.reject(lambda x:x.reverse())
    def test_missing_or_reordered_menu_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'].pop())
        self.reject(lambda x:x[0]['audit']['envelopes'].reverse())
    def test_parent_substitution_rejects(self):
        self.reject(lambda x:x[0]['audit'].__setitem__('parent',x[1]['audit']['parent']))
    def test_parent_original_certificate_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['firstParentWitness']['dual'].__setitem__(0,17.))
    def test_parent_quality_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['parent']['behavioralQuality'].__setitem__('nashConvBb',.2))
    def test_changed_physical_literal_utility_rejects(self):
        def change(x):x[0]['game']['initialState']['children'][0]['children'][0]['children'][0]['utilities'][0]+=1
        self.reject(change)
    def test_original_chance_mass_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0].__setitem__('questionChanceMass',.5))
    def test_cross_hand_question_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0].__setitem__('question',x[0]['questions'][1]))
    def test_terminal_ceiling_or_tightened_outer_bound_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['actions'][1].__setitem__('upperUtility',-.2))
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['actions'][1].__setitem__('literalUtilityCeiling',17.))
    def test_fixed_policy_substitution_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['fixedHeroChoices'].__setitem__('0:type0','risk'))
    def test_opponent_flow_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['questionUpperWitness']['opponentFlow']['realization'].__setitem__(0,.5))
    def test_witness_policy_hash_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['questionUpperWitness'].__setitem__('behavioralPolicyHash','f'*64))
    def test_endpoint_original_certificate_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['questionUpperWitness']['point'].__setitem__(0,2.))
    def test_per_question_compiler_limit_cannot_reset(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][1]['work'].__setitem__('compilerLimit',8_000_000))
    def test_aggregate_work_and_parent_call_forgery_rejects(self):
        for key,value in [('parentSolveCalls',2),('parentLpSolves',4),('endpointLpSolves',1),('endpointPivots',0),('endpointArithmeticWork',0),('compilerUnits',1),('parentCompilerUnits',True)]:
            self.reject(lambda x,k=key,v=value:x[0]['audit']['work'].__setitem__(k,v))
    def test_research_scope_and_admission_forgery_rejects(self):
        self.reject(lambda x:x[0]['audit'].__setitem__('trainerAdmission',True))
        self.reject(lambda x:x[0]['audit'].__setitem__('scope','ATTAINED_FULL_ACTION_MAXIMUM'))
        self.reject(lambda x:x[0]['audit']['envelopes'][0].__setitem__('trainerAdmission',True))
    def test_source_root_identity_and_support_hash_reject(self):
        self.reject(lambda x:x[0]['audit']['envelopes'][0]['support'][0].__setitem__('stateHash','a'*64))
        self.reject(lambda x:x[0]['audit'].__setitem__('snapshotHash','a'*64))

if __name__=='__main__':unittest.main()
