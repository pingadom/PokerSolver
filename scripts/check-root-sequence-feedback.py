"""Verify complete-menu sequence-minimum qualification without optimization."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
IMMUTABLE_HELPERS = {'check-physical-root-sequence-minimum.py': '5d8ea982b786bb22256ca5d85e0488394733457de978487ae170c7fb1f13cec3', 'check-root-minimum-feedback.py': '683e1f614d333d4a881ed4223baae2f1d149115a5414f73b56dc553bbb071c69', 'check-root-minimum.py': '12f49d03dd35e19cdb56fa498b83014787cae75bd70042d271b2933744cc9c7d', 'check-root-sequence-minimax.py': '5a242aa3b791e04807e5c3f0426633db2c03c5001554f24461f2198db97ce765', 'check-root-envelope-feedback.py': 'e0e7d3e803fe6d2de99b893ccda9e0e3ce71b93b74580be398c3c6ac03fdcf48', 'check-root-security-envelope.py': 'dd871cc2d9394a52263a2f8945d5713e2f9e0a8a21fde0be0ead0bc60c28a0b1', 'check-reduced-preflop.py': '4202b10cbeddab54d20708b87d5769403bbbf4dda3a604df3518299a851d0960', 'check-root-minimax.py': 'a41d0959f74c5e00a0a43843c13b31107d396fdd8da43b0f87e4685a84ad6cc5', 'check-resolvable-refined-lp.py': '84795dabca56dd86a4870b23efd8d5731d4b4b17d3399fbfa818f7ee9d18f080'}
for name, pin in IMMUTABLE_HELPERS.items():
    if hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest() != pin:
        raise ValueError('Immutable qualification helper changed: '+name)


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT/'scripts'/filename)
    value = importlib.util.module_from_spec(spec); sys.modules[name] = value
    spec.loader.exec_module(value); return value


sequence = module('sequence_feedback_physical', 'check-physical-root-sequence-minimum.py')
menu = module('sequence_feedback_menu', 'check-root-minimum-feedback.py')
require, read = sequence.require, sequence.read
DATA = ROOT/'docs/data'
REQUEST = DATA/'supplied-root-sequence-feedback-request.json'
REPORT = DATA/'supplied-root-sequence-feedback-report.json.gz'
MINIMUM_SHA = '1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f'


def context():
    require(hashlib.sha256(sequence.REPORT.read_bytes()).hexdigest() == MINIMUM_SHA,
            'Pinned physical sequence minimum bytes differ')
    original = sequence.context()
    minimum = read(sequence.REPORT)
    proof = sequence.check_values(read(sequence.REQUEST), minimum, original)
    return original[0][0], minimum, proof


def canonical(request):
    request = copy.deepcopy(request)
    p = request['minimumRequest']['sourceRequest']['envelope']['parent']
    p['input'] = sequence.physical.source.physical.canonical_input(p['input'])
    p['flop'].sort()
    return request


def exact(actual, expected, message):
    # Distinguish booleans from numbers and reject NaN, including nested evidence.
    require(json.dumps(actual, sort_keys=True, allow_nan=False) ==
            json.dumps(expected, sort_keys=True, allow_nan=False), message)


def compare(minimum, original, moves):
    legacy = menu.moves(minimum['legacyMinimum'], original, .01)
    result = []
    for before, after in zip(legacy, moves, strict=True):
        require(before['action'] == after['action'], 'Complete ordered comparison required')
        result.append(dict(action=after['action'], legacyMove=before,
            lowerEvDifferenceBb=after['lowerEvBb']-before['lowerEvBb'],
            upperEvDifferenceBb=after['upperEvBb']-before['upperEvBb'],
            lowerLossDifferenceBb=after['lowerLossBb']-before['lowerLossBb'],
            upperLossDifferenceBb=after['upperLossBb']-before['upperLossBb'],
            classificationUnchanged=after['classification'] == before['classification']))
    return result


def check_values(request, report, verified=None):
    original, minimum, proof = verified if verified is not None else context()
    request = canonical(request)
    require(set(request) == {'schemaVersion','minimumRequest','minimumStoredSha256','maximumLossBb'}
            and request['schemaVersion'] == 'pokerlab-root-sequence-feedback-request/v1'
            and request['minimumStoredSha256'] == MINIMUM_SHA and request['maximumLossBb'] == .01,
            'Declared sequence feedback request differs')
    exact(request['minimumRequest'], minimum['request'], 'Pinned minimum request differs')
    require(set(report) == {'schemaVersion','publicationStatus','trainerAdmission','boundsScope','request',
            'originalEnvelope','sourceMinimum','moves','policies','legacyMenuComparison','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion'] == 'pokerlab-root-sequence-feedback-report/v1'
            and report['publicationStatus'] == 'SEQUENCE_MINIMUM_COMPLETE_MENU_FEEDBACK_RESEARCH_ONLY'
            and report['boundsScope'] == menu.SCOPE and report['trainerAdmission'] is False,
            'Report scope/schema/admission differs')
    exact(report['request'], request, 'Report request differs')
    exact(report['originalEnvelope'], original, 'Original envelope differs')
    exact(report['sourceMinimum'], minimum, 'Full physical sequence evidence differs')
    expected = menu.moves(minimum, original, .01)
    exact(report['moves'], expected, 'Complete move/loss bounds differ')
    policies = [menu.assess(p, expected) for p in minimum['policies']]
    exact(report['policies'], policies, 'Whole-menu policy assessments differ')
    comparison = compare(minimum, original, expected)
    exact(report['legacyMenuComparison'], comparison, 'Legacy comparison differs')
    reasons = set()
    if not minimum['referenceSupportedMinimum']: reasons.add('MINIMUM_NOT_REFERENCE_SUPPORTED')
    if not any(m['classification'] == 'WITHIN_LIMIT' for m in expected): reasons.add('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification'] == 'OUTSIDE_LIMIT' for m in expected): reasons.add('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for p in policies:
        reasons.update(('PRIMARY' if p['iterations'] == 1 else str(p['iterations']))+':'+f for f in p['failures'])
    require(report['rejectionReasons'] == sorted(reasons) and report['qualifiedForOfflineDrill'] is (not reasons),
            'Offline qualification outcome differs')
    return dict(schemaVersion='pokerlab-root-sequence-feedback-independent-check/v1',trainerAdmission=False,
                sourceMinimumCheck=proof,movesChecked=3,policyMovesChecked=9,utilityAndLossBoundsChecked=36,
                legacyMenuComparison=comparison,qualifiedForOfflineDrill=not reasons,rejectionReasons=sorted(reasons),
                minimumUpperUsedAsActionCeiling=False,currentDecisionEvShiftApplied=False,freshLpSolves=0,freshCfrRuns=0)


def check():
    result = check_values(read(REQUEST),read(REPORT))
    result.update(requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),
        reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),
        checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        immutableHelperSha256=IMMUTABLE_HELPERS)
    return result


if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2,allow_nan=False)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
