"""Independent public projection, cross-hand substitution and mixed-review corruption controls."""
import copy
import gzip
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('bank_drill_checker',Path(__file__).with_name('check-root-hand-bank-drill.py'))
check=importlib.util.module_from_spec(spec);spec.loader.exec_module(check)


class RootHandBankDrillChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=check.read(check.MANIFEST);cls.fixture=check.read(check.FIXTURE)
        cls.export=check.REVIEW.read_bytes().decode('utf-8');cls.verified=check.source_context()

    def reject(self,change):
        fixture=copy.deepcopy(self.fixture);change(fixture)
        with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):
            check.check_values(self.manifest,fixture,self.export,self.verified)

    def test_stored_receipt_checks_all_three_hands_nine_grades_and_original_certificates(self):
        receipt=check.check();self.assertEqual(receipt,check.read(check.DATA/'supplied-root-hand-bank-drill-independent-check.json'))
        self.assertEqual(3,receipt['publicQuestionsChecked']);self.assertEqual(9,receipt['cachedGradesChecked'])
        self.assertEqual(15,receipt['sourceCheck']['originalEnvelopeCertificatesChecked'])
        self.assertEqual(11,receipt['canonicalMixedReviewAttemptsChecked'])
        self.assertFalse(receipt['trainerAdmission']);self.assertFalse(receipt['gradingAuthority'])
        self.assertEqual(0,receipt['freshLpSolves']);self.assertEqual(0,receipt['freshCfrRuns'])

    def test_manifest_requires_exact_source_bytes_pin_and_full_shared_startup_budget(self):
        for field,value in [('studyHash','0'*64),('requestSha256','0'*64),('requestFile','../escape'),
                            ('reportStoredSha256','0'*64),('parentStoredSha256','0'*64)]:
            manifest=copy.deepcopy(self.manifest);manifest[field]=value
            with self.assertRaises(ValueError):check.check_values(manifest,self.fixture,self.export,self.verified)
        for field in check.BUDGET:
            manifest=copy.deepcopy(self.manifest);manifest['startupBudget'][field]=0
            with self.assertRaises(ValueError):check.check_values(manifest,self.fixture,self.export,self.verified)

    def test_question_inventory_cannot_be_dropped_duplicated_or_relabelled(self):
        self.reject(lambda f:f['questions'].pop())
        self.reject(lambda f:f['questions'].reverse())
        self.reject(lambda f:f['questions'][2].__setitem__('heroCards','7d 7h'))
        self.reject(lambda f:f['questions'][2].__setitem__('sourceCaseIndex',0))

    def test_grades_cannot_borrow_another_hands_value_or_identity(self):
        self.reject(lambda f:f['grades'].pop())
        self.reject(lambda f:f['grades'][7].__setitem__('move',f['grades'][1]['move']))
        self.reject(lambda f:f['grades'][7].__setitem__('sourceCaseIndex',0))
        self.reject(lambda f:f['grades'].reverse())

    def test_each_hands_current_decision_ev_is_shifted_exactly_once(self):
        for i in range(3):
            self.reject(lambda f:f['questions'][i]['moves'][1].__setitem__('lowerEvBb',f['questions'][i]['moves'][1]['lowerEvBb']+1))
            self.reject(lambda f:f['grades'][3*i]['move'].__setitem__('lowerEvBb',-1.00000001))
        self.reject(lambda f:f['metadata'].__setitem__('evScope','ABSOLUTE_TERMINAL_PROFIT'))

    def test_original_loss_bounds_and_unresolved_raises_are_preserved(self):
        for i in range(3):
            self.reject(lambda f:f['grades'][3*i+2]['move'].__setitem__('classification','WITHIN_LIMIT'))
            self.reject(lambda f:f['questions'][i]['moves'][2].__setitem__('upperLossBb',.001))

    def test_public_projection_does_not_expose_opponent_or_future_cards(self):
        for i in range(3):
            self.reject(lambda f:f['questions'][i]['board'].append('Qc'))
            self.reject(lambda f:f['questions'][i].__setitem__('opponentCards','As Ah'))
            self.reject(lambda f:f['questions'][i]['context'].__setitem__('candidate',{}))

    def test_metadata_preserves_inventory_scope_and_review_limits(self):
        for field,value in [('availableQuestions',1),('cachedLegalGrades',3),('activePlayers',6),
                            ('tableSeats',2),('maximumAttempts',256),('trainerAdmission',True)]:
            self.reject(lambda f:f['metadata'].__setitem__(field,value))

    def test_mixed_review_counts_distinct_hands_and_keeps_repeated_order(self):
        choices=[a['attempt'] for a in self.fixture['review']['attempts']]
        self.assertEqual(self.fixture['review'],check.review(choices,self.fixture['grades']))
        self.assertEqual([2,0],[a['sourceCaseIndex'] for a in choices[-2:]])
        self.reject(lambda f:f['review']['counts'].__setitem__('distinctQuestions',1))
        self.reject(lambda f:f['review']['attempts'][9]['grade'].__setitem__('sourceCaseIndex',0))
        self.reject(lambda f:f['review']['attempts'].reverse())

    def test_canonical_utf8_lf_export_cannot_change_layout_values_or_ordinals(self):
        for export in [self.export.replace('\n','\r\n'),self.export.rstrip(),self.export.replace('"ordinal" : 0','"ordinal" : 9')]:
            self.assertNotEqual(self.export,export)
            with self.assertRaises(ValueError):check.check_values(self.manifest,self.fixture,export,self.verified)

    def test_review_accepts_only_explicit_owned_hand_and_action_choices(self):
        for choice in [dict(sourceCaseIndex=3,action='call'),dict(sourceCaseIndex=-1,action='call'),
                       dict(sourceCaseIndex=True,action='fold'),dict(sourceCaseIndex='0',action='call'),
                       dict(sourceCaseIndex=0,action='check'),dict(sourceCaseIndex=0,action='fold',ev=99)]:
            with self.assertRaises(ValueError):check.review([choice],self.fixture['grades'])
        with self.assertRaises(ValueError):check.review([dict(sourceCaseIndex=0,action='fold')]*129,self.fixture['grades'])

    def test_empty_and_maximum_mixed_reviews_have_honest_counts(self):
        self.assertEqual(dict(distinctQuestions=0,withinLimit=0,outsideLimit=0,strategyDependent=0),check.review([],self.fixture['grades'])['counts'])
        actions=['fold','call','raise:9.0']
        choices=[dict(sourceCaseIndex=i%3,action=actions[i%3]) for i in range(128)]
        self.assertEqual(dict(distinctQuestions=3,withinLimit=43,outsideLimit=43,strategyDependent=42),check.review(choices,self.fixture['grades'])['counts'])

    def test_foreign_source_pins_do_not_authorize_bank_grades_or_reviews(self):
        old='5f52b053c7610adbfb4abd2cfb210674b4a325d8b870cd350edc22d192708da6'
        self.reject(lambda f:f['metadata'].__setitem__('studyHash',old))
        self.reject(lambda f:f['grades'][0].__setitem__('studyHash',old))
        self.reject(lambda f:f['review'].__setitem__('studyHash',old))

    def test_protocol_indices_counters_and_budget_fields_are_integral(self):
        self.reject(lambda f:f['questions'][0].__setitem__('sourceCaseIndex',False))
        self.reject(lambda f:f['review']['counts'].__setitem__('distinctQuestions',3.0))
        self.reject(lambda f:f['metadata']['startupBudget'].__setitem__('physicalGames',True))
        self.reject(lambda f:f['grades'][1]['move'].__setitem__('lowerEvBb',float('nan')))

    def test_duplicate_aliases_nonfinite_json_and_byte_caps_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bad.json'
            for raw in ['{"a":1,"a":2}','{"a":1,"\\u0061":2}','{"a":NaN}','{"a":1e999}']:
                p.write_text(raw)
                with self.assertRaises(ValueError):check.read(p)
            p.write_bytes(b' '*33)
            with self.assertRaises(ValueError):check.read(p,32)
            p=Path(temp)/'bad.gz';p.write_bytes(gzip.compress(b' '*100))
            with self.assertRaises(ValueError):check.read(p,32)


if __name__=='__main__':unittest.main()
