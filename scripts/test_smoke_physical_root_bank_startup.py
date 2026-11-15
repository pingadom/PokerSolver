import copy
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('physical_root_startup_smoke_test',Path(__file__).with_name('smoke-physical-root-bank-startup.py'));smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
class StartupSmokeControls(unittest.TestCase):
 def good(self):return smoke.expected('PHYSICAL_PARENT_REUSE')
 def reject(self,value):
  with patch.object(smoke.legacy,'run'),patch.object(smoke.legacy,'call',return_value=value):
   with self.assertRaises(AssertionError):smoke.run()
 def test_complete_optimized_startup_and_unchanged_legacy_contract(self):
  with patch.object(smoke.legacy,'run') as run,patch.object(smoke.legacy,'call',return_value=self.good()) as call:
   self.assertEqual(self.good(),smoke.run());run.assert_called_once_with(False);self.assertEqual(2,call.call_count)
 def test_original_default_has_a_distinct_proof_and_eight_parent_reservation(self):
  x=smoke.expected('ORIGINAL');self.assertEqual(8,x['startupBudget']['maximumParentLpSolves']);self.assertEqual(smoke.legacy.PIN,x['sourceProofHash'])
  with patch.object(smoke.legacy,'run'),patch.object(smoke.legacy,'call',return_value=x):self.assertEqual(x,smoke.run('ORIGINAL'))
 def test_disabled_provenance_and_old_routes_remain_absent(self):
  with patch.object(smoke.legacy,'run') as run,patch.object(smoke.legacy,'call') as call:
   smoke.run(disabled=True);run.assert_called_once_with(True);call.assert_called_once_with('/startup',None,404)
 def test_unknown_source_rejects_before_http(self):
  with patch.object(smoke.legacy,'run') as run:
   for value in ['',None,'physical','ORIGINAL ']:
    with self.assertRaises(ValueError):smoke.run(value)
   run.assert_not_called()
 def test_public_and_physical_proof_pins_cannot_be_substituted(self):
  for key in ['studyHash','sourceProofHash']:
   x=self.good();x[key]='0'*64;self.reject(x)
 def test_every_work_budget_field_is_bound(self):
  for key in self.good()['startupBudget']:
   x=self.good();x['startupBudget'][key]=0;self.reject(x)
 def test_integer_and_boolean_types_are_not_coerced(self):
  for value in [2.0,True,'2']:
   x=self.good();x['startupBudget']['maximumParentLpSolves']=value;self.reject(x)
  x=self.good();x['trainerAdmission']=0;self.reject(x)
 def test_unknown_fields_or_hidden_cards_are_rejected(self):
  x=self.good();x['opponentCards']='As Ah';self.reject(x)
 def test_false_admission_and_inventory_reject(self):
  for key,value in [('trainerAdmission',True),('availableQuestions',2),('cachedLegalGrades',6)]:
   x=self.good();x[key]=value;self.reject(x)
 def test_source_label_cannot_misrepresent_work(self):
  self.reject(smoke.expected('ORIGINAL'))
 def test_historical_metadata_semantics_remain_explicit(self):
  x=self.good();x['publicMetadataMeaning']='ACTUAL_EIGHT_PARENT_STARTUP';self.reject(x)
if __name__=='__main__':unittest.main()
