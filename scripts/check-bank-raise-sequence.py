"""Independent two-hand physical raise minima, original flow certificates and best responses.

The endpoint verifier generalises the original physical complete-menu checker to
the pinned sevens/AJs roots. It reconstructs the payoff and information-set best
response for each hand; the earlier nines-only pure-plan oracle is not reused.
No optimizer or CFR is run. Parent face identity is checked against the independently
verified original sequence study, which uses the same physical game and actor.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
def module(name, filename):
    spec=importlib.util.spec_from_file_location(name, ROOT/'scripts'/filename)
    value=importlib.util.module_from_spec(spec);sys.modules[name]=value;spec.loader.exec_module(value);return value
IMMUTABLE_HELPERS={'check-physical-root-sequence-batch.py': 'f4ac141bd1d8debbd046818416713d11f7cabfc45ee41a2c353198110f0fbb04', 'check-root-hand-bank.py': 'e2ffd071b195d26a44c0339958ac51eac57a8c89c2d54c89e7b0d7f2faff9893'}
for helper,pin in IMMUTABLE_HELPERS.items():
    if hashlib.sha256((ROOT/'scripts'/helper).read_bytes()).hexdigest()!=pin:
        raise ValueError('Original independent helper changed: '+helper)
physical=module('bank_raise_physical_batch','check-physical-root-sequence-batch.py')
bank_checker=module('bank_raise_original_bank','check-root-hand-bank.py')
sequence,old=physical.sequence,physical.old
require,close,near,dot=physical.require,physical.close,physical.near,physical.dot
behavior,value,best_response,node=physical.behavior,physical.value,physical.best_response,physical.node
physical_payoff=physical.physical_payoff
EPS=1e-8
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-bank-raise-sequence-request.json'
REPORT=DATA/'supplied-bank-raise-sequence-report.json.gz'
BANK_SHA='eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd'
BANK_HASH='e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d'
read=physical.read

def context():
    require(hashlib.sha256(bank_checker.REPORT.read_bytes()).hexdigest()==BANK_SHA,'Original bank bytes differ')
    bank=read(bank_checker.REPORT);bank_proof=bank_checker.check_values(read(bank_checker.REQUEST),bank)
    original,legacy,previous_proof=physical.context()
    source_context,_,_,tree=original
    require(source_context[1]==bank['parent'],'Shared physical parent differs')
    return bank,tree,legacy,bank_proof,previous_proof

def endpoint(a, assessment, parent, tree, legacy_report, remaining):
    h, o = 5, 3
    action = "raise:9.0"
    require(a['algorithm'] == sequence.ALGORITHM and a['scope'] == sequence.SCOPE and a['trainerAdmission'] is False, 'Sequence minimum scope differs')
    require(a['question'] == dict(hero=h, informationSet=assessment['envelope']['question']['informationSet'], action=action), 'Original physical root question differs')
    require(a['requiredMinimumReach'] == .0001 and a['securitySlack'] == EPS, 'Original physical gates differ')
    base = a['baseline']; require(base == parent['solve'] and base['firstActor'] == 3 and base['secondActor'] == 5
            and a['firstParentWitness'] == parent['firstLpWitness'] and a['secondParentWitness'] == parent['secondLpWitness']
            and a['securityFaceHash'] == legacy_report['minimum']['securityFaceHash'], 'Original physical parent and face identity differ')
    f, s, fp, sp = (base[k] for k in ['firstFlow', 'secondFlow', 'firstProjection', 'secondProjection'])
    require(f['actor'] == 3 and s['actor'] == 5, 'Original flow actor labels differ')
    old.projection(f, fp); old.projection(s, sp)
    parent_payoff = old.reduce_payoff(physical_payoff(tree, 3, 5, f, s, base['constantSum']), fp, sp)
    for key, expected in parent_payoff.items(): near(base['projectedPayoff'][key], expected)
    for witness, own, other, first, flow in [(a['firstParentWitness'], fp, sp, True, f), (a['secondParentWitness'], sp, fp, False, s)]:
        optimum = old.certificate(*old.parent_problem(parent_payoff, own, other, first), witness, old.PARENT_LP)
        require(witness['certificate'] == base['firstLp' if first else 'secondLp'], 'Parent witness binding differs')
        close(base['lowerValue' if first else 'upperValue'], parent_payoff['constant']+(optimum if first else -optimum))
        realization = [off+dot(row, witness['point'][:len(own['independentSequences'])]) for off, row in zip(own['offset'], own['transform'], strict=True)]
        near(flow['realization'], realization); behavior(flow, realization)
    hp, op, hf, of = (fp, sp, f, s) if h == 3 else (sp, fp, s, f)
    require(a['heroProjection'] == hp and a['opponentProjection'] == op, 'Oriented projection differs')
    oriented = old.reduce_payoff(physical_payoff(tree, h, o, hf, of, base['constantSum']), hp, op)
    for key, expected in oriented.items(): near(a['orientedPayoff'][key], expected)
    upper = base['upperValue'] if h == 3 else base['constantSum']-base['lowerValue']; close(a['globalHeroUpperValue'], upper)
    w, z, d = len(op['independentSequences']), len(hp['independentSequences']), len(hp['rhs'])
    face = [oriented['matrix'][i]+[-hp['inequalities'][j][i] for j in range(d)] for i in range(z)]
    face += [row+[0.]*d for row in op['inequalities']]+[oriented['secondCost']+hp['rhs']]
    face_rhs = [-v for v in oriented['firstCost']]+op['rhs']+[upper+a['securitySlack']-oriented['constant']]
    roots = []
    def collect(n, chance=1., previous=False):
        if n['actor'] == h and n['key'] == a['question']['informationSet']:
            require(not previous, 'Question follows a player action'); roots.append((chance, n)); return
        for i, child in enumerate(n['children']): collect(child, chance*n['probabilities'][i] if n['actor'] == -1 else chance, previous or n['actor'] >= 0)
    collect(tree); mass = sum(p for p, _ in roots); close(a['originalQuestionReach'], mass)
    require(.0001 <= mass <= 1 and len(a['support']) == len(roots), 'Root support differs')
    forest = []
    require(a['support'] == [dict(originalChanceWeight=s['chanceWeight'], posteriorProbability=s['chanceWeight']/mass, stateHash=s['stateHash']) for s in assessment['envelope']['support']], 'Original private-hand root support differs')
    for (probability, n), support in zip(roots, a['support'], strict=True):
        close(support['originalChanceWeight'], probability); close(support['posteriorProbability'], probability/mass)
        forest.append((probability/mass, n['children'][n['actions'].index(action)]))
    conditional = node(-1, probabilities=[p for p, _ in forest], children=[n for _, n in forest])
    ids = {p: {(s['key'], s['action']): s['index'] for s in flow['sequences'][1:]} for p, flow in [(h, hf), (o, of)]}
    forced = ids[h][a['question']['informationSet'], action]; grid = [[0.]*len(of['sequences']) for _ in hf['sequences']]
    def compile_payoff(n, hs, os, probability):
        if n['actor'] == -2: grid[hs][os] += probability*n['utilities'][h]; return
        for i, child in enumerate(n['children']):
            actor = n['actor']; seq = ids[actor][n['key'], n['actions'][i]] if actor >= 0 else None
            compile_payoff(child, seq if actor == h else hs, seq if actor == o else os, probability*n['probabilities'][i] if actor == -1 else probability)
    for p, n in forest: compile_payoff(n, forced, 0, p)
    local = old.reduce_payoff(grid, hp, op)
    for key, expected in local.items(): near(a['conditionalPayoff'][key], expected)
    restricted = hp['inequalities']+[hp['transform'][forced], [-v for v in hp['transform'][forced]]]
    restricted_rhs = hp['rhs']+[1-hp['offset'][forced], hp['offset'][forced]-1]
    require(a['heroDomain'] == dict(forcedSequence=forced, matrix=restricted, rhs=restricted_rhs), 'Forced original hero domain differs')
    columns = w+d+len(restricted); rows = len(face)+z
    matrix = [row+[0.]*len(restricted) for row in face]
    matrix += [local['matrix'][i]+[0.]*d+[-row[i] for row in restricted] for i in range(z)]
    rhs = face_rhs+[-v for v in local['firstCost']]
    cost = [-v for v in local['secondCost']]+[0.]*d+[-v for v in restricted_rhs]
    p = a['problem']; near(p['matrix'], matrix); near(p['rhs'], rhs); near(p['cost'], cost)
    close(p['objectiveConstant'], local['constant'])
    require(p['securityRows'] == len(face) and p['opponentVariables'] == w and p['globalDualVariables'] == d and p['continuationDualVariables'] == len(restricted), 'LP block inventory differs')
    require(0 < rows <= 512 and 0 < columns <= 512, 'Original LP dimension cap exceeded')
    witness = a['witness']
    certificate_work = witness['certificate']['work']
    require(type(certificate_work['blandFallback']) is bool
            and all(type(v) is int for k, v in certificate_work.items() if k != 'blandFallback'), 'Integer certificate work required')
    old.certificate(matrix, rhs, cost, witness, old.LP)
    def flow_values(projection, variables):
        raw = [off+dot(row, variables) for off, row in zip(projection['offset'], projection['transform'], strict=True)]
        require(all(v >= -old.EPS for v in raw), 'Invalid original projected realization')
        return [max(0., v) for v in raw]
    original_opponent = flow_values(op, witness['point'][:w]); original_hero = flow_values(hp, witness['dual'][len(face):])
    policies = {}
    for record, original, flow in [(witness['opponentFlow'], original_opponent, of), (a['continuationFlow'], original_hero, hf)]:
        require(record['actor'] == flow['actor'] and record['sequences'] == flow['sequences'] and record['conservation'] == flow['conservation'], 'Original witness flow identity differs')
        near(record['realization'], original); close(record['maximumResidual'], 0.)
        policies.update(behavior(flow, original))
    close(original_hero[forced], 1.)
    require(witness['behavioralPolicyHash'] == a['continuationPolicyHash'], 'Joint witness identity differs')
    local_br = best_response(conditional, h, policies); global_br = best_response(tree, h, policies)
    close(witness['globalHeroBestResponse'], global_br); close(witness['conditionalHeroBestResponse'], local_br)
    close(a['continuationUtility'], value(conditional, policies)[h]); close(a['continuationUtility'], local_br)
    require(global_br <= upper+a['securitySlack']+old.EPS, 'Opponent witness escapes the global security face')
    cert = witness['certificate']; objective = local['constant']-cert['primalValue']
    close(objective, local_br); close(a['minimum']['witnessUtility'], local_br)
    close(a['minimum']['lowerBound'], local['constant']-cert['dualValue']-old.EPS)
    close(a['minimum']['upperBound'], max(objective, local_br)+old.EPS)
    work = a['work']; require(all(type(v) is int for v in work.values()), 'Integer work counters required')
    require(work['compilerLimit'] == remaining and 0 < work['compilerUnits'] <= work['compilerLimit'], 'Compiler work differs')
    require(work['enumeratedContinuationPlans'] == 0 and work['endpointLpSolves'] == 1, 'Unexpected plan enumeration or endpoint solve count')
    require(work['endpointArithmeticWork'] == cert['work']['arithmeticWork'] and work['endpointPivots'] == sum(cert['work'][k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']), 'Endpoint work totals differ')

    return dict(parentCertificatesChecked=2, endpointCertificatesChecked=1, originalHeroSequences=len(hf['sequences']), originalOpponentSequences=len(of['sequences']), variables=columns, constraints=rows)

def check_values(request, report, original=None):
    bank,tree,legacy,bank_proof,prior_proof=context() if original is None else original
    bank_checker.finite(request);bank_checker.finite(report)
    expected_request=dict(schemaVersion='pokerlab-bank-raise-sequence-request/v1',bankStoredSha256=BANK_SHA,
                          bankStudyHash=BANK_HASH,heroCards=['7d 7h','Ac Jc'],action='raise:9.0')
    require(bank_checker.equal(request,expected_request),'Declared original two-hand raise request differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','minimumScope','sourceBank',
                          'parent','batch','rejection','comparisons','policySupports','completeMinima','referenceSupportedMinima','rejectionReasons'},'Complete report fields required')
    require(report['schemaVersion']=='pokerlab-bank-raise-sequence-report/v1'
            and report['publicationStatus']=='BANK_RAISE_SEQUENCE_MINIMA_RESEARCH_ONLY' and report['trainerAdmission'] is False
            and bank_checker.equal(report['request'],request) and report['minimumScope']==sequence.SCOPE
            and bank_checker.equal(report['sourceBank'],bank) and bank_checker.equal(report['parent'],bank['parent']),
            'Pinned original physical source, scope or parent differs')
    b=report['batch'];rejection=report['rejection'];comparisons=[];supports=[];reasons=set();certificates=[]
    if b is None:
        require(isinstance(rejection,dict) and set(rejection)=={'kind','reason','detail'} and rejection['kind'] in ['LP','PARENT','MINIMUM','STRUCTURE']
                and isinstance(rejection['reason'],str) and isinstance(rejection['detail'],str),'Typed rejected batch required')
        reasons.add('BATCH:'+rejection['kind']+':'+rejection['reason'])
    else:
        require(rejection is None and b['algorithm']==physical.ALGORITHM and b['scope']==physical.SCOPE and b['trainerAdmission'] is False
                and b['snapshotHash']==bank['parent']['solve']['snapshotHash'] and len(b['minima'])==2,'Complete ordered research batch required')
        w=b['work'];require(all(type(v) is int for v in w.values()),'Integer cumulative work required')
        require(w['parentSolveCalls']==1 and w['parentLpSolves']==2 and w['endpointLpSolves']==2 and w['enumeratedContinuationPlans']==0
                and w['compilerLimit']==8000000 and w['endpointPivotLimit']==10000 and w['endpointArithmeticLimit']==4000000000,
                'Original cumulative limits and shared-parent inventory differ')
        parent=bank['parent'];compiler=2*parent['solve']['treeNodes'];arithmetic=pivots=0
        require(w['preflightCompilerUnits']==compiler and w['parentCompilerUnits']==parent['solve']['reductionWork']['chargedUnits']
                and w['parentLpArithmeticWork']==sum(parent['solve'][k]['work']['arithmeticWork'] for k in ['firstLp','secondLp']),
                'Physical parent and preflight work differs')
        for a,index,hand in zip(b['minima'],[0,2],['7d 7h','Ac Jc'],strict=True):
            assessment=bank['assessments'][index]
            certificates.append(endpoint(a,assessment,parent,tree,legacy,8000000-compiler))
            compiler+=a['work']['compilerUnits'];arithmetic+=a['work']['endpointArithmeticWork'];pivots+=a['work']['endpointPivots']
            old_action=next(m for m in assessment['envelope']['actions'] if m['action']=='raise:9.0')
            c=dict(sourceQuestionIndex=index,heroCards=hand,originalLowerUtilityBb=old_action['lowerUtility'],originalUpperUtilityBb=old_action['upperUtility'],
                   minimumLowerBb=a['minimum']['lowerBound'],minimumUpperBb=a['minimum']['upperBound'],
                   lowerBoundImprovementBb=a['minimum']['lowerBound']-old_action['lowerUtility'])
            comparisons.append(c)
            if c['lowerBoundImprovementBb'] < -EPS:reasons.add('WEAKER_LOWER_BOUND:'+str(index))
            for policy in assessment['policies']:
                violation=max(0,a['minimum']['lowerBound']-policy['actionUtilitiesBb']['raise:9.0'])
                supported=policy['insideDeclaredFace'] and not policy['failures'] and violation <= EPS
                supports.append(dict(sourceQuestionIndex=index,iterations=policy['iterations'],solutionHash=policy['solutionHash'],
                                     minimumLowerViolationBb=violation,insideDeclaredFace=policy['insideDeclaredFace'],supported=supported))
                if not supported:reasons.add('REFERENCE_BELOW_MINIMUM_OR_OUTSIDE_FACE:'+str(index)+':'+str(policy['iterations']))
        require((w['compilerUnits'],w['endpointArithmeticWork'],w['endpointPivots'])==(compiler,arithmetic,pivots)
                and compiler<=8000000 and arithmetic<=4000000000 and pivots<=10000,'Cumulative endpoint work differs')
    require(bank_checker.equal(report['comparisons'],comparisons) and bank_checker.equal(report['policySupports'],supports)
            and report['completeMinima'] is (b is not None) and report['referenceSupportedMinima'] is (b is not None and not reasons)
            and report['rejectionReasons']==sorted(reasons),'Original-hand comparisons, policy support or rejection inventory differs')
    return dict(schemaVersion='pokerlab-bank-raise-sequence-independent-check/v1',trainerAdmission=False,gradingAuthority=False,
                completeMinima=b is not None,referenceSupportedMinima=b is not None and not reasons,
                originalPrivateHands=['7d 7h','Ac Jc'],endpointCertificatesChecked=len(certificates),distinctBatchParentCertificatesChecked=2 if b else 0,
                policyLowerBoundChecks=len(supports),freshLpSolves=0,freshCfrRuns=0,fullActionUpperEndpointClaim=False,
                coherentMinimumPolicyClaim=False,comparisons=comparisons,endpoints=certificates,sourceBankCheck=bank_proof,originalFaceCheck=prior_proof)

def check():
    result=check_values(read(REQUEST),read(REPORT))
    result.update(checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(),reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),
                  originalBankStoredSha256=BANK_SHA,
                  immutableHelperSha256=IMMUTABLE_HELPERS)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    raw=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(raw)
    print(raw.decode())
