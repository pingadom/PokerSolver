"""Independent v7 four-hand paired-board endpoint certificates and literal physical posteriors."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

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
PARENT_REQUEST = ROOT/'docs/data/supplied-paired-flop-refined-parent-request.json'
PARENT_REPORT = ROOT/'docs/data/supplied-paired-flop-refined-parent-report.json.gz'
INTERVAL_ALGORITHM = 'OWNED_PHYSICAL_FLOP_ASCENDING_PARENT_REFINED_BASIS_ENDPOINTS/v7'
LP = 'OWNED_ASCENDING_L1_COMPENSATED_BASIS_DUAL_DIRECTION_STRICT_RATIOS_ORIGINAL_CERTIFICATE/v5'
HAND_PINS = {'9s Js': ['f89d3504f13527a033c9b6b321d51cdccbe73fd78f58fa0620e3a0d7c94bb1ed', 'd8867a62f1fb7c26e9353290d25834bff87d15b433905ec54c69d981267228eb'], 'Ah Kh': ['d2f8afb5f155965413c0963b74fcca085ea9e2509c6e5fce9988f3055d381788', 'f9223ea564dfd4d731233e72a5cfcd59aa1ffd4bd2dd228439978b56fde163e8'], 'Qh Th': ['5e4c95d7c5461a57f0acd600509aa0c8bc792ec7ec359d34e9ed8d079e7c1e19', 'c14e80cc1e12e5bc62b9f1cb3adcdf94f541f0f2e478cb567978ffcc80df26a1'], 'As Qs': ['817534048a0467dfeaa808f584b69ca81380789d672790309c658d48766e5776', 'dd0a7acd26f6b4003812bb9a23052f8d481b603c350cef884f9c75633d6f462d']}
REGRESSION_SHA = 'fe3cea1ae6ea499fbeac3442decbae0f1b3af8ea97a8771423840c3c398eda06'
EPS = 1e-8


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


def check(request_path, report_path, *, bind_saved_bytes=True):
    fixture = ROOT/'solver/src/test/resources/lp-refined-basis-endpoints.json.gz'
    require(hashlib.sha256(fixture.read_bytes()).hexdigest() == REGRESSION_SHA, 'Captured regression fixture differs')
    parent_receipt = parent_check.check(PARENT_REQUEST, PARENT_REPORT)
    parent, _, _ = read(PARENT_REPORT)
    request, request_bytes, _ = read(request_path)
    report, raw, expanded = read(report_path)
    hand = request['heroCards']
    require(hand in HAND_PINS, 'Unsupported fixed basis hand')
    KEY = f'3:supplied-flop-one-bet:{hand}:|BB:call:8d Kc Kd:bet'
    REQUEST_SHA, REPORT_SHA = HAND_PINS[hand]
    original_parent, _, _ = read(physical.PARENT)
    declared = dict(request)
    declared['parent'] = dict(request['parent'])
    declared['parent']['connected'] = dict(request['parent']['connected'], input=physical.canonical_input(request['parent']['connected']['input']))
    require(set(request) == {'schemaVersion', 'parent', 'intervalAlgorithm', 'hero', 'heroCards', 'publicPreflopHistory', 'flopActions'}
            and request['schemaVersion'] == 'pokerlab-refined-flop-conditional-request/v1'
            and declared['parent'] == parent['request'] and request['intervalAlgorithm'] == INTERVAL_ALGORITHM
            and request['hero'] == 3 and request['heroCards'] == hand
            and request['publicPreflopHistory'] == '|BB:call' and request['flopActions'] == ['bet'], 'Unsupported fixed paired conditional request')
    require(set(report) == {'schemaVersion', 'publicationStatus', 'trainerAdmission', 'request', 'evScope', 'reachUnits', 'informationSet', 'parent', 'context', 'actions'}
            and report['schemaVersion'] == 'pokerlab-refined-flop-conditional-report/v2'
            and report['publicationStatus'] == 'REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request'] == declared and report['parent'] == parent
            and report['informationSet'] == KEY
            and report['evScope'] == 'ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
            and report['reachUnits'] == 'COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1', 'Paired conditional identity differs')
    root = parent_check.literal_tree(parent, original_parent, parent['request']['connected'])
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
    exact_captured = False
    if hand != 'As Qs':
        from fractions import Fraction as F
        import gzip
        fixture = ROOT/'solver/src/test/resources/lp-refined-basis-endpoints.json.gz'
        cases = json.loads(gzip.decompress(fixture.read_bytes()))['cases']
        label = {'9s Js':'js9s','Ah Kh':'ahkh','Qh Th':'qhth'}[hand]
        original = next(c for c in cases if c['id'] == label)
        witness = actions[1]['diagnostic']['lowerWitness']
        x, y = list(map(F,witness['point'])), list(map(F,witness['dual']))
        a = [list(map(F,row)) for row in original['matrix']]
        b, c = list(map(F,original['rhs'])), list(map(F,original['cost']))
        exact_dot = lambda u,v: sum((i*j for i,j in zip(u,v,strict=True)),F(0))
        violations = [F(0)]+[-v for v in x]+[-v for v in y]
        violations += [exact_dot(row,x)-rhs for row,rhs in zip(a,b,strict=True)]
        violations += [cost-exact_dot(col,y) for col,cost in zip(zip(*a),c,strict=True)]
        violations += [abs(exact_dot(c,x)-exact_dot(b,y))]
        require(max(violations) <= F(EPS), 'Exact captured original binary64 witness fails')
        exact_captured = True
    if bind_saved_bytes:
        require(hashlib.sha256(request_bytes).hexdigest() == REQUEST_SHA and hashlib.sha256(raw).hexdigest() == REPORT_SHA, 'Fixed paired conditional byte binding differs')
    return dict(schemaVersion='pokerlab-independent-refined-basis-conditional-check/v1', trainerAdmission=False,
                regressionFixtureSha256=REGRESSION_SHA, freshLpSolves=0, freshCfrRuns=0, exactCapturedBinary64WitnessChecked=exact_captured, originalEndpointCertificatesChecked=certificates, actionsChecked=2,
                fullParentAndFullPathReachChecked=True, literalPublicContextChecked=True,
                sourceRequestSha256=hashlib.sha256(request_bytes).hexdigest(), sourceReportStoredSha256=hashlib.sha256(raw).hexdigest(),
                sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),
                checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                parentHelperSha256=hashlib.sha256(PARENT_HELPER.read_bytes()).hexdigest(),
                faceHelperSha256=hashlib.sha256(FACE_HELPER.read_bytes()).hexdigest(), parentCheck=parent_receipt)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path);parser.add_argument('report',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Output receipt must be new')
    receipt=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8',newline='\n') as output:output.write(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))


if __name__ == '__main__': main()
