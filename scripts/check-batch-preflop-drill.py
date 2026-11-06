"""Independent public projection, all cached grades and canonical review; no optimizer/training."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('batch_cache_source',ROOT/'scripts/check-root-sequence-batch-feedback.py')
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require,read=feedback.require,feedback.read
IMMUTABLE_HELPERS={'check-root-sequence-batch-feedback.py': '8d88b48b438f8c6a59bf1f3ef4ef15c241ecf58840227bc0624caf133d5dba2c', 'check-physical-root-sequence-batch.py': 'f4ac141bd1d8debbd046818416713d11f7cabfc45ee41a2c353198110f0fbb04', 'check-root-sequence-feedback.py': '473ce94f287446c8cc9faff0f0c7687ca6152e9c13cb060ee8068f4c29a18f3c'}
for name,value in IMMUTABLE_HELPERS.items():
    require(hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest()==value,'Immutable projection helper changed: '+name)
DATA=ROOT/'docs/data'
PIN='a7533a49a06fe2b7376109bce133c1b3c937dbef8915ed47af86290d36b777c8'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-batch-preflop-drill-manifest.json'
FIXTURE=DATA/'supplied-batch-preflop-drill-fixture.json'
REVIEW=DATA/'supplied-batch-preflop-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=2,maximumParentLpSolves=4,maximumMinimumLpSolves=3,freshReferenceRuns=2,freshIterations=240000)


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical(value,level=0):
    """Jackson's existing sorted default-pretty layout for this frozen review protocol."""
    if isinstance(value,dict):
        if not value:return '{ }'
        return '{\n'+',\n'.join('  '*(level+1)+json.dumps(k)+' : '+canonical(v,level+1) for k,v in sorted(value.items()))+'\n'+'  '*level+'}'
    if isinstance(value,list):return '[ '+', '.join(canonical(v,level) for v in value)+' ]' if value else '[ ]'
    if isinstance(value,float):
        return re.sub(r'e([+-]?)(0*)(\d+)',lambda m:'E'+('-' if m[1]=='-' else '')+m[3],repr(value))
    return json.dumps(value,ensure_ascii=False,separators=(',',':'),allow_nan=False)


def review(choices,grades):
    require(len(choices)<=128,'Attempt cap exceeded')
    attempts=[];counts=dict(distinctQuestions=1 if choices else 0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    fields=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for choice in choices:
        require(set(choice)=={'sourceCaseIndex','action'} and type(choice['sourceCaseIndex']) is int and choice['sourceCaseIndex']==0,'Original choice identity required')
        matches=[g for g in grades if g['move']['action']==choice['action']]
        require(len(matches)==1,'Unknown cached choice')
        grade=matches[0];attempts.append(dict(ordinal=len(attempts),attempt=choice,grade=grade));counts[fields[grade['move']['classification']]]+=1
    return dict(studyHash=PIN,attempts=attempts,counts=counts)


def source_context():
    source=read(feedback.REPORT)
    return source,feedback.check_values(read(feedback.REQUEST),source)


def check_values(manifest,fixture,export,verified=None):
    expected={'schemaVersion': 'pokerlab-batch-preflop-drill-manifest/v1', 'studyHash': 'a7533a49a06fe2b7376109bce133c1b3c937dbef8915ed47af86290d36b777c8', 'startupBudget': {'physicalGames': 1, 'maximumParentSolveCalls': 2, 'maximumParentLpSolves': 4, 'maximumMinimumLpSolves': 3, 'freshReferenceRuns': 2, 'freshIterations': 240000}, 'requestFile': 'supplied-root-sequence-batch-feedback-request.json', 'requestSha256': '37cd3aa141d2eeb083d68dd586d9335b470f7e77717bc5dc7c466b55350684b0', 'reportFile': 'supplied-root-sequence-batch-feedback-report.json.gz', 'reportStoredSha256': 'd8f6897061acbba3e52643be206ca8e0e1927ed2577c2cd053b2cb02db5f4653', 'envelopeFile': 'supplied-root-envelope-feedback-report.json.gz', 'envelopeStoredSha256': 'e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d', 'minimumFile': 'supplied-root-sequence-minimum-report.json.gz', 'minimumStoredSha256': '1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f', 'batchFile': 'supplied-root-sequence-batch-report.json.gz', 'batchStoredSha256': 'be3e7400a75fd86bb617cf4e70f758e2a885d80ba052e6441f28d81c61a0dc8b', 'previousFeedbackFile': 'supplied-root-sequence-feedback-report.json.gz', 'previousFeedbackStoredSha256': '013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b'}
    feedback.exact(manifest,expected,'Published sequence source manifest differs')
    for name,field in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),
            ('envelopeFile','envelopeStoredSha256'),('minimumFile','minimumStoredSha256'),
            ('batchFile','batchStoredSha256'),('previousFeedbackFile','previousFeedbackStoredSha256')]:
        require(sha(DATA/manifest[name])==manifest[field],'Approved stored source bytes differ')
    source,source_receipt=verified if verified is not None else source_context()
    require(source_receipt['qualifiedForOfflineDrill'] and source['rejectionReasons']==[],'Qualified source required')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-batch-preflop-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],trainerAdmission=False,
                  model=source['sourceBatch']['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,availableQuestions=1,cachedLegalGrades=3,maximumAttempts=128,startupBudget=BUDGET)
    feedback.exact(fixture['metadata'],metadata,'Public metadata or EV scope differs')
    context=source['sourceBatch']['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
    require(commit==1 and context['actingSeat']=='BB','Original acting commitment differs')
    moves=copy.deepcopy(source['moves'])
    for move in moves:move['lowerEvBb']+=commit;move['upperEvBb']+=commit
    question=dict(sourceCaseIndex=0,heroCards='9d 9h',board=[],context=context,evScope=SCOPE,moves=moves)
    feedback.exact(fixture['questions'],[question],'Public preflop projection differs or hidden information leaked')
    grades=[dict(studyHash=PIN,sourceCaseIndex=0,evScope=SCOPE,move=m) for m in moves]
    feedback.exact(fixture['grades'],grades,'Cached grade, shifted EV, loss or ambiguity differs')
    choices=[dict(sourceCaseIndex=0,action=m['action']) for m in moves]
    expected=review(choices,grades);feedback.exact(fixture['review'],expected,'Canonical review values/order/counts differ')
    require(export==canonical(expected)+'\n' and '\r' not in export,'Canonical LF export bytes differ')
    require([g['move']['classification'] for g in grades]==['OUTSIDE_LIMIT','WITHIN_LIMIT','STRATEGY_DEPENDENT'],'Raise ambiguity lost')
    return dict(schemaVersion='pokerlab-batch-preflop-drill-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
                canonicalJavaSourcePinVerifiedByOwnedReplay=PIN,publicQuestionsChecked=1,cachedGradesChecked=3,evScope=SCOPE,
                emptyPreflopBoard=True,hiddenDealsAndFutureBoardAbsent=True,canonicalReviewAttemptsChecked=3,sourceCommitmentShiftBb=commit,freshLpSolves=0,freshCfrRuns=0)


def check():
    result=check_values(read(MANIFEST),read(FIXTURE),REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),sourceStoredSha256={p.name:sha(p) for p in [MANIFEST,FIXTURE,REVIEW]},
                  immutableHelperSha256=IMMUTABLE_HELPERS)
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(data)
    print(data.decode())
