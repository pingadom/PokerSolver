"""Independent sequence-flow root-minimum reconstruction; exact certificates, no optimizer."""
import argparse
import hashlib
import importlib.util
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-root-minimax.py'
EXACT = ROOT / 'scripts/check-resolvable-refined-lp.py'
HELPER_SHA = 'a41d0959f74c5e00a0a43843c13b31107d396fdd8da43b0f87e4685a84ad6cc5'
EXACT_SHA = '84795dabca56dd86a4870b23efd8d5731d4b4b17d3399fbfa818f7ee9d18f080'
if hashlib.sha256(HELPER.read_bytes()).hexdigest() != HELPER_SHA or hashlib.sha256(EXACT.read_bytes()).hexdigest() != EXACT_SHA:
    raise ValueError('Immutable independent helpers changed')
spec = importlib.util.spec_from_file_location('sequence_minimum_original', HELPER)
old = importlib.util.module_from_spec(spec); spec.loader.exec_module(old)
require, close, near, dot = old.require, old.close, old.near, old.dot
FIXTURE = ROOT / 'solver/src/test/resources/root-sequence-minimax-analytic-controls.json.gz'
FIXTURE_SHA = '7fde2e928d0a05330b6466264fd057b7d1ffae4f5411eb291046eae019efe51c'
ALGORITHM = 'ROOT_CONSTANT_REACH_SEQUENCE_DUAL_MINIMAX/v1'
SCOPE = 'MINIMUM_OPTIMAL_ROOT_CONTINUATION_SEQUENCE_DUAL_NOT_FULL_ACTION_INTERVAL/v1'
EXPECTED = {**old.EXPECTED, 'private32-3': 0., 'private32-5': 0., 'quarter-5': -2.}
LEGACY, _ = old.read(old.FIXTURE)
old.check_values(LEGACY)
LEGACY = {c['name']: c for c in LEGACY}


def node(actor, key='', actions=(), probabilities=(), children=(), utilities=()):
    return dict(actor=actor, key=key, actions=list(actions), probabilities=list(probabilities), children=list(children), utilities=list(utilities))


def terminal(hero, value):
    values = [-1.] * 6; values[hero] = value; values[3 if hero == 5 else 5] = 4 - value
    return node(-2, utilities=values)


def decision(actor, key, actions, *children):
    return node(actor, str(actor)+':'+key, actions, children=children)


def literal_equal(actual, expected):
    if isinstance(expected, dict):
        require(isinstance(actual, dict) and set(actual) == set(expected), 'Literal control keys differ')
        for key in expected: literal_equal(actual[key], expected[key])
    elif isinstance(expected, list):
        require(isinstance(actual, list) and len(actual) == len(expected), 'Literal control cardinality differs')
        for a, b in zip(actual, expected, strict=True): literal_equal(a, b)
    elif type(expected) in (int, float):
        require(type(actual) in (int, float) and math.isfinite(actual) and actual == expected, 'Literal control number differs')
    else:
        require(type(actual) is type(expected) and actual == expected, 'Literal control value differs')


def literal_case(name):
    if name in LEGACY: return LEGACY[name]['game']
    if name == 'quarter-5':
        selected = LEGACY['dominated-5']['game']['initialState']
        outside = decision(5, 'outside', ['a', 'b'], terminal(5, 0), terminal(5, 0))
        return dict(playerCount=6, initialState=node(-1, probabilities=[.25, .75], children=[selected, outside]))
    hero = int(name[-1]); opponent = 5 if hero == 3 else 3; children = []
    for i in range(32):
        sign = 1 if i % 2 == 0 else -1
        left = decision(opponent, 'reply', ['x', 'y'], terminal(hero, sign), terminal(hero, -sign))
        right = decision(opponent, 'reply', ['x', 'y'], terminal(hero, -sign), terminal(hero, sign))
        children.append(decision(hero, 'private-'+str(i), ['left', 'right'], left, right))
    chance = node(-1, probabilities=[1/32]*32, children=children)
    return dict(playerCount=6, initialState=decision(hero, 'root', ['safe', 'risk'], terminal(hero, 10), chance))


def behavior(flow, realization):
    require(len(realization) == len(flow['sequences']), 'Original flow cardinality differs')
    require(all(type(v) in (int, float) and math.isfinite(v) and -old.EPS <= v <= 1+old.EPS for v in realization), 'Original realization is invalid')
    close(realization[0], 1.)
    result = {}
    for row in flow['conservation']:
        children = row['childSequences']; mass = realization[row['parentSequence']]
        close(sum(realization[i] for i in children), mass)
        probabilities = [realization[i]/mass if mass > 1e-12 else 1/len(children) for i in children]
        close(sum(probabilities), 1.)
        bounded = [max(0., min(1., v)) for v in probabilities]; total = sum(bounded)
        result[row['key']] = {flow['sequences'][i]['action']: v/total for i, v in zip(children, bounded, strict=True)}
    return result


def value(tree, policy):
    if tree['actor'] == -2: return tree['utilities']
    probabilities = tree['probabilities'] if tree['actor'] == -1 else [policy[tree['key']][a] for a in tree['actions']]
    values = [value(child, policy) for child in tree['children']]
    return [math.fsum(p*v[i] for p, v in zip(probabilities, values, strict=True)) for i in range(6)]


def best_response(tree, hero, policy):
    """Group hidden states before choosing an action; never choose separately per world."""
    infos = {}
    def collect(n, reach=1., depth=0):
        if n['actor'] == -2: return
        if n['actor'] == hero:
            infos.setdefault(n['key'], []).append((depth, reach, n))
            for child in n['children']: collect(child, reach, depth+1)
        else:
            probabilities = n['probabilities'] if n['actor'] == -1 else [policy[n['key']][a] for a in n['actions']]
            for p, child in zip(probabilities, n['children'], strict=True): collect(child, reach*p, depth+1)
    collect(tree); selected = {}
    def continuation(n):
        if n['actor'] == -2: return n['utilities'][hero]
        if n['actor'] == hero: return continuation(n['children'][n['actions'].index(selected[n['key']])])
        probabilities = n['probabilities'] if n['actor'] == -1 else [policy[n['key']][a] for a in n['actions']]
        return math.fsum(p*continuation(child) for p, child in zip(probabilities, n['children'], strict=True))
    for key, states in sorted(infos.items(), key=lambda item: item[1][0][0], reverse=True):
        require(len({d for d, _, _ in states}) == 1 and all(n['actions'] == states[0][2]['actions'] for _, _, n in states), 'Information-set depth/menu differs')
        scores = [math.fsum(reach*continuation(n['children'][i]) for _, reach, n in states) for i in range(len(states[0][2]['actions']))]
        selected[key] = states[0][2]['actions'][max(range(len(scores)), key=scores.__getitem__)]
    return continuation(tree)


def check_case(case):
    require(set(case) == {'name', 'game', 'expected', 'audit'} and case['name'] in EXPECTED, 'Control identity differs')
    name = case['name']; close(case['expected'], EXPECTED[name]); literal_equal(case['game'], literal_case(name))
    a = case['audit']; h = int(name[-1]); o = 5 if h == 3 else 3; tree = case['game']['initialState']
    require(a['algorithm'] == ALGORITHM and a['scope'] == SCOPE and a['trainerAdmission'] is False, 'Sequence minimum scope differs')
    require(a['question'] == dict(hero=h, informationSet=str(h)+':root', action='risk'), 'Root question differs')
    require(a['requiredMinimumReach'] == .0001 and a['securitySlack'] == 1e-8, 'Original gates differ')
    base = a['baseline']; require(base['firstActor'] == 3 and base['secondActor'] == 5 and base['constantSum'] == 4., 'Parent orientation differs')
    f, s, fp, sp = (base[k] for k in ['firstFlow', 'secondFlow', 'firstProjection', 'secondProjection'])
    require(f['actor'] == 3 and s['actor'] == 5, 'Original flow actor labels differ')
    old.projection(f, fp); old.projection(s, sp)
    parent_payoff = old.reduce_payoff(old.original_payoff(tree, 3, 5, f, s), fp, sp)
    for key, expected in parent_payoff.items(): near(base['projectedPayoff'][key], expected)
    for witness, own, other, first, flow in [(a['firstParentWitness'], fp, sp, True, f), (a['secondParentWitness'], sp, fp, False, s)]:
        optimum = old.certificate(*old.parent_problem(parent_payoff, own, other, first), witness, old.PARENT_LP)
        require(witness['certificate'] == base['firstLp' if first else 'secondLp'], 'Parent witness binding differs')
        close(base['lowerValue' if first else 'upperValue'], parent_payoff['constant']+(optimum if first else -optimum))
        realization = [off+dot(row, witness['point'][:len(own['independentSequences'])]) for off, row in zip(own['offset'], own['transform'], strict=True)]
        near(flow['realization'], realization); behavior(flow, realization)
    hp, op, hf, of = (fp, sp, f, s) if h == 3 else (sp, fp, s, f)
    require(a['heroProjection'] == hp and a['opponentProjection'] == op, 'Oriented projection differs')
    oriented = old.reduce_payoff(old.original_payoff(tree, h, o, hf, of), hp, op)
    for key, expected in oriented.items(): near(a['orientedPayoff'][key], expected)
    upper = base['upperValue'] if h == 3 else 4-base['lowerValue']; close(a['globalHeroUpperValue'], upper)
    w, z, d = len(op['independentSequences']), len(hp['independentSequences']), len(hp['rhs'])
    face = [oriented['matrix'][i]+[-hp['inequalities'][j][i] for j in range(d)] for i in range(z)]
    face += [row+[0.]*d for row in op['inequalities']]+[oriented['secondCost']+hp['rhs']]
    face_rhs = [-v for v in oriented['firstCost']]+op['rhs']+[upper+a['securitySlack']-oriented['constant']]
    roots = []
    def collect(n, chance=1., previous=False):
        if n['actor'] == h and n['key'] == a['question']['informationSet']:
            require(not previous, 'Question follows a player action'); roots.append((chance, n)); return
        for i, child in enumerate(n['children']): collect(child, chance*n['probabilities'][i] if n['actor'] == -1 else chance, previous or n['actor'] >= 0)
    collect(tree); mass = sum(p for p, _ in roots); close(a['originalQuestionReach'], mass)
    require(.0001 <= mass <= 1 and len(a['support']) == len(roots), 'Root support differs')
    forest = []
    for (probability, n), support in zip(roots, a['support'], strict=True):
        close(support['originalChanceWeight'], probability); close(support['posteriorProbability'], probability/mass)
        forest.append((probability/mass, n['children'][n['actions'].index('risk')]))
    conditional = node(-1, probabilities=[p for p, _ in forest], children=[n for _, n in forest])
    ids = {p: {(s['key'], s['action']): s['index'] for s in flow['sequences'][1:]} for p, flow in [(h, hf), (o, of)]}
    forced = ids[h][str(h)+':root', 'risk']; grid = [[0.]*len(of['sequences']) for _ in hf['sequences']]
    def compile_payoff(n, hs, os, probability):
        if n['actor'] == -2: grid[hs][os] += probability*n['utilities'][h]; return
        for i, child in enumerate(n['children']):
            actor = n['actor']; seq = ids[actor][n['key'], n['actions'][i]] if actor >= 0 else None
            compile_payoff(child, seq if actor == h else hs, seq if actor == o else os, probability*n['probabilities'][i] if actor == -1 else probability)
    for p, n in forest: compile_payoff(n, forced, 0, p)
    local = old.reduce_payoff(grid, hp, op)
    for key, expected in local.items(): near(a['conditionalPayoff'][key], expected)
    restricted = hp['inequalities']+[hp['transform'][forced], [-v for v in hp['transform'][forced]]]
    restricted_rhs = hp['rhs']+[1-hp['offset'][forced], hp['offset'][forced]-1]
    require(a['heroDomain'] == dict(forcedSequence=forced, matrix=restricted, rhs=restricted_rhs), 'Forced original hero domain differs')
    columns = w+d+len(restricted); rows = len(face)+z
    matrix = [row+[0.]*len(restricted) for row in face]
    matrix += [local['matrix'][i]+[0.]*d+[-row[i] for row in restricted] for i in range(z)]
    rhs = face_rhs+[-v for v in local['firstCost']]
    cost = [-v for v in local['secondCost']]+[0.]*d+[-v for v in restricted_rhs]
    p = a['problem']; near(p['matrix'], matrix); near(p['rhs'], rhs); near(p['cost'], cost)
    close(p['objectiveConstant'], local['constant'])
    require(p['securityRows'] == len(face) and p['opponentVariables'] == w and p['globalDualVariables'] == d and p['continuationDualVariables'] == len(restricted), 'LP block inventory differs')
    require(0 < rows <= 512 and 0 < columns <= 512, 'Original LP dimension cap exceeded')
    witness = a['witness']; old.certificate(matrix, rhs, cost, witness, old.LP)
    def flow_values(projection, variables):
        raw = [off+dot(row, variables) for off, row in zip(projection['offset'], projection['transform'], strict=True)]
        require(all(v >= -old.EPS for v in raw), 'Invalid original projected realization')
        return [max(0., v) for v in raw]
    original_opponent = flow_values(op, witness['point'][:w]); original_hero = flow_values(hp, witness['dual'][len(face):])
    policies = {}
    for record, original, flow in [(witness['opponentFlow'], original_opponent, of), (a['continuationFlow'], original_hero, hf)]:
        require(record['actor'] == flow['actor'] and record['sequences'] == flow['sequences'] and record['conservation'] == flow['conservation'], 'Original witness flow identity differs')
        near(record['realization'], original); close(record['maximumResidual'], 0.)
        policies.update(behavior(flow, original))
    close(original_hero[forced], 1.)
    require(witness['behavioralPolicyHash'] == a['continuationPolicyHash'], 'Joint witness identity differs')
    local_br = best_response(conditional, h, policies); global_br = best_response(tree, h, policies)
    close(witness['globalHeroBestResponse'], global_br); close(witness['conditionalHeroBestResponse'], local_br)
    close(a['continuationUtility'], value(conditional, policies)[h]); close(a['continuationUtility'], local_br)
    require(global_br <= upper+a['securitySlack']+old.EPS, 'Opponent witness escapes the global security face')
    if name.startswith('private32-'):
        close(policies[str(o)+':reply']['x'], .5)
        require(len(hf['sequences']) == 67 and len(hf['conservation']) == 33, '32-type original flow differs')
        # Each type independently selects one of two rows of matching pennies.
        # V(p)=sum_type probability(type)*abs(2p-1), whose minimum is zero at p=1/2.
        close(local_br, 0.)
    else:
        _, plans = old.pure_plans(forest, h)
        exhaustive = max(math.fsum(p*old.value(n, h, plan, policies) for p, n in forest) for plan in plans.values())
        close(local_br, exhaustive)
    cert = witness['certificate']; objective = local['constant']-cert['primalValue']
    close(objective, local_br); close(a['minimum']['witnessUtility'], local_br)
    close(a['minimum']['lowerBound'], local['constant']-cert['dualValue']-old.EPS)
    close(a['minimum']['upperBound'], max(objective, local_br)+old.EPS)
    require(a['minimum']['lowerBound']-old.EPS <= case['expected'] <= a['minimum']['upperBound']+old.EPS, 'Closed-form minimum not enclosed')
    work = a['work']; require(all(type(v) is int for v in work.values()), 'Integer work counters required')
    require(work['compilerLimit'] == 8_000_000 and 0 < work['compilerUnits'] <= work['compilerLimit'], 'Compiler work differs')
    require(work['enumeratedContinuationPlans'] == 0 and work['endpointLpSolves'] == 1, 'Unexpected plan enumeration or endpoint solve count')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Endpoint work totals differ')


def check_values(controls):
    require(isinstance(controls, list) and len(controls) == 8 and {c['name'] for c in controls} == set(EXPECTED), 'Eight distinct declared controls required')
    for control in controls: check_case(control)
    return dict(status='PASS_ANALYTIC_SEQUENCE_ROOT_MINIMUM_NOT_TRAINER_ADMISSION', controls=8,
                exactParentCertificates=16, exactEndpointCertificates=8, enumeratedLargeControlPlans=0,
                largeControlCompletePlanCount=2**32, freshLpSolves=0, freshCfrRuns=0, physicalPokerGames=0, trainerAdmission=False)


def check():
    controls, raw = old.read(FIXTURE)
    require(hashlib.sha256(raw).hexdigest() == FIXTURE_SHA, 'Frozen analytic fixture byte pin differs')
    result = check_values(controls)
    result.update(fixtureSha256=FIXTURE_SHA, checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  immutableHelpersSha256={HELPER.name: HELPER_SHA, EXACT.name: EXACT_SHA})
    return result


if __name__ == '__main__':
    import json
    parser = argparse.ArgumentParser(); parser.add_argument('--receipt', type=Path); args = parser.parse_args()
    data = (json.dumps(check(), indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream: stream.write(data)
    print(data.decode())
