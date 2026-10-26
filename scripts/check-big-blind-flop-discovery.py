"""Independent BB physical/context/complete-face endpoint checks, without LP or CFR optimization.

The old six numerical failures remain frozen. New v10 intervals are research only: none of
these six contexts has both a robust acceptable and a robust unacceptable move.
"""
import argparse
import copy
import hashlib
import importlib.util
from itertools import product
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value


source = module('bb_unpaired_physics', 'check-unpaired-flop-discovery.py')
exact = module('bb_exact_lp', 'check-resolvable-refined-lp.py')
physical, bounds = source.physical, source.bounds
require, close, read, dot, vector = source.require, source.close, source.read, source.dot, source.vector
EPS = 1e-8
ALGORITHM = 'OWNED_PHYSICAL_FLOP_ALL_PHASE_REFINED_BASIS_CERTIFIED_REACH_ENDPOINTS/v10'
LP = 'OWNED_ASCENDING_L1_RESOLVABLE_COST_ALL_PHASES_COMPENSATED_DIRECTION_STRICT_RATIO_ORIGINAL_CERTIFICATE/v7'
DATA = ROOT / 'docs/data'
REQUEST = DATA / 'supplied-unpaired-bb-refined-flop-coverage-request.json'
REPORT = DATA / 'supplied-unpaired-bb-refined-flop-coverage-report.json.gz'
CAPTURE = ROOT / 'solver/src/test/resources/lp-big-blind-phase-one.json.gz'
BUDGET = DATA / 'big-blind-refined-flop-discovery-generation-budget.json'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def certificate(matrix, rhs, cost, witness):
    cert = witness['certificate']
    require(cert['algorithm'] == LP, 'Explicit LP mode differs')
    values = exact.exact(matrix, rhs, cost, witness['point'], witness['dual'])
    require(cert['variables'] == len(cost) and cert['constraints'] == len(rhs), 'LP dimensions differ')
    for key, value in values.items():
        close(cert[key], float(value), 'Exact original LP certificate field differs')
    return values


def roots_for(request, parent, tree):
    hand, path = request['heroCards'], request['flopActions']
    roots = []
    flow = parent['solve']['firstFlow']
    indices = {(s['key'], s['action']): s['index'] for s in flow['sequences']}
    key = f'5:supplied-flop-one-bet:{hand}:|BB:call:{source.BOARD}:' + '|'.join(path)
    for (_, preflop), world in zip(tree.edges, parent['originalJointPrior'], strict=True):
        if world['dealtCombos'][5] != hand:
            continue
        initial = dict(preflop.edges)['call'].edges[0][1]
        question, sequence = initial, 0
        if path:
            reply = dict(initial.edges)['check']
            question = dict(reply.edges)['bet']
            sequence = indices[reply.key, 'bet']
        require(question.actor == 5 and question.key == key, 'Literal BB actor/path differs')
        roots.append((world['conditionalProbability'], initial, question, sequence))
    require(len(roots) == 8, 'Complete BB compatible worlds differ')
    return key, roots


def numerator_plans(roots, action, audit):
    menus = {}

    def collect(node):
        if node.actor == 5:
            menus[node.key] = tuple(a for a, _ in node.edges)
        for _, child in node.edges:
            collect(child)

    for _, _, question, _ in roots:
        collect(dict(question.edges)[action])
    keys = sorted(menus)
    flow = audit['minimumReachWitness']['opponentFlow']
    indices = {(s['key'], s['action']): s['index'] for s in flow['sequences']}
    projection = audit['opponentProjection']
    expected = []
    for selected in product(*(menus[k] for k in keys)):
        policy = dict(zip(keys, selected, strict=True))
        coefficients = [0.] * len(flow['sequences'])

        def visit(node, chance, sequence):
            if node.actor == -2:
                coefficients[sequence] += chance * node.utilities[5]
            elif node.actor == 5:
                visit(dict(node.edges)[policy[node.key]], chance, sequence)
            else:
                for label, child in node.edges:
                    visit(child, chance * label if node.actor == -1 else chance,
                          sequence if node.actor == -1 else indices[node.key, label])

        for chance, _, question, sequence in roots:
            visit(dict(question.edges)[action], chance, sequence)
        expected.append(dict(continuation=policy, constant=dot(coefficients, projection['offset']),
            cost=[dot(coefficients, [row[j] for row in projection['transform']])
                  for j in range(len(projection['independentSequences']))]))
    require(len(expected) == len(audit['numeratorPlans']), 'Complete BB continuation enumeration differs')
    for wanted, actual in zip(expected, audit['numeratorPlans'], strict=True):
        require(wanted['continuation'] == actual['continuation'], 'BB continuation identity differs')
        close(actual['constant'], wanted['constant'], 'BB numerator constant differs')
        vector(actual['cost'], wanted['cost'])


def checked_witness(witness, audit, parent, tree, roots, action, matrix_width):
    width = len(audit['opponentProjection']['independentSequences'])
    scaling = witness['point'][matrix_width] if len(witness['point']) > matrix_width else 1
    require(0 < scaling <= 10000 + EPS, 'Fractional scaling invalid')
    close(witness['scaling'], scaling, 'Fractional scaling differs')
    vector(witness['opponentVariables'], [v / scaling for v in witness['point'][:width]])
    flow = witness['opponentFlow']
    original = parent['solve']['firstFlow']
    require(flow['actor'] == 3 and flow['sequences'] == original['sequences']
            and flow['conservation'] == original['conservation'], 'BB opponent topology differs')
    projection = audit['opponentProjection']
    realization = flow['realization']
    vector(realization, [offset + dot(row, witness['opponentVariables'])
                        for offset, row in zip(projection['offset'], projection['transform'], strict=True)])
    close(realization[0], 1, 'Original root flow differs')
    require(min(realization) >= -EPS, 'Negative opponent flow')
    policy = copy.deepcopy(parent['candidate']['strategy'])
    for row in flow['conservation']:
        ids, mass = row['childSequences'], realization[row['parentSequence']]
        close(sum(realization[i] for i in ids), mass, 'Original conservation differs')
        weights = [min(1, max(0, realization[i] / mass)) if mass > 1e-12 else 1 / len(ids) for i in ids]
        total = sum(weights)
        policy[row['key']] = {flow['sequences'][i]['action']: p / total for i, p in zip(ids, weights, strict=True)}
    response = physical.best_response(tree, policy, 5)
    close(witness['globalHeroBestResponse'], response, 'Full-parent BB response differs')
    require(response <= audit['globalHeroUpperValue'] + 2 * EPS, 'Original full security face violated')
    # BB's own preflop call and flop check cancel; BTN's later bet never cancels.
    weights = [p * policy[dict(initial.edges)['check'].key]['bet']
               if question is not initial else p for p, initial, question, _ in roots]
    reach = math.fsum(weights)
    close(witness['questionReach'], reach, 'BB cancelled-path reach differs')
    denominator = audit['denominator']
    close(denominator['constant'] + dot(denominator['cost'], witness['opponentVariables']), reach,
          'Original projected reach differs')
    if len(witness['point']) > matrix_width:
        close(reach * scaling, 1, 'Fractional normalization differs')
    forced = physical.Node(-1, edges=tuple((weight / reach, dict(question.edges)[action])
        for (_, _, question, _), weight in zip(roots, weights, strict=True)))
    close(witness['conditionalHeroBestResponse'], physical.best_response(forced, policy, 5),
          'BB conditional optimal continuation differs')
    require(len(witness['posterior']) == len(roots), 'BB posterior inventory differs')
    linear_total = math.fsum(p * realization[sequence] for p, _, _, sequence in roots)
    tv = math.fsum(.5 * abs(weight / reach - p * realization[sequence] / linear_total)
        for (p, _, _, sequence), weight in zip(roots, weights, strict=True))
    close(witness['posteriorTotalVariation'], tv, 'BB posterior total variation differs')
    require(tv <= EPS, 'Original and behavioral posteriors disagree')
    for i, (row, (p, _, _, sequence), weight) in enumerate(zip(witness['posterior'], roots, weights, strict=True)):
        require(row['rootIndex'] == i and row['opponentSequence'] == sequence, 'BB posterior root identity differs')
        for field, wanted in [('chanceWeight', p), ('originalRealizationWeight', p * realization[sequence]),
                              ('behavioralWeight', weight), ('probability', weight / reach)]:
            close(row[field], wanted, 'BB posterior or cancelled path differs', mass=True)


def check_case(item, parent, tree, captured=None):
    request, report = item['request'], item['conditional']
    key, roots = roots_for(request, parent, tree)
    path, hand = request['flopActions'], request['heroCards']
    require(report is not None and item['rejection'] is None and report['parent'] == parent
        and request['hero'] == 5 and request['intervalAlgorithm'] == ALGORITHM
        and request['publicPreflopHistory'] == '|BB:call' and path in [[], ['check', 'bet']]
        and report['request'] == request and report['informationSet'] == key
        and report['schemaVersion'] == 'pokerlab-refined-flop-conditional-report/v5'
        and report['publicationStatus'] == 'REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY'
        and report['trainerAdmission'] is False
        and report['evScope'] == 'ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
        and report['reachUnits'] == 'COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1',
        'Explicit BB interval identity differs')
    handoff = parent['handoffs'][0]
    committed = list(handoff['committedBb'])
    if path:
        committed[3] += 3.25
    players = [dict(seat=s, status='FOLDED' if handoff['folded'][i] else 'ACTING' if i == 5 else 'ACTIVE',
                    committedBb=committed[i], remainingStackBb=100-committed[i])
               for i, s in enumerate(physical.SEATS)]
    context = dict(actingSeat='BB', board=list(source.FLOP), publicPreflopHistory='|BB:call',
        preflopHistory=handoff['completePreflopHistory'],
        flopHistory=[dict(seat='BB', action='check', amountBb=0), dict(seat='BTN', action='bet', amountBb=3.25)] if path else [],
        potBb=9.75 if path else 6.5, toCallBb=3.25 if path else 0, betSizeBb=3.25,
        players=players, legalActions=['fold', 'call'] if path else ['check', 'bet'])
    require(report['context'] == context, 'Literal BB table context differs')
    require([a['action'] for a in report['actions']] == context['legalActions'], 'BB menu differs')
    baseline, certificates = parent['solve'], 0
    for action in report['actions']:
        audit = action['diagnostic']
        past = [dict(action='call', key=f'5:supplied-connected-single-flop:supplied-conditional-preflop:{hand}:')]
        if path:
            past.append(dict(action='check', key=f'5:supplied-flop-one-bet:{hand}:|BB:call:{source.BOARD}:'))
        require(audit['algorithm'] == ALGORITHM and audit['trainerAdmission'] is False
            and audit['baseline'] == baseline and audit['fixedHeroPast'] == past
            and audit['scope'] == 'ACTION_CONDITIONED_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1'
            and audit['securitySlack'] == EPS and audit['requiredMinimumReach'] == .0001
            and audit['question'] == dict(hero=5, informationSet=key, action=action['action']), 'BB endpoint scope differs')
        matrix, rhs, width = bounds.face(audit, baseline)
        flow = baseline['firstFlow']
        coefficients = [0.] * len(flow['sequences'])
        for p, _, _, sequence in roots:
            coefficients[sequence] += p
        projection = audit['opponentProjection']
        dc = dot(coefficients, projection['offset'])
        d = [dot(coefficients, [row[j] for row in projection['transform']]) for j in range(width)]
        close(audit['denominator']['constant'], dc, 'BB denominator constant differs')
        vector(audit['denominator']['cost'], d)
        numerator_plans(roots, action['action'], audit)
        n, shift = len(matrix[0]), action['upperObjectiveShift']
        close(shift, -bounds.maximum_utility(tree)-1, 'BB original objective shift differs')
        scaled = [row + [-bound] for row, bound in zip(matrix, rhs, strict=True)]
        scaled += [d+[0]*(n-width)+[dc], [-v for v in d]+[0]*(n-width)+[-dc], [0]*n+[1]]
        sb = [0]*len(rhs)+[1,-1,10000]
        for sign, name in [(-1,'minimumReachWitness'), (1,'maximumReachWitness')]:
            certificate(matrix, rhs, [sign*v for v in d]+[0]*(n-width), audit[name]); certificates += 1
        require(len(audit['upperWitnesses']) == len(audit['numeratorPlans']), 'Upper endpoint plan inventory differs')
        for plan_index, (plan, witness) in enumerate(zip(audit['numeratorPlans'], audit['upperWitnesses'], strict=True)):
            cost = [c+shift*v for c,v in zip(plan['cost'],d,strict=True)]+[0]*(n-width)+[plan['constant']+shift*dc]
            certificate(scaled,sb,cost,witness); certificates += 1
            if captured is not None and action['action'] == 'check' and plan_index == 0:
                require(len(captured['matrix']) == len(scaled), 'Captured row inventory differs')
                for actual, wanted in zip(captured['matrix'], scaled, strict=True):
                    vector(actual,wanted)
                vector(captured['rhs'],sb); vector(captured['cost'],cost)
        plans = audit['numeratorPlans']
        lower = [row+[0] for row in scaled] + [plan['cost']+[0]*(n-width)+[plan['constant'],1] for plan in plans]
        certificate(lower, sb+[-shift]*len(plans), [0]*(n+1)+[1], audit['lowerWitness']); certificates += 1
        close(action['lowerUtilityBb'], -shift-audit['lowerWitness']['certificate']['dualValue']-EPS, 'BB padded lower differs')
        close(action['upperUtilityBb'], max(w['certificate']['dualValue']-shift for w in audit['upperWitnesses'])+EPS, 'BB padded upper differs')
        close(audit['lowerUtility'], -shift-audit['lowerWitness']['certificate']['primalValue'], 'BB raw lower differs')
        close(audit['upperUtility'], max(w['certificate']['primalValue']-shift for w in audit['upperWitnesses']), 'BB raw upper differs')
        require(audit['upperPlan'] == max(range(len(plans)),key=lambda i:audit['upperWitnesses'][i]['certificate']['primalValue']), 'BB selected upper plan differs')
        close(audit['certifiedReachLowerBound'], dc-audit['minimumReachWitness']['certificate']['dualValue']-EPS, 'BB reach lower differs')
        close(audit['certifiedReachUpperBound'], dc+audit['maximumReachWitness']['certificate']['dualValue']+EPS, 'BB reach upper differs')
        require(audit['certifiedReachLowerBound'] >= .0001, 'BB original-face reach insufficient')
        for field in ['Lower','Upper']:
            close(action['rawCertifiedReach'+field+'Bound'], physical.P*audit['certifiedReach'+field+'Bound'], 'BB raw reach differs',mass=True)
        witnesses = [audit['minimumReachWitness'],audit['maximumReachWitness'],audit['lowerWitness'],*audit['upperWitnesses']]
        for witness in witnesses:
            checked_witness(witness,audit,parent,tree,roots,action['action'],n)
        work = audit['work']
        require(work['intervalLpSolves'] == len(witnesses)
            and work['intervalLpArithmeticWork'] == sum(w['certificate']['work']['arithmeticWork'] for w in witnesses) <= 4_000_000_000
            and work['intervalLpPivots'] == sum(sum(w['certificate']['work'][k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots']) for w in witnesses) <= 10000
            and 0 <= work['compilerUnits'] <= work['compilerLimit'] == 8_000_000, 'BB cumulative work differs')
    lower = max(a['lowerUtilityBb'] for a in report['actions'])
    upper = max(a['upperUtilityBb'] for a in report['actions'])
    moves = []
    for action in report['actions']:
        close(action['lowerLossBb'],max(0,lower-action['upperUtilityBb']),'BB loss lower differs')
        close(action['upperLossBb'],max(0,upper-action['lowerUtilityBb']),'BB loss upper differs')
        classification = 'WITHIN_LIMIT' if action['upperLossBb'] <= .01 else 'OUTSIDE_LIMIT' if action['lowerLossBb'] > .01 else 'STRATEGY_DEPENDENT'
        moves.append(dict(action=action['action'],lowerEvBb=action['lowerUtilityBb'],upperEvBb=action['upperUtilityBb'],lowerLossBb=action['lowerLossBb'],upperLossBb=action['upperLossBb'],classification=classification))
    require(item['moves'] == moves, 'BB loss classification differs')
    separated = {'WITHIN_LIMIT','OUTSIDE_LIMIT'} <= {m['classification'] for m in moves}
    require(item['outcome'] == ('ACTION_SEPARATION' if separated else 'NO_ACTION_SEPARATION'), 'BB outcome differs')
    return certificates


def check(request_path=REQUEST, report_path=REPORT, *, bind_saved_bytes=True):
    request,_,_ = read(request_path)
    report,raw,expanded = read(report_path)
    budget,_,_ = read(BUDGET)
    old_request,_,_ = read(DATA/'supplied-unpaired-bb-flop-coverage-request.json')
    old,_,_ = read(DATA/'supplied-unpaired-bb-flop-coverage-report.json.gz')
    capture,_,_ = read(CAPTURE)
    wanted = dict(old_request,schemaVersion='pokerlab-big-blind-flop-coverage-request/v2',intervalAlgorithm=ALGORITHM)
    require(request == wanted and digest(request_path) == budget['coverageRequestSha256'], 'Explicit complete BB request differs')
    require(digest(CAPTURE) == budget['capturedLpSha256'] and digest(DATA/'supplied-unpaired-bb-flop-coverage-report.json.gz') == budget['originalRejectedReportSha256'], 'Frozen original failure bytes differ')
    require(budget['physicalGameBuilds'] == 1 and budget['plannedCaseAttempts'] == 6
        and budget['maximumParentSolves'] == 18 and budget['maximumActionComputations'] == 12
        and budget['freshReferenceRuns'] == budget['automaticRetries'] == 0
        and budget['trainerAdmission'] is False and budget['maximumExpandedBytesPerReport'] == 8388608
        and budget['securitySlack'] == EPS and budget['requiredMinimumReach'] == .0001
        and budget['maximumLossBb'] == .01 and budget['originalWorkAndNumericCapsPreserved'] is True, 'Predeclared BB generation bounds differ')
    parent,_,_ = read(source.PARENT_REPORT)
    parent_request,_,_ = read(source.PARENT_REQUEST)
    require(request['parent'] == parent_request, 'Original BB parent request changed')
    tree,parent_receipt = source.checked_parent(parent,parent_request)
    declared = copy.deepcopy(request)
    declared['parent']['connected'] = source.connected(request['parent']['connected'])
    require(report['schemaVersion'] == 'pokerlab-big-blind-flop-coverage-report/v2'
        and report['publicationStatus'] == 'INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and report['trainerAdmission'] is False and report['request'] == declared and len(report['cases']) == 6
        and report['binding'] == parent['binding']
        and report['reservation'] == dict(physicalGameBuilds=1,caseAttempts=6,maximumActionComputations=12,maximumParentSolves=18,freshReferenceRuns=0,automaticRetries=0), 'BB batch identity or reservation differs')
    require(request['hero'] == 5 and request['heroHands'] == sorted({w['dealtCombos'][5] for w in parent['originalJointPrior']}) == ['7d 7h','9d 9h','Ac Jc'], 'BB complete hand inventory differs')
    certificates = 0
    for i,item in enumerate(report['cases']):
        conditional_request = dict(schemaVersion='pokerlab-refined-flop-conditional-request/v1',parent=declared['parent'],intervalAlgorithm=ALGORITHM,hero=5,heroCards=request['heroHands'][i//2],publicPreflopHistory='|BB:call',flopActions=[] if i%2 == 0 else ['check','bet'])
        require(item['index'] == i and item['request'] == conditional_request, 'BB complete ordered plan differs')
        certificates += check_case(item,parent,tree,capture if i == 0 else None)
        original = old['cases'][i]
        require(original['index'] == i and original['request'] == dict(conditional_request,intervalAlgorithm=old_request['intervalAlgorithm'])
            and original['outcome'] == 'LP_REJECTED' and original['conditional'] is None and original['moves'] == []
            and original['rejection'] == dict(layer='LP',reason='NUMERICAL_FAILURE',detail='Singular LP basis',reachFailure=None), 'Original six failures hidden or edited')
    require(report['counts'] == dict(planned=6,solved=6,actionSeparated=0,conditionalRejected=0,lpRejected=0)
        and old['counts'] == dict(planned=6,solved=0,actionSeparated=0,conditionalRejected=0,lpRejected=6), 'BB outcome inventory differs')
    oracle = capture['oracle']
    require(capture['trainerAdmission'] is False and oracle['trainerAdmission'] is False
        and oracle['status'] == 0 and capture['failedPhaseOnePivots'] == 21 and capture['failedPhaseTwoPivots'] == 0
        and capture['originalRejection'] == 'Singular LP basis', 'Captured diagnostic identity differs')
    oracle_values = exact.exact(capture['matrix'],capture['rhs'],capture['cost'],oracle['point'],oracle['dual'])
    close(oracle['maximum'],float(oracle_values['primalValue']),'Independent LP oracle differs')
    if bind_saved_bytes:
        pins,_,_ = read(DATA/'big-blind-flop-discovery-pins.json')
        require(pins == dict(reportStoredSha256=hashlib.sha256(raw).hexdigest(),reportExpandedSha256=hashlib.sha256(expanded).hexdigest()), 'Frozen refined BB report differs')
    return dict(schemaVersion='pokerlab-independent-big-blind-flop-discovery/v1',trainerAdmission=False,
        freshLpSolves=0,freshCfrRuns=0,contextsChecked=6,actionsChecked=12,originalFailuresPreserved=6,
        originalEndpointCertificatesChecked=certificates,parentCheck=parent_receipt,qualifiedTrainerQuestions=0,
        completeOriginalSecurityFace=True,heroPastCancellationAndOpponentLikelihoodChecked=True,
        capturedUpperLpPhysicallyReconstructed=True,oracleExactCertificateChecked=True,
        classifications=[dict(heroCards=i['request']['heroCards'],flopActions=i['request']['flopActions'],moves=i['moves']) for i in report['cases']],
        checkerSourceSha256=digest(Path(__file__)),requestSha256=digest(request_path),reportStoredSha256=hashlib.sha256(raw).hexdigest(),
        reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),generationBudgetSha256=digest(BUDGET),capturedLpSha256=digest(CAPTURE))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args = parser.parse_args()
    require(not args.output.exists(),'Output receipt must be new')
    result = check()
    with args.output.open('x',encoding='utf-8',newline='\n') as stream:
        json.dump(result,stream,indent=2);stream.write('\n')
    print(json.dumps(result,indent=2))
