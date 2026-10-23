"""Verify v5 using complete original source proofs and exact typed question/grade composition."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'docs/data'
def module(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file)
    result=importlib.util.module_from_spec(spec);sys.modules[name]=result;spec.loader.exec_module(result);return result
legacy=module('call_collection_legacy','check-qualified-flop-collection.py')
shared=module('call_collection_shared','check-shared-call-flop-feedback.py')
require,exact,read=legacy.require,legacy.exact,legacy.read
PIN='62bda418612e3b7c336652c960e16c1433eba3730b94b7b2cfb3e3e30cdbb619'
SCHEMA='pokerlab-flop-interval-collection-review/v5'
SELECTION='ORDERED_LEGACY_ELEVEN_THEN_SHARED_CALL_QUESTIONS/v5'
KINDS=['LEGACY_ELEVEN_QUALIFIED_COLLECTION','SHARED_REFERENCE_CALL_QUESTIONS']
BUDGET=dict(sourceReplays=6,freshReferenceRuns=12,freshIterations=972000)
MANIFEST=DATA/'supplied-fourteen-question-flop-collection-manifest.json'
REVIEW=DATA/'supplied-fourteen-question-flop-collection-review.json'

def file_sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def expected_manifest():
    old=DATA/'supplied-eleven-question-flop-collection-manifest.json'
    old_manifest=read(old,65536)[2];require(exact(old_manifest,legacy.expected_manifest()),'Original v4 manifest differs')
    request=DATA/'supplied-shared-call-flop-request.json';report=DATA/'supplied-shared-call-flop-report.json.gz'
    source=shared.read(request)[0]
    input_hash=legacy.sha(shared.physical.canonical_input(source['parent']['connected']['input']))
    require(input_hash==old_manifest['weakInputHash'],'Shared source must match declared weak-fold game')
    identity=dict(schemaVersion=SCHEMA,selection=SELECTION,beliefs=legacy.BELIEFS,
        legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),batchReportHash=shared.REPORT_HASH,
        sharedInputHash=input_hash,batchRequestSha256=file_sha(request),batchStoredSha256=file_sha(report))
    require(legacy.sha(identity)==PIN,'Collection identity differs')
    return dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v5',collectionHash=PIN,
        legacyManifestFile=old.name,legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),
        batchRequestFile=request.name,batchRequestSha256=file_sha(request),batchReportFile=report.name,
        batchReportStoredSha256=file_sha(report),batchReportHash=shared.REPORT_HASH,sharedInputHash=input_hash,startupBudget=BUDGET)

def union(index,old,new):return dict(kind=KINDS[int(index>=11)],legacy=old if index<11 else None,shared=new if index>=11 else None)
def source_review():
    return (read(DATA/'supplied-eleven-question-flop-collection-review.json',1048576)[2],shared.read(shared.REVIEW)[0])
def source_attempt(old,new,index,action):
    matches=[a for a in (old['attempts'] if index<11 else new['attempts'])
        if a['action']==action and (a['questionIndex']==index if index<11 else a['question']['index']==index-11)]
    require(matches,'Illegal source action or question')
    require(all(exact(a['feedback'],matches[0]['feedback']) for a in matches),'Repeated source grades differ')
    return matches[0]['feedback']
def move(feedback):
    if 'selected' in feedback:return feedback['selected']
    d=feedback['decision']
    values=[v for k,v in d.items() if k!='kind' and v is not None]
    require(len(values)==1,'Typed grade must have one source')
    return move(values[0])

def verify_review(review,old,new):
    questions=[]
    for index in range(14):
        source=old['questions'][index] if index<11 else next(a['question'] for a in new['attempts'] if a['question']['index']==index-11)
        questions.append(dict(collectionHash=PIN,index=index,totalQuestions=14,selection=SELECTION,
            sourceQuestionIndex=index if index<11 else index-11,decision=union(index,source,source)))
    require(type(review) is dict and set(review)=={'schemaVersion','collectionHash','selection','questions','attempts','counts'}
        and review['schemaVersion']==SCHEMA and review['collectionHash']==PIN and review['selection']==SELECTION
        and exact(review['questions'],questions),'Exact fourteen-question inventory differs')
    require(type(review['attempts']) is list and len(review['attempts'])<=100,'Bounded explicit attempts required')
    counts=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0);seen=set()
    names=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for index,attempt in enumerate(review['attempts']):
        require(type(attempt) is dict and set(attempt)=={'index','questionIndex','action','feedback'}
            and type(attempt['index']) is int and attempt['index']==index and type(attempt['questionIndex']) is int
            and 0<=attempt['questionIndex']<14,'Attempt order or source index differs')
        qi=attempt['questionIndex'];grade=source_attempt(old,new,qi,attempt['action'])
        expected=dict(collectionHash=PIN,questionIndex=qi,sourceQuestionIndex=qi if qi<11 else qi-11,decision=union(qi,grade,grade))
        require(exact(attempt['feedback'],expected),'Owned source grade or mapping differs')
        counts[names[move(grade)['classification']]]+=1;seen.add(qi)
    counts['distinctQuestionsAttempted']=len(seen)
    require(exact(counts,review['counts']),'Coverage or attempt classifications differ')
    robust={a:0 for a in ['fold','check','call','bet']}
    for qi in range(14):
        for action in (['fold','call'] if qi==1 or qi>=6 else ['check','bet']):
            if move(source_attempt(old,new,qi,action))['classification']=='WITHIN_LIMIT':robust[action]+=1
    require(robust==dict(fold=2,check=1,call=7,bet=4),'Robust action coverage differs')
    return dict(attemptsChecked=len(review['attempts']),robustMoves=robust,counts=counts)

def check(manifest_path=MANIFEST,review_path=REVIEW):
    raw_manifest,_,manifest=read(manifest_path,65536)
    require(exact(manifest,expected_manifest()),'Pinned v5 manifest differs')
    budget_path=DATA/'fourteen-question-flop-collection-generation-budget.json'
    budget=read(budget_path,65536)[2]
    require(budget['schemaVersion']=='pokerlab-call-flop-collection-generation-budget/v1'
        and budget['collectionHash']==PIN and budget['manifestSha256']==hashlib.sha256(raw_manifest).hexdigest()
        and budget['maximumGenerationInvocations']==1 and budget['sourceReplaysPerInvocation']==6
        and budget['freshReferenceRunsPerInvocation']==12 and budget['freshIterationsPerInvocation']==972000
        and budget['explicitReviewChoices']==[f'{i}:{a}' for i in range(14) for a in (['fold','call'] if i==1 or i>=6 else ['check','bet'])]
        and budget['maximumReviewAttempts']==100 and budget['automaticNumericalRetries']==0
        and budget['allOriginalSourceCapsAndTolerancesPreserved'] is True,'Predeclared work reservation differs')
    folder=manifest_path.resolve().parent
    old_receipt=legacy.check(folder/manifest['legacyManifestFile'],DATA/'supplied-eleven-question-flop-collection-review.json')
    new_receipt=shared.check(folder/manifest['batchRequestFile'],folder/manifest['batchReportFile'],review_path=shared.REVIEW)
    old,new=source_review();raw,_,review=read(review_path,1048576)
    checked=verify_review(review,old,new)
    return dict(schemaVersion='pokerlab-independent-call-flop-collection/v5',trainerAdmission=False,
        collectionHash=PIN,sourceQuestionsChecked=14,underlyingSourceQualifications=6,independentInputSets=2,
        sharedReferencePoliciesForThreeAddedQuestions=2,startupBudget=BUDGET,freshLpSolves=0,freshCfrRuns=0,
        reviewCheck=checked,sourceReceipts=[old_receipt,new_receipt],checkerSourceSha256=file_sha(Path(__file__)),
        manifestStoredSha256=hashlib.sha256(raw_manifest).hexdigest(),reviewStoredSha256=hashlib.sha256(raw).hexdigest(),
        generationBudgetSha256=file_sha(budget_path))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('manifest',type=Path);parser.add_argument('review',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'New receipt output required')
    result=check(args.manifest,args.review)
    with args.output.open('x',encoding='utf-8',newline='\n') as out:out.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__':main()
