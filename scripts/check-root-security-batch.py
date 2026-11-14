"""Independently check shared-parent root envelopes; no LP optimisation, CFR or poker replay."""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/check-root-sequence-batch.py'
HELPER_SHA = 'ecbdeebf354275f2a9a5653730369335a54d49ea513a89a6a0822a674e29d30b'
if hashlib.sha256(HELPER.read_bytes()).hexdigest() != HELPER_SHA:
    raise ValueError('Immutable original analytic checker changed')
spec = importlib.util.spec_from_file_location('security_batch_original_helpers', HELPER)
original = importlib.util.module_from_spec(spec); spec.loader.exec_module(original)
seq, old = original.seq, original.old
require, close, near, dot = old.require, old.close, old.near, old.dot
node, decision = seq.node, seq.decision
FIXTURE = ROOT / 'solver/src/test/resources/root-security-batch-analytic-controls.json.gz'
FIXTURE_SHA = '1e5a3f11df5f765b4e12318e790f91cd99b356abe0da73add0a47c9cb7849c15'
ALGORITHM = 'ROOT_SECURITY_ENVELOPE_SHARED_OWNED_PARENT_BATCH/v1'
SCOPE = 'CONSERVATIVE_FULL_MENU_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS_OR_TRAINER_ADMISSION/v1'
ENVELOPE = 'ROOT_FIXED_PURE_POLICY_GLOBAL_SECURITY_OUTER_ENVELOPE/v1'
ENVELOPE_SCOPE = 'CONSERVATIVE_OPTIMAL_CONTINUATION_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1'


def literal(hero):
    constant = 0. if hero == 0 else 7.
    def leaf(v):
        return node(-2, utilities=[v, constant-v] if hero == 0 else [constant-v, v])
    roots = []
    for sign in [1., -1.]:
        safe = decision(1-hero, 'opponent', ['x','y'], leaf(1.), leaf(1.))
        risk = decision(1-hero, 'opponent', ['x','y'], leaf(sign*10), leaf(0.))
        roots.append(decision(hero, 'type'+str(len(roots)), ['safe','risk'], safe, risk))
    return dict(playerCount=2, initialState=node(-1, probabilities=[.25,.75], children=roots))


def declared():
    result = []
    for h in range(2):
        qs = [dict(hero=h,informationSet=str(h)+':type'+str(i)) for i in range(2)]
        result.append(('oriented-'+str(h),literal(h),qs))
    qs = [dict(hero=3 if i%2 == 0 else 5,informationSet=str(3 if i%2 == 0 else 5)+':root-'+str(i)) for i in range(4)]
    return [*result,('mixed-six-seats',original.literal_mixed(),qs)]


def payoff(tree, hero, opponent, hf, of, constant):
    flows = {hero:hf,opponent:of}
    ids = {p:{(s['key'],s['action']):s['index'] for s in f['sequences'][1:]} for p,f in flows.items()}
    rows = {p:{r['key']:r for r in f['conservation']} for p,f in flows.items()}
    seen = {hero:set(),opponent:set()}; matrix = [[0.]*len(of['sequences']) for _ in hf['sequences']]
    def visit(n,hs=0,os=0,chance=1.):
        actor=n['actor']
        if actor == -2:
            close(n['utilities'][hero]+n['utilities'][opponent],constant)
            matrix[hs][os] += chance*n['utilities'][hero]; return
        if actor == -1:
            require(all(type(v) in (int,float) and math.isfinite(v) and v>0 for v in n['probabilities']),'Invalid chance')
            close(sum(n['probabilities']),1.)
            for p,c in zip(n['probabilities'],n['children'],strict=True):visit(c,hs,os,chance*p)
        else:
            require(actor in flows,'Unsupported actor')
            row=rows[actor][n['key']];seen[actor].add(n['key'])
            require(row['parentSequence']==(hs if actor==hero else os),'Own-history flow differs')
            require([ids[actor][n['key'],a] for a in n['actions']]==row['childSequences'],'Original menu differs')
            for a,c in zip(n['actions'],n['children'],strict=True):
                index=ids[actor][n['key'],a];visit(c,index if actor==hero else hs,index if actor==opponent else os,chance)
    visit(tree)
    require(all(seen[p]==set(rows[p]) for p in flows),'Extra flow information set')
    return matrix


def form(roots, hero, choices, of, op):
    ids={(s['key'],s['action']):s['index'] for s in of['sequences'][1:]}; coefficients=[0.]*len(of['sequences'])
    def visit(n,os=0,mass=1.):
        if n['actor']==-2:coefficients[os]+=mass*n['utilities'][hero];return
        if n['actor']==hero:
            visit(n['children'][n['actions'].index(choices[n['key']])],os,mass);return
        for i,c in enumerate(n['children']):
            visit(c,os if n['actor']==-1 else ids[n['key'],n['actions'][i]],mass*n['probabilities'][i] if n['actor']==-1 else mass)
    for mass,n in roots:visit(n,0,mass)
    return dict(constant=dot(coefficients,op['offset']),cost=[dot(coefficients,[row[i] for row in op['transform']]) for i in range(len(op['independentSequences']))])


def policy_hash(policy):
    # Match Java binary64 JSON notation for these pinned finite controls.
    def encode(v):
        if isinstance(v,dict):return '{'+','.join(json.dumps(k)+':'+encode(v[k]) for k in sorted(v))+'}'
        if type(v) is int:return str(v)
        require(type(v) is float and math.isfinite(v),'Finite policy number required')
        text=repr(v)
        if v==0:return '-0.0' if math.copysign(1,v)<0 else '0.0'
        if abs(v)<.001 or abs(v)>=1e7:
            from decimal import Decimal
            decimal=Decimal(text);digits=''.join(str(d) for d in decimal.as_tuple().digits)
            exponent=len(digits)+decimal.as_tuple().exponent-1
            return ('-' if v<0 else '')+digits[0]+'.'+(digits[1:] or '0')+'E'+str(exponent)
        if 'e' in text:
            from decimal import Decimal
            text=format(Decimal(text),'f')
        return text if '.' in text else text+'.0'
    return hashlib.sha256(encode(dict(iterations=1,strategy=policy)).encode()).hexdigest()


def envelope_behavior(flow):
    result={};real=flow['realization']
    for row in flow['conservation']:
        total=sum(real[i] for i in row['childSequences'])
        result[row['key']]={flow['sequences'][i]['action']:real[i]/total if total>0 else 1./len(row['childSequences']) for i in row['childSequences']}
    return result


def utilities(tree,policy):
    if tree['actor']==-2:return tree['utilities']
    ps=tree['probabilities'] if tree['actor']==-1 else [policy[tree['key']][a] for a in tree['actions']]
    values=[utilities(c,policy) for c in tree['children']]
    return [math.fsum(p*v[i] for p,v in zip(ps,values,strict=True)) for i in range(len(values[0]))]


def residual(flow,realization):
    return max([abs(realization[0]-1.),*[abs(math.fsum(realization[i] for i in row['childSequences'])-realization[row['parentSequence']]) for row in flow['conservation']]])


def work(c):
    original.certificate_work(c)
    return sum(c['work'][k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots']),c['work']['arithmeticWork']


def validate_control(control, expected):
    name,game,qs=expected
    require(set(control)=={'name','game','questions','audit'} and control['name']==name,'Control identity differs')
    seq.literal_equal(control['game'],game);seq.literal_equal(control['questions'],qs)
    a=control['audit'];parent=a['parent'];tree=game['initialState'];constant=0. if name=='oriented-0' else 7. if name=='oriented-1' else 4.
    require(a['algorithm']==ALGORITHM and a['scope']==SCOPE and a['trainerAdmission'] is False,'Batch scope differs')
    require(a['snapshotHash']==original.canonical(game)==parent['snapshotHash'],'Captured game binding differs')
    require(parent['algorithm']=='BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_REFINED_DIRECTIONS_ORIGINAL_CERTIFICATES/v2','Parent algorithm differs')
    close(parent['constantSum'],constant)
    f,s,fp,sp=(parent[k] for k in ['firstFlow','secondFlow','firstProjection','secondProjection'])
    first,second=parent['firstActor'],parent['secondActor'];require((first,second)==((0,1) if game['playerCount']==2 else (3,5)),'Actor orientation differs')
    old.projection(f,fp);old.projection(s,sp)
    projected=old.reduce_payoff(payoff(tree,first,second,f,s,constant),fp,sp)
    for k,v in projected.items():near(parent['projectedPayoff'][k],v)
    strategies={}
    for key,own,other,is_first,flow in [('first',fp,sp,True,f),('second',sp,fp,False,s)]:
        witness=a[key+'ParentWitness'];result=old.certificate(*old.parent_problem(projected,own,other,is_first),witness,old.PARENT_LP)
        require(witness['certificate']==parent[key+'Lp'],'Parent certificate binding differs');work(witness['certificate'])
        close(parent['lowerValue' if is_first else 'upperValue'],projected['constant']+(result if is_first else -result))
        real=[off+dot(row,witness['point'][:len(own['independentSequences'])]) for off,row in zip(own['offset'],own['transform'],strict=True)]
        near(flow['realization'],real);strategies.update(seq.behavior(flow,real))
    reduction=parent['reductionWork']
    require(set(reduction)=={'chargedUnits','limit'} and type(reduction['chargedUnits']) is int and 0<reduction['chargedUnits']<=8_000_000 and type(reduction['limit']) is int and reduction['limit']==8_000_000,'Parent compiler limits differ')
    require(parent['behavioralPolicyHash']==policy_hash(strategies),'Parent policy hash differs')
    quality=parent['behavioralQuality'];profile=utilities(tree,strategies)
    responses=[seq.best_response(tree,h,strategies) for h in range(game['playerCount'])]
    gains=[max(0.,br-value) for br,value in zip(responses,profile,strict=True)]
    near(quality['profileUtilitiesBb'],profile);near(quality['bestResponseUtilitiesBb'],responses)
    near(quality['deviationGainsBb'],gains);close(quality['nashConvBb'],sum(gains))
    for flow in [f,s]:close(flow['maximumResidual'],residual(flow,flow['realization']))
    require(len(a['envelopes'])==len(qs),'Incomplete batch')
    used=0;pivots=0;arithmetic=0;solves=0;w=a['work']
    expected_preflight=original.count_nodes(tree)*len(qs)
    require(w['preflightCompilerUnits']==expected_preflight,'Preflight work differs');used=expected_preflight
    for envelope,q in zip(a['envelopes'],qs,strict=True):
        e=envelope;h=q['hero'];o=second if h==first else first
        require(e['question']==q and e['baseline']==parent and e['algorithm']==ENVELOPE and e['scope']==ENVELOPE_SCOPE and e['trainerAdmission'] is False,'Envelope parent or scope differs')
        require(e['securitySlack']==1e-8 and e['fixedPolicyAlgorithm']=='OWNED_BASELINE_GREEDY_COMPLETE_HERO_POLICY/v1','Original envelope gates differ')
        hp,op,hf,of=(fp,sp,f,s) if h==first else (sp,fp,s,f)
        require(e['heroProjection']==hp and e['opponentProjection']==op,'Oriented projection differs')
        oriented=old.reduce_payoff(payoff(tree,h,o,hf,of,constant),hp,op)
        for k,v in oriented.items():near(e['orientedPayoff'][k],v)
        upper=parent['upperValue'] if h==first else constant-parent['lowerValue'];close(e['globalHeroUpperValue'],upper)
        choices={row['key']:max(strategies[row['key']],key=strategies[row['key']].get) for row in hf['conservation']}
        require(e['fixedHeroChoices']==choices,'Original greedy complete policy differs')
        roots=[];inside=set();outside=set()
        def scope(n,chance=1.,previous=False,selected=False):
            match=n['actor']==h and n['key']==q['informationSet']
            if match:require(not previous,'Question follows an action');selected=True;roots.append((chance,n))
            if n['actor']==h:(inside if selected else outside).add(n['key'])
            for i,c in enumerate(n['children']):scope(c,chance*n['probabilities'][i] if n['actor']==-1 else chance,previous or n['actor']>=0,selected)
        scope(tree);mass=sum(p for p,_ in roots)
        require(.0001<=mass<=1 and not inside.intersection(outside),'Invalid root scope or reach');close(e['questionChanceMass'],mass)
        require(len(e['support'])==len(roots),'Root support differs')
        for i,(support,(p,n)) in enumerate(zip(e['support'],roots,strict=True)):
            require(support['rootIndex']==i and support['stateHash']==original.canonical(n),'Root identity differs');close(support['chanceWeight'],p)
        normalized=[(p/mass,n) for p,n in roots];question=node(-1,probabilities=[p for p,_ in normalized],children=[n for _,n in normalized])
        global_form=form([(1.,tree)],h,choices,of,op);qform=form(normalized,h,choices,of,op)
        for field,expected_form in [('globalFixedPolicy',global_form),('questionFixedPolicy',qform)]:
            close(e[field]['constant'],expected_form['constant']);near(e[field]['cost'],expected_form['cost'])
        width=len(op['independentSequences']);z=len(hp['independentSequences']);d=len(hp['rhs'])
        matrix=[oriented['matrix'][i]+[-hp['inequalities'][j][i] for j in range(d)] for i in range(z)]
        matrix += [row+[0.]*d for row in op['inequalities']]+[oriented['secondCost']+hp['rhs']]
        rhs=[-v for v in oriented['firstCost']]+op['rhs']+[upper+1e-8-oriented['constant']]
        witnesses=[]
        def verify_witness(v,domain,problem,cost,linear,evaluated):
            require(v['domain']==domain,'Witness domain differs')
            objective=old.certificate(*problem,cost,v,old.LP);witnesses.append(v)
            real=[off+dot(row,v['point'][:width]) for off,row in zip(op['offset'],op['transform'],strict=True)]
            flow=v['opponentFlow'];require(flow['actor']==o and flow['sequences']==of['sequences'] and flow['conservation']==of['conservation'],'Witness flow identity differs')
            near(flow['realization'],real);seq.behavior(flow,real);policy=envelope_behavior(flow)
            close(flow['maximumResidual'],residual(flow,real))
            combined={**policy,**{row['key']:{hf['sequences'][i]['action']:1. if choices[row['key']]==hf['sequences'][i]['action'] else 0. for i in row['childSequences']} for row in hf['conservation']}}
            require(v['behavioralPolicyHash']==policy_hash(combined),'Witness policy hash differs')
            close(v['globalFixedHeroUtility'],old.value(tree,h,choices,policy));close(v['questionFixedHeroUtility'],old.value(question,h,choices,policy))
            fixed=old.value(evaluated,h,choices,policy);close(v['evaluatedFixedPolicyUtility'],fixed);close(fixed,linear['constant']+dot(linear['cost'],v['point'][:width]))
            local=seq.best_response(evaluated,h,policy);global_br=seq.best_response(tree,h,policy)
            close(v['optimalContinuationUtility'],local);close(v['globalHeroBestResponse'],global_br)
            require(local>=fixed-1e-8,'Optimal response below fixed policy')
            if domain=='FULL_OPPONENT_SECURITY_FACE':require(global_br<=upper+2e-8,'Witness leaves global face')
            return objective
        guarantee=e['guaranteeWitness'];qupper=e['questionUpperWitness']
        verify_witness(guarantee,'ALL_OPPONENT_FLOWS',(op['inequalities'],op['rhs']),[-v for v in global_form['cost']],global_form,tree)
        verify_witness(qupper,'FULL_OPPONENT_SECURITY_FACE',(matrix,rhs),qform['cost']+[0.]*d,qform,question)
        lower=global_form['constant']-guarantee['certificate']['dualValue']-1e-8
        fixed_upper=qform['constant']+qupper['certificate']['dualValue']+1e-8
        allowance=(upper+1e-8-lower)/mass;common=fixed_upper+allowance
        require(allowance>=0,'Negative security allowance')
        for k,v in dict(guaranteedGlobalLower=lower,fixedQuestionUpper=fixed_upper,securityAllowance=allowance,commonActionUpper=common).items():close(e[k],v)
        require([v['action'] for v in e['actions']]==roots[0][1]['actions'],'Incomplete menu')
        def ceiling(n):return n['utilities'][h] if n['actor']==-2 else max(ceiling(c) for c in n['children'])
        for action in e['actions']:
            forced=[(p,n['children'][n['actions'].index(action['action'])]) for p,n in normalized]
            conditional=node(-1,probabilities=[p for p,_ in forced],children=[n for _,n in forced]);linear=form(forced,h,choices,of,op)
            close(action['fixedContinuation']['constant'],linear['constant']);near(action['fixedContinuation']['cost'],linear['cost'])
            v=action['lowerWitness'];verify_witness(v,'FULL_OPPONENT_SECURITY_FACE',(matrix,rhs),[-v for v in linear['cost']]+[0.]*d,linear,conditional)
            lo=linear['constant']-v['certificate']['dualValue']-1e-8;cap=max(ceiling(n) for _,n in forced);hi=min(common,cap+1e-8)
            close(action['literalUtilityCeiling'],cap);close(action['lowerUtility'],lo);close(action['upperUtility'],hi)
            require(lo<=hi and lo-1e-8<=v['optimalContinuationUtility']<=hi+1e-8,'Envelope does not enclose witness')
        ew=e['work'];require(ew['compilerLimit']==8_000_000-used and type(ew['compilerUnits']) is int and 0<ew['compilerUnits']<=ew['compilerLimit'],'Compiler budget reset or invalid')
        ep,ea=map(sum,zip(*(work(v['certificate']) for v in witnesses),strict=True))
        require(ew['lpSolves']==len(witnesses) and ew['lpPivots']==ep and ew['lpArithmeticWork']==ea,'Endpoint work differs')
        used+=ew['compilerUnits'];pivots+=ep;arithmetic+=ea;solves+=len(witnesses)
        require(used<=8_000_000 and pivots<=10000 and arithmetic<=4_000_000_000,'Cumulative limits exceeded')
    expected_work=dict(parentSolveCalls=1,parentLpSolves=2,parentCompilerUnits=parent['reductionWork']['chargedUnits'],parentLpArithmeticWork=parent['firstLp']['work']['arithmeticWork']+parent['secondLp']['work']['arithmeticWork'],preflightCompilerUnits=expected_preflight,compilerUnits=used,compilerLimit=8_000_000,endpointLpSolves=solves,endpointPivots=pivots,endpointPivotLimit=10000,endpointArithmeticWork=arithmetic,endpointArithmeticLimit=4_000_000_000,enumeratedContinuationPlans=0)
    require(w==expected_work and all(type(v) is int for v in w.values()),'Batch inventory differs')
    return solves


def check_values(controls):
    require(type(controls) is list and len(controls)==3,'Complete analytic inventory required')
    endpoints=sum(validate_control(c,e) for c,e in zip(controls,declared(),strict=True))
    return dict(status='PASS_ANALYTIC_ROOT_SECURITY_BATCH_NOT_TRAINER_ADMISSION',batches=3,rootMenus=8,parentCertificates=6,endpointCertificates=endpoints,sharedParentLpSolves=6,standaloneParentLpSolves=16,avoidedParentLpSolves=10,physicalPokerGames=0,freshLpSolves=0,freshCfrRuns=0,trainerAdmission=False)


def check():
    controls,raw=old.read(FIXTURE);require(hashlib.sha256(raw).hexdigest()==FIXTURE_SHA,'Frozen capture byte pin differs')
    result=check_values(controls);result.update(fixtureSha256=FIXTURE_SHA,checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),immutableHelperSha256=HELPER_SHA)
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    result=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(result)
    print(result.decode())
