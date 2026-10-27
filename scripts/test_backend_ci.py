"""Fail-closed controls for backend CI evidence, independent of Maven/test runtimes."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('backend_ci', Path(__file__).with_name('backend-ci.py'))
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class BackendEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / 'evidence'
        self.folder.mkdir()
        self.addCleanup(patch.stopall)
        patch.object(ci, 'ROOT', self.root).start()
        patch.object(ci.subprocess, 'check_output', return_value='test-head\n').start()
        modules = ''.join(f'<module>{module}</module>' for module in ci.MODULES)
        (self.root / 'pom.xml').write_text(f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>{modules}</modules></project>', encoding='utf-8')
        for module in ci.MODULES:
            directory = self.root / module / 'src/test/java'
            directory.mkdir(parents=True)
            # Exercise all four default Surefire naming patterns and all hash buckets.
            names = ['TestStart', 'ExampleTest', 'ExampleTests', 'ExampleTestCase', 'FixtureTest', 'Partition1Test', 'Partition6Test'] + [f'Coverage{i}Test' for i in range(64)] if module == 'solver' else ['ExampleTest']
            for name in names:
                (directory / f'{name}.java').write_text(f'package example.{module}; class {name} {{}}', encoding='utf-8')
        self.sources = ci.inventory()
        for group in ci.GROUPS:
            directory = self.folder / f'junit-results-{group}'
            directory.mkdir()
            planned = ci.manifest(group)
            planned['completed'] = True
            (directory / f'backend-ci-manifest-{group}.json').write_text(json.dumps(planned), encoding='utf-8')
            for name in planned['selectedClasses']:
                report = directory / self.sources[name] / 'target/surefire-reports' / f'TEST-{name}.xml'
                report.parent.mkdir(parents=True, exist_ok=True)
                report.write_text(f'<testsuite name="{name}" tests="1" failures="0" errors="0" skipped="0"><testcase name="one"/></testsuite>', encoding='utf-8')

    def verify(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return ci.verify(self.folder)

    def test_complete_partition_accounts_for_all_modules_and_default_name_patterns(self):
        self.assertEqual(tuple(f'solver-{i}' for i in range(8)) + ('services-0', 'services-1'), ci.GROUPS)
        assigned = [ci.selected(group, self.sources) for group in ci.GROUPS]
        self.assertTrue(all(assigned))
        self.assertEqual(len(self.sources), len(set().union(*map(set, assigned))))
        self.assertEqual(len(self.sources), sum(map(len, assigned)))
        summary = self.verify()
        self.assertEqual(len(self.sources), summary['tests'])
        self.assertEqual(len(self.sources), summary['classes'])
        self.assertEqual(0, summary['failures'] + summary['errors'])

    def test_expensive_replays_move_to_distinct_shards_without_losing_any_class(self):
        sources = {name: 'solver' for name in ci.SOLVER_REPLAY_SHARDS}
        sources.update({'example.solver.OtherTest': 'solver', 'example.api.OtherTest': 'api'})
        assigned = [ci.selected(group, sources) for group in ci.GROUPS]
        self.assertEqual(set(sources), set().union(*map(set, assigned)))
        self.assertEqual(len(sources), sum(map(len, assigned)))
        self.assertEqual({4, 6, 7}, set(ci.SOLVER_REPLAY_SHARDS.values()))
        for name, bucket in ci.SOLVER_REPLAY_SHARDS.items():
            self.assertIn(name, ci.selected(f'solver-{bucket}', sources))
            self.assertNotIn(name, ci.selected('solver-3', sources))

    def test_missing_additional_solver_shards_never_pass(self):
        for bucket in range(2, 8):
            with self.subTest(bucket=bucket):
                original = self.folder / f'junit-results-solver-{bucket}'
                missing = self.folder / f'missing-solver-{bucket}'
                original.rename(missing)
                try:
                    with self.assertRaisesRegex(ValueError, f'Missing evidence group: solver-{bucket}'):
                        self.verify()
                finally:
                    missing.rename(original)

    def test_both_service_shards_are_required(self):
        for bucket in (0, 1):
            original = self.folder / f'junit-results-services-{bucket}'
            missing = self.folder / f'missing-services-{bucket}'
            original.rename(missing)
            try:
                with self.assertRaisesRegex(ValueError, f'Missing evidence group: services-{bucket}'):
                    self.verify()
            finally:
                missing.rename(original)

    def test_service_maven_selection_is_explicit_and_module_gaps_do_not_skip_assignments(self):
        with patch.object(ci.subprocess, 'run', return_value=subprocess.CompletedProcess(['mvn'], 0)) as execute:
            with contextlib.redirect_stdout(io.StringIO()):
                ci.run('services-0', 'mvn')
        command = execute.call_args.args[0]
        self.assertIn('engine,shared,api,worker', command)
        self.assertIn('-Dtest=' + ','.join(ci.selected('services-0', self.sources)), command)
        self.assertIn('-Dsurefire.failIfNoSpecifiedTests=false', command)
        report = next((self.folder / 'junit-results-services-0').rglob('TEST-*.xml'))
        report.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing test-class evidence'):
            self.verify()

    def test_missing_group_or_class_never_passes(self):
        report = next(self.folder.rglob('TEST-*.xml'))
        report.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing test-class evidence'):
            self.verify()
        (self.folder / 'junit-results-solver-0').rename(self.folder / 'wrong-group')
        with self.assertRaisesRegex(ValueError, 'Missing evidence group'):
            self.verify()

    def test_old_commit_unfinished_or_changed_class_assignment_never_passes(self):
        path = self.folder / 'junit-results-services-0/backend-ci-manifest-services-0.json'
        original = json.loads(path.read_text(encoding='utf-8'))
        for key, value in [('commit', 'old-head'), ('completed', False), ('completed', 1), ('selectedClasses', []), ('inventoryHash', 'old-inventory'), ('schemaVersion', 'pokerlab-backend-ci-manifest/v3'), ('schemaVersion', 'pokerlab-backend-ci-manifest/v4')]:
            changed = {**original, key: value}
            path.write_text(json.dumps(changed), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Incomplete or foreign manifest'):
                self.verify()

    def test_duplicate_suite_and_unassigned_group_never_pass(self):
        report = next(self.folder.rglob('TEST-*.xml'))
        duplicate = report.parent / 'TEST-duplicate.xml'
        duplicate.write_bytes(report.read_bytes())
        with self.assertRaisesRegex(ValueError, 'Duplicate test suite'):
            self.verify()
        duplicate.unlink()
        other = next(path for path in self.folder.iterdir() if path.is_dir() and path not in report.parents)
        target = other / report.relative_to(report.parents[3])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(report.read_bytes())
        with self.assertRaisesRegex(ValueError, 'Unassigned test suite'):
            self.verify()

    def test_hidden_failures_zero_case_and_inconsistent_counts_never_pass(self):
        report = next(self.folder.rglob('TEST-*.xml'))
        original = report.read_text(encoding='utf-8')
        for changed in [original.replace('<testcase name="one"/>', '<testcase name="one"><failure/></testcase>'),
                        original.replace('tests="1"', 'tests="2"'),
                        original.replace('tests="1"', 'tests="0"').replace('<testcase name="one"/>', ''),
                        original.replace('failures="0"', 'failures="1"').replace('<testcase name="one"/>', '<testcase name="one"><failure/></testcase>')]:
            report.write_text(changed, encoding='utf-8')
            with self.assertRaises(ValueError):
                self.verify()

    def test_new_source_or_module_cannot_silently_fall_out_of_coverage(self):
        source = self.root / 'solver/src/test/java/NewTest.java'
        source.write_text('package example.solver; class NewTest {}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Incomplete or foreign manifest'):
            self.verify()
        pom = self.root / 'pom.xml'
        pom.write_text(pom.read_text(encoding='utf-8').replace('</modules>', '<module>new-module</module></modules>'), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Backend module inventory changed'):
            ci.inventory()

    def test_a_disabled_or_environment_skipped_solver_check_is_not_complete_evidence(self):
        report = next((self.folder / 'junit-results-solver-0').rglob('TEST-*.xml'))
        original = report.read_text(encoding='utf-8')
        report.write_text(original.replace('skipped="0"', 'skipped="1"').replace(
            '<testcase name="one"/>', '<testcase name="one"><skipped message="disabled"/></testcase>'), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Unexpected skipped test'):
            self.verify()

    def test_failed_maven_leaves_an_incomplete_manifest_and_propagates_failure(self):
        with patch.object(ci.subprocess, 'run', return_value=subprocess.CompletedProcess(['mvn'], 9)):
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(subprocess.CalledProcessError) as failure:
                ci.run('solver-0', 'mvn')
        self.assertEqual(9, failure.exception.returncode)
        planned = json.loads((self.root / 'backend-ci-manifest-solver-0.json').read_text(encoding='utf-8'))
        self.assertIs(planned['completed'], False)

    def test_stale_reports_and_existing_manifests_are_rejected_before_maven(self):
        report = self.root / 'solver/target/surefire-reports/TEST-old.xml'
        report.parent.mkdir(parents=True)
        report.write_text('old evidence', encoding='utf-8')
        with patch.object(ci.subprocess, 'run') as execute:
            with self.assertRaisesRegex(ValueError, 'stale JUnit reports'):
                ci.run('solver-0', 'mvn')
            execute.assert_not_called()
        report.unlink()
        (self.root / 'backend-ci-manifest-solver-0.json').write_text('old manifest', encoding='utf-8')
        with patch.object(ci.subprocess, 'run') as execute:
            with self.assertRaisesRegex(ValueError, 'existing manifest'):
                ci.run('solver-0', 'mvn')
            execute.assert_not_called()


if __name__ == '__main__':
    unittest.main()
