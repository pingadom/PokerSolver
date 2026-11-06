"""Independent complete-menu batch qualification; certificates first, no optimizer/CFR."""
import argparse
import copy
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
IMMUTABLE_HELPERS = {'check-physical-root-sequence-batch.py': 'f4ac141bd1d8debbd046818416713d11f7cabfc45ee41a2c353198110f0fbb04', 'check-root-sequence-feedback.py': '473ce94f287446c8cc9faff0f0c7687ca6152e9c13cb060ee8068f4c29a18f3c'}
for name, pin in IMMUTABLE_HELPERS.items():
    if hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest() != pin:
        raise ValueError('Immutable physical batch qualification helper changed: '+name)

def module(name, file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file)
    loaded=importlib.util.module_from_spec(spec);sys.modules[name]=loaded;spec.loader.exec_module(loaded);return loaded

batch = module('batch_feedback_physical','check-physical-root-sequence-batch.py')
previous = module('batch_feedback_previous','check-root-sequence-feedback.py')
require, exact = batch.require, previous.exact
DATA = ROOT/'docs/data'
REQUEST = DATA/'supplied-root-sequence-batch-feedback-request.json'
REPORT = DATA/'supplied-root-sequence-batch-feedback-report.json.gz'
REPORT_SHA = 'd8f6897061acbba3e52643be206ca8e0e1927ed2577c2cd053b2cb02db5f4653'
BATCH_SHA = 'be3e7400a75fd86bb617cf4e70f758e2a885d80ba052e6441f28d81c61a0dc8b'
PREVIOUS_SHA = '013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b'
MAX_BYTES = 8*1024*1024
ACTIONS = ['fold','call','raise:9.0']
SCOPE = 'COMPLETE_MENU_TERMINAL_PROFIT_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1'
QUALIFICATION_SCOPE = 'ORIGINAL_BB99_COMPLETE_MENU_SHARED_SEQUENCE_MINIMA_OFFLINE_ONLY/v1'

def pairs(values):
    result={}
    for key,value in values:
        require(key not in result,'Duplicate JSON key');result[key]=value
    return result

def read(path):
    require(not path.is_symlink() and path.is_file() and path.stat().st_size<=MAX_BYTES,'Bounded regular report required')
    with path.open('rb') as stream: raw=stream.read(MAX_BYTES+1)
    require(len(raw)<=MAX_BYTES,'Stored report cap exceeded')
    if raw[:2]==b'\x1f\x8b':
        import io
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:raw=stream.read(MAX_BYTES+1)
    require(len(raw)<=MAX_BYTES,'Expanded report cap exceeded')
    def invalid(value):raise ValueError('Nonfinite JSON number: '+value)
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=invalid)

def context():
    require(hashlib.sha256(batch.REPORT.read_bytes()).hexdigest()==BATCH_SHA,'Original batch bytes differ')
    require(hashlib.sha256(previous.REPORT.read_bytes()).hexdigest()==PREVIOUS_SHA,'Previous feedback bytes differ')
    original=batch.context();source=read(batch.REPORT);old=read(previous.REPORT)
    batch_proof=batch.check_values(read(batch.REQUEST),source,original)
    old_proof=previous.check_values(read(previous.REQUEST),old,(original[0][0][0],original[1],original[2]))
    require(batch_proof['completeMinima'] and batch_proof['referenceSupportedMinima'] and old_proof['qualifiedForOfflineDrill'],'Complete supported original sources required')
    exact(source['previousMinimum'],old['sourceMinimum'],'Original shared minimum lineage differs')
    exact(source['originalEnvelope'],old['originalEnvelope']['envelope']['envelope'],'Original full-action envelope differs')
    return source,old,batch_proof,old_proof

def canonical(request):
    request=copy.deepcopy(request);p=request['batchRequest']['sourceRequest']['envelope']['parent']
    p['input']=batch.prior.physical.source.physical.canonical_input(p['input']);p['flop'].sort()
    return request

def bound_sources(source,old):
    original=old['originalEnvelope']['moves'];minima=source['batch']['minima']
    require([m['action'] for m in original]==[m['question']['action'] for m in minima]==ACTIONS,'Complete original menu order required')
    result=[]
    for floor,minimum in zip(original,minima,strict=True):
        candidate=minimum['minimum']['lowerBound'];selected=max(floor['lowerEvBb'],candidate)
        require(all(type(v) in (int,float) and math.isfinite(v) for v in [candidate,floor['lowerEvBb'],floor['upperEvBb']]) and selected<=floor['upperEvBb'],'Consistent finite original bounds required')
        result.append(dict(action=floor['action'],originalConservativeLowerBb=floor['lowerEvBb'],sequenceMinimumLowerBb=candidate,
                           selectedLowerBb=selected,originalFullActionUpperBb=floor['upperEvBb']))
    return result

def moves(inputs):
    result=[]
    for b in inputs:
        low=max([0.]+[o['selectedLowerBb']-b['originalFullActionUpperBb'] for o in inputs])
        high=max([0.]+[o['originalFullActionUpperBb']-b['selectedLowerBb'] for o in inputs])
        classification='WITHIN_LIMIT' if high<=.01 else 'OUTSIDE_LIMIT' if low>.01 else 'STRATEGY_DEPENDENT'
        result.append(dict(action=b['action'],lowerEvBb=b['selectedLowerBb'],upperEvBb=b['originalFullActionUpperBb'],lowerLossBb=low,upperLossBb=high,classification=classification))
    return result

def assess(policy,menu):
    original=policy['original'];failures=set(policy['failures'])
    if not policy['allMinimumLowerBoundsSupported']:failures.add('BATCH_MINIMA_NOT_SUPPORTED')
    keys={m['action'] for m in menu}
    require(len(menu)==len(keys)==3 and keys==set(original['actionUtilitiesBb'])==set(original['actionLossBb']),'Complete policy menu required')
    violation=None;count=0
    if original['insideDeclaredFace']:
        violation=0.
        for m in menu:
            value=original['actionUtilitiesBb'][m['action']];loss=original['actionLossBb'][m['action']]
            require(type(value) in (int,float) and type(loss) in (int,float) and math.isfinite(value) and math.isfinite(loss),'Finite original policy values required')
            violation=max(violation,m['lowerEvBb']-value,value-m['upperEvBb'],m['lowerLossBb']-loss,loss-m['upperLossBb']);count+=4
        if violation>1e-8:failures.add('REFERENCE_OUTSIDE_BATCH_MENU_BOUNDS')
    else:failures.add('BATCH_MENU_REFERENCE_OUTSIDE_FACE')
    return dict(iterations=original['iterations'],solutionHash=original['solutionHash'],sourceMinimaSupported=policy['allMinimumLowerBoundsSupported'],
                minimumLowerChecks=len(policy['minimumLowerViolationsBb']),boundsChecked=violation is not None,utilityLossInequalitiesChecked=count,
                maximumBoundViolationBb=violation,failures=sorted(failures))

def compare(old,menu):
    result=[]
    for before,after in zip(old['moves'],menu,strict=True):
        require(before['action']==after['action'],'Complete ordered previous comparison required')
        result.append(dict(action=after['action'],previousMove=before,lowerEvDifferenceBb=after['lowerEvBb']-before['lowerEvBb'],
                           upperEvDifferenceBb=after['upperEvBb']-before['upperEvBb'],lowerLossDifferenceBb=after['lowerLossBb']-before['lowerLossBb'],
                           upperLossDifferenceBb=after['upperLossBb']-before['upperLossBb'],classificationUnchanged=after['classification']==before['classification']))
    return result

def reasons(source,menu,policies):
    result=set()
    if not source['referenceSupportedMinima']:result.add('BATCH_NOT_REFERENCE_SUPPORTED')
    if not any(m['classification']=='WITHIN_LIMIT' for m in menu):result.add('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification']=='OUTSIDE_LIMIT' for m in menu):result.add('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for p in policies:result.update(('PRIMARY' if p['iterations']==1 else str(p['iterations']))+':'+f for f in p['failures'])
    return sorted(result)

def check_values(request,report,verified=None):
    source,old,batch_proof,old_proof=context() if verified is None else verified
    request=canonical(request)
    require(set(request)=={'schemaVersion','batchRequest','batchStoredSha256','previousFeedbackStoredSha256','maximumLossBb'}
            and request['schemaVersion']=='pokerlab-root-sequence-batch-feedback-request/v1' and request['batchStoredSha256']==BATCH_SHA
            and request['previousFeedbackStoredSha256']==PREVIOUS_SHA and request['maximumLossBb']==.01,'Original batch feedback request differs')
    exact(request['batchRequest'],source['request'],'Complete original batch request differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','boundsScope','qualificationScope','request','sourceBatch','previousFeedback',
                         'lowerBoundSources','moves','policies','previousMenuComparison','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion']=='pokerlab-root-sequence-batch-feedback-report/v1'
            and report['publicationStatus']=='SHARED_SEQUENCE_MINIMA_COMPLETE_MENU_FEEDBACK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['boundsScope']==SCOPE and report['qualificationScope']==QUALIFICATION_SCOPE,'Offline source scope/schema differs')
    exact(report['request'],request,'Feedback request differs');exact(report['sourceBatch'],source,'Full independently verified physical batch differs')
    exact(report['previousFeedback'],old,'Full independently verified prior feedback differs')
    inputs=bound_sources(source,old);expected=moves(inputs);policies=[assess(p,expected) for p in source['policies']];comparison=compare(old,expected)
    exact(report['lowerBoundSources'],inputs,'Original lower-bound inputs or conservative floor differ')
    exact(report['moves'],expected,'Complete action utility/loss bounds differ');exact(report['policies'],policies,'All primary/reference checks differ')
    exact(report['previousMenuComparison'],comparison,'Every previous-menu difference must remain visible')
    rejection=reasons(source,expected,policies)
    exact(report['rejectionReasons'],rejection,'Qualification failure reasons differ')
    require(report['qualifiedForOfflineDrill'] is (not rejection),'Offline qualification claim differs')
    return dict(schemaVersion='pokerlab-root-sequence-batch-feedback-independent-check/v1',trainerAdmission=False,sourceBatchCheck=batch_proof,
                previousFeedbackCheck=old_proof,movesChecked=3,minimumLowerChecks=9,policyMovesChecked=9,utilityLossInequalitiesChecked=36,
                qualifiedForOfflineDrill=not rejection,rejectionReasons=rejection,previousMenuComparison=comparison,
                minimumUpperUsedAsFullActionCeiling=False,currentDecisionEvShiftApplied=False,freshLpSolves=0,freshCfrRuns=0)

def check():
    require(hashlib.sha256(REPORT.read_bytes()).hexdigest()==REPORT_SHA,'Frozen batch feedback bytes differ')
    result=check_values(read(REQUEST),read(REPORT))
    result.update(requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),reportStoredSha256=REPORT_SHA,
                  checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),immutableHelperSha256=IMMUTABLE_HELPERS)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2,allow_nan=False)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
