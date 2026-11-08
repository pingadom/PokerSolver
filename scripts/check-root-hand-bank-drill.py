"""Independently verify every hand/grade and mixed review against the original bank; no LP/CFR."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file)
    result=importlib.util.module_from_spec(spec);sys.modules[name]=result;spec.loader.exec_module(result);return result
bank=module('root_bank_cache_source','check-root-hand-bank.py')
protocol=module('root_bank_cache_protocol','check-sevens-preflop-drill.py')
require,read,exact=bank.require,bank.read,protocol.exact
DATA=ROOT/'docs/data'
PIN='e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d'
SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-root-hand-bank-drill-manifest.json'
FIXTURE=DATA/'supplied-root-hand-bank-drill-fixture.json'
REVIEW=DATA/'supplied-root-hand-bank-drill-review.json'
BUDGET=dict(physicalGames=1,maximumParentSolveCalls=4,maximumParentLpSolves=8,maximumEnvelopeLpSolves=15,freshReferenceRuns=2,freshIterations=240000)
IMMUTABLE_HELPERS={**protocol.IMMUTABLE_HELPERS,
 'check-root-hand-bank.py':'e2ffd071b195d26a44c0339958ac51eac57a8c89c2d54c89e7b0d7f2faff9893',
 'check-sevens-preflop-drill.py':'41994853f7294a15ec720ae4c550cdcc4eba39e504110d7ddaae830912051a45'}
# Freeze the established canonical JSON helper when preparing this new checker.

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def review(choices,grades):
    require(isinstance(choices,list) and len(choices)<=128,'Review attempt cap exceeded')
    attempts=[];distinct=set();counts=dict(distinctQuestions=0,withinLimit=0,outsideLimit=0,strategyDependent=0)
    fields=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for choice in choices:
        require(isinstance(choice,dict) and set(choice)=={'sourceCaseIndex','action'}
                and type(choice['sourceCaseIndex']) is int and 0<=choice['sourceCaseIndex']<3,'Original bank choice required')
        matches=[g for g in grades if g['sourceCaseIndex']==choice['sourceCaseIndex'] and g['move']['action']==choice['action']]
        require(len(matches)==1,'Unknown or duplicate cached choice')
        grade=matches[0];distinct.add(choice['sourceCaseIndex'])
        attempts.append(dict(ordinal=len(attempts),attempt=choice,grade=grade))
        counts[fields[grade['move']['classification']]]+=1
    counts['distinctQuestions']=len(distinct)
    return dict(studyHash=PIN,attempts=attempts,counts=counts)

def source_context():
    source=read(bank.REPORT)
    return source,bank.check_values(read(bank.REQUEST,128*1024),source)

def check_values(manifest,fixture,export,verified=None):
    bank.finite(manifest);bank.finite(fixture)
    expected=dict(schemaVersion='pokerlab-root-hand-bank-drill-manifest/v1',studyHash=PIN,
        requestFile='supplied-root-hand-bank-request.json',requestSha256='8eb03eb2dfd0d09e02be0a7b31f2b152db6a59c3b7c5a94a718ed50986b2d919',
        reportFile='supplied-root-hand-bank-report.json.gz',reportStoredSha256='eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd',
        parentFile='supplied-flop-size-menu-report.json.gz',parentStoredSha256='14cccdffafbb70dbfc54e6ff4467881cbf8d612891277cfcdcb38cab0ca373bf',startupBudget=BUDGET)
    exact(manifest,expected,'Exact original bank manifest and reservation required')
    for file,field in [('requestFile','requestSha256'),('reportFile','reportStoredSha256'),('parentFile','parentStoredSha256')]:
        require(sha(DATA/manifest[file])==manifest[field],'Original stored source bytes differ')
    protocol.protocol_integers(manifest);protocol.protocol_integers(fixture)
    source,receipt=verified if verified is not None else source_context()
    require(receipt['completeOuterBounds'] and receipt['supportedQuestionIndices']==[0,1,2]
            and len(source['assessments'])==3,'Every original hand must support complete feedback')
    require(set(fixture)=={'metadata','questions','grades','review'},'Public fixture fields differ')
    metadata=dict(schemaVersion='pokerlab-root-hand-bank-drill/v1',studyHash=PIN,publicationStatus=source['publicationStatus'],
        trainerAdmission=False,model=source['parent']['binding']['model'],evScope=SCOPE,tableSeats=6,activePlayers=2,
        availableQuestions=3,cachedLegalGrades=9,maximumAttempts=128,startupBudget=BUDGET)
    exact(fixture['metadata'],metadata,'Public metadata or original model scope differs')
    questions=[];grades=[]
    for i,assessment in enumerate(source['assessments']):
        require(assessment['questionIndex']==i and assessment['heroCards']==bank.HANDS[i]
                and assessment['feedbackSupported'] and assessment['rejectionReasons']==[],'Ordered complete hand inventory required')
        context=assessment['context'];commit=next(p['committedBb'] for p in context['players'] if p['seat']==context['actingSeat'])
        require(commit==1 and context['actingSeat']=='BB','Original hero commitment differs')
        moves=copy.deepcopy(assessment['moves'])
        for move in moves:move['lowerEvBb']+=commit;move['upperEvBb']+=commit
        questions.append(dict(sourceCaseIndex=i,heroCards=bank.HANDS[i],board=[],context=context,evScope=SCOPE,moves=moves))
        grades.extend(dict(studyHash=PIN,sourceCaseIndex=i,evScope=SCOPE,move=m) for m in moves)
    exact(fixture['questions'],questions,'Complete public hands, contexts or hidden-information boundary differs')
    exact(fixture['grades'],grades,'All nine original hand/action grades required')
    choices=[dict(sourceCaseIndex=g['sourceCaseIndex'],action=g['move']['action']) for g in grades]
    choices.extend([dict(sourceCaseIndex=2,action='call'),dict(sourceCaseIndex=0,action='raise:9.0')])
    expected=review(choices,grades)
    exact(fixture['review'],expected,'Mixed-hand review identities, order or distinct counts differ')
    require(export==protocol.canonical(expected)+'\n' and '\r' not in export,'Canonical UTF-8 LF export bytes differ')
    return dict(schemaVersion='pokerlab-root-hand-bank-drill-independent-check/v1',trainerAdmission=False,gradingAuthority=False,
        sourceCheck=receipt,sourceStudyHash=PIN,publicQuestionsChecked=3,cachedGradesChecked=9,
        canonicalMixedReviewAttemptsChecked=11,distinctReviewQuestions=3,evScope=SCOPE,sourceCommitmentShiftBb=1,
        emptyPreflopBoards=True,hiddenDealsAndFutureBoardAbsent=True,freshLpSolves=0,freshCfrRuns=0)

def check():
    for name,pin in IMMUTABLE_HELPERS.items():require(sha(ROOT/'scripts'/name)==pin,'Immutable helper changed: '+name)
    result=check_values(read(MANIFEST,16*1024),read(FIXTURE),REVIEW.read_bytes().decode('utf-8'))
    result.update(checkerSourceSha256=sha(Path(__file__)),sourceStoredSha256={p.name:sha(p) for p in [MANIFEST,FIXTURE,REVIEW]},immutableHelperSha256=IMMUTABLE_HELPERS)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
