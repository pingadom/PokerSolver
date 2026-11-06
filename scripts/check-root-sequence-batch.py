"""Independent bounded root-batch reconstruction and exact LP certificate checking.

The shared parent and forced-action sequence domains are checked on the literal
original games. This verifier performs no LP solving or CFR training.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-root-sequence-minimax.py'
HELPER_SHA = '5a242aa3b791e04807e5c3f0426633db2c03c5001554f24461f2198db97ce765'
if hashlib.sha256(HELPER.read_bytes()).hexdigest() != HELPER_SHA:
    raise ValueError('Immutable original sequence checker changed')
spec = importlib.util.spec_from_file_location('batch_original_sequence_checker', HELPER)
seq = importlib.util.module_from_spec(spec); spec.loader.exec_module(seq)
old = seq.old
require, close, near, dot = seq.require, seq.close, seq.near, seq.dot
node, terminal, decision = seq.node, seq.terminal, seq.decision
behavior, value, best_response = seq.behavior, seq.value, seq.best_response
ALGORITHM, SCOPE = seq.ALGORITHM, seq.SCOPE
BATCH_ALGORITHM = 'ROOT_SEQUENCE_MINIMUM_SHARED_OWNED_PARENT_BATCH/v1'
BATCH_SCOPE = 'ROOT_MINIMA_ONLY_NOT_FULL_ACTION_INTERVALS_OR_TRAINER_ADMISSION/v1'
FIXTURE = ROOT / 'solver/src/test/resources/root-sequence-batch-analytic-controls.json.gz'
FIXTURE_SHA = 'd91cefea461e1c235784639fc20bf741e956124f6a5fa49d7f83d37aae07c802'


def literal_mixed():
    roots = []
    for r in range(4):
        hero = 3 if r % 2 == 0 else 5; other = 5 if hero == 3 else 3; types = []
        for t in range(4):
            sign = 1. if t % 2 == 0 else -1.
            left = decision(other, 'reply-'+str(r), ['x', 'y'], terminal(hero, sign), terminal(hero, -sign))
            right = decision(other, 'reply-'+str(r), ['x', 'y'], terminal(hero, -sign), terminal(hero, sign))
            types.append(decision(hero, 'private-'+str(r)+'-'+str(t), ['left', 'right'], left, right))
        roots.append(decision(hero, 'root-'+str(r), ['safe', 'risk'], terminal(hero, 10.),
                              node(-1, probabilities=[.25]*4, children=types)))
    return dict(playerCount=6, initialState=node(-1, probabilities=[.125,.25,.125,.5], children=roots))


def canonical(game):
    # These two literal games use only exact dyadic chances and integer utilities.
    # Normalize declared double arrays, then match Jackson's sorted property order.
    def typed(n):
        return dict(actor=n['actor'], key=n['key'], actions=n['actions'],
                    probabilities=[float(v) for v in n['probabilities']],
                    children=[typed(c) for c in n['children']], utilities=[float(v) for v in n['utilities']])
    data = dict(playerCount=game['playerCount'],initialState=typed(game['initialState'])) if 'playerCount' in game else typed(game)
    return hashlib.sha256(json.dumps(data,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()


def count_nodes(n):
    return 1 + sum(count_nodes(child) for child in n['children'])


def certificate_work(c):
    w = c['work']
    counters = {'phaseOnePivots','cleanupPivots','phaseTwoPivots','basisFactorizations','arithmeticWork'}
    require(set(w)==counters|{'blandFallback'} and type(w['blandFallback']) is bool,'Certificate work inventory differs')
    require(all(type(w[k]) is int and w[k]>=0 for k in counters),'Integer certificate work required')


def declared():
    qs = [dict(hero=3 if r % 2 == 0 else 5, informationSet=str(3 if r % 2 == 0 else 5)+':root-'+str(r), action=action)
          for r in range(4) for action in ['risk','safe']]
    return [('mixed-four-roots',literal_mixed(),qs,[0.,10.]*4,False),
            ('private32',seq.literal_case('private32-5'),[dict(hero=5,informationSet='5:root',action=a) for a in ['risk','safe']],[0.,10.],True)]


def check_minimum(game, a, expected_minimum, question, compiler_limit, large):
    h = question['hero']; o = 5 if h == 3 else 3; tree = game['initialState']
    require(a['algorithm'] == ALGORITHM and a['scope'] == SCOPE and a['trainerAdmission'] is False, 'Sequence minimum scope differs')
    require(a['question'] == question, 'Root question differs')
    require(a['requiredMinimumReach'] == .0001 and a['securitySlack'] == 1e-8, 'Original gates differ')
    base = a['baseline']; require(base['firstActor'] == 3 and base['secondActor'] == 5 and base['constantSum'] == 4., 'Parent orientation differs')
    require(base['algorithm']=='BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_REFINED_DIRECTIONS_ORIGINAL_CERTIFICATES/v2','Owned parent algorithm differs')
    reduction = base['reductionWork']
    require(set(reduction)=={'chargedUnits','limit'} and all(type(v) is int for v in reduction.values()) and reduction['limit']==8_000_000 and 0<reduction['chargedUnits']<=reduction['limit'],'Parent compiler limits differ')
    certificate_work(base['firstLp']); certificate_work(base['secondLp'])
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
        require(support['stateHash'] == canonical(n), 'Original root state hash differs')
        close(support['originalChanceWeight'], probability); close(support['posteriorProbability'], probability/mass)
        forest.append((probability/mass, n['children'][n['actions'].index(question['action'])]))
    conditional = node(-1, probabilities=[p for p, _ in forest], children=[n for _, n in forest])
    ids = {p: {(s['key'], s['action']): s['index'] for s in flow['sequences'][1:]} for p, flow in [(h, hf), (o, of)]}
    forced = ids[h][question['informationSet'], question['action']]; grid = [[0.]*len(of['sequences']) for _ in hf['sequences']]
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
    witness = a['witness']; certificate_work(witness['certificate']); old.certificate(matrix, rhs, cost, witness, old.LP)
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
    if large and question['action'] == 'risk':
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
    require(a['minimum']['lowerBound']-old.EPS <= expected_minimum <= a['minimum']['upperBound']+old.EPS, 'Closed-form minimum not enclosed')
    work = a['work']; require(all(type(v) is int for v in work.values()), 'Integer work counters required')
    require(work['compilerLimit'] == compiler_limit and 0 < work['compilerUnits'] <= work['compilerLimit'], 'Compiler work differs')
    require(work['enumeratedContinuationPlans'] == 0 and work['endpointLpSolves'] == 1, 'Unexpected plan enumeration or endpoint solve count')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Endpoint work totals differ')


def check_values(controls):
    require(isinstance(controls,list) and len(controls)==2,'Two declared batches required')
    distinct_parent_certificates = 0; endpoints = 0
    for c, (name, game, qs, expected, large) in zip(controls,declared(),strict=True):
        require(isinstance(c,dict) and set(c)=={'name','game','expected','audit'} and c['name']==name,'Batch identity differs')
        seq.literal_equal(c['game'],game); seq.literal_equal(c['expected'],expected)
        b = c['audit']; require(set(b)=={'algorithm','scope','trainerAdmission','snapshotHash','minima','work'},'Batch audit fields differ')
        require(b['algorithm']==BATCH_ALGORITHM and b['scope']==BATCH_SCOPE and b['trainerAdmission'] is False,'Batch scope differs')
        require(b['snapshotHash']==canonical(game),'Literal parent snapshot identity differs')
        require(isinstance(b['minima'],list) and len(b['minima'])==len(qs)<=8,'Complete bounded root menu required')
        w = b['work']
        fields = {'parentSolveCalls','parentLpSolves','parentCompilerUnits','parentLpArithmeticWork','preflightCompilerUnits','compilerUnits','compilerLimit','endpointLpSolves','endpointPivots','endpointPivotLimit','endpointArithmeticWork','endpointArithmeticLimit','enumeratedContinuationPlans'}
        require(set(w)==fields and all(type(v) is int and v>=0 for v in w.values()),'Integer batch work inventory differs')
        require(w['parentSolveCalls']==1 and w['parentLpSolves']==2 and w['enumeratedContinuationPlans']==0,'Shared parent accounting differs')
        require(w['compilerLimit']==8_000_000 and w['endpointPivotLimit']==10_000 and w['endpointArithmeticLimit']==4_000_000_000,'Original cumulative limits differ')
        require(w['endpointLpSolves']==len(qs),'Incomplete endpoint accounting')
        used = count_nodes(game['initialState'])*len(qs); require(w['preflightCompilerUnits']==used,'Preflight work differs')
        arithmetic = 0; pivots = 0; first = b['minima'][0]; base = first['baseline']
        require(base['snapshotHash']==b['snapshotHash'] and base['treeNodes']==count_nodes(game['initialState']),'Parent source identity differs')
        require(w['parentCompilerUnits']==base['reductionWork']['chargedUnits']<=8_000_000,'Parent compiler accounting differs')
        require(w['parentLpArithmeticWork']==sum(base[k]['work']['arithmeticWork'] for k in ['firstLp','secondLp']),'Parent arithmetic accounting differs')
        for a, q, e in zip(b['minima'],qs,expected,strict=True):
            require(a['baseline']==base and a['firstParentWitness']==first['firstParentWitness'] and a['secondParentWitness']==first['secondParentWitness'],'Borrowed parent evidence differs within batch')
            check_minimum(game,a,e,q,8_000_000-used,large)
            used += a['work']['compilerUnits']; arithmetic += a['work']['endpointArithmeticWork']; pivots += a['work']['endpointPivots']
            require(used<=8_000_000 and arithmetic<=4_000_000_000 and pivots<=10_000,'Cumulative endpoint budget exceeded')
        require(w['compilerUnits']==used and w['endpointArithmeticWork']==arithmetic and w['endpointPivots']==pivots,'Aggregate endpoint charges differ')
        distinct_parent_certificates += 2; endpoints += len(qs)
    return dict(status='PASS_ANALYTIC_ROOT_SEQUENCE_BATCH_NOT_TRAINER_ADMISSION',batches=2,distinctParentCertificates=distinct_parent_certificates,
                endpointCertificates=endpoints,largeControlCompletePlanCount=2**32,productionContinuationPlansEnumerated=0,
                freshLpSolves=0,freshCfrRuns=0,physicalPokerGames=0,trainerAdmission=False)


def check():
    controls, raw = old.read(FIXTURE)
    require(hashlib.sha256(raw).hexdigest()==FIXTURE_SHA,'Frozen batch fixture byte pin differs')
    result = check_values(controls)
    result.update(fixtureSha256=FIXTURE_SHA,checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  immutableHelpersSha256={HELPER.name:HELPER_SHA,**{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [seq.HELPER,seq.EXACT]}})
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
