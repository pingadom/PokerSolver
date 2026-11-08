import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('root_cache_test',Path(__file__).with_name('check-sevens-preflop-drill.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class SevensCacheCheckTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=check.read(check.MANIFEST);cls.fixture=check.read(check.FIXTURE);cls.export=check.REVIEW.read_bytes().decode();cls.verified=check.source_context();cls.receipt=check.check_values(cls.manifest,cls.fixture,cls.export,cls.verified);cls.receipt.update(checkerSourceSha256=check.sha(Path(check.__file__)),sourceStoredSha256={p.name:check.sha(p) for p in [check.MANIFEST,check.FIXTURE,check.REVIEW]},immutableHelperSha256=check.IMMUTABLE_HELPERS)
    def reject(self,change):
        value=copy.deepcopy(self.fixture);change(value)
        with self.assertRaises(ValueError):check.check_values(self.manifest,value,self.export,self.verified)
    def test_original_source_and_public_three_grade_receipt(self):
        self.assertEqual(self.receipt,check.read(check.DATA/'supplied-sevens-preflop-drill-independent-check.json'))
        self.assertEqual(3,self.receipt['cachedGradesChecked']);self.assertTrue(self.receipt['emptyPreflopBoard'])
        self.assertEqual(0,self.receipt['freshLpSolves']);self.assertEqual(0,self.receipt['freshCfrRuns'])
    def test_future_board_hidden_cards_and_context_cannot_leak(self):
        self.reject(lambda f:f['questions'][0]['board'].append('6d'))
        self.reject(lambda f:f['questions'][0].__setitem__('opponentCards','As Ac'))
        self.reject(lambda f:f['questions'][0]['context'].__setitem__('toCallBb',9))
    def test_ev_scope_shift_losses_and_raise_ambiguity_are_independent(self):
        self.reject(lambda f:f['metadata'].__setitem__('evScope','TERMINAL_PROFIT'))
        self.reject(lambda f:f['grades'][1]['move'].__setitem__('lowerEvBb',99))
        self.reject(lambda f:f['grades'][2]['move'].__setitem__('classification','WITHIN_LIMIT'))
        self.reject(lambda f:f['questions'][0]['moves'][0].__setitem__('lowerLossBb',0))
    def test_no_public_admission_missing_action_or_alternative_source(self):
        self.reject(lambda f:f['metadata'].__setitem__('trainerAdmission',True))
        self.reject(lambda f:f['grades'].pop())
        self.reject(lambda f:f['questions'][0].__setitem__('sourceCaseIndex',1))
        self.reject(lambda f:f['metadata'].__setitem__('studyHash','0'*64))
    def test_manifest_file_identity_and_work_reservation_are_checked(self):
        for field,value in [('reportStoredSha256','0'*64),('studyHash','0'*64),('requestFile','../escape.json')]:
            m=copy.deepcopy(self.manifest);m[field]=value
            with self.assertRaises(ValueError):check.check_values(m,self.fixture,self.export,self.verified)
        m=copy.deepcopy(self.manifest);m['startupBudget']['maximumEnvelopeLpSolves']=99
        with self.assertRaises(ValueError):check.check_values(m,self.fixture,self.export,self.verified)
    def test_reviews_retain_order_repeats_and_unresolved_counts(self):
        choices=[a['attempt'] for a in self.fixture['review']['attempts']]
        self.assertEqual(self.fixture['review'],check.review(choices,self.fixture['grades']))
        repeated=check.review([dict(sourceCaseIndex=0,action='raise:9.0')]*128,self.fixture['grades'])
        self.assertEqual(128,repeated['counts']['strategyDependent'])
        self.assertEqual(list(range(128)),[a['ordinal'] for a in repeated['attempts']])
        self.reject(lambda f:f['review']['counts'].__setitem__('strategyDependent',0))
    def test_invalid_choices_and_review_overflow_cannot_get_grades(self):
        for c in [dict(sourceCaseIndex=1,action='call'),dict(sourceCaseIndex='0',action='call'),dict(sourceCaseIndex=False,action='call'),dict(sourceCaseIndex=0,action='check'),dict(sourceCaseIndex=0,action='fold',ev=99)]:
            with self.assertRaises(ValueError):check.review([c],self.fixture['grades'])
        with self.assertRaises(ValueError):check.review([dict(sourceCaseIndex=0,action='fold')]*129,self.fixture['grades'])
    def test_canonical_export_checks_real_bytes_without_newline_normalization(self):
        for text in [self.export.replace('\n','\r\n'),self.export.rstrip(),self.export+'\n']:
            with self.assertRaises(ValueError):check.check_values(self.manifest,self.fixture,text,self.verified)
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'review.json';p.write_bytes(self.export.replace('\n','\r\n').encode())
            with patch.object(check,'REVIEW',p),self.assertRaises(ValueError):check.check()


    def test_boolean_numeric_and_legacy_source_substitutions_are_rejected(self):
        self.reject(lambda f:f['metadata'].__setitem__('availableQuestions',True))
        self.reject(lambda f:f['grades'][1]['move'].__setitem__('lowerLossBb',False))
        self.reject(lambda f:f['questions'][0].__setitem__('sourceCaseIndex',False))
        for field in ['parentStoredSha256','reportStoredSha256','requestSha256']:
            manifest=copy.deepcopy(self.manifest);manifest[field]='0'*64
            with self.assertRaises(ValueError):check.check_values(manifest,self.fixture,self.export,self.verified)

    def test_one_commitment_shift_and_complete_source_identity_are_preserved(self):
        self.reject(lambda f:f['questions'][0]['moves'][2].__setitem__('lowerEvBb',f['questions'][0]['moves'][2]['lowerEvBb']+1))
        self.reject(lambda f:f['metadata'].__setitem__('studyHash','baa6420476438abfca0c508d19ca313d8f9dc78f643293df9b2866418f15bf6d'))
        self.assertEqual(1,self.receipt['sourceCommitmentShiftBb'])
        self.assertFalse(self.receipt['trainerAdmission'])

    def test_floating_question_indices_cannot_pass_as_integer_protocol_fields(self):
        self.reject(lambda f:f['questions'][0].__setitem__('sourceCaseIndex',0.0))
        self.reject(lambda f:f['grades'][0].__setitem__('sourceCaseIndex',0.0))
        self.reject(lambda f:f['review']['attempts'][0]['attempt'].__setitem__('sourceCaseIndex',0.0))
        self.reject(lambda f:f['review']['attempts'][0].__setitem__('ordinal',0.0))

    def test_floating_counts_and_work_reservations_cannot_pass_as_integers(self):
        for key in ['tableSeats','activePlayers','availableQuestions','cachedLegalGrades','maximumAttempts']:
            self.reject(lambda f,k=key:f['metadata'].__setitem__(k,float(f['metadata'][k])))
        for key in self.fixture['review']['counts']:
            self.reject(lambda f,k=key:f['review']['counts'].__setitem__(k,float(f['review']['counts'][k])))
        for key in self.manifest['startupBudget']:
            changed=copy.deepcopy(self.manifest);changed['startupBudget'][key]=float(changed['startupBudget'][key])
            with self.assertRaises(ValueError):check.check_values(changed,self.fixture,self.export,self.verified)
            self.reject(lambda f,k=key:f['metadata']['startupBudget'].__setitem__(k,float(f['metadata']['startupBudget'][k])))

if __name__=='__main__':unittest.main()
