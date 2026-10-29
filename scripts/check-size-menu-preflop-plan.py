"""Independent literal preflop/flop topology and Cartesian-plan counts; no poker evaluation or solving."""
import argparse
import copy
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-flop-size-menu-request.json'
REPORT=DATA/'supplied-size-menu-preflop-plan.json'
SEATS=['UTG','HJ','CO','BTN','SB','BB']
SOURCE_SHA='28bd4ce547e1819904d5cc217f18410c0d939a81aa87f89e0050915891b3675e'
LIMIT=64

def require(value,message):
    if not value:raise ValueError(message)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):
    require(path.stat().st_size<=8*1024*1024,'Stored JSON cap exceeded')
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,'Duplicate JSON key');result[key]=value
        return result
    def invalid(value):raise ValueError('Nonfinite JSON number')
    return json.loads(path.read_bytes(),object_pairs_hook=pairs,parse_constant=invalid)
@dataclass(frozen=True)
class Node:
    actor:int=-2
    key:str=''
    children:tuple=()

def check_values(request,report):
    request=copy.deepcopy(request)
    for world in request['input']['worlds']:world['dealtCombos']=[' '.join(sorted(c.split())) for c in world['dealtCombos']]
    request['input']['worlds'].sort(key=lambda w:'|'.join(w['dealtCombos']));request['flop'].sort()
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','sourceRequestHash','chanceMassScope','request','estimatedPhysicalBudget','continuationPlanLimit','questions','work'}
        and report['schemaVersion']=='pokerlab-size-menu-preflop-plan/v1' and report['publicationStatus']=='PREFLOP_CONTINUATION_PLAN_PREFLIGHT_ONLY'
        and report['trainerAdmission'] is False and report['chanceMassScope']=='ORIGINAL_JOINT_CHANCE_ONLY_NOT_QUESTION_REACH/v1'
        and report['request']==request and report['continuationPlanLimit']==LIMIT,'Structural report identity differs')
    require(report['sourceRequestHash']==hashlib.sha256(json.dumps(request,sort_keys=True,separators=(',',':')).encode()).hexdigest(),'Structural source identity differs')
    worlds=request['input']['worlds'];spec=request['input']['specification'];menu=request['menu'];board=request['flop']
    require(spec['rules']==dict(stackBb=100.,smallBlindBb=.5,raiseToBb=[3.,9.,22.,50.,100.],raiseSchedule='NEXT_TARGET')
        and spec['history']==[dict(seat=s,action=a) for s,a in [('UTG','fold'),('HJ','fold'),('CO','fold'),('BTN','raise:3.0'),('SB','fold')]]
        and menu==dict(firstToAct=[.25,.5],afterCheck=[1.]) and board==['6d','Qc','Td'] and len(worlds)==24,'Fixed study declaration differs')
    weights=[w['weight'] for w in worlds];maximum=max(weights);total=math.fsum(w/maximum for w in weights)
    require(all(math.isfinite(w) and w>0 for w in weights),'Invalid joint weights')
    probabilities=[(w/maximum)/total for w in weights]
    compatible=[]
    for w in worlds:
        cards=[c for hand in w['dealtCombos'] for c in hand.split()]
        require(len(cards)==len(set(cards))==12,'Original joint blockers differ')
        compatible.append(not set(cards).intersection(board))
    all_infos={};pre_infos={};questions={};nodes=1
    initial_history=[dict(seat='SB',kind='POST_SMALL_BLIND',amountBb=.5),dict(seat='BB',kind='POST_BIG_BLIND',amountBb=1.)]
    for a in spec['history']:
        initial_history.append(dict(seat=a['seat'],kind='RAISE_TO' if a['action'].startswith('raise:') else 'FOLD',amountBb=3. if a['action'].startswith('raise:') else 0.))
    def decision(actor,key,children):
        actions=[a for a,_ in children]
        require(key not in all_infos or all_infos[key]==actions,'Hidden-world menu differs')
        all_infos[key]=actions
        return Node(actor,key,tuple(children))
    def leaf():
        nonlocal nodes
        nodes+=1;return Node()
    def flop(i,history,path=''):
        nonlocal nodes
        if path=='check|check' or path.endswith('|fold') or path.endswith('|call'):return leaf()
        nodes+=1;depth=len(path.split('|')) if path else 0;actor=5 if depth%2==0 else 3
        key=f'{actor}:supplied-size-menu-flop:{worlds[i]["dealtCombos"][actor]}:{history}:6d Qc Td:{path}'
        actions=['fold','call'] if 'bet:' in path else ['check']+[f'bet:{x}' for x in menu['firstToAct' if not path else 'afterCheck']]
        return decision(actor,key,[(a,flop(i,history,path+'|'+a if path else a)) for a in actions])
    def called(i,level,history):
        nonlocal nodes
        if level==4 or not compatible[i]:return leaf()
        nodes+=1;return Node(-1,'',(('physical-flop',flop(i,history)),('residual',leaf())))
    def preflop(i,level=0,history='',committed=(0.,0.,0.,3.,.5,1.),moves=None):
        nonlocal nodes
        if moves is None:moves=initial_history
        nodes+=1;actor=5 if level%2==0 else 3;opponent=3 if actor==5 else 5
        key=f'{actor}:supplied-size-menu-preflop:supplied-conditional-preflop:{worlds[i]["dealtCombos"][actor]}:{history}'
        matched=list(committed);matched[actor]=matched[opponent]
        h=history+f'|{SEATS[actor]}:call'
        children=[('fold',leaf()),('call',called(i,level,h))]
        prices=[dict(action='fold',amountBb=0.),dict(action='call',amountBb=committed[opponent]-committed[actor])]
        if level<4:
            target=[9.,22.,50.,100.][level];action=f'raise:{target}'
            raised=list(committed);raised[actor]=target
            next_moves=moves+[dict(seat=SEATS[actor],kind='RAISE_TO',amountBb=target)]
            children.append((action,preflop(i,level+1,history+f'|{SEATS[actor]}:{action}',tuple(raised),next_moves)))
            prices.append(dict(action=action,amountBb=target-committed[actor]))
        node=decision(actor,key,children);pre_infos[key]=[a for a,_ in children]
        players=[dict(seat=s,status='FOLDED' if seat in (0,1,2,4) else 'ACTING' if seat==actor else 'ACTIVE',committedBb=committed[seat],remainingStackBb=100-committed[seat]) for seat,s in enumerate(SEATS)]
        table=dict(actingSeat=SEATS[actor],publicHistory=history,history=moves,potBb=math.fsum(committed),toCallBb=committed[opponent]-committed[actor],players=players,actions=prices)
        questions.setdefault(key,[]).append((i,node,table))
        return node
    for i in range(len(worlds)):preflop(i)
    def future(node,hero,rows):
        if node.actor==hero:
            actions=[a for a,_ in node.children]
            require(node.key not in rows or rows[node.key]==actions,'Future recall differs');rows[node.key]=actions
        for _,child in node.children:future(child,hero,rows)
    expected=[];within=0;over=0
    for index,(key,support) in enumerate(sorted(questions.items())):
        actor=support[0][1].actor;table=support[0][2]
        require(all(t==table for _,_,t in support),'Hidden-world public context differs')
        actions=[]
        for a,_ in support[0][1].children:
            rows={}
            for _,node,_ in support:future(dict(node.children)[a],actor,rows)
            decisions=[dict(informationSet=k,actions=v) for k,v in sorted(rows.items())];count=math.prod(len(v) for v in rows.values());fits=count<=LIMIT
            actions.append(dict(action=a,futureHeroDecisions=decisions,cartesianPlans=count,enumerationPrechargeUnits=count*nodes,withinPlanLimit=fits,status='WITHIN_PLAN_LIMIT_ONLY' if fits else 'CONTINUATION_PLAN_LIMIT'))
            if fits:within+=1
            else:over+=1
        expected.append(dict(index=index,informationSet=key,hero=SEATS[actor],heroCards=worlds[support[0][0]]['dealtCombos'][actor],originalWorldIndices=sorted(i for i,_,_ in support),priorChanceMass=math.fsum(probabilities[i] for i,_,_ in support),context=table,actions=actions,allMovesWithinPlanLimit=all(a['withinPlanLimit'] for a in actions)))
    require(len(expected)==len(report['questions']),'Complete question inventory differs')
    for e,a in zip(expected,report['questions'],strict=True):
        # Chance-only mass uses compensated summation in both implementations.
        require(abs(e['priorChanceMass']-a['priorChanceMass'])<1e-15,'Original chance mass differs')
        e['priorChanceMass']=a['priorChanceMass'];require(e==a,'Complete preflop context or continuation plans differ')
    def counts(rows,actor):return {k:v for k,v in rows.items() if k.startswith(str(actor)+':')}
    info={SEATS[a]:len(counts(all_infos,a)) for a in (3,5)}
    sequences={SEATS[a]:1+sum(map(len,counts(all_infos,a).values())) for a in (3,5)}
    source_budget=dict(jointDeals=24,publicStates=15,completeTreeStates=361,informationSets={SEATS[a]:len(counts(pre_infos,a)) for a in (3,5)},sequences={SEATS[a]:1+sum(map(len,counts(pre_infos,a).values())) for a in (3,5)},enumeratedBoards=24*math.comb(40,5),handEvaluations=48*math.comb(40,5))
    budget=dict(sourceBudget=source_budget,nonAllInBoundaries=4,compatibleDeals=sum(compatible),completeTreeStates=nodes,informationSets=info,sequences=sequences,additionalEnumeratedRunouts=4*sum(compatible)*math.comb(37,2),additionalHandEvaluations=8*sum(compatible)*math.comb(37,2))
    work=dict(symbolicTreeStates=nodes,preflopInformationSets=len(expected),preflopActions=within+over,withinPlanLimitActions=within,overPlanLimitActions=over,enumeratedBoards=0,handEvaluations=0,lpSolves=0,cfrIterations=0)
    require(report['estimatedPhysicalBudget']==budget and report['work']==work,'Structural inventory or zero-work declaration differs')
    return dict(schemaVersion='pokerlab-size-menu-preflop-plan-independent-check/v1',trainerAdmission=False,symbolicTreeStatesChecked=nodes,preflopQuestionsChecked=len(expected),preflopActionsChecked=within+over,withinPlanLimitActions=within,overPlanLimitActions=over,completeMenuQuestionIndices=[q['index'] for q in expected if q['allMovesWithinPlanLimit']],maximumCartesianPlans=max(a['cartesianPlans'] for q in expected for a in q['actions']),fullJointWorldsChecked=len(worlds),enumeratedBoards=0,handEvaluations=0,freshLpSolves=0,freshCfrRuns=0)

def check():
    require(sha(REQUEST)==SOURCE_SHA,'Original fixed source bytes changed')
    result=check_values(read(REQUEST),read(REPORT))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(REQUEST),reportStoredSha256=sha(REPORT))
    return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output:output.write(data)
    print(data.decode())
