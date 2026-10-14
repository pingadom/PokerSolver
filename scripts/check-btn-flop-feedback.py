"""Independent fixed-case BTN after-check flop qualification; literal physics and original LP checks."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-supplied-flop-action-bounds.py'
spec = importlib.util.spec_from_file_location('btn_bounds_helper', HELPER)
bounds = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = bounds
spec.loader.exec_module(bounds)
physical = bounds.physical
require, close, read, dot, vector = bounds.require, bounds.close, bounds.read, bounds.dot, bounds.vector
CONNECTED = ROOT / 'docs/data/supplied-four-button-hand-connected-flop-request.json'
LP_ALGORITHM = 'OWNED_EXACT_POWER_TWO_L1_RHS_RESOLVABLE_COST_ORIGINAL_CERTIFICATE/v1'
INTERVAL_ALGORITHM = 'OWNED_PHYSICAL_FLOP_CONDITIONAL_REACH_EXACT_POWER_TWO_ENDPOINTS/v4'
REPORT_SHA = '4963cb315ec2b19df901ed41e74f1469d9b0623cad31cd78a337e35c6f249baa'
REPORT_HASH = 'c95a6ce29095416967306a49496a7636d30cd9f9a4b648fca832a9db5d8545e7'
EPS = 1e-8
KEY = '3:supplied-flop-one-bet:9s Js:|BB:call:2s 8s Ts:check'


def certificate(matrix, rhs, cost, witness):
    require(0 < len(cost) <= 512 and 0 < len(rhs) <= 512
            and len(matrix) == len(rhs) and all(len(row) == len(cost) for row in matrix)
            and all(math.isfinite(v) and abs(v) <= 1_000_000 for row in matrix for v in row)
            and all(math.isfinite(v) and abs(v) <= 1_000_000 for v in rhs + cost),
            'Original LP dimensions or coefficient cap differs')
    x, y, cert = witness['point'], witness['dual'], witness['certificate']
    require(cert['algorithm'] == LP_ALGORITHM and cert['variables'] == len(cost) == len(x)
            and cert['constraints'] == len(rhs) == len(y), 'Original LP identity differs')
    require(all(math.isfinite(v) for v in x + y), 'Nonfinite LP witness')
    primal = max([0] + [-v for v in x] + [dot(r, x) - b for r, b in zip(matrix, rhs, strict=True)])
    dual = max([0] + [-v for v in y] + [cost[j] - dot([r[j] for r in matrix], y) for j in range(len(cost))])
    pv, dv = dot(cost, x), dot(rhs, y)
    require(max(primal, dual, abs(pv - dv)) <= EPS, 'Original LP certificate fails')
    for k, v in dict(primalValue=pv, dualValue=dv, maximumPrimalViolation=primal,
                     maximumDualViolation=dual, absoluteDualityGap=abs(pv - dv)).items():
        close(cert[k], v, 'Recorded original LP certificate differs')
    work = cert['work']
    require(0 <= work['arithmeticWork'] <= 4_000_000_000 and
            0 <= sum(work[k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) <= 10000,
            'Original LP work cap differs')


def maximum_utility(node):
    if node.actor == -2:
        return abs(node.utilities[3])
    return max(maximum_utility(child) for _, child in node.edges)


def face(audit, baseline):
    hero, opponent, payoff = audit['heroProjection'], audit['opponentProjection'], audit['orientedPayoff']
    require(hero == baseline['firstProjection'] and opponent == baseline['secondProjection'],
            'Original BTN projections changed')
    require(payoff == baseline['projectedPayoff'], 'Original BTN payoff orientation changed')
    w, z, d = len(opponent['independentSequences']), len(hero['independentSequences']), len(hero['rhs'])
    matrix = [payoff['matrix'][i] + [-hero['inequalities'][j][i] for j in range(d)] for i in range(z)]
    matrix += [row + [0] * d for row in opponent['inequalities']]
    matrix += [payoff['secondCost'] + hero['rhs']]
    close(audit['globalHeroUpperValue'], baseline['upperValue'], 'Global BTN security value differs')
    rhs = [-x for x in payoff['firstCost']] + opponent['rhs'] + [
        audit['globalHeroUpperValue'] + audit['securitySlack'] - payoff['constant']]
    return matrix, rhs, w


def local_check(actual, policy, context):
    prior, selected = context['prior'], context['selected']
    weights = [(i, w['conditionalProbability'] * physical.P * policy[
        f'5:supplied-connected-single-flop:supplied-conditional-preflop:{w["dealtCombos"][5]}:']['call'])
        for i, w in enumerate(prior)]
    total = math.fsum(p for _, p in weights)
    close(actual['candidateRootFlopReach'], total, 'Reached-flop raw mass differs', mass=True)
    close(actual['physicalSupportFlopMass'], physical.P, 'Physical support mass differs', mass=True)
    close(actual['candidateBoundaryReachGivenPhysicalFlop'], total / physical.P, 'Reached-flop units differ', mass=True)
    require(actual['scope'] == 'CANDIDATE_POLICY_REACHED_FLOP_POSTERIOR_NOT_FULL_SECURITY_FACE/v1'
            and actual['publicHistory'] == '|BB:call' and actual['conditionalReachFloor'] == .0001
            and actual['conditioning'] == 'ABOVE_CANDIDATE_REACH_FLOOR', 'Reached-flop scope differs')
    require(len(actual['rawWorldMasses']) == len(weights) == len(actual['reachedWorlds']), 'Reached-world coverage differs')
    for (i, p), raw, reached in zip(weights, actual['rawWorldMasses'], actual['reachedWorlds'], strict=True):
        require(raw['originalDealIndex'] == reached['originalDealIndex'] == i, 'Reached-world identity differs')
        close(raw['jointRootFlopReach'], p, 'Raw world reach differs', mass=True)
        close(reached['jointRootFlopReach'], p, 'Reached world mass differs', mass=True)
        close(reached['conditionalWeight'], p / total, 'Reached posterior differs', mass=True)
    root = physical.Node(-1, edges=tuple((p / total, selected[i, '|BB:call']) for i, p in weights))
    physical.compare_quality(actual['conditionalBestResponse'], physical.quality(root, policy))


def policy_check(actual, policy, expected_hash, context, actions, roots):
    require(set(actual) == {'iterations', 'solutionHash', 'fullParentQuality', 'reachedFlopQuality',
            'actualQuestionRootReach', 'actualQuestionReachGivenPhysicalFlop',
            'counterfactualQuestionReachGivenPhysicalFlop', 'globalHeroBestResponseBb', 'globalHeroUpperBb',
            'inclusionMarginBb', 'insideDeclaredFace', 'boundsChecked', 'maximumBoundViolationBb',
            'actionUtilitiesBb', 'actionLossBb', 'decisionRegretBb', 'failures'}, 'Policy check fields differ')
    require(actual['iterations'] == policy['iterations'] and actual['solutionHash'] == expected_hash, 'Policy identity differs')
    strategy = policy['strategy']
    physical.compare_quality(actual['fullParentQuality'], physical.quality(context['root'], strategy))
    local_check(actual['reachedFlopQuality'], strategy, context)
    actual_mass, cf_weights = 0, []
    for i, p, initial, reply in roots:
        preflop = strategy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call']
        checked = strategy[initial.key]['check']
        actual_mass += p * physical.P * preflop * checked
        # BTN has no modeled past action. Both BB call and BB check remain.
        cf_weights.append(p * preflop * checked)
    cf = math.fsum(cf_weights)
    close(actual['actualQuestionRootReach'], actual_mass, 'Actual full-path root reach differs', mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'], actual_mass / physical.P, 'Actual full-path conditional reach differs', mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'], cf, 'Hero past cancellation or opponent call/check reach differs', mass=True)
    values = {a['action']: physical.best_response(physical.Node(-1, edges=tuple(
        (p / cf, dict(root[3].edges)[a['action']]) for root, p in zip(roots, cf_weights, strict=True))), strategy, 3)
        for a in actions}
    losses = {a: max(values.values()) - v for a, v in values.items()}
    require(set(actual['actionUtilitiesBb']) == set(values) == set(actual['actionLossBb']), 'Policy action inventory differs')
    for action in values:
        close(actual['actionUtilitiesBb'][action], values[action], 'BTN forced action value differs')
        close(actual['actionLossBb'][action], losses[action], 'BTN action loss differs')
    br = physical.best_response(context['root'], strategy, 3)
    upper = actions[0]['diagnostic']['globalHeroUpperValue']
    margin = upper + EPS - br
    close(actual['globalHeroBestResponseBb'], br, 'Full-parent hero response differs')
    close(actual['globalHeroUpperBb'], upper, 'Full-parent security upper differs')
    close(actual['inclusionMarginBb'], margin, 'Reference inclusion differs')
    included = margin >= -EPS
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is included, 'Reference face status differs')
    failures = []
    if not included:
        failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
        require(actual['maximumBoundViolationBb'] is None, 'Outside-face bounds must remain unchecked')
    if actual['fullParentQuality']['nashConvBb'] > .001:
        failures.append('FULL_PARENT_GAP')
    if actual['reachedFlopQuality']['conditionalBestResponse']['nashConvBb'] > .001:
        failures.append('REACHED_FLOP_GAP')
    if actual_mass / physical.P < .0001:
        failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf < .0001:
        failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    regret = math.fsum(strategy[KEY][a] * loss for a, loss in losses.items())
    close(actual['decisionRegretBb'], regret, 'BTN selected decision regret differs')
    if regret > .01:
        failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation = max([0] + [v for a in actions for v in (
            a['lowerUtilityBb'] - values[a['action']], values[a['action']] - a['upperUtilityBb'],
            a['lowerLossBb'] - losses[a['action']], losses[a['action']] - a['upperLossBb'])])
        close(actual['maximumBoundViolationBb'], violation, 'BTN bound comparison differs')
        if violation > EPS:
            failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    require(actual['failures'] == failures, 'BTN policy failures differ')
    return failures


def check(request_path, report_path):
    request, request_bytes, _ = read(request_path)
    report, raw, expanded = read(report_path)
    parent, parent_bytes, _ = read(bounds.PARENT)
    require(hashlib.sha256(parent_bytes).hexdigest() == bounds.PARENT_SHA and report['parent'] == parent,
            'Original physical parent differs')
    _, context = physical.check(CONNECTED, bounds.PARENT, include_context=True)
    declared = dict(request)
    declared['connected'] = dict(request['connected'], input=physical.canonical_input(request['connected']['input']))
    require(set(request) == {'schemaVersion', 'connected', 'intervalAlgorithm', 'hero', 'heroCards',
                            'publicPreflopHistory', 'flopActions', 'maximumLossBb', 'referenceBudget'}
            and request['schemaVersion'] == 'pokerlab-btn-after-check-flop-feedback-request/v1'
            and declared['connected'] == parent['request'] and request['intervalAlgorithm'] == INTERVAL_ALGORITHM
            and request['hero'] == 3 and request['heroCards'] == '9s Js'
            and request['publicPreflopHistory'] == '|BB:call' and request['flopActions'] == ['check']
            and request['maximumLossBb'] == .01
            and request['referenceBudget'] == dict(algorithm='FRESH_INDEPENDENT_CFR_PLUS_80K_160K/v1',
                iterations=[80000, 160000], totalIterations=240000), 'Unsupported fixed BTN request')
    require(set(report) == {'schemaVersion', 'publicationStatus', 'trainerAdmission', 'request', 'evScope', 'reachUnits',
            'informationSet', 'parent', 'context', 'actions', 'moves', 'primary', 'references',
            'qualifiedForOfflineDrill', 'rejectionReasons'}
            and report['schemaVersion'] == 'pokerlab-btn-after-check-flop-feedback-report/v1'
            and report['publicationStatus'] == 'BTN_AFTER_CHECK_PHYSICAL_FLOP_INTERVAL_FEEDBACK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request'] == declared and report['informationSet'] == KEY
            and report['evScope'] == 'ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
            and report['reachUnits'] == 'COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1',
            'BTN report identity differs')
    handoff = parent['handoffs'][0]
    seats = ['UTG', 'HJ', 'CO', 'BTN', 'SB', 'BB']
    players = [dict(seat=s, status='FOLDED' if handoff['folded'][i] else 'ACTING' if i == 3 else 'ACTIVE',
                    committedBb=handoff['committedBb'][i],
                    remainingStackBb=handoff['remainingStackBb'][i]) for i, s in enumerate(seats)]
    expected_context = dict(actingSeat='BTN', board=['2s', '8s', 'Ts'], publicPreflopHistory='|BB:call',
            preflopHistory=handoff['completePreflopHistory'], flopHistory=[dict(seat='BB', action='check', amountBb=0)], potBb=6.5, toCallBb=0, betSizeBb=3.25,
            players=players, legalActions=['check', 'bet'])
    require(report['context'] == expected_context, 'Owned BTN table context differs')
    roots = []
    for i, world in enumerate(context['prior']):
        if world['dealtCombos'][3] != '9s Js':
            continue
        initial = context['selected'][i, '|BB:call']
        reply = dict(initial.edges)['check']
        require(reply.key == KEY, 'Literal BTN information set differs')
        roots.append((i, world['conditionalProbability'], initial, reply))
    baseline = parent['solve']
    bounds.payoff(context['root'], baseline)
    actions = report['actions']
    require([a['action'] for a in actions] == ['check', 'bet'], 'BTN action menu differs')
    certificates = 0
    for action in actions:
        audit = action['diagnostic']
        require(audit['algorithm'] == INTERVAL_ALGORITHM and audit['trainerAdmission'] is False
                and audit['baseline'] == baseline and audit['securitySlack'] == EPS and audit['requiredMinimumReach'] == .0001
                and audit['question'] == dict(hero=3, informationSet=KEY, action=action['action'])
                and audit['scope'] == 'ACTION_CONDITIONED_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1'
                and audit['fixedHeroPast'] == [], 'BTN interval scope differs')
        m, b, w = face(audit, baseline)
        flow = audit['minimumReachWitness']['opponentFlow']
        indices = {(s['key'], s['action']): s['index'] for s in flow['sequences']}
        opponent = audit['opponentProjection']
        coefficients = [0.0] * len(flow['sequences'])
        sequences = []
        numerator = [0.0] * len(coefficients)
        for _, p, initial, reply in roots:
            sequence = indices[initial.key, 'check']
            sequences.append(sequence)
            coefficients[sequence] += p
            child = dict(reply.edges)[action['action']]
            if action['action'] == 'check':
                require(child.actor == -2, 'BTN check must close the one-bet round')
                numerator[sequence] += p * child.utilities[3]
            else:
                require(child.actor == 5 and [a for a, _ in child.edges] == ['fold', 'call'],
                        'BB response to BTN bet differs')
                for response, terminal in child.edges:
                    require(terminal.actor == -2, 'One-bet continuation changed')
                    numerator[indices[child.key, response]] += p * terminal.utilities[3]
        dc = dot(coefficients, opponent['offset'])
        d = [dot(coefficients, [row[j] for row in opponent['transform']]) for j in range(w)]
        close(audit['denominator']['constant'], dc, 'Opponent path denominator constant differs')
        vector(audit['denominator']['cost'], d)
        require(len(audit['numeratorPlans']) == 1 and audit['numeratorPlans'][0]['continuation'] == {}, 'BTN continuation plan differs')
        plan = audit['numeratorPlans'][0]
        close(plan['constant'], dot(numerator, opponent['offset']), 'BTN numerator constant differs')
        vector(plan['cost'], [dot(numerator, [row[j] for row in opponent['transform']]) for j in range(w)])
        n, shift = len(m[0]), action['upperObjectiveShift']
        close(shift, -maximum_utility(context['root']) - 1, 'Original objective shift differs')
        scaled = [row + [-rhs] for row, rhs in zip(m, b, strict=True)]
        scaled += [d + [0] * (n - w) + [dc], [-v for v in d] + [0] * (n - w) + [-dc], [0] * n + [1]]
        sb = [0] * len(b) + [1, -1, 10000]
        for sign, name in [(-1, 'minimumReachWitness'), (1, 'maximumReachWitness')]:
            certificate(m, b, [sign * v for v in d] + [0] * (n - w), audit[name]); certificates += 1
        require(len(audit['upperWitnesses']) == 1, 'BTN endpoint coverage differs')
        upper_cost = [c + shift * v for c, v in zip(plan['cost'], d, strict=True)] + [0] * (n - w) + [plan['constant'] + shift * dc]
        certificate(scaled, sb, upper_cost, audit['upperWitnesses'][0]); certificates += 1
        lower = [row + [0] for row in scaled] + [plan['cost'] + [0] * (n - w) + [plan['constant'], 1]]
        certificate(lower, sb + [-shift], [0] * (n + 1) + [1], audit['lowerWitness']); certificates += 1
        close(action['lowerUtilityBb'], -shift - audit['lowerWitness']['certificate']['dualValue'] - EPS, 'BTN padded lower differs')
        close(action['upperUtilityBb'], audit['upperWitnesses'][0]['certificate']['dualValue'] - shift + EPS, 'BTN padded upper differs')
        close(audit['lowerUtility'], -shift - audit['lowerWitness']['certificate']['primalValue'], 'BTN raw lower differs')
        close(audit['upperUtility'], audit['upperWitnesses'][0]['certificate']['primalValue'] - shift, 'BTN raw upper differs')
        require(audit['upperPlan'] == 0, 'BTN upper plan differs')
        close(audit['certifiedReachLowerBound'], dc - audit['minimumReachWitness']['certificate']['dualValue'] - EPS, 'BTN lower reach differs')
        close(audit['certifiedReachUpperBound'], dc + audit['maximumReachWitness']['certificate']['dualValue'] + EPS, 'BTN upper reach differs')
        require(audit['certifiedReachLowerBound'] >= .0001, 'BTN full-face reach insufficient')
        for field in ['Lower', 'Upper']:
            close(action['rawCertifiedReach' + field + 'Bound'], physical.P * audit['certifiedReach' + field + 'Bound'], 'BTN raw reach units differ', mass=True)
        witnesses = [audit['minimumReachWitness'], audit['maximumReachWitness'], audit['lowerWitness'], *audit['upperWitnesses']]
        for witness in witnesses:
            scaled_witness = len(witness['point']) > n
            scaling = witness['point'][n] if scaled_witness else 1
            require(math.isfinite(scaling) and 0 < scaling <= 10000 + EPS, 'BTN fractional scaling invalid')
            close(witness['scaling'], scaling, 'BTN fractional scaling differs')
            vector(witness['opponentVariables'], [v / scaling for v in witness['point'][:w]])
            f = witness['opponentFlow']
            require(f['actor'] == 5 and f['sequences'] == baseline['secondFlow']['sequences'] and f['conservation'] == baseline['secondFlow']['conservation'], 'BTN opponent topology differs')
            realization = f['realization']
            vector(realization, [c + dot(row, witness['opponentVariables']) for c, row in zip(opponent['offset'], opponent['transform'], strict=True)])
            close(realization[0], 1, 'Original opponent root differs')
            require(min(realization) >= -EPS, 'Negative original realization')
            policy = copy.deepcopy(parent['candidate']['strategy'])
            for row in f['conservation']:
                ids, mass = row['childSequences'], realization[row['parentSequence']]
                close(sum(realization[i] for i in ids), mass, 'Original opponent conservation differs')
                weights = [min(1, max(0, realization[i] / mass)) if mass > 1e-12 else 1 / len(ids) for i in ids]
                total = sum(weights)
                policy[row['key']] = {f['sequences'][i]['action']: p / total for i, p in zip(ids, weights, strict=True)}
            br = physical.best_response(context['root'], policy, 3)
            close(witness['globalHeroBestResponse'], br, 'Original full-parent witness response differs')
            require(br <= audit['globalHeroUpperValue'] + EPS + EPS, 'BTN witness violates full-parent security')
            weights = [p * policy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call'] * policy[initial.key]['check']
                       for i, p, initial, _ in roots]
            reach = math.fsum(weights)
            close(witness['questionReach'], reach, 'BTN behavioral reach differs')
            close(dc + dot(d, witness['opponentVariables']), reach, 'Original projected path reach differs')
            if scaled_witness:
                close(reach * scaling, 1, 'BTN fractional normalization differs')
            forced = physical.Node(-1, edges=tuple((p / reach, dict(root[3].edges)[action['action']]) for root, p in zip(roots, weights, strict=True)))
            close(witness['conditionalHeroBestResponse'], physical.best_response(forced, policy, 3), 'BTN witness conditional response differs')
            require(len(witness['posterior']) == len(roots), 'BTN posterior coverage differs')
            linear_total = math.fsum(root[1] * realization[sequence] for root, sequence in zip(roots, sequences, strict=True))
            tv = math.fsum(.5 * abs(weight / reach - root[1] * realization[sequence] / linear_total)
                          for root, sequence, weight in zip(roots, sequences, weights, strict=True))
            close(witness['posteriorTotalVariation'], tv, 'BTN posterior total variation differs')
            require(tv <= EPS, 'BTN original and behavioral posteriors disagree')
            for i, (row, root, sequence, weight) in enumerate(zip(witness['posterior'], roots, sequences, weights, strict=True)):
                require(row['rootIndex'] == i and row['opponentSequence'] == sequence, 'Opponent past sequence differs')
                for field, expected in [('chanceWeight', root[1]), ('originalRealizationWeight', root[1] * realization[sequence]),
                                        ('behavioralWeight', weight), ('probability', weight / reach)]:
                    close(row[field], expected, 'BTN witness posterior or cancelled hero path differs', mass=True)
        require(audit['work']['intervalLpSolves'] == len(witnesses)
                and audit['work']['intervalLpArithmeticWork'] == sum(x['certificate']['work']['arithmeticWork'] for x in witnesses)
                and audit['work']['intervalLpArithmeticWork'] <= 4_000_000_000
                and audit['work']['intervalLpPivots'] == sum(sum(x['certificate']['work'][k]
                    for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) for x in witnesses)
                and audit['work']['intervalLpPivots'] <= 10000
                and 0 <= audit['work']['compilerUnits'] <= audit['work']['compilerLimit'] <= 8_000_000,
                'Cumulative BTN LP work differs')
    best_lower, best_upper = max(a['lowerUtilityBb'] for a in actions), max(a['upperUtilityBb'] for a in actions)
    moves = []
    for a in actions:
        close(a['lowerLossBb'], max(0, best_lower - a['upperUtilityBb']), 'BTN loss lower differs')
        close(a['upperLossBb'], max(0, best_upper - a['lowerUtilityBb']), 'BTN loss upper differs')
        classification = 'WITHIN_LIMIT' if a['upperLossBb'] <= .01 else 'OUTSIDE_LIMIT' if a['lowerLossBb'] > .01 else 'STRATEGY_DEPENDENT'
        moves.append(dict(action=a['action'], lowerEvBb=a['lowerUtilityBb'], upperEvBb=a['upperUtilityBb'],
                          lowerLossBb=a['lowerLossBb'], upperLossBb=a['upperLossBb'], classification=classification))
    require(report['moves'] == moves, 'BTN interval classification differs')
    failures = ['PRIMARY:' + f for f in policy_check(report['primary'], parent['candidate'], parent['solutionHash'], context, actions, roots)]
    require([r['policy']['iterations'] for r in report['references']] == [80000, 160000], 'Fresh BTN reference coverage differs')
    hashes = ['1514e799aba00367f0e4e1ec558a3c25619f98cc182ad37258c5ff4e03a7ef90', '7570295a7c649bcb3fea04a946deb1b9841323180349815d7de8a8be6893ac31']
    for reference, expected_hash in zip(report['references'], hashes, strict=True):
        policy = reference['policy']
        require(set(policy['strategy']) == set(parent['candidate']['strategy']), 'Fresh policy coverage differs')
        for key, row in policy['strategy'].items():
            require(set(row) == set(parent['candidate']['strategy'][key]) and all(math.isfinite(p) and 0 <= p <= 1 for p in row.values()), 'Fresh BTN policy invalid')
            close(sum(row.values()), 1, 'Fresh BTN policy not normalized')
        failures += [str(policy['iterations']) + ':' + f for f in policy_check(reference['check'], policy, expected_hash, context, actions, roots)]
    classes = {m['classification'] for m in moves}
    if 'WITHIN_LIMIT' not in classes:
        failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if 'OUTSIDE_LIMIT' not in classes:
        failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    require(report['rejectionReasons'] == sorted(set(failures)) and report['qualifiedForOfflineDrill'] is (not failures), 'BTN qualification differs')
    require(hashlib.sha256(raw).hexdigest() == REPORT_SHA, 'Fixed Java report byte binding differs')
    return dict(schemaVersion='pokerlab-independent-btn-after-check-flop-feedback/v1', trainerAdmission=False,
                freshLpSolves=0, originalEndpointCertificatesChecked=certificates, actionsChecked=2,
                policiesChecked=3, freshCfrRuns=0, declaredFreshReferenceIterations=240000,
                fullParentAndFullPathReachChecked=True, literalPublicContextChecked=True,
                reportHash=REPORT_HASH, checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                boundsHelperSourceSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest(),
                physicalHelperSourceSha256=hashlib.sha256(bounds.HELPER.read_bytes()).hexdigest(),
                requestStoredBytesSha256=hashlib.sha256(request_bytes).hexdigest(), reportStoredBytesSha256=hashlib.sha256(raw).hexdigest(),
                reportExpandedBytesSha256=hashlib.sha256(expanded).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request', type=Path)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.output:
        require(args.output.resolve() not in {args.request.resolve(), args.report.resolve()}, 'Receipt must be distinct')
    receipt = check(args.request, args.report)
    text = json.dumps(receipt, indent=2) + '\n'
    if args.output:
        with args.output.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(text)
    print(text, end='')


if __name__ == '__main__':
    main()
