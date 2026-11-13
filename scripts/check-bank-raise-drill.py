"""Derive the public two-hand cache from independently checked qualification; no LP/CFR."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'docs/data'
HELPER_SHA='4b05fc706cc05f998b0e88a664085c4528ed1ede21d761624d54b234d7f13741'
helper=ROOT/'scripts/check-bank-raise-feedback.py'
if hashlib.sha256(helper.read_bytes()).hexdigest()!=HELPER_SHA:raise ValueError('Original qualification checker changed')
spec=importlib.util.spec_from_file_location('bank_raise_cache_feedback_source',helper)
feedback=importlib.util.module_from_spec(spec);sys.modules[spec.name]=feedback;spec.loader.exec_module(feedback)
read,require,equal=feedback.read,feedback.require,feedback.equal
PIN='60f8eb975a963ebdf3964cf4edb72354b8e0841e01bc5eb3a3a333e5bc9b0343'
EV_SCOPE='CURRENT_DECISION_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
MANIFEST=DATA/'supplied-bank-raise-drill-manifest.json'
FIXTURE=DATA/'supplied-bank-raise-drill-fixture.json'
FILES={'requestFile':('supplied-bank-raise-feedback-request.json','requestSha256','8f9f0b0947a5786fd59a64f62b083113dd71493e95149159d2c6b84578962be6'),
       'reportFile':('supplied-bank-raise-feedback-report.json.gz','reportStoredSha256','7077bd260e422690e0060a31d36dea53e545c8ee88641ef8e5e811633d9723e2'),
       'minimumFile':('supplied-bank-raise-sequence-report.json.gz','minimumStoredSha256',feedback.SOURCE_SHA),
       'bankFile':('supplied-root-hand-bank-report.json.gz','bankStoredSha256','eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd')}
BUDGET=dict(physicalGames=1,maximumParentLpSolves=4,maximumEndpointLpSolves=2,freshReferenceRuns=2,freshIterations=240000)

def context():
    proof=feedback.check()
    require(proof['qualifiedForOfflineDrill'] and proof['utilityLossInequalitiesChecked']==72,'Complete original qualification required')
    for name,_,sha in FILES.values():require(hashlib.sha256((DATA/name).read_bytes()).hexdigest()==sha,'Pinned input bytes differ')
    return read(feedback.REPORT),proof

def expected_manifest():
    result=dict(schemaVersion='pokerlab-bank-raise-drill-manifest/v1',studyHash=PIN,startupBudget=BUDGET)
    for key,(name,hash_key,sha) in FILES.items():result.update({key:name,hash_key:sha})
    return result

def check_values(manifest,fixture,verified=None):
    report,source=context() if verified is None else verified
    feedback.source.bank_checker.finite(manifest);feedback.source.bank_checker.finite(fixture)
    require(equal(manifest,expected_manifest()),'Complete exact manifest, source pins and startup budget required')
    questions=[];grades=[]
    for a in report['assessments']:
        require(a['sourceQuestionIndex'] in [0,2] and a['qualifiedForOfflineDrill'],'Original qualified source hand required')
        hero=next(p for p in a['context']['players'] if p['seat']==a['context']['actingSeat'])
        require(hero['committedBb']==1,'Original posted BB required')
        moves=[dict(m,lowerEvBb=m['lowerEvBb']+1,upperEvBb=m['upperEvBb']+1) for m in a['moves']]
        questions.append(dict(sourceCaseIndex=a['sourceQuestionIndex'],heroCards=a['heroCards'],board=[],context=a['context'],evScope=EV_SCOPE,moves=moves))
        grades.extend(dict(studyHash=PIN,sourceCaseIndex=a['sourceQuestionIndex'],evScope=EV_SCOPE,move=m) for m in moves)
    require([q['sourceCaseIndex'] for q in questions]==[0,2],'Two original sparse case IDs required')
    snapshot=dict(schemaVersion='pokerlab-bank-raise-drill/v1',trainerAdmission=False,studyHash=PIN,evScope=EV_SCOPE,questions=questions)
    expected=dict(schemaVersion='pokerlab-bank-raise-drill-owned-capture/v1',snapshot=snapshot,grades=grades)
    require(equal(fixture,expected),'Exact complete public projection, six grades and one EV shift required')
    return dict(schemaVersion='pokerlab-bank-raise-drill-independent-check/v1',trainerAdmission=False,gradingAuthority=False,
                studyHash=PIN,publicQuestions=2,cachedLegalGrades=6,originalSourceCaseIndices=[0,2],currentDecisionEvShiftBb=1,
                sourceUtilityLossInequalities=72,freshLpSolves=0,freshCfrRuns=0,sourceQualificationCheck=source,
                raiseLossUpperBb=[q['moves'][2]['upperLossBb'] for q in questions])

def check():
    require(MANIFEST.stat().st_size<=16*1024,'Bounded manifest required')
    result=check_values(read(MANIFEST),read(FIXTURE))
    result.update(checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),manifestStoredSha256=hashlib.sha256(MANIFEST.read_bytes()).hexdigest(),fixtureStoredSha256=hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),immutableHelperSha256={'check-bank-raise-feedback.py':HELPER_SHA})
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    raw=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(raw)
    print(raw.decode())
