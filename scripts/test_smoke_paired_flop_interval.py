"""Readiness retries transport startup failures, never successful but incorrect evidence."""

import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

SPEC = importlib.util.spec_from_file_location('paired_flop_smoke', Path(__file__).with_name('smoke-paired-flop-interval-trainer.py'))
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class PairedFlopReadinessTest(unittest.TestCase):
    def test_reset_and_refusal_retry_then_return_live_metadata(self):
        metadata = {'reportHash': SMOKE.PIN}
        with patch.object(SMOKE, 'call', side_effect=[ConnectionResetError('restarting'),
                                                    urllib.error.URLError('refused'), metadata]) as request, \
                patch.object(SMOKE.time, 'sleep') as sleep:
            self.assertEqual(metadata, SMOKE.wait_for_metadata())
        self.assertEqual(3, request.call_count)
        self.assertEqual(2, sleep.call_count)

    def test_explicit_long_budget_is_separate_from_old_bb_waits(self):
        self.assertEqual(900, SMOKE.STARTUP_SECONDS)

    def test_permanent_transport_failure_exhausts_budget(self):
        with patch.object(SMOKE, 'call', side_effect=ConnectionResetError('not ready')), \
                patch.object(SMOKE.time, 'monotonic', side_effect=[0, 1, 2]), \
                patch.object(SMOKE.time, 'sleep') as sleep:
            with self.assertRaises(ConnectionResetError):
                SMOKE.wait_for_metadata(timeout=2)
        sleep.assert_called_once_with(2)

    def test_wrong_successful_pin_fails_without_retrying_or_grading(self):
        with patch.object(SMOKE, 'call', return_value={'reportHash': '0' * 64}) as request, \
                patch.object(SMOKE.time, 'sleep') as sleep:
            with self.assertRaises(AssertionError):
                SMOKE.run(False)
        request.assert_called_once_with()
        sleep.assert_not_called()

    def test_malformed_json_is_not_a_transport_readiness_retry(self):
        with patch.object(SMOKE, 'call', side_effect=ValueError('malformed JSON')), \
                patch.object(SMOKE.time, 'sleep') as sleep:
            with self.assertRaises(ValueError):
                SMOKE.wait_for_metadata()
        sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
