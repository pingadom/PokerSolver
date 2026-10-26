"""Independently verify zero-diagonal conditional regret over the complete owned BB source.

No optimization. Old marginal-loss reports remain immutable; two candidates are not lessons.
"""
import argparse
from fractions import Fraction
import hashlib
import importlib.util
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bb_pairwise_source',ROOT/'scripts/check-big-blind-flop-discovery.py')
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
require, read = source.require, source.read
DATA = ROOT/'docs/data'
REPORT = DATA/'supplied-unpaired-bb-pairwise-study.json.gz'
BUDGET = DATA/'big-blind-pairwise-study-generation-budget.json'
ALGORITHM = 'OTHER_ACTION_INTERVAL_DIFFERENCES_ZERO_DIAGONAL_OUTWARD_ROUNDED/v1'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def moves(conditional):
    actions = conditional['actions']
    require(len(actions) >= 2 and len({a['action'] for a in actions}) == len(actions),'Distinct pairwise menu required')
    result = []
    for selected in actions:
        others = [a for a in actions if a['action'] != selected['action']]
        lower = max([0.] + [math.nextafter(a['lowerUtilityBb']-selected['upperUtilityBb'],-math.inf) for a in others])
        upper = max([0.] + [math.nextafter(a['upperUtilityBb']-selected['lowerUtilityBb'],math.inf) for a in others])
        exact_lower = max([Fraction(0)] + [Fraction(a['lowerUtilityBb'])-Fraction(selected['upperUtilityBb']) for a in others])
        exact_upper = max([Fraction(0)] + [Fraction(a['upperUtilityBb'])-Fraction(selected['lowerUtilityBb']) for a in others])
        require(Fraction(lower) <= exact_lower <= exact_upper <= Fraction(upper),'Original binary64 difference not bounded outwards')
        classification = 'WITHIN_LIMIT' if upper <= .01 else 'OUTSIDE_LIMIT' if lower > .01 else 'STRATEGY_DEPENDENT'
        result.append(dict(action=selected['action'],lowerEvBb=selected['lowerUtilityBb'],upperEvBb=selected['upperUtilityBb'],
            lowerLossBb=lower,upperLossBb=upper,classification=classification))
    return result


def check_report(report, coverage):
    require(set(report) == {'schemaVersion','publicationStatus','trainerAdmission','lossAlgorithm','source','cases','candidateIndices'}
        and report['schemaVersion'] == 'pokerlab-big-blind-pairwise-regret-study/v1'
        and report['publicationStatus'] == 'PAIRWISE_REGRET_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and report['trainerAdmission'] is False and report['lossAlgorithm'] == ALGORITHM
        and report['source'] == coverage and len(report['cases']) == len(coverage['cases']) == 6,'Complete pairwise source identity differs')
    candidates = []
    for i,(item,original) in enumerate(zip(report['cases'],coverage['cases'],strict=True)):
        expected = moves(original['conditional']) if original['conditional'] is not None else []
        separated = {'WITHIN_LIMIT','OUTSIDE_LIMIT'} <= {m['classification'] for m in expected}
        require(item == dict(index=i,moves=expected,actionSeparated=separated),'Pairwise move, context or complete inventory differs')
        if separated:
            candidates.append(i)
    require(report['candidateIndices'] == candidates == [1,3],'Complete candidate list differs')
    for i in candidates:
        require(coverage['cases'][i]['request']['hero'] == 5
            and coverage['cases'][i]['request']['flopActions'] == ['check','bet']
            and report['cases'][i]['moves'][1]['upperLossBb'] == 0,'Dominant call or BB context differs')
    return candidates


def check(report_path=REPORT, *, verify_source=True, bind_saved_bytes=True):
    source_receipt = source.check() if verify_source else None
    report,raw,expanded = read(report_path)
    coverage,_,_ = read(source.REPORT)
    budget,_,_ = read(BUDGET)
    require(budget['schemaVersion'] == 'pokerlab-big-blind-pairwise-study-generation-budget/v1'
        and budget['sourceRequestSha256'] == digest(source.REQUEST)
        and budget['frozenCoverageReportSha256'] == digest(source.REPORT)
        and budget['lossAlgorithm'] == ALGORITHM and budget['trainerAdmission'] is False
        and budget['physicalGameBuilds'] == 1 and budget['plannedCaseAttempts'] == 6
        and budget['maximumActionComputations'] == 12 and budget['maximumParentSolves'] == 18
        and budget['freshReferenceRuns'] == budget['automaticRetries'] == 0
        and budget['maximumExpandedBytesPerReport'] == 8388608 and budget['maximumLossBb'] == .01
        and budget['originalNumericAndWorkCapsPreserved'] is True,'Predeclared pairwise bounds differ')
    candidates = check_report(report,coverage)
    if bind_saved_bytes:
        pins,_,_ = read(DATA/'big-blind-pairwise-study-pins.json')
        require(pins == dict(storedSha256=hashlib.sha256(raw).hexdigest(),expandedSha256=hashlib.sha256(expanded).hexdigest()),'Frozen pairwise bytes differ')
    return dict(schemaVersion='pokerlab-independent-big-blind-pairwise-study/v1',trainerAdmission=False,
        freshLpSolves=0,freshCfrRuns=0,contextsChecked=6,actionsChecked=12,candidateIndices=candidates,
        qualifiedTrainerQuestions=0,exactBinary64OutwardDifferenceChecks=True,sourceCheck=source_receipt,
        checkerSourceSha256=digest(Path(__file__)),reportStoredSha256=hashlib.sha256(raw).hexdigest(),
        reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),generationBudgetSha256=digest(BUDGET))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    require(not args.output.exists(),'Receipt must be new')
    result = check()
    with args.output.open('x',encoding='utf-8',newline='\n') as stream:
        json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps(result,indent=2))
