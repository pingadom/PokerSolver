"""Independent original physical complete-menu minima; no optimization or admission.

The endpoint verifier explicitly generalises the prior pinned physical verifier
to all three original actions and the remaining cumulative compiler cap.
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
HELPER_SHA = "5d8ea982b786bb22256ca5d85e0488394733457de978487ae170c7fb1f13cec3"
helper = ROOT/'scripts/check-physical-root-sequence-minimum.py'
if hashlib.sha256(helper.read_bytes()).hexdigest() != HELPER_SHA:
    raise ValueError('Original physical sequence verifier changed')
spec = importlib.util.spec_from_file_location('physical_batch_prior', helper)
prior = importlib.util.module_from_spec(spec); sys.modules[spec.name] = prior; spec.loader.exec_module(prior)
sequence, old = prior.sequence, prior.old
require, close, near, dot = prior.require, prior.close, prior.near, prior.dot
physical_payoff, behavior, value, best_response, node = prior.physical_payoff, prior.behavior, prior.value, prior.best_response, prior.node
read = prior.read
DATA = ROOT/'docs/data'
REQUEST = DATA/'supplied-root-sequence-batch-request.json'
REPORT = DATA/'supplied-root-sequence-batch-report.json.gz'
REPORT_SHA = "be3e7400a75fd86bb617cf4e70f758e2a885d80ba052e6441f28d81c61a0dc8b"
PRIOR_SHA = '1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f'
ACTIONS = ['fold', 'call', 'raise:9.0']
SCHEMA = 'pokerlab-physical-root-sequence-batch-report/v1'
STATUS = 'PHYSICAL_ROOT_SEQUENCE_COMPLETE_MENU_MINIMA_RESEARCH_ONLY'
ALGORITHM = 'ROOT_SEQUENCE_MINIMUM_SHARED_OWNED_PARENT_BATCH/v1'
SCOPE = 'ROOT_MINIMA_ONLY_NOT_FULL_ACTION_INTERVALS_OR_TRAINER_ADMISSION/v1'
EPS = 1e-8

def context():
    original = prior.context()
    require(hashlib.sha256(prior.REPORT.read_bytes()).hexdigest() == PRIOR_SHA, 'Original sequence minimum bytes differ')
    previous = read(prior.REPORT)
    proof = prior.check_values(read(prior.REQUEST), previous, original)
    return original, previous, proof

def endpoint(a, saved, parent, tree, legacy_report, action, remaining):
    h, o = 5, 3
    require(a['algorithm'] == sequence.ALGORITHM and a['scope'] == sequence.SCOPE and a['trainerAdmission'] is False, 'Sequence minimum scope differs')
    require(a['question'] == dict(hero=h, informationSet=saved['envelope']['envelope']['question']['informationSet'], action=action), 'Original physical root question differs')
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
        forest.append((probability/mass, n['children'][n['actions'].index(action)]))
    conditional = node(-1, probabilities=[p for p, _ in forest], children=[n for _, n in forest])
    ids = {p: {(s['key'], s['action']): s['index'] for s in flow['sequences'][1:]} for p, flow in [(h, hf), (o, of)]}
    forced = ids[h][a['question']['informationSet'], action]; grid = [[0.]*len(of['sequences']) for _ in hf['sequences']]
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
    witness = a['witness']
    certificate_work = witness['certificate']['work']
    require(type(certificate_work['blandFallback']) is bool
            and all(type(v) is int for k, v in certificate_work.items() if k != 'blandFallback'), 'Integer certificate work required')
    old.certificate(matrix, rhs, cost, witness, old.LP)
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
    if action == 'raise:9.0':
        complete_plan_br = max(p['constant']+dot(p['cost'], witness['point'][:w]) for p in legacy_report['minimum']['plans'])
        close(local_br, complete_plan_br)
    cert = witness['certificate']; objective = local['constant']-cert['primalValue']
    close(objective, local_br); close(a['minimum']['witnessUtility'], local_br)
    close(a['minimum']['lowerBound'], local['constant']-cert['dualValue']-old.EPS)
    close(a['minimum']['upperBound'], max(objective, local_br)+old.EPS)
    work = a['work']; require(all(type(v) is int for v in work.values()), 'Integer work counters required')
    require(work['compilerLimit'] == remaining and 0 < work['compilerUnits'] <= work['compilerLimit'], 'Compiler work differs')
    require(work['enumeratedContinuationPlans'] == 0 and work['endpointLpSolves'] == 1, 'Unexpected plan enumeration or endpoint solve count')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Endpoint work totals differ')

    return dict(parentCertificatesChecked=2, endpointCertificatesChecked=1, originalHeroSequences=len(hf['sequences']), originalOpponentSequences=len(of['sequences']), variables=columns, constraints=rows)

def same_endpoint(a, b):
    a, b = copy.deepcopy(a), copy.deepcopy(b)
    a['work'].pop('compilerLimit'); b['work'].pop('compilerLimit')
    return a == b

def check_values(request, report, original=None):
    original, previous, prior_proof = context() if original is None else original
    source_context, legacy, _, tree = original
    saved, parent, _, _, _ = source_context
    request = copy.deepcopy(request)
    nested = request['sourceRequest']['envelope']['parent']
    nested['input'] = prior.physical.source.physical.canonical_input(nested['input']); nested['flop'].sort()
    require(set(request) == {'schemaVersion','sourceRequest','sourceReportHash','previousMinimumStoredSha256','actions'}
            and request['schemaVersion'] == 'pokerlab-physical-root-sequence-batch-request/v1'
            and request['sourceRequest'] == saved['request'] and request['sourceReportHash'] == prior.physical.SOURCE_HASH
            and request['previousMinimumStoredSha256'] == PRIOR_SHA and request['actions'] == ACTIONS, 'Original complete-menu request differs')
    require(report['schemaVersion'] == SCHEMA and report['publicationStatus'] == STATUS and report['trainerAdmission'] is False
            and report['request'] == request and report['minimumScope'] == sequence.SCOPE and report['parent'] == parent
            and report['previousMinimum'] == previous and report['originalEnvelope'] == saved['envelope']['envelope']
            and report['context'] == saved['envelope']['context'], 'Complete original batch lineage differs')
    for actual, expected in [(report['parent'], parent), (report['previousMinimum'], previous),
                             (report['originalEnvelope'], saved['envelope']['envelope']), (report['request'], request)]:
        sequence.literal_equal(actual, expected)
    batch = report['batch']; complete = batch is not None
    require(type(report['completeMinima']) is bool and report['completeMinima'] == complete
            and (report['rejection'] is None) == complete, 'Owned complete batch or typed rejection required')
    comparisons, proofs, reasons = [], [], set()
    if complete:
        require(batch['algorithm'] == ALGORITHM and batch['scope'] == SCOPE and batch['trainerAdmission'] is False
                and batch['snapshotHash'] == parent['solve']['snapshotHash'] and len(batch['minima']) == 3
                and [a['question']['action'] for a in batch['minima']] == ACTIONS, 'Original complete owned action menu differs')
        work = batch['work']; require(all(type(x) is int for x in work.values()), 'Integer batch work required')
        require(work['parentSolveCalls'] == 1 and work['parentLpSolves'] == 2 and work['endpointLpSolves'] == 3
                and work['enumeratedContinuationPlans'] == 0 and work['compilerLimit'] == 8_000_000
                and work['endpointPivotLimit'] == 10_000 and work['endpointArithmeticLimit'] == 4_000_000_000, 'Original cumulative caps differ')
        require(work['parentCompilerUnits'] == parent['solve']['reductionWork']['chargedUnits']
                and work['parentLpArithmeticWork'] == sum(parent['solve'][k]['work']['arithmeticWork'] for k in ['firstLp','secondLp']), 'Shared parent work differs')
        compiler = 3*parent['solve']['treeNodes']; pivots = arithmetic = 0
        require(work['preflightCompilerUnits'] == compiler, 'Original root preflight work differs')
        for action, audit in zip(ACTIONS, batch['minima'], strict=True):
            proofs.append(endpoint(audit, saved, parent, tree, legacy, action, 8_000_000-compiler))
            compiler += audit['work']['compilerUnits']; pivots += audit['work']['endpointPivots']; arithmetic += audit['work']['endpointArithmeticWork']
            require(compiler <= 8_000_000 and pivots <= 10_000 and arithmetic <= 4_000_000_000, 'Cumulative endpoint cap exceeded')
            old_action = next(a for a in report['originalEnvelope']['actions'] if a['action'] == action)
            raise_action = action == 'raise:9.0'
            overlap = audit['minimum']['lowerBound'] <= previous['minimum']['minimum']['upperBound'] and previous['minimum']['minimum']['lowerBound'] <= audit['minimum']['upperBound']
            identical = same_endpoint(audit, previous['minimum'])
            comparisons.append(dict(action=action, originalLowerUtilityBb=old_action['lowerUtility'], fullActionUpperUtilityBb=old_action['upperUtility'],
                                    lowerBoundDifferenceBb=audit['minimum']['lowerBound']-old_action['lowerUtility'],
                                    previousRaiseBracketsOverlap=overlap if raise_action else None,
                                    exactPreviousRaiseExceptRemainingCompilerLimit=identical if raise_action else None))
            if raise_action and not (overlap and identical): reasons.add('ORIGINAL_SEQUENCE_RAISE_CHANGED')
        require((work['compilerUnits'],work['endpointPivots'],work['endpointArithmeticWork']) == (compiler,pivots,arithmetic), 'Cumulative work totals differ')
    else:
        failure = report['rejection']
        require(failure['kind'] in ('LP','PARENT','MINIMUM','STRUCTURE') and isinstance(failure['reason'],str) and isinstance(failure['detail'],str), 'Typed rejection differs')
        reasons.add('BATCH:'+failure['kind']+':'+failure['reason'])
    sequence.literal_equal(report['comparisons'], comparisons)
    checks = [saved['primary'], *saved['references']]
    require(len(report['policies']) == len(checks) == 3, 'Primary and both fresh references required')
    for assessment, check in zip(report['policies'], checks, strict=True):
        sequence.literal_equal(assessment['original'], check)
        failures = set(check['failures']); violations = {}
        if complete:
            if not check['insideDeclaredFace']: failures.add('MINIMUM_REFERENCE_OUTSIDE_FACE')
            else:
                for audit in batch['minima']:
                    action = audit['question']['action']; violation = max(0.,audit['minimum']['lowerBound']-check['actionUtilitiesBb'][action])
                    violations[action] = violation
                    if violation > EPS: failures.add('REFERENCE_BELOW_MINIMUM:'+action)
        sequence.literal_equal(assessment['minimumLowerViolationsBb'], violations)
        require(assessment['allMinimumLowerBoundsSupported'] is (complete and len(violations) == 3 and not failures)
                and assessment['failures'] == sorted(failures), 'Minimum lower support differs')
        reasons.update(str(check['iterations'])+':'+f for f in failures)
    supported = complete and not reasons
    require(report['referenceSupportedMinima'] is supported and report['rejectionReasons'] == sorted(reasons), 'Original complete-menu outcome differs')
    return dict(schemaVersion='pokerlab-physical-root-sequence-batch-independent-check/v1', trainerAdmission=False,
                completeMinima=complete, referenceSupportedMinima=supported, endpointCertificatesChecked=len(proofs),
                distinctBatchParentCertificatesChecked=2 if complete else 0, endpointProofs=proofs, originalPriorChecked=prior_proof,
                policiesChecked=3, actionLowerChecks=9 if complete else 0, fullActionUpperEndpointClaim=False,
                coherentMinimumPolicyClaim=False, freshLpSolves=0, freshCfrRuns=0)

def check():
    require(hashlib.sha256(REPORT.read_bytes()).hexdigest() == REPORT_SHA, 'Frozen physical batch bytes differ')
    receipt = check_values(read(REQUEST), read(REPORT))
    receipt.update(requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),reportStoredSha256=REPORT_SHA,
                   checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), immutableHelperSha256=HELPER_SHA)
    return receipt

if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream: stream.write(data)
    print(data.decode())
