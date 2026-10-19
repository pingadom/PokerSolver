"""Independent paired-flop physics, original parent LP witnesses and literal best responses; no optimizer."""
import argparse
from functools import lru_cache
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-supplied-flop-action-bounds.py'
spec=importlib.util.spec_from_file_location('refined_parent_bounds',HELPER)
bounds=importlib.util.module_from_spec(spec);sys.modules[spec.name]=bounds;spec.loader.exec_module(bounds)
physical_helper=bounds.physical
FLOP=('8d','Kc','Kd')
physical_helper.FLOP=FLOP
P=physical_helper.P
SEATS=physical_helper.SEATS
Node,utility,quality=physical_helper.Node,physical_helper.utility,physical_helper.quality
require,close,read=physical_helper.require,physical_helper.close,physical_helper.read
canonical_input=physical_helper.canonical_input
LP='OWNED_ASCENDING_L1_COMPENSATED_PHASE_TWO_DIRECTION_ORIGINAL_CERTIFICATE/v4'
AFFINE='BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_REFINED_DIRECTIONS_ORIGINAL_CERTIFICATES/v2'
REQUEST_SHA='b59a1086567f7f0212d383ad177693ff08f50ee8b40e6bf78d8ce227e010a037'
REPORT_SHA='4ed407bd3f4094691c966f1c5d218fb5942d8340714707a0eae3fae7d5693d64'

@lru_cache(maxsize=24)
def count_cached(world):
    return physical_helper.count_flop(json.loads(world))

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
        key = f'{actor}:supplied-flop-one-bet:{hands[i]["dealtCombos"][actor]}:{history}:8d Kc Kd:{path}'
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

def lp_problem(audit,row):
    own=audit['firstProjection' if row else 'secondProjection']
    other=audit['secondProjection' if row else 'firstProjection']
    payoff=audit['projectedPayoff']
    primal=len(own['independentSequences']); dual=len(other['rhs'])
    inequalities=len(other['independentSequences'])
    matrix=[];rhs=[]
    for i in range(inequalities):
        matrix.append([(-payoff['matrix'][j][i] if row else payoff['matrix'][i][j]) for j in range(primal)]+[-other['inequalities'][j][i] for j in range(dual)])
        rhs.append(payoff['secondCost'][i] if row else -payoff['firstCost'][i])
    for coefficients,bound in zip(own['inequalities'],own['rhs'],strict=True):
        matrix.append(coefficients+[0]*dual);rhs.append(bound)
    cost=(payoff['firstCost'] if row else [-v for v in payoff['secondCost']])+[-v for v in other['rhs']]
    return matrix,rhs,cost

def certificate(matrix,rhs,cost,witness,expected):
    x,y,c=witness['point'],witness['dual'],witness['certificate']
    require(c==expected and c['algorithm']==LP and c['variables']==len(x)==len(cost)<=512 and c['constraints']==len(y)==len(rhs)<=512,'LP identity or dimensions differ')
    require(all(math.isfinite(v) for v in x+y) and all(math.isfinite(v) and abs(v)<=1_000_000 for row in matrix for v in row) and all(math.isfinite(v) and abs(v)<=1_000_000 for v in rhs+cost),'Invalid original LP coefficients/witness')
    dot=bounds.dot
    pv,dv=dot(cost,x),dot(rhs,y)
    primal=max([0]+[-v for v in x]+[dot(a,x)-b for a,b in zip(matrix,rhs,strict=True)])
    dual=max([0]+[-v for v in y]+[cost[j]-dot([a[j] for a in matrix],y) for j in range(len(cost))])
    require(max(primal,dual,abs(pv-dv))<=1e-8,'Original LP primal/dual certificate failed')
    for k,v in dict(primalValue=pv,dualValue=dv,maximumPrimalViolation=primal,maximumDualViolation=dual,absoluteDualityGap=abs(pv-dv)).items():close(c[k],v,'Original certificate field differs')
    work=c['work']
    require(0<=work['arithmeticWork']<=4_000_000_000 and 0<=sum(work[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots'])<=10000,'Original work limits changed')

def flow_policy(flow,projection,witness,policy):
    n=len(projection['independentSequences'])
    realization=[offset+bounds.dot(row,witness['point'][:n]) for offset,row in zip(projection['offset'],projection['transform'],strict=True)]
    bounds.vector(flow['realization'],realization)
    for row in flow['conservation']:
        mass=realization[row['parentSequence']]
        probabilities=[max(0,min(1,realization[i]/mass if mass>1e-12 else 1/len(row['childSequences']))) for i in row['childSequences']]
        total=math.fsum(probabilities)
        for i,p in zip(row['childSequences'],probabilities,strict=True):
            close(policy[row['key']][flow['sequences'][i]['action']],p/total,'Policy differs from original LP flow')

def check(request_path,report_path,*,bind_saved_bytes=True):
    request,request_bytes,_=read(request_path)
    report,report_bytes,expanded=read(report_path)
    parent,parent_bytes,_=read(physical_helper.PARENT)
    require(hashlib.sha256(parent_bytes).hexdigest()==physical_helper.PARENT_BYTES,'Pinned preflop physical evidence differs')
    require(set(request)=={'schemaVersion','algorithm','connected'} and request['schemaVersion']=='pokerlab-refined-parent-flop-request/v1' and request['algorithm']==AFFINE,'Explicit refined parent request identity differs')
    connected=request['connected']
    require(set(connected)=={'schemaVersion','input','flop','betPotFraction'} and connected['schemaVersion']=='pokerlab-supplied-connected-flop-request/v1' and tuple(connected['flop'])==FLOP and connected['betPotFraction']==.5 and canonical_input(connected['input'])==parent['request']['input'],'Fixed paired input differs')
    declared=dict(connected,input=canonical_input(connected['input']))
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','binding','originalJointPrior','sourcePayoffs','handoffs','residuals','solutionHash','candidate','solve','firstLpWitness','secondLpWitness'},'Report fields differ')
    require(report['schemaVersion']=='pokerlab-refined-parent-flop-report/v1' and report['publicationStatus']=='REFINED_SINGLE_FLOP_PARENT_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==dict(request,connected=declared),'Report identity differs')
    require(report['originalJointPrior']==parent['prior'] and report['sourcePayoffs']==parent['payoffs'],'Original private worlds/preflop payoffs changed')
    binding=report['binding']
    require(binding['sourceBinding']==parent['binding'] and binding['trainerAdmission'] is False and binding['flop']==list(FLOP) and binding['betPotFraction']==.5 and binding['model']=='SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1' and binding['chanceModel']=='ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1' and binding['sourceHistoryReachStatus']=='UNAVAILABLE_NO_SOURCE_POLICY','Physical model binding differs')
    require(binding['budget']==dict(sourceBudget=parent['binding']['budget'],nonAllInBoundaries=4,compatibleDeals=24,completeTreeStates=1321,informationSets={'BTN':40,'BB':33},sequences={'BTN':89,'BB':73},additionalEnumeratedRunouts=63936,additionalHandEvaluations=127872),'Original model budgets changed')
    policy=report['candidate']['strategy'];audit=report['solve']
    require(report['candidate']['iterations']==1 and len(policy)==73 and audit['algorithm']==AFFINE and audit['treeNodes']==1321 and audit['firstActor']==3 and audit['secondActor']==5 and len(audit['informationSets'])==73,'Owned snapshot inventory differs')
    require(audit['reductionWork']['limit']==8_000_000 and 0<=audit['reductionWork']['chargedUnits']<=8_000_000,'Reduction budget differs')
    for row in policy.values():
        require(all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Invalid behavior probability')
        close(math.fsum(row.values()),1,'Behavior row not normalized')
    root=literal_tree(report,parent,declared)
    bounds.payoff(root,audit)
    for row,name in [(True,'first'),(False,'second')]:
        matrix,rhs,cost=lp_problem(audit,row)
        witness=report[name+'LpWitness']
        require(set(witness)=={'point','dual','certificate'},'Witness fields differ')
        certificate(matrix,rhs,cost,witness,audit[name+'Lp'])
        flow_policy(audit[name+'Flow'],audit[name+'Projection'],witness,policy)
    expected=quality(root,policy)
    physical_helper.compare_quality(audit['behavioralQuality'],expected)
    require(expected[3]<=1e-8,'Independent literal best-response gap exceeds gate')
    close(audit['lowerValue'],expected[0][3],'Parent lower value differs')
    close(audit['upperValue'],expected[1][3],'Parent upper value differs')
    require(report['solutionHash']==audit['behavioralPolicyHash'],'Policy identity changed')
    if bind_saved_bytes:
        require(hashlib.sha256(request_bytes).hexdigest()==REQUEST_SHA and hashlib.sha256(report_bytes).hexdigest()==REPORT_SHA,'Saved request/report bytes differ')
    return dict(schemaVersion='pokerlab-independent-refined-parent-check/v1',trainerAdmission=False,scope='FIXED_PAIRED_BOARD_PARENT_ONLY',freshLpSolves=0,freshCfrRuns=0,originalLpCertificatesIndependentlyChecked=2,physicalWorlds=24,runoutsPerWorld=666,literalTreeStates=1321,policyInformationSets=73,originalFullPreflopCounts='BYTE_PINNED_PRIOR_PHYSICAL_ARTIFACT_JAVA_REPLAY_REGENERATES',nashConvBb=expected[3],maximumAllowedGapBb=1e-8,sourceRequestSha256=hashlib.sha256(request_bytes).hexdigest(),sourceReportStoredSha256=hashlib.sha256(report_bytes).hexdigest(),sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),physicalHelperSha256=hashlib.sha256((ROOT/'scripts/check-supplied-connected-flop.py').read_bytes()).hexdigest(),projectionHelperSha256=hashlib.sha256(HELPER.read_bytes()).hexdigest())

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path);parser.add_argument('report',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Output receipt must be new')
    receipt=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8') as output:output.write(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))

if __name__=='__main__':main()
