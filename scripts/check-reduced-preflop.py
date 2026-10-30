"""Original-face certificates and exhaustive erasure oracle for the declared reduced preflop study."""
import argparse
import copy
import hashlib
import importlib.util
from itertools import product
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('reduced_parent',ROOT/'scripts/check-flop-size-menu.py')
size=importlib.util.module_from_spec(spec);sys.modules[spec.name]=size;spec.loader.exec_module(size)
base=size.base;bounds=base.bounds;physical=base.physical
require,close,vector,dot,read=size.require,size.close,size.vector,size.dot,size.read
DATA=ROOT/'docs/data';REQUEST=DATA/'supplied-reduced-preflop-request.json';REPORT=DATA/'supplied-reduced-preflop-report.json.gz'
ALGORITHM='OWNED_SIZE_MENU_PREFLOP_ROOT_REDUCED_HERO_PLANS_CERTIFIED_ENDPOINTS/v1'
EPS=1e-8

def plans(roots,action,baseline,audit):
    menus={};flow=baseline['firstFlow'];indices={(s['key'],s['action']):s['index'] for s in flow['sequences']}
    def collect(n):
        if n.actor==5:
            choices=tuple(a for a,_ in n.edges)
            require(n.key not in menus or menus[n.key]==choices,'Hidden-world hero menus differ');menus[n.key]=choices
        for _,child in n.edges:collect(child)
    for _,node in roots:collect(dict(node.edges)[action])
    keys=sorted(menus);count=math.prod(len(menus[k]) for k in keys)
    require(count<=1296,'Independent Cartesian oracle ceiling exceeded')
    representatives={};largest=0.
    for selected in product(*(menus[k] for k in keys)):
        full=dict(zip(keys,selected,strict=True));active={};coefficients=[0.]*len(flow['sequences'])
        def visit(n,p,sequence,choice,used,output):
            if n.actor==-2:output[sequence]+=p*n.utilities[5]
            elif n.actor==5:
                a=choice[n.key];used[n.key]=a;visit(dict(n.edges)[a],p,sequence,choice,used,output)
            else:
                for a,child in n.edges:visit(child,p*a if n.actor==-1 else p,sequence if n.actor==-1 else indices[n.key,a],choice,used,output)
        for p,node in roots:visit(dict(node.edges)[action],p,0,full,active,coefficients)
        # Independently replay the erased map, rather than just comparing the plan count.
        erased=[0.]*len(flow['sequences'])
        for p,node in roots:visit(dict(node.edges)[action],p,0,active,{},erased)
        vector(coefficients,erased);largest=max(largest,max(map(abs,(a-b for a,b in zip(coefficients,erased,strict=True)))))
        signature=tuple(sorted(active.items()))
        if signature in representatives:vector(representatives[signature],coefficients)
        else:representatives[signature]=coefficients
    ordered=sorted(representatives)
    if audit is not None:
        require(len(audit['numeratorPlans'])==len(ordered),'Reduced plan inventory differs')
        projection=audit['opponentProjection']
        for signature,actual in zip(ordered,audit['numeratorPlans'],strict=True):
            coefficients=representatives[signature]
            require(actual['continuation']==dict(signature),'Reduced hero choices differ')
            close(actual['constant'],dot(coefficients,projection['offset']),'Reduced numerator constant differs')
            vector(actual['cost'],[dot(coefficients,[row[j] for row in projection['transform']]) for j in range(len(projection['independentSequences']))])
    return dict(action=action,cartesianPlansChecked=count,reducedPlansChecked=len(ordered),maximumErasureCoefficientDifference=largest)

def witness(w,a,parent,tree,roots,action,width):
    variables=len(a['opponentProjection']['independentSequences']);scaling=w['point'][width] if len(w['point'])>width else 1
    require(0<scaling<=10000+EPS,'Fractional scaling outside original bounds');close(w['scaling'],scaling,'Scaling differs')
    vector(w['opponentVariables'],[x/scaling for x in w['point'][:variables]])
    projection=a['opponentProjection'];original=[off+dot(row,w['opponentVariables']) for off,row in zip(projection['offset'],projection['transform'],strict=True)]
    flow=parent['solve']['firstFlow'];f=w['opponentFlow']
    require(f['actor']==3 and f['sequences']==flow['sequences'] and f['conservation']==flow['conservation'],'Original opponent topology differs')
    require(all(math.isfinite(v) and -EPS<=v<=1+EPS for v in original),'Invalid original opponent flow')
    point=[max(0.,v) for v in original];vector(f['realization'],point)
    policy=copy.deepcopy(parent['candidate']['strategy']);residual=abs(original[0]-1)
    for row in flow['conservation']:
        ids=row['childSequences'];total=sum(point[i] for i in ids)
        residual=max(residual,abs(sum(original[i] for i in ids)-original[row['parentSequence']]),abs(total-point[row['parentSequence']]))
        probabilities=[point[i]/total for i in ids] if total>0 else [1/len(ids)]*len(ids)
        policy[row['key']]={flow['sequences'][i]['action']:v for i,v in zip(ids,probabilities,strict=True)}
    require(residual<=EPS,'Original conservation differs');close(f['maximumResidual'],residual,'Recorded flow residual differs')
    resolved={0:1.};pending=list(flow['conservation'])
    while pending:
        rest=[]
        for row in pending:
            if row['parentSequence'] not in resolved:rest.append(row);continue
            for i in row['childSequences']:resolved[i]=resolved[row['parentSequence']]*policy[row['key']][flow['sequences'][i]['action']]
        require(len(rest)<len(pending),'Unresolvable original recall');pending=rest
    require(max(abs(resolved[i]-v) for i,v in enumerate(original))<=EPS,'Behavior changes original realization')
    response=physical.best_response(tree,policy,5);close(w['globalHeroBestResponse'],response,'Full-parent best response differs')
    require(response<=a['globalHeroUpperValue']+2*EPS,'Full-parent security violated')
    reach=math.fsum(p for p,_ in roots);close(w['questionReach'],reach,'Original-prior root reach differs')
    close(a['denominator']['constant']+dot(a['denominator']['cost'],w['opponentVariables']),reach,'Affine root reach differs')
    if len(w['point'])>width:close(scaling*reach,1,'Fractional root normalization differs')
    forced=size.Node(-1,edges=tuple((p/reach,dict(n.edges)[action]) for p,n in roots))
    close(w['conditionalHeroBestResponse'],physical.best_response(forced,policy,5),'Forced full continuation best response differs')
    require(len(w['posterior'])==8,'Complete root posterior required')
    linear=sum(p*point[0] for p,_ in roots);tv=sum(.5*abs(p/reach-p*point[0]/linear) for p,_ in roots)
    close(w['posteriorTotalVariation'],tv,'Posterior total variation differs');require(tv<=EPS,'Original/behavioral posterior mismatch')
    for i,(mass,(p,_)) in enumerate(zip(w['posterior'],roots,strict=True)):
        require(mass['rootIndex']==i and mass['opponentSequence']==0,'Original root identity differs')
        for field,value in [('chanceWeight',p),('originalRealizationWeight',p*point[0]),('behavioralWeight',p),('probability',p/reach)]:close(mass[field],value,'Original joint-prior posterior differs',mass=True)

def endpoint(e,parent,tree,roots,key):
    u=e['utility'];a=u['diagnostic'];baseline=parent['solve']
    require(e['rejection'] is None and a['algorithm']==ALGORITHM and a['trainerAdmission'] is False and a['baseline']==baseline and a['fixedHeroPast']==[]
        and a['question']==dict(hero=5,informationSet=key,action=e['action']) and a['securitySlack']==EPS and a['requiredMinimumReach']==.0001
        and a['scope']=='ACTION_CONDITIONED_POSTERIOR_OPTIMAL_HERO_CONTINUATION_TERMINAL_UTILITY/v1','Original reduced interval identity differs')
    coverage=plans(roots,e['action'],baseline,a)
    matrix,rhs,width=bounds.face(a,baseline);coefficients=[sum(p for p,_ in roots)]+[0.]*(len(baseline['firstFlow']['sequences'])-1)
    projection=a['opponentProjection'];dc=dot(coefficients,projection['offset']);d=[dot(coefficients,[row[j] for row in projection['transform']]) for j in range(width)]
    close(a['denominator']['constant'],dc,'Original-prior denominator differs');vector(a['denominator']['cost'],d)
    n=len(matrix[0]);shift=u['upperObjectiveShift'];close(shift,-bounds.maximum_utility(tree)-1,'Original utility shift differs')
    scaled=[row+[-b] for row,b in zip(matrix,rhs,strict=True)]+[d+[0]*(n-width)+[dc],[-v for v in d]+[0]*(n-width)+[-dc],[0]*n+[1]]
    sb=[0]*len(rhs)+[1,-1,10000];certificates=0
    for sign,name in [(-1,'minimumReachWitness'),(1,'maximumReachWitness')]:base.certificate(matrix,rhs,[sign*v for v in d]+[0]*(n-width),a[name]);certificates+=1
    require(len(a['upperWitnesses'])==len(a['numeratorPlans']),'Reduced upper witness inventory differs')
    for plan,w in zip(a['numeratorPlans'],a['upperWitnesses'],strict=True):
        cost=[c+shift*v for c,v in zip(plan['cost'],d,strict=True)]+[0]*(n-width)+[plan['constant']+shift*dc]
        base.certificate(scaled,sb,cost,w);certificates+=1
    lower=[row+[0] for row in scaled]+[p['cost']+[0]*(n-width)+[p['constant'],1] for p in a['numeratorPlans']]
    base.certificate(lower,sb+[-shift]*len(a['numeratorPlans']),[0]*(n+1)+[1],a['lowerWitness']);certificates+=1
    close(u['lowerBb'],-shift-a['lowerWitness']['certificate']['dualValue']-EPS,'Padded lower differs')
    close(u['upperBb'],max(w['certificate']['dualValue']-shift for w in a['upperWitnesses'])+EPS,'Padded upper differs')
    close(a['lowerUtility'],-shift-a['lowerWitness']['certificate']['primalValue'],'Raw lower differs')
    close(a['upperUtility'],max(w['certificate']['primalValue']-shift for w in a['upperWitnesses']),'Raw upper differs')
    require(a['upperPlan']==max(range(len(a['upperWitnesses'])),key=lambda i:a['upperWitnesses'][i]['certificate']['primalValue']),'Upper plan identity differs')
    for field,value in [('Lower',dc-a['minimumReachWitness']['certificate']['dualValue']-EPS),('Upper',dc+a['maximumReachWitness']['certificate']['dualValue']+EPS)]:
        close(a['certifiedReach'+field+'Bound'],value,'Certified root reach differs');close(u['rawCertifiedReach'+field+'Bound'],value,'Root reach was scaled by flop chance',mass=True)
    require(a['certifiedReachLowerBound']>=.0001,'Whole-face positive reach required')
    witnesses=[a['minimumReachWitness'],a['maximumReachWitness'],a['lowerWitness'],*a['upperWitnesses']]
    for w in witnesses:witness(w,a,parent,tree,roots,e['action'],n)
    work=a['work'];require(work['intervalLpSolves']==certificates and work['intervalLpArithmeticWork']==sum(w['certificate']['work']['arithmeticWork'] for w in witnesses)<=4_000_000_000
        and work['intervalLpPivots']==sum(sum(w['certificate']['work'][k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots']) for w in witnesses)<=10000
        and 0<=work['compilerUnits']<=work['compilerLimit']==8_000_000,'Original work budget differs')
    return coverage,certificates

def check_values(request,report):
    request=copy.deepcopy(request);request['parent']['input']=physical.canonical_input(request['parent']['input']);request['parent']['flop'].sort()
    require(set(request)=={'schemaVersion','parent','hero','heroCards'} and request['schemaVersion']=='pokerlab-reduced-preflop-request/v1' and request['hero']==5 and request['heroCards']=='9d 9h','Declared single case differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','evScope','reachUnits','informationSet','context','cartesianPlanCounts','parent','endpoints','complete'}
        and report['schemaVersion']=='pokerlab-reduced-preflop-report/v1' and report['publicationStatus']=='REDUCED_PREFLOP_INTERVAL_RESEARCH_ONLY'
        and report['trainerAdmission'] is False and report['request']==request and report['complete'] is False
        and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1' and report['reachUnits']=='ORIGINAL_JOINT_QUESTION_REACH_HERO_PAST_CANCELLED/v1','Reduced research scope differs')
    parent=report['parent'];parent_receipt=size.check_values(request['parent'],parent);require(parent==read(size.REPORT),'Original joint parent differs')
    tree=size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),request['parent'])
    roots=[(w['conditionalProbability'],n) for (_,n),w in zip(tree.edges,parent['originalJointPrior'],strict=True) if w['dealtCombos'][5]=='9d 9h']
    key='5:supplied-size-menu-preflop:supplied-conditional-preflop:9d 9h:'
    require(len(roots)==8 and all(n.key==key and n.actor==5 for _,n in roots) and report['informationSet']==key,'Complete original root support differs')
    chips=[0,0,0,3,.5,1];players=[dict(seat=s,status='FOLDED' if i in (0,1,2,4) else 'ACTING' if i==5 else 'ACTIVE',committedBb=chips[i],remainingStackBb=100-chips[i]) for i,s in enumerate(size.SEATS)]
    context=dict(actingSeat='BB',publicHistory='',history=parent['handoffs'][0]['completePreflopHistory'][:-1],potBb=4.5,toCallBb=2.,players=players,actions=[dict(action=a,amountBb=v) for a,v in [('fold',0.),('call',2.),('raise:9.0',8.)]])
    require(report['context']==context and report['cartesianPlanCounts']==dict(fold=1,call=6,**{'raise:9.0':1296}) and [e['action'] for e in report['endpoints']]==['fold','call','raise:9.0'],'Original complete root menu/context differs')
    coverages=[];certificates=0
    for e in report['endpoints'][:2]:
        require(set(e)=={'action','utility','rejection'} and e['utility'] is not None,'Completed endpoint lost evidence');coverage,count=endpoint(e,parent,tree,roots,key);coverages.append(coverage);certificates+=count
    failed=report['endpoints'][2];require(failed['utility'] is None and failed['rejection']==dict(kind='LP',reason='WORK_LIMIT',detail='LP arithmetic work budget exceeded',reachFailure=None,cartesianPlans=None,reducedPlans=None),'Original raise work failure must remain visible')
    coverages.append(plans(roots,'raise:9.0',parent['solve'],None))
    require([c['reducedPlansChecked'] for c in coverages]==[1,4,52],'Expected exact reduced inventories differ')
    return dict(schemaVersion='pokerlab-reduced-preflop-independent-check/v1',trainerAdmission=False,parentCheck=parent_receipt,originalEndpointCertificatesChecked=certificates,completedActions=['fold','call'],retainedRejections=[dict(action='raise:9.0',kind='LP',reason='WORK_LIMIT')],completeMenu=False,fullCartesianPlansChecked=sum(c['cartesianPlansChecked'] for c in coverages),reducedPlansChecked=sum(c['reducedPlansChecked'] for c in coverages),coverage=coverages,freshLpSolves=0,freshCfrRuns=0)

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def check():
    result=check_values(read(REQUEST),read(REPORT));result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(REQUEST),reportStoredSha256=sha(REPORT),immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in ['check-flop-size-menu.py','check-bb-sizing-physics.py','check-big-blind-flop-discovery.py','check-supplied-flop-action-bounds.py']});return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as f:f.write(data)
    print(data.decode())
