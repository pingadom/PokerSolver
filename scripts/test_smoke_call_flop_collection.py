import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('call_collection_smoke',ROOT/'scripts/smoke-call-flop-collection.py')
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)

class CallCollectionSmokeTest(unittest.TestCase):
    def test_source_pins_are_extracted_from_every_real_typed_question(self):
        review=json.loads((ROOT/'docs/data/supplied-fourteen-question-flop-collection-review.json').read_bytes())
        for q in review['questions']:
            pin=smoke.source_pin(q)
            self.assertRegex(pin,r'^[0-9a-f]{64}$')
            if q['index']>=11:self.assertEqual('55902ac40959215482cbf3d0b623da7d64313847f7c58c14a81f867d76f22e90',pin)
            elif q['index']==10:self.assertEqual('5dbd0cdeba8a74e6da32dae0bddeb69381a90ba4f72220ab4a9bf92bcc667c56',pin)
        with self.assertRaises(AssertionError):smoke.source_pin(dict(kind='ambiguous',legacy={},shared={}))
    def test_readiness_retries_bounded_transient_errors(self):
        with patch.object(smoke,'call',side_effect=[urllib.error.URLError('starting'),AssertionError('503'),{'ready':True}]),patch.object(smoke.time,'sleep') as sleep:
            self.assertEqual({'ready':True},smoke.wait_for_metadata());self.assertEqual(2,sleep.call_count)
    def test_deadline_is_not_extended_by_failures(self):
        with patch.object(smoke,'call',side_effect=TimeoutError),patch.object(smoke.time,'monotonic',side_effect=[0,1201]),patch.object(smoke.time,'sleep') as sleep:
            with self.assertRaises(TimeoutError):smoke.wait_for_metadata()
            sleep.assert_not_called()
    def test_disabled_smoke_checks_every_route(self):
        with patch.object(smoke,'call') as call:
            smoke.run(True);self.assertEqual(5,call.call_count)
            self.assertTrue(all(c.args[2]==404 for c in call.call_args_list))

if __name__=='__main__':unittest.main()
