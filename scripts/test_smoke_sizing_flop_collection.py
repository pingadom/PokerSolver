import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('sizing_collection_smoke',ROOT/'scripts/smoke-sizing-flop-collection.py')
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)

class SizingCollectionSmokeTest(unittest.TestCase):
    def test_source_pins_are_extracted_from_every_real_typed_question(self):
        review=json.loads((ROOT/'docs/data/supplied-twenty-question-flop-collection-review.json').read_bytes())
        for q in review['questions']:
            pin=smoke.source_pin(q)
            self.assertRegex(pin,r'^[0-9a-f]{64}$')
            if q['index']>=18:self.assertEqual('04e99982ef9775a83cbffa28f64aedc7e146dd95dc38f09ad73604df24828996',pin)
        with self.assertRaises(AssertionError):smoke.source_pin(dict(kind='ambiguous',legacy={},shared={}))
    def test_readiness_retries_bounded_transient_errors(self):
        with patch.object(smoke,'call',side_effect=[urllib.error.URLError('starting'),AssertionError('503'),{'ready':True}]),patch.object(smoke.time,'sleep') as sleep:
            self.assertEqual({'ready':True},smoke.wait_for_metadata());self.assertEqual(2,sleep.call_count)
    def test_deadline_is_not_extended_by_failures(self):
        with patch.object(smoke,'call',side_effect=TimeoutError),patch.object(smoke.time,'monotonic',side_effect=[0,1501]),patch.object(smoke.time,'sleep') as sleep:
            with self.assertRaises(TimeoutError):smoke.wait_for_metadata()
            sleep.assert_not_called()
    def test_disabled_smoke_checks_every_route(self):
        with patch.object(smoke,'call') as call:
            smoke.run(True);self.assertEqual(5,call.call_count)
            self.assertTrue(all(c.args[2]==404 for c in call.call_args_list))

if __name__=='__main__':unittest.main()
