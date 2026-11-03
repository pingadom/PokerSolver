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
spec=importlib.util.spec_from_file_location('root_cache_source',ROOT/'scripts/check-root-minimum-feedback.py')
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require,read=feedback.require,feedback.read
DATA=ROOT/'docs/data'
PIN='baa6420476438abfca0c508d19ca313d8f9dc78f643293df9b2866418f15bf6d'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-minimum-preflop-drill-manifest.json'
FIXTURE=DATA/'supplied-minimum-preflop-drill-fixture.json'
REVIEW=DATA/'supplied-minimum-preflop-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=2,maximumParentLpSolves=4,maximumMinimumLpSolves=1,freshReferenceRuns=2,freshIterations=240000)


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


def check_values(manifest,fixture,export):
    expected=dict(schemaVersion='pokerlab-minimum-preflop-drill-manifest/v1',studyHash=PIN,startupBudget=BUDGET,
        requestFile='supplied-root-minimum-feedback-request.json',requestSha256='9dc93d4fab85ec8bd50b012b19debad9ec2cd8c601e077b65425256963533897',
        reportFile='supplied-root-minimum-feedback-report.json.gz',reportStoredSha256='94ed219424afe003d38893696f37d0bf5aa64aaf73620b66f470319d5593902e',
        envelopeFile='supplied-root-envelope-feedback-report.json.gz',envelopeStoredSha256='e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d',
        minimumFile='supplied-root-minimum-report.json.gz',minimumStoredSha256='ea48889cd6677102c917d1990e6b14dc576a12dad75bc7a86d3fb21cc1906403')
    require(manifest==expected,'Published minimum source manifest differs')
    for name,pin in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),('envelopeFile','envelopeStoredSha256'),('minimumFile','minimumStoredSha256')]:
        require(sha(DATA/manifest[name])==manifest[pin],'Approved stored source bytes differ')
    source=read(DATA/manifest['reportFile']);source_receipt=feedback.check_values(read(DATA/manifest['requestFile']),source)
    require(source_receipt['qualifiedForOfflineDrill'] and source['rejectionReasons']==[],'Qualified source required')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-minimum-preflop-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],trainerAdmission=False,
                  model=source['sourceMinimum']['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,availableQuestions=1,cachedLegalGrades=3,maximumAttempts=128,startupBudget=BUDGET)
    require(fixture['metadata']==metadata,'Public metadata or EV scope differs')
    context=source['sourceMinimum']['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
    require(commit==1 and context['actingSeat']=='BB','Original acting commitment differs')
    moves=copy.deepcopy(source['moves'])
    for move in moves:move['lowerEvBb']+=commit;move['upperEvBb']+=commit
    question=dict(sourceCaseIndex=0,heroCards='9d 9h',board=[],context=context,evScope=SCOPE,moves=moves)
    require(fixture['questions']==[question],'Public preflop projection differs or hidden information leaked')
    grades=[dict(studyHash=PIN,sourceCaseIndex=0,evScope=SCOPE,move=m) for m in moves]
    require(fixture['grades']==grades,'Cached grade, shifted EV, loss or ambiguity differs')
    choices=[dict(sourceCaseIndex=0,action=m['action']) for m in moves]
    expected=review(choices,grades);require(fixture['review']==expected,'Canonical review values/order/counts differ')
    require(export==canonical(expected)+'\n' and '\r' not in export,'Canonical LF export bytes differ')
    require([g['move']['classification'] for g in grades]==['OUTSIDE_LIMIT','WITHIN_LIMIT','STRATEGY_DEPENDENT'],'Raise ambiguity lost')
    return dict(schemaVersion='pokerlab-minimum-preflop-drill-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
                canonicalJavaSourcePinVerifiedByOwnedReplay=PIN,publicQuestionsChecked=1,cachedGradesChecked=3,evScope=SCOPE,
                emptyPreflopBoard=True,hiddenDealsAndFutureBoardAbsent=True,canonicalReviewAttemptsChecked=3,sourceCommitmentShiftBb=commit,freshLpSolves=0,freshCfrRuns=0)


def check():
    result=check_values(read(MANIFEST),read(FIXTURE),REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),sourceStoredSha256={p.name:sha(p) for p in [MANIFEST,FIXTURE,REVIEW]},
                  immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in ['check-root-minimum-feedback.py','check-root-security-envelope.py','check-flop-size-menu.py','check-bb-sizing-physics.py','check-big-blind-flop-discovery.py','check-supplied-flop-action-bounds.py']})
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(data)
    print(data.decode())
