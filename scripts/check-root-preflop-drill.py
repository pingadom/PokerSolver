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
spec=importlib.util.spec_from_file_location('root_cache_source',ROOT/'scripts/check-root-envelope-feedback.py')
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require,read=feedback.require,feedback.read
DATA=ROOT/'docs/data'
PIN='cc20afe1dee2584956fba058a4cbac5e421886b6bc98e91ff2c602666173a658'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-root-preflop-drill-manifest.json'
FIXTURE=DATA/'supplied-root-preflop-drill-fixture.json'
REVIEW=DATA/'supplied-root-preflop-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=2,maximumParentLpSolves=4,maximumEnvelopeLpSolves=5,freshReferenceRuns=2,freshIterations=240000)


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
    require(manifest==dict(schemaVersion='pokerlab-root-preflop-drill-manifest/v1',studyHash=PIN,
        requestFile='supplied-root-envelope-feedback-request.json',requestSha256='2503e36c63d7026b1d53c5b49c91cd3572de0fe4e23c4f3cc048b0a6cc82c8a6',
        reportFile='supplied-root-envelope-feedback-report.json.gz',reportStoredSha256='e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d',startupBudget=BUDGET),'Published source manifest differs')
    require(sha(DATA/manifest['requestFile'])==manifest['requestSha256'] and sha(DATA/manifest['reportFile'])==manifest['reportStoredSha256'],'Original stored source bytes differ')
    source=read(DATA/manifest['reportFile']);source_receipt=feedback.check_values(read(DATA/manifest['requestFile']),source)
    require(source_receipt['qualifiedForOfflineDrill'] and source['rejectionReasons']==[],'Qualified source required')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-root-preflop-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],trainerAdmission=False,
                  model=source['envelope']['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,availableQuestions=1,cachedLegalGrades=3,maximumAttempts=128,startupBudget=BUDGET)
    require(fixture['metadata']==metadata,'Public metadata or EV scope differs')
    context=source['envelope']['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
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
    return dict(schemaVersion='pokerlab-root-preflop-drill-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
                canonicalJavaSourcePinVerifiedByOwnedReplay=PIN,publicQuestionsChecked=1,cachedGradesChecked=3,evScope=SCOPE,
                emptyPreflopBoard=True,hiddenDealsAndFutureBoardAbsent=True,canonicalReviewAttemptsChecked=3,sourceCommitmentShiftBb=commit,freshLpSolves=0,freshCfrRuns=0)


def check():
    result=check_values(read(MANIFEST),read(FIXTURE),REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),sourceStoredSha256={p.name:sha(p) for p in [MANIFEST,FIXTURE,REVIEW]},
                  immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in ['check-root-envelope-feedback.py','check-root-security-envelope.py','check-flop-size-menu.py','check-bb-sizing-physics.py','check-big-blind-flop-discovery.py','check-supplied-flop-action-bounds.py']})
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(data)
    print(data.decode())
