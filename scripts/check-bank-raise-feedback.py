"""Independent two-hand full-menu qualification from verified physical minima; no LP/CFR."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
SOURCE_SHA='e01215a5da6ee3ffe3ea4e427c6777ad211881bdbd7ef970c3abb1a9d74df034'
SOURCE_CANONICAL='73e4fcde12e37749fa11278c143830f72f16378802affcd9fa5980e26fc8a8be'
SOURCE_HELPER_SHA='7f02d0724fb65dddeee5d69ea5efed772ec85f10ebe945144d062693ddea9a98'
helper=ROOT/'scripts/check-bank-raise-sequence.py'
if hashlib.sha256(helper.read_bytes()).hexdigest()!=SOURCE_HELPER_SHA:
    raise ValueError('Original independent minimum checker changed')
spec=importlib.util.spec_from_file_location('bank_feedback_original_minimum',helper)
source=importlib.util.module_from_spec(spec);sys.modules[spec.name]=source;spec.loader.exec_module(source)
require,equal=source.require,source.bank_checker.equal
read=source.read
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-bank-raise-feedback-request.json'
REPORT=DATA/'supplied-bank-raise-feedback-report.json.gz'
ACTIONS=['fold','call','raise:9.0']
SCOPE='COMPLETE_MENU_TERMINAL_PROFIT_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1'
QUALIFICATION_SCOPE='ORIGINAL_BB77_ACJC_COMPLETE_MENU_RAISE_MINIMA_OFFLINE_ONLY/v1'

def context():
    require(hashlib.sha256(source.REPORT.read_bytes()).hexdigest()==SOURCE_SHA,'Original minimum source bytes differ')
    minimum=read(source.REPORT)
    proof=source.check_values(read(source.REQUEST),minimum)
    require(proof['completeMinima'] and proof['referenceSupportedMinima'],'Complete independently supported original minima required')
    return minimum,proof

def inputs(original,minimum):
    require([m['action'] for m in original['moves']]==ACTIONS,'Complete original action order required')
    result=[]
    for m in original['moves']:
        candidate=minimum if m['action']=='raise:9.0' else None
        selected=m['lowerEvBb'] if candidate is None else max(m['lowerEvBb'],candidate)
        require(all(type(v) in (int,float) and math.isfinite(v) for v in [m['lowerEvBb'],m['upperEvBb'],selected])
                and selected<=m['upperEvBb'],'Finite consistent conservative action bounds required')
        result.append(dict(action=m['action'],originalLowerBb=m['lowerEvBb'],sequenceMinimumLowerBb=candidate,
                           selectedLowerBb=selected,originalFullActionUpperBb=m['upperEvBb']))
    return result

def moves(bounds):
    result=[]
    for b in bounds:
        low=max([0.]+[o['selectedLowerBb']-b['originalFullActionUpperBb'] for o in bounds])
        high=max([0.]+[o['originalFullActionUpperBb']-b['selectedLowerBb'] for o in bounds])
        classification='WITHIN_LIMIT' if high<=.01 else 'OUTSIDE_LIMIT' if low>.01 else 'STRATEGY_DEPENDENT'
        result.append(dict(action=b['action'],lowerEvBb=b['selectedLowerBb'],upperEvBb=b['originalFullActionUpperBb'],
                           lowerLossBb=low,upperLossBb=high,classification=classification))
    return result

def assess(original,supported,menu):
    require(supported['iterations']==original['iterations'] and supported['solutionHash']==original['solutionHash']
            and [m['action'] for m in menu]==ACTIONS
            and set(original['actionUtilitiesBb'])==set(original['actionLossBb'])==set(ACTIONS),'Original policy and complete menu identity differ')
    failures=set(original['failures'])
    if not supported['supported']:failures.add('RAISE_MINIMUM_NOT_SUPPORTED')
    violation=None;checked=0
    if original['insideDeclaredFace']:
        violation=0.
        for m in menu:
            v=original['actionUtilitiesBb'][m['action']];loss=original['actionLossBb'][m['action']]
            require(type(v) in (int,float) and type(loss) in (int,float) and math.isfinite(v) and math.isfinite(loss),'Finite original policy values required')
            violation=max(violation,m['lowerEvBb']-v,v-m['upperEvBb'],m['lowerLossBb']-loss,loss-m['upperLossBb']);checked+=4
        if violation>1e-8:failures.add('REFERENCE_OUTSIDE_BANK_MENU_BOUNDS')
    else:failures.add('BANK_MENU_REFERENCE_OUTSIDE_FACE')
    return dict(iterations=original['iterations'],solutionHash=original['solutionHash'],sourceMinimumSupported=supported['supported'],minimumLowerChecks=1,
                boundsChecked=violation is not None,utilityLossInequalitiesChecked=checked,maximumBoundViolationBb=violation,failures=sorted(failures))

def compare(original,menu):
    result=[]
    for before,after in zip(original,menu,strict=True):
        require(before['action']==after['action'],'Original and new menu orders differ')
        result.append(dict(action=after['action'],previousMove=before,lowerEvDifferenceBb=after['lowerEvBb']-before['lowerEvBb'],
                           upperEvDifferenceBb=after['upperEvBb']-before['upperEvBb'],lowerLossDifferenceBb=after['lowerLossBb']-before['lowerLossBb'],
                           upperLossDifferenceBb=after['upperLossBb']-before['upperLossBb'],classificationUnchanged=after['classification']==before['classification']))
    return result

def assessment(minimum,i,index):
    original=minimum['sourceBank']['assessments'][index]
    bounds=inputs(original,minimum['batch']['minima'][i]['minimum']['lowerBound']);menu=moves(bounds)
    supports=minimum['policySupports'][i*3:i*3+3]
    require(all(p['sourceQuestionIndex']==index for p in supports) and len(supports)==3,'Per-hand original policy support differs')
    checks=[assess(policy,support,menu) for policy,support in zip(original['policies'],supports,strict=True)]
    reasons=set()
    if not any(m['classification']=='WITHIN_LIMIT' for m in menu):reasons.add('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification']=='OUTSIDE_LIMIT' for m in menu):reasons.add('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for p in [*original['policies'],*checks]:
        reasons.update(('PRIMARY' if p['iterations']==1 else str(p['iterations']))+':'+f for f in p['failures'])
    return dict(sourceQuestionIndex=index,heroCards=original['heroCards'],context=original['context'],lowerBoundSources=bounds,moves=menu,policies=checks,
                previousMenuComparison=compare(original['moves'],menu),qualifiedForOfflineDrill=not reasons,rejectionReasons=sorted(reasons))

def check_values(request,report,verified=None):
    minimum,proof=context() if verified is None else verified
    source.bank_checker.finite(request);source.bank_checker.finite(report)
    expected_request=dict(schemaVersion='pokerlab-bank-raise-feedback-request/v1',minimumRequest=minimum['request'],minimumStoredSha256=SOURCE_SHA,maximumLossBb=.01)
    require(equal(request,expected_request),'Original minimum request or loss threshold differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','boundsScope','qualificationScope','request','sourceMinimum','assessments','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion']=='pokerlab-bank-raise-feedback-report/v1'
            and report['publicationStatus']=='BANK_RAISE_MINIMA_TWO_HAND_FEEDBACK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['boundsScope']==SCOPE and report['qualificationScope']==QUALIFICATION_SCOPE
            and equal(report['request'],request) and equal(report['sourceMinimum'],minimum),'Pinned offline feedback source, scope or metadata differs')
    expected=[assessment(minimum,i,index) for i,index in enumerate([0,2])]
    reasons=sorted(str(a['sourceQuestionIndex'])+':'+reason for a in expected for reason in a['rejectionReasons'])
    require(equal(report['assessments'],expected) and report['qualifiedForOfflineDrill'] is (not reasons) and report['rejectionReasons']==reasons,
            'Complete two-hand action, reference and qualification results differ')
    return dict(schemaVersion='pokerlab-bank-raise-feedback-independent-check/v1',trainerAdmission=False,gradingAuthority=False,
                qualifiedForOfflineDrill=not reasons,sourceMinimumCheck=proof,handsChecked=2,movesChecked=6,policyChecks=6,minimumLowerChecks=6,
                utilityLossInequalitiesChecked=sum(p['utilityLossInequalitiesChecked'] for a in expected for p in a['policies']),
                minimumUpperUsedAsFullActionCeiling=False,currentDecisionEvShiftApplied=False,freshLpSolves=0,freshCfrRuns=0,
                classificationMenus=[[m['classification'] for m in a['moves']] for a in expected],
                raiseLossUpperBb=[a['moves'][2]['upperLossBb'] for a in expected],rejectionReasons=reasons)

def check():
    result=check_values(read(REQUEST),read(REPORT))
    result.update(checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),
                  reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),minimumStoredSha256=SOURCE_SHA,
                  immutableHelperSha256={'check-bank-raise-sequence.py':SOURCE_HELPER_SHA})
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    raw=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(raw)
    print(raw.decode())
