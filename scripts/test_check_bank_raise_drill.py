import copy
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('bank_raise_cache_checker',Path(__file__).with_name('check-bank-raise-drill.py'))
checker=importlib.util.module_from_spec(spec);spec.loader.exec_module(checker)

class BankRaiseDrillCheckerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.context=checker.context();cls.manifest=checker.read(checker.MANIFEST);cls.fixture=checker.read(checker.FIXTURE)
    def corrupt(self,edit):
        m,f=copy.deepcopy(self.manifest),copy.deepcopy(self.fixture);edit(m,f)
        with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):checker.check_values(m,f,self.context)
    def test_two_sparse_hands_and_six_grades_derive_from_qualified_source(self):
        p=checker.check_values(self.manifest,self.fixture,self.context)
        self.assertEqual((2,6,[0,2],1,72),(p['publicQuestions'],p['cachedLegalGrades'],p['originalSourceCaseIndices'],p['currentDecisionEvShiftBb'],p['sourceUtilityLossInequalities']))
        self.assertEqual((False,False,0,0),(p['trainerAdmission'],p['gradingAuthority'],p['freshLpSolves'],p['freshCfrRuns']))
    def test_manifest_source_hashes_and_paths_cannot_change(self):
        for name in [*checker.FILES,'studyHash','requestSha256','reportStoredSha256','minimumStoredSha256','bankStoredSha256']:
            self.corrupt(lambda m,f,n=name:m.__setitem__(n,'../other'))
    def test_manifest_budget_cannot_shrink_grow_or_coerce(self):
        for name in checker.BUDGET:
            for value in [0,999999,'1',True]:self.corrupt(lambda m,f,n=name,v=value:m['startupBudget'].__setitem__(n,v))
    def test_manifest_unknown_missing_or_wrong_schema_rejects(self):
        self.corrupt(lambda m,f:m.__setitem__('extra',1));self.corrupt(lambda m,f:m.pop('minimumFile'));self.corrupt(lambda m,f:m.__setitem__('schemaVersion','wrong'))
    def test_source_ids_must_remain_zero_and_two(self):
        for i in [0,1]:self.corrupt(lambda m,f,i=i:f['snapshot']['questions'][i].__setitem__('sourceCaseIndex',1))
        self.corrupt(lambda m,f:f['snapshot']['questions'].reverse())
    def test_questions_must_be_complete(self):
        self.corrupt(lambda m,f:f['snapshot']['questions'].pop());self.corrupt(lambda m,f:f['snapshot']['questions'].append(f['snapshot']['questions'][0]))
    def test_future_board_villain_cards_and_hidden_solver_details_reject(self):
        for name,value in [('board',['6d','Qc','Td']),('opponentCards','As Ah'),('sourceMinimum',{}),('strategy',{})]:
            self.corrupt(lambda m,f,n=name,v=value:f['snapshot']['questions'][0].__setitem__(n,v))
    def test_public_context_and_hero_cards_cannot_be_borrowed(self):
        self.corrupt(lambda m,f:f['snapshot']['questions'][0].__setitem__('heroCards','Ac Jc'))
        self.corrupt(lambda m,f:f['snapshot']['questions'][1]['context'].__setitem__('toCallBb',0))
    def test_ev_shift_is_exactly_once(self):
        for delta in [-1,1]:self.corrupt(lambda m,f,d=delta:f['snapshot']['questions'][0]['moves'][2].__setitem__('lowerEvBb',f['snapshot']['questions'][0]['moves'][2]['lowerEvBb']+d))
    def test_losses_classification_and_full_action_ceilings_cannot_change(self):
        for name,value in [('upperLossBb',0),('lowerLossBb',1),('classification','WITHIN_LIMIT'),('upperEvBb',0)]:
            self.corrupt(lambda m,f,n=name,v=value:f['snapshot']['questions'][0]['moves'][2].__setitem__(n,v))
    def test_missing_reordered_or_forged_cached_grades_reject(self):
        self.corrupt(lambda m,f:f['grades'].pop());self.corrupt(lambda m,f:f['grades'].reverse())
        self.corrupt(lambda m,f:f['grades'][3].__setitem__('sourceCaseIndex',0));self.corrupt(lambda m,f:f['grades'][2]['move'].__setitem__('upperLossBb',0))
    def test_admission_identity_or_ev_scope_cannot_change(self):
        for name,value in [('trainerAdmission',True),('studyHash','0'*64),('evScope','TERMINAL'),('schemaVersion','wrong')]:
            self.corrupt(lambda m,f,n=name,v=value:f['snapshot'].__setitem__(n,v))
    def test_extra_fields_nonfinite_values_and_coercions_reject(self):
        self.corrupt(lambda m,f:f.__setitem__('counts',{}))
        for value in [float('nan'),float('inf'),'0',True]:self.corrupt(lambda m,f,v=value:f['grades'][0]['move'].__setitem__('lowerLossBb',v))

if __name__=='__main__':unittest.main()
