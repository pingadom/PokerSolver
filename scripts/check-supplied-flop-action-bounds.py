"""Independent fixed-case physical payoff compilation, endpoint LP and behavioral checks; no optimizer."""
import argparse
import copy
import hashlib
import importlib.util
import itertools
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-supplied-connected-flop.py'
spec = importlib.util.spec_from_file_location('physical_flop_bounds_helper', HELPER)
physical = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = physical
spec.loader.exec_module(physical)
PARENT = ROOT / 'docs/data/supplied-four-button-hand-connected-flop-report.json.gz'
PARENT_SHA = '9f02bd48287ed22d64c397d3b2b504a514f0fd4da89ab59808c69fac7d307319'
ALGORITHM = 'OWNED_ASCENDING_L1_RESOLVABLE_COST_EXACT_RATIO_ORIGINAL_CERTIFICATE/v3'
EPS = 1e-8
require, close, read = physical.require, physical.close, physical.read


def dot(a, b):
    return math.fsum(x*y for x, y in zip(a, b, strict=True))


def vector(a, b):
    require(len(a) == len(b), 'Vector dimension differs')
    for x, y in zip(a, b):
        close(x, y, 'Independent vector differs')


def projection(flow, actual):
    rows, sequences = flow['conservation'], flow['sequences']
    require([s['index'] for s in sequences] == list(range(len(sequences))), 'Sequence inventory differs')
    independent = [i for r in rows for i in r['childSequences'][:-1]]
    n, v = len(sequences), len(independent)
    offset = [1]+[0]*(n-1)
    transform = [[0]*v for _ in range(n)]
    inequalities = [[0]*v for _ in rows]
    rhs = [0]*len(rows)
    available, done = {0}, set()
    while len(done) < len(rows):
        start = len(done)
        for j, row in enumerate(rows):
            p, children = row['parentSequence'], row['childSequences']
            if j in done or p not in available:
                continue
            last = children[-1]
            offset[last] = offset[p]
            transform[last] = transform[p].copy()
            inequalities[j] = [-c for c in transform[p]]
            rhs[j] = offset[p]
            for child in children[:-1]:
                k = independent.index(child)
                transform[child][k] = 1
                transform[last][k] -= 1
                inequalities[j][k] += 1
            available.update(children)
            done.add(j)
        require(len(done) > start, 'Disconnected flow')
    for key, value in dict(independentSequences=independent, offset=offset, transform=transform,
                           inequalities=inequalities, rhs=rhs).items():
        require(actual[key] == value, 'Literal projection differs: '+key)


def payoff(root, baseline):
    flows = [baseline['firstFlow'], baseline['secondFlow']]
    projections = [baseline['firstProjection'], baseline['secondProjection']]
    for f, p in zip(flows, projections):
        projection(f, p)
    indices = [{(s['key'], s['action']): s['index'] for s in f['sequences']} for f in flows]
    infos = [{r['key']: r for r in f['conservation']} for f in flows]
    a = [[0.0]*len(flows[1]['sequences']) for _ in flows[0]['sequences']]

    def visit(node, x=0, y=0, chance=1):
        if node.actor == -2:
            a[x][y] += chance*node.utilities[3]
            return
        for action, child in node.edges:
            if node.actor == -1:
                visit(child, x, y, chance*action)
            else:
                side = 0 if node.actor == 3 else 1
                row = infos[side][node.key]
                require(row['parentSequence'] == (x if side == 0 else y), 'Physical own recall differs')
                i = indices[side][node.key, action]
                require(i in row['childSequences'], 'Physical action sequence differs')
                visit(child, i if side == 0 else x, i if side == 1 else y, chance)
    visit(root)
    f, s = projections
    ay0 = [dot(row, s['offset']) for row in a]
    ay = [[dot(row, [t[j] for t in s['transform']])
           for j in range(len(s['independentSequences']))] for row in a]
    p = baseline['projectedPayoff']
    close(p['constant'], dot(f['offset'], ay0), 'Physical reduced constant differs')
    vector(p['firstCost'], [dot([t[j] for t in f['transform']], ay0)
                           for j in range(len(f['independentSequences']))])
    vector(p['secondCost'], [dot(f['offset'], [row[k] for row in ay])
                            for k in range(len(s['independentSequences']))])
    for j, row in enumerate(p['matrix']):
        vector(row, [dot([t[j] for t in f['transform']], [r[k] for r in ay])
                     for k in range(len(s['independentSequences']))])


def face(a, baseline):
    h, o, p = a['heroProjection'], a['opponentProjection'], a['orientedPayoff']
    require(h == baseline['secondProjection'] and o == baseline['firstProjection'], 'Original projections changed')
    original = baseline['projectedPayoff']
    close(p['constant'], baseline['constantSum']-original['constant'], 'Oriented physical constant differs')
    vector(p['firstCost'], [-x for x in original['secondCost']])
    vector(p['secondCost'], [-x for x in original['firstCost']])
    for j, row in enumerate(p['matrix']):
        vector(row, [-r[j] for r in original['matrix']])
    w, z, d = len(o['independentSequences']), len(h['independentSequences']), len(h['rhs'])
    matrix = [p['matrix'][i]+[-h['inequalities'][j][i] for j in range(d)] for i in range(z)]
    matrix += [row+[0]*d for row in o['inequalities']]
    matrix += [p['secondCost']+h['rhs']]
    close(a['globalHeroUpperValue'], baseline['constantSum']-baseline['lowerValue'], 'Global security value differs')
    rhs = [-x for x in p['firstCost']]+o['rhs']+[a['globalHeroUpperValue']+a['securitySlack']-p['constant']]
    return matrix, rhs, w


def certificate(matrix, rhs, cost, witness):
    x, y, c = witness['point'], witness['dual'], witness['certificate']
    require(c['algorithm'] == ALGORITHM and c['variables'] == len(cost) == len(x)
            and c['constraints'] == len(rhs) == len(y), 'Endpoint identity/dimensions differ')
    work = c['work']
    require(0 <= work['arithmeticWork'] <= 4_000_000_000
            and 0 <= sum(work[k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) <= 10000,
            'Original work limits differ')
    require(all(math.isfinite(v) for v in x+y), 'Nonfinite certificate')
    pv, dv = dot(cost, x), dot(rhs, y)
    primal = max([0]+[-v for v in x]+[dot(r, x)-b for r, b in zip(matrix, rhs, strict=True)])
    dual = max([0]+[-v for v in y]+[cost[j]-dot([r[j] for r in matrix], y) for j in range(len(cost))])
    require(max(primal, dual, abs(pv-dv)) <= EPS, 'Original endpoint certificate fails')
    for k, v in dict(primalValue=pv, dualValue=dv, maximumPrimalViolation=primal,
                     maximumDualViolation=dual, absoluteDualityGap=abs(pv-dv)).items():
        close(c[k], v, 'Recorded original certificate differs')


def maximum_utility(node):
    if node.actor == -2:
        return abs(node.utilities[5])
    return max(maximum_utility(child) for _, child in node.edges)


def numerator_plans(roots, action, audit):
    menus = {}
    def collect(node):
        if node.actor == 5:
            menus[node.key] = tuple(a for a, _ in node.edges)
        for _, child in node.edges:
            collect(child)
    for _, node in roots:
        collect(dict(node.edges)[action])
    keys = sorted(menus)
    choices = itertools.product(*(menus[k] for k in keys))
    flow = audit['minimumReachWitness']['opponentFlow']
    indices = {(s['key'], s['action']): s['index'] for s in flow['sequences']}
    o = audit['opponentProjection']
    count = 0
    for selected in choices:
        plan = audit['numeratorPlans'][count]
        policy = dict(zip(keys, selected))
        require(plan['continuation'] == policy, 'Incomplete continuation plan enumeration')
        coefficients = [0.0]*len(flow['sequences'])
        def visit(node, chance, sequence=0):
            if node.actor == -2:
                coefficients[sequence] += chance*node.utilities[5]
            elif node.actor == 5:
                visit(dict(node.edges)[policy[node.key]], chance, sequence)
            else:
                for label, child in node.edges:
                    visit(child, chance*label if node.actor == -1 else chance,
                          sequence if node.actor == -1 else indices[node.key, label])
        for chance, node in roots:
            visit(dict(node.edges)[action], chance)
        close(plan['constant'], dot(coefficients, o['offset']), 'Physical numerator constant differs')
        vector(plan['cost'], [dot(coefficients, [t[j] for t in o['transform']])
                              for j in range(len(o['independentSequences']))])
        count += 1
    require(count == len(audit['numeratorPlans']), 'Extra continuation plan')


def check(request, report_path):
    saved, raw, expanded = read(report_path)
    parent, parent_bytes, _ = read(PARENT)
    require(hashlib.sha256(parent_bytes).hexdigest() == PARENT_SHA, 'Pinned physical parent changed')
    _, context = physical.check(request, PARENT, include_context=True)
    root, selected, prior = context['root'], context['selected'], context['prior']
    require(saved['schemaVersion'] == 'pokerlab-supplied-flop-action-bounds/v1'
            and saved['trainerAdmission'] is False and saved['parent'] == parent
            and saved['reachUnits'] == 'COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1'
            and saved['publicHistory'] == '|BB:call', 'Action bounds scope differs')
    close(saved['physicalFlopMass'], physical.P, 'Physical event changed', mass=True)
    close(saved['rawRequiredMinimumReach'], physical.P*.0001, 'Raw floor units changed', mass=True)
    require(saved['physicalIncidence'] == [dict(originalDealIndex=w['dealIndex'],
            originalJointProbability=w['conditionalProbability'], physicalFlopProbability=physical.P)
            for w in prior], 'Original physical incidence changed')
    baseline = parent['solve']
    payoff(root, baseline)
    hands = sorted({w['dealtCombos'][5] for w in prior})
    require([d['informationSet'] for d in saved['decisions']] == [
        f'5:supplied-flop-one-bet:{hand}:|BB:call:2s 8s Ts:' for hand in hands], 'Question inventory differs')
    checks = 0
    for decision in saved['decisions']:
        require(decision['hero'] == 5 and [a['action'] for a in decision['actions']] == ['check', 'bet'], 'Decision menu differs')
        roots = [(w['conditionalProbability'], selected[i, '|BB:call']) for i, w in enumerate(prior)
                 if selected[i, '|BB:call'].key == decision['informationSet']]
        reach = math.fsum(p for p, _ in roots)
        for action in decision['actions']:
            a = action['diagnostic']
            require(a['algorithm'] == 'OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_RESOLVABLE_ENDPOINTS/v3'
                    and a['trainerAdmission'] is False and a['baseline'] == baseline
                    and a['securitySlack'] == EPS and a['requiredMinimumReach'] == .0001
                    and a['question'] == dict(hero=5, informationSet=decision['informationSet'], action=action['action']), 'Endpoint scope differs')
            close(a['denominator']['constant'], reach, 'Conditional denominator differs')
            vector(a['denominator']['cost'], [0]*len(a['denominator']['cost']))
            m, b, w = face(a, baseline)
            numerator_plans(roots, action['action'], a)
            n = len(m[0]); shift = action['upperObjectiveShift']
            close(shift, -maximum_utility(root)-1, 'Derived objective shift differs')
            scaled = [r+[-v] for r, v in zip(m, b)]
            scaled += [a['denominator']['cost']+[0]*(n-w)+[reach],
                       [-c for c in a['denominator']['cost']]+[0]*(n-w)+[-reach], [0]*n+[1]]
            sb = [0]*len(b)+[1, -1, 10000]
            for sign, name in [(-1, 'minimumReachWitness'), (1, 'maximumReachWitness')]:
                certificate(m, b, [sign*c for c in a['denominator']['cost']]+[0]*(n-w), a[name]); checks += 1
            for plan, witness in zip(a['numeratorPlans'], a['upperWitnesses'], strict=True):
                cost = [c+shift*d for c, d in zip(plan['cost'], a['denominator']['cost'])]+[0]*(n-w)+[plan['constant']+shift*reach]
                certificate(scaled, sb, cost, witness); checks += 1
            lower = [r+[0] for r in scaled]
            lower += [p['cost']+[0]*(n-w)+[p['constant'], 1] for p in a['numeratorPlans']]
            certificate(lower, sb+[-shift]*len(a['numeratorPlans']), [0]*(n+1)+[1], a['lowerWitness']); checks += 1
            close(action['lowerUtilityBb'], -shift-a['lowerWitness']['certificate']['dualValue']-EPS, 'Padded lower bound differs')
            close(action['upperUtilityBb'], max(x['certificate']['dualValue']-shift for x in a['upperWitnesses'])+EPS, 'Padded upper bound differs')
            close(a['certifiedReachLowerBound'], reach-EPS, 'Full-face lower reach differs')
            close(a['certifiedReachUpperBound'], reach+EPS, 'Full-face upper reach differs')
            for field in ['Lower', 'Upper']:
                close(action['rawCertifiedReach'+field+'Bound'], physical.P*a['certifiedReach'+field+'Bound'], 'Raw reach units differ', mass=True)
            for witness in [a['minimumReachWitness'], a['maximumReachWitness'], a['lowerWitness'], *a['upperWitnesses']]:
                flow, variables = witness['opponentFlow'], witness['opponentVariables']
                require(flow['actor'] == 3 and flow['sequences'] == baseline['firstFlow']['sequences']
                        and flow['conservation'] == baseline['firstFlow']['conservation'], 'Opponent topology differs')
                realization = flow['realization']
                o = a['opponentProjection']
                vector(realization, [c+dot(t, variables) for c, t in zip(o['offset'], o['transform'])])
                close(realization[0], 1, 'Original root flow differs')
                require(min(realization) >= -EPS, 'Negative original flow')
                policy = copy.deepcopy(parent['candidate']['strategy'])
                for r in flow['conservation']:
                    ids = r['childSequences']; mass = realization[r['parentSequence']]
                    close(sum(realization[i] for i in ids), mass, 'Original flow conservation differs')
                    weights = [min(1, max(0, realization[i]/mass)) if mass > 1e-12 else 1/len(ids) for i in ids]
                    total = sum(weights)
                    policy[r['key']] = {flow['sequences'][i]['action']: p/total for i, p in zip(ids, weights)}
                global_value = physical.best_response(root, policy, 5)
                close(witness['globalHeroBestResponse'], global_value, 'Independent parent security differs')
                require(global_value <= a['globalHeroUpperValue']+a['securitySlack']+EPS, 'Witness violates original global security')
                forced = physical.Node(-1, edges=tuple((p/reach, dict(node.edges)[action['action']]) for p, node in roots))
                value = physical.best_response(forced, policy, 5)
                close(witness['conditionalHeroBestResponse'], value, 'Independent conditional response differs')
                close(witness['questionReach'], reach, 'Behavioral question reach differs')
                require(len(witness['posterior']) == len(roots), 'Posterior inventory differs')
                for i, (row, (p, _)) in enumerate(zip(witness['posterior'], roots)):
                    require(row['rootIndex'] == i and row['opponentSequence'] == 0, 'Counterfactual past changed')
                    for key, expected in [('chanceWeight', p), ('originalRealizationWeight', p),
                                          ('behavioralWeight', p), ('probability', p/reach)]:
                        close(row[key], expected, 'Own reach included or posterior changed', mass=True)
            witnesses = [a['minimumReachWitness'], a['maximumReachWitness'], a['lowerWitness'], *a['upperWitnesses']]
            require(a['work']['intervalLpSolves'] == len(witnesses)
                    and a['work']['intervalLpArithmeticWork'] == sum(x['certificate']['work']['arithmeticWork'] for x in witnesses),
                    'Shared endpoint work accounting differs')
        best_lower = max(a['lowerUtilityBb'] for a in decision['actions'])
        best_upper = max(a['upperUtilityBb'] for a in decision['actions'])
        for a in decision['actions']:
            close(a['lowerLossBb'], max(0, best_lower-a['upperUtilityBb']), 'Conservative loss lower differs')
            close(a['upperLossBb'], max(0, best_upper-a['lowerUtilityBb']), 'Conservative loss upper differs')
    require([r['policy']['iterations'] for r in saved['freshReferences']] == [2000, 4000], 'Fresh budgets differ')
    for reference in saved['freshReferences']:
        policy = reference['policy']['strategy']
        require(set(policy) == set(parent['candidate']['strategy']), 'Fresh policy incomplete')
        for key, row in policy.items():
            require(set(row) == set(parent['candidate']['strategy'][key]) and all(0 <= p <= 1 for p in row.values()), 'Invalid fresh policy')
            close(sum(row.values()), 1, 'Fresh policy does not sum to one')
        physical.compare_quality(reference['fullParentQuality'], physical.quality(root, policy))
        local = reference['reachedFlopQuality']
        masses = [(i, w['conditionalProbability']*physical.P*policy[
            f'5:supplied-connected-single-flop:supplied-conditional-preflop:{w["dealtCombos"][5]}:']['call'])
            for i, w in enumerate(prior)]
        total = math.fsum(p for _, p in masses)
        close(local['candidateRootFlopReach'], total, 'Fresh raw flop reach differs', mass=True)
        require(local['conditioning'] == 'ABOVE_CANDIDATE_REACH_FLOOR', 'Fresh flop unsupported')
        local_root = physical.Node(-1, edges=tuple((p/total, selected[i, '|BB:call']) for i, p in masses))
        physical.compare_quality(local['conditionalBestResponse'], physical.quality(local_root, policy))
    return dict(schemaVersion='pokerlab-independent-flop-action-bounds/v1', trainerAdmission=False,
                freshLpSolves=0, physicalPayoffAndProjectionsIndependentlyChecked=True,
                originalEndpointCertificatesChecked=checks, fullParentBehavioralSecurityChecked=True,
                conditionalResponsesChecked=True, decisionsChecked=3, actionsChecked=6, freshReferencesChecked=2,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                physicalHelperSourceSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),
                requestStoredBytesSha256=hashlib.sha256(request.read_bytes()).hexdigest(),
                reportStoredBytesSha256=hashlib.sha256(raw).hexdigest(),
                reportExpandedBytesSha256=hashlib.sha256(expanded).hexdigest(), pinnedParentStoredBytesSha256=PARENT_SHA)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('request', type=Path); p.add_argument('report', type=Path)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    require(not args.output.exists(), 'Receipt output must be new')
    result = check(args.request, args.report)
    with args.output.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2); stream.write('\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
