"""Independent stdlib certificate and literal-pot check for the saved 24-world rejection.

No LP solver or Java evaluator is called. Original primal/dual inequalities are
recomputed; the literal BB-call-all opponent proves zero reach is feasible.
Java physical replay separately regenerates integer board counts and the LPs.
"""
import argparse
from fractions import Fraction
import gzip
import hashlib
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
    stored = path.read_bytes()
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
    if len(left) != len(right):
        raise ValueError('Vector dimension mismatch')
    return math.fsum(a * b for a, b in zip(left, right))


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
w = a['minimumReachWitness']
if (report['schemaVersion'] != 'pokerlab-supplied-reach-rejection-report/v1'
        or report['publicationStatus'] != 'REJECTED_FULL_FACE_REACH_DIAGNOSTIC_ONLY'
        or report['trainerAdmission'] is not False or a['trainerAdmission'] is not False
        or request != report['request']
        or request['schemaVersion'] != 'pokerlab-supplied-reach-rejection-request/v1'
        or request['hero'] != 3 or request['action'] != 'fold'
        or request['informationSet'] != '3:supplied-conditional-preflop:As Qs:|BB:raise:9.0'
        or request['minimumReach'] != .01 or request['securitySlack'] != .0001
        or a['algorithm'] != 'FULL_SECURITY_FACE_MINIMUM_REACH_REJECTION_OWNED_LP/v1'
        or a['status'] != 'WITNESS_BELOW_REQUIRED_FLOOR'
        or a['fixedHeroPast'] or 'posterior' in w or 'conditionalHeroBestResponse' in w):
    raise ValueError('Unsupported saved zero-reach diagnostic identity')
close(a['requiredMinimumReach'], request['minimumReach'])
close(a['securitySlack'], request['securitySlack'])
if a['question'] != {k: request[k] for k in ('hero', 'informationSet', 'action')}:
    raise ValueError('Question identity differs')
h, o, p = a['heroProjection'], a['opponentProjection'], a['orientedPayoff']
n, z, duals = len(o['independentSequences']), len(h['independentSequences']), len(h['rhs'])
if (n != 15 or z != 16 or duals != 8 or len(o['rhs']) != 9
        or len(h['inequalities']) != duals or len(o['inequalities']) != 9):
    raise ValueError('Unexpected saved projection dimensions')
matrix = [p['matrix'][i] + [-h['inequalities'][j][i] for j in range(duals)] for i in range(z)]
matrix += [row + [0] * duals for row in o['inequalities']]
matrix += [p['secondCost'] + h['rhs']]
rhs = [-v for v in p['firstCost']] + o['rhs'] + [a['globalHeroUpperValue'] + a['securitySlack'] - p['constant']]
cost = [-v for v in a['denominator']['cost']] + [0] * duals
x, y, cert = w['point'], w['dual'], w['certificate']
if (len(matrix) != len(rhs) or len(y) != len(rhs) or len(x) != n + duals
        or cert['variables'] != len(x) or cert['constraints'] != len(y)
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
close(a['certifiedReachLowerBound'], a['denominator']['constant'] - dv - EPS)
if w['opponentVariables'] != x[:n]:
    raise ValueError('Opponent variables differ from original point')
flow = w['opponentFlow']
if flow['actor'] != 5 or len(flow['realization']) != len(o['offset']):
    raise ValueError('Wrong opponent flow')
realization = flow['realization']
for offset, row, actual in zip(o['offset'], o['transform'], realization):
    close(offset + dot(row, x[:n]), actual)
close(realization[0], 1)
for row in flow['conservation']:
    close(realization[row['parentSequence']], math.fsum(realization[i] for i in row['childSequences']))
if min(realization) < -EPS or max(realization) > 1 + EPS:
    raise ValueError('Invalid original realization probability')
root_actions = {}
for row in flow['conservation']:
    if row['parentSequence'] == 0:
        hand = row['key'].split(':')[2]
        actions = {flow['sequences'][i]['action']: realization[i] for i in row['childSequences']}
        for action, expected in [('fold', 0), ('call', 1), ('raise:9.0', 0)]:
            close(actions[action], expected)
        root_actions[hand] = actions
if set(root_actions) != {'7d 7h', '9d 9h', 'Ac Jc'}:
    raise ValueError('Incomplete root opponent inventory')
prior, payoffs = report['prior'], report['payoffs']
if len(prior) != 24 or len(payoffs) != 24 or len(request['input']['worlds']) != 24:
    raise ValueError('Expected separate 24-world physical model')
expected_worlds = {(btn, bb, hj) for btn in ('9s Js', 'Ah Kh', 'As Qs', 'Qh Th')
                   for bb in root_actions for hj in ('2d 3d', '6h 8c')}
seen = set()
literal = Fraction(0)
for index, (world, payoff, input_world) in enumerate(zip(prior, payoffs, request['input']['worlds'])):
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
    if key not in expected_worlds or key in seen:
        raise ValueError('Duplicate or foreign world')
    seen.add(key)
    equity = Fraction(2 * c['firstWins'] + c['ties'], 2 * c['boards'])
    literal += (Fraction(13, 2) * equity - 3) / 24
if literal != Fraction(315337, 607392):
    raise ValueError('Literal BB-call-all value differs from saved physical control')
close(float(literal), w['globalHeroBestResponse'])
close(float(literal), a['globalHeroUpperValue'])
if len(w['masses']) != 6:
    raise ValueError('Incomplete selected world inventory')
for index, mass in enumerate(w['masses']):
    if mass['rootIndex'] != index:
        raise ValueError('Selected mass index differs')
    close(mass['chanceWeight'], 1 / 24)
    close(mass['originalRealizationWeight'], mass['chanceWeight'] * realization[mass['opponentSequence']])
    close(mass['originalRealizationWeight'], 0)
    close(mass['behavioralWeight'], 0)
close(w['questionReach'], 0)
close(a['denominator']['constant'] - pv, 0)
summary = dict(schemaVersion='pokerlab-independent-zero-reach-check/v1', trainerAdmission=False,
               freshLpSolves=0, originalLpCertificatesChecked=1, checkedPhysicalWorlds=24,
               checkedSelectedMasses=6, independentMinimumReach=0,
               literalHeroBestResponseRational=f'{literal.numerator}/{literal.denominator}',
               literalHeroBestResponseBb=float(literal),
               sourceScriptBytesSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               requestStoredBytesSha256=hashlib.sha256(request_stored).hexdigest(),
               reportStoredBytesSha256=hashlib.sha256(stored).hexdigest(),
               reportExpandedBytesSha256=hashlib.sha256(raw).hexdigest(),
               limitations=['Saved integer board counts and projection; Java physical replay regenerates both',
                            'One explicit supplied-belief counterexample; no conditional EV or trainer admission'])
with args.output.open('x', encoding='utf-8', newline='\n') as out:
    out.write(json.dumps(summary, indent=2, allow_nan=False) + '\n')
print('Original LP certificate and literal 24-world zero-reach counterexample verified')
