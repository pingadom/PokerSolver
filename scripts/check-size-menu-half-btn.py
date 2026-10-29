"""Independent fixed half-pot coverage: original security-face zero-reach witnesses, no solving."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
def load(name,file):
 spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);return m
size=load('half_size_parent','check-flop-size-menu.py')
btn=load('half_face','check-btn-flop-feedback.py')
negative=load('half_negative','check-certified-reach-coverage.py')
require,close,read,dot,vector=size.require,size.close,size.read,size.dot,size.vector
DATA=ROOT/'docs/data';REQUEST=DATA/'supplied-size-menu-btn-half-request.json';REPORT=DATA/'supplied-size-menu-btn-half-report.json.gz'
BET='bet:0.5';EPS=1e-8
REJECTION='FULL_SECURITY_FACE_MINIMUM_REACH_CHILD_SUM_ORIGINAL_FLOW_SECURITY_CHECKS/v2'

def rejected_endpoint(endpoint,parent,tree,hand,key):
 require(set(endpoint)=={'action','utility','rejection'} and endpoint['action'] in ['fold','call'] and endpoint['utility'] is None,'Rejected endpoint invented a utility')
 rejection=endpoint['rejection']
 require(set(rejection)=={'kind','reason','detail','reachFailure'} and rejection['kind']=='CONDITIONAL' and rejection['reason']=='INSUFFICIENT_REACH'
   and rejection['detail']=='Full security face question reach is not certified above the declared floor','Typed half-pot rejection differs')
 a=rejection['reachFailure'];baseline=parent['solve'];w=a['minimumReachWitness']
 require(set(a)=={'algorithm','trainerAdmission','status','question','fixedHeroPast','baseline','securitySlack','requiredMinimumReach','globalHeroUpperValue','heroProjection','opponentProjection','orientedPayoff','securityFaceHash','denominator','certifiedReachLowerBound','minimumReachWitness','work'}
   and a['algorithm']==REJECTION and a['trainerAdmission'] is False and a['status']=='WITNESS_BELOW_REQUIRED_FLOOR'
   and a['baseline']==baseline and a['question']==dict(hero=3,informationSet=key,action=endpoint['action'])
   and a['fixedHeroPast']==[] and a['securitySlack']==EPS and a['requiredMinimumReach']==.0001,'Rejected half-pot audit identity differs')
 matrix,rhs,width=btn.face(a,baseline)
 flow=baseline['secondFlow'];ids={(s['key'],s['action']):s['index'] for s in flow['sequences']}
 roots=[];coefficients=[0.]*len(flow['sequences'])
 for i,((_,pre),world) in enumerate(zip(tree.edges,parent['originalJointPrior'],strict=True)):
  if world['dealtCombos'][3]!=hand:continue
  initial=dict(pre.edges)['call'].edges[0][1];reply=dict(initial.edges)[BET]
  require(reply.key==key and reply.actor==3,'Independent half-pot path differs')
  sequence=ids[initial.key,BET];p=world['conditionalProbability']
  roots.append((i,p,pre,initial,sequence));coefficients[sequence]+=p
 require(len(roots)==6,'Complete rejected world inventory differs')
 projection=a['opponentProjection'];dc=dot(coefficients,projection['offset'])
 d=[dot(coefficients,[row[j] for row in projection['transform']]) for j in range(width)]
 close(a['denominator']['constant'],dc,'Half-pot denominator constant differs');vector(a['denominator']['cost'],d)
 exact={k:float(v) for k,v in size.base.certificate(matrix,rhs,[-v for v in d]+[0.]*(len(matrix[0])-width),w).items()}
 variables=w['point'][:width];vector(w['opponentVariables'],variables)
 original=negative.reconstruct(projection,variables)
 policy,point,residual,difference=negative.behavior(baseline,original,parent['candidate']['strategy'])
 f=w['opponentFlow'];require(f['actor']==5 and f['sequences']==flow['sequences'] and f['conservation']==flow['conservation'],'Half-pot rejected topology differs')
 vector(f['realization'],point);close(f['maximumResidual'],residual,'Rejected original conservation residual differs')
 response=size.base.physical.best_response(tree,policy,3)
 close(w['globalHeroBestResponse'],response,'Half-pot witness global best response differs')
 require(response<=a['globalHeroUpperValue']+2*EPS,'Half-pot witness violates full-parent security')
 require(set(w)=={'certificate','point','dual','opponentVariables','opponentFlow','behavioralPolicyHash','questionReach','masses','globalHeroBestResponse'} and len(w['masses'])==6,'Rejected witness invented a posterior or omitted worlds')
 reach=0
 for j,((i,p,pre,initial,sequence),mass) in enumerate(zip(roots,w['masses'],strict=True)):
  actual=p*policy[pre.key]['call']*policy[initial.key][BET];linear=p*point[sequence]
  require(set(mass)=={'rootIndex','stateHash','opponentSequence','chanceWeight','originalRealizationWeight','behavioralWeight'} and mass['rootIndex']==j and mass['opponentSequence']==sequence,'Rejected half-pot path identity differs')
  for field,value in [('chanceWeight',p),('originalRealizationWeight',linear),('behavioralWeight',actual)]:close(mass[field],value,'Rejected half-pot world mass differs',mass=True)
  close(linear,actual,'Opponent call/bet likelihood not retained',mass=True);reach+=actual
 close(w['questionReach'],reach,'Rejected half-pot reach differs');close(dc+dot(d,variables),reach,'Rejected affine/path reach differs')
 lower=dc-w['certificate']['dualValue']-EPS
 close(a['certifiedReachLowerBound'],lower,'Rejected half-pot dual lower differs')
 require(lower<.0001 and reach<.0001-EPS,'Rejected half-pot minimum is not witnessed')
 work=a['work'];lp=w['certificate']['work']
 require(work['intervalLpSolves']==1 and work['intervalLpArithmeticWork']==lp['arithmeticWork']<=4_000_000_000
   and work['intervalLpPivots']==sum(lp[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots'])<=10000
   and 0<=work['compilerUnits']<=work['compilerLimit']==8_000_000,'Rejected half-pot work differs')
 return dict(action=endpoint['action'],questionReach=reach,certifiedReachLowerBound=lower,fullParentHeroBestResponse=response,maximumOriginalRealizationDifference=difference,exactOriginalCertificate=exact)

def check_values(request,report):
 request=copy.deepcopy(request);request['parent']['input']=size.base.physical.canonical_input(request['parent']['input']);request['parent']['flop']=sorted(request['parent']['flop'])
 require(set(request)=={'schemaVersion','parent','maximumLossBb'} and request['schemaVersion']=='pokerlab-size-menu-btn-half-request/v1' and request['maximumLossBb']==.01,'Half-pot declaration differs')
 parent=report['parent'];parent_receipt=size.check_values(request['parent'],parent)
 require(parent==read(size.REPORT),'Half-pot shared parent differs from published joint game')
 require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','parent','cases','qualifiedIndices'} and report['schemaVersion']=='pokerlab-size-menu-btn-half-report/v1'
   and report['publicationStatus']=='SIZE_MENU_BTN_HALF_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==request,'Half-pot report identity differs')
 require([c['heroCards'] for c in report['cases']]==['2c 3c','Ah Kh','As Qs','Qh Th'] and [c['index'] for c in report['cases']]==list(range(4)),'Complete half-pot hand inventory differs')
 tree=size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),parent['request'])
 outcomes=[]
 for c in report['cases']:
  require(set(c)=={'index','heroCards','informationSet','context','endpoints','moves','primary','references','qualifiedForOfflineDrill','rejectionReasons'},'Half-pot case fields differ')
  key=f'3:supplied-size-menu-flop:{c["heroCards"]}:|BB:call:{size.BOARD}:{BET}'
  h=parent['handoffs'][0];chips=list(h['committedBb']);chips[5]+=3.25
  players=[dict(seat=s,status='FOLDED' if h['folded'][i] else 'ACTING' if i==3 else 'ACTIVE',committedBb=chips[i],remainingStackBb=100-chips[i]) for i,s in enumerate(size.SEATS)]
  context=dict(actingSeat='BTN',board=list(size.FLOP),publicPreflopHistory='|BB:call',preflopHistory=h['completePreflopHistory'],flopHistory=[dict(seat='BB',action=BET,amountBb=3.25)],potBb=9.75,toCallBb=3.25,players=players,actions=[dict(action='fold',amountBb=0),dict(action='call',amountBb=3.25)])
  require(c['informationSet']==key and c['context']==context and [e['action'] for e in c['endpoints']]==['fold','call'],'Owned half-pot context or action inventory differs')
  checks=[rejected_endpoint(e,parent,tree,c['heroCards'],key) for e in c['endpoints']]
  reasons=['ENDPOINT:call:CONDITIONAL:INSUFFICIENT_REACH','ENDPOINT:fold:CONDITIONAL:INSUFFICIENT_REACH']
  require(c['moves']==[] and c['primary'] is None and c['references']==[] and c['qualifiedForOfflineDrill'] is False and c['rejectionReasons']==reasons,'Rejected half-pot case gained grades or qualification')
  outcomes.append(dict(index=c['index'],hand=c['heroCards'],qualified=False,reasons=reasons,negativeWitnesses=checks))
 require(report['qualifiedIndices']==[],'Half-pot rejection acquired a capability')
 return dict(schemaVersion='pokerlab-size-menu-btn-half-independent-check/v1',trainerAdmission=False,parentCheck=parent_receipt,handsChecked=4,actionAttemptsChecked=8,negativeReachCertificatesChecked=8,rejectedWorldMassesChecked=48,conditionalUtilityCertificatesChecked=0,policyAssessmentsChecked=0,retainedEndpointRejections=8,qualifiedIndices=[],outcomes=outcomes,freshLpSolves=0,freshCfrRuns=0)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def check(request_path=REQUEST,report_path=REPORT):
 result=check_values(read(request_path),read(report_path))
 result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(request_path),reportStoredSha256=sha(report_path),immutableHelperSha256={n:sha(ROOT/'scripts'/n) for n in ['check-flop-size-menu.py','check-certified-reach-coverage.py','check-btn-flop-feedback.py','check-refined-conditional-study.py','check-big-blind-flop-discovery.py','check-resolvable-refined-lp.py']})
 return result
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();result=check();data=(json.dumps(result,indent=2)+'\n').encode('utf-8')
 if args.receipt:
  with args.receipt.open('xb') as output:output.write(data)
 print(data.decode('utf-8'))
