"""Literal physical reconstruction for the two explicitly declared unpaired-flop sizing models.

Original checkers remain unchanged. Physical deal/runout helpers are shared read-only;
this tree uses the declared bet fraction, with original stack caps and all four boundaries.
"""
import copy
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def load(name,file):
 spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
base=load('sizing_physical_source','check-unpaired-flop-discovery.py')
coverage=load('sizing_interval_source','check-big-blind-flop-discovery.py')
physical,bounds,parent_check=base.physical,base.bounds,base.parent_check
require,close,read,dot,vector=base.require,base.close,base.read,base.dot,base.vector
FLOP,BOARD,EPS=base.FLOP,base.BOARD,base.EPS
Node,utility,P,SEATS=base.Node,base.utility,base.P,base.SEATS
connected,full_counts,count_cached,digest=base.connected,base.full_counts,base.count_cached,base.digest
source=base
CARD_REFERENCE=base.CARD_REFERENCE
roots_for,numerator_plans,checked_witness,certificate=coverage.roots_for,coverage.numerator_plans,coverage.checked_witness,coverage.certificate
ALGORITHM=coverage.ALGORITHM

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
        amount = min(100-committed[3], math.floor(math.fsum(committed)*declared["betPotFraction"]*1e6+.5)/1e6)
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
    require(b==dict(betPotFraction=declared["betPotFraction"],budget=dict(additionalEnumeratedRunouts=63936,additionalHandEvaluations=127872,compatibleDeals=24,completeTreeStates=1321,informationSets=dict(BTN=40,BB=33),nonAllInBoundaries=4,sequences=dict(BTN=89,BB=73),sourceBudget=source_budget),chanceModel='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1',flop=list(FLOP),handoffsHash='1412314572476fe6556d6f7eeb7b8d800634387658ef01dd50f6de65d9ec4607',model='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1',residualsHash='60698f26ad89b4cb2f30215c197c68873149e8e7299a742b73294256bd4091ed',sourceBinding=source,sourceHistoryReachStatus='UNAVAILABLE_NO_SOURCE_POLICY',trainerAdmission=False),'New physical model binding differs')
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
    bet_size = min(100-committed[3], math.floor(sum(committed)*parent['binding']['betPotFraction']*1e6+.5)/1e6)
    if path:
        committed[3] += bet_size
    players = [dict(seat=s, status='FOLDED' if handoff['folded'][i] else 'ACTING' if i == 5 else 'ACTIVE',
                    committedBb=committed[i], remainingStackBb=100-committed[i])
               for i, s in enumerate(physical.SEATS)]
    context = dict(actingSeat='BB', board=list(source.FLOP), publicPreflopHistory='|BB:call',
        preflopHistory=handoff['completePreflopHistory'],
        flopHistory=[dict(seat='BB', action='check', amountBb=0), dict(seat='BTN', action='bet', amountBb=bet_size)] if path else [],
        potBb=6.5+bet_size if path else 6.5, toCallBb=bet_size if path else 0, betSizeBb=bet_size,
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

