"""Independent literal size-menu physics, perfect recall, certificates and three-policy BR.

No LP optimization or CFR training. Immutable card/certificate helpers are reused
read-only; the size-menu action tree is reconstructed separately from Java.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('size_menu_physics',ROOT/'scripts/check-bb-sizing-physics.py')
base=importlib.util.module_from_spec(spec);sys.modules[spec.name]=base;spec.loader.exec_module(base)
require,close,dot,vector=base.require,base.close,base.dot,base.vector
def read(path): return base.read(path)[0]
FLOP,BOARD,EPS=base.FLOP,base.BOARD,base.EPS
Node,utility,P,SEATS=base.Node,base.utility,base.P,base.SEATS
count_cached,full_counts=base.count_cached,base.full_counts
bounds,parent_check=base.bounds,base.parent_check
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-flop-size-menu-request.json'
REPORT=DATA/'supplied-flop-size-menu-report.json.gz'
REFERENCE=DATA/'supplied-unpaired-flop-parent-report.json.gz'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

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
        moves = path.split('|') if path else []
        size = next((float(m.split(':')[1]) for m in moves if m.startswith('bet:')),None)
        if path == 'check|check' or path.endswith('|fold') or path.endswith('|call'):
            chips = list(committed)
            if size is not None:
                bettor = 5 if moves[0].startswith('bet:') else 3
                amount = min(100-committed[bettor],math.floor(math.fsum(committed)*size*1e6+.5)/1e6)
                chips[bettor] += amount
                if moves[-1] == 'call': chips[3 if bettor == 5 else 5] += amount
                if moves[-1] == 'fold': return Node(-2,utilities=utility(chips,winner=bettor))
            return Node(-2,utilities=utility(chips,share))
        actor = 5 if len(moves)%2 == 0 else 3
        choices = declared['menu']['firstToAct' if not moves else 'afterCheck']
        actions = ('fold','call') if size is not None else ('check',*(f'bet:{v}' for v in choices))
        key = f'{actor}:supplied-size-menu-flop:{hands[i]["dealtCombos"][actor]}:{history}:{BOARD}:{path}'
        return decision(actor,key,[(a,postflop(i,history,committed,path+'|'+a if path else a)) for a in actions])

    def continuation(i, history, committed, actions):
        nonlocal node_count
        # The boundary itself is counted by the preflop caller; add residual and twelve flop nodes.
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
        key = f'{actor}:supplied-size-menu-preflop:supplied-conditional-preflop:{hands[i]["dealtCombos"][actor]}:{history}'
        return decision(actor, key, children)

    root = Node(-1, edges=tuple((w['conditionalProbability'], preflop(i)) for i, w in enumerate(hands)))
    node_count += 1
    require(node_count == 1609 and rows_used == set(policy), 'Independent complete tree or policy coverage differs')
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

def check_values(request, report):
    request=copy.deepcopy(request)
    request['input']=base.physical.canonical_input(request['input']);request['flop']=sorted(request['flop'])
    old=read(REFERENCE)
    require(set(request)=={'schemaVersion','input','flop','menu'} and request['schemaVersion']=='pokerlab-supplied-flop-size-menu-request/v1'
        and request['input']==old['request']['connected']['input'] and request['flop']==list(FLOP)
        and request['menu']==dict(firstToAct=[.25,.5],afterCheck=[1.0]),'Declared size-menu input differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','binding','originalJointPrior','sourcePayoffs','handoffs','residuals','solutionHash','candidate','solve','firstLpWitness','secondLpWitness','references'}
        and report['schemaVersion']=='pokerlab-supplied-flop-size-menu-report/v1' and report['publicationStatus']=='JOINTLY_SOLVED_SIZE_MENU_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and report['request']==request,'Size-menu report identity differs')
    binding=report['binding'];carrier=old['binding']
    expected=copy.deepcopy(carrier['budget']);expected.update(completeTreeStates=1609,informationSets=dict(BTN=56,BB=33),sequences=dict(BTN=121,BB=85))
    require(binding==dict(model='SUPPLIED_JOINT_PREFLOP_SELECTED_FLOP_SIZE_MENU/v1',trainerAdmission=False,
        physicalCarrier=carrier,menu=request['menu'],budget=expected),'Size-menu physical lineage or dimensions differ')
    require(report['originalJointPrior']==old['originalJointPrior'] and report['sourcePayoffs']==old['sourcePayoffs']
        and report['handoffs']==old['handoffs'] and report['residuals']==old['residuals'],'Physical source changed')
    worlds=request['input']['worlds'];counts=full_counts(tuple(tuple(w['dealtCombos']) for w in worlds))
    for i,(world,c) in enumerate(zip(worlds,counts,strict=True)):
        require(report['originalJointPrior'][i]==dict(dealIndex=i,dealtCombos=world['dealtCombos'],conditionalProbability=1/24),'Joint prior differs')
        require(report['sourcePayoffs'][i]['counts']==dict(boards=658008,firstSeat=3,secondSeat=5,firstWins=c[0],secondWins=c[1],ties=c[2],handEvaluations=1316016),'Full-board physical counts differ')
        require(tuple(count_cached(json.dumps(report['originalJointPrior'][i],sort_keys=True)))==c[3:],'Independent fixed-flop evaluator differs')
    policy=report['candidate']['strategy'];audit=report['solve']
    require(report['candidate']['iterations']==1 and len(policy)==89 and audit['algorithm']==parent_check.AFFINE
        and audit['treeNodes']==1609 and audit['firstActor']==3 and audit['secondActor']==5
        and len(audit['informationSets'])==89 and audit['constantSum']==.5,'Solver inventory differs')
    require(audit['reductionWork']['limit']==8_000_000 and 0<=audit['reductionWork']['chargedUnits']<=8_000_000,'Reduction cap differs')
    root=literal_tree(report,dict(binding=carrier['sourceBinding']),request)
    def validate_policy(value):
        require(set(value)==set(policy),'Policy information-set coverage differs')
        for key,row in value.items():
            require(set(row)==set(policy[key]) and all(math.isfinite(p) and 0<=p<=1 for p in row.values()),'Policy actions or probabilities differ')
            close(math.fsum(row.values()),1,'Policy row not normalized')
    validate_policy(policy)
    bounds.payoff(root,audit)
    for first,name in [(True,'first'),(False,'second')]:
        matrix,rhs,cost=parent_check.lp_problem(audit,first);witness=report[name+'LpWitness']
        parent_check.certificate(matrix,rhs,cost,witness,audit[name+'Lp'])
        parent_check.flow_policy(audit[name+'Flow'],audit[name+'Projection'],witness,policy)
    q=parent_check.quality(root,policy);base.physical.compare_quality(audit['behavioralQuality'],q)
    require(q[3]<=1e-8,'Owned policy gap exceeds unchanged tolerance')
    close(audit['lowerValue'],q[0][3],'Lower value differs');close(audit['upperValue'],q[1][3],'Upper value differs')
    require(report['solutionHash']==audit['behavioralPolicyHash'],'Policy hash differs')
    require(len(report['references'])==2,'Fresh reference inventory differs')
    def leaves(node):return 1 if node.actor==-2 else sum(leaves(child) for _,child in node.edges)
    terminal_count=leaves(root)
    gaps=[]
    for reference,iterations in zip(report['references'],(80000,160000),strict=True):
        require(reference['policy']['iterations']==iterations and reference['validatedTreeNodes']==1609
            and reference['inactiveUtilities']=={'0':0,'1':0,'2':0,'4':-.5}
            and reference['prunedSuffixRoots']==4*iterations,'Fresh reference pruning evidence differs')
        require(reference['statistics']==dict(visitedNodes=iterations*(2*1609+4),terminalNodes=iterations*2*terminal_count,
            sampledChanceNodes=0,baselineCorrections=0),'Fresh exhaustive work differs')
        validate_policy(reference['policy']['strategy']);quality=parent_check.quality(root,reference['policy']['strategy'])
        base.physical.compare_quality(reference['quality'],quality)
        require(quality[3]<=.001,'Fresh reference gap exceeds unchanged gate');gaps.append(quality[3])
    return dict(schemaVersion='pokerlab-size-menu-independent-check/v1',trainerAdmission=False,
        literalTreeStates=1609,informationSets=89,originalLpCertificatesChecked=2,policiesChecked=3,
        physicalWorlds=24,fullBoardsRegenerated=15792192,fixedRunoutsRegenerated=15984,
        nashConvBb=q[3],referenceNashConvBb=gaps,freshLpSolves=0,freshCfrRuns=0)


def check(request_path=REQUEST,report_path=REPORT):
    receipt=check_values(read(request_path),read(report_path))
    receipt.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(request_path),reportStoredSha256=sha(report_path),
        immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in ['check-bb-sizing-physics.py','check-unpaired-flop-discovery.py','CheckWeakHandPhysicalCounts.java','check-supplied-flop-action-bounds.py','check-refined-parent-study.py']},
        physicalCarrierStoredSha256=sha(REFERENCE))
    return receipt

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    result=check();data=json.dumps(result,indent=2)+'\n'
    if args.receipt:
        with args.receipt.open('x') as output:output.write(data)
    print(data)
