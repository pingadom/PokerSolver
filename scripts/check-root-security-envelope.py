"""Literal root-envelope coefficients, original LP certificates and deviation checks.

No optimizer or training is run. Outer bounds are not attained endpoint claims.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('root_envelope_parent', ROOT/'scripts/check-flop-size-menu.py')
size = importlib.util.module_from_spec(spec); sys.modules[spec.name] = size; spec.loader.exec_module(size)
base, bounds, physical = size.base, size.bounds, size.base.physical
require, close, vector, dot, read = size.require, size.close, size.vector, size.dot, size.read
DATA = ROOT/'docs/data'
REQUEST = DATA/'supplied-root-envelope-request.json'
REPORT = DATA/'supplied-root-envelope-report.json.gz'
ALGORITHM = 'ROOT_FIXED_PURE_POLICY_GLOBAL_SECURITY_OUTER_ENVELOPE/v1'
SCOPE = 'CONSERVATIVE_OPTIMAL_CONTINUATION_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1'
POLICY = 'OWNED_BASELINE_GREEDY_COMPLETE_HERO_POLICY/v1'
EPS = 1e-8


def fixed_value(node, policy):
    if node.actor == -2:
        return node.utilities[5]
    return math.fsum((a if node.actor == -1 else policy[node.key][a])*fixed_value(child, policy)
                     for a, child in node.edges)


def form(roots, choices, opponent, projection):
    coefficients = [0.]*len(opponent['sequences'])
    indices = {(s['key'], s['action']): s['index'] for s in opponent['sequences']}
    def visit(node, probability, sequence):
        if node.actor == -2:
            coefficients[sequence] += probability*node.utilities[5]
        elif node.actor == 5:
            visit(dict(node.edges)[choices[node.key]], probability, sequence)
        else:
            for a, child in node.edges:
                visit(child, probability*a if node.actor == -1 else probability,
                      sequence if node.actor == -1 else indices[node.key, a])
    for probability, node in roots:
        visit(node, probability, 0)
    return dict(constant=dot(coefficients, projection['offset']),
                cost=[dot(coefficients, [row[j] for row in projection['transform']])
                      for j in range(len(projection['independentSequences']))])


def check_form(actual, expected):
    require(set(actual) == {'constant', 'cost'}, 'Affine form identity differs')
    close(actual['constant'], expected['constant'], 'Literal fixed-policy constant differs')
    vector(actual['cost'], expected['cost'])


def witness(w, audit, parent, tree, question, evaluated, linear, domain):
    require(w['domain'] == domain, 'Certificate domain differs')
    projection = audit['opponentProjection']; width = len(projection['independentSequences'])
    original = [off+dot(row, w['point'][:width])
                for off, row in zip(projection['offset'], projection['transform'], strict=True)]
    flow, actual = parent['solve']['firstFlow'], w['opponentFlow']
    require(actual['actor'] == 3 and actual['sequences'] == flow['sequences']
            and actual['conservation'] == flow['conservation'], 'Original flow topology differs')
    require(all(math.isfinite(v) and -EPS <= v <= 1+EPS for v in original), 'Invalid original flow')
    point = [max(0., v) for v in original]; vector(actual['realization'], point)
    policy = {key: {a: float(a == audit['fixedHeroChoices'][key]) for a in row}
              for key, row in parent['candidate']['strategy'].items() if key.startswith('5:')}
    residual = abs(original[0]-1)
    for row in flow['conservation']:
        ids = row['childSequences']; total = math.fsum(point[i] for i in ids)
        residual = max(residual, abs(math.fsum(original[i] for i in ids)-original[row['parentSequence']]),
                       abs(total-point[row['parentSequence']]))
        policy[row['key']] = {flow['sequences'][i]['action']: point[i]/total if total > 0 else 1/len(ids)
                              for i in ids}
    require(residual <= EPS, 'Original conservation fails')
    close(actual['maximumResidual'], residual, 'Flow residual differs')
    resolved = {0: 1.}; pending = list(flow['conservation'])
    while pending:
        rest = []
        for row in pending:
            if row['parentSequence'] not in resolved:
                rest.append(row); continue
            for i in row['childSequences']:
                resolved[i] = resolved[row['parentSequence']]*policy[row['key']][flow['sequences'][i]['action']]
        require(len(rest) < len(pending), 'Unresolvable original recall'); pending = rest
    require(max(abs(resolved[i]-v) for i, v in enumerate(original)) <= EPS,
            'Behavior changes original realization')
    global_br = physical.best_response(tree, policy, 5)
    close(w['globalHeroBestResponse'], global_br, 'Global best response differs')
    if domain == 'FULL_OPPONENT_SECURITY_FACE':
        require(global_br <= audit['globalHeroUpperValue']+audit['securitySlack']+EPS,
                'Full-parent security violated')
    values = dict(globalFixedHeroUtility=fixed_value(tree, policy),
                  questionFixedHeroUtility=fixed_value(question, policy),
                  evaluatedFixedPolicyUtility=fixed_value(evaluated, policy),
                  optimalContinuationUtility=physical.best_response(evaluated, policy, 5))
    for field, value in values.items():
        close(w[field], value, 'Literal policy/continuation value differs: '+field)
    close(linear['constant']+dot(linear['cost'], w['point'][:width]),
          values['evaluatedFixedPolicyUtility'], 'Flow objective changes behavioral payoff')
    require(values['optimalContinuationUtility'] >= values['evaluatedFixedPolicyUtility']-EPS,
            'Fixed policy exceeds optimal continuation')
    if domain == 'FULL_OPPONENT_SECURITY_FACE':
        require(values['globalFixedHeroUtility']+audit['questionChanceMass']*(
            values['optimalContinuationUtility']-values['questionFixedHeroUtility']) <= global_br+EPS,
            'Root deviation fails full-game composition')
    return values['optimalContinuationUtility']


def check_values(request, report):
    request = copy.deepcopy(request)
    request['parent']['input'] = physical.canonical_input(request['parent']['input'])
    request['parent']['flop'].sort()
    require(set(request) == {'schemaVersion', 'parent', 'hero', 'heroCards'}
            and request['schemaVersion'] == 'pokerlab-root-envelope-request/v1'
            and request['hero'] == 5 and request['heroCards'] == '9d 9h', 'Declared case differs')
    require(set(report) == {'schemaVersion', 'publicationStatus', 'trainerAdmission', 'request', 'evScope',
                           'reachUnits', 'context', 'parent', 'envelope', 'rejection', 'completeOuterBounds'}
            and report['schemaVersion'] == 'pokerlab-root-envelope-report/v1'
            and report['publicationStatus'] == 'ROOT_SECURITY_ENVELOPE_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request'] == request
            and report['evScope'] == 'ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
            and report['reachUnits'] == 'ORIGINAL_JOINT_ROOT_CHANCE/v1'
            and report['completeOuterBounds'] is True and report['rejection'] is None,
            'Conservative research report identity differs')
    parent = report['parent']; parent_receipt = size.check_values(request['parent'], parent)
    require(parent == read(size.REPORT), 'Published original joint parent differs')
    tree = size.literal_tree(parent, dict(binding=parent['binding']['physicalCarrier']['sourceBinding']), request['parent'])
    key = '5:supplied-size-menu-preflop:supplied-conditional-preflop:9d 9h:'
    roots, inside, outside = [], set(), set()
    def scope(node, chance=1., previous=False, selected=False):
        if node.actor == 5 and node.key == key:
            require(not previous, 'Question follows a player action'); selected = True; roots.append((chance, node))
        if node.actor == 5:
            (inside if selected else outside).add(node.key)
        for a, child in node.edges:
            scope(child, chance*a if node.actor == -1 else chance, previous or node.actor >= 0, selected)
    scope(tree)
    require(len(roots) == 8 and not inside.intersection(outside), 'Root deviation is not separable')
    mass = math.fsum(p for p, _ in roots); close(mass, 1/3, 'Original root chance differs')
    audit = report['envelope']
    require(audit['algorithm'] == ALGORITHM and audit['scope'] == SCOPE and audit['trainerAdmission'] is False
            and audit['question'] == dict(hero=5, informationSet=key) and audit['baseline'] == parent['solve']
            and audit['fixedPolicyAlgorithm'] == POLICY and audit['securitySlack'] == EPS,
            'Envelope provenance/scope differs')
    close(audit['questionChanceMass'], mass, 'Root chance was scaled or replaced')
    require(len(audit['support']) == len(roots), 'Root support incomplete')
    for i, (s, (p, _)) in enumerate(zip(audit['support'], roots, strict=True)):
        require(s['rootIndex'] == i and len(s['stateHash']) == 64, 'Root identity differs')
        close(s['chanceWeight'], p, 'Original root chance weight differs')
    chips = [0, 0, 0, 3, .5, 1]
    players = [dict(seat=s, status='FOLDED' if i in (0, 1, 2, 4) else 'ACTING' if i == 5 else 'ACTIVE',
                    committedBb=chips[i], remainingStackBb=100-chips[i]) for i, s in enumerate(size.SEATS)]
    context = dict(actingSeat='BB', publicHistory='', history=parent['handoffs'][0]['completePreflopHistory'][:-1],
                   potBb=4.5, toCallBb=2., players=players,
                   actions=[dict(action=a, amountBb=v) for a, v in [('fold', 0.), ('call', 2.), ('raise:9.0', 8.)]])
    actions = ['fold', 'call', 'raise:9.0']
    require(report['context'] == context and [a['action'] for a in audit['actions']] == actions
            and all([a for a, _ in n.edges] == actions for _, n in roots), 'Complete root menu/context differs')
    choices = {}
    for row in parent['solve']['secondFlow']['conservation']:
        moves = [parent['solve']['secondFlow']['sequences'][i]['action'] for i in row['childSequences']]
        choices[row['key']] = max(moves, key=lambda a: parent['candidate']['strategy'][row['key']][a])
    require(audit['fixedHeroChoices'] == choices and len(choices) == 33, 'Complete greedy pure policy differs')
    normalized = [(p/mass, n) for p, n in roots]
    question = size.Node(-1, edges=tuple(normalized))
    op = audit['opponentProjection']; opponent = parent['solve']['firstFlow']
    global_form = form([(1., tree)], choices, opponent, op)
    question_form = form(normalized, choices, opponent, op)
    check_form(audit['globalFixedPolicy'], global_form); check_form(audit['questionFixedPolicy'], question_form)
    matrix, rhs, width = bounds.face(audit, parent['solve']); n = len(matrix[0])
    guarantee, upper = audit['guaranteeWitness'], audit['questionUpperWitness']
    base.certificate(op['inequalities'], op['rhs'], [-v for v in global_form['cost']], guarantee)
    base.certificate(matrix, rhs, question_form['cost']+[0]*(n-width), upper)
    lower = global_form['constant']-guarantee['certificate']['dualValue']-EPS
    fixed_upper = question_form['constant']+upper['certificate']['dualValue']+EPS
    allowance = (audit['globalHeroUpperValue']+EPS-lower)/mass
    require(allowance >= 0, 'Negative global guarantee allowance')
    common = fixed_upper+allowance
    for field, value in [('guaranteedGlobalLower', lower), ('fixedQuestionUpper', fixed_upper),
                         ('securityAllowance', allowance), ('commonActionUpper', common)]:
        close(audit[field], value, 'Conservative envelope formula differs: '+field)
    witness(guarantee, audit, parent, tree, question, tree, global_form, 'ALL_OPPONENT_FLOWS')
    require(witness(upper, audit, parent, tree, question, question, question_form,
                    'FULL_OPPONENT_SECURITY_FACE') <= common+EPS, 'Common bound excludes optimal response')
    witnesses = [guarantee, upper]
    def ceiling(node):
        return node.utilities[5] if node.actor == -2 else max(ceiling(c) for _, c in node.edges)
    for action in audit['actions']:
        forced = [(p, dict(node.edges)[action['action']]) for p, node in normalized]
        f = form(forced, choices, opponent, op); check_form(action['fixedContinuation'], f)
        w = action['lowerWitness']; witnesses.append(w)
        base.certificate(matrix, rhs, [-v for v in f['cost']]+[0]*(n-width), w)
        lo = f['constant']-w['certificate']['dualValue']-EPS
        cap = max(ceiling(node) for _, node in forced); hi = min(common, cap+EPS)
        close(action['literalUtilityCeiling'], cap, 'Literal terminal ceiling differs')
        close(action['lowerUtility'], lo, 'Padded fixed-policy lower differs')
        close(action['upperUtility'], hi, 'Padded conservative upper differs')
        require(math.isfinite(lo) and math.isfinite(hi) and lo <= hi, 'Invalid action bounds')
        optimal = witness(w, audit, parent, tree, question, size.Node(-1, edges=tuple(forced)), f,
                          'FULL_OPPONENT_SECURITY_FACE')
        require(lo-EPS <= optimal <= hi+EPS, 'Bounds exclude optimal continuation')
    work = audit['work']
    require(work['lpSolves'] == len(witnesses) == 5
            and work['lpArithmeticWork'] == sum(w['certificate']['work']['arithmeticWork'] for w in witnesses) <= 4_000_000_000
            and work['lpPivots'] == sum(sum(w['certificate']['work'][k] for k in
                ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) for w in witnesses) <= 10000
            and 0 <= work['compilerUnits'] <= work['compilerLimit'] == 8_000_000, 'Shared work budget differs')
    return dict(schemaVersion='pokerlab-root-envelope-independent-check/v1', trainerAdmission=False,
                scope=SCOPE, parentCheck=parent_receipt, originalEnvelopeCertificatesChecked=5,
                originalRootWorlds=8, completePureHeroRows=33, checkedActions=actions,
                completeOuterBounds=True, attainedEndpointClaims=False, gradingAuthority=False,
                work=work, freshLpSolves=0, freshCfrRuns=0)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check():
    result = check_values(read(REQUEST), read(REPORT))
    result.update(checkerSourceSha256=sha(Path(__file__)), requestStoredSha256=sha(REQUEST),
                  reportStoredSha256=sha(REPORT), immutableHelperSha256={p: sha(ROOT/'scripts'/p) for p in
                  ['check-flop-size-menu.py', 'check-bb-sizing-physics.py', 'check-big-blind-flop-discovery.py',
                   'check-supplied-flop-action-bounds.py']})
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--receipt', type=Path); args = parser.parse_args()
    data = (json.dumps(check(), indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output:
            output.write(data)
    print(data.decode())
