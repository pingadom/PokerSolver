"""Readiness must tolerate all four source replays and still fail a stuck startup."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location('collection_smoke', Path(__file__).with_name('smoke-interval-collection.py'))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class CollectionReadinessTest(unittest.TestCase):
    def test_connection_and_not_ready_responses_retry_until_real_metadata(self):
        expected = {'collectionHash': smoke.PIN, 'availableQuestions': 4}
        with patch.object(smoke, 'call', side_effect=[urllib.error.URLError('not listening'),
                AssertionError('not ready'), expected]) as call, \
                patch.object(smoke.time, 'sleep') as sleep, \
                patch.object(smoke.time, 'monotonic', side_effect=[0, 1, 2]):
            self.assertEqual(expected, smoke.wait_for_metadata())
            self.assertEqual(3, call.call_count)
            self.assertEqual(2, sleep.call_count)

    def test_deadline_rethrows_the_actual_failure(self):
        error = TimeoutError('physical replay did not finish')
        with patch.object(smoke, 'call', side_effect=error), \
                patch.object(smoke.time, 'sleep') as sleep, \
                patch.object(smoke.time, 'monotonic', side_effect=[0, 180]):
            with self.assertRaises(TimeoutError) as raised:
                smoke.wait_for_metadata()
            self.assertIs(error, raised.exception)
            sleep.assert_not_called()

    def test_invalid_json_or_unrelated_errors_are_not_hidden_as_readiness_delays(self):
        with patch.object(smoke, 'call', side_effect=ValueError('invalid response')), \
                patch.object(smoke.time, 'sleep') as sleep:
            with self.assertRaises(ValueError):
                smoke.wait_for_metadata()
            sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
