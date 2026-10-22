"""Independent shared-pair qualification: one physical parent, every endpoint and all nine assessments.

No optimization or qualification capability is created. Full Java replay establishes fresh training.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'docs/data'
HELPER=ROOT/'scripts/check-weak-range-flop-coverage.py'
spec=importlib.util.spec_from_file_location('shared_flop_coverage_helper',HELPER)
coverage=importlib.util.module_from_spec(spec);sys.modules[spec.name]=coverage;spec.loader.exec_module(coverage)
physical=coverage.physical
require,close,read=coverage.require,coverage.close,coverage.read
local_check=coverage.weak.btn.local_check
EPS=1e-8
REQUEST=DATA/'supplied-shared-call-flop-request.json'
REPORT=DATA/'supplied-shared-call-flop-report.json.gz'
BUDGET=DATA/'shared-call-flop-generation-budget.json'
REQUEST_SHA='015ed5937b6ca782a907f9c8a08973f927348c7851b031a9f76093db36ffcdaf'
BUDGET_SHA='bc03dbacfc1131d0b0e271d69e4cfc8a6d57dc86bdd15b5e2db8cfa8b3846b5e'
REPORT_SHA='d5218bccd622f6087a80fb4e5ce4b3f8e252d9da7cdafbe747eb4e192c774725'
REPORT_HASH='55902ac40959215482cbf3d0b623da7d64313847f7c58c14a81f867d76f22e90'
REVIEW=DATA/'supplied-shared-call-flop-review.json'
REVIEW_SHA='d80ea68f1316e31539b881c6e4d230785a34367bdbc5cd9a027721cee6d4f8f4'
HANDS=['Ah Kh','As Qs','Qh Th']
SHARING='ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1'

def policy_check(actual, policy, expected_hash, context, actions, roots, key):
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
    regret = math.fsum(strategy[key][a] * loss for a, loss in losses.items())
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


def check(request_path=REQUEST, report_path=REPORT, *, bind_saved_bytes=True, review_path=None):
    request,request_raw,_=read(request_path)
    report,raw,expanded=read(report_path)
    budget,budget_raw,_=read(BUDGET)
    require(hashlib.sha256(budget_raw).hexdigest()==BUDGET_SHA, 'Predeclared shared budget changed')
    require(hashlib.sha256(request_raw).hexdigest()==REQUEST_SHA==budget['requestSha256'], 'Declared ordered request changed')
    gate=ROOT/'solver/src/main/java/com/pokerlab/solver/SixMaxRefinedFlopFeedback.java'
    require(hashlib.sha256(gate.read_bytes()).hexdigest()==budget['unchangedGateSourceSha256'], 'Original qualification gate code changed')
    controls,controls_raw,_=read(coverage.REPORT)
    old,old_raw,_=read(DATA/'supplied-weak-hand-qualified-flop-report.json.gz')
    require(hashlib.sha256(controls_raw).hexdigest()==budget['controlCoverageReportSha256']
            and hashlib.sha256(old_raw).hexdigest()==budget['controlReferenceReportSha256'], 'Original control evidence changed')
    declared=coverage.copy.deepcopy(request)
    declared['parent']['connected']['input']=physical.canonical_input(request['parent']['connected']['input'])
    require(set(request)=={'schemaVersion','parent','intervalAlgorithm','heroHands','publicPreflopHistory','referenceBudget','maximumLossBb'}
            and request['schemaVersion']=='pokerlab-shared-flop-feedback-request/v1'
            and declared['parent']==controls['request']['parent'] and request['heroHands']==HANDS
            and request['intervalAlgorithm']==coverage.INTERVAL_ALGORITHM
            and request['publicPreflopHistory']=='|BB:call' and request['maximumLossBb']==.01
            and request['referenceBudget']==dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',iterations=[80000,160000],totalIterations=240000), 'Unsupported shared request')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','referenceSharing','request','reservation','references','cases','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion']=='pokerlab-shared-flop-feedback-report/v1'
            and report['publicationStatus']=='SHARED_REFERENCE_PHYSICAL_FLOP_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['referenceSharing']==SHARING
            and report['request']==declared, 'Shared batch protocol or game changed')
    require(report['reservation']==dict(physicalGameBuilds=1,conditionalStudies=3,maximumParentSolves=9,
            maximumActionComputations=6,freshReferenceRuns=2,totalReferenceIterations=240000,selectedPolicyAssessments=9,automaticRetries=0), 'Shared work reservation changed')
    require(len(report['cases'])==3 and len(report['references'])==2, 'Missing question or shared reference')
    # Reconstruct all literal physical outcomes once. Each question still checks its own endpoints.
    parent=report['cases'][0]['conditional']['parent']
    root,parent_receipt=coverage.weak.checked_parent(parent,declared['parent'])
    require(parent==controls['cases'][3]['conditional']['parent'], 'Original physical parent differs')
    selected={(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(root.edges)}
    context=dict(root=root,prior=parent['originalJointPrior'],selected=selected)
    def sizes(node):
        children=[sizes(child) for _,child in node.edges]
        return 1+sum(n for n,_ in children),int(node.actor==-2)+sum(t for _,t in children)
    def constant(node,actor):
        require(node.actor!=actor, 'Supposedly inactive seat acts')
        if node.actor==-2:return node.utilities[actor]
        values=[constant(c,actor) for _,c in node.edges]
        require(values and all(v==values[0] for v in values), 'Inactive payoff is not literal constant')
        return values[0]
    nodes,terminals=sizes(root)
    constants={str(actor):constant(root,actor) for actor in [0,1,2,4]}
    require(nodes==1321 and terminals==720, 'Physical reference tree inventory differs')
    reference_pins=[]
    visited=0
    for reference,control,iteration in zip(report['references'],old['references'],[80000,160000],strict=True):
        require(set(reference)=={'policy','work'} and reference['policy']['iterations']==iteration, 'Shared policy identity differs')
        # They solve exactly the same game as the original independently qualified weak-hand source.
        # Equality is a control, not evidence that these newly declared runs may be skipped.
        require(reference['policy']==control['policy'], 'Shared fresh policy differs from literal game control')
        expected=dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',validatedTreeNodes=nodes,
            inactiveUtilities=constants,statistics=dict(visitedNodes=(2*nodes+4)*iteration,
            terminalNodes=2*terminals*iteration,sampledChanceNodes=0,baselineCorrections=0),prunedSuffixRoots=4*iteration)
        require(reference['work']==expected==control['work'], 'Fresh shared pruning proof or work differs')
        reference_pins.append(control['check']['solutionHash'])
        visited+=reference['work']['statistics']['visitedNodes']
    require(visited<=budget['maximumReferenceVisitedNodes'], 'Reference reservation exceeded')
    certificates=0;all_failures=[];classifications=[];compiler=pivots=work=0
    for index,(hand,item) in enumerate(zip(HANDS,report['cases'],strict=True)):
        require(set(item)=={'index','conditional','moves','primary','references','rejectionReasons'} and item['index']==index, 'Question order or fields differ')
        conditional=item['conditional'];control=controls['cases'][2*index+3]['conditional']
        require(conditional==control, 'Original hand-specific interval evidence changed')
        certificates+=coverage.check_solved(conditional['request'],conditional,parent,root)
        moves=[]
        for action in conditional['actions']:
            classification='WITHIN_LIMIT' if action['upperLossBb']<=.01 else 'OUTSIDE_LIMIT' if action['lowerLossBb']>.01 else 'STRATEGY_DEPENDENT'
            moves.append(dict(action=action['action'],lowerEvBb=action['lowerUtilityBb'],upperEvBb=action['upperUtilityBb'],lowerLossBb=action['lowerLossBb'],upperLossBb=action['upperLossBb'],classification=classification))
            audit=action['diagnostic']['work'];compiler+=audit['compilerUnits'];pivots+=audit['intervalLpPivots'];work+=audit['intervalLpArithmeticWork']
        require(item['moves']==moves, 'Question action classifications changed')
        classes={m['classification'] for m in moves};classifications.append([m['classification'] for m in moves])
        roots=[]
        for i,world in enumerate(parent['originalJointPrior']):
            if world['dealtCombos'][3]==hand:
                initial=selected[i,'|BB:call'];roots.append((i,world['conditionalProbability'],initial,dict(initial.edges)['bet']))
        require(len(roots)==6 and len(item['references'])==2, 'Hand or policy assessment inventory differs')
        key=f'3:supplied-flop-one-bet:{hand}:|BB:call:8d Kc Kd:bet'
        failures=['PRIMARY:'+f for f in policy_check(item['primary'],parent['candidate'],parent['solutionHash'],context,conditional['actions'],roots,key)]
        for check,reference,pin in zip(item['references'],report['references'],reference_pins,strict=True):
            failures += [str(reference['policy']['iterations'])+':'+f for f in policy_check(check,reference['policy'],pin,context,conditional['actions'],roots,key)]
        if 'WITHIN_LIMIT' not in classes:failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if 'OUTSIDE_LIMIT' not in classes:failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        require(item['rejectionReasons']==sorted(set(failures)), 'Per-question qualification failures hidden')
        all_failures += [str(index)+':'+f for f in failures]
    require(certificates==24 and compiler<=budget['maximumCompilerUnits'] and pivots<=budget['maximumLpPivots']
            and work<=budget['maximumLpArithmeticWork'] and len(expanded)<=budget['maximumReportExpandedBytes'], 'Batch endpoint work exceeded reservation')
    require(report['rejectionReasons']==sorted(set(all_failures)) and report['qualifiedForOfflineDrill'] is (not all_failures), 'Whole-batch qualification differs')
    if bind_saved_bytes:require(hashlib.sha256(raw).hexdigest()==REPORT_SHA, 'Fixed shared report bytes differ')
    receipt=dict(schemaVersion='pokerlab-independent-shared-call-flop-check/v1',trainerAdmission=False,
        qualifiedForOfflineDrill=not all_failures,reportHash=REPORT_HASH,questionsChecked=3,physicalReconstructions=1,
        endpointCertificatesChecked=certificates,selectedPolicyAssessments=9,sharedReferencePolicies=2,
        declaredFreshReferenceIterations=240000,referenceVisitedNodes=visited,freshLpSolves=0,freshCfrRuns=0,
        compilerUnits=compiler,intervalLpPivots=pivots,intervalLpArithmeticWork=work,classifications=classifications,
        sourceRequestSha256=hashlib.sha256(request_raw).hexdigest(),sourceReportStoredSha256=hashlib.sha256(raw).hexdigest(),
        sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),generationBudgetSha256=BUDGET_SHA,
        checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        endpointHelperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),parentCheck=parent_receipt)
    if review_path is not None:receipt['reviewCheck']=check_review(report,review_path,bind_saved_bytes=bind_saved_bytes)
    return receipt


def check_review(report, review_path=REVIEW, *, bind_saved_bytes=True):
    review,raw,expanded=read(review_path)
    require(len(raw)<=512*1024 and len(expanded)<=512*1024, 'Review exceeds original byte cap')
    require(set(review)=={'schemaVersion','reportHash','attempts','counts'}
            and review['schemaVersion']=='pokerlab-shared-flop-review/v1' and review['reportHash']==REPORT_HASH
            and type(review['attempts']) is list and len(review['attempts'])<=100, 'Review protocol or attempt bound differs')
    template=read(DATA/'supplied-weak-hand-qualified-flop-drill.json')[0]['question']
    status='SHARED_REFERENCE_PHYSICAL_FLOP_DRILL_RESEARCH_ONLY'
    expected=[];seen=set();counts={'WITHIN_LIMIT':0,'OUTSIDE_LIMIT':0,'STRATEGY_DEPENDENT':0}
    for index,attempt in enumerate(review['attempts']):
        require(set(attempt)=={'index','question','action','feedback'} and attempt['index']==index, 'Review attempt fields or ordering differ')
        require(set(attempt['question'])=={'index','totalQuestions','decision'}, 'Review question fields differ')
        question_index=attempt['question']['index']
        require(type(question_index) is int and 0<=question_index<len(report['cases']), 'Review question index differs')
        case=report['cases'][question_index];conditional=case['conditional']
        decision=dict(template,reportHash=REPORT_HASH,publicationStatus=status,
            selection='ORDERED_ALL_QUALIFIED_SHARED_REFERENCE_QUESTIONS/v1',
            informationSet=conditional['informationSet'],heroCards=conditional['request']['heroCards'],context=conditional['context'])
        question=dict(index=question_index,totalQuestions=len(report['cases']),decision=decision)
        menu={m['action']:m for m in case['moves']}
        require(attempt['action'] in menu, 'Review action is not legal')
        move=menu[attempt['action']]
        feedback=dict(reportHash=REPORT_HASH,publicationStatus=status,trainerAdmission=False,
            evScope=conditional['evScope'],maximumLossBb=.01,selected=move,alternatives=case['moves'])
        expected.append(dict(index=index,question=question,action=attempt['action'],feedback=feedback))
        seen.add(question_index);counts[move['classification']]+=1
    expected_counts=dict(withinLimit=counts['WITHIN_LIMIT'],outsideLimit=counts['OUTSIDE_LIMIT'],
        strategyDependent=counts['STRATEGY_DEPENDENT'],distinctQuestions=len(seen),totalQuestions=len(report['cases']))
    require(review['attempts']==expected and review['counts']==expected_counts, 'Canonical shared review grades, questions or coverage differ')
    if bind_saved_bytes:require(hashlib.sha256(raw).hexdigest()==REVIEW_SHA, 'Fixed review bytes differ')
    return dict(attemptsChecked=len(expected),counts=expected_counts,sourceReviewSha256=hashlib.sha256(raw).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path);parser.add_argument('report',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--review',type=Path)
    args=parser.parse_args();require(not args.output.exists(), 'Receipt output must be new')
    result=check(args.request,args.report,review_path=args.review)
    with args.output.open('x',encoding='utf-8',newline='\n') as out:out.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
