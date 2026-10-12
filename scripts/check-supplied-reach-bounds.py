"""Independent original-LP and exact literal-plan check for the saved 24-world reach bounds.

Uses stdlib arithmetic, not Java evaluation or an external LP solver. Java replay
separately regenerates physical board counts and the original projections.
"""
import argparse
from fractions import Fraction as F
import gzip
import hashlib
from itertools import product
import json
import math
from pathlib import Path

CAP = 8 * 1024 * 1024
EPS = 1e-8


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key')
        result[key] = value
    return result


def read(path):
    with path.open('rb') as source:
        stored = source.read(CAP + 1)
    if len(stored) > CAP:
        raise ValueError('Stored byte cap exceeded')
    if path.name.endswith('.gz'):
        with gzip.open(path, 'rb') as source:
            raw = source.read(CAP + 1)
    else:
        raw = stored
    if len(raw) > CAP:
        raise ValueError('Expanded byte cap exceeded')
    def constant(value):
        raise ValueError('Non-finite JSON constant: ' + value)
    def number(value):
        result = float(value)
        if not math.isfinite(result):
            raise ValueError('Non-finite JSON number')
        return result
    return json.loads(raw, object_pairs_hook=unique, parse_constant=constant,
                      parse_float=number), stored, raw


def close(left, right):
    if not math.isfinite(left) or not math.isfinite(right) or abs(left - right) > EPS:
        raise ValueError(f'Independent disagreement: {left} versus {right}')


def dot(left, right):
    return math.fsum(a * b for a, b in zip(left, right, strict=True))


def check_endpoint(a, witness, sign):
    h, o, p = a['heroProjection'], a['opponentProjection'], a['orientedPayoff']
    n, z, d = len(o['independentSequences']), len(h['independentSequences']), len(h['rhs'])
    if (n != 15 or z != 16 or d != 8 or len(o['rhs']) != 9
            or len(h['inequalities']) != d or len(o['inequalities']) != 9):
        raise ValueError('Unexpected saved projection dimensions')
    matrix = [p['matrix'][i] + [-h['inequalities'][j][i] for j in range(d)] for i in range(z)]
    matrix += [row + [0] * d for row in o['inequalities']]
    matrix += [p['secondCost'] + h['rhs']]
    rhs = [-v for v in p['firstCost']] + o['rhs'] + [a['globalHeroUpperValue'] + a['securitySlack'] - p['constant']]
    cost = [sign * v for v in a['denominator']['cost']] + [0] * d
    x, y, cert = witness['point'], witness['dual'], witness['certificate']
    if (len(x) != n + d or len(y) != len(rhs) or cert['variables'] != len(x)
            or cert['constraints'] != len(y)
            or cert['algorithm'] != 'OWNED_TWO_PHASE_ORIGINAL_BASIS_LU_LARGEST_BLAND/v1'):
        raise ValueError('Original LP dimensions/identity differ')
    primal = max([0] + [dot(row, x) - bound for row, bound in zip(matrix, rhs)] + [-v for v in x])
    dual = max([0] + [cost[j] - dot([row[j] for row in matrix], y) for j in range(len(x))] + [-v for v in y])
    pv, dv = dot(cost, x), dot(rhs, y)
    if max(primal, dual, abs(pv - dv)) > EPS:
        raise ValueError('Original LP primal/dual certificate failed')
    for key, value in dict(primalValue=pv, dualValue=dv, maximumPrimalViolation=primal,
                           maximumDualViolation=dual, absoluteDualityGap=abs(pv - dv)).items():
        close(cert[key], value)
    if witness['opponentVariables'] != x[:n]:
        raise ValueError('Opponent variables differ from original point')
    flow = witness['opponentFlow']
    realization = flow['realization']
    if flow['actor'] != 5 or len(realization) != len(o['offset']):
        raise ValueError('Wrong original opponent flow')
    for offset, row, actual in zip(o['offset'], o['transform'], realization, strict=True):
        close(offset + dot(row, x[:n]), actual)
    close(realization[0], 1)
    if min(realization) < -EPS or max(realization) > 1 + EPS:
        raise ValueError('Invalid realization probability')
    raises = set()
    for row in flow['conservation']:
        close(realization[row['parentSequence']], math.fsum(realization[i] for i in row['childSequences']))
        if row['parentSequence'] == 0:
            raises.add(next(i for i in row['childSequences'] if flow['sequences'][i]['action'] == 'raise:9.0'))
    masses = witness['masses']
    if len(masses) != 6 or len(raises) != 3:
        raise ValueError('Incomplete selected-world inventory')
    weights = [0] * len(realization)
    for index, mass in enumerate(masses):
        if mass['rootIndex'] != index or mass['opponentSequence'] not in raises:
            raise ValueError('Selected world identity differs')
        close(mass['chanceWeight'], 1 / 24)
        expected = mass['chanceWeight'] * realization[mass['opponentSequence']]
        close(mass['originalRealizationWeight'], expected)
        close(mass['behavioralWeight'], expected)
        weights[mass['opponentSequence']] += mass['chanceWeight']
    for i in raises:
        close(weights[i], 1 / 12)
    close(dot(weights, o['offset']), a['denominator']['constant'])
    for j in range(n):
        close(dot(weights, [row[j] for row in o['transform']]), a['denominator']['cost'][j])
    reach = math.fsum(m['behavioralWeight'] for m in masses)
    close(witness['questionReach'], reach)
    close(a['denominator']['constant'] + sign * pv, reach)
    if witness['globalHeroBestResponse'] > a['globalHeroUpperValue'] + a['securitySlack'] + EPS:
        raise ValueError('Reported witness violates security')
    return a['denominator']['constant'] + sign * dv + sign * EPS


def exact_saddle(worlds):
    """Check a literal rational hero witness against every BB plan; no optimization."""
    bbs = sorted({w[1] for w in worlds})
    plans = list(product(('fold', 'call', 'raise'), ('fold', 'call', 'raise'), ('fold', 'call')))
    def value(btn, eq, plan, params):
        root, at22, at100 = plan
        if root == 'fold': return F(3, 2)
        if root == 'call': return F(13, 2) * eq - 3
        if btn == 'Ah Kh':
            return params[0] * (-3) + (1 - params[0]) * (F(37, 2) * eq - 9)
        if at22 == 'fold': return F(19, 2)
        if at22 == 'call': return F(89, 2) * eq - 22
        fold = F(0) if btn == '9s Js' else params[1] if btn == 'As Qs' else params[2]
        raised = F(101, 2) if at100 == 'fold' else F(401, 2) * eq - 100
        return fold * (-22) + (1 - fold) * raised
    def conditional(bb, plan, params):
        subset = [(btn, eq) for btn, opp, eq in worlds if opp == bb]
        return sum((value(btn, eq, plan, params) for btn, eq in subset), F(0)) / len(subset)
    zero = [F(0)] * 3
    calls = {bb: conditional(bb, ('call', 'fold', 'fold'), zero) for bb in bbs}
    params = [F(3961134, 8981491), F(644698344133760727600, 9838462437035599630081),
              F(1373422625856558816120, 9838462437035599630081)]
    if not all(0 <= probability <= 1 for probability in params):
        raise ValueError('Invalid exact literal hero policy')
    for bb in bbs:
        for plan in plans:
            if conditional(bb, plan, params) < calls[bb]:
                raise ValueError('Exact literal hero policy fails against an opponent plan')
    # Hero guarantees call-all value against every BB plan; call-all gives the reverse bound.
    return sum(calls.values(), F(0)) / len(calls), params


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('request', type=Path)
parser.add_argument('report', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if args.output.exists():
    raise ValueError('Output must be a new path')
request, request_stored, _ = read(args.request)
report, stored, raw = read(args.report)
for world in request['input']['worlds']:
    world['dealtCombos'] = [' '.join(sorted(combo.split())) for combo in world['dealtCombos']]
request['input']['worlds'].sort(key=lambda w: tuple(w['dealtCombos']))
a = report['diagnostic']
expected_specification = dict(
    rules=dict(stackBb=100, smallBlindBb=.5, raiseToBb=[3, 9, 22, 50, 100], raiseSchedule='NEXT_TARGET'),
    history=[dict(seat=seat, action=action) for seat, action in
             [('UTG', 'fold'), ('HJ', 'fold'), ('CO', 'fold'), ('BTN', 'raise:3.0'), ('SB', 'fold')]])
if (report['schemaVersion'] != 'pokerlab-supplied-reach-bounds-report/v1'
        or report['publicationStatus'] != 'FULL_FACE_REACH_BOUNDS_DIAGNOSTIC_ONLY'
        or report['trainerAdmission'] is not False or a['trainerAdmission'] is not False
        or request != report['request']
        or request['schemaVersion'] != 'pokerlab-supplied-reach-bounds-request/v1'
        or request['hero'] != 3 or request['action'] != 'fold'
        or request['informationSet'] != '3:supplied-conditional-preflop:As Qs:|BB:raise:9.0'
        or request['minimumReach'] != .01 or request['securitySlack'] != .0001
        or request['input']['schemaVersion'] != 'pokerlab-supplied-joint-preflop-input/v1'
        or request['input']['specification'] != expected_specification
        or report['binding']['sourceHistoryReachStatus'] != 'UNAVAILABLE_NO_SOURCE_POLICY'
        or report['binding']['model'] != 'TWO_ACTIVE_SUPPLIED_JOINT_PREFLOP_MANDATORY_CHECKDOWN/v1'
        or report['binding']['beliefs'] != 'EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1'
        or report['binding']['payoffAlgorithm'] != 'EXACT_TWO_ACTIVE_SIX_DEALT_BOARD_ENUMERATION/v1'
        or a['algorithm'] != 'FULL_SECURITY_FACE_QUESTION_REACH_BOUNDS_OWNED_LP/v1'
        or a['status'] != 'CERTIFIED_ALL_BELOW_REQUIRED_FLOOR' or a['fixedHeroPast']
        or a['work']['intervalLpSolves'] != 2):
    raise ValueError('Unsupported saved reach-bounds identity')
if a['question'] != {k: request[k] for k in ('hero', 'informationSet', 'action')}:
    raise ValueError('Question identity differs')
close(a['requiredMinimumReach'], request['minimumReach'])
close(a['securitySlack'], request['securitySlack'])
lower = check_endpoint(a, a['minimumReachWitness'], -1)
upper = check_endpoint(a, a['maximumReachWitness'], 1)
close(lower, a['certifiedReachLowerBound'])
close(upper, a['certifiedReachUpperBound'])
close(a['minimumReachWitness']['questionReach'], 0)
if not lower <= upper < .0001 < request['minimumReach']:
    raise ValueError('Reach upper does not rule out every currently supported floor')
prior, payoffs = report['prior'], report['payoffs']
if len(prior) != 24 or len(payoffs) != 24 or len(request['input']['worlds']) != 24:
    raise ValueError('Expected separate 24-world model')
expected = {(btn, bb, hj) for btn in ('9s Js', 'Ah Kh', 'As Qs', 'Qh Th')
            for bb in ('7d 7h', '9d 9h', 'Ac Jc') for hj in ('2d 3d', '6h 8c')}
seen, worlds = set(), []
for index, (world, payoff, input_world) in enumerate(zip(prior, payoffs, request['input']['worlds'], strict=True)):
    combos, c = world['dealtCombos'], payoff['counts']
    if (world['dealIndex'] != index or payoff['dealIndex'] != index
            or payoff['dealtCombos'] != combos or input_world['dealtCombos'] != combos
            or input_world['weight'] != 1 or combos[0] != '4c 4d' or combos[2] != '2h 3h'
            or combos[4] != '5c 6c' or len(set(' '.join(combos).split())) != 12
            or c['firstSeat'] != 3 or c['secondSeat'] != 5 or c['boards'] != 658008
            or c['handEvaluations'] != 1316016
            or min(c[k] for k in ('firstWins', 'secondWins', 'ties')) < 0
            or c['firstWins'] + c['secondWins'] + c['ties'] != c['boards']):
        raise ValueError('Physical world/count identity differs')
    close(world['conditionalProbability'], 1 / 24)
    key = (combos[3], combos[5], combos[1])
    if key not in expected or key in seen:
        raise ValueError('Duplicate or foreign world')
    seen.add(key)
    worlds.append((combos[3], combos[5], F(2 * c['firstWins'] + c['ties'], 2 * c['boards'])))
game, params = exact_saddle(worlds)
if game != F(315337, 607392):
    raise ValueError('Exact literal saddle value differs')
close(float(game), a['globalHeroUpperValue'])
summary = dict(schemaVersion='pokerlab-independent-reach-bounds-check/v1', trainerAdmission=False,
               freshLpSolves=0, originalLpCertificatesChecked=2, checkedPhysicalWorlds=24,
               literalOpponentPlansChecked=54, exactGameValueRational=str(game),
               exactHeroPolicyParameters=[str(x) for x in params],
               independentlyCheckedReachLower=lower, independentlyCheckedReachUpper=upper,
               sourceScriptBytesSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               requestStoredBytesSha256=hashlib.sha256(request_stored).hexdigest(),
               reportStoredBytesSha256=hashlib.sha256(stored).hexdigest(),
               reportExpandedBytesSha256=hashlib.sha256(raw).hexdigest(),
               limitations=['Saved integer board counts and projections; Java physical replay regenerates both',
                            'One supplied-belief model; no conditional EV or trainer admission'])
with args.output.open('x', encoding='utf-8', newline='\n') as out:
    out.write(json.dumps(summary, indent=2, allow_nan=False) + '\n')
print('Both original LP certificates and exact 54-plan saddle checked; maximum reach below every supported floor')
