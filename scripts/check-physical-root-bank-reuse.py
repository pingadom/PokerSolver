"""Verify physical hand-bank parent reuse using unchanged source physics and original certificates."""
import argparse
import copy
import gzip
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import struct

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-root-hand-bank.py'
HELPER_SHA='e2ffd071b195d26a44c0339958ac51eac57a8c89c2d54c89e7b0d7f2faff9893'
if hashlib.sha256(HELPER.read_bytes()).hexdigest()!=HELPER_SHA:raise ValueError('Immutable original physical checker changed')
spec=importlib.util.spec_from_file_location('physical_reuse_original_bank',HELPER)
bank=importlib.util.module_from_spec(spec);spec.loader.exec_module(bank)
require,read=bank.require,bank.read
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-physical-root-bank-reuse-request.json'
REPORT=DATA/'supplied-physical-root-bank-reuse-report.json.gz'
SOURCE=DATA/'supplied-root-hand-bank-report.json.gz'
SOURCE_SHA='eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd'
SOURCE_HASH='e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d'
REUSE='ROOT_SECURITY_ENVELOPE_REUSED_OWNED_PHYSICAL_PARENT_BATCH/v1'
BUDGET=dict(questions=3,physicalGames=1,physicalParentCalls=1,physicalParentLpSolves=2,batchParentCalls=0,endpointLpSolves=15,freshReferenceRuns=2,freshIterations=240000,compilerUnits=8000000,endpointPivots=10000,endpointArithmetic=4000000000)
EXPECTED_REQUEST=dict(schemaVersion='pokerlab-physical-root-bank-reuse-request/v1',bankStoredSha256=SOURCE_SHA,bankStudyHash=SOURCE_HASH,heroCards=bank.HANDS,maximumLossBb=.01)
_CACHE=None


def exact(actual,expected):
    if isinstance(expected,dict):
        require(type(actual) is dict and actual.keys()==expected.keys(),'Object keys differ')
        for k,v in expected.items():exact(actual[k],v)
    elif isinstance(expected,list):
        require(type(actual) is list and len(actual)==len(expected),'Array inventory differs')
        for a,b in zip(actual,expected,strict=True):exact(a,b)
    elif type(expected) is float:
        require(type(actual) is float and math.isfinite(actual) and struct.pack('>d',actual)==struct.pack('>d',expected),'Original binary64 value differs')
    else:require(type(actual) is type(expected) and actual==expected,'Original typed value differs')


def compact(text):
    output=[];inside=False;escaped=False
    for char in text:
        if inside:
            output.append(char)
            if escaped:escaped=False
            elif char=='\\':escaped=True
            elif char=='"':inside=False
        elif char=='"':inside=True;output.append(char)
        elif not char.isspace():output.append(char)
    return ''.join(output).encode()


def raw_span(text,path):
    decoder=json.JSONDecoder();start=0
    def white(pos):
        while text[pos].isspace():pos+=1
        return pos
    for part in path:
        pos=white(start)+1
        if isinstance(part,str):
            while True:
                pos=white(pos);key,end=decoder.raw_decode(text,pos);pos=white(end)
                require(text[pos]==':','Malformed source object');pos=white(pos+1)
                _,end=decoder.raw_decode(text,pos)
                if key==part:start=pos;break
                pos=white(end);require(text[pos]==',','Missing original source key');pos+=1
        else:
            for i in range(part+1):
                pos=white(pos);_,end=decoder.raw_decode(text,pos)
                if i==part:start=pos;break
                pos=white(end);require(text[pos]==',','Missing original source index');pos+=1
    _,end=decoder.raw_decode(text,start)
    return text[start:end]


def source():
    global _CACHE
    stored=SOURCE.read_bytes();require(hashlib.sha256(stored).hexdigest()==SOURCE_SHA,'Original bank byte pin differs')
    if _CACHE is None:
        value=read(SOURCE);raw=gzip.decompress(stored).decode()
        require(hashlib.sha256(compact(raw)).hexdigest()==SOURCE_HASH,'Original lexical canonical identity differs')
        receipt=bank.check_values(value['request'],value)
        hashes=[hashlib.sha256(compact(raw_span(raw,['assessments',i,'envelope']))).hexdigest() for i in range(3)]
        _CACHE=value,receipt,hashes
    return _CACHE


def check_values(request,report):
    bank.finite(request);bank.finite(report);exact(request,EXPECTED_REQUEST)
    original,source_receipt,hashes=source()
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','maximumWork','historicalSource','physicalParent','batch','comparisons','assessments','originalMenusReproduced'},'Complete physical reuse report required')
    require(report['schemaVersion']=='pokerlab-physical-root-bank-reuse-report/v1' and report['publicationStatus']=='PHYSICAL_ROOT_BANK_OWNED_PARENT_REUSE_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['originalMenusReproduced'] is True,'Research scope differs')
    exact(report['request'],request);exact(report['maximumWork'],BUDGET)
    exact(report['historicalSource'],original);parent=report['physicalParent'];exact(parent,original['parent'])
    b=report['batch'];w=b['work']
    require(set(b)=={'algorithm','scope','trainerAdmission','snapshotHash','parent','firstParentWitness','secondParentWitness','envelopes','work'} and b['algorithm']==REUSE and b['scope']=='CONSERVATIVE_FULL_MENU_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS_OR_TRAINER_ADMISSION/v1' and b['trainerAdmission'] is False,'Owned reused-parent batch mode differs')
    exact(b['snapshotHash'],parent['solve']['snapshotHash']);exact(b['parent'],parent['solve'])
    exact(b['firstParentWitness'],parent['firstLpWitness']);exact(b['secondParentWitness'],parent['secondLpWitness'])
    require(len(b['envelopes'])==len(report['comparisons'])==len(report['assessments'])==3,'All three ordered hands required')
    used=parent['solve']['treeNodes']*3;pivots=0;arithmetic=0;solves=0
    for i,(envelope,c,a,old) in enumerate(zip(b['envelopes'],report['comparisons'],report['assessments'],original['assessments'],strict=True)):
        ew=envelope['work'];require(type(ew['compilerLimit']) is int and ew['compilerLimit']==8000000-used,'Remaining compiler limit was reset')
        adjusted=copy.deepcopy(envelope);adjusted['work']['compilerLimit']=8000000
        # Only the declared remaining compiler limit may differ. IEEE signed zero is retained.
        exact(adjusted,old['envelope'])
        exact(c,dict(sourceQuestionIndex=i,heroCards=bank.HANDS[i],originalEnvelopeHash=hashes[i],adjustedEnvelopeHash=hashes[i],identical=True))
        exact(a,dict(sourceQuestionIndex=i,heroCards=bank.HANDS[i],context=old['context'],moves=old['moves'],policies=old['policies'],rejectionReasons=old['rejectionReasons']))
        used+=ew['compilerUnits'];pivots+=ew['lpPivots'];arithmetic+=ew['lpArithmeticWork'];solves+=ew['lpSolves']
        require(used<=8000000 and pivots<=10000 and arithmetic<=4000000000,'Cumulative endpoint cap exceeded')
    expected=dict(parentSolveCalls=0,parentLpSolves=0,parentCompilerUnits=0,parentLpArithmeticWork=0,preflightCompilerUnits=3*parent['solve']['treeNodes'],compilerUnits=used,compilerLimit=8000000,endpointLpSolves=solves,endpointPivots=pivots,endpointPivotLimit=10000,endpointArithmeticWork=arithmetic,endpointArithmeticLimit=4000000000,enumeratedContinuationPlans=0)
    exact(w,expected);require(solves==15,'Complete endpoint inventory differs')
    require(source_receipt['originalEnvelopeCertificatesChecked']==15 and source_receipt['completeOuterBounds'] and source_receipt['supportedQuestionIndices']==[0,1,2],'Original independent gates are incomplete')
    return dict(status='PASS_PHYSICAL_PARENT_REUSE_RESEARCH_ONLY',physicalGames=1,actualParentLpSolves=2,historicalParentLpSolves=8,additionalBatchParentLpSolves=0,endpointLpSolves=15,parentCertificatesChecked=2,endpointCertificatesChecked=15,originalMenus=3,legalMoves=9,policyChecks=9,forcedContinuationValuesChecked=27,decisionLossesChecked=27,cumulativeCompilerUnits=used,endpointPivots=pivots,endpointArithmeticWork=arithmetic,freshReferenceRuns=2,freshReferenceIterations=[80000,160000],originalEnvelopeHashes=hashes,trainerAdmission=False,gradingAuthority=False,freshLpSolves=0,freshCfrRuns=0)


def check():
    result=check_values(read(REQUEST,128*1024),read(REPORT))
    result.update(sourceStoredSha256=SOURCE_SHA,requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),immutableHelperSha256=HELPER_SHA)
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    result=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(result)
    print(result.decode())
