import copy
import importlib.util
from pathlib import Path
import sys
import unittest
P=Path(__file__).with_name('check-physical-root-bank-drill.py')
spec=importlib.util.spec_from_file_location('physical_cache_check_tests',P);check=importlib.util.module_from_spec(spec);sys.modules[spec.name]=check;spec.loader.exec_module(check)
class PhysicalRootCacheChecks(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.manifest=check.read(check.MANIFEST);cls.report=check.read(check.physical.REPORT);cls.fixture=check.read(check.public.FIXTURE);cls.export=check.public.REVIEW.read_text(encoding='utf-8')
 def run_values(self,m=None,r=None,f=None,e=None):return check.check_values(self.manifest if m is None else m,self.report if r is None else r,self.fixture if f is None else f,self.export if e is None else e)
 def rejects(self,**kw):
  with self.assertRaises(ValueError):self.run_values(**kw)
 def test_complete_dual_proof_and_existing_public_fixture(self):
  r=check.check();self.assertEqual(r,check.read(check.DATA/'supplied-physical-root-bank-drill-independent-check.json'));self.assertEqual(2,r['startupBudget']['maximumParentLpSolves']);self.assertEqual(9,r['publicCheck']['cachedGradesChecked']);self.assertFalse(r['trainerAdmission']);self.assertEqual(0,r['freshLpSolves']);self.assertEqual(0,r['freshCfrRuns'])
 def test_public_reservation_retains_historical_eight_parents(self):
  self.assertEqual(8,self.fixture['metadata']['startupBudget']['maximumParentLpSolves']);self.assertEqual(2,self.manifest['startupBudget']['maximumParentLpSolves']);self.assertTrue(self.run_values()['publicMetadataReservationIsHistorical'])
 def test_both_exact_source_identities_are_required(self):
  for field in ['studyHash','physicalStudyHash']:
   m=copy.deepcopy(self.manifest);m[field]='0'*64;self.rejects(m=m)
 def test_each_startup_budget_field_is_required(self):
  for field in self.manifest['startupBudget']:
   m=copy.deepcopy(self.manifest);m['startupBudget'][field]=0;self.rejects(m=m)
 def test_non_integer_or_boolean_reservation_is_not_coerced(self):
  for value in [True,2.0,'2']:
   m=copy.deepcopy(self.manifest);m['startupBudget']['maximumParentLpSolves']=value;self.rejects(m=m)
 def test_wrong_bytes_or_paths_cannot_refresh_authority(self):
  for field,value in [('reportStoredSha256','0'*64),('bankStoredSha256','0'*64),('requestSha256','0'*64),('reportFile','../report.json.gz')]:
   m=copy.deepcopy(self.manifest);m[field]=value;self.rejects(m=m)
 def test_unknown_manifest_fields_are_rejected(self):
  m=copy.deepcopy(self.manifest);m['admitted']=True;self.rejects(m=m)
 def test_false_parent_reuse_accounting_is_rejected(self):
  r=copy.deepcopy(self.report);r['batch']['work']['parentLpSolves']=2;self.rejects(r=r)
 def test_old_bank_report_cannot_be_presented_as_physical_proof(self):self.rejects(r=self.report['historicalSource'])
 def test_complete_physical_reproduction_flag_is_required(self):
  r=copy.deepcopy(self.report);r['originalMenusReproduced']=False;self.rejects(r=r)
 def test_all_nine_grades_retain_exact_ev_loss_and_classification(self):
  for field in ['lowerEvBb','upperLossBb','classification']:
   f=copy.deepcopy(self.fixture);f['grades'][8]['move'][field]='CHANGED' if field=='classification' else 123;self.rejects(f=f)
 def test_future_board_and_hidden_cards_cannot_be_added(self):
  f=copy.deepcopy(self.fixture);f['questions'][2]['board']=['As'];self.rejects(f=f)
  f=copy.deepcopy(self.fixture);f['questions'][1]['opponentCards']='As Ad';self.rejects(f=f)
 def test_mixed_review_order_identity_and_grade_are_bound(self):
  f=copy.deepcopy(self.fixture);f['review']['attempts'][0]['ordinal']=1;self.rejects(f=f)
  f=copy.deepcopy(self.fixture);f['review']['attempts'][0]['grade']['sourceCaseIndex']=2;self.rejects(f=f)
 def test_canonical_export_rejects_crlf_or_extra_whitespace(self):
  self.rejects(e=self.export.replace('\n','\r\n'));self.rejects(e=self.export+' ')
 def test_old_public_metadata_cannot_claim_new_provenance(self):
  f=copy.deepcopy(self.fixture);f['metadata']['startupBudget']=copy.deepcopy(self.manifest['startupBudget']);self.rejects(f=f)
 def test_all_three_questions_and_nine_choices_are_required(self):
  f=copy.deepcopy(self.fixture);f['questions'].pop();self.rejects(f=f)
  f=copy.deepcopy(self.fixture);f['grades'].pop();self.rejects(f=f)
 def test_review_boundaries_preserve_original_protocol(self):
  grades=self.fixture['grades'];choice=dict(sourceCaseIndex=2,action='call')
  self.assertEqual([],check.public.review([],grades)['attempts']);self.assertEqual(128,len(check.public.review([choice]*128,grades)['attempts']))
  with self.assertRaises(ValueError):check.public.review([choice]*129,grades)
  with self.assertRaises(ValueError):check.public.review([dict(sourceCaseIndex=3,action='call')],grades)
if __name__=='__main__':unittest.main()
