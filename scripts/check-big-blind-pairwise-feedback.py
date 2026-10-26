"""Independent complete BB qualification audit; recompute every policy gate without optimization."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value


pairwise=load('bb_feedback_pairwise','check-big-blind-pairwise-study.py')
legacy=load('bb_feedback_local_helper','check-later-bb-flop-feedback.py')
coverage=pairwise.source
base=coverage.source
physical=coverage.physical
require,close,read=coverage.require,coverage.close,coverage.read
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-unpaired-bb-pairwise-feedback-request.json'
REPORT=DATA/'supplied-unpaired-bb-pairwise-feedback-report.json.gz'
BUDGET=DATA/'big-blind-pairwise-feedback-generation-budget.json'
EPS=1e-8


def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def policy_check(actual,policy,expected_hash,context,conditional,moves):
    require(set(actual)=={'iterations','solutionHash','fullParentQuality','reachedFlopQuality',
        'actualQuestionRootReach','actualQuestionReachGivenPhysicalFlop','counterfactualQuestionReachGivenPhysicalFlop',
        'globalHeroBestResponseBb','globalHeroUpperBb','inclusionMarginBb','insideDeclaredFace','boundsChecked',
        'maximumBoundViolationBb','actionUtilitiesBb','actionLossBb','decisionRegretBb','failures'},'BB policy fields differ')
    require(actual['iterations']==policy['iterations'] and actual['solutionHash']==expected_hash,'BB policy identity differs')
    strategy=policy['strategy']
    physical.compare_quality(actual['fullParentQuality'],physical.quality(context['root'],strategy))
    legacy.local_check(actual['reachedFlopQuality'],strategy,context)
    key,roots=coverage.roots_for(conditional['request'],context['parent'],context['root'])
    actual_mass=0.;weights=[]
    hero_hand=conditional['request']['heroCards']
    preflop_key=f'5:supplied-connected-single-flop:supplied-conditional-preflop:{hero_hand}:'
    for p,initial,question,_ in roots:
        reached=p*physical.P*strategy[preflop_key]['call']
        cf=p
        if question is not initial:
            reply=dict(initial.edges)['check']
            opponent_bet=strategy[reply.key]['bet']
            reached*=strategy[initial.key]['check']*opponent_bet
            cf*=opponent_bet
        actual_mass+=reached;weights.append(cf)
    cf=math.fsum(weights)
    close(actual['actualQuestionRootReach'],actual_mass,'BB full-path actual reach differs',mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'],actual_mass/physical.P,'BB actual conditional reach differs',mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'],cf,'BB own past cancellation differs',mass=True)
    values={}
    if cf>0:
        for move in moves:
            forced=physical.Node(-1,edges=tuple((p/cf,dict(root[2].edges)[move['action']]) for root,p in zip(roots,weights,strict=True) if p>0))
            values[move['action']]=physical.best_response(forced,strategy,5)
    losses={a:max(values.values())-v for a,v in values.items()} if values else {}
    require(set(actual['actionUtilitiesBb'])==set(values)==set(actual['actionLossBb']),'BB action assessment inventory differs')
    for action,value in values.items():
        close(actual['actionUtilitiesBb'][action],value,'BB reference EV differs')
        close(actual['actionLossBb'][action],losses[action],'BB reference regret differs')
    response=physical.best_response(context['root'],strategy,5)
    upper=conditional['actions'][0]['diagnostic']['globalHeroUpperValue']
    margin=upper+EPS-response;included=margin>=-EPS;checked=included and cf>0
    close(actual['globalHeroBestResponseBb'],response,'BB full-parent response differs')
    close(actual['globalHeroUpperBb'],upper,'BB security value differs')
    close(actual['inclusionMarginBb'],margin,'BB face inclusion margin differs')
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is checked,'BB face/check status differs')
    failures=[]
    if not included:failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
    if actual['fullParentQuality']['nashConvBb']>.001:failures.append('FULL_PARENT_GAP')
    local=actual['reachedFlopQuality']['conditionalBestResponse']
    if local is None or local['nashConvBb']>.001:failures.append('REACHED_FLOP_GAP')
    if actual_mass/physical.P<.0001:failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf<.0001:failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    if cf>0:
        regret=math.fsum(strategy[key][action]*value for action,value in losses.items())
        close(actual['decisionRegretBb'],regret,'BB policy regret differs')
        if regret>.01:failures.append('SELECTED_DECISION_REGRET')
    else:require(actual['decisionRegretBb'] is None,'Zero-reach policy cannot claim regret')
    if checked:
        violation=max([0.]+[v for m in moves for v in [m['lowerEvBb']-values[m['action']],values[m['action']]-m['upperEvBb'],
            m['lowerLossBb']-losses[m['action']],losses[m['action']]-m['upperLossBb']]])
        close(actual['maximumBoundViolationBb'],violation,'BB pairwise bound check differs')
        if violation>EPS:failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    else:require(actual['maximumBoundViolationBb'] is None,'Unchecked bounds cannot claim a violation result')
    require(actual['failures']==failures,'Complete BB policy failure inventory differs')
    return failures


def check(report_path=REPORT, *, verify_source=True,bind_saved_bytes=True):
    pairwise_receipt=pairwise.check() if verify_source else None
    request,_,_=read(REQUEST);report,raw,expanded=read(report_path);budget,_,_=read(BUDGET)
    study,_,_=read(pairwise.REPORT);old,_,_=read(DATA/'supplied-unpaired-shared-flop-report.json.gz')
    require(digest(REQUEST)==budget['requestSha256'] and digest(pairwise.REPORT)==budget['pairwiseStudySha256']
        and digest(DATA/'big-blind-pairwise-study-independent-check.json')==budget['pairwiseReceiptSha256']
        and digest(ROOT/'solver/src/main/java/com/pokerlab/solver/SixMaxRefinedFlopFeedback.java')==budget['unchangedOriginalGateSha256'],'Predeclared BB inputs or old gate changed')
    require(budget['physicalGameBuilds']==1 and budget['plannedCaseAttempts']==6 and budget['maximumParentSolves']==18
        and budget['maximumActionComputations']==12 and budget['maximumEndpointCertificates']==51
        and budget['freshReferenceRuns']==2 and budget['referenceIterations']==[80000,160000]
        and budget['totalReferenceIterations']==240000 and budget['selectedPolicyAssessments']==18
        and budget['automaticRetries']==0 and budget['maximumExpandedBytesPerReport']==8388608
        and budget['securitySlack']==EPS and budget['requiredMinimumReach']==.0001
        and budget['maximumLossBb']==.01 and budget['fullParentAndReachedFlopGapBb']==.001
        and budget['originalNumericAndCompilerCapsPreserved'] is True and budget['trainerAdmission'] is False,'BB qualification bounds differ')
    expected=copy.deepcopy(request);expected['coverage']=study['source']['request']
    require(request['schemaVersion']=='pokerlab-big-blind-pairwise-feedback-request/v1'
        and request['lossAlgorithm']==pairwise.ALGORITHM
        and request['referenceBudget']==dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',iterations=[80000,160000],totalIterations=240000)
        and report['request']==expected and report['study']==study
        and report['schemaVersion']=='pokerlab-big-blind-pairwise-feedback-report/v1'
        and report['publicationStatus']=='BIG_BLIND_PAIRWISE_FRESH_REFERENCE_QUALIFICATION_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and len(report['cases'])==6 and len(report['references'])==2
        and report['reservation']==dict(physicalGameBuilds=1,caseAttempts=6,maximumParentSolves=18,maximumActionComputations=12,
            freshReferenceRuns=2,totalReferenceIterations=240000,selectedPolicyAssessments=18,automaticRetries=0),'Complete BB source/assessment inventory differs')
    parent=study['source']['cases'][0]['conditional']['parent']
    root=base.literal_tree(parent,dict(binding=parent['binding']['sourceBinding']),parent['request']['connected'])
    selected={(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(root.edges)}
    context=dict(root=root,prior=parent['originalJointPrior'],selected=selected,parent=parent)
    visited=0
    for reference,control,iteration in zip(report['references'],old['references'],[80000,160000],strict=True):
        # Exact deterministic equality is an independent control; owned Java replay proves fresh training.
        require(reference['policy']==control['policy'] and reference['work']==control['work'],'Fresh deterministic policy or work differs from fixed independent control')
        require(reference['policy']['iterations']==iteration,'Reference iteration budget differs')
        work=reference['work'];require(work['validatedTreeNodes']==1321 and work['prunedSuffixRoots']==4*iteration
            and work['statistics']==dict(visitedNodes=2646*iteration,terminalNodes=1440*iteration,sampledChanceNodes=0,baselineCorrections=0),'Explicit BB reference work differs')
        visited+=work['statistics']['visitedNodes']
    require(visited<=budget['maximumReferenceVisitedNodes']==635040000,'Cumulative reference work exceeds cap')
    qualified=[];outcomes=[]
    for i,item in enumerate(report['cases']):
        conditional=study['source']['cases'][i]['conditional'];moves=study['cases'][i]['moves']
        require(item['index']==i and conditional is not None and len(item['references'])==2,'BB case dropped or unbound')
        failures=[]
        if not any(m['classification']=='WITHIN_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if not any(m['classification']=='OUTSIDE_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        primary=policy_check(item['primary'],parent['candidate'],parent['solutionHash'],context,conditional,moves)
        failures+=['PRIMARY:'+f for f in primary]
        for j,reference in enumerate(report['references']):
            pin=old['cases'][0]['references'][j]['solutionHash']
            failures += [str(reference['policy']['iterations'])+':'+f for f in
                policy_check(item['references'][j],reference['policy'],pin,context,conditional,moves)]
        failures=sorted(set(failures))
        require(item['rejectionReasons']==failures and item['qualifiedForOfflineDrill'] is (not failures),'BB gate failures or qualification differ')
        if not failures:qualified.append(i)
        outcomes.append(dict(index=i,heroCards=conditional['request']['heroCards'],flopActions=conditional['request']['flopActions'],
            actionSeparated=study['cases'][i]['actionSeparated'],qualifiedForOfflineDrill=not failures,
            actualReach=[c['actualQuestionReachGivenPhysicalFlop'] for c in [item['primary'],*item['references']]],
            counterfactualReach=[c['counterfactualQuestionReachGivenPhysicalFlop'] for c in [item['primary'],*item['references']]],rejectionReasons=failures))
    require(report['qualifiedIndices']==qualified,'Complete surviving BB question inventory differs')
    if bind_saved_bytes:
        pins,_,_=read(DATA/'big-blind-pairwise-feedback-pins.json')
        require(pins==dict(storedSha256=hashlib.sha256(raw).hexdigest(),expandedSha256=hashlib.sha256(expanded).hexdigest()),'Frozen BB qualification bytes differ')
    return dict(schemaVersion='pokerlab-independent-big-blind-pairwise-feedback/v1',trainerAdmission=False,freshLpSolves=0,freshCfrRuns=0,
        sourceCheck=pairwise_receipt,contextsChecked=6,policyAssessmentsChecked=18,referenceVisitedNodes=visited,
        qualifiedIndices=qualified,outcomes=outcomes,checkerSourceSha256=digest(Path(__file__)),
        reportStoredSha256=hashlib.sha256(raw).hexdigest(),reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),generationBudgetSha256=digest(BUDGET))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    require(not args.output.exists(),'Receipt must be new');result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as stream:json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps(result,indent=2))
