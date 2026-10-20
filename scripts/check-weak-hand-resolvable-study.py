"""Independent fixed 2c3c v8 research: regenerate all boards and certify the full tree and LPs."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
import subprocess
import os
from functools import lru_cache

ROOT = Path(__file__).resolve().parents[1]
PARENT_HELPER = ROOT/'scripts/check-refined-parent-study.py'
spec = importlib.util.spec_from_file_location('basis_conditional_parent', PARENT_HELPER)
parent_check = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = parent_check
spec.loader.exec_module(parent_check)
FACE_HELPER = ROOT/'scripts/check-btn-flop-feedback.py'
spec = importlib.util.spec_from_file_location('basis_conditional_face', FACE_HELPER)
btn = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = btn
spec.loader.exec_module(btn)
physical = parent_check.physical_helper
bounds = parent_check.bounds
require, close, read, dot, vector = bounds.require, bounds.close, bounds.read, bounds.dot, bounds.vector
face, maximum_utility = btn.face, btn.maximum_utility
REQUEST = ROOT/'docs/data/supplied-weak-hand-resolvable-conditional-request.json'
REPORT = ROOT/'docs/data/supplied-weak-hand-resolvable-conditional-report.json.gz'
BUDGET = ROOT/'docs/data/weak-hand-resolvable-study-budget.json'
CARD_REFERENCE = ROOT/'scripts/CheckWeakHandPhysicalCounts.java'
INTERVAL_ALGORITHM = 'OWNED_PHYSICAL_FLOP_ASCENDING_PARENT_RESOLVABLE_REFINED_BASIS_ENDPOINTS/v8'
LP = 'OWNED_ASCENDING_L1_RESOLVABLE_COST_COMPENSATED_BASIS_DUAL_DIRECTION_ORIGINAL_CERTIFICATE/v6'
REPORT_SHA = 'acb0992ee3464295fda59f179f6c8c38290eda9fce140ef86804ec719be5df3a'
EPS = 1e-8

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()

@lru_cache(maxsize=4)
def full_counts(worlds):
    java = str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if 'JAVA_HOME' in os.environ else 'java'
    lines = [' '.join(cards)+' 8d Kc Kd' for cards in worlds]
    result = subprocess.run([java,str(CARD_REFERENCE)],input='\n'.join(lines)+'\n',text=True,capture_output=True,check=True,timeout=180)
    counts = [tuple(map(int,line.split(','))) for line in result.stdout.splitlines()]
    require(len(counts)==24 and all(len(c)==6 and min(c)>=0 and sum(c[:3])==658008 and sum(c[3:])==666 for c in counts),'Independent card inventory differs')
    return counts


def checked_parent(parent, request):
    connected = dict(request['connected'],input=physical.canonical_input(request['connected']['input']))
    require(set(parent)=={'schemaVersion','publicationStatus','trainerAdmission','request','binding','originalJointPrior','sourcePayoffs','handoffs','residuals','solutionHash','candidate','solve','firstLpWitness','secondLpWitness'} and
            parent['schemaVersion']=='pokerlab-refined-parent-flop-report/v1' and parent['publicationStatus']=='REFINED_SINGLE_FLOP_PARENT_RESEARCH_ONLY' and parent['trainerAdmission'] is False and parent['request']==dict(request,connected=connected), 'New physical parent identity differs')
    worlds = connected['input']['worlds']
    require(len(worlds)==24 and all(w['weight']==1.0 for w in worlds),'Fixed joint worlds differ')
    prior = [dict(dealIndex=i,dealtCombos=w['dealtCombos'],conditionalProbability=1/24) for i,w in enumerate(worlds)]
    require(parent['originalJointPrior']==prior,'Original joint prior differs')
    counts=full_counts(tuple(tuple(w['dealtCombos']) for w in worlds))
    expected = [dict(dealIndex=i,dealtCombos=w['dealtCombos'],counts=dict(boards=658008,firstSeat=3,secondSeat=5,firstWins=c[0],secondWins=c[1],ties=c[2],handEvaluations=1316016)) for i,(w,c) in enumerate(zip(worlds,counts,strict=True))]
    require(parent['sourcePayoffs']==expected,'Independent complete preflop physical counts differ')
    source_budget=dict(completeTreeStates=361,enumeratedBoards=15792192,handEvaluations=31584384,informationSets=dict(BTN=8,BB=9),jointDeals=24,publicStates=15,sequences=dict(BTN=25,BB=25))
    source=dict(beliefs='EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1',budget=source_budget,inputHash='ad38e633f247e0746498cf719c48b13e15e50981ad5aea4125630280f547c5c4',jointPriorHash='349b805035540bfd40e7fb21d2f0ef88b5a8ce94622c89d4a999a9dda24539a7',model='TWO_ACTIVE_SUPPLIED_JOINT_PREFLOP_MANDATORY_CHECKDOWN/v1',payoffAlgorithm='EXACT_TWO_ACTIVE_SIX_DEALT_BOARD_ENUMERATION/v1',payoffHash='3e46f65af73aeaaf3e552669645d92f01f8c40717b364b6a80c81ba450d98fa3',sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY')
    b=parent['binding']
    require(b==dict(betPotFraction=.5,budget=dict(additionalEnumeratedRunouts=63936,additionalHandEvaluations=127872,compatibleDeals=24,completeTreeStates=1321,informationSets=dict(BTN=40,BB=33),nonAllInBoundaries=4,sequences=dict(BTN=89,BB=73),sourceBudget=source_budget),chanceModel='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1',flop=['8d','Kc','Kd'],handoffsHash='72146da329feb0402fe896310d54ba7a16469d0468c3dcb6d5d601b88bcea994',model='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1',residualsHash='08f7622281384f18455851ba7f07390c5872b0fb3ec7bd35ba51e449a311b37c',sourceBinding=source,sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY',trainerAdmission=False),'New physical model binding differs')
    policy=parent['candidate']['strategy'];audit=parent['solve']
    require(parent['candidate']['iterations']==1 and len(policy)==73 and audit['algorithm']==parent_check.AFFINE and audit['treeNodes']==1321 and audit['firstActor']==3 and audit['secondActor']==5 and len(audit['informationSets'])==73,'Parent solver or complete tree inventory differs')
    require(audit['reductionWork']['limit']==8_000_000 and 0<=audit['reductionWork']['chargedUnits']<=8_000_000,'Parent reduction budget differs')
    for row in policy.values():
        require(all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Invalid parent probability')
        close(math.fsum(row.values()),1,'Parent row not normalized')
    root=parent_check.literal_tree(parent,dict(binding=source),connected)
    for i,c in enumerate(counts):
        literal=parent_check.count_cached(json.dumps(prior[i],sort_keys=True))
        require(tuple(literal)==c[3:],'Independent mask evaluator differs from literal best-of-21 fixed-flop evaluator')
    bounds.payoff(root,audit)
    for first,name in [(True,'first'),(False,'second')]:
        a,b,c=parent_check.lp_problem(audit,first);witness=parent[name+'LpWitness']
        parent_check.certificate(a,b,c,witness,audit[name+'Lp'])
        parent_check.flow_policy(audit[name+'Flow'],audit[name+'Projection'],witness,policy)
    q=parent_check.quality(root,policy)
    physical.compare_quality(audit['behavioralQuality'],q)
    require(q[3]<=EPS,'Independent full-parent best-response gap exceeds gate')
    close(audit['lowerValue'],q[0][3],'Parent lower differs');close(audit['upperValue'],q[1][3],'Parent upper differs')
    require(parent['solutionHash']==audit['behavioralPolicyHash'],'Parent policy identity differs')
    return root,dict(originalLpCertificatesIndependentlyChecked=2,physicalWorlds=24,fullBoardsRegenerated=15792192,fullBoardHandEvaluations=31584384,fixedRunoutsRegenerated=15984,literalTreeStates=1321,policyInformationSets=73,nashConvBb=q[3],cardReferenceSha256=digest(CARD_REFERENCE))

def certificate(matrix,rhs,cost,witness):
    x,y,c=witness['point'],witness['dual'],witness['certificate']
    require(c['algorithm']==LP and c['variables']==len(x)==len(cost)<=512 and c['constraints']==len(y)==len(rhs)<=512,'LP identity or dimensions differ')
    require(all(math.isfinite(v) for v in x+y) and all(math.isfinite(v) and abs(v)<=1_000_000 for row in matrix for v in row) and all(math.isfinite(v) and abs(v)<=1_000_000 for v in rhs+cost),'Invalid original LP coefficients/witness')
    dot=bounds.dot
    pv,dv=dot(cost,x),dot(rhs,y)
    primal=max([0]+[-v for v in x]+[dot(a,x)-b for a,b in zip(matrix,rhs,strict=True)])
    dual=max([0]+[-v for v in y]+[cost[j]-dot([a[j] for a in matrix],y) for j in range(len(cost))])
    require(max(primal,dual,abs(pv-dv))<=1e-8,'Original LP primal/dual certificate failed')
    for k,v in dict(primalValue=pv,dualValue=dv,maximumPrimalViolation=primal,maximumDualViolation=dual,absoluteDualityGap=abs(pv-dv)).items():close(c[k],v,'Original certificate field differs')
    work=c['work']
    require(0<=work['arithmeticWork']<=4_000_000_000 and 0<=sum(work[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots'])<=10000,'Original work limits changed')


def check(request_path=REQUEST, report_path=REPORT, *, bind_saved_bytes=True):
    request,request_bytes,_=read(request_path)
    report,raw,expanded=read(report_path)
    budget,_,_=read(BUDGET)
    require(hashlib.sha256(request_bytes).hexdigest()==budget['requestSha256'],'Predeclared weak-hand request differs')
    hand='2c 3c';KEY=f'3:supplied-flop-one-bet:{hand}:|BB:call:8d Kc Kd:bet'
    declared=dict(request,parent=dict(request['parent'],connected=dict(request['parent']['connected'],input=physical.canonical_input(request['parent']['connected']['input']))))
    require(request['intervalAlgorithm']==INTERVAL_ALGORITHM and request['hero']==3 and request['heroCards']==hand and request['flopActions']==['bet'] and request['publicPreflopHistory']=='|BB:call','Explicit weak-hand context differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','evScope','reachUnits','informationSet','parent','context','actions'} and report['schemaVersion']=='pokerlab-refined-flop-conditional-report/v3' and report['publicationStatus']=='REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==declared and report['informationSet']==KEY and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1' and report['reachUnits']=='COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1','Weak-hand report identity differs')
    parent=report['parent']
    root,parent_receipt=checked_parent(parent,request['parent'])
    context = dict(root=root, prior=parent['originalJointPrior'])
    handoff = parent['handoffs'][0]
    seats = ['UTG', 'HJ', 'CO', 'BTN', 'SB', 'BB']
    committed = list(handoff['committedBb']); committed[5] += 3.25
    players = [dict(seat=s, status='FOLDED' if handoff['folded'][i] else 'ACTING' if i == 3 else 'ACTIVE',
                    committedBb=committed[i], remainingStackBb=100-committed[i]) for i, s in enumerate(seats)]
    expected_context = dict(actingSeat='BTN', board=['8d','Kc','Kd'], publicPreflopHistory='|BB:call',
            preflopHistory=handoff['completePreflopHistory'], flopHistory=[dict(seat='BB', action='bet', amountBb=3.25)],
            potBb=9.75, toCallBb=3.25, betSizeBb=3.25, players=players, legalActions=['fold','call'])
    require(report['context'] == expected_context, 'Paired literal public table context differs')
    roots = []
    for i, ((_, preflop), world) in enumerate(zip(root.edges, context['prior'], strict=True)):
        if world['dealtCombos'][3] != hand: continue
        initial = dict(preflop.edges)['call'].edges[0][1]
        reply = dict(initial.edges)['bet']
        require(reply.key == KEY, 'Literal paired question differs')
        roots.append((i, world['conditionalProbability'], initial, reply))
    require(len(roots) == 6, 'Paired compatible hero worlds differ')
    baseline = parent['solve']
    bounds.payoff(context['root'], baseline)
    actions = report['actions']
    require([a['action'] for a in actions] == ['fold', 'call'], 'BTN action menu differs')
    certificates = 0
    for action in actions:
        audit = action['diagnostic']
        require(audit['algorithm'] == INTERVAL_ALGORITHM and audit['trainerAdmission'] is False
                and audit['baseline'] == baseline and audit['securitySlack'] == EPS and audit['requiredMinimumReach'] == .0001
                and audit['question'] == dict(hero=3, informationSet=KEY, action=action['action'])
                and audit['scope'] == 'ACTION_CONDITIONED_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1'
                and audit['fixedHeroPast'] == [], 'BTN interval scope differs')
        m, b, w = face(audit, baseline)
        flow = audit['minimumReachWitness']['opponentFlow']
        indices = {(s['key'], s['action']): s['index'] for s in flow['sequences']}
        opponent = audit['opponentProjection']
        coefficients = [0.0] * len(flow['sequences'])
        sequences = []
        numerator = [0.0] * len(coefficients)
        for _, p, initial, reply in roots:
            sequence = indices[initial.key, 'bet']
            sequences.append(sequence)
            coefficients[sequence] += p
            child = dict(reply.edges)[action['action']]
            require(child.actor == -2, 'Fold/call must terminate the one-bet round')
            numerator[sequence] += p * child.utilities[3]
        dc = dot(coefficients, opponent['offset'])
        d = [dot(coefficients, [row[j] for row in opponent['transform']]) for j in range(w)]
        close(audit['denominator']['constant'], dc, 'Opponent path denominator constant differs')
        vector(audit['denominator']['cost'], d)
        require(len(audit['numeratorPlans']) == 1 and audit['numeratorPlans'][0]['continuation'] == {}, 'BTN continuation plan differs')
        plan = audit['numeratorPlans'][0]
        close(plan['constant'], dot(numerator, opponent['offset']), 'BTN numerator constant differs')
        vector(plan['cost'], [dot(numerator, [row[j] for row in opponent['transform']]) for j in range(w)])
        n, shift = len(m[0]), action['upperObjectiveShift']
        close(shift, -maximum_utility(context['root']) - 1, 'Original objective shift differs')
        scaled = [row + [-rhs] for row, rhs in zip(m, b, strict=True)]
        scaled += [d + [0] * (n - w) + [dc], [-v for v in d] + [0] * (n - w) + [-dc], [0] * n + [1]]
        sb = [0] * len(b) + [1, -1, 10000]
        for sign, name in [(-1, 'minimumReachWitness'), (1, 'maximumReachWitness')]:
            certificate(m, b, [sign * v for v in d] + [0] * (n - w), audit[name]); certificates += 1
        require(len(audit['upperWitnesses']) == 1, 'BTN endpoint coverage differs')
        upper_cost = [c + shift * v for c, v in zip(plan['cost'], d, strict=True)] + [0] * (n - w) + [plan['constant'] + shift * dc]
        certificate(scaled, sb, upper_cost, audit['upperWitnesses'][0]); certificates += 1
        lower = [row + [0] for row in scaled] + [plan['cost'] + [0] * (n - w) + [plan['constant'], 1]]
        certificate(lower, sb + [-shift], [0] * (n + 1) + [1], audit['lowerWitness']); certificates += 1
        close(action['lowerUtilityBb'], -shift - audit['lowerWitness']['certificate']['dualValue'] - EPS, 'BTN padded lower differs')
        close(action['upperUtilityBb'], audit['upperWitnesses'][0]['certificate']['dualValue'] - shift + EPS, 'BTN padded upper differs')
        close(audit['lowerUtility'], -shift - audit['lowerWitness']['certificate']['primalValue'], 'BTN raw lower differs')
        close(audit['upperUtility'], audit['upperWitnesses'][0]['certificate']['primalValue'] - shift, 'BTN raw upper differs')
        require(audit['upperPlan'] == 0, 'BTN upper plan differs')
        close(audit['certifiedReachLowerBound'], dc - audit['minimumReachWitness']['certificate']['dualValue'] - EPS, 'BTN lower reach differs')
        close(audit['certifiedReachUpperBound'], dc + audit['maximumReachWitness']['certificate']['dualValue'] + EPS, 'BTN upper reach differs')
        require(audit['certifiedReachLowerBound'] >= .0001, 'BTN full-face reach insufficient')
        for field in ['Lower', 'Upper']:
            close(action['rawCertifiedReach' + field + 'Bound'], physical.P * audit['certifiedReach' + field + 'Bound'], 'BTN raw reach units differ', mass=True)
        witnesses = [audit['minimumReachWitness'], audit['maximumReachWitness'], audit['lowerWitness'], *audit['upperWitnesses']]
        for witness in witnesses:
            scaled_witness = len(witness['point']) > n
            scaling = witness['point'][n] if scaled_witness else 1
            require(math.isfinite(scaling) and 0 < scaling <= 10000 + EPS, 'BTN fractional scaling invalid')
            close(witness['scaling'], scaling, 'BTN fractional scaling differs')
            vector(witness['opponentVariables'], [v / scaling for v in witness['point'][:w]])
            f = witness['opponentFlow']
            require(f['actor'] == 5 and f['sequences'] == baseline['secondFlow']['sequences'] and f['conservation'] == baseline['secondFlow']['conservation'], 'BTN opponent topology differs')
            realization = f['realization']
            vector(realization, [c + dot(row, witness['opponentVariables']) for c, row in zip(opponent['offset'], opponent['transform'], strict=True)])
            close(realization[0], 1, 'Original opponent root differs')
            require(min(realization) >= -EPS, 'Negative original realization')
            policy = copy.deepcopy(parent['candidate']['strategy'])
            for row in f['conservation']:
                ids, mass = row['childSequences'], realization[row['parentSequence']]
                close(sum(realization[i] for i in ids), mass, 'Original opponent conservation differs')
                weights = [min(1, max(0, realization[i] / mass)) if mass > 1e-12 else 1 / len(ids) for i in ids]
                total = sum(weights)
                policy[row['key']] = {f['sequences'][i]['action']: p / total for i, p in zip(ids, weights, strict=True)}
            br = physical.best_response(context['root'], policy, 3)
            close(witness['globalHeroBestResponse'], br, 'Original full-parent witness response differs')
            require(br <= audit['globalHeroUpperValue'] + EPS + EPS, 'BTN witness violates full-parent security')
            weights = [p * policy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call'] * policy[initial.key]['bet']
                       for i, p, initial, _ in roots]
            reach = math.fsum(weights)
            close(witness['questionReach'], reach, 'BTN behavioral reach differs')
            close(dc + dot(d, witness['opponentVariables']), reach, 'Original projected path reach differs')
            if scaled_witness:
                close(reach * scaling, 1, 'BTN fractional normalization differs')
            forced = physical.Node(-1, edges=tuple((p / reach, dict(root[3].edges)[action['action']]) for root, p in zip(roots, weights, strict=True)))
            close(witness['conditionalHeroBestResponse'], physical.best_response(forced, policy, 3), 'BTN witness conditional response differs')
            require(len(witness['posterior']) == len(roots), 'BTN posterior coverage differs')
            linear_total = math.fsum(root[1] * realization[sequence] for root, sequence in zip(roots, sequences, strict=True))
            tv = math.fsum(.5 * abs(weight / reach - root[1] * realization[sequence] / linear_total)
                          for root, sequence, weight in zip(roots, sequences, weights, strict=True))
            close(witness['posteriorTotalVariation'], tv, 'BTN posterior total variation differs')
            require(tv <= EPS, 'BTN original and behavioral posteriors disagree')
            for i, (row, root, sequence, weight) in enumerate(zip(witness['posterior'], roots, sequences, weights, strict=True)):
                require(row['rootIndex'] == i and row['opponentSequence'] == sequence, 'Opponent past sequence differs')
                for field, expected in [('chanceWeight', root[1]), ('originalRealizationWeight', root[1] * realization[sequence]),
                                        ('behavioralWeight', weight), ('probability', weight / reach)]:
                    close(row[field], expected, 'BTN witness posterior or cancelled hero path differs', mass=True)
        require(audit['work']['intervalLpSolves'] == len(witnesses)
                and audit['work']['intervalLpArithmeticWork'] == sum(x['certificate']['work']['arithmeticWork'] for x in witnesses)
                and audit['work']['intervalLpArithmeticWork'] <= 4_000_000_000
                and audit['work']['intervalLpPivots'] == sum(sum(x['certificate']['work'][k]
                    for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) for x in witnesses)
                and audit['work']['intervalLpPivots'] <= 10000
                and 0 <= audit['work']['compilerUnits'] <= audit['work']['compilerLimit'] <= 8_000_000,
                'Cumulative BTN LP work differs')
    best_lower, best_upper = max(a['lowerUtilityBb'] for a in actions), max(a['upperUtilityBb'] for a in actions)
    for action in actions:
        close(action['lowerLossBb'], max(0, best_lower-action['upperUtilityBb']), 'Conditional loss lower differs')
        close(action['upperLossBb'], max(0, best_upper-action['lowerUtilityBb']), 'Conditional loss upper differs')
    spec=importlib.util.spec_from_file_location('weak_hand_exact_lp',ROOT/'scripts/check-resolvable-refined-lp.py')
    exact=importlib.util.module_from_spec(spec);spec.loader.exec_module(exact)
    problem,_=exact.read(ROOT/'solver/src/test/resources/lp-resolvable-refined-weak-hand.json.gz')
    w=actions[1]['diagnostic']['lowerWitness']
    exact.exact(problem['matrix'],problem['rhs'],problem['cost'],w['point'],w['dual'])
    require(actions[0]['upperLossBb']<=budget['maximumLossBb'] and actions[1]['lowerLossBb']>2.24,'Actual weak-hand action classification differs')
    parent_work=sum(parent['solve'][name+'Lp']['work']['arithmeticWork'] for name in ['first','second'])
    parent_pivots=sum(sum(parent['solve'][name+'Lp']['work'][k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots']) for name in ['first','second'])
    total_work=3*parent_work+sum(a['diagnostic']['work']['intervalLpArithmeticWork'] for a in actions)
    total_pivots=3*parent_pivots+sum(a['diagnostic']['work']['intervalLpPivots'] for a in actions)
    compiler=3*parent['solve']['reductionWork']['chargedUnits']+sum(a['diagnostic']['work']['compilerUnits'] for a in actions)
    require(total_work<=budget['maximumLpArithmeticWork'] and total_pivots<=budget['maximumLpPivots'] and compiler<=budget['maximumCompilerUnits'],'Predeclared full-study work cap exceeded')
    if bind_saved_bytes: require(hashlib.sha256(raw).hexdigest()==REPORT_SHA,'Fixed weak-hand report bytes differ')
    return dict(schemaVersion='pokerlab-independent-weak-hand-resolvable-check/v1',trainerAdmission=False,scope='FIXED_TWO_ACTIVE_RESEARCH_NOT_TRAINER_QUALIFICATION',freshLpSolves=0,freshCfrRuns=0,originalEndpointCertificatesChecked=certificates,actionsChecked=2,actualWithinLimitAction='fold',callMinimumLossBb=actions[1]['lowerLossBb'],fullParentAndFullPathReachChecked=True,literalPublicContextChecked=True,exactCapturedBinary64WitnessChecked=True,studyLpArithmeticWork=total_work,studyLpPivots=total_pivots,studyCompilerUnits=compiler,parentCheck=parent_receipt,sourceRequestSha256=hashlib.sha256(request_bytes).hexdigest(),sourceReportStoredSha256=hashlib.sha256(raw).hexdigest(),sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),checkerSha256=digest(Path(__file__)),parentHelperSha256=digest(PARENT_HELPER),faceHelperSha256=digest(FACE_HELPER),budgetSha256=digest(BUDGET))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path);parser.add_argument('report',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Output receipt must be new')
    receipt=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8',newline='\n') as output:output.write(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))


if __name__ == '__main__': main()
