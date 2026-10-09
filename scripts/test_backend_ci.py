"""Fail-closed controls for backend CI evidence, independent of Maven/test runtimes."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
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
            # Exercise all four default Surefire naming patterns and both hash buckets.
            names = ['TestStart', 'ExampleTest', 'ExampleTests', 'ExampleTestCase', 'FixtureTest'] if module == 'solver' else ['ExampleTest']
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
        assigned = [ci.selected(group, self.sources) for group in ci.GROUPS]
        self.assertTrue(all(assigned))
        self.assertEqual(len(self.sources), len(set().union(*map(set, assigned))))
        self.assertEqual(len(self.sources), sum(map(len, assigned)))
        summary = self.verify()
        self.assertEqual(9, summary['tests'])
        self.assertEqual(9, summary['classes'])
        self.assertEqual(0, summary['failures'] + summary['errors'])

    def test_missing_group_or_class_never_passes(self):
        report = next(self.folder.rglob('TEST-*.xml'))
        report.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing test-class evidence'):
            self.verify()
        (self.folder / 'junit-results-solver-0').rename(self.folder / 'wrong-group')
        with self.assertRaisesRegex(ValueError, 'Missing evidence group'):
            self.verify()

    def test_old_commit_unfinished_or_changed_class_assignment_never_passes(self):
        path = self.folder / 'junit-results-services/backend-ci-manifest-services.json'
        original = json.loads(path.read_text(encoding='utf-8'))
        for key, value in [('commit', 'old-head'), ('completed', False), ('completed', 1), ('selectedClasses', []), ('inventoryHash', 'old-inventory')]:
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


if __name__ == '__main__':
    unittest.main()
