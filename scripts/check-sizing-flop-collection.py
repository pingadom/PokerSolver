"""Verify v7 using original v6 and independent pot-size physical proofs and exact typed composition."""
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
legacy=module('sizing_collection_legacy','check-unpaired-flop-collection.py')
pot=module('sizing_collection_pot','check-bb-pot-size-review.py')
require,exact,read=legacy.require,legacy.exact,legacy.read
PIN='eeeadc2a2addf1e6878af533fe51251a42f3f2afa2f1a1fb7f0dd6e6bb01b9a5'
SCHEMA='pokerlab-flop-interval-collection-review/v7'
SELECTION='ORDERED_LEGACY_EIGHTEEN_THEN_POT_SIZE_BB_QUESTIONS/v7'
KINDS=['LEGACY_EIGHTEEN_QUALIFIED_COLLECTION','POT_SIZE_BIG_BLIND_QUESTIONS']
BUDGET=dict(sourceReplays=8,freshReferenceRuns=16,freshIterations=1452000)
MANIFEST=DATA/'supplied-twenty-question-flop-collection-manifest.json'
REVIEW=DATA/'supplied-twenty-question-flop-collection-review.json'

def file_sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def expected_manifest():
    old=DATA/'supplied-eighteen-question-flop-collection-manifest.json'
    old_manifest=read(old,65536)[2];require(exact(old_manifest,legacy.expected_manifest()),'Original v6 manifest differs')
    request=DATA/'supplied-unpaired-bb-pot-size-feedback-request.json';report=DATA/'supplied-unpaired-bb-pot-size-feedback-report.json.gz'
    source=pot.source.read(request)[0]
    input_hash=legacy.legacy.legacy.sha(pot.source.screens.physics.physical.canonical_input(source['coverage']['parent']['connected']['input']))
    require(input_hash==old_manifest['sharedInputHash'],'Common prior pin differs')
    identity=dict(schemaVersion=SCHEMA,selection=SELECTION,beliefs=legacy.legacy.legacy.BELIEFS,
        legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),potReportHash='04e99982ef9775a83cbffa28f64aedc7e146dd95dc38f09ad73604df24828996',
        sharedInputHash=input_hash,potRequestSha256=file_sha(request),potStoredSha256=file_sha(report))
    require(legacy.legacy.legacy.sha(identity)==PIN,'Collection identity differs')
    return dict(schemaVersion='pokerlab-flop-interval-collection-manifest/v7',collectionHash=PIN,
        legacyManifestFile=old.name,legacyCollectionHash=legacy.PIN,legacyManifestSha256=file_sha(old),
        potRequestFile=request.name,potRequestSha256=file_sha(request),potReportFile=report.name,
        potReportStoredSha256=file_sha(report),potReportHash=identity['potReportHash'],sharedInputHash=input_hash,startupBudget=BUDGET)


def union(index,old,new):return dict(kind=KINDS[int(index>=18)],legacy=old if index<18 else None,pot=new if index>=18 else None)

def source_review():
    return (read(DATA/'supplied-eighteen-question-flop-collection-review.json',1048576)[2],pot.source.read(DATA/'supplied-unpaired-bb-pot-size-review.json')[0])

def source_attempt(old,new,index,action):
    matches=[a for a in (old['attempts'] if index<18 else new['attempts'])
        if (a['action'] if index<18 else a['feedback']['action'])==action
        and (a['questionIndex']==index if index<18 else a['feedback']['question']['index']==index-18)]
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
    for index in range(20):
        source=old['questions'][index] if index<18 else next(a['feedback']['question'] for a in new['attempts'] if a['feedback']['question']['index']==index-18)
        questions.append(dict(collectionHash=PIN,index=index,totalQuestions=20,selection=SELECTION,
            sourceQuestionIndex=index if index<18 else index-18,decision=union(index,source,source)))
    require(type(review) is dict and set(review)=={'schemaVersion','collectionHash','selection','questions','attempts','counts'}
        and review['schemaVersion']==SCHEMA and review['collectionHash']==PIN and review['selection']==SELECTION
        and exact(review['questions'],questions),'Exact twenty-question inventory differs')
    require(type(review['attempts']) is list and len(review['attempts'])<=100,'Bounded explicit attempts required')
    counts=dict(distinctQuestionsAttempted=0,withinLimit=0,outsideLimit=0,strategyDependent=0);seen=set()
    names=dict(WITHIN_LIMIT='withinLimit',OUTSIDE_LIMIT='outsideLimit',STRATEGY_DEPENDENT='strategyDependent')
    for index,attempt in enumerate(review['attempts']):
        require(type(attempt) is dict and set(attempt)=={'index','questionIndex','action','feedback'}
            and type(attempt['index']) is int and attempt['index']==index and type(attempt['questionIndex']) is int
            and 0<=attempt['questionIndex']<20,'Attempt order or source index differs')
        qi=attempt['questionIndex'];grade=source_attempt(old,new,qi,attempt['action'])
        expected=dict(collectionHash=PIN,questionIndex=qi,sourceQuestionIndex=qi if qi<18 else qi-18,decision=union(qi,grade,grade))
        require(exact(attempt['feedback'],expected),'Owned source grade or mapping differs')
        counts[names[move(grade)['classification']]]+=1;seen.add(qi)
    counts['distinctQuestionsAttempted']=len(seen)
    require(exact(counts,review['counts']),'Coverage or attempt classifications differ')
    robust={a:0 for a in ['fold','check','call','bet']}
    for qi in range(20):
        for action in (['fold','call'] if qi==1 or qi>=6 else ['check','bet']):
            if move(source_attempt(old,new,qi,action))['classification']=='WITHIN_LIMIT':robust[action]+=1
    require(robust==dict(fold=4,check=1,call=11,bet=4),'Robust action coverage differs')
    return dict(attemptsChecked=len(review['attempts']),robustMoves=robust,counts=counts)

def check(manifest_path=MANIFEST,review_path=REVIEW):
    raw_manifest,_,manifest=read(manifest_path,65536)
    require(exact(manifest,expected_manifest()),'Pinned v7 manifest differs')
    budget_path=DATA/'twenty-question-flop-collection-generation-budget.json'
    budget=read(budget_path,65536)[2]
    require(budget['schemaVersion']=='pokerlab-sizing-flop-collection-generation-budget/v1'
        and budget['collectionHash']==PIN and budget['manifestSha256']==hashlib.sha256(raw_manifest).hexdigest()
        and budget['maximumGenerationInvocations']==1 and budget['sourceReplaysPerInvocation']==8
        and budget['freshReferenceRunsPerInvocation']==16 and budget['freshIterationsPerInvocation']==1452000
        and budget['explicitReviewChoices']==[f'{i}:{a}' for i in range(20) for a in (['fold','call'] if i==1 or i>=6 else ['check','bet'])]
        and budget['maximumReviewAttempts']==100 and budget['automaticNumericalRetries']==0
        and budget['allOriginalSourceCapsAndTolerancesPreserved'] is True,'Predeclared work reservation differs')
    folder=manifest_path.resolve().parent
    old_receipt=legacy.check(folder/manifest['legacyManifestFile'],DATA/'supplied-eighteen-question-flop-collection-review.json')
    new_receipt=pot.check()
    old,new=source_review();raw,_,review=read(review_path,1048576)
    checked=verify_review(review,old,new)
    return dict(schemaVersion='pokerlab-independent-sizing-flop-collection/v7',trainerAdmission=False,
        collectionHash=PIN,sourceQuestionsChecked=20,underlyingSourceQualifications=8,independentInputSets=2,
        separateBetSizeReferencePoliciesForTwoAddedQuestions=2,startupBudget=BUDGET,freshLpSolves=0,freshCfrRuns=0,
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
