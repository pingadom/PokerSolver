"""Independently verify minimum-informed menu/loss qualification; no LP/CFR optimization."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('minimum_feedback_source', ROOT/'scripts/check-root-minimum.py')
source = importlib.util.module_from_spec(spec); sys.modules[spec.name] = source; spec.loader.exec_module(source)
require, close, read = source.require, source.minimum.close, source.read
DATA = ROOT/'docs/data'
REQUEST, REPORT = DATA/'supplied-root-minimum-feedback-request.json', DATA/'supplied-root-minimum-feedback-report.json.gz'
MINIMUM_SHA = 'ea48889cd6677102c917d1990e6b14dc576a12dad75bc7a86d3fb21cc1906403'
SCOPE = 'COMPLETE_MENU_TERMINAL_PROFIT_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1'
EPS = 1e-8


def context():
    require(hashlib.sha256(source.REPORT.read_bytes()).hexdigest() == MINIMUM_SHA, 'Original minimum byte pin differs')
    original = source.context()
    minimum = read(source.REPORT)
    checked = source.check_values(read(source.REQUEST), minimum, original)
    return original[0], minimum, checked


def canonical(request):
    request = copy.deepcopy(request)
    p = request['minimumRequest']['sourceRequest']['envelope']['parent']
    p['input'] = source.source.physical.canonical_input(p['input']); p['flop'].sort()
    return request


def moves(minimum, original, limit):
    require(minimum['completeMinimum'], 'Complete minimum required')
    old = original['moves']
    require([m['action'] for m in old] == ['fold', 'call', 'raise:9.0'], 'Complete original menu required')
    lower = {m['action']: max(m['lowerEvBb'], minimum['minimum']['minimum']['lowerBound'])
             if m['action'] == minimum['request']['action'] else m['lowerEvBb'] for m in old}
    upper = {m['action']: m['upperEvBb'] for m in old}
    result = []
    for m in old:
        action = m['action']; require(lower[action] <= upper[action], 'Conflicting outer bounds')
        lo = max([0.] + [v-upper[action] for v in lower.values()])
        hi = max([0.] + [v-lower[action] for v in upper.values()])
        result.append(dict(action=action, lowerEvBb=lower[action], upperEvBb=upper[action], lowerLossBb=lo,
                           upperLossBb=hi, classification=source.feedback.classify(lo, hi, limit)))
    return result


def assess(policy, menu):
    original = policy['original']; failures = set(policy['failures'])
    if not policy['minimumLowerSupported']: failures.add('MINIMUM_NOT_SUPPORTED')
    keys = {m['action'] for m in menu}
    require(len(menu) == len(keys) == 3 and keys == set(original['actionUtilitiesBb']) == set(original['actionLossBb']), 'Complete policy menu required')
    violation = None
    if original['insideDeclaredFace']:
        violation = max([0.] + [v for m in menu for v in (
            m['lowerEvBb']-original['actionUtilitiesBb'][m['action']], original['actionUtilitiesBb'][m['action']]-m['upperEvBb'],
            m['lowerLossBb']-original['actionLossBb'][m['action']], original['actionLossBb'][m['action']]-m['upperLossBb'])])
        if violation > EPS: failures.add('REFERENCE_OUTSIDE_MINIMUM_MENU_BOUNDS')
    else: failures.add('MINIMUM_MENU_REFERENCE_OUTSIDE_FACE')
    return dict(iterations=original['iterations'], solutionHash=original['solutionHash'], boundsChecked=violation is not None,
                maximumBoundViolationBb=violation, failures=sorted(failures))


def check_values(request, report, verified=None):
    original, minimum, source_check = verified if verified is not None else context()
    request = canonical(request)
    require(set(request) == {'schemaVersion', 'minimumRequest', 'minimumStoredSha256', 'maximumLossBb'}
            and request['schemaVersion'] == 'pokerlab-root-minimum-feedback-request/v1'
            and request['minimumRequest'] == minimum['request'] and request['minimumStoredSha256'] == MINIMUM_SHA
            and request['maximumLossBb'] == .01, 'Original minimum request and threshold required')
    require(set(report) == {'schemaVersion', 'publicationStatus', 'trainerAdmission', 'boundsScope', 'request', 'originalEnvelope',
                           'sourceMinimum', 'moves', 'policies', 'qualifiedForOfflineDrill', 'rejectionReasons'}
            and report['schemaVersion'] == 'pokerlab-root-minimum-feedback-report/v1'
            and report['publicationStatus'] == 'MINIMUM_INFORMED_ROOT_FEEDBACK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['boundsScope'] == SCOPE and report['request'] == request
            and report['originalEnvelope'] == original and report['sourceMinimum'] == minimum, 'Owned source, scope or report identity differs')
    expected = moves(minimum, original, request['maximumLossBb'])
    require(len(report['moves']) == len(expected) == 3, 'Complete three-move menu required')
    for actual, wanted in zip(report['moves'], expected, strict=True):
        require(set(actual) == set(wanted) and actual['action'] == wanted['action'] and actual['classification'] == wanted['classification'], 'Move identity/classification differs')
        for field in ['lowerEvBb', 'upperEvBb', 'lowerLossBb', 'upperLossBb']: close(actual[field], wanted[field])
    policies = [assess(p, expected) for p in minimum['policies']]
    require(len(report['policies']) == len(policies) == 3, 'Primary and both fresh references required')
    for actual, wanted in zip(report['policies'], policies, strict=True):
        require(set(actual) == set(wanted) and actual['iterations'] == wanted['iterations'] and actual['solutionHash'] == wanted['solutionHash']
                and actual['boundsChecked'] is wanted['boundsChecked'] and actual['failures'] == wanted['failures'], 'Whole-menu policy check differs')
        if wanted['maximumBoundViolationBb'] is None: require(actual['maximumBoundViolationBb'] is None, 'Outside-face policy cannot claim checked bounds')
        else: close(actual['maximumBoundViolationBb'], wanted['maximumBoundViolationBb'])
    reasons = set()
    if not minimum['referenceSupportedMinimum']: reasons.add('MINIMUM_NOT_REFERENCE_SUPPORTED')
    if not any(m['classification'] == 'WITHIN_LIMIT' for m in expected): reasons.add('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification'] == 'OUTSIDE_LIMIT' for m in expected): reasons.add('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for p in policies: reasons.update(('PRIMARY' if p['iterations'] == 1 else str(p['iterations']))+':'+f for f in p['failures'])
    require(report['rejectionReasons'] == sorted(reasons) and report['qualifiedForOfflineDrill'] is (not reasons), 'Offline qualification outcome differs')
    return dict(schemaVersion='pokerlab-root-minimum-feedback-independent-check/v1', trainerAdmission=False,
                sourceMinimumCheck=source_check, movesChecked=3, policyMovesChecked=9, utilityAndLossBoundsChecked=36,
                qualifiedForOfflineDrill=not reasons, rejectionReasons=sorted(reasons), moves=expected,
                minimumUpperUsedAsActionCeiling=False, currentDecisionEvShiftApplied=False, freshLpSolves=0, freshCfrRuns=0)


def check():
    result = check_values(read(REQUEST), read(REPORT))
    result.update(checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(), reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),
                  immutableHelperSha256={p: hashlib.sha256((ROOT/'scripts'/p).read_bytes()).hexdigest() for p in
                    ['check-root-minimum.py', 'check-root-envelope-feedback.py', 'check-root-security-envelope.py', 'check-root-minimax.py', 'check-resolvable-refined-lp.py']})
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--receipt', type=Path); args = parser.parse_args()
    data = (json.dumps(check(), indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output: output.write(data)
    print(data.decode())
