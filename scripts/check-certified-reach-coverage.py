"""Independently check the captured binary64 LP and v9 negative-reach witnesses.

No LP/CFR solve or trainer capability. Rebuild the physical parent once; derive the original
security face, all opponent paths, behavioral realization and full-parent best responses.
Positive endpoints retain the existing v8 checks after checking their explicit v9 identity.
"""
import argparse
import copy
from fractions import Fraction as F
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
HELPER=ROOT/'scripts/check-weak-range-flop-coverage.py'
spec=importlib.util.spec_from_file_location('certified_reach_coverage_helper',HELPER)
coverage=importlib.util.module_from_spec(spec);sys.modules[spec.name]=coverage;spec.loader.exec_module(coverage)
weak=coverage.weak
require,close,read,dot,vector=weak.require,weak.close,weak.read,weak.dot,weak.vector
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-certified-reach-flop-coverage-request.json'
REPORT=DATA/'supplied-certified-reach-flop-coverage-report.json.gz'
CAPTURE=DATA/'captured-near-zero-minimum-reach-lp.json'
BUDGET=DATA/'certified-reach-coverage-generation-budget.json'
ALGORITHM='OWNED_PHYSICAL_FLOP_RESOLVABLE_BASIS_CERTIFIED_NEGATIVE_REACH_ENDPOINTS/v9'
REJECTION='FULL_SECURITY_FACE_MINIMUM_REACH_CHILD_SUM_ORIGINAL_FLOW_SECURITY_CHECKS/v2'
EPS=1e-8
CAPTURE_SHA='7d0e232b3741c9be2f605caaa321590f4cdbbdd85dea08209025b2647f0fdf9f'


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def exact_certificate(matrix,rhs,cost,witness):
    # Fraction(float) checks the exact rational represented by each binary64, not decimal text.
    a=[[F(v) for v in row] for row in matrix];b=list(map(F,rhs));c=list(map(F,cost))
    x=list(map(F,witness['point']));y=list(map(F,witness['dual']))
    require(len(a)==len(b)==len(y) and len(c)==len(x) and all(len(row)==len(x) for row in a),'Exact LP dimensions differ')
    exact_dot=lambda u,v:sum((p*q for p,q in zip(u,v,strict=True)),F(0))
    primal=max([F(0)]+[-v for v in x]+[exact_dot(row,x)-rhs for row,rhs in zip(a,b,strict=True)])
    dual=max([F(0)]+[-v for v in y]+[c[j]-exact_dot([row[j] for row in a],y) for j in range(len(c))])
    gap=abs(exact_dot(c,x)-exact_dot(b,y))
    require(max(primal,dual,gap)<=F(EPS),'Exact original LP certificate failed')
    weak.certificate(matrix,rhs,cost,witness)
    return dict(primalViolation=float(primal),dualViolation=float(dual),dualityGap=float(gap))


def reconstruct(projection,variables):
    require(len(variables)==len(projection['independentSequences']),'Affine dimension differs')
    result=[]
    for offset,row in zip(projection['offset'],projection['transform'],strict=True):
        value=offset
        for coefficient,v in zip(row,variables,strict=True): value+=coefficient*v
        result.append(value)
    return result


def behavior(baseline,original,parent_policy):
    flow=baseline['secondFlow']
    require(len(original)==len(flow['sequences']) and all(math.isfinite(v) and -EPS<=v<=1+EPS for v in original),'Invalid original flow')
    point=[max(0.,v) for v in original]
    policy=copy.deepcopy(parent_policy);residual=abs(original[0]-1)
    for row in flow['conservation']:
        ids=row['childSequences'];total=sum(point[i] for i in ids)
        residual=max(residual,abs(sum(original[i] for i in ids)-original[row['parentSequence']]),abs(total-point[row['parentSequence']]))
        weights=[point[i]/total for i in ids] if total>0 else [1/len(ids)]*len(ids)
        policy[row['key']]={flow['sequences'][i]['action']:p for i,p in zip(ids,weights,strict=True)}
    require(residual<=EPS,'Original rejected flow conservation failed')
    implied={0:1.}
    # Resolve dependencies independently of the production information-set iteration order.
    pending=list(flow['conservation'])
    while pending:
        rest=[]
        for row in pending:
            if row['parentSequence'] not in implied: rest.append(row);continue
            for i in row['childSequences']:
                implied[i]=implied[row['parentSequence']]*policy[row['key']][flow['sequences'][i]['action']]
        require(len(rest)<len(pending),'Unresolvable original recall topology');pending=rest
    difference=max(abs(implied[i]-v) for i,v in enumerate(original))
    require(difference<=EPS,'Behavioral policy changes original realization')
    return policy,point,residual,difference


def check_rejection(item,parent,root):
    request=item['request'];hand=request['heroCards'];rejection=item['rejection']
    require(item['outcome']=='CONDITIONAL_REJECTED' and item['conditional'] is None and item['moves']==[]
        and set(rejection)=={'layer','reason','detail','reachFailure'} and rejection['layer']=='CONDITIONAL'
        and rejection['reason']=='INSUFFICIENT_REACH' and rejection['detail']=='Full security face question reach is not certified above the declared floor','Negative reach result differs')
    audit=rejection['reachFailure'];baseline=parent['solve'];witness=audit['minimumReachWitness']
    key=f'3:supplied-flop-one-bet:{hand}:|BB:call:8d Kc Kd:check'
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


def check(request_path=REQUEST,report_path=REPORT,*,bind_saved_bytes=True):
    request,request_bytes,_=read(request_path);report,raw,expanded=read(report_path)
    budget,_,_=read(BUDGET);capture,_,_=read(CAPTURE)
    require(digest(CAPTURE)==CAPTURE_SHA==budget['capturedLpSha256'],'Original captured bytes differ')
    require(hashlib.sha256(request_bytes).hexdigest()==budget['requestSha256'],'Predeclared v9 request differs')
    old_request,_,_=read(coverage.REQUEST);expected=copy.deepcopy(old_request)
    expected.update(schemaVersion='pokerlab-refined-flop-coverage-request/v2',intervalAlgorithm=ALGORITHM)
    require(request==expected,'Study selection or algorithm differs')
    declared=copy.deepcopy(request);declared['parent']['connected']['input']=weak.physical.canonical_input(request['parent']['connected']['input'])
    require(report['request']==declared and report['schemaVersion']=='pokerlab-refined-flop-coverage-report/v2'
        and report['publicationStatus']=='INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION'
        and report['trainerAdmission'] is False and len(report['cases'])==8,'v9 report identity differs')
    old,_,_=read(coverage.REPORT);parent=report['cases'][1]['conditional']['parent']
    root,parent_receipt=weak.checked_parent(parent,request['parent'])
    require(capture['baseline']==parent['solve'] and len(capture['cost'])==79 and len(capture['rhs'])==82,'Captured original parent differs')
    exact=exact_certificate(capture['matrix'],capture['rhs'],capture['cost'],capture['solved'])
    projection=capture['baseline']['secondProjection']
    original=reconstruct(projection,capture['solved']['point'][:len(projection['independentSequences'])])
    first=report['cases'][0]['rejection']['reachFailure'];matrix,rhs,width=weak.face(first,parent['solve'])
    require(capture['matrix']==matrix and capture['rhs']==rhs
        and capture['cost']==[-v for v in first['denominator']['cost']]+[0.]*(len(matrix[0])-width)
        and capture['solved']=={k:first['minimumReachWitness'][k] for k in ['certificate','point','dual']}
        and capture['question']==first['question'] and capture['minimumReach']==first['requiredMinimumReach']
        and capture['certifiedReachLower']==first['certifiedReachLowerBound'],'Original captured problem/point changed')
    violations=[]
    for row in capture['baseline']['secondFlow']['conservation']:
        mass=max(0.,original[row['parentSequence']])
        for child in row['childSequences']:
            ratio=max(0.,original[child])/mass if mass>1e-12 else 1/len(row['childSequences'])
            if ratio>1+EPS:violations.append(dict(key=row['key'],ratio=ratio,absoluteExcess=original[child]-mass))
    require(len(violations)==1 and violations[0]['absoluteExcess']==3.650294126530209e-17,'Captured old ratio boundary differs')
    rejected=[];certificates=0
    for index,item in enumerate(report['cases']):
        wanted=copy.deepcopy(old['cases'][index]['request']);wanted['intervalAlgorithm']=ALGORITHM
        require(item['index']==index and item['request']==wanted,'Full ordered plan differs')
        if index%2==0:rejected.append(check_rejection(item,parent,root));continue
        require(item['outcome']=='ACTION_SEPARATION' and item['rejection'] is None,'Positive result differs')
        normalized=copy.deepcopy(item);normalized['request']['intervalAlgorithm']=coverage.INTERVAL_ALGORITHM
        normalized['conditional']['schemaVersion']='pokerlab-refined-flop-conditional-report/v3'
        normalized['conditional']['request']['intervalAlgorithm']=coverage.INTERVAL_ALGORITHM
        for action in normalized['conditional']['actions']:action['diagnostic']['algorithm']=coverage.INTERVAL_ALGORITHM
        require(normalized==old['cases'][index],'Positive evidence changed beyond explicit identity')
        certificates+=coverage.check_solved(normalized['request'],normalized['conditional'],parent,root)
    require(report['counts']==old['counts'] and report['reservation']==old['reservation'],'Study counts/reservation differ')
    require(budget['physicalGameBuilds']==1 and budget['plannedCaseAttempts']==8 and budget['maximumActionComputations']==16
        and budget['maximumParentSolves']==24 and budget['freshReferenceRuns']==budget['automaticRetries']==0
        and budget['maximumReportExpandedBytes']==8388608 and budget['allOriginalNumericAndCompilerCapsPreserved'] is True
        and budget['noPositiveReachPosteriorConversionChanges'] is True and budget['failedInternalWorkCountersAvailable'] is False,'Generation budget differs')
    require(len(expanded)<=8388608,'Expanded report exceeds cap')
    if bind_saved_bytes:
        saved,_,_=read(DATA/'certified-reach-coverage-pins.json')
        require(saved==dict(storedSha256=hashlib.sha256(raw).hexdigest(),expandedSha256=hashlib.sha256(expanded).hexdigest()),'Frozen v9 report differs')
    return dict(schemaVersion='pokerlab-independent-certified-reach-coverage/v1',trainerAdmission=False,freshLpSolves=0,freshCfrRuns=0,
        negativeReachWitnessesChecked=4,positiveEndpointCertificatesChecked=certificates,
        capturedOriginalLpDimensions=[79,82],capturedExactCertificate=exact,oldRatioViolations=violations,
        rejectedCases=rejected,parentCheck=parent_receipt,noNewTrainerQuestions=True,
        requestSha256=hashlib.sha256(request_bytes).hexdigest(),reportStoredSha256=hashlib.sha256(raw).hexdigest(),
        reportExpandedSha256=hashlib.sha256(expanded).hexdigest(),checkerSha256=digest(Path(__file__)),helperSha256=digest(HELPER),budgetSha256=digest(BUDGET))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();require(not args.output.exists(),'Output must be new')
    result=check()
    with args.output.open('x',encoding='utf-8',newline='\n') as out:json.dump(result,out,indent=2);out.write('\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':main()
