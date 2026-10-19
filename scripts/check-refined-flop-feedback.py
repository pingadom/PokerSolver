"""Independent paired feedback qualification, literal reference checks and cached drill replay."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-refined-conditional-study.py'
spec=importlib.util.spec_from_file_location('refined_feedback_conditional',HELPER)
conditional=importlib.util.module_from_spec(spec);sys.modules[spec.name]=conditional;spec.loader.exec_module(conditional)
physical=conditional.physical
require,close,read,dot=conditional.require,conditional.close,conditional.read,conditional.dot
local_check=conditional.btn.local_check
CONDITIONAL_REQUEST=ROOT/'docs/data/supplied-paired-btn-facing-bet-conditional-request.json'
CONDITIONAL_REPORT=ROOT/'docs/data/supplied-paired-btn-facing-bet-conditional-report.json.gz'
EPS=1e-8
KEY=conditional.KEY
ALGORITHM='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1'
REQUEST_SHA='c6915fd92bc6a9630e00d513d5c8b78a07423f60591e2682953c2434b75134c7'
REPORT_SHA='4285f03d9445abf9173864f5d5fc4c528a66a83373d0a2f7f635352611d3a05c'


def policy_check(actual, policy, expected_hash, context, actions, roots):
    require(set(actual) == {'iterations', 'solutionHash', 'fullParentQuality', 'reachedFlopQuality',
            'actualQuestionRootReach', 'actualQuestionReachGivenPhysicalFlop',
            'counterfactualQuestionReachGivenPhysicalFlop', 'globalHeroBestResponseBb', 'globalHeroUpperBb',
            'inclusionMarginBb', 'insideDeclaredFace', 'boundsChecked', 'maximumBoundViolationBb',
            'actionUtilitiesBb', 'actionLossBb', 'decisionRegretBb', 'failures'}, 'Policy check fields differ')
    require(actual['iterations'] == policy['iterations'] and actual['solutionHash'] == expected_hash, 'Policy identity differs')
    strategy = policy['strategy']
    physical.compare_quality(actual['fullParentQuality'], physical.quality(context['root'], strategy))
    local_check(actual['reachedFlopQuality'], strategy, context)
    actual_mass, cf_weights = 0, []
    for i, p, initial, reply in roots:
        preflop = strategy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call']
        bet = strategy[initial.key]['bet']
        actual_mass += p * physical.P * preflop * bet
        # Both BB call and BB bet remain; BTN has no modeled past action.
        cf_weights.append(p * preflop * bet)
    cf = math.fsum(cf_weights)
    close(actual['actualQuestionRootReach'], actual_mass, 'Actual full-path root reach differs', mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'], actual_mass / physical.P, 'Actual full-path conditional reach differs', mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'], cf, 'Hero past cancellation or opponent call/bet reach differs', mass=True)
    values = {a['action']: physical.best_response(physical.Node(-1, edges=tuple(
        (p / cf, dict(root[3].edges)[a['action']]) for root, p in zip(roots, cf_weights, strict=True))), strategy, 3)
        for a in actions}
    losses = {a: max(values.values()) - v for a, v in values.items()}
    require(set(actual['actionUtilitiesBb']) == set(values) == set(actual['actionLossBb']), 'Policy action inventory differs')
    for action in values:
        close(actual['actionUtilitiesBb'][action], values[action], 'BTN forced action value differs')
        close(actual['actionLossBb'][action], losses[action], 'BTN action loss differs')
    br = physical.best_response(context['root'], strategy, 3)
    upper = actions[0]['diagnostic']['globalHeroUpperValue']
    margin = upper + EPS - br
    close(actual['globalHeroBestResponseBb'], br, 'Full-parent hero response differs')
    close(actual['globalHeroUpperBb'], upper, 'Full-parent security upper differs')
    close(actual['inclusionMarginBb'], margin, 'Reference inclusion differs')
    included = margin >= -EPS
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is included, 'Reference face status differs')
    failures = []
    if not included:
        failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
        require(actual['maximumBoundViolationBb'] is None, 'Outside-face bounds must remain unchecked')
    if actual['fullParentQuality']['nashConvBb'] > .001:
        failures.append('FULL_PARENT_GAP')
    if actual['reachedFlopQuality']['conditionalBestResponse']['nashConvBb'] > .001:
        failures.append('REACHED_FLOP_GAP')
    if actual_mass / physical.P < .0001:
        failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf < .0001:
        failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    regret = math.fsum(strategy[KEY][a] * loss for a, loss in losses.items())
    close(actual['decisionRegretBb'], regret, 'BTN selected decision regret differs')
    if regret > .01:
        failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation = max([0] + [v for a in actions for v in (
            a['lowerUtilityBb'] - values[a['action']], values[a['action']] - a['upperUtilityBb'],
            a['lowerLossBb'] - losses[a['action']], losses[a['action']] - a['upperLossBb'])])
        close(actual['maximumBoundViolationBb'], violation, 'BTN bound comparison differs')
        if violation > EPS:
            failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    require(actual['failures'] == failures, 'BTN policy failures differ')
    return failures


def check(request_path,report_path,*,bind_saved_bytes=True):
    conditional_receipt=conditional.check(CONDITIONAL_REQUEST,CONDITIONAL_REPORT)
    source,_,_=read(CONDITIONAL_REPORT)
    request,request_bytes,_=read(request_path)
    report,raw,expanded=read(report_path)
    declared=dict(request,conditional=source['request'])
    require(set(request)=={'schemaVersion','conditional','referenceBudget','maximumLossBb'}
            and request['schemaVersion']=='pokerlab-refined-flop-feedback-request/v1'
            and request['maximumLossBb']==.01 and request['referenceBudget']==dict(algorithm=ALGORITHM,iterations=[80000,160000],totalIterations=240000)
            and physical.canonical_input(request['conditional']['parent']['connected']['input'])==source['request']['parent']['connected']['input'],'Unsupported fixed feedback request')
    # Check every other conditional request field too; canonical input is the only normalization.
    normalized=copy.deepcopy(request['conditional'])
    normalized['parent']['connected']['input']=physical.canonical_input(normalized['parent']['connected']['input'])
    require(normalized==source['request'],'Conditional request differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','conditional','moves','primary','references','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion']=='pokerlab-refined-flop-feedback-report/v1'
            and report['publicationStatus']=='REFINED_PHYSICAL_FLOP_FEEDBACK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request']==declared and report['conditional']==source,'Feedback identity differs')
    parent=source['parent'];original,_,_=read(physical.PARENT)
    root=conditional.parent_check.literal_tree(parent,original,parent['request']['connected'])
    selected={}
    for i,(_,node) in enumerate(root.edges):selected[i,'|BB:call']=dict(node.edges)['call'].edges[0][1]
    context=dict(root=root,prior=parent['originalJointPrior'],selected=selected)
    roots=[]
    for i,world in enumerate(context['prior']):
        if world['dealtCombos'][3]=='As Qs':
            initial=selected[i,'|BB:call'];roots.append((i,world['conditionalProbability'],initial,dict(initial.edges)['bet']))
    actions=source['actions']
    moves=[]
    for a in actions:
        classification='WITHIN_LIMIT' if a['upperLossBb']<=.01 else 'OUTSIDE_LIMIT' if a['lowerLossBb']>.01 else 'STRATEGY_DEPENDENT'
        moves.append(dict(action=a['action'],lowerEvBb=a['lowerUtilityBb'],upperEvBb=a['upperUtilityBb'],lowerLossBb=a['lowerLossBb'],upperLossBb=a['upperLossBb'],classification=classification))
    require(report['moves']==moves,'Classified moves differ')
    failures=['PRIMARY:'+f for f in policy_check(report['primary'],parent['candidate'],parent['solutionHash'],context,actions,roots)]
    require(len(report['references'])==2 and [r['policy']['iterations'] for r in report['references']]==[80000,160000],'Fresh reference inventory differs')
    hashes=['f0166881b2d76f79d97fb48b567246da03981d193a0819e1008697a8cfb96981','aa42587dfb8e1502cc33216bc07f15bf36ca08dbda5c7299b117a94efd569d21']
    def literal_fixed(node,actor):
        require(node.actor!=actor,'Inactive actor has a decision')
        if node.actor==-2:return node.utilities[actor]
        values=[literal_fixed(child,actor) for _,child in node.edges]
        require(values and all(v==values[0] for v in values),'Inactive utility varies')
        return values[0]
    constants={str(actor):literal_fixed(root,actor) for actor in [0,1,2,4]}
    def sizes(node):
        pairs=[sizes(c) for _,c in node.edges]
        return 1+sum(n for n,_ in pairs),int(node.actor==-2)+sum(t for _,t in pairs)
    nodes,terminals=sizes(root)
    require(nodes==1321 and terminals==720,'Literal reference tree inventory differs')
    for reference,pin in zip(report['references'],hashes,strict=True):
        policy=reference['policy'];iteration=policy['iterations'];strategy=policy['strategy']
        require(set(strategy)==set(parent['candidate']['strategy']),'Fresh policy coverage differs')
        for key,row in strategy.items():
            require(set(row)==set(parent['candidate']['strategy'][key]) and all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Invalid reference policy')
            close(math.fsum(row.values()),1,'Reference row normalization differs')
        failures += [str(iteration)+':'+f for f in policy_check(reference['check'],policy,pin,context,actions,roots)]
        work=reference['work']
        require(work==dict(algorithm=ALGORITHM,validatedTreeNodes=nodes,inactiveUtilities=constants,statistics=dict(visitedNodes=(2*nodes+4)*iteration,terminalNodes=2*terminals*iteration,sampledChanceNodes=0,baselineCorrections=0),prunedSuffixRoots=4*iteration),'Literal reference pruning proof/work differs')
    classes={m['classification'] for m in moves}
    if 'WITHIN_LIMIT' not in classes:failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if 'OUTSIDE_LIMIT' not in classes:failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    require(report['rejectionReasons']==sorted(set(failures)) and report['qualifiedForOfflineDrill'] is (not failures),'Qualification result differs')
    if bind_saved_bytes:require(hashlib.sha256(request_bytes).hexdigest()==REQUEST_SHA and hashlib.sha256(raw).hexdigest()==REPORT_SHA,'Fixed feedback byte binding differs')
    return dict(schemaVersion='pokerlab-independent-refined-feedback-check/v1',trainerAdmission=False,qualifiedForOfflineDrill=not failures,policiesChecked=3,freshLpSolves=0,freshCfrRuns=0,declaredFreshReferenceIterations=240000,literalInactiveUtilitiesIndependentlyChecked=constants,
                sourceRequestSha256=hashlib.sha256(request_bytes).hexdigest(),sourceReportStoredSha256=hashlib.sha256(raw).hexdigest(),sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),conditionalHelperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),policyHelperSha256=hashlib.sha256((ROOT/'scripts/check-btn-flop-feedback.py').read_bytes()).hexdigest(),conditionalCheck=conditional_receipt)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('request',type=Path);parser.add_argument('report',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Receipt output must be new')
    result=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8',newline='\n') as out:out.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
