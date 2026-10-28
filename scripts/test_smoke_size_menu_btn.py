import copy
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('btn_sizing_smoke', ROOT / 'scripts/smoke-size-menu-btn.py')
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class BtnSizingSmokeTest(unittest.TestCase):
    def test_all_eight_choices_match_frozen_canonical_review(self):
        choices = [a['attempt'] for a in smoke.FIXTURE['review']['attempts']]
        self.assertEqual(smoke.FIXTURE['review'], smoke.canonical_review(choices))
        self.assertEqual(choices[::-1], [a['attempt'] for a in smoke.canonical_review(choices[::-1])['attempts']])
        self.assertEqual(dict(distinctQuestions=1, withinLimit=128, outsideLimit=0, strategyDependent=0), smoke.canonical_review([choices[0]] * 128)['counts'])

    def test_invalid_choices_and_overflow_cannot_gain_cached_grades(self):
        for choice in [dict(sourceCaseIndex=0, action='check'), dict(sourceCaseIndex=4, action='fold'),
                       dict(sourceCaseIndex='0', action='fold'), dict(sourceCaseIndex=False, action='fold'),
                       dict(sourceCaseIndex=0, action='fold', ev=99)]:
            with self.assertRaises(AssertionError):
                smoke.canonical_review([choice])
        with self.assertRaises(AssertionError):
            smoke.canonical_review([dict(sourceCaseIndex=0, action='fold')] * 129)

    def test_readiness_retries_transient_failure_without_extending_deadline(self):
        with patch.object(smoke, 'call', side_effect=[urllib.error.URLError('starting'), smoke.FIXTURE['metadata']]), patch.object(smoke.time, 'sleep') as sleep:
            self.assertEqual(smoke.FIXTURE['metadata'], smoke.wait_for_metadata())
            sleep.assert_called_once_with(2)
        with patch.object(smoke, 'call', side_effect=TimeoutError), patch.object(smoke.time, 'monotonic', side_effect=[0, 601]), patch.object(smoke.time, 'sleep') as sleep:
            with self.assertRaises(TimeoutError):
                smoke.wait_for_metadata()
            sleep.assert_not_called()

    def test_disabled_smoke_requires_absence_of_every_route(self):
        with patch.object(smoke, 'call') as call:
            smoke.run(True)
            self.assertEqual(5, call.call_count)
            self.assertTrue(all(c.args[2] == 404 for c in call.call_args_list))

    def test_stale_metadata_prevents_acceptance_of_any_enabled_response(self):
        metadata = copy.deepcopy(smoke.FIXTURE['metadata'])
        metadata['studyHash'] = '0' * 64
        with patch.object(smoke, 'wait_for_metadata', return_value=metadata), patch.object(smoke, 'call') as call:
            with self.assertRaises(AssertionError):
                smoke.run()
            call.assert_not_called()


if __name__ == '__main__':
    unittest.main()
