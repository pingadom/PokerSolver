"""Reconstruct five analytic root minimax controls; verify original binary64 LP certificates.

No physical poker game, LP optimization or CFR training. Pure strategy enumeration
is restricted to the fixed small controls, with a 128-map ceiling.
"""
import argparse
import hashlib
import importlib.util
import itertools
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-resolvable-refined-lp.py'
spec = importlib.util.spec_from_file_location('root_minimax_exact', HELPER)
exact = importlib.util.module_from_spec(spec); spec.loader.exec_module(exact)
require, read = exact.require, exact.read
EPS = 1e-8
FIXTURE = ROOT / 'solver/src/test/resources/root-minimax-analytic-controls.json.gz'
ALGORITHM = 'ROOT_CONSTANT_REACH_REDUCED_PLANS_MINIMAX_EPIGRAPH/v1'
SCOPE = 'MINIMUM_OPTIMAL_ROOT_CONTINUATION_TERMINAL_UTILITY_NOT_FULL_ACTION_INTERVAL/v1'
LP = 'OWNED_ASCENDING_L1_RESOLVABLE_COST_ALL_PHASES_COMPENSATED_DIRECTION_STRICT_RATIO_ORIGINAL_CERTIFICATE/v7'
PARENT_LP = 'OWNED_ASCENDING_L1_COMPENSATED_PHASE_TWO_DIRECTION_ORIGINAL_CERTIFICATE/v4'
EXPECTED = {'dominated-3': -2., 'shared-3': 0., 'dominated-5': -2., 'shared-5': 0., 'reduced-5': -8/3}


def close(a, b):
    require(type(a) in (int, float) and type(b) in (int, float) and
            math.isfinite(a) and math.isfinite(b) and abs(a-b) <= EPS, 'Numerical value differs')


def near(a, b):
    if isinstance(b, list):
        require(isinstance(a, list) and len(a) == len(b), 'Matrix/vector dimensions differ')
        for x, y in zip(a, b, strict=True): near(x, y)
    else: close(a, b)


def dot(a, b):
    return math.fsum(x*y for x, y in zip(a, b, strict=True))


def projection(flow, p):
    rows, seq = flow['conservation'], flow['sequences']
    require([s['index'] for s in seq] == list(range(len(seq))) and seq[0]['key'] == seq[0]['action'] == '', 'Original sequences differ')
    independent = [i for row in rows for i in row['childSequences'][:-1]]
    n, w = len(seq), len(independent)
    offset = [1]+[0]*(n-1); transform = [[0]*w for _ in seq]
    matrix = [[0]*w for _ in rows]; rhs = [0]*len(rows)
    done, available = set(), {0}
    while len(done) < len(rows):
        old = len(done)
        for j, row in enumerate(rows):
            parent, children = row['parentSequence'], row['childSequences']
            if j in done or parent not in available: continue
            offset[children[-1]] = offset[parent]; transform[children[-1]] = transform[parent].copy()
            matrix[j] = [-v for v in transform[parent]]; rhs[j] = offset[parent]
            for child in children[:-1]:
                k = independent.index(child); transform[child][k] = 1
                transform[children[-1]][k] -= 1; matrix[j][k] += 1
            done.add(j); available.update(children)
        require(len(done) > old, 'Original flow recall is disconnected')
    for key, value in dict(independentSequences=independent, offset=offset, transform=transform, inequalities=matrix, rhs=rhs).items():
        require(p[key] == value, 'Original projection differs: '+key)


def reduce_payoff(matrix, h, o):
    a0 = [dot(row, o['offset']) for row in matrix]
    au = [[dot(row, [t[j] for t in o['transform']]) for j in range(len(o['independentSequences']))] for row in matrix]
    return dict(constant=dot(h['offset'], a0),
                firstCost=[dot([t[j] for t in h['transform']], a0) for j in range(len(h['independentSequences']))],
                secondCost=[dot(h['offset'], [row[k] for row in au]) for k in range(len(o['independentSequences']))],
                matrix=[[dot([t[j] for t in h['transform']], [r[k] for r in au])
                         for k in range(len(o['independentSequences']))] for j in range(len(h['independentSequences']))])


def original_payoff(tree, hero, opponent, hf, of):
    flows = {hero: hf, opponent: of}
    ids = {p: {(s['key'], s['action']): s['index'] for s in f['sequences'][1:]} for p, f in flows.items()}
    rows = {p: {r['key']: r for r in f['conservation']} for p, f in flows.items()}
    seen = {hero: set(), opponent: set()}
    result = [[0.]*len(of['sequences']) for _ in hf['sequences']]
    def visit(n, hs=0, os=0, chance=1.):
        actor = n['actor']
        if actor == -2:
            close(n['utilities'][hero]+n['utilities'][opponent], 4.)
            result[hs][os] += chance*n['utilities'][hero]; return
        if actor == -1:
            require(len(n['probabilities']) == len(n['children']), 'Chance inventory differs')
            close(sum(n['probabilities']), 1)
            for p, child in zip(n['probabilities'], n['children'], strict=True):
                require(math.isfinite(p) and p >= 0, 'Invalid original chance'); visit(child, hs, os, chance*p)
            return
        require(actor in flows and 0 < len(n['actions']) <= 16, 'Original actor/menu differs')
        r = rows[actor][n['key']]; seen[actor].add(n['key'])
        require(r['parentSequence'] == (hs if actor == hero else os), 'Original own recall differs')
        require([ids[actor][n['key'], a] for a in n['actions']] == r['childSequences'], 'Original menu differs')
        for a, child in zip(n['actions'], n['children'], strict=True):
            i = ids[actor][n['key'], a]; visit(child, i if actor == hero else hs, i if actor == opponent else os, chance)
    visit(tree)
    require(all(seen[p] == set(rows[p]) for p in flows), 'Extra original information set')
    return result


def certificate(a, b, c, witness, algorithm):
    cert = witness['certificate']
    require(cert['algorithm'] == algorithm and cert['variables'] == len(c) and cert['constraints'] == len(b), 'Original LP identity differs')
    values = exact.exact(a, b, c, witness['point'], witness['dual'])
    for key, value in values.items(): close(cert[key], float(value))
    work = cert['work']; pivots = sum(work[k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots'])
    require(0 <= pivots <= 10000 and 0 <= work['arithmeticWork'] <= 4_000_000_000, 'LP work exceeds original caps')
    return float(values['primalValue'])


def parent_problem(p, own, other, first):
    n, d, m = len(own['independentSequences']), len(other['rhs']), len(other['independentSequences'])
    matrix = [[(-p['matrix'][j][i] if first else p['matrix'][i][j]) for j in range(n)] +
              [-other['inequalities'][j][i] for j in range(d)] for i in range(m)]
    matrix += [row+[0.]*d for row in own['inequalities']]
    rhs = (p['secondCost'] if first else [-v for v in p['firstCost']])+own['rhs']
    cost = (p['firstCost'] if first else [-v for v in p['secondCost']])+[-v for v in other['rhs']]
    return matrix, rhs, cost


def pure_plans(roots, hero):
    menus = {}
    def collect(n):
        if n['actor'] == hero:
            if n['key'] in menus: require(menus[n['key']] == n['actions'], 'Hidden-world menu differs')
            menus[n['key']] = n['actions']
        for child in n['children']: collect(child)
    for _, n in roots: collect(n)
    require(math.prod(len(v) for v in menus.values()) <= 128, 'Analytic exhaustive oracle ceiling exceeded')
    keys = sorted(menus)
    full = [dict(zip(keys, values, strict=True)) for values in itertools.product(*(menus[k] for k in keys))]
    reduced = {}
    def visited(n, plan, kept):
        if n['actor'] == hero:
            action = plan[n['key']]; kept[n['key']] = action
            visited(n['children'][n['actions'].index(action)], plan, kept)
        else:
            for child in n['children']: visited(child, plan, kept)
    for plan in full:
        kept = {}
        for _, n in roots: visited(n, plan, kept)
        reduced[tuple(sorted(kept.items()))] = kept
    return full, reduced


def value(n, hero, plan, policy):
    if n['actor'] == -2: return n['utilities'][hero]
    if n['actor'] == hero: return value(n['children'][n['actions'].index(plan[n['key']])], hero, plan, policy)
    probabilities = n['probabilities'] if n['actor'] == -1 else [policy[n['key']][a] for a in n['actions']]
    return math.fsum(p*value(child, hero, plan, policy) for p, child in zip(probabilities, n['children'], strict=True))


def check_case(case):
    require(set(case) == {'name', 'game', 'expected', 'audit'} and case['name'] in EXPECTED, 'Control identity differs')
    close(case['expected'], EXPECTED[case['name']])
    a, tree = case['audit'], case['game']['initialState']; h = a['question']['hero']; o = 5 if h == 3 else 3
    require(h == int(case['name'][-1]) and a['algorithm'] == ALGORITHM and a['scope'] == SCOPE and a['trainerAdmission'] is False, 'Minimax scope differs')
    require(a['securitySlack'] == 1e-8 and a['requiredMinimumReach'] == .0001, 'Original gates differ')
    base = a['baseline']; require(base['firstActor'] == 3 and base['secondActor'] == 5 and case['game']['playerCount'] == 6, 'Parent orientation differs')
    close(base['constantSum'], 4.)
    f, s, fp, sp = (base[k] for k in ['firstFlow', 'secondFlow', 'firstProjection', 'secondProjection'])
    projection(f, fp); projection(s, sp)
    p = reduce_payoff(original_payoff(tree, 3, 5, f, s), fp, sp)
    for key, expected in p.items(): near(base['projectedPayoff'][key], expected)
    for witness, own, other, first in [(a['firstParentWitness'], fp, sp, True), (a['secondParentWitness'], sp, fp, False)]:
        args = parent_problem(p, own, other, first); result = certificate(*args, witness, PARENT_LP)
        require(witness['certificate'] == base['firstLp' if first else 'secondLp'], 'Parent witness/certificate binding differs')
        close(base['lowerValue' if first else 'upperValue'], p['constant']+(result if first else -result))
        flow = f if first else s
        parent_realization = [off+dot(row, witness['point'][:len(own['independentSequences'])])
                              for off, row in zip(own['offset'], own['transform'], strict=True)]
        near(flow['realization'], parent_realization)
    hp, op, hf, of = (fp, sp, f, s) if h == 3 else (sp, fp, s, f)
    require(a['heroProjection'] == hp and a['opponentProjection'] == op, 'Oriented original projection differs')
    oriented = reduce_payoff(original_payoff(tree, h, o, hf, of), hp, op)
    for key, expected in oriented.items(): near(a['orientedPayoff'][key], expected)
    global_upper = base['upperValue'] if h == 3 else base['constantSum']-base['lowerValue']
    close(a['globalHeroUpperValue'], global_upper)
    n, z, d = len(op['independentSequences']), len(hp['independentSequences']), len(hp['rhs'])
    face = [oriented['matrix'][i]+[-hp['inequalities'][j][i] for j in range(d)] for i in range(z)]
    face += [row+[0.]*d for row in op['inequalities']]+[oriented['secondCost']+hp['rhs']]
    rhs = [-v for v in oriented['firstCost']]+op['rhs']+[global_upper+a['securitySlack']-oriented['constant']]
    roots = []
    def collect(node, chance=1., previous=False):
        if node['actor'] == h and node['key'] == a['question']['informationSet']:
            require(not previous, 'Question follows a player action'); roots.append((chance, node)); return
        for i, child in enumerate(node['children']): collect(child, chance*node['probabilities'][i] if node['actor'] == -1 else chance, previous or node['actor'] >= 0)
    collect(tree); mass = sum(weight for weight, _ in roots); close(a['originalQuestionReach'], mass)
    require(.0001 <= mass <= 1 and len(roots) == len(a['support']), 'Original root support differs')
    forest = []
    for (weight, node), support in zip(roots, a['support'], strict=True):
        close(support['originalChanceWeight'], weight); close(support['posteriorProbability'], weight/mass)
        forest.append((weight/mass, node['children'][node['actions'].index(a['question']['action'])]))
    full, reduced = pure_plans(forest, h)
    require(a['cartesianPlans'] == len(full) and a['reducedPlans'] == len(reduced) == len(a['plans']) <= 64, 'Complete reduced plan count differs')
    require({tuple(sorted(p['continuation'].items())) for p in a['plans']} == set(reduced), 'Missing or clairvoyant continuation')
    ids = {(seq['key'], seq['action']): seq['index'] for seq in of['sequences'][1:]}
    for plan in a['plans']:
        coefficients = [0.]*len(of['sequences'])
        def visit(node, chance, sequence=0):
            if node['actor'] == -2: coefficients[sequence] += chance*node['utilities'][h]; return
            if node['actor'] == h:
                visit(node['children'][node['actions'].index(plan['continuation'][node['key']])], chance, sequence); return
            for i, child in enumerate(node['children']): visit(child, chance*node['probabilities'][i] if node['actor'] == -1 else chance,
                    sequence if node['actor'] == -1 else ids[node['key'], node['actions'][i]])
        for weight, node in forest: visit(node, weight)
        close(plan['constant'], dot(coefficients, op['offset']))
        near(plan['cost'], [dot(coefficients, [row[j] for row in op['transform']]) for j in range(n)])
    def ceiling(node): return abs(node['utilities'][h]) if node['actor'] == -2 else max(ceiling(child) for child in node['children'])
    shift = ceiling(tree)+1; close(a['problem']['utilityShift'], shift)
    matrix = [row+[0.] for row in face]+[p['cost']+[0.]*d+[-1.] for p in a['plans']]
    rhs += [-p['constant']-shift for p in a['plans']]; cost = [0.]*(n+d)+[-1.]
    near(a['problem']['matrix'], matrix); near(a['problem']['rhs'], rhs); near(a['problem']['cost'], cost)
    witness = a['witness']; certificate(matrix, rhs, cost, witness, LP)
    variables = witness['point'][:n]; realization = [off+dot(row, variables) for off, row in zip(op['offset'], op['transform'], strict=True)]
    flow = witness['opponentFlow']; require(flow['actor'] == o and flow['sequences'] == of['sequences'] and flow['conservation'] == of['conservation'], 'Original witness flow differs')
    near(flow['realization'], realization); policy = {}
    for row in of['conservation']:
        children = row['childSequences']; total = sum(realization[i] for i in children)
        close(total, realization[row['parentSequence']])
        policy[row['key']] = {of['sequences'][i]['action']: realization[i]/total if total > 1e-12 else 1/len(children) for i in children}
    require(all(v >= -EPS and v <= 1+EPS for v in realization), 'Invalid behavioral realization')
    local = max(math.fsum(weight*value(node, h, plan, policy) for weight, node in forest) for plan in reduced.values())
    all_hero, _ = pure_plans([(1., tree)], h)
    global_br = max(value(tree, h, plan, policy) for plan in all_hero)
    close(witness['conditionalHeroBestResponse'], local); close(witness['globalHeroBestResponse'], global_br)
    require(global_br <= global_upper+a['securitySlack']+EPS, 'Behavioral witness outside global security face')
    cert = witness['certificate']; objective = -cert['primalValue']-shift
    close(objective, local); close(a['minimum']['witnessUtility'], local)
    close(a['minimum']['lowerBound'], -cert['dualValue']-shift-EPS)
    close(a['minimum']['upperBound'], max(objective, local)+EPS)
    require(a['minimum']['lowerBound']-EPS <= case['expected'] <= a['minimum']['upperBound']+EPS, 'Closed-form minimax value not enclosed')
    work = a['work']; require(work['endpointLpSolves'] == 1 and work['compilerLimit'] == 8_000_000 and 0 <= work['compilerUnits'] <= work['compilerLimit'], 'Endpoint/compiler work differs')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Charged LP totals differ')
    return len(reduced)


def check_values(controls):
    require(isinstance(controls, list) and len(controls) == 5 and {c['name'] for c in controls} == set(EXPECTED), 'Five fixed analytic controls required')
    plans = sum(check_case(c) for c in controls)
    return dict(status='PASS_ANALYTIC_ROOT_MINIMUM_NOT_TRAINER_ADMISSION', controls=5,
                exactParentCertificates=10, exactMinimaxCertificates=5, reducedPlansChecked=plans,
                freshLpSolves=0, freshCfrRuns=0, physicalPokerGames=0, trainerAdmission=False)


def check():
    controls, raw = read(FIXTURE); result = check_values(controls)
    result.update(fixtureSha256=hashlib.sha256(raw).hexdigest(), checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  immutableExactHelperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest())
    return result


if __name__ == '__main__':
    import json
    parser = argparse.ArgumentParser(); parser.add_argument('--receipt', type=Path); args = parser.parse_args()
    data = (json.dumps(check(), indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream: stream.write(data)
    print(data.decode())
