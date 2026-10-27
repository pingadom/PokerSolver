"""Independent original-face conditional witnesses for the three-action size-menu game."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('conditional_size_menu_parent',ROOT/'scripts/check-flop-size-menu.py')
size=importlib.util.module_from_spec(spec);sys.modules[spec.name]=size;spec.loader.exec_module(size)
base=size.base
physical,bounds,source=base.physical,base.bounds,base.source
require,close,dot,vector=size.require,size.close,size.dot,size.vector
read=size.read
certificate,numerator_plans,checked_witness=base.certificate,base.numerator_plans,base.checked_witness
EPS=1e-8
ALGORITHM=base.ALGORITHM
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-size-menu-99-conditional-request.json'
REPORT=DATA/'supplied-size-menu-99-conditional-report.json.gz'

def check_values(request,report):
    request=copy.deepcopy(request);request['parent']['input']=physical.canonical_input(request['parent']['input']);request['parent']['flop']=sorted(request['parent']['flop'])
    require(set(request)=={'schemaVersion','parent','hero','heroCards','publicPreflopHistory','flopActions'}
        and request['schemaVersion']=='pokerlab-size-menu-conditional-request/v1' and request['hero']==5 and request['heroCards']=='9d 9h'
        and request['publicPreflopHistory']=='|BB:call' and request['flopActions']==[],'Declared conditional scope differs')
    parent=report['parent'];parent_receipt=size.check_values(request['parent'],parent)
    require(parent==read(size.REPORT),'Owned size-menu parent changed')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','evScope','reachUnits','informationSet','parent','context','actions'}
        and report['schemaVersion']=='pokerlab-size-menu-conditional-report/v1' and report['publicationStatus']=='SIZE_MENU_CONDITIONAL_INTERVAL_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and report['request']==request
        and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
        and report['reachUnits']=='COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1','Conditional report identity differs')
    tree=size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),request['parent'])
    hand,path=request['heroCards'],request['flopActions'];roots=[]
    key=f'5:supplied-size-menu-flop:{hand}:|BB:call:{size.BOARD}:'
    require(report['informationSet']==key,'Owned conditional question differs')
    for (_,preflop),world in zip(tree.edges,parent['originalJointPrior'],strict=True):
        if world['dealtCombos'][5] != hand:continue
        initial=dict(preflop.edges)['call'].edges[0][1]
        require(initial.key==key and initial.actor==5,'Literal size-menu root differs')
        roots.append((world['conditionalProbability'],initial,initial,0))
    require(len(roots)==8,'Physical question support differs')
    handoff=parent['handoffs'][0]
    players=[dict(seat=s,status='FOLDED' if handoff['folded'][i] else 'ACTING' if i==5 else 'ACTIVE',committedBb=handoff['committedBb'][i],remainingStackBb=handoff['remainingStackBb'][i]) for i,s in enumerate(size.SEATS)]
    context=dict(actingSeat='BB',board=list(size.FLOP),publicPreflopHistory='|BB:call',preflopHistory=handoff['completePreflopHistory'],
        flopHistory=[],potBb=6.5,toCallBb=0,players=players,actions=[dict(action='check',amountBb=0),dict(action='bet:0.25',amountBb=1.625),dict(action='bet:0.5',amountBb=3.25)])
    require(report['context']==context and [a['action'] for a in report['actions']]==[a['action'] for a in context['actions']],'Literal public context/menu differs')
    baseline, certificates = parent['solve'], 0
    for action in report['actions']:
        audit = action['diagnostic']
        past = [dict(action='call', key=f'5:supplied-size-menu-preflop:supplied-conditional-preflop:{hand}:')]
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
    require(certificates==13,'Complete endpoint certificate count differs')
    return dict(schemaVersion='pokerlab-size-menu-conditional-independent-check/v1',trainerAdmission=False,
        parentCheck=parent_receipt,conditionalCertificatesChecked=certificates,conditionalActionsChecked=3,
        physicalQuestionWorlds=8,heroPastCancelled=True,reachFloor=.0001,securitySlack=EPS,
        continuationPlans=[len(a['diagnostic']['numeratorPlans']) for a in report['actions']],
        moves=moves,freshLpSolves=0,freshCfrRuns=0)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def check(request_path=REQUEST,report_path=REPORT):
    result=check_values(read(request_path),read(report_path))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(request_path),reportStoredSha256=sha(report_path),
        helperSha256={n:sha(ROOT/'scripts'/n) for n in ['check-flop-size-menu.py','check-bb-sizing-physics.py','check-big-blind-flop-discovery.py']})
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    result=check();data=(json.dumps(result,indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output:output.write(data)
    print(data.decode())
