"""Independent unpaired-board physical tree, original LP certificates and zero-reach witnesses.

Fixed declared 24-world study. No LP/CFR optimization, policy qualification or trainer authority.
All older checkers remain byte-unchanged; shared algebra helpers are imported read-only.
"""
import argparse
import copy
from functools import lru_cache
import hashlib
import importlib.util
from itertools import combinations
import json
import math
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('unpaired_certified_helper',ROOT/'scripts/check-certified-reach-coverage.py')
certified=importlib.util.module_from_spec(spec);sys.modules[spec.name]=certified;spec.loader.exec_module(certified)
weak=certified.weak
parent_check=weak.parent_check
physical,bounds=weak.physical,weak.bounds
require,close,read,dot,vector=weak.require,weak.close,weak.read,weak.dot,weak.vector
face,maximum_utility,certificate=weak.face,weak.maximum_utility,weak.certificate
exact_certificate,reconstruct,behavior=certified.exact_certificate,certified.reconstruct,certified.behavior
Node,utility,P,SEATS=physical.Node,physical.utility,physical.P,physical.SEATS
FLOP=('6d','Qc','Td');BOARD=' '.join(FLOP)
EPS=1e-8
INTERVAL_ALGORITHM=certified.ALGORITHM
REJECTION=certified.REJECTION
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-unpaired-flop-coverage-request.json'
REPORT=DATA/'supplied-unpaired-flop-coverage-report.json.gz'
PARENT_REQUEST=DATA/'supplied-unpaired-flop-parent-request.json'
PARENT_REPORT=DATA/'supplied-unpaired-flop-parent-report.json.gz'
BUDGET=DATA/'unpaired-flop-discovery-generation-budget.json'
CARD_REFERENCE=ROOT/'scripts/CheckWeakHandPhysicalCounts.java'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def connected(value):
    result=copy.deepcopy(value);result['input']=physical.canonical_input(result['input']);result['flop']=sorted(result['flop']);return result

@lru_cache(maxsize=24)
def count_cached(world):
    hands=[tuple(map(physical.card,h.split())) for h in json.loads(world)['dealtCombos']]
    flop=tuple(map(physical.card,FLOP));dead={c for h in hands for c in h}|set(flop)
    require(len(dead)==15,'Physical card collision, including folded cards')
    remaining=tuple(c for c in physical.DECK if c not in dead)
    wins=losses=ties=0
    for runout in combinations(remaining,2):
        a,b=physical.seven(hands[3]+flop+runout),physical.seven(hands[5]+flop+runout)
        wins+=a>b;losses+=a<b;ties+=a==b
    require(wins+losses+ties==666,'Physical runout count differs')
    return wins,losses,ties

@lru_cache(maxsize=1)
def full_counts(worlds):
    java=str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if 'JAVA_HOME' in os.environ else 'java'
    lines=[' '.join(cards)+' '+BOARD for cards in worlds]
    result=subprocess.run([java,str(CARD_REFERENCE)],input='\n'.join(lines)+'\n',text=True,capture_output=True,check=True,timeout=180)
    counts=[tuple(map(int,line.split(','))) for line in result.stdout.splitlines()]
    require(len(counts)==24 and all(len(c)==6 and min(c)>=0 and sum(c[:3])==658008 and sum(c[3:])==666 for c in counts),'Independent card inventory differs')
    return counts

def literal_tree(report,parent,declared):
    policy=report['candidate']['strategy']
    hands = report['originalJointPrior']
    physical = [count_cached(json.dumps(w, sort_keys=True)) for w in hands]
    handoffs = {h['publicHistory']: h for h in report['handoffs']}
    residuals = {(r['publicHistory'], r['originalDealIndex']): r for r in report['residuals']}
    require(len(handoffs) == 4 and len(residuals) == 96, 'Continuation inventory differs')
    selected = {}
    public = {}
    rows_used = set()
    node_count = 0

    def decision(actor, key, children):
        require(key in policy and set(policy[key]) == {a for a, _ in children}, 'Candidate action identity differs')
        rows_used.add(key)
        return Node(actor, key, tuple(children))

    def postflop(i, history, committed, path=''):
        nonlocal node_count
        node_count += 1
        share = (physical[i][0]+.5*physical[i][2])/666
        amount = min(100-committed[3], math.floor(math.fsum(committed)*.5*1e6+.5)/1e6)
        if path in ('check|check', 'bet|fold', 'bet|call', 'check|bet|fold', 'check|bet|call'):
            chips = list(committed)
            if path != 'check|check':
                bettor = 5 if path.startswith('bet') else 3
                chips[bettor] += amount
                if path.endswith('call'):
                    chips[3 if bettor == 5 else 5] += amount
                if path.endswith('fold'):
                    return Node(-2, utilities=utility(chips, winner=bettor))
            return Node(-2, utilities=utility(chips, share))
        actor = 5 if path in ('', 'check|bet') else 3
        actions = ('fold', 'call') if path in ('bet', 'check|bet') else ('check', 'bet')
        key = f'{actor}:supplied-flop-one-bet:{hands[i]["dealtCombos"][actor]}:{history}:{BOARD}:{path}'
        return decision(actor, key, [(a, postflop(i, history, committed, path+'|'+a if path else a)) for a in actions])

    def continuation(i, history, committed, actions):
        nonlocal node_count
        # The boundary itself is counted by the preflop caller; add residual and nine flop nodes.
        node_count += 1
        flop_root = postflop(i, history, committed)
        selected[i, history] = flop_root
        full = report['sourcePayoffs'][i]['counts']
        residual = tuple(10*full[k]-v for k, v in zip(('firstWins', 'secondWins', 'ties'), physical[i]))
        denominator = 6579414
        share = (residual[0]+.5*residual[2])/denominator
        values = utility(committed, share)
        saved = residuals[history, i]
        require(saved['counts'] == dict(firstSeat=3, secondSeat=5, firstWins=residual[0],
                secondWins=residual[1], ties=residual[2], boardFlopPartitions=denominator), 'Residual partition counts differ')
        for a, b in zip(saved['utilitiesBb'], values):
            close(a, b, 'Residual literal settlement differs')
        public[history] = (committed, actions)
        return Node(-1, edges=((P, flop_root), (1-P, Node(-2, utilities=values))))

    def preflop(i, level=0, history='', committed=(0, 0, 0, 3, .5, 1), actions=()):
        nonlocal node_count
        node_count += 1
        actor = 5 if level % 2 == 0 else 3
        opponent = 3 if actor == 5 else 5
        fold = Node(-2, utilities=utility(committed, winner=opponent))
        matched = list(committed)
        matched[actor] = matched[opponent]
        call_history = history+f'|{SEATS[actor]}:call'
        call_actions = actions+('call',)
        node_count += 2  # fold leaf and matched call boundary/leaf.
        if level < 4:
            called = continuation(i, call_history, tuple(matched), call_actions)
        else:
            counts = report['sourcePayoffs'][i]['counts']
            called = Node(-2, utilities=utility(matched, (counts['firstWins']+.5*counts['ties'])/658008))
        children = [('fold', fold), ('call', called)]
        if level < 4:
            target = (9, 22, 50, 100)[level]
            raised = list(committed)
            raised[actor] = target
            action = f'raise:{target}.0'
            children.append((action, preflop(i, level+1, history+f'|{SEATS[actor]}:{action}', tuple(raised), actions+(action,))))
        key = f'{actor}:supplied-connected-single-flop:supplied-conditional-preflop:{hands[i]["dealtCombos"][actor]}:{history}'
        return decision(actor, key, children)

    root = Node(-1, edges=tuple((w['conditionalProbability'], preflop(i)) for i, w in enumerate(hands)))
    node_count += 1
    require(node_count == 1321 and rows_used == set(policy), 'Independent complete tree or policy coverage differs')
    for history, (committed, actions) in public.items():
        h = handoffs[history]
        require(h['sourceBinding'] == parent['binding'] and h['trainerAdmission'] is False
                and h['model'] == 'SUPPLIED_JOINT_COUNTERFACTUAL_FIXED_FLOP_CHECKDOWN/v1'
                and h['beliefs'] == 'ORIGINAL_JOINT_SUPPORT_CONDITIONED_ONLY_ON_PHYSICAL_FLOP/v1'
                and h['sourceHistoryReachStatus'] == 'UNAVAILABLE_NO_SOURCE_POLICY'
                and h['flop'] == list(FLOP) and h['continuationActions'] == list(actions)
                and h['firstToAct'] == 'BB' and h['secondToAct'] == 'BTN', 'Handoff physical identity differs')
        close(h['potBb'], math.fsum(committed), 'Handoff pot differs')
        require(h['committedBb'] == list(committed) and h['remainingStackBb'] == [100-c for c in committed]
                and h['folded'] == [True, True, True, False, True, False], 'Handoff stacks/fold flags differ')
        moves = [dict(seat='SB', kind='POST_SMALL_BLIND', amountBb=.5), dict(seat='BB', kind='POST_BIG_BLIND', amountBb=1)]
        for action in declared['input']['specification']['history']:
            text = action['action']
            moves.append(dict(seat=action['seat'], kind='FOLD' if text == 'fold' else 'RAISE_TO',
                              amountBb=0 if text == 'fold' else float(text.split(':')[1])))
        chips, actor = [0, 0, 0, 3, .5, 1], 5
        for action in actions:
            amount = chips[3 if actor == 5 else 5] if action == 'call' else float(action.split(':')[1])
            moves.append(dict(seat=SEATS[actor], kind='CALL' if action == 'call' else 'RAISE_TO', amountBb=amount))
            chips[actor] = amount
            actor = 3 if actor == 5 else 5
        require(h['completePreflopHistory'] == moves, 'Complete literal public history differs')
        close(h['compatibleOriginalPriorMass'], 1, 'Compatible support differs')
        close(h['physicalFlopMass'], P, 'Physical chance was renormalized', mass=True)
        require(h['enumeratedRunouts'] == 15984 and h['handEvaluations'] == 31968 and len(h['worlds']) == 24,
                'Fixed-flop physical budget differs')
        masses = []
        for i, world in enumerate(h['worlds']):
            w, l, t = physical[i]
            require(world['originalDealIndex'] == i and world['sixDealtCombos'] == hands[i]['dealtCombos']
                    and world['counts'] == dict(firstSeat=3, secondSeat=5, firstWins=w, secondWins=l,
                    ties=t, runouts=666, handEvaluations=1332), 'Independent physical runout counts differ')
            close(world['originalJointProbability'], 1/24, 'Original joint weight differs', mass=True)
            close(world['privateGivenPhysicalFlopWeight'], 1/24, 'Conditional physical support differs', mass=True)
            close(world['physicalFlopProbabilityGivenDeal'], P, 'Physical chance was renormalized', mass=True)
            for a, b in zip(world['checkdownUtilitiesBb'], utility(committed, (w+.5*t)/666)):
                close(a, b, 'Flop literal checkdown differs')
    require(set(handoffs)==set(public),'Handoff inventory changed')
    return root

def checked_parent(parent, request):
    declared = connected(request['connected'])
    require(set(parent)=={'schemaVersion','publicationStatus','trainerAdmission','request','binding','originalJointPrior','sourcePayoffs','handoffs','residuals','solutionHash','candidate','solve','firstLpWitness','secondLpWitness'} and
            parent['schemaVersion']=='pokerlab-refined-parent-flop-report/v1' and parent['publicationStatus']=='REFINED_SINGLE_FLOP_PARENT_RESEARCH_ONLY' and parent['trainerAdmission'] is False and parent['request']==dict(request,connected=declared), 'New physical parent identity differs')
    worlds = declared['input']['worlds']
    require(len(worlds)==24 and all(w['weight']==1.0 for w in worlds),'Fixed joint worlds differ')
    prior = [dict(dealIndex=i,dealtCombos=w['dealtCombos'],conditionalProbability=1/24) for i,w in enumerate(worlds)]
    require(parent['originalJointPrior']==prior,'Original joint prior differs')
    counts=full_counts(tuple(tuple(w['dealtCombos']) for w in worlds))
    expected = [dict(dealIndex=i,dealtCombos=w['dealtCombos'],counts=dict(boards=658008,firstSeat=3,secondSeat=5,firstWins=c[0],secondWins=c[1],ties=c[2],handEvaluations=1316016)) for i,(w,c) in enumerate(zip(worlds,counts,strict=True))]
    require(parent['sourcePayoffs']==expected,'Independent complete preflop physical counts differ')
    source_budget=dict(completeTreeStates=361,enumeratedBoards=15792192,handEvaluations=31584384,informationSets=dict(BTN=8,BB=9),jointDeals=24,publicStates=15,sequences=dict(BTN=25,BB=25))
    source=dict(beliefs='EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1',budget=source_budget,inputHash='ad38e633f247e0746498cf719c48b13e15e50981ad5aea4125630280f547c5c4',jointPriorHash='349b805035540bfd40e7fb21d2f0ef88b5a8ce94622c89d4a999a9dda24539a7',model='TWO_ACTIVE_SUPPLIED_JOINT_PREFLOP_MANDATORY_CHECKDOWN/v1',payoffAlgorithm='EXACT_TWO_ACTIVE_SIX_DEALT_BOARD_ENUMERATION/v1',payoffHash='3e46f65af73aeaaf3e552669645d92f01f8c40717b364b6a80c81ba450d98fa3',sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY')
    b=parent['binding']
    require(b==dict(betPotFraction=.5,budget=dict(additionalEnumeratedRunouts=63936,additionalHandEvaluations=127872,compatibleDeals=24,completeTreeStates=1321,informationSets=dict(BTN=40,BB=33),nonAllInBoundaries=4,sequences=dict(BTN=89,BB=73),sourceBudget=source_budget),chanceModel='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1',flop=list(FLOP),handoffsHash='1412314572476fe6556d6f7eeb7b8d800634387658ef01dd50f6de65d9ec4607',model='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1',residualsHash='60698f26ad89b4cb2f30215c197c68873149e8e7299a742b73294256bd4091ed',sourceBinding=source,sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY',trainerAdmission=False),'New physical model binding differs')
    policy=parent['candidate']['strategy'];audit=parent['solve']
    require(parent['candidate']['iterations']==1 and len(policy)==73 and audit['algorithm']==parent_check.AFFINE and audit['treeNodes']==1321 and audit['firstActor']==3 and audit['secondActor']==5 and len(audit['informationSets'])==73,'Parent solver or complete tree inventory differs')
    require(audit['reductionWork']['limit']==8_000_000 and 0<=audit['reductionWork']['chargedUnits']<=8_000_000,'Parent reduction budget differs')
    for row in policy.values():
        require(all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Invalid parent probability')
        close(math.fsum(row.values()),1,'Parent row not normalized')
    root=literal_tree(parent,dict(binding=source),declared)
    for i,c in enumerate(counts):
        literal=count_cached(json.dumps(prior[i],sort_keys=True))
        require(tuple(literal)==c[3:],'Independent mask evaluator differs from literal best-of-21 fixed-flop evaluator')
    bounds.payoff(root,audit)
    for first,name in [(True,'first'),(False,'second')]:
        a,b,c=parent_check.lp_problem(audit,first);witness=parent[name+'LpWitness']
        parent_check.certificate(a,b,c,witness,audit[name+'Lp'])
        parent_check.flow_policy(audit[name+'Flow'],audit[name+'Projection'],witness,policy)
    q=parent_check.quality(root,policy)
    physical.compare_quality(audit['behavioralQuality'],q)
    require(q[3]<=EPS,'Independent full-parent best-response gap exceeds gate')
    close(audit['lowerValue'],q[0][3],'Parent lower differs');close(audit['upperValue'],q[1][3],'Parent upper differs')
    require(parent['solutionHash']==audit['behavioralPolicyHash'],'Parent policy identity differs')
    return root,dict(originalLpCertificatesIndependentlyChecked=2,physicalWorlds=24,fullBoardsRegenerated=15792192,fullBoardHandEvaluations=31584384,fixedRunoutsRegenerated=15984,literalTreeStates=1321,policyInformationSets=73,nashConvBb=q[3],cardReferenceSha256=digest(CARD_REFERENCE))

def check_solved(request,report,parent,root):
    hand=request['heroCards'];KEY=f'3:supplied-flop-one-bet:{hand}:|BB:call:{BOARD}:bet'
    declared=dict(request,parent=dict(request['parent'],connected=connected(request['parent']['connected'])))
    require(request['intervalAlgorithm']==INTERVAL_ALGORITHM and request['hero']==3 and request['heroCards']==hand and request['flopActions']==['bet'] and request['publicPreflopHistory']=='|BB:call','Explicit weak-hand context differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','evScope','reachUnits','informationSet','parent','context','actions'} and report['schemaVersion']=='pokerlab-refined-flop-conditional-report/v4' and report['publicationStatus']=='REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==declared and report['informationSet']==KEY and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1' and report['reachUnits']=='COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1','Weak-hand report identity differs')
    require(report['parent']==parent, 'Shared physical parent differs')
    context = dict(root=root, prior=parent['originalJointPrior'])
    handoff = parent['handoffs'][0]
    seats = ['UTG', 'HJ', 'CO', 'BTN', 'SB', 'BB']
    committed = list(handoff['committedBb']); committed[5] += 3.25
    players = [dict(seat=s, status='FOLDED' if handoff['folded'][i] else 'ACTING' if i == 3 else 'ACTIVE',
                    committedBb=committed[i], remainingStackBb=100-committed[i]) for i, s in enumerate(seats)]
    expected_context = dict(actingSeat='BTN', board=list(FLOP), publicPreflopHistory='|BB:call',
            preflopHistory=handoff['completePreflopHistory'], flopHistory=[dict(seat='BB', action='bet', amountBb=3.25)],
            potBb=9.75, toCallBb=3.25, betSizeBb=3.25, players=players, legalActions=['fold','call'])
    require(report['context'] == expected_context, 'Paired literal public table context differs')
    roots = []
    for i, ((_, preflop), world) in enumerate(zip(root.edges, context['prior'], strict=True)):
        if world['dealtCombos'][3] != hand: continue
        initial = dict(preflop.edges)['call'].edges[0][1]
        reply = dict(initial.edges)['bet']
        require(reply.key == KEY, 'Literal paired question differs')
        roots.append((i, world['conditionalProbability'], initial, reply))
    require(len(roots) == 6, 'Paired compatible hero worlds differ')
    baseline = parent['solve']
    bounds.payoff(context['root'], baseline)
    actions = report['actions']
    require([a['action'] for a in actions] == ['fold', 'call'], 'BTN action menu differs')
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
            sequence = indices[initial.key, 'bet']
            sequences.append(sequence)
            coefficients[sequence] += p
            child = dict(reply.edges)[action['action']]
            require(child.actor == -2, 'Fold/call must terminate the one-bet round')
            numerator[sequence] += p * child.utilities[3]
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
            weights = [p * policy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call'] * policy[initial.key]['bet']
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
    for action in actions:
        close(action['lowerLossBb'], max(0, best_lower-action['upperUtilityBb']), 'Conditional loss lower differs')
        close(action['upperLossBb'], max(0, best_upper-action['lowerUtilityBb']), 'Conditional loss upper differs')
    return certificates

def check_rejection(item,parent,root):
    request=item['request'];hand=request['heroCards'];rejection=item['rejection']
    require(item['outcome']=='CONDITIONAL_REJECTED' and item['conditional'] is None and item['moves']==[]
        and set(rejection)=={'layer','reason','detail','reachFailure'} and rejection['layer']=='CONDITIONAL'
        and rejection['reason']=='INSUFFICIENT_REACH' and rejection['detail']=='Full security face question reach is not certified above the declared floor','Negative reach result differs')
    audit=rejection['reachFailure'];baseline=parent['solve'];witness=audit['minimumReachWitness']
    key=f'3:supplied-flop-one-bet:{hand}:|BB:call:{BOARD}:check'
    require(set(audit)=={'algorithm','trainerAdmission','status','question','fixedHeroPast','baseline','securitySlack','requiredMinimumReach','globalHeroUpperValue','heroProjection','opponentProjection','orientedPayoff','securityFaceHash','denominator','certifiedReachLowerBound','minimumReachWitness','work'}
        and audit['algorithm']==REJECTION and audit['trainerAdmission'] is False
        and audit['status']=='WITNESS_BELOW_REQUIRED_FLOOR' and audit['baseline']==baseline
        and audit['question']==dict(hero=3,informationSet=key,action='check') and audit['fixedHeroPast']==[]
        and audit['securitySlack']==EPS and audit['requiredMinimumReach']==.0001,'Negative reach audit identity differs')
    matrix,rhs,width=weak.face(audit,baseline)
    flow=baseline['secondFlow'];indices={(s['key'],s['action']):s['index'] for s in flow['sequences']}
    roots=[];coefficients=[0.]*len(flow['sequences'])
    for i,((_,preflop),world) in enumerate(zip(root.edges,parent['originalJointPrior'],strict=True)):
        if world['dealtCombos'][3]!=hand:continue
        initial=dict(preflop.edges)['call'].edges[0][1];question=dict(initial.edges)['check']
        require(question.key==key,'Independent after-check question differs')
        sequence=indices[initial.key,'check'];p=world['conditionalProbability']
        roots.append((i,p,preflop,initial,sequence));coefficients[sequence]+=p
    require(len(roots)==6,'Rejected world coverage differs')
    projection=audit['opponentProjection'];dc=dot(coefficients,projection['offset'])
    d=[dot(coefficients,[row[j] for row in projection['transform']]) for j in range(width)]
    close(audit['denominator']['constant'],dc,'Rejected path constant differs');vector(audit['denominator']['cost'],d)
    exact=exact_certificate(matrix,rhs,[-v for v in d]+[0.]*(len(matrix[0])-width),witness)
    variables=witness['point'][:width];vector(witness['opponentVariables'],variables)
    original=reconstruct(projection,variables)
    policy,point,residual,difference=behavior(baseline,original,parent['candidate']['strategy'])
    actual_flow=witness['opponentFlow']
    require(actual_flow['actor']==5 and actual_flow['sequences']==flow['sequences'] and actual_flow['conservation']==flow['conservation'],'Rejected topology differs')
    vector(actual_flow['realization'],point);close(actual_flow['maximumResidual'],residual,'Rejected flow residual differs')
    global_response=weak.physical.best_response(root,policy,3)
    close(witness['globalHeroBestResponse'],global_response,'Rejected global best response differs')
    require(global_response<=baseline['upperValue']+2*EPS,'Rejected witness violates full-parent security')
    require(len(witness['masses'])==6 and 'posterior' not in witness,'Negative witness must not create a posterior')
    reach=0
    for j,((i,p,preflop,initial,sequence),mass) in enumerate(zip(roots,witness['masses'],strict=True)):
        actual=p*policy[preflop.key]['call']*policy[initial.key]['check'];linear=p*point[sequence]
        require(mass['rootIndex']==j and mass['opponentSequence']==sequence,'Rejected root identity differs')
        for field,expected in [('chanceWeight',p),('originalRealizationWeight',linear),('behavioralWeight',actual)]:
            close(mass[field],expected,'Rejected original/path mass differs',mass=True)
        close(linear,actual,'Rejected linear/behavioral mass mismatch',mass=True);reach+=actual
    close(witness['questionReach'],reach,'Rejected question reach differs')
    close(dc+dot(d,variables),reach,'Rejected affine reach differs')
    lower=dc-witness['certificate']['dualValue']-EPS
    close(audit['certifiedReachLowerBound'],lower,'Rejected dual lower differs')
    require(lower<.0001 and reach<.0001-EPS,'Insufficient reach is not witnessed')
    work=audit['work'];lp_work=witness['certificate']['work']
    require(work['intervalLpSolves']==1 and work['intervalLpArithmeticWork']==lp_work['arithmeticWork']<=4_000_000_000
        and work['intervalLpPivots']==sum(lp_work[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots'])<=10000
        and 0<=work['compilerUnits']<=work['compilerLimit']==8_000_000,'Rejected work limits differ')
    return dict(hand=hand,questionReach=reach,certifiedReachLowerBound=lower,fullParentHeroBestResponse=global_response,
        maximumOriginalRealizationDifference=difference,exactOriginalCertificate=exact)

def check(request_path=REQUEST,report_path=REPORT,*,parent_path=PARENT_REPORT,bind_saved_bytes=True):
    request,request_bytes,_=read(request_path);report,raw,expanded=read(report_path)
    parent,parent_raw,parent_expanded=read(parent_path);parent_request,parent_request_bytes,_=read(PARENT_REQUEST)
    budget,_,_=read(BUDGET);control,_,_=read(DATA/'supplied-certified-reach-flop-coverage-request.json')
    expected=copy.deepcopy(control);expected['parent']['connected']['flop']=['6d','Td','Qc']
    require(request==expected and parent_request==request['parent'],'Only the predeclared board may change')
    require(digest(request_path)==budget['coverageRequestSha256'] and digest(PARENT_REQUEST)==budget['parentRequestSha256']
        and digest(DATA/'supplied-certified-reach-flop-coverage-request.json')==budget['controlRequestSha256'],'Predeclared request bytes differ')
    require(budget['schemaVersion']=='pokerlab-unpaired-flop-discovery-generation-budget/v1' and not budget['trainerAdmission']
        and budget['physicalGameBuilds']==2 and budget['standaloneParentStudies']==1 and budget['plannedCaseAttempts']==8
        and budget['maximumActionComputations']==16 and budget['maximumParentSolves']==25
        and budget['freshReferenceRuns']==budget['automaticRetries']==0 and budget['maximumExpandedBytesPerReport']==8388608
        and budget['allOriginalNumericAndCompilerCapsPreserved'] is True,'Declared generation budget differs')
    require(digest(ROOT/'solver/src/main/java/com/pokerlab/solver/SixMaxRefinedFlopFeedback.java')==budget['unchangedQualificationGateSha256'],'Qualification gate changed')
    declared=copy.deepcopy(request);declared['parent']['connected']=connected(request['parent']['connected'])
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','reservation','counts','binding','cases'}
        and report['schemaVersion']=='pokerlab-refined-flop-coverage-report/v2' and report['request']==declared
        and report['publicationStatus']=='INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and report['trainerAdmission'] is False and len(report['cases'])==8,'Coverage identity differs')
    root,parent_receipt=checked_parent(parent,parent_request)
    require(report['binding']==parent['binding'],'Physical board/source binding differs')
    rejected=[];certificates=0;classifications=[]
    for index,item in enumerate(report['cases']):
        wanted=dict(schemaVersion='pokerlab-refined-flop-conditional-request/v1',parent=declared['parent'],intervalAlgorithm=INTERVAL_ALGORITHM,
            hero=3,heroCards=request['heroHands'][index//2],publicPreflopHistory='|BB:call',flopActions=['check' if index%2==0 else 'bet'])
        require(item['index']==index and item['request']==wanted,'Complete ordered case plan differs')
        if index%2==0:rejected.append(check_rejection(item,parent,root));continue
        require(item['outcome']=='ACTION_SEPARATION' and item['rejection'] is None,'Expected solved outcome differs')
        certificates+=check_solved(wanted,item['conditional'],parent,root)
        moves=[]
        for a in item['conditional']['actions']:
            classification='WITHIN_LIMIT' if a['upperLossBb']<=.01 else 'OUTSIDE_LIMIT' if a['lowerLossBb']>.01 else 'STRATEGY_DEPENDENT'
            moves.append(dict(action=a['action'],lowerEvBb=a['lowerUtilityBb'],upperEvBb=a['upperUtilityBb'],lowerLossBb=a['lowerLossBb'],upperLossBb=a['upperLossBb'],classification=classification))
        require(item['moves']==moves and sorted(m['classification'] for m in moves)==['OUTSIDE_LIMIT','WITHIN_LIMIT'],'Move losses/classifications differ')
        classifications.append(dict(hand=wanted['heroCards'],withinLimit=next(m['action'] for m in moves if m['classification']=='WITHIN_LIMIT')))
    require(report['counts']==dict(planned=8,solved=4,actionSeparated=4,conditionalRejected=4,lpRejected=0)
        and report['reservation']==dict(physicalGameBuilds=1,caseAttempts=8,maximumActionComputations=16,maximumParentSolves=24,freshReferenceRuns=0,automaticRetries=0),'Counts/reservation differ')
    if bind_saved_bytes:
        pins,_raw,_exp=read(DATA/'unpaired-flop-discovery-pins.json')
        require(pins==dict(parentStoredSha256=hashlib.sha256(parent_raw).hexdigest(),parentExpandedSha256=hashlib.sha256(parent_expanded).hexdigest(),coverageStoredSha256=hashlib.sha256(raw).hexdigest(),coverageExpandedSha256=hashlib.sha256(expanded).hexdigest()),'Frozen report bytes differ')
    return dict(schemaVersion='pokerlab-independent-unpaired-flop-discovery/v1',trainerAdmission=False,freshLpSolves=0,freshCfrRuns=0,
        board=list(FLOP),parentCheck=parent_receipt,solvedCases=4,originalActionEndpointCertificatesChecked=certificates,
        certifiedRejectedCases=rejected,discoveredMoves=classifications,
        checkerSourceSha256=digest(Path(__file__)),generationBudgetSha256=digest(BUDGET),requestSha256=hashlib.sha256(request_bytes).hexdigest(),
        parentStoredSha256=hashlib.sha256(parent_raw).hexdigest(),coverageStoredSha256=hashlib.sha256(raw).hexdigest())

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    require(not args.output.exists(),'Output receipt must be new');result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps(result,indent=2))
