"""Independent three-policy reach, face, regret and interval-inclusion qualification."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('size_feedback_conditional',ROOT/'scripts/check-size-menu-conditional.py')
conditional=importlib.util.module_from_spec(spec);sys.modules[spec.name]=conditional;spec.loader.exec_module(conditional)
size=conditional.size
physical=size.base.physical
require,close,read=size.require,size.close,size.read
EPS=1e-8
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-size-menu-99-feedback-request.json'
REPORT=DATA/'supplied-size-menu-99-feedback-report.json.gz'


def policy_check(actual, policy, expected_hash, source, tree):
    require(set(actual)=={'iterations','solutionHash','fullParentQuality','reachedFlopQuality','actualQuestionRootReach',
        'actualQuestionReachGivenPhysicalFlop','counterfactualQuestionReachGivenPhysicalFlop','globalHeroBestResponseBb',
        'globalHeroUpperBb','inclusionMarginBb','insideDeclaredFace','boundsChecked','maximumBoundViolationBb',
        'actionUtilitiesBb','actionLossBb','decisionRegretBb','failures'},'Policy assessment fields differ')
    require(actual['iterations']==policy['iterations'] and actual['solutionHash']==expected_hash,'Assessment policy identity differs')
    strategy=policy['strategy'];parent=source['parent'];q=source['request']
    full=physical.quality(tree,strategy);physical.compare_quality(actual['fullParentQuality'],full)
    local=actual['reachedFlopQuality'];roots=[];selected=[];raw=[];mass=0;actual_mass=0;cf=0
    for i,((_,preflop),world) in enumerate(zip(tree.edges,parent['originalJointPrior'],strict=True)):
        state=dict(preflop.edges)['call'].edges[0][1]
        key=f'5:supplied-size-menu-preflop:supplied-conditional-preflop:{world["dealtCombos"][5]}:'
        reached=world['conditionalProbability']*size.P*strategy[key]['call']
        if reached>0:
            roots.append((reached,state));raw.append(dict(originalDealIndex=i,jointRootFlopReach=reached));mass+=reached
        if world['dealtCombos'][5]==q['heroCards']:
            weight=world['conditionalProbability']
            selected.append((weight,state));cf+=weight;actual_mass+=reached
    require(set(local)=={'scope','publicHistory','candidateRootFlopReach','physicalSupportFlopMass',
        'candidateBoundaryReachGivenPhysicalFlop','conditionalReachFloor','conditioning','rawWorldMasses','reachedWorlds','conditionalBestResponse'}
        and local['scope']=='CANDIDATE_POLICY_REACHED_FLOP_POSTERIOR_NOT_FULL_SECURITY_FACE/v1'
        and local['publicHistory']=='|BB:call' and local['conditionalReachFloor']==.0001,'Reached-flop scope differs')
    close(local['candidateRootFlopReach'],mass,'Reached boundary raw mass differs',mass=True)
    close(local['physicalSupportFlopMass'],size.P,'Physical event differs',mass=True)
    close(local['candidateBoundaryReachGivenPhysicalFlop'],mass/size.P,'Reached boundary units differ',mass=True)
    require(len(local['rawWorldMasses'])==len(raw),'Raw world inventory differs')
    for saved,wanted in zip(local['rawWorldMasses'],raw,strict=True):
        require(saved['originalDealIndex']==wanted['originalDealIndex'],'Raw world identity differs')
        close(saved['jointRootFlopReach'],wanted['jointRootFlopReach'],'Raw world mass differs',mass=True)
    supported=mass/size.P>=.0001
    require(local['conditioning']==('ABOVE_CANDIDATE_REACH_FLOOR' if supported else 'BELOW_CANDIDATE_REACH_FLOOR'),'Conditioning differs')
    local_quality=None
    if supported:
        require(len(local['reachedWorlds'])==len(raw),'Normalized reached inventory differs')
        for saved,wanted in zip(local['reachedWorlds'],raw,strict=True):
            require(saved['originalDealIndex']==wanted['originalDealIndex'],'Reached world identity differs')
            close(saved['jointRootFlopReach'],wanted['jointRootFlopReach'],'Reached raw world mass differs',mass=True)
            close(saved['conditionalWeight'],wanted['jointRootFlopReach']/mass,'Reached posterior differs',mass=True)
        root=size.Node(-1,edges=tuple((p/mass,node) for p,node in roots))
        local_quality=physical.quality(root,strategy);physical.compare_quality(local['conditionalBestResponse'],local_quality)
    else:
        require(local['reachedWorlds']==[] and local['conditionalBestResponse'] is None,'Unsupported posterior fabricated')
    close(actual['actualQuestionRootReach'],actual_mass,'Actual question mass differs',mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'],actual_mass/size.P,'Actual question units differ',mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'],cf,'Hero-past cancellation differs',mass=True)
    values={a['action']:physical.best_response(size.Node(-1,edges=tuple((p/cf,dict(node.edges)[a['action']]) for p,node in selected)),strategy,5) for a in source['actions']}
    losses={a:max(values.values())-v for a,v in values.items()}
    require(set(actual['actionUtilitiesBb'])==set(values)==set(actual['actionLossBb']),'Action coverage differs')
    for a in values:
        close(actual['actionUtilitiesBb'][a],values[a],'Forced optimal-continuation utility differs')
        close(actual['actionLossBb'][a],losses[a],'Forced action loss differs')
    upper=source['actions'][0]['diagnostic']['globalHeroUpperValue'];br=full[1][5];margin=upper+EPS-br
    close(actual['globalHeroBestResponseBb'],br,'Global security best response differs')
    close(actual['globalHeroUpperBb'],upper,'Original global upper differs')
    close(actual['inclusionMarginBb'],margin,'Security inclusion margin differs')
    included=margin>=-EPS
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is included,'Outside-face policy claims checked bounds')
    failures=[]
    if not included:failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
    if full[3]>.001:failures.append('FULL_PARENT_GAP')
    if local_quality is None or local_quality[3]>.001:failures.append('REACHED_FLOP_GAP')
    if actual_mass/size.P<.0001:failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf<.0001:failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    regret=math.fsum(strategy[source['informationSet']][a]*v for a,v in losses.items())
    close(actual['decisionRegretBb'],regret,'Selected decision regret differs')
    if regret>.01:failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation=max([0]+[v for a in source['actions'] for v in [a['lowerUtilityBb']-values[a['action']],values[a['action']]-a['upperUtilityBb'],a['lowerLossBb']-losses[a['action']],losses[a['action']]-a['upperLossBb']]])
        close(actual['maximumBoundViolationBb'],violation,'Interval bound violation differs')
        if violation>EPS:failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    else:require(actual['maximumBoundViolationBb'] is None,'Outside-face policy claims bound verification')
    require(actual['failures']==failures,'Complete policy rejection reasons differ')
    return failures


def check_values(request,report):
    request=copy.deepcopy(request)
    request['conditional']['parent']['input']=physical.canonical_input(request['conditional']['parent']['input'])
    request['conditional']['parent']['flop']=sorted(request['conditional']['parent']['flop'])
    require(set(request)=={'schemaVersion','conditional','maximumLossBb'} and request['schemaVersion']=='pokerlab-size-menu-feedback-request/v1' and request['maximumLossBb']==.01,'Fixed feedback request differs')
    source=report['conditional'];source_receipt=conditional.check_values(request['conditional'],source)
    require(source==read(conditional.REPORT),'Original owned conditional study changed')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','conditional','moves','primary','references','qualifiedForOfflineDrill','rejectionReasons'}
        and report['schemaVersion']=='pokerlab-size-menu-feedback-report/v1' and report['publicationStatus']=='SIZE_MENU_POLICY_QUALIFICATION_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and report['request']==request,'Qualification identity differs')
    require(report['moves']==source_receipt['moves'],'Ambiguous move classification changed')
    parent=source['parent'];tree=size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),parent['request'])
    reasons=[]
    if not any(m['classification']=='WITHIN_LIMIT' for m in report['moves']):reasons.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification']=='OUTSIDE_LIMIT' for m in report['moves']):reasons.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    reasons+=['PRIMARY:'+f for f in policy_check(report['primary'],parent['candidate'],parent['solutionHash'],source,tree)]
    require(len(report['references'])==2,'Fresh reference assessment inventory differs')
    for assessment,reference in zip(report['references'],parent['references'],strict=True):
        reasons += [str(reference['policy']['iterations'])+':'+f for f in policy_check(assessment,reference['policy'],reference['policyHash'],source,tree)]
    reasons=sorted(set(reasons))
    require(report['rejectionReasons']==reasons and report['qualifiedForOfflineDrill'] is (not reasons),'Qualification bypasses an original gate')
    return dict(schemaVersion='pokerlab-size-menu-feedback-independent-check/v1',trainerAdmission=False,sourceCheck=source_receipt,
        policyAssessmentsChecked=3,forcedActionValuesChecked=9,qualifiedForOfflineDrill=not reasons,rejectionReasons=reasons,
        policyFailures=[report['primary']['failures'],*(r['failures'] for r in report['references'])],
        freshLpSolves=0,freshCfrRuns=0,qualificationAdditionalLpSolves=0,qualificationAdditionalCfrRuns=0)


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def check(request_path=REQUEST,report_path=REPORT):
    result=check_values(read(request_path),read(report_path))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(request_path),reportStoredSha256=sha(report_path),
        immutableHelperSha256={n:sha(ROOT/'scripts'/n) for n in ['check-size-menu-conditional.py','check-flop-size-menu.py','check-bb-sizing-physics.py']})
    return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    result=check();data=(json.dumps(result,indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output:output.write(data)
    print(data.decode())
