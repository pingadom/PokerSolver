"""Independent fixed weak-range coverage: physical parent, 32 endpoint certificates and ordered outcomes.

Rejected-case labels are checked against the captured production inventory, not certified as poker
infeasibility. Full Java replay is required to reproduce those numerical rejections. No fresh CFR,
qualification or grading authority is created here.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-weak-hand-resolvable-study.py'
spec=importlib.util.spec_from_file_location('coverage_weak_helper',HELPER)
weak=importlib.util.module_from_spec(spec);sys.modules[spec.name]=weak;spec.loader.exec_module(weak)
physical,bounds=weak.physical,weak.bounds
require,close,read,dot,vector=weak.require,weak.close,weak.read,weak.dot,weak.vector
face,maximum_utility,certificate=weak.face,weak.maximum_utility,weak.certificate
copy,math=weak.copy,weak.math
EPS,INTERVAL_ALGORITHM=weak.EPS,weak.INTERVAL_ALGORITHM
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-weak-range-flop-coverage-request.json'
REPORT=DATA/'supplied-weak-range-flop-coverage-report.json.gz'
BUDGET=DATA/'weak-range-flop-coverage-generation-budget.json'
HANDS=['2c 3c','Ah Kh','As Qs','Qh Th']
REPORT_SHA='fa5aaab231398deb1f1be7361682750f7479d18ef295f914f3eb6ce8cd1cdd42'
EXPANDED_SHA='ca62571c19098f8390d6c3da6fa990d11c326cd27e0ab5f591196fcd9e1c9adb'


def check_solved(request,report,parent,root):
    hand=request['heroCards'];KEY=f'3:supplied-flop-one-bet:{hand}:|BB:call:8d Kc Kd:bet'
    declared=dict(request,parent=dict(request['parent'],connected=dict(request['parent']['connected'],input=physical.canonical_input(request['parent']['connected']['input']))))
    require(request['intervalAlgorithm']==INTERVAL_ALGORITHM and request['hero']==3 and request['heroCards']==hand and request['flopActions']==['bet'] and request['publicPreflopHistory']=='|BB:call','Explicit weak-hand context differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','evScope','reachUnits','informationSet','parent','context','actions'} and report['schemaVersion']=='pokerlab-refined-flop-conditional-report/v3' and report['publicationStatus']=='REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==declared and report['informationSet']==KEY and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1' and report['reachUnits']=='COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1','Weak-hand report identity differs')
    require(report['parent']==parent, 'Shared physical parent differs')
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
    return certificates


def check(request_path=REQUEST,report_path=REPORT,*,bind_saved_bytes=True):
    request,request_raw,_=read(request_path);report,raw,expanded=read(report_path)
    budget,_,_=read(BUDGET)
    require(budget['requestSha256']==hashlib.sha256(request_raw).hexdigest(), 'Predeclared input differs')
    require(budget==dict(schemaVersion='pokerlab-weak-range-coverage-generation-budget/v1',
        scope='INTERVAL_DISCOVERY_ONLY_NOT_QUALIFICATION',base='a027cab2e1722b1b2bfbcc5fab332478f93b526d',
        requestSha256=budget['requestSha256'],physicalGameBuilds=1,plannedCaseAttempts=8,
        maximumActionComputations=16,maximumParentSolves=24,freshReferenceRuns=0,automaticRetries=0,
        maximumReportExpandedBytes=8388608,allOriginalNumericAndCompilerCapsPreserved=True,
        failedInternalWorkCountersAvailable=False,failedCaseOutcomeDoesNotProvePokerInfeasibility=True),
        'Generation reservation differs')
    require(set(request)=={'schemaVersion','parent','intervalAlgorithm','heroHands','publicPreflopHistory','maximumLossBb'}
        and request['schemaVersion']=='pokerlab-refined-flop-coverage-request/v1'
        and request['heroHands']==HANDS and request['intervalAlgorithm']==INTERVAL_ALGORITHM
        and request['publicPreflopHistory']=='|BB:call' and request['maximumLossBb']==.01,'Discovery selection differs')
    declared=dict(request,parent=dict(request['parent'],connected=dict(request['parent']['connected'],
        input=physical.canonical_input(request['parent']['connected']['input']))))
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','reservation','counts','binding','cases'}
        and report['schemaVersion']=='pokerlab-refined-flop-coverage-report/v1'
        and report['publicationStatus']=='INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and report['trainerAdmission'] is False and report['request']==declared,'Discovery report identity differs')
    require(report['reservation']==dict(physicalGameBuilds=1,caseAttempts=8,maximumActionComputations=16,
        maximumParentSolves=24,freshReferenceRuns=0,automaticRetries=0),'Discovery call reservation differs')
    require(len(report['cases'])==8,'Ordered case coverage differs')
    parent=report['cases'][1]['conditional']['parent']
    root,parent_receipt=weak.checked_parent(parent,request['parent'])
    require(report['binding']==parent['binding'],'Survey physical identity differs')
    old,_,_=read(DATA/'supplied-weak-hand-resolvable-conditional-report.json.gz')
    require(report['cases'][1]['conditional']==old,'Existing weak-fold control changed')
    certificates=0;classifications=[];work=0;pivots=0;compiler=0
    for index,item in enumerate(report['cases']):
        hand,action=HANDS[index//2],['check','bet'][index%2]
        expected=dict(schemaVersion='pokerlab-refined-flop-conditional-request/v1',parent=declared['parent'],
            intervalAlgorithm=INTERVAL_ALGORITHM,hero=3,heroCards=hand,publicPreflopHistory='|BB:call',flopActions=[action])
        require(set(item)=={'index','request','outcome','conditional','moves','rejection'}
            and item['index']==index and item['request']==expected,'Case identity/order differs')
        if action=='check':
            require(item['outcome']=='CONDITIONAL_REJECTED' and item['conditional'] is None and item['moves']==[]
                and item['rejection']==dict(layer='CONDITIONAL',reason='NUMERICAL_FAILURE',
                    detail='Resolvable refined endpoint witness failed: Invalid behavioral ratio',reachFailure=None),
                'Captured after-check numerical rejection was hidden or relabelled')
            continue
        require(item['outcome']=='ACTION_SEPARATION' and item['rejection'] is None,'Solved case state differs')
        conditional=item['conditional']
        certificates+=check_solved(expected,conditional,parent,root)
        moves=[]
        for a in conditional['actions']:
            classification='WITHIN_LIMIT' if a['upperLossBb']<=.01 else 'OUTSIDE_LIMIT' if a['lowerLossBb']>.01 else 'STRATEGY_DEPENDENT'
            moves.append(dict(action=a['action'],lowerEvBb=a['lowerUtilityBb'],upperEvBb=a['upperUtilityBb'],
                lowerLossBb=a['lowerLossBb'],upperLossBb=a['upperLossBb'],classification=classification))
        require(item['moves']==moves and {m['classification'] for m in moves}=={'WITHIN_LIMIT','OUTSIDE_LIMIT'},'Move classification differs')
        classifications.append(dict(hand=hand,action=next(m['action'] for m in moves if m['classification']=='WITHIN_LIMIT')))
        parent_work=[parent['solve'][name+'Lp']['work'] for name in ['first','second']]
        work+=3*sum(w['arithmeticWork'] for w in parent_work)+sum(a['diagnostic']['work']['intervalLpArithmeticWork'] for a in conditional['actions'])
        pivots+=3*sum(sum(w[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots']) for w in parent_work)+sum(a['diagnostic']['work']['intervalLpPivots'] for a in conditional['actions'])
        compiler+=3*parent['solve']['reductionWork']['chargedUnits']+sum(a['diagnostic']['work']['compilerUnits'] for a in conditional['actions'])
    require(report['counts']==dict(planned=8,solved=4,actionSeparated=4,conditionalRejected=4,lpRejected=0),'Counts differ')
    require(certificates==32 and len(expanded)<=8388608,'Evidence coverage or size cap differs')
    if bind_saved_bytes:
        require(hashlib.sha256(raw).hexdigest()==REPORT_SHA and hashlib.sha256(expanded).hexdigest()==EXPANDED_SHA,'Frozen report identity differs')
    return dict(schemaVersion='pokerlab-independent-weak-range-coverage/v1',trainerAdmission=False,
        scope='FIXED_INTERVAL_DISCOVERY_NOT_FRESH_REFERENCE_QUALIFICATION',freshCfrRuns=0,freshLpSolves=0,
        plannedCases=8,completedCasesChecked=4,endpointCertificatesChecked=certificates,capturedNumericalRejections=4,
        numericalRejectionsIndependentlyCertified=False,numericalRejectionsRequireProductionReplay=True,
        successfulCaseLpArithmeticWork=work,successfulCaseLpPivots=pivots,successfulCaseCompilerUnits=compiler,
        failedInternalWorkCountersAvailable=False,withinLimitMoves=classifications,parentCheck=parent_receipt,
        requestSha256=hashlib.sha256(request_raw).hexdigest(),reportStoredSha256=hashlib.sha256(raw).hexdigest(),
        reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        helperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),budgetSha256=hashlib.sha256(BUDGET.read_bytes()).hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path);parser.add_argument('report',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Output must be new')
    receipt=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8',newline='\n') as out: out.write(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))


if __name__=='__main__': main()
