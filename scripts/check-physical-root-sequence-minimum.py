"""Independent physical sequence-dual minimum and legacy comparison; no optimizer/CFR."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
IMMUTABLE_HELPERS = {'check-root-minimum.py': '12f49d03dd35e19cdb56fa498b83014787cae75bd70042d271b2933744cc9c7d', 'check-root-sequence-minimax.py': '5a242aa3b791e04807e5c3f0426633db2c03c5001554f24461f2198db97ce765', 'check-root-envelope-feedback.py': 'e0e7d3e803fe6d2de99b893ccda9e0e3ce71b93b74580be398c3c6ac03fdcf48', 'check-root-security-envelope.py': 'dd871cc2d9394a52263a2f8945d5713e2f9e0a8a21fde0be0ead0bc60c28a0b1', 'check-reduced-preflop.py': '4202b10cbeddab54d20708b87d5769403bbbf4dda3a604df3518299a851d0960', 'check-root-minimax.py': 'a41d0959f74c5e00a0a43843c13b31107d396fdd8da43b0f87e4685a84ad6cc5', 'check-resolvable-refined-lp.py': '84795dabca56dd86a4870b23efd8d5731d4b4b17d3399fbfa818f7ee9d18f080'}
for name, pin in IMMUTABLE_HELPERS.items():
    if hashlib.sha256((ROOT/'scripts'/name).read_bytes()).hexdigest() != pin:
        raise ValueError('Immutable physical minimum helper changed: '+name)

def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT/'scripts'/filename)
    value = importlib.util.module_from_spec(spec); sys.modules[name] = value; spec.loader.exec_module(value); return value

physical = module('physical_sequence_legacy', 'check-root-minimum.py')
sequence = module('physical_sequence_kernel', 'check-root-sequence-minimax.py')
old = sequence.old
require, close, near, dot = old.require, old.close, old.near, old.dot
behavior, value, best_response = sequence.behavior, sequence.value, sequence.best_response
node = sequence.node
DATA = ROOT/'docs/data'
REQUEST = DATA/'supplied-root-sequence-minimum-request.json'
REPORT = DATA/'supplied-root-sequence-minimum-report.json.gz'
LEGACY_SHA = 'ea48889cd6677102c917d1990e6b14dc576a12dad75bc7a86d3fb21cc1906403'
SCHEMA = 'pokerlab-root-sequence-minimum-report/v1'
STATUS = 'PHYSICAL_ROOT_SEQUENCE_RAISE_MINIMUM_RESEARCH_ONLY'
EPS = 1e-8
read = physical.read

def literal(n):
    return node(n.actor, n.key, [a for a,_ in n.edges] if n.actor >= 0 else (),
                [p for p,_ in n.edges] if n.actor == -1 else (),
                [literal(child) for _,child in n.edges], list(n.utilities))

def context():
    source = physical.context()
    require(hashlib.sha256(physical.REPORT.read_bytes()).hexdigest() == LEGACY_SHA, 'Pinned legacy minimum bytes differ')
    legacy_report = read(physical.REPORT)
    legacy_check = physical.check_values(read(physical.REQUEST), legacy_report, source)
    return source, legacy_report, legacy_check, literal(source[2])

def physical_payoff(tree, hero, opponent, hf, of, constant_sum):
    flows = {hero: hf, opponent: of}
    ids = {p: {(s['key'], s['action']): s['index'] for s in f['sequences'][1:]} for p, f in flows.items()}
    rows = {p: {r['key']: r for r in f['conservation']} for p, f in flows.items()}
    seen = {hero: set(), opponent: set()}
    result = [[0.]*len(of['sequences']) for _ in hf['sequences']]
    def visit(n, hs=0, os=0, chance=1.):
        actor = n['actor']
        if actor == -2:
            close(n['utilities'][hero]+n['utilities'][opponent], constant_sum)
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


def endpoint(a, saved, parent, tree, legacy_report):
    h, o = 5, 3
    require(a['algorithm'] == sequence.ALGORITHM and a['scope'] == sequence.SCOPE and a['trainerAdmission'] is False, 'Sequence minimum scope differs')
    require(a['question'] == dict(hero=h, informationSet=saved['envelope']['envelope']['question']['informationSet'], action='raise:9.0'), 'Original physical root question differs')
    require(a['requiredMinimumReach'] == .0001 and a['securitySlack'] == EPS, 'Original physical gates differ')
    base = a['baseline']; require(base == parent['solve'] and base['firstActor'] == 3 and base['secondActor'] == 5
            and a['firstParentWitness'] == parent['firstLpWitness'] and a['secondParentWitness'] == parent['secondLpWitness']
            and a['securityFaceHash'] == legacy_report['minimum']['securityFaceHash'], 'Original physical parent and face identity differ')
    f, s, fp, sp = (base[k] for k in ['firstFlow', 'secondFlow', 'firstProjection', 'secondProjection'])
    require(f['actor'] == 3 and s['actor'] == 5, 'Original flow actor labels differ')
    old.projection(f, fp); old.projection(s, sp)
    parent_payoff = old.reduce_payoff(physical_payoff(tree, 3, 5, f, s, base['constantSum']), fp, sp)
    for key, expected in parent_payoff.items(): near(base['projectedPayoff'][key], expected)
    for witness, own, other, first, flow in [(a['firstParentWitness'], fp, sp, True, f), (a['secondParentWitness'], sp, fp, False, s)]:
        optimum = old.certificate(*old.parent_problem(parent_payoff, own, other, first), witness, old.PARENT_LP)
        require(witness['certificate'] == base['firstLp' if first else 'secondLp'], 'Parent witness binding differs')
        close(base['lowerValue' if first else 'upperValue'], parent_payoff['constant']+(optimum if first else -optimum))
        realization = [off+dot(row, witness['point'][:len(own['independentSequences'])]) for off, row in zip(own['offset'], own['transform'], strict=True)]
        near(flow['realization'], realization); behavior(flow, realization)
    hp, op, hf, of = (fp, sp, f, s) if h == 3 else (sp, fp, s, f)
    require(a['heroProjection'] == hp and a['opponentProjection'] == op, 'Oriented projection differs')
    oriented = old.reduce_payoff(physical_payoff(tree, h, o, hf, of, base['constantSum']), hp, op)
    for key, expected in oriented.items(): near(a['orientedPayoff'][key], expected)
    upper = base['upperValue'] if h == 3 else base['constantSum']-base['lowerValue']; close(a['globalHeroUpperValue'], upper)
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
    require(a['support'] == legacy_report['minimum']['support'], 'Original physical root state identities differ')
    for (probability, n), support in zip(roots, a['support'], strict=True):
        close(support['originalChanceWeight'], probability); close(support['posteriorProbability'], probability/mass)
        forest.append((probability/mass, n['children'][n['actions'].index('raise:9.0')]))
    conditional = node(-1, probabilities=[p for p, _ in forest], children=[n for _, n in forest])
    ids = {p: {(s['key'], s['action']): s['index'] for s in flow['sequences'][1:]} for p, flow in [(h, hf), (o, of)]}
    forced = ids[h][a['question']['informationSet'], 'raise:9.0']; grid = [[0.]*len(of['sequences']) for _ in hf['sequences']]
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
    complete_plan_br = max(p['constant']+dot(p['cost'], witness['point'][:w]) for p in legacy_report['minimum']['plans'])
    close(local_br, complete_plan_br)
    cert = witness['certificate']; objective = local['constant']-cert['primalValue']
    close(objective, local_br); close(a['minimum']['witnessUtility'], local_br)
    close(a['minimum']['lowerBound'], local['constant']-cert['dualValue']-old.EPS)
    close(a['minimum']['upperBound'], max(objective, local_br)+old.EPS)
    work = a['work']; require(all(type(v) is int for v in work.values()), 'Integer work counters required')
    require(work['compilerLimit'] == 8_000_000 and 0 < work['compilerUnits'] <= work['compilerLimit'], 'Compiler work differs')
    require(work['enumeratedContinuationPlans'] == 0 and work['endpointLpSolves'] == 1, 'Unexpected plan enumeration or endpoint solve count')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Endpoint work totals differ')

    return dict(parentCertificatesChecked=2, endpointCertificatesChecked=1, originalHeroSequences=len(hf['sequences']), originalOpponentSequences=len(of['sequences']), variables=columns, constraints=rows)


def check_values(request, report, original=None):
    source_context, legacy_report, legacy_check, tree = original if original is not None else context()
    saved, parent, _, _, source_check = source_context
    request = copy.deepcopy(request)
    nested = request['sourceRequest']['envelope']['parent']
    nested['input'] = physical.source.physical.canonical_input(nested['input']); nested['flop'].sort()
    require(set(request) == {'schemaVersion','sourceRequest','sourceReportHash','action','legacyMinimumStoredSha256'}
            and request['schemaVersion'] == 'pokerlab-root-sequence-minimum-request/v1'
            and request['sourceRequest'] == saved['request'] and request['sourceReportHash'] == physical.SOURCE_HASH
            and request['action'] == 'raise:9.0' and request['legacyMinimumStoredSha256'] == LEGACY_SHA, 'Declared physical sequence request differs')
    require(report['schemaVersion'] == SCHEMA and report['publicationStatus'] == STATUS and report['trainerAdmission'] is False
            and report['minimumScope'] == sequence.SCOPE and report['request'] == request and report['parent'] == parent
            and report['context'] == saved['envelope']['context'] and report['legacyMinimum'] == legacy_report, 'Physical sequence report lineage differs')
    a = report['minimum']; complete = a is not None
    require(report['completeMinimum'] == complete and (report['rejection'] is None) == complete, 'Owned minimum or typed failure required')
    proof = dict(parentCertificatesChecked=0,endpointCertificatesChecked=0)
    reasons = set(); overlap = difference = None
    if complete:
        proof = endpoint(a, saved, parent, tree, legacy_report)
        lower, upper = a['minimum']['lowerBound'], a['minimum']['upperBound']
        legacy = legacy_report['minimum']['minimum']
        overlap = lower <= legacy['upperBound'] and legacy['lowerBound'] <= upper
        difference = a['minimum']['witnessUtility']-legacy['witnessUtility']
        require(type(report['legacyMinimumBracketsOverlap']) is bool and report['legacyMinimumBracketsOverlap'] == overlap, 'Legacy overlap claim differs')
        close(report['legacyMinimumWitnessDifferenceBb'], difference)
        previous = next(x['lowerUtility'] for x in saved['envelope']['envelope']['actions'] if x['action'] == 'raise:9.0')
        close(report['previousConservativeLowerUtilityBb'], previous); close(report['lowerBoundDifferenceBb'], lower-previous)
        if not overlap: reasons.add('LEGACY_MINIMUM_BRACKETS_DISJOINT')
    else:
        failure = report['rejection']
        require(failure['kind'] in ('LP','PARENT','MINIMUM','STRUCTURE') and isinstance(failure['reason'],str) and isinstance(failure['detail'],str), 'Typed failure identity differs')
        for key in ['previousConservativeLowerUtilityBb','lowerBoundDifferenceBb','legacyMinimumWitnessDifferenceBb','legacyMinimumBracketsOverlap']:
            require(report[key] is None, 'Failed sequence minimum cannot publish comparisons')
        reasons.add('MINIMUM:'+failure['kind']+':'+failure['reason'])
    checks = [saved['primary'],*saved['references']]
    require(len(report['policies']) == len(checks) == 3, 'Primary and both fresh references required')
    supported = complete and overlap
    for assessment, check in zip(report['policies'], checks, strict=True):
        require(assessment['original'] == check, 'Fresh physical policy differs')
        utility = check['actionUtilitiesBb']['raise:9.0']; close(assessment['forcedActionUtilityBb'],utility)
        failures = set(check['failures']); violation = None
        if complete and check['insideDeclaredFace']: violation = max(0.,a['minimum']['lowerBound']-utility)
        if complete and not check['insideDeclaredFace']: failures.add('MINIMUM_REFERENCE_OUTSIDE_FACE')
        if violation is not None and violation > EPS: failures.add('REFERENCE_BELOW_MINIMUM_LOWER_BOUND')
        if violation is None: require(assessment['minimumLowerViolationBb'] is None, 'Unchecked policy violation claim')
        else: close(assessment['minimumLowerViolationBb'], violation)
        ok = violation is not None and not failures
        require(assessment['minimumLowerSupported'] == ok and assessment['failures'] == sorted(failures), 'Minimum policy support differs')
        supported = supported and ok
        reasons.update(str(check['iterations'])+':'+f for f in failures)
    require(report['referenceSupportedMinimum'] == supported and report['rejectionReasons'] == sorted(reasons), 'Physical sequence outcome differs')
    return dict(schemaVersion='pokerlab-root-sequence-minimum-independent-check/v1', trainerAdmission=False,
                completeMinimum=complete, referenceSupportedMinimum=supported, legacyMinimumBracketsOverlap=overlap,
                legacyMinimumWitnessDifferenceBb=difference, sequenceMinimumProof=proof, legacyControlChecked=legacy_check,
                originalSourceChecked=source_check, policiesChecked=3, fullActionUpperEndpointClaim=False, freshLpSolves=0, freshCfrRuns=0)

def check():
    require(hashlib.sha256(REPORT.read_bytes()).hexdigest() == '1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f', 'Frozen physical comparison report bytes differ')
    receipt = check_values(read(REQUEST),read(REPORT))
    receipt.update(requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),
                   reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),
                   checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),immutableHelperSha256=IMMUTABLE_HELPERS)
    return receipt

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
