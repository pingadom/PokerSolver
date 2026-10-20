"""Check all 298 owned v6 LP witnesses with exact original binary64 arithmetic; no optimizer."""
import argparse
from fractions import Fraction as F
import gzip
import hashlib
import io
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIMIT = 8 * 1024 * 1024
EPS = F(1e-8)
ALGORITHM = 'OWNED_ASCENDING_L1_RESOLVABLE_COST_COMPENSATED_BASIS_DUAL_DIRECTION_ORIGINAL_CERTIFICATE/v6'
BUDGET = ROOT / 'docs/data/weak-hand-resolvable-study-budget.json'
EVIDENCE = ROOT / 'docs/data/resolvable-refined-lp-evidence.json.gz'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    with Path(path).open('rb') as stream:
        raw = stream.read(LIMIT + 1)
    require(len(raw) <= LIMIT, 'Stored byte cap exceeded')
    if str(path).endswith('.gz'):
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
            expanded = stream.read(LIMIT + 1)
    else:
        expanded = raw
    require(len(expanded) <= LIMIT, 'Expanded byte cap exceeded')
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    def number(text):
        value = float(text)
        require(math.isfinite(value), 'Nonfinite JSON number')
        return value
    return json.loads(expanded, object_pairs_hook=pairs, parse_float=number,
                      parse_constant=lambda _: require(False, 'Nonfinite JSON constant')), raw


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def exact(matrix, rhs, cost, point, dual):
    require(0 < len(rhs) == len(matrix) == len(dual) <= 512 and
            0 < len(cost) == len(point) <= 512 and all(len(row) == len(cost) for row in matrix), 'LP dimensions differ')
    require(all(type(v) in (float, int) and math.isfinite(v) for row in [*matrix, rhs, cost, point, dual] for v in row), 'Nonfinite LP value')
    require(all(abs(v) <= 1_000_000 for row in [*matrix, rhs, cost] for v in row), 'LP coefficient cap exceeded')
    a = [list(map(F, row)) for row in matrix]
    b, c, x, y = (list(map(F, row)) for row in [rhs, cost, point, dual])
    dot = lambda u, v: sum((i*j for i, j in zip(u, v, strict=True)), F(0))
    pv, dv = dot(c, x), dot(b, y)
    primal = max([F(0), *(-v for v in x), *(dot(row, x)-bound for row, bound in zip(a, b, strict=True))])
    dviolation = max([F(0), *(-v for v in y), *(value-dot(col, y) for col, value in zip(zip(*a), c, strict=True))])
    gap = abs(pv-dv)
    require(max(primal, dviolation, gap) <= EPS, 'Exact original primal/dual certificate failed')
    return dict(primalValue=pv, dualValue=dv, maximumPrimalViolation=primal,
                maximumDualViolation=dviolation, absoluteDualityGap=gap)


def problems():
    budget, _ = read(BUDGET)
    for path, expected in budget['fixtureSha256'].items():
        require(digest(ROOT/path) == expected, 'Regression fixture bytes differ')
    cases = {}
    for origin, name, key in [('ORACLE', 'bounded-lp-oracle-controls.json.gz', 'problems'),
                              ('CAPTURED', 'lp-refined-basis-endpoints.json.gz', 'cases')]:
        data, _ = read(ROOT/'solver/src/test/resources'/name)
        for p in data[key]:
            identity = origin, p.get('group', '')+':'+str(p['id'])
            require(identity not in cases, 'Duplicate source problem')
            cases[identity] = p
    cases['NEAR_RATIO', 'two-distinct-positive-steps'] = dict(matrix=[[1.], [35491.780049572175]], rhs=[1.0236165219481362e-12, 2.2032117954153633e-8], cost=[1.], expectedMaximum=2.2032117954153633e-8/35491.780049572175)
    weak, raw = read(ROOT/'solver/src/test/resources/lp-resolvable-refined-weak-hand.json.gz')
    require(hashlib.sha256(gzip.decompress(raw)).hexdigest() == '415f55db28df9474e8f927c24577efe454e526882898bc6f26c013a4fa48c2e2', 'Original captured matrix differs')
    oracle, _ = read(ROOT/'solver/src/test/resources/lp-resolvable-refined-weak-hand-oracle.json')
    require(oracle['originalProblemSha256'] == '415f55db28df9474e8f927c24577efe454e526882898bc6f26c013a4fa48c2e2' and oracle['success'] is True and oracle['trainerAdmission'] is False, 'Independent oracle provenance differs')
    checked = exact(weak['matrix'], weak['rhs'], weak['cost'], oracle['point'], oracle['dual'])
    require(abs(checked['primalValue']-F(oracle['primalValue'])) <= EPS, 'Oracle objective differs')
    cases['WEAK_HAND', 'call-lower'] = dict(weak, expectedMaximum=oracle['primalValue'])
    cases['SMALL_PRICING', 'resolvable-positive'] = dict(matrix=[[1.]], rhs=[1_000_000.], cost=[1e-11], expectedMaximum=1e-5)
    require(len(cases) == budget['lpRegressionBudget']['maximumCases'] == 298, 'Declared control inventory differs')
    return cases, budget


def check(path=EVIDENCE):
    cases, budget = problems()
    data, raw = read(path)
    require(set(data) == {'schemaVersion', 'scope', 'trainerAdmission', 'cases'} and
            data['schemaVersion'] == 'pokerlab-resolvable-refined-lp-evidence/v1' and
            data['scope'] == 'ORIGINAL_LP_REGRESSIONS_NOT_PHYSICAL_OR_TRAINER_QUALIFICATION' and
            data['trainerAdmission'] is False and len(data['cases']) == len(cases), 'LP evidence identity differs')
    seen = set(); work = pivots = 0; maxima = dict(maximumPrimalViolation=F(0), maximumDualViolation=F(0), absoluteDualityGap=F(0))
    for row in data['cases']:
        require(set(row) == {'origin', 'id', 'point', 'dual', 'certificate'}, 'Witness fields differ')
        key = row['origin'], row['id']
        require(key in cases and key not in seen, 'Missing, duplicate or unknown LP')
        seen.add(key); p = cases[key]; c = row['certificate']
        require(c['algorithm'] == ALGORITHM and c['variables'] == len(p['cost']) and c['constraints'] == len(p['rhs']), 'Explicit v6 identity differs')
        metrics = exact(p['matrix'], p['rhs'], p['cost'], row['point'], row['dual'])
        require(abs(metrics['primalValue']-F(p['expectedMaximum'])) <= EPS, 'Original oracle objective differs')
        for name, value in metrics.items():
            require(math.isfinite(c[name]) and abs(F(c[name])-value) <= EPS, 'Certificate field differs')
            if name in maxima: maxima[name] = max(maxima[name], value)
        w = c['work']; count = sum(w[k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots'])
        require(all(type(w[k]) is int and w[k] >= 0 for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots', 'basisFactorizations', 'arithmeticWork']) and count <= 10000 and w['arithmeticWork'] <= 4_000_000_000 and type(w['blandFallback']) is bool, 'Original LP work cap exceeded')
        work += w['arithmeticWork']; pivots += count
    require(seen == set(cases) and work <= budget['lpRegressionBudget']['maximumAggregateArithmeticWork'] and pivots <= budget['lpRegressionBudget']['maximumAggregatePivots'], 'Cumulative control budget exceeded')
    return dict(schemaVersion='pokerlab-independent-resolvable-refined-lp-check/v1', trainerAdmission=False,
                scope='EXACT_ORIGINAL_BINARY64_LP_WITNESSES_ONLY', casesChecked=len(seen), originalOracleCases=292,
                priorCapturedEndpoints=3, nearRatioCases=1, weakHandCases=1, smallPricingCases=1,
                independentWeakHandOracleChecked=True, aggregateArithmeticWork=work, aggregatePivots=pivots,
                **{name: float(value) for name, value in maxima.items()},
                evidenceSha256=hashlib.sha256(raw).hexdigest(), budgetSha256=digest(BUDGET), checkerSha256=digest(__file__))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path, default=EVIDENCE)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    receipt = check(args.evidence)
    with args.output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(receipt, indent=2)+'\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__': main()
