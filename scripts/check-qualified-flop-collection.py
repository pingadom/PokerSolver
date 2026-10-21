"""Verify v4 by composing original physical proofs and reconstructing every question and grade."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'docs/data'


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    result = importlib.util.module_from_spec(spec)
    sys.modules[name] = result
    spec.loader.exec_module(result)
    return result


legacy = module('qualified_collection_legacy', 'check-expanded-flop-interval-collection.py')
weak = module('qualified_collection_weak', 'check-weak-hand-flop-feedback.py')
require, exact, read = legacy.require, legacy.exact, legacy.read
PIN = '57abbb5f5bbc10b2227739299c1eec0f0092a7a136dc80e63c0830865cb7fd87'
WEAK_PIN = '5dbd0cdeba8a74e6da32dae0bddeb69381a90ba4f72220ab4a9bf92bcc667c56'
SCHEMA = 'pokerlab-flop-interval-collection-review/v4'
SELECTION = 'ORDERED_LEGACY_TEN_THEN_QUALIFIED_WEAK_HAND/v4'
BELIEFS = 'INDEPENDENT_SOURCE_SPECIFIC_SUPPLIED_JOINT_PRIORS/v1'
KINDS = ['LEGACY_TEN_QUESTION_COLLECTION', 'QUALIFIED_WEAK_HAND_FLOP']
BUDGET = dict(sourceReplays=5, freshReferenceRuns=10, freshIterations=732000)


def sha(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def expected_manifest():
    # Normalize through the independently owned physical input parser, matching typed Java input.
    a = read(DATA / legacy.SOURCES[0]['requestFile'], 1024*1024)[2]['connected']['input']
    b = read(DATA / 'supplied-weak-hand-qualified-flop-request.json', 1024*1024)[2]['conditional']['parent']['connected']['input']
    a = weak.physical.canonical_input(a)
    b = weak.physical.canonical_input(b)
    identity = dict(schemaVersion=SCHEMA, selection=SELECTION, beliefs=BELIEFS,
                    legacyCollectionHash=legacy.PIN, legacyInputHash=sha(a), weakReportHash=WEAK_PIN, weakInputHash=sha(b))
    require(identity['legacyInputHash'] != identity['weakInputHash'] and sha(identity) == PIN,
            'Independent source range identities or collection hash differ')
    return dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v4', collectionHash=PIN,
                legacyManifestFile='supplied-ten-question-flop-collection-manifest.json', legacyCollectionHash=legacy.PIN,
                legacyInputHash=sha(a), weakRequestFile='supplied-weak-hand-qualified-flop-request.json',
                weakReportFile='supplied-weak-hand-qualified-flop-report.json.gz', weakReportHash=WEAK_PIN,
                weakInputHash=sha(b), startupBudget=BUDGET)


def verify_weak_drill(drill, report):
    source = report['conditional']
    q = source['request']
    question = dict(reportHash=WEAK_PIN, publicationStatus='REFINED_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY', trainerAdmission=False,
                    selection='SINGLE_QUALIFIED_REFINED_FLOP_QUESTION/v1', informationSet=source['informationSet'], hero='BTN',
                    heroCards=q['heroCards'], context=source['context'], model=source['parent']['binding']['model'],
                    chanceModel=source['parent']['binding']['chanceModel'], beliefs=source['parent']['binding']['sourceBinding']['beliefs'],
                    sourceHistoryReachStatus=source['parent']['binding']['sourceHistoryReachStatus'], maximumLossBb=.01,
                    securitySlackBb=1e-8, minimumReach=.0001, reachUnits=source['reachUnits'], evScope=source['evScope'])
    require(exact(drill['question'], question) and drill['schemaVersion'] == 'pokerlab-refined-flop-drill/v1', 'Weak canonical drill question differs')
    require(len(drill['attempts']) == 2, 'Both weak moves required')
    for i, move in enumerate(report['moves']):
        feedback = dict(reportHash=WEAK_PIN, publicationStatus=question['publicationStatus'], trainerAdmission=False,
                        evScope=source['evScope'], maximumLossBb=.01, selected=move, alternatives=report['moves'])
        require(exact(drill['attempts'][i], dict(index=i, action=move['action'], feedback=feedback)), 'Weak canonical grade differs')
    require(exact(drill['counts'], dict(withinLimit=1, outsideLimit=1, strategyDependent=0)), 'Weak counts differ')


def decision(index, legacy_value, weak_value):
    return dict(kind=KINDS[0 if index < 10 else 1], legacy=legacy_value if index < 10 else None,
                weak=weak_value if index == 10 else None)


def verify_review(review, old_review, weak_drill):
    questions = [dict(collectionHash=PIN, index=i, totalQuestions=11, selection=SELECTION,
                      sourceQuestionIndex=i if i < 10 else 0,
                      decision=decision(i, old_review['questions'][i] if i < 10 else None, weak_drill['question']))
                 for i in range(11)]
    require(isinstance(review, dict) and set(review) == {'schemaVersion', 'collectionHash', 'selection', 'questions', 'attempts', 'counts'}
            and review['schemaVersion'] == SCHEMA and review['collectionHash'] == PIN and review['selection'] == SELECTION
            and exact(review['questions'], questions), 'Exact eleven-question inventory differs')
    require(isinstance(review['attempts'], list) and len(review['attempts']) <= 100, 'Review attempt cap differs')
    counts = dict(distinctQuestionsAttempted=0, withinLimit=0, outsideLimit=0, strategyDependent=0)
    names = dict(WITHIN_LIMIT='withinLimit', OUTSIDE_LIMIT='outsideLimit', STRATEGY_DEPENDENT='strategyDependent')
    seen = set()
    for index, attempt in enumerate(review['attempts']):
        require(isinstance(attempt, dict) and set(attempt) == {'index', 'questionIndex', 'action', 'feedback'}
                and type(attempt['index']) is int and attempt['index'] == index and type(attempt['questionIndex']) is int
                and 0 <= attempt['questionIndex'] < 11, 'Attempt identity/order differs')
        qi = attempt['questionIndex']
        source_attempts = old_review['attempts'] if qi < 10 else weak_drill['attempts']
        matches = [a for a in source_attempts if a['action'] == attempt['action'] and (qi == 10 or a['questionIndex'] == qi)]
        require(len(matches) == 1, 'Illegal action or source question')
        grade = matches[0]['feedback']
        feedback = dict(collectionHash=PIN, questionIndex=qi, sourceQuestionIndex=qi if qi < 10 else 0,
                        decision=decision(qi, grade, grade))
        require(exact(attempt['feedback'], feedback), 'Canonical collection grade differs')
        inner = grade if qi == 10 else next(grade['decision'][key] for key in ('initial', 'later', 'btn', 'paired') if grade['decision'][key])
        counts[names[inner['selected']['classification']]] += 1
        seen.add(qi)
    counts['distinctQuestionsAttempted'] = len(seen)
    require(exact(review['counts'], counts), 'Distinct coverage/attempt counts differ')
    return len(review['attempts'])


def check(manifest_path, review_path):
    raw_manifest, _, manifest = read(manifest_path, 64*1024)
    require(exact(manifest, expected_manifest()), 'Fixed qualified manifest differs')
    budget = read(DATA / 'eleven-question-flop-collection-generation-budget.json', 64*1024)[2]
    require(budget['schemaVersion'] == 'pokerlab-qualified-flop-collection-generation-budget/v1'
            and budget['collectionHash'] == PIN and budget['manifestSha256'] == hashlib.sha256(raw_manifest).hexdigest()
            and budget['maximumGenerationInvocations'] == 1 and budget['sourceReplaysPerInvocation'] == 5
            and budget['freshReferenceRunsPerInvocation'] == 10 and budget['freshIterationsPerInvocation'] == 732000
            and budget['explicitReviewChoices'] == 22 and budget['maximumReviewAttempts'] == 100
            and budget['allOriginalSourceCapsAndTolerancesPreserved'] is True and budget['automaticNumericalRetries'] == 0,
            'Predeclared collection generation reservation differs')
    folder = manifest_path.resolve().parent
    old_review_path = DATA / 'supplied-ten-question-flop-collection-review.json'
    old_receipt = legacy.check(folder / manifest['legacyManifestFile'], old_review_path)
    weak_receipt = weak.check(folder / manifest['weakRequestFile'], folder / manifest['weakReportFile'])
    require(weak_receipt['qualifiedForOfflineDrill'] is True, 'Weak source did not qualify')
    weak_drill = read(DATA / 'supplied-weak-hand-qualified-flop-drill.json', 256*1024)[2]
    report = read(folder / manifest['weakReportFile'], 16*1024*1024)[2]
    verify_weak_drill(weak_drill, report)
    old_review = read(old_review_path, 1024*1024)[2]
    raw, _, review = read(review_path, 1024*1024)
    attempts = verify_review(review, old_review, weak_drill)
    return dict(schemaVersion='pokerlab-independent-qualified-flop-collection/v4', trainerAdmission=False, fixedCaseOnly=True,
                collectionHash=PIN, sourceQuestionsChecked=11, independentInputSets=2, startupBudget=BUDGET,
                attemptsChecked=attempts, freshLpSolves=0, freshCfrRuns=0, sourceReceipts=[old_receipt, weak_receipt],
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                manifestStoredSha256=hashlib.sha256(raw_manifest).hexdigest(), reviewStoredSha256=hashlib.sha256(raw).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('review', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Exclusive creation protects every existing input, including hard links and source files.
    require(not args.output.exists(), 'Receipt must be a new path')
    text = json.dumps(check(args.manifest, args.review), indent=2)+'\n'
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(text)
    print(text, end='')


if __name__ == '__main__':
    main()
