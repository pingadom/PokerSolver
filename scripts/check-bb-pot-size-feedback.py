"""Independent qualification of the pot-size BB source and its cached public review; no optimizer."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'docs/data'
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
screens=load('pot_screens','check-bb-sizing-primary-screen.py')
policy_helper=screens.feedback
require,read,close=screens.require,screens.read,screens.physics.close
REPORT=DATA/'supplied-unpaired-bb-pot-size-feedback-report.json.gz'
REQUEST=DATA/'supplied-unpaired-bb-pot-size-feedback-request.json'
BUDGET=DATA/'bb-pot-size-qualification-generation-budget.json'
PINS=DATA/'bb-pot-size-feedback-pins.json'
REVIEW=DATA/'supplied-unpaired-bb-pot-size-review.json'

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def check_source(report_path=REPORT,*,verify_screens=True,bind_saved_bytes=True):
    screen_receipt=screens.check() if verify_screens else None
    report,raw,expanded=read(report_path);request,_,_=read(REQUEST);budget,_,_=read(BUDGET);pins,_,_=read(PINS)
    screen,_,_=read(DATA/'supplied-unpaired-bb-pot-size-primary-screen.json.gz')
    require(digest(REQUEST)==budget['requestSha256'] and digest(DATA/'bb-sizing-primary-screen-independent-check.json')==budget['screenReceiptSha256']
        and digest(DATA/'supplied-unpaired-bb-pot-size-primary-screen.json.gz')==budget['screenReportSha256']
        and budget['screenPrimaryEligibleIndices']==[3,5] and budget['physicalGameBuilds']==1
        and budget['plannedCaseAttempts']==6 and budget['maximumParentSolves']==18 and budget['maximumActionComputations']==12
        and budget['maximumEndpointCertificates']==51 and budget['freshReferenceRuns']==2 and budget['referenceIterations']==[80000,160000]
        and budget['totalReferenceIterations']==240000 and budget['selectedPolicyAssessments']==18 and budget['automaticRetries']==0
        and budget['quarterPotFreshReferenceRuns']==0 and budget['allOriginalGatesAndNumericCapsPreserved'] is True
        and budget['maximumExpandedBytesPerReport']==8388608,'Declared pot-size source/reference budget differs')
    expected=copy.deepcopy(request);expected['coverage']=screen['request']
    require(request['schemaVersion']=='pokerlab-big-blind-pairwise-feedback-request/v1'
        and request['lossAlgorithm']==screens.pairwise.ALGORITHM
        and request['referenceBudget']==dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',iterations=[80000,160000],totalIterations=240000)
        and report['request']==expected and report['study']==screen['study']
        and report['schemaVersion']=='pokerlab-big-blind-pairwise-feedback-report/v1'
        and report['publicationStatus']=='BIG_BLIND_PAIRWISE_FRESH_REFERENCE_QUALIFICATION_RESEARCH_ONLY' and report['trainerAdmission'] is False
        and report['reservation']==dict(physicalGameBuilds=1,caseAttempts=6,maximumParentSolves=18,maximumActionComputations=12,
            freshReferenceRuns=2,totalReferenceIterations=240000,selectedPolicyAssessments=18,automaticRetries=0)
        and len(report['cases'])==6 and len(report['references'])==2,'Complete pot-size qualification inventory differs')
    parent=screen['study']['source']['cases'][0]['conditional']['parent']
    tree=screens.physics.literal_tree(parent,dict(binding=parent['binding']['sourceBinding']),parent['request']['connected'])
    context=dict(root=tree,prior=parent['originalJointPrior'],parent=parent,
        selected={(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(tree.edges)})
    old=read(DATA/'supplied-unpaired-shared-flop-report.json.gz')[0]
    visited=0
    for j,(reference,iterations) in enumerate(zip(report['references'],[80000,160000],strict=True)):
        policy=reference['policy'];strategy=policy['strategy']
        require(policy['iterations']==iterations and set(strategy)==set(parent['candidate']['strategy']),'Fresh pot-size policy inventory differs')
        for key,row in strategy.items():
            require(set(row)==set(parent['candidate']['strategy'][key]) and all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Invalid reference row')
            close(math.fsum(row.values()),1,'Reference probabilities not normalized')
        canonical=hashlib.sha256(json.dumps(policy,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()
        require(canonical==pins['canonicalNumericPolicySha256'][j],'Frozen numeric policy identity differs')
        require(reference['work']==old['references'][j]['work'],'Literal inactive proof or exact traversal work differs')
        visited+=reference['work']['statistics']['visitedNodes']
    require(visited<=budget['maximumReferenceVisitedNodes']==635040000,'Reference work cap differs')
    qualified=[];outcomes=[]
    for i,item in enumerate(report['cases']):
        conditional=screen['study']['source']['cases'][i]['conditional'];moves=screen['study']['cases'][i]['moves']
        require(item['index']==i and len(item['references'])==2 and item['primary']==screen['cases'][i]['primary'],'Primary/source identity differs')
        failures=[]
        if not any(m['classification']=='WITHIN_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if not any(m['classification']=='OUTSIDE_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        primary=policy_helper.policy_check(item['primary'],parent['candidate'],parent['solutionHash'],context,conditional,moves)
        failures+=['PRIMARY:'+f for f in primary]
        for j,reference in enumerate(report['references']):
            failures+=[str(reference['policy']['iterations'])+':'+f for f in policy_helper.policy_check(
                item['references'][j],reference['policy'],pins['referencePolicyHashes'][j],context,conditional,moves)]
        failures=sorted(set(failures))
        require(item['rejectionReasons']==failures and item['qualifiedForOfflineDrill'] is (not failures),'Full fresh pot-size rejection inventory differs')
        if not failures:qualified.append(i)
        outcomes.append(dict(index=i,heroCards=conditional['request']['heroCards'],flopActions=conditional['request']['flopActions'],qualified=not failures,
            actualReach=[c['actualQuestionReachGivenPhysicalFlop'] for c in [item['primary'],*item['references']]],rejectionReasons=failures))
    require(report['qualifiedIndices']==qualified==[3,5],'Complete qualified pot-size inventory differs')
    if bind_saved_bytes:require(pins['storedSha256']==hashlib.sha256(raw).hexdigest() and pins['expandedSha256']==hashlib.sha256(expanded).hexdigest(),'Frozen source bytes differ')
    return dict(screenCheck=screen_receipt,qualifiedIndices=qualified,policyAssessmentsChecked=18,referenceVisitedNodes=visited,outcomes=outcomes,
        reportStoredSha256=hashlib.sha256(raw).hexdigest(),reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),generationBudgetSha256=digest(BUDGET))

def check_review(review,report,pins):
    require(set(review)=={'schemaVersion','publicationStatus','trainerAdmission','reportHash','qualifiedSourceIndices','attempts','summary'}
        and review['schemaVersion']=='pokerlab-big-blind-pairwise-review/v1'
        and review['publicationStatus']=='QUALIFIED_BOUNDED_BIG_BLIND_PAIRWISE_DRILL_RESEARCH_ONLY'
        and review['trainerAdmission'] is False and review['reportHash']==pins['reportHash']
        and review['qualifiedSourceIndices']==report['qualifiedIndices']==[3,5] and len(review['attempts'])<=100,'Cached BB review identity or bound differs')
    questions=set();counts={'WITHIN_LIMIT':0,'OUTSIDE_LIMIT':0,'STRATEGY_DEPENDENT':0}
    for i,attempt in enumerate(review['attempts']):
        require(set(attempt)=={'index','feedback'} and attempt['index']==i,'Review order differs')
        actual=attempt['feedback'];index=actual['question']['index']
        require(type(index) is int and 0<=index<2,'Review question index differs')
        source_index=report['qualifiedIndices'][index];conditional=report['study']['source']['cases'][source_index]['conditional']
        q=dict(reportHash=pins['reportHash'],index=index,totalQuestions=2,sourceIndex=source_index,
            heroCards=conditional['request']['heroCards'],table=conditional['context'])
        moves=report['study']['cases'][source_index]['moves'];action=actual['action']
        require(action in q['table']['legalActions'],'Review action is illegal')
        selected=next(m for m in moves if m['action']==action)
        require(actual==dict(question=q,action=action,selected=selected,alternatives=moves),'Complete cached BB review regrade differs')
        questions.add(index);counts[selected['classification']]+=1
    require(review['summary']==dict(attempts=len(review['attempts']),questionsAttempted=len(questions),withinLimit=counts['WITHIN_LIMIT'],
        outsideLimit=counts['OUTSIDE_LIMIT'],strategyDependent=counts['STRATEGY_DEPENDENT']),'Review counts differ')
    return dict(attempts=len(review['attempts']),questionsAttempted=len(questions),classifications=counts)

def check(*,review_path=None):
    source=check_source();result=dict(schemaVersion='pokerlab-independent-bb-pot-size-qualification/v1',trainerAdmission=False,
        freshLpSolves=0,freshCfrRuns=0,sourceCheck=source,checkerSourceSha256=digest(Path(__file__)),reviewCheck=None)
    if review_path is not None:
        review,raw,_=read(review_path);result['reviewCheck']=check_review(review,read(REPORT)[0],read(PINS)[0]);result['reviewStoredSha256']=hashlib.sha256(raw).hexdigest()
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--review',type=Path);args=p.parse_args()
    require(not args.output.exists(),'Receipt must be new');result=check(review_path=args.review)
    with args.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps(result,indent=2))
