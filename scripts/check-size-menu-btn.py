"""Independent full four-hand BTN study, conditional witnesses and fresh policy gates."""
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
 spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module);return module
size=load('btn_size_parent','check-flop-size-menu.py')
btn=load('btn_size_face','check-btn-flop-feedback.py')
physical=size.base.physical
bounds=size.bounds
require,close,read,dot,vector=size.require,size.close,size.read,size.dot,size.vector
face,maximum_utility=btn.face,btn.maximum_utility
certificate=size.base.certificate
EPS=1e-8
BET='bet:0.25'
INTERVAL_ALGORITHM=size.base.ALGORITHM
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-size-menu-btn-quarter-request.json'
REPORT=DATA/'supplied-size-menu-btn-quarter-report.json.gz'
def endpoint_checks(actions,parent,tree,key,roots):
    baseline=parent['solve'];context=dict(root=tree,prior=parent['originalJointPrior'])
    certificates = 0
    for action in actions:
        audit = action['diagnostic']
        require(audit['algorithm'] == INTERVAL_ALGORITHM and audit['trainerAdmission'] is False
                and audit['baseline'] == baseline and audit['securitySlack'] == EPS and audit['requiredMinimumReach'] == .0001
                and audit['question'] == dict(hero=3, informationSet=key, action=action['action'])
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
            sequence = indices[initial.key, BET]
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
            weights = [p * policy[f'5:supplied-size-menu-preflop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call'] * policy[initial.key][BET]
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
    return certificates

def policy_check(actual, policy, expected_hash, source, tree):
    require(set(actual)=={'iterations','solutionHash','fullParentQuality','reachedFlopQuality','actualQuestionRootReach',
        'actualQuestionReachGivenPhysicalFlop','counterfactualQuestionReachGivenPhysicalFlop','globalHeroBestResponseBb',
        'globalHeroUpperBb','inclusionMarginBb','insideDeclaredFace','boundsChecked','maximumBoundViolationBb',
        'actionUtilitiesBb','actionLossBb','decisionRegretBb','failures'},'Policy assessment fields differ')
    require(actual['iterations']==policy['iterations'] and actual['solutionHash']==expected_hash,'Assessment policy identity differs')
    strategy=policy['strategy'];parent=source['parent'];q=source['request']
    full=physical.quality(tree,strategy);physical.compare_quality(actual['fullParentQuality'],full)
    local=actual['reachedFlopQuality'];roots=[];selected=[];raw=[];mass=0;actual_mass=0;cf=0
    for i,((_,preflop),world) in enumerate(zip(tree.edges,parent['originalJointPrior'],strict=True)):
        state=dict(preflop.edges)['call'].edges[0][1]
        key=f'5:supplied-size-menu-preflop:supplied-conditional-preflop:{world["dealtCombos"][5]}:'
        reached=world['conditionalProbability']*size.P*strategy[key]['call']
        if reached>0:
            roots.append((reached,state));raw.append(dict(originalDealIndex=i,jointRootFlopReach=reached));mass+=reached
        if world['dealtCombos'][3]==q['heroCards']:
            weight=world['conditionalProbability']*strategy[key]['call']*strategy[state.key][BET]
            selected.append((weight,dict(state.edges)[BET]));cf+=weight;actual_mass+=reached*strategy[state.key][BET]
    require(set(local)=={'scope','publicHistory','candidateRootFlopReach','physicalSupportFlopMass',
        'candidateBoundaryReachGivenPhysicalFlop','conditionalReachFloor','conditioning','rawWorldMasses','reachedWorlds','conditionalBestResponse'}
        and local['scope']=='CANDIDATE_POLICY_REACHED_FLOP_POSTERIOR_NOT_FULL_SECURITY_FACE/v1'
        and local['publicHistory']=='|BB:call' and local['conditionalReachFloor']==.0001,'Reached-flop scope differs')
    close(local['candidateRootFlopReach'],mass,'Reached boundary raw mass differs',mass=True)
    close(local['physicalSupportFlopMass'],size.P,'Physical event differs',mass=True)
    close(local['candidateBoundaryReachGivenPhysicalFlop'],mass/size.P,'Reached boundary units differ',mass=True)
    require(len(local['rawWorldMasses'])==len(raw),'Raw world inventory differs')
    for saved,wanted in zip(local['rawWorldMasses'],raw,strict=True):
        require(saved['originalDealIndex']==wanted['originalDealIndex'],'Raw world identity differs')
        close(saved['jointRootFlopReach'],wanted['jointRootFlopReach'],'Raw world mass differs',mass=True)
    supported=mass/size.P>=.0001
    require(local['conditioning']==('ABOVE_CANDIDATE_REACH_FLOOR' if supported else 'BELOW_CANDIDATE_REACH_FLOOR'),'Conditioning differs')
    local_quality=None
    if supported:
        require(len(local['reachedWorlds'])==len(raw),'Normalized reached inventory differs')
        for saved,wanted in zip(local['reachedWorlds'],raw,strict=True):
            require(saved['originalDealIndex']==wanted['originalDealIndex'],'Reached world identity differs')
            close(saved['jointRootFlopReach'],wanted['jointRootFlopReach'],'Reached raw world mass differs',mass=True)
            close(saved['conditionalWeight'],wanted['jointRootFlopReach']/mass,'Reached posterior differs',mass=True)
        root=size.Node(-1,edges=tuple((p/mass,node) for p,node in roots))
        local_quality=physical.quality(root,strategy);physical.compare_quality(local['conditionalBestResponse'],local_quality)
    else:
        require(local['reachedWorlds']==[] and local['conditionalBestResponse'] is None,'Unsupported posterior fabricated')
    close(actual['actualQuestionRootReach'],actual_mass,'Actual question mass differs',mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'],actual_mass/size.P,'Actual question units differ',mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'],cf,'Hero-past cancellation differs',mass=True)
    values={a['action']:physical.best_response(size.Node(-1,edges=tuple((p/cf,dict(node.edges)[a['action']]) for p,node in selected)),strategy,3) for a in source['actions']}
    losses={a:max(values.values())-v for a,v in values.items()}
    require(set(actual['actionUtilitiesBb'])==set(values)==set(actual['actionLossBb']),'Action coverage differs')
    for a in values:
        close(actual['actionUtilitiesBb'][a],values[a],'Forced optimal-continuation utility differs')
        close(actual['actionLossBb'][a],losses[a],'Forced action loss differs')
    upper=source['actions'][0]['diagnostic']['globalHeroUpperValue'];br=full[1][3];margin=upper+EPS-br
    close(actual['globalHeroBestResponseBb'],br,'Global security best response differs')
    close(actual['globalHeroUpperBb'],upper,'Original global upper differs')
    close(actual['inclusionMarginBb'],margin,'Security inclusion margin differs')
    included=margin>=-EPS
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is included,'Outside-face policy claims checked bounds')
    failures=[]
    if not included:failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
    if full[3]>.001:failures.append('FULL_PARENT_GAP')
    if local_quality is None or local_quality[3]>.001:failures.append('REACHED_FLOP_GAP')
    if actual_mass/size.P<.0001:failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf<.0001:failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    regret=math.fsum(strategy[source['informationSet']][a]*v for a,v in losses.items())
    close(actual['decisionRegretBb'],regret,'Selected decision regret differs')
    if regret>.01:failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation=max([0]+[v for a in source['actions'] for v in [a['lowerUtilityBb']-values[a['action']],values[a['action']]-a['upperUtilityBb'],a['lowerLossBb']-losses[a['action']],losses[a['action']]-a['upperLossBb']]])
        close(actual['maximumBoundViolationBb'],violation,'Interval bound violation differs')
        if violation>EPS:failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    else:require(actual['maximumBoundViolationBb'] is None,'Outside-face policy claims bound verification')
    require(actual['failures']==failures,'Complete policy rejection reasons differ')
    return failures

def check_values(request,report):
    request=copy.deepcopy(request);request['parent']['input']=physical.canonical_input(request['parent']['input']);request['parent']['flop']=sorted(request['parent']['flop'])
    require(set(request)=={'schemaVersion','parent','maximumLossBb'} and request['schemaVersion']=='pokerlab-size-menu-btn-quarter-request/v1' and request['maximumLossBb']==.01,'Declared BTN request differs')
    parent=report['parent'];parent_receipt=size.check_values(request['parent'],parent)
    require(parent==read(size.REPORT),'Shared physical parent changed')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','parent','cases','qualifiedIndices'} and report['schemaVersion']=='pokerlab-size-menu-btn-quarter-report/v1'
        and report['publicationStatus']=='SIZE_MENU_BTN_QUARTER_RESEARCH_ONLY' and report['trainerAdmission'] is False and report['request']==request,'BTN report identity differs')
    hands=sorted({w['dealtCombos'][3] for w in parent['originalJointPrior']})
    require(hands==['2c 3c','Ah Kh','As Qs','Qh Th'] and len(report['cases'])==4 and [c['heroCards'] for c in report['cases']]==hands and [c['index'] for c in report['cases']]==list(range(4)),'Complete BTN inventory differs')
    tree=size.literal_tree(parent,dict(binding=parent['binding']['physicalCarrier']['sourceBinding']),parent['request'])
    qualifications=[];certificates=0;assessments=0;outcomes=[];rejections=0
    for item in report['cases']:
        require(set(item)=={'index','heroCards','informationSet','context','endpoints','moves','primary','references','qualifiedForOfflineDrill','rejectionReasons'},'Case fields differ')
        key=f'3:supplied-size-menu-flop:{item["heroCards"]}:|BB:call:{size.BOARD}:{BET}'
        require(item['informationSet']==key and [e['action'] for e in item['endpoints']]==['fold','call'],'Owned BTN key/menu differs')
        h=parent['handoffs'][0];chips=list(h['committedBb']);chips[5]+=1.625
        players=[dict(seat=s,status='FOLDED' if h['folded'][i] else 'ACTING' if i==3 else 'ACTIVE',committedBb=chips[i],remainingStackBb=100-chips[i]) for i,s in enumerate(size.SEATS)]
        context=dict(actingSeat='BTN',board=list(size.FLOP),publicPreflopHistory='|BB:call',preflopHistory=h['completePreflopHistory'],flopHistory=[dict(seat='BB',action=BET,amountBb=1.625)],
            potBb=8.125,toCallBb=1.625,players=players,actions=[dict(action='fold',amountBb=0),dict(action='call',amountBb=1.625)])
        require(item['context']==context,'Literal BTN context differs')
        roots=[]
        for i,((_,pre),w) in enumerate(zip(tree.edges,parent['originalJointPrior'],strict=True)):
            if w['dealtCombos'][3]!=item['heroCards']:continue
            initial=dict(pre.edges)['call'].edges[0][1];reply=dict(initial.edges)[BET]
            require(reply.actor==3 and reply.key==key,'Owned path differs')
            roots.append((i,w['conditionalProbability'],initial,reply))
        require(len(roots)==6,'All six compatible worlds required')
        actions=[];failures=[]
        for endpoint in item['endpoints']:
            require(set(endpoint)=={'action','utility','rejection'} and (endpoint['utility'] is None)!=(endpoint['rejection'] is None),'Endpoint completeness differs')
            if endpoint['utility'] is None:
                rejection=endpoint['rejection'];rejections+=1
                require(set(rejection)=={'kind','reason','detail','reachFailure'} and rejection['kind'] in ['CONDITIONAL','LP','PARENT'] and rejection['reason'] in ['INSUFFICIENT_REACH','NUMERICAL_FAILURE','WORK_LIMIT','NO_CERTIFIED_OPTIMUM','INVALID_INPUT'] and isinstance(rejection['detail'],str),'Typed rejection differs')
                # A numerical rejection is retained data, never a claim of infeasibility or a bound.
                failures.append('ENDPOINT:'+endpoint['action']+':'+rejection['kind']+':'+rejection['reason']);continue
            u=endpoint['utility'];require(set(u)=={'lowerBb','upperBb','upperObjectiveShift','rawCertifiedReachLowerBound','rawCertifiedReachUpperBound','diagnostic'},'Utility fields differ')
            a=dict(action=endpoint['action'],lowerUtilityBb=u['lowerBb'],upperUtilityBb=u['upperBb'],upperObjectiveShift=u['upperObjectiveShift'],rawCertifiedReachLowerBound=u['rawCertifiedReachLowerBound'],rawCertifiedReachUpperBound=u['rawCertifiedReachUpperBound'],diagnostic=u['diagnostic'])
            certificates+=endpoint_checks([a],parent,tree,key,roots);actions.append(a)
        if failures:
            require(item['moves']==[] and item['primary'] is None and item['references']==[],'Incomplete menu acquired qualification')
        else:
            bestLo=max(a['lowerUtilityBb'] for a in actions);bestHi=max(a['upperUtilityBb'] for a in actions)
            moves=[]
            for a in actions:
                lo=max(0,bestLo-a['upperUtilityBb']);hi=max(0,bestHi-a['lowerUtilityBb']);a.update(lowerLossBb=lo,upperLossBb=hi)
                moves.append(dict(action=a['action'],lowerEvBb=a['lowerUtilityBb'],upperEvBb=a['upperUtilityBb'],lowerLossBb=lo,upperLossBb=hi,classification='WITHIN_LIMIT' if hi<=.01 else 'OUTSIDE_LIMIT' if lo>.01 else 'STRATEGY_DEPENDENT'))
            require(item['moves']==moves,'Complete move classification differs')
            if not any(m['classification']=='WITHIN_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
            if not any(m['classification']=='OUTSIDE_LIMIT' for m in moves):failures.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
            source=dict(parent=parent,request=dict(heroCards=item['heroCards']),informationSet=key,actions=actions)
            failures+=['PRIMARY:'+f for f in policy_check(item['primary'],parent['candidate'],parent['solutionHash'],source,tree)];assessments+=1
            require(len(item['references'])==2,'Reference inventory differs')
            for check,ref in zip(item['references'],parent['references'],strict=True):
                failures += [str(ref['policy']['iterations'])+':'+f for f in policy_check(check,ref['policy'],ref['policyHash'],source,tree)];assessments+=1
        failures=sorted(set(failures))
        require(item['rejectionReasons']==failures and item['qualifiedForOfflineDrill'] is (not failures),'Qualification bypasses a gate')
        if not failures:qualifications.append(item['index'])
        outcomes.append(dict(index=item['index'],hand=item['heroCards'],qualified=not failures,reasons=failures,moves=item['moves']))
    require(report['qualifiedIndices']==qualifications,'Qualified index inventory differs')
    return dict(schemaVersion='pokerlab-size-menu-btn-quarter-independent-check/v1',trainerAdmission=False,parentCheck=parent_receipt,handsChecked=4,actionAttemptsChecked=8,
        conditionalCertificatesChecked=certificates,policyAssessmentsChecked=assessments,retainedEndpointRejections=rejections,qualifiedIndices=qualifications,outcomes=outcomes,
        freshLpSolves=0,freshCfrRuns=0,qualificationAdditionalLpSolves=0,qualificationAdditionalCfrRuns=0)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def check(request_path=REQUEST,report_path=REPORT):
    result=check_values(read(request_path),read(report_path))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(request_path),reportStoredSha256=sha(report_path),immutableHelperSha256={n:sha(ROOT/'scripts'/n) for n in ['check-flop-size-menu.py','check-refined-conditional-study.py','check-btn-flop-feedback.py','check-big-blind-flop-discovery.py']})
    return result
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args();result=check();data=(json.dumps(result,indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as out:out.write(data)
    print(data.decode())
