"""Independently reconstruct both declared betting models and all twelve primary gate outcomes."""
import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1];DATA=ROOT/'docs/data'
def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
physics=load('sizing_literal_physics','check-bb-sizing-physics.py')
pairwise=load('sizing_pairwise','check-big-blind-pairwise-study.py')
feedback=load('sizing_policy_gates','check-big-blind-pairwise-feedback.py')
require,read=physics.require,physics.read
BUDGET=DATA/'bb-sizing-primary-screen-generation-budget.json'
NAMES=['quarter-pot','pot-size']

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def check_model(name,report,request):
    fraction={'quarter-pot':.25,'pot-size':1.}[name]
    control=read(pairwise.source.REQUEST)[0];wanted=copy.deepcopy(control)
    wanted['parent']['connected']['betPotFraction']=fraction
    require(request==wanted,'Only the declared bet fraction may change')
    declared=copy.deepcopy(request);declared['parent']['connected']=physics.connected(request['parent']['connected'])
    study=report['study'];source=study['source']
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','reservation','study','cases','primaryEligibleIndices'}
        and report['schemaVersion']=='pokerlab-big-blind-primary-screen/v1'
        and report['publicationStatus']=='PRIMARY_ELIGIBILITY_ONLY_FRESH_REFERENCE_QUALIFICATION_REQUIRED'
        and report['trainerAdmission'] is False and report['request']==declared
        and report['reservation']==dict(physicalGameBuilds=1,caseAttempts=6,maximumParentSolves=18,maximumActionComputations=12,
            maximumPrimaryAssessments=6,freshReferenceRuns=0,automaticRetries=0),'Primary screen identity or reservation differs')
    require(study['schemaVersion']=='pokerlab-big-blind-pairwise-regret-study/v1'
        and study['publicationStatus']=='PAIRWISE_REGRET_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and study['lossAlgorithm']==pairwise.ALGORITHM and study['trainerAdmission'] is False
        and source['schemaVersion']=='pokerlab-big-blind-flop-coverage-report/v2'
        and source['publicationStatus']=='INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and source['trainerAdmission'] is False and source['request']==declared
        and source['reservation']==dict(physicalGameBuilds=1,caseAttempts=6,maximumActionComputations=12,maximumParentSolves=18,freshReferenceRuns=0,automaticRetries=0)
        and len(source['cases'])==len(study['cases'])==len(report['cases'])==6,'Complete six-context source differs')
    parent=source['cases'][0]['conditional']['parent']
    tree,parent_receipt=physics.checked_parent(parent,declared['parent'])
    require(source['binding']==parent['binding'],'Sizing source binding differs')
    selected={(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(tree.edges)}
    context=dict(root=tree,prior=parent['originalJointPrior'],selected=selected,parent=parent)
    candidates=[];eligible=[];summaries=[];certificates=0;legacy_separated=0
    for i,(original,item,screen) in enumerate(zip(source['cases'],study['cases'],report['cases'],strict=True)):
        expected=dict(schemaVersion='pokerlab-refined-flop-conditional-request/v1',parent=declared['parent'],intervalAlgorithm=physics.ALGORITHM,
            hero=5,heroCards=declared['heroHands'][i//2],publicPreflopHistory='|BB:call',flopActions=[] if i%2==0 else ['check','bet'])
        require(original['index']==i and original['request']==expected and original['conditional'] is not None,'Source case order or scope differs')
        certificates+=physics.check_case(original,parent,tree)
        if original['outcome']=='ACTION_SEPARATION':legacy_separated+=1
        moves=pairwise.moves(original['conditional'])
        separated={'WITHIN_LIMIT','OUTSIDE_LIMIT'}<={m['classification'] for m in moves}
        require(item==dict(index=i,moves=moves,actionSeparated=separated),'Sizing pairwise moves differ')
        if separated:candidates.append(i)
        primary=screen['primary'];require(primary is not None and screen['index']==i,'Missing primary assessment')
        failures=feedback.policy_check(primary,parent['candidate'],parent['solutionHash'],context,original['conditional'],moves)
        reasons=['PRIMARY:'+f for f in failures]
        if not any(m['classification']=='WITHIN_LIMIT' for m in moves):reasons.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if not any(m['classification']=='OUTSIDE_LIMIT' for m in moves):reasons.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        reasons=sorted(set(reasons))
        require(screen==dict(index=i,primary=primary,primaryEligible=not reasons,rejectionReasons=reasons),'Primary eligibility or failure inventory differs')
        if not reasons:eligible.append(i)
        summaries.append(dict(index=i,heroCards=expected['heroCards'],flopActions=expected['flopActions'],actionSeparated=separated,
            primaryEligible=not reasons,actualReach=primary['actualQuestionReachGivenPhysicalFlop'],
            counterfactualReach=primary['counterfactualQuestionReachGivenPhysicalFlop'],rejectionReasons=reasons))
    require(source['counts']==dict(planned=6,solved=6,actionSeparated=legacy_separated,conditionalRejected=0,lpRejected=0)
        and study['candidateIndices']==candidates and report['primaryEligibleIndices']==eligible,'Complete sizing counts or candidates differ')
    require(eligible==([] if name=='quarter-pot' else [3,5]),'Frozen primary screen outcome differs')
    return dict(name=name,betPotFraction=fraction,originalEndpointCertificatesChecked=certificates,parentCheck=parent_receipt,
        candidateIndices=candidates,primaryEligibleIndices=eligible,contexts=summaries)

def check(report_paths=None,*,bind_saved_bytes=True):
    budget,_,_=read(BUDGET)
    require(budget['physicalGameBuilds']==2 and budget['plannedCaseAttempts']==12 and budget['maximumParentSolves']==36
        and budget['maximumActionComputations']==24 and budget['maximumPrimaryAssessments']==12
        and budget['maximumOriginalLpCertificates']==180 and budget['freshReferenceRuns']==budget['automaticRetries']==0
        and budget['maximumExpandedBytesPerReport']==8388608 and budget['originalNumericAndCompilerCapsPreserved'] is True
        and budget['securitySlack']==1e-8 and budget['requiredMinimumReach']==.0001 and budget['maximumLossBb']==.01
        and budget['fullParentAndReachedFlopGapBb']==.001 and budget['trainerAdmission'] is False,'Declared sizing budget differs')
    require([m['name'] for m in budget['models']]==NAMES and [m['betPotFraction'] for m in budget['models']]==[.25,1.],'Declared model order differs')
    models=[];pins={}
    for model in budget['models']:
        name=model['name'];request_path=DATA/('supplied-unpaired-bb-'+name+'-screen-request.json')
        request,_,_=read(request_path);require(digest(request_path)==model['requestSha256'],'Frozen sizing request bytes differ')
        path=report_paths[name] if report_paths else ROOT/model['output'];report,raw,expanded=read(path)
        models.append(check_model(name,report,request));pins[name]=dict(storedSha256=hashlib.sha256(raw).hexdigest(),expandedSha256=hashlib.sha256(expanded).hexdigest())
    if bind_saved_bytes:require(read(DATA/'bb-sizing-primary-screen-pins.json')[0]==pins,'Frozen screen bytes differ')
    return dict(schemaVersion='pokerlab-independent-bb-sizing-primary-screen/v1',trainerAdmission=False,freshLpSolves=0,freshCfrRuns=0,
        models=models,contextsChecked=12,primaryAssessmentsChecked=12,originalEndpointCertificatesChecked=sum(m['originalEndpointCertificatesChecked'] for m in models),
        qualifiedTrainerQuestions=0,checkerSourceSha256=digest(Path(__file__)),physicsHelperSha256=digest(ROOT/'scripts/check-bb-sizing-physics.py'),generationBudgetSha256=digest(BUDGET))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
    require(not args.output.exists(),'Receipt must be new');result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps(result,indent=2))
