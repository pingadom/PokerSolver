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
spec=importlib.util.spec_from_file_location('sevens_cache_source',ROOT/'scripts/check-sevens-root-study.py')
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
require,read=feedback.require,feedback.read
IMMUTABLE_HELPERS={'check-sevens-root-study.py': 'f1160705e71ecadd48fe6c9fb76624a0608f3f3038ce22bf80ed2c68faaeb089', 'check-root-envelope-feedback.py': 'e0e7d3e803fe6d2de99b893ccda9e0e3ce71b93b74580be398c3c6ac03fdcf48', 'check-root-security-envelope.py': 'dd871cc2d9394a52263a2f8945d5713e2f9e0a8a21fde0be0ead0bc60c28a0b1', 'check-flop-size-menu.py': '054c6c2db950244559d6274f1ba4bb15e0c0aee53ec7c1b04cb132606de3569a', 'check-bb-sizing-physics.py': 'c8344e028a326691e841d960b7ae7b0604e14372cbc5ae2246b58ef4b6804d65', 'check-big-blind-flop-discovery.py': 'fadffe2ae7deb39a57978a15109245641ed23c8bd6a348ce2730c0f2be8bac60', 'check-supplied-flop-action-bounds.py': 'c545845504dd0fc345095477aff80d0ac5b45aec71bf71a4e5bda670bdda714e'}
for name,value in IMMUTABLE_HELPERS.items():
    require(hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest()==value,'Immutable projection helper changed: '+name)
DATA=ROOT/'docs/data'
PIN='5f52b053c7610adbfb4abd2cfb210674b4a325d8b870cd350edc22d192708da6'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-sevens-preflop-drill-manifest.json'
FIXTURE=DATA/'supplied-sevens-preflop-drill-fixture.json'
REVIEW=DATA/'supplied-sevens-preflop-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=2,maximumParentLpSolves=4,maximumEnvelopeLpSolves=5,freshReferenceRuns=2,freshIterations=240000)


def exact(actual,expected,message):
    require(feedback.equal(actual,expected),message)


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


INTEGER_FIELDS=set(BUDGET)|{'tableSeats','activePlayers','availableQuestions','cachedLegalGrades',
    'maximumAttempts','sourceCaseIndex','ordinal','distinctQuestions','withinLimit','outsideLimit','strategyDependent'}


def protocol_integers(value):
    if isinstance(value,dict):
        for key,item in value.items():
            if key in INTEGER_FIELDS:require(type(item) is int,'Integral protocol field required: '+key)
            protocol_integers(item)
    elif isinstance(value,list):
        for item in value:protocol_integers(item)


def source_context():
    source=read(feedback.REPORT)
    return source,feedback.check_values(read(feedback.REQUEST),source)


def check_values(manifest,fixture,export,verified=None):
    expected={'schemaVersion': 'pokerlab-sevens-preflop-drill-manifest/v1', 'studyHash': '5f52b053c7610adbfb4abd2cfb210674b4a325d8b870cd350edc22d192708da6', 'requestFile': 'supplied-sevens-root-study-request.json', 'requestSha256': '9c5c26307922844b09b61cf34510b0e31afeb2c38a9e78a98e70f334828c0049', 'reportFile': 'supplied-sevens-root-study-report.json.gz', 'reportStoredSha256': 'f6320a28164f3e6f8e4ae78b019d2933bccda5dc9f9cf212f0c7ae6aff59b3c3', 'parentFile': 'supplied-flop-size-menu-report.json.gz', 'parentStoredSha256': '14cccdffafbb70dbfc54e6ff4467881cbf8d612891277cfcdcb38cab0ca373bf', 'startupBudget': {'physicalGames': 1, 'maximumParentSolveCalls': 2, 'maximumParentLpSolves': 4, 'maximumEnvelopeLpSolves': 5, 'freshReferenceRuns': 2, 'freshIterations': 240000}}
    require(feedback.equal(manifest,expected),'Published sevens source manifest differs')
    for name,field in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),('parentFile','parentStoredSha256')]:
        require(sha(DATA/manifest[name])==manifest[field],'Approved stored source bytes differ')
    protocol_integers(manifest)
    protocol_integers(fixture)
    source,source_receipt=verified if verified is not None else source_context()
    require(source_receipt['feedbackSupported'] and source['rejectionReasons']==[],'Qualified source required')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-sevens-preflop-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],trainerAdmission=False,
                  model=source['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,availableQuestions=1,cachedLegalGrades=3,maximumAttempts=128,startupBudget=BUDGET)
    exact(fixture['metadata'],metadata,'Public metadata or EV scope differs')
    context=source['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
    require(commit==1 and context['actingSeat']=='BB','Original acting commitment differs')
    moves=copy.deepcopy(source['moves'])
    for move in moves:move['lowerEvBb']+=commit;move['upperEvBb']+=commit
    question=dict(sourceCaseIndex=0,heroCards='7d 7h',board=[],context=context,evScope=SCOPE,moves=moves)
    exact(fixture['questions'],[question],'Public preflop projection differs or hidden information leaked')
    grades=[dict(studyHash=PIN,sourceCaseIndex=0,evScope=SCOPE,move=m) for m in moves]
    exact(fixture['grades'],grades,'Cached grade, shifted EV, loss or ambiguity differs')
    choices=[dict(sourceCaseIndex=0,action=m['action']) for m in moves]
    expected=review(choices,grades);exact(fixture['review'],expected,'Canonical review values/order/counts differ')
    require(export==canonical(expected)+'\n' and '\r' not in export,'Canonical LF export bytes differ')
    require([g['move']['classification'] for g in grades]==['OUTSIDE_LIMIT','WITHIN_LIMIT','STRATEGY_DEPENDENT'],'Raise ambiguity lost')
    return dict(schemaVersion='pokerlab-sevens-preflop-drill-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
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
