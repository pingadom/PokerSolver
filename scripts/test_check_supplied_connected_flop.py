"""Real physical evidence and corruption controls for the independent connected-flop checker."""
import gzip
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHECKER = ROOT / 'scripts/check-supplied-connected-flop.py'
REQUEST = ROOT / 'docs/data/supplied-four-button-hand-connected-flop-request.json'
REPORT = ROOT / 'docs/data/supplied-four-button-hand-connected-flop-report.json.gz'


class ConnectedFlopCheckerTest(unittest.TestCase):
    def run_checker(self, request, report, output):
        return subprocess.run([sys.executable, str(CHECKER), str(request), str(report), '--output', str(output)],
                              capture_output=True, text=True, timeout=30)

    def corrupt(self, mutation, expected):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            report = json.loads(gzip.decompress(REPORT.read_bytes()))
            mutation(report)
            changed = folder / 'corrupt.json.gz'
            changed.write_bytes(gzip.compress(json.dumps(report).encode()))
            output = folder / 'receipt.json'
            result = self.run_checker(REQUEST, changed, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(expected, result.stderr)
            self.assertFalse(output.exists())

    def test_real_evidence_passes_with_no_optimizer_and_preserves_existing_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'receipt.json'
            result = self.run_checker(REQUEST, REPORT, output)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads(output.read_text())
            self.assertEqual(0, receipt['freshLpSolves'])
            self.assertFalse(receipt['originalLpCertificatesIndependentlyChecked'])
            self.assertEqual(15984, receipt['independentFixedFlopRunouts'])
            self.assertEqual(1321, receipt['completeParentTreeStates'])
            self.assertEqual(1, receipt['reachedFlopQualityChecks'])
            self.assertEqual(3, receipt['suppressedTinyReachPosteriors'])
            original = output.read_bytes()
            self.assertNotEqual(0, self.run_checker(REQUEST, REPORT, output).returncode)
            self.assertEqual(original, output.read_bytes())

    def test_independent_card_ranking_controls(self):
        spec = importlib.util.spec_from_file_location('connected_flop_checker_controls', CHECKER)
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        def five(text):
            return module.five(tuple(map(module.card, text.split())))
        self.assertEqual((4, 5), five('As 2d 3h 4s 5c'))
        self.assertEqual((8, 14), five('As Ks Qs Js Ts'))
        self.assertEqual((6, 14, 13), five('Ah Ad Ac Ks Kd'))
        self.assertGreater(five('Ah Ad Ac As 2d'), five('Kh Kd Kc Qs Qh'))
        self.assertEqual(module.seven(tuple(map(module.card, 'As Ks Qs Js Ts 2c 3d'.split()))),
                         module.seven(tuple(map(module.card, 'Ah Kh Qh Jh Th 4c 5d'.split()))))

    def test_changed_fixed_flop_counts_fail_independent_cards(self):
        def change(report):
            counts = report['handoffs'][0]['worlds'][0]['counts']
            counts['firstWins'] += 1
            counts['secondWins'] -= 1
        self.corrupt(change, 'Independent physical runout counts differ')

    def test_wrong_residual_partition_multiplicity_fails(self):
        def change(report):
            counts = report['residuals'][0]['counts']
            counts['firstWins'] += 1
            counts['secondWins'] -= 1
        self.corrupt(change, 'Residual partition counts differ')

    def test_renormalizing_selected_flop_chance_fails(self):
        def change(report):
            report['handoffs'][0]['worlds'][0]['physicalFlopProbabilityGivenDeal'] = 1
        self.corrupt(change, 'Physical chance was renormalized')

    def test_policy_corruption_fails_independent_whole_parent_best_response(self):
        def change(report):
            policy = report['candidate']['strategy']
            key = next(k for k in policy if k.startswith('5:supplied-connected-single-flop:') and k.endswith(':'))
            policy[key] = dict(fold=1, call=0, **{'raise:9.0': 0})
        self.corrupt(change, 'Independent literal best-response disagreement')

    def test_tiny_reach_cannot_manufacture_a_conditional_posterior(self):
        def change(report):
            local = next(a for a in report['reachedFlopQuality'] if a['conditioning'] == 'BELOW_CANDIDATE_REACH_FLOOR')
            local['reachedWorlds'] = [dict(originalDealIndex=0, jointRootFlopReach=local['candidateRootFlopReach'], conditionalWeight=1)]
        self.corrupt(change, 'Tiny reach must not manufacture a posterior')

    def test_numerical_trace_mass_cannot_be_relabelled_as_zero(self):
        def change(report):
            local = next(a for a in report['reachedFlopQuality'] if a['conditioning'] == 'BELOW_CANDIDATE_REACH_FLOOR')
            local['rawWorldMasses'][0]['jointRootFlopReach'] = 0
        self.corrupt(change, 'Raw world mass differs')

    def test_original_joint_chance_cannot_be_replaced(self):
        self.corrupt(lambda report: report['originalJointPrior'][0].update(conditionalProbability=.2),
                     'Original physical prior/payoffs differ')

    def test_pot_and_admission_corruptions_fail(self):
        self.corrupt(lambda report: report['handoffs'][0]['committedBb'].__setitem__(3, 4),
                     'Handoff stacks/fold flags differ')
        self.corrupt(lambda report: report.update(trainerAdmission=True), 'Unsupported connected identity')

    def test_changed_model_request_cannot_reuse_the_literal_tree(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            request = json.loads(REQUEST.read_bytes())
            request['betPotFraction'] = .6
            path = folder / 'changed-request.json'
            path.write_text(json.dumps(request))
            output = folder / 'receipt.json'
            result = self.run_checker(path, REPORT, output)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Unsupported fixed-case request', result.stderr)
            self.assertFalse(output.exists())

    def test_byte_caps_duplicate_keys_and_nonfinite_values_fail_without_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            cases = [('json', b'x'*(8*1024*1024+1), 'Stored byte cap'),
                     ('json.gz', gzip.compress(b' '*(8*1024*1024+1)), 'Expanded byte cap'),
                     ('json', b'{"x":0,"x":1}', 'Duplicate JSON key'),
                     ('json', b'{"x":NaN}', 'Non-finite JSON constant'),
                     ('json', b'{"x":1e309}', 'Non-finite JSON number')]
            for index, (suffix, data, expected) in enumerate(cases):
                path = folder / f'bad-{index}.{suffix}'
                path.write_bytes(data)
                output = folder / 'receipt.json'
                result = self.run_checker(REQUEST, path, output)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(expected, result.stderr)
                self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
