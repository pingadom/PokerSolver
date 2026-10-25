"""Verify v6 using complete original source proofs and exact typed question/grade composition."""
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
legacy=module('unpaired_collection_legacy','check-call-flop-collection.py')
shared=module('unpaired_collection_shared','check-unpaired-shared-flop.py')
require,exact,read=legacy.require,legacy.exact,legacy.read
PIN='b1302c7d2ab8eb9e506567ca4d73c6a754e11b38470612d16bc090ffe7fe95d2'
SCHEMA='pokerlab-flop-interval-collection-review/v6'
SELECTION='ORDERED_LEGACY_FOURTEEN_THEN_UNPAIRED_FLOP_QUESTIONS/v6'
KINDS=['LEGACY_FOURTEEN_QUALIFIED_COLLECTION','SHARED_REFERENCE_UNPAIRED_QUESTIONS']
BUDGET=dict(sourceReplays=7,freshReferenceRuns=14,freshIterations=1212000)
MANIFEST=DATA/'supplied-eighteen-question-flop-collection-manifest.json'
REVIEW=DATA/'supplied-eighteen-question-flop-collection-review.json'

def file_sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def expected_manifest():
    old=DATA/'supplied-fourteen-question-flop-collection-manifest.json'
    old_manifest=read(old,65536)[2];require(exact(old_manifest,legacy.expected_manifest()),'Original v5 manifest differs')
    request=DATA/'supplied-unpaired-shared-flop-request.json';report=DATA/'supplied-unpaired-shared-flop-report.json.gz'
    source=shared.read(request)[0]
    input_hash=legacy.legacy.sha(shared.physical.canonical_input(source['parent']['connected']['input']))
    require(input_hash==old_manifest['sharedInputHash'],'Shared source must match declared weak-fold game')
    identity=dict(schemaVersion=SCHEMA,selection=SELECTION,beliefs=legacy.legacy.BELIEFS,
        legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),batchReportHash=shared.REPORT_HASH,
        sharedInputHash=input_hash,batchRequestSha256=file_sha(request),batchStoredSha256=file_sha(report))
    require(legacy.legacy.sha(identity)==PIN,'Collection identity differs')
    return dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v6',collectionHash=PIN,
        legacyManifestFile=old.name,legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),
        batchRequestFile=request.name,batchRequestSha256=file_sha(request),batchReportFile=report.name,
        batchReportStoredSha256=file_sha(report),batchReportHash=shared.REPORT_HASH,sharedInputHash=input_hash,startupBudget=BUDGET)

def union(index,old,new):return dict(kind=KINDS[int(index>=14)],legacy=old if index<14 else None,shared=new if index>=14 else None)
def source_review():
    return (read(DATA/'supplied-fourteen-question-flop-collection-review.json',1048576)[2],shared.read(shared.REVIEW)[0])
def source_attempt(old,new,index,action):
    matches=[a for a in (old['attempts'] if index<14 else new['attempts'])
        if a['action']==action and (a['questionIndex']==index if index<14 else a['question']['index']==index-14)]
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
    for index in range(18):
        source=old['questions'][index] if index<14 else next(a['question'] for a in new['attempts'] if a['question']['index']==index-14)
        questions.append(dict(collectionHash=PIN,index=index,totalQuestions=18,selection=SELECTION,
            sourceQuestionIndex=index if index<14 else index-14,decision=union(index,source,source)))
    require(type(review) is dict and set(review)=={'schemaVersion','collectionHash','selection','questions','attempts','counts'}
        and review['schemaVersion']==SCHEMA and review['collectionHash']==PIN and review['selection']==SELECTION
        and exact(review['questions'],questions),'Exact fourteen-question inventory differs')
    require(type(review['attempts']) is list and len(review['attempts'])<=100,'Bounded explicit attempts required')
    counts=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0);seen=set()
    names=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for index,attempt in enumerate(review['attempts']):
        require(type(attempt) is dict and set(attempt)=={'index','questionIndex','action','feedback'}
            and type(attempt['index']) is int and attempt['index']==index and type(attempt['questionIndex']) is int
            and 0<=attempt['questionIndex']<18,'Attempt order or source index differs')
        qi=attempt['questionIndex'];grade=source_attempt(old,new,qi,attempt['action'])
        expected=dict(collectionHash=PIN,questionIndex=qi,sourceQuestionIndex=qi if qi<14 else qi-14,decision=union(qi,grade,grade))
        require(exact(attempt['feedback'],expected),'Owned source grade or mapping differs')
        counts[names[move(grade)['classification']]]+=1;seen.add(qi)
    counts['distinctQuestionsAttempted']=len(seen)
    require(exact(counts,review['counts']),'Coverage or attempt classifications differ')
    robust={a:0 for a in ['fold','check','call','bet']}
    for qi in range(18):
        for action in (['fold','call'] if qi==1 or qi>=6 else ['check','bet']):
            if move(source_attempt(old,new,qi,action))['classification']=='WITHIN_LIMIT':robust[action]+=1
    require(robust==dict(fold=3,check=1,call=10,bet=4),'Robust action coverage differs')
    return dict(attemptsChecked=len(review['attempts']),robustMoves=robust,counts=counts)

def check(manifest_path=MANIFEST,review_path=REVIEW):
    raw_manifest,_,manifest=read(manifest_path,65536)
    require(exact(manifest,expected_manifest()),'Pinned v6 manifest differs')
    budget_path=DATA/'eighteen-question-flop-collection-generation-budget.json'
    budget=read(budget_path,65536)[2]
    require(budget['schemaVersion']=='pokerlab-unpaired-flop-collection-generation-budget/v1'
        and budget['collectionHash']==PIN and budget['manifestSha256']==hashlib.sha256(raw_manifest).hexdigest()
        and budget['maximumGenerationInvocations']==1 and budget['sourceReplaysPerInvocation']==7
        and budget['freshReferenceRunsPerInvocation']==14 and budget['freshIterationsPerInvocation']==1212000
        and budget['explicitReviewChoices']==[f'{i}:{a}' for i in range(18) for a in (['fold','call'] if i==1 or i>=6 else ['check','bet'])]
        and budget['maximumReviewAttempts']==100 and budget['automaticNumericalRetries']==0
        and budget['allOriginalSourceCapsAndTolerancesPreserved'] is True,'Predeclared work reservation differs')
    folder=manifest_path.resolve().parent
    old_receipt=legacy.check(folder/manifest['legacyManifestFile'],DATA/'supplied-fourteen-question-flop-collection-review.json')
    new_receipt=shared.check(folder/manifest['batchRequestFile'],folder/manifest['batchReportFile'],review_path=shared.REVIEW)
    old,new=source_review();raw,_,review=read(review_path,1048576)
    checked=verify_review(review,old,new)
    return dict(schemaVersion='pokerlab-independent-unpaired-flop-collection/v6',trainerAdmission=False,
        collectionHash=PIN,sourceQuestionsChecked=18,underlyingSourceQualifications=7,independentInputSets=2,
        sharedReferencePoliciesForFourAddedQuestions=2,startupBudget=BUDGET,freshLpSolves=0,freshCfrRuns=0,
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
