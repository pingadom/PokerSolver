"""Run disjoint backend test groups and reject incomplete/mixed JUnit evidence."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('engine', 'solver', 'shared', 'api', 'worker')
GROUPS = ('solver-0', 'solver-1', 'services')
SCHEMA = 'pokerlab-backend-ci-manifest/v1'


def inventory():
    """Mirror Surefire's four default source-name patterns; no manual allowlist."""
    pom = ET.parse(ROOT / 'pom.xml').getroot()
    namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
    if tuple(module.text for module in pom.findall('m:modules/m:module', namespace)) != MODULES:
        raise ValueError('Backend module inventory changed; assign every module explicitly')
    result = {}
    for module in MODULES:
        for path in sorted((ROOT / module / 'src/test/java').rglob('*.java')):
            name = path.stem
            if not (name.startswith('Test') or name.endswith(('Test', 'Tests', 'TestCase'))):
                continue
            package = re.search(r'^\s*package\s+([A-Za-z_][\w.]*)\s*;', path.read_text(encoding='utf-8'), re.MULTILINE)
            if not package:
                raise ValueError(f'Test source has no declared package: {path}')
            qualified = package[1] + '.' + name
            if qualified in result:
                raise ValueError(f'Duplicate test class: {qualified}')
            result[qualified] = module
    if set(result.values()) != set(MODULES):
        raise ValueError('Every backend module must have test sources')
    return dict(sorted(result.items()))


def selected(group, sources):
    if group not in GROUPS:
        raise ValueError(f'Unknown test group: {group}')
    if group == 'services':
        return [name for name, module in sources.items() if module != 'solver']
    bucket = int(group[-1])
    return [name for name, module in sources.items() if module == 'solver'
            and int(hashlib.sha256(name.encode('utf-8')).hexdigest(), 16) % 2 == bucket]


def manifest(group):
    sources = inventory()
    classes = selected(group, sources)
    if not classes:
        raise ValueError(f'Empty test group: {group}')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    digest = hashlib.sha256(json.dumps(sources, sort_keys=True, separators=(',', ':')).encode('utf-8')).hexdigest()
    return {'schemaVersion': SCHEMA, 'commit': head, 'inventoryHash': digest, 'group': group,
            'selectedClasses': classes, 'completed': False}


def run(group, maven):
    planned = manifest(group)
    path = ROOT / f'backend-ci-manifest-{group}.json'
    if path.exists():
        raise ValueError('Use a clean checkout: an existing manifest cannot be overwritten')
    # An old local report must never be mistaken for a class executed in this run.
    for module in MODULES:
        if list((ROOT / module / 'target/surefire-reports').glob('TEST-*.xml')):
            raise ValueError('Use a clean checkout: stale JUnit reports exist')
    path.write_text(json.dumps(planned, indent=2) + '\n', encoding='utf-8')
    command = [maven, '-B', '-pl', 'engine,shared,api,worker' if group == 'services' else 'solver', 'verify']
    if group != 'services':
        command.append('-Dtest=' + ','.join(planned['selectedClasses']))
    print(f"Verifying {group}: {len(planned['selectedClasses'])} test classes", flush=True)
    result = subprocess.run(command, cwd=ROOT, check=False)
    planned['completed'] = result.returncode == 0
    path.write_text(json.dumps(planned, indent=2) + '\n', encoding='utf-8')
    if result.returncode:
        raise subprocess.CalledProcessError(result.returncode, command)


def verify(folder):
    folder = folder.resolve()
    sources = inventory()
    suites = []
    seen = set()
    for group in GROUPS:
        directory = folder / f'junit-results-{group}'
        if not directory.is_dir():
            raise ValueError(f'Missing evidence group: {group}')
        actual = json.loads((directory / f'backend-ci-manifest-{group}.json').read_text(encoding='utf-8'))
        expected = manifest(group)
        expected['completed'] = True
        if actual != expected or actual.get('completed') is not True:
            raise ValueError(f'Incomplete or foreign manifest: {group}')
        group_classes = set()
        for path in sorted(directory.rglob('TEST-*.xml')):
            suite = ET.parse(path).getroot()
            if suite.tag != 'testsuite':
                raise ValueError(f'Unsupported JUnit document: {path}')
            name = suite.attrib['name']
            outer = name.split('$')[0]
            module = path.relative_to(directory).parts[0]
            if outer not in expected['selectedClasses'] or sources.get(outer) != module:
                raise ValueError(f'Unassigned test suite: {name} in {group}/{module}')
            if name in seen:
                raise ValueError(f'Duplicate test suite: {name}')
            seen.add(name)
            group_classes.add(outer)
            counts = {key: int(suite.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')}
            cases = suite.findall('testcase')
            if min(counts.values()) < 0 or counts['tests'] < 1 or counts['tests'] != len(cases):
                raise ValueError(f'Incomplete test-case accounting: {name}')
            for key, element in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
                if counts[key] != sum(case.find(element) is not None for case in cases):
                    raise ValueError(f'Inconsistent JUnit {key}: {name}')
            if counts['failures'] or counts['errors']:
                raise ValueError(f'Failed test suite: {name}')
            for case in cases:
                skip = case.find('skipped')
                if skip is not None and not (
                        outer == 'com.pokerlab.shared.PostgresLifecycleTest'
                        and skip.attrib.get('message') == 'Environment variable [TEST_DATABASE_URL] does not exist'):
                    raise ValueError(f'Unexpected skipped test: {name}/{case.attrib.get("name")}')
            suites.append({'name': name, 'module': module, 'group': group, **counts})
        if group_classes != set(expected['selectedClasses']):
            raise ValueError(f'Missing test-class evidence in {group}: {sorted(set(expected["selectedClasses"]) - group_classes)}')
    actual_directories = {path.name for path in folder.iterdir() if path.is_dir()}
    if actual_directories != {f'junit-results-{group}' for group in GROUPS}:
        raise ValueError('Unexpected evidence directories')
    summary = {key: sum(suite[key] for suite in suites) for key in ('tests', 'failures', 'errors', 'skipped')}
    summary.update(schemaVersion='pokerlab-backend-ci-evidence/v1', commit=manifest('services')['commit'],
                   suites=len(suites), classes=len(sources), modules=list(MODULES),
                   passed=summary['tests'] - summary['skipped'],
                   skipSuites=[suite for suite in suites if suite['skipped']])
    (folder / 'backend-ci-summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary, indent=2))
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='mode', required=True)
    plan = sub.add_parser('plan')
    plan.add_argument('group', choices=GROUPS)
    execute = sub.add_parser('run')
    execute.add_argument('group', choices=GROUPS)
    execute.add_argument('--maven', default=shutil.which('mvn') or 'mvn')
    audit = sub.add_parser('verify')
    audit.add_argument('folder', type=Path)
    args = parser.parse_args()
    if args.mode == 'plan':
        print(json.dumps(manifest(args.group), indent=2))
    elif args.mode == 'run':
        run(args.group, args.maven)
    else:
        verify(args.folder)


if __name__ == '__main__':
    main()
