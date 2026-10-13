"""Independently check the fixed later BB report and its exact offline drill; no optimizer."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-later-bb-flop-feedback.py'
spec = importlib.util.spec_from_file_location('later_bb_drill_feedback', HELPER)
feedback = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = feedback
spec.loader.exec_module(feedback)
require = feedback.require
SCHEMA = 'pokerlab-later-bb-flop-drill/v1'
STATUS = 'LATER_BB_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY'
SELECTION = 'SINGLE_QUALIFIED_LATER_BB_FLOP_QUESTION/v1'
MAX_BYTES = 256 * 1024


def verify_review(report, review):
    require(report['qualifiedForOfflineDrill'] and not report['rejectionReasons'], 'Qualified later report required')
    question = dict(reportHash=feedback.REPORT_HASH, publicationStatus=STATUS, trainerAdmission=False,
                    selection=SELECTION, informationSet=feedback.KEY, hero='BB', heroCards='7d 7h',
                    context=report['context'], model='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1',
                    chanceModel='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1',
                    beliefs='EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1', sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY',
                    maximumLossBb=.01, securitySlackBb=1e-8, minimumReach=.0001,
                    reachUnits=report['reachUnits'], evScope=report['evScope'])
    require(set(review) == {'schemaVersion', 'question', 'attempts', 'counts'}
            and review['schemaVersion'] == SCHEMA and review['question'] == question,
            'Exact later drill question differs')
    attempts = review['attempts']
    require(isinstance(attempts, list) and len(attempts) <= 100, 'Later drill attempt cap differs')
    counts = dict(withinLimit=0, outsideLimit=0, strategyDependent=0)
    count_keys = dict(WITHIN_LIMIT='withinLimit', OUTSIDE_LIMIT='outsideLimit', STRATEGY_DEPENDENT='strategyDependent')
    for index, attempt in enumerate(attempts):
        require(isinstance(attempt, dict) and set(attempt) == {'index', 'action', 'feedback'}, 'Later attempt fields differ')
        matches = [move for move in report['moves'] if move['action'] == attempt['action']]
        require(len(matches) == 1, 'Illegal later drill action')
        selected = matches[0]
        expected = dict(reportHash=feedback.REPORT_HASH, publicationStatus=STATUS, trainerAdmission=False,
                        evScope=report['evScope'], maximumLossBb=.01, selected=selected, alternatives=report['moves'])
        require(type(attempt['index']) is int and attempt['index'] == index, 'Later attempt index differs')
        require(attempt['feedback'] == expected, 'Exact later interval feedback differs')
        counts[count_keys[selected['classification']]] += 1
    require(review['counts'] == counts, 'Later repeated-attempt counts differ')
    return len(attempts)


def check(request, report, drill):
    receipt = feedback.check(request, report)
    raw, review = read_drill(drill)
    qualified, _, _ = feedback.read(report)
    attempts = verify_review(qualified, review)
    return dict(schemaVersion='pokerlab-independent-later-bb-flop-drill/v1', trainerAdmission=False,
                freshLpSolves=0, originalEndpointCertificatesChecked=receipt['originalEndpointCertificatesChecked'],
                policiesChecked=receipt['policiesChecked'], reportHash=feedback.REPORT_HASH, attemptsChecked=attempts,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                feedbackReceipt=receipt, drillStoredBytesSha256=hashlib.sha256(raw).hexdigest())


def read_drill(path):
    with path.open('rb') as stream:
        raw = stream.read(MAX_BYTES + 1)
    require(len(raw) <= MAX_BYTES, 'Stored later drill exceeds cap')
    if path.suffix == '.gz':
        with gzip.open(path, 'rb') as stream:
            expanded = stream.read(MAX_BYTES + 1)
    else:
        expanded = raw
    require(len(expanded) <= MAX_BYTES, 'Expanded later drill exceeds cap')
    return raw, json.loads(expanded)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request', type=Path)
    parser.add_argument('report', type=Path)
    parser.add_argument('drill', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.output:
        require(args.output.resolve() not in {args.request.resolve(), args.report.resolve(), args.drill.resolve()}, 'Receipt must be distinct')
    receipt = check(args.request, args.report, args.drill)
    text = json.dumps(receipt, indent=2) + '\n'
    if args.output:
        with args.output.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(text)
    print(text, end='')


if __name__ == '__main__':
    main()
