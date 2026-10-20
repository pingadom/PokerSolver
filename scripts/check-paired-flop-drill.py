"""Independently check four fixed public BTN drills after all 32 source certificates; no optimizer."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-paired-flop-curriculum.py'
spec = importlib.util.spec_from_file_location('paired_drill_curriculum', HELPER)
source = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = source
spec.loader.exec_module(source)
require = source.require
SCHEMA = 'pokerlab-paired-four-hand-flop-drill/v1'
STATUS = 'FOUR_PAIRED_BTN_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY'
SELECTION = 'ALL_FOUR_SUPPLIED_PAIRED_BTN_HANDS_FACING_BET/v1'
MAX_BYTES = 256 * 1024


def exact(actual, expected):
    """Keep bool/integer distinctions, exact values and complete public field inventories."""
    if isinstance(expected, dict):
        return isinstance(actual, dict) and actual.keys() == expected.keys() and all(exact(actual[k], v) for k, v in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and len(actual) == len(expected) and all(exact(a, b) for a, b in zip(actual, expected, strict=True))
    if type(expected) is float:
        return type(actual) in (int, float) and actual == expected
    return type(actual) is type(expected) and actual == expected


def verify_review(report, review):
    require(report['qualifiedForOfflineDrill'] is True and not report['rejectionReasons'], 'Qualified four-hand source required')
    questions = [dict(reportHash=source.REPORT_HASH, questionIndex=i, publicationStatus=STATUS,
                      trainerAdmission=False, selection=SELECTION, informationSet=d['conditional']['informationSet'],
                      hero='BTN', heroCards=d['conditional']['request']['heroCards'], context=d['conditional']['context'],
                      model='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1',
                      chanceModel='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1',
                      beliefs='EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1', sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY',
                      maximumLossBb=report['request']['maximumLossBb'], securitySlackBb=1e-8, minimumReach=.0001,
                      reachUnits=report['decisions'][0]['conditional']['reachUnits'], evScope=report['decisions'][0]['conditional']['evScope']) for i, d in enumerate(report['decisions'])]
    require(isinstance(review, dict) and set(review) == {'schemaVersion', 'reportHash', 'selection', 'questions', 'attempts', 'counts'}
            and review['schemaVersion'] == SCHEMA and review['reportHash'] == source.REPORT_HASH
            and review['selection'] == SELECTION and exact(review['questions'], questions), 'Exact four-question BTN inventory differs')
    attempts = review['attempts']
    require(isinstance(attempts, list) and len(attempts) <= 100, 'BTN drill attempt cap differs')
    counts = dict(distinctQuestionsAttempted=0, withinLimit=0, outsideLimit=0, strategyDependent=0)
    names = dict(WITHIN_LIMIT='withinLimit', OUTSIDE_LIMIT='outsideLimit', STRATEGY_DEPENDENT='strategyDependent')
    seen = set()
    for index, attempt in enumerate(attempts):
        require(isinstance(attempt, dict) and set(attempt) == {'index', 'questionIndex', 'action', 'feedback'}
                and type(attempt['index']) is int and attempt['index'] == index
                and type(attempt['questionIndex']) is int and 0 <= attempt['questionIndex'] < 4,
                'BTN attempt identity or order differs')
        qi = attempt['questionIndex']
        moves = report['decisions'][qi]['moves']
        selected = [move for move in moves if move['action'] == attempt['action']]
        require(len(selected) == 1, 'Illegal BTN question action')
        feedback = dict(reportHash=source.REPORT_HASH, questionIndex=qi, publicationStatus=STATUS,
                        trainerAdmission=False, evScope=report['decisions'][0]['conditional']['evScope'], maximumLossBb=report['request']['maximumLossBb'],
                        selected=selected[0], alternatives=moves)
        require(exact(attempt['feedback'], feedback), 'Exact cached BTN interval feedback differs')
        seen.add(qi)
        counts[names[selected[0]['classification']]] += 1
    counts['distinctQuestionsAttempted'] = len(seen)
    require(exact(review['counts'], counts), 'BTN repeated-attempt counts differ')
    return len(attempts)


def read_review(path):
    with path.open('rb') as stream:
        raw = stream.read(MAX_BYTES + 1)
    require(len(raw) <= MAX_BYTES, 'Stored BTN review exceeds cap')
    if path.suffix == '.gz':
        with gzip.open(path, 'rb') as stream:
            expanded = stream.read(MAX_BYTES + 1)
    else:
        expanded = raw
    require(len(expanded) <= MAX_BYTES, 'Expanded BTN review exceeds cap')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate BTN JSON key')
            result[key] = value
        return result
    def invalid_number(value):
        raise ValueError('Nonfinite BTN JSON number: ' + value)
    return raw, expanded, json.loads(expanded, object_pairs_hook=unique, parse_constant=invalid_number)


def check(request, report, review):
    receipt = source.check(request, report)
    raw, expanded, saved = read_review(review)
    qualified, _, _ = source.read(report)
    attempts = verify_review(qualified, saved)
    return dict(schemaVersion='pokerlab-independent-paired-flop-drill/v1', trainerAdmission=False,
                fixedCaseOnly=True, freshLpSolves=0, freshCfrRuns=0,
                originalEndpointCertificatesChecked=receipt['originalEndpointCertificatesChecked'],
                sourcePolicyContextChecks=receipt['policyContextChecks'], sourceQuestionsChecked=4,
                reportHash=source.REPORT_HASH, attemptsChecked=attempts,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                sourceReceipt=receipt, reviewStoredBytesSha256=hashlib.sha256(raw).hexdigest(),
                reviewExpandedBytesSha256=hashlib.sha256(expanded).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request', type=Path)
    parser.add_argument('report', type=Path)
    parser.add_argument('review', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.output:
        require(args.output.resolve() not in {args.request.resolve(), args.report.resolve(), args.review.resolve()}, 'Receipt must be distinct')
    text = json.dumps(check(args.request, args.report, args.review), indent=2) + '\n'
    if args.output:
        with args.output.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(text)
    print(text, end='')


if __name__ == '__main__':
    main()
