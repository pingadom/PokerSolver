"""Independent complete three-hand bank physics, certificates and policy checks; no LP/CFR."""
import argparse
import copy
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('root_bank_sevens_helpers',ROOT/'scripts/check-sevens-root-study.py')
sevens=importlib.util.module_from_spec(spec);sys.modules[spec.name]=sevens;spec.loader.exec_module(sevens)
legacy,source=sevens.legacy,sevens.source
require,close=sevens.require,sevens.close
size,physical,bounds,base=sevens.size,sevens.physical,sevens.bounds,sevens.base
form,check_form,witness=sevens.form,sevens.check_form,sevens.witness
ALGORITHM,SCOPE,POLICY,EPS=sevens.ALGORITHM,sevens.SCOPE,sevens.POLICY,sevens.EPS
read,sha,finite,equal=sevens.read,sevens.sha,sevens.finite,sevens.equal
DATA=ROOT/'docs/data'
REQUEST=DATA/'supplied-root-hand-bank-request.json'
REPORT=DATA/'supplied-root-hand-bank-report.json.gz'
HANDS=['7d 7h','9d 9h','Ac Jc']
BUDGET=dict(maximumQuestions=3,physicalGames=1,maximumParentCalls=4,maximumParentLps=8,
            maximumEnvelopeLps=15,freshReferenceRuns=2,freshIterations=240000,
            envelopeCompilerUnits=24000000,envelopePivots=30000,envelopeArithmetic=12000000000)
HELPERS=['check-sevens-root-study.py',*sevens.HELPERS]

def check_envelope(parent,report,tree,parent_receipt):
    key = '5:supplied-size-menu-preflop:supplied-conditional-preflop:'+report['heroCards']+':'
    roots, inside, outside = [], set(), set()
    def scope(node, chance=1., previous=False, selected=False):
        if node.actor == 5 and node.key == key:
            require(not previous, 'Question follows a player action'); selected = True; roots.append((chance, node))
        if node.actor == 5:
            (inside if selected else outside).add(node.key)
        for a, child in node.edges:
            scope(child, chance*a if node.actor == -1 else chance, previous or node.actor >= 0, selected)
    scope(tree)
    require(len(roots) == 8 and not inside.intersection(outside), 'Root deviation is not separable')
    mass = math.fsum(p for p, _ in roots); close(mass, 1/3, 'Original root chance differs')
    audit = report['envelope']
    require(audit['algorithm'] == ALGORITHM and audit['scope'] == SCOPE and audit['trainerAdmission'] is False
            and audit['question'] == dict(hero=5, informationSet=key) and audit['baseline'] == parent['solve']
            and audit['fixedPolicyAlgorithm'] == POLICY and audit['securitySlack'] == EPS,
            'Envelope provenance/scope differs')
    close(audit['questionChanceMass'], mass, 'Root chance was scaled or replaced')
    require(len(audit['support']) == len(roots), 'Root support incomplete')
    for i, (s, (p, _)) in enumerate(zip(audit['support'], roots, strict=True)):
        require(s['rootIndex'] == i and len(s['stateHash']) == 64, 'Root identity differs')
        close(s['chanceWeight'], p, 'Original root chance weight differs')
    chips = [0, 0, 0, 3, .5, 1]
    players = [dict(seat=s, status='FOLDED' if i in (0, 1, 2, 4) else 'ACTING' if i == 5 else 'ACTIVE',
                    committedBb=chips[i], remainingStackBb=100-chips[i]) for i, s in enumerate(size.SEATS)]
    context = dict(actingSeat='BB', publicHistory='', history=parent['handoffs'][0]['completePreflopHistory'][:-1],
                   potBb=4.5, toCallBb=2., players=players,
                   actions=[dict(action=a, amountBb=v) for a, v in [('fold', 0.), ('call', 2.), ('raise:9.0', 8.)]])
    actions = ['fold', 'call', 'raise:9.0']
    require(report['context'] == context and [a['action'] for a in audit['actions']] == actions
            and all([a for a, _ in n.edges] == actions for _, n in roots), 'Complete root menu/context differs')
    choices = {}
    for row in parent['solve']['secondFlow']['conservation']:
        moves = [parent['solve']['secondFlow']['sequences'][i]['action'] for i in row['childSequences']]
        choices[row['key']] = max(moves, key=lambda a: parent['candidate']['strategy'][row['key']][a])
    require(audit['fixedHeroChoices'] == choices and len(choices) == 33, 'Complete greedy pure policy differs')
    normalized = [(p/mass, n) for p, n in roots]
    question = size.Node(-1, edges=tuple(normalized))
    op = audit['opponentProjection']; opponent = parent['solve']['firstFlow']
    global_form = form([(1., tree)], choices, opponent, op)
    question_form = form(normalized, choices, opponent, op)
    check_form(audit['globalFixedPolicy'], global_form); check_form(audit['questionFixedPolicy'], question_form)
    matrix, rhs, width = bounds.face(audit, parent['solve']); n = len(matrix[0])
    guarantee, upper = audit['guaranteeWitness'], audit['questionUpperWitness']
    base.certificate(op['inequalities'], op['rhs'], [-v for v in global_form['cost']], guarantee)
    base.certificate(matrix, rhs, question_form['cost']+[0]*(n-width), upper)
    lower = global_form['constant']-guarantee['certificate']['dualValue']-EPS
    fixed_upper = question_form['constant']+upper['certificate']['dualValue']+EPS
    allowance = (audit['globalHeroUpperValue']+EPS-lower)/mass
    require(allowance >= 0, 'Negative global guarantee allowance')
    common = fixed_upper+allowance
    for field, value in [('guaranteedGlobalLower', lower), ('fixedQuestionUpper', fixed_upper),
                         ('securityAllowance', allowance), ('commonActionUpper', common)]:
        close(audit[field], value, 'Conservative envelope formula differs: '+field)
    witness(guarantee, audit, parent, tree, question, tree, global_form, 'ALL_OPPONENT_FLOWS')
    require(witness(upper, audit, parent, tree, question, question, question_form,
                    'FULL_OPPONENT_SECURITY_FACE') <= common+EPS, 'Common bound excludes optimal response')
    witnesses = [guarantee, upper]
    def ceiling(node):
        return node.utilities[5] if node.actor == -2 else max(ceiling(c) for _, c in node.edges)
    for action in audit['actions']:
        forced = [(p, dict(node.edges)[action['action']]) for p, node in normalized]
        f = form(forced, choices, opponent, op); check_form(action['fixedContinuation'], f)
        w = action['lowerWitness']; witnesses.append(w)
        base.certificate(matrix, rhs, [-v for v in f['cost']]+[0]*(n-width), w)
        lo = f['constant']-w['certificate']['dualValue']-EPS
        cap = max(ceiling(node) for _, node in forced); hi = min(common, cap+EPS)
        close(action['literalUtilityCeiling'], cap, 'Literal terminal ceiling differs')
        close(action['lowerUtility'], lo, 'Padded fixed-policy lower differs')
        close(action['upperUtility'], hi, 'Padded conservative upper differs')
        require(math.isfinite(lo) and math.isfinite(hi) and lo <= hi, 'Invalid action bounds')
        optimal = witness(w, audit, parent, tree, question, size.Node(-1, edges=tuple(forced)), f,
                          'FULL_OPPONENT_SECURITY_FACE')
        require(lo-EPS <= optimal <= hi+EPS, 'Bounds exclude optimal continuation')
    work = audit['work']
    require(work['lpSolves'] == len(witnesses) == 5
            and work['lpArithmeticWork'] == sum(w['certificate']['work']['arithmeticWork'] for w in witnesses) <= 4_000_000_000
            and work['lpPivots'] == sum(sum(w['certificate']['work'][k] for k in
                ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) for w in witnesses) <= 10000
            and 0 <= work['compilerUnits'] <= work['compilerLimit'] == 8_000_000, 'Shared work budget differs')
    return dict(schemaVersion='pokerlab-bank-envelope-independent-check/v1', trainerAdmission=False,
                scope=SCOPE, parentCheck=parent_receipt, originalEnvelopeCertificatesChecked=5,
                originalRootWorlds=8, completePureHeroRows=33, checkedActions=actions,
                completeOuterBounds=True, attainedEndpointClaims=False, gradingAuthority=False,
                work=work, freshLpSolves=0, freshCfrRuns=0)

def check_hand(parent,report,tree,parent_receipt):
    require(set(report)=={'questionIndex','heroCards','context','envelope','rejection','moves','policies',
                         'completeOuterBounds','feedbackSupported','rejectionReasons'},'Hand outcome fields differ')
    require(equal(report['context'],sevens.context(parent)),'Original six-seat context differs')
    if report['completeOuterBounds'] is False:
        failure=report['rejection']
        require(report['envelope'] is None and type(failure) is dict
                and set(failure)=={'kind','reason','detail'}
                and failure['kind'] in ('LP','PARENT','ENVELOPE')
                and failure['reason'] in {'LP':{'INVALID_INPUT','WORK_LIMIT','NUMERICAL_FAILURE','NO_CERTIFIED_OPTIMUM'},
                    'PARENT':{'INVALID_INPUT','WORK_LIMIT','NUMERICAL_FAILURE'},'ENVELOPE':{'NUMERICAL_VERIFICATION'}}[failure['kind']]
                and all(isinstance(failure[k],str) and failure[k].strip() for k in ('reason','detail'))
                and report['moves']==[] and report['policies']==[] and report['feedbackSupported'] is False
                and report['rejectionReasons']==['INCOMPLETE_ROOT_ENVELOPE'],'Rejected hand cannot claim feedback')
        return dict(heroCards=report['heroCards'],completeOuterBounds=False,feedbackSupported=False,
                    retainedTypedFailure=failure,freshLpSolves=0,freshCfrRuns=0)
    require(report['completeOuterBounds'] is True and report['rejection'] is None,'Complete hand envelope required')
    receipt=check_envelope(parent,report,tree,parent_receipt);a=report['envelope'];expected=[]
    for action in a['actions']:
        lo=max(0.,max(y['lowerUtility']-action['upperUtility'] for y in a['actions']))
        hi=max(0.,max(y['upperUtility']-action['lowerUtility'] for y in a['actions']))
        expected.append(dict(action=action['action'],lowerEvBb=action['lowerUtility'],upperEvBb=action['upperUtility'],
                             lowerLossBb=lo,upperLossBb=hi,classification=legacy.classify(lo,hi,.01)))
    require(len(report['moves'])==3 and len(report['policies'])==3,'Complete menu and both references required')
    for actual,wanted in zip(report['moves'],expected,strict=True):
        require(actual.keys()==wanted.keys() and actual['action']==wanted['action'] and actual['classification']==wanted['classification'],
                'Move identity or classification differs')
        for key in ['lowerEvBb','upperEvBb','lowerLossBb','upperLossBb']:close(actual[key],wanted[key],'Conservative bounds differ')
    roots=[(w['conditionalProbability'],n) for (_,n),w in zip(tree.edges,parent['originalJointPrior'],strict=True) if n.key==a['question']['informationSet']]
    policies=[parent['candidate'],*[r['policy'] for r in parent['references']]]
    hashes=[parent['solutionHash'],*[r['policyHash'] for r in parent['references']]]
    failures=[legacy.policy_check(c,p,h,parent,a,tree,roots,expected) for c,p,h in zip(report['policies'],policies,hashes,strict=True)]
    reasons=[]
    if not any(m['classification']=='WITHIN_LIMIT' for m in expected):reasons.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
    if not any(m['classification']=='OUTSIDE_LIMIT' for m in expected):reasons.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
    for label,items in zip(['PRIMARY','80000','160000'],failures,strict=True):reasons.extend(label+':'+f for f in items)
    reasons=sorted(set(reasons))
    require(report['rejectionReasons']==reasons and report['feedbackSupported'] is (not reasons),'Feedback outcome differs')
    return dict(schemaVersion='pokerlab-bank-hand-independent-check/v1',trainerAdmission=False,
                heroCards=report['heroCards'],completeOuterBounds=True,envelopeCheck=receipt,policiesChecked=3,
                forcedContinuationValuesChecked=9,decisionLossesChecked=9,publicRootQualityScope=legacy.PUBLIC_SCOPE,
                moveClassifications=[dict(action=m['action'],classification=m['classification']) for m in expected],
                feedbackSupported=not reasons,rejectionReasons=reasons,gradingAuthority=False,freshLpSolves=0,freshCfrRuns=0)

def check_values(request,report):
    finite(request);finite(report);request=copy.deepcopy(request)
    require(set(request)=={'schemaVersion','parent','parentStoredSha256','hero','heroCards','maximumLossBb'}
            and request['schemaVersion']=='pokerlab-root-hand-bank-request/v1'
            and type(request['hero']) is int and request['hero']==5 and request['heroCards']==HANDS
            and request['maximumLossBb']==.01 and request['parentStoredSha256']==sevens.SOURCE_SHA,
            'Complete ordered bank request required')
    require(sha(size.REPORT)==sevens.SOURCE_SHA,'Pinned original source differs')
    published=read(size.REPORT)
    request['parent']['input']=physical.canonical_input(request['parent']['input']);request['parent']['flop'].sort()
    require(equal(request['parent'],published['request']),'Original shared parent request differs')
    require(set(report)=={'schemaVersion','publicationStatus','trainerAdmission','request','maximumWork','evScope',
                         'reachUnits','parent','assessments','completedWork','completeOuterBounds','supportedQuestionIndices'}
            and report['schemaVersion']=='pokerlab-root-hand-bank-report/v1'
            and report['publicationStatus']=='MULTI_HAND_PREFLOP_ROOT_BANK_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and equal(report['request'],request)
            and equal(report['parent'],published)
            and report['evScope']=='ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1'
            and report['reachUnits']=='ORIGINAL_JOINT_ROOT_CHANCE/v1','Original research bank identity differs')
    require(equal(report['maximumWork'],BUDGET)
            and all(type(report['maximumWork'][k]) is int for k in BUDGET),'Bank reservation differs')
    require(len(report['assessments'])==3,'Every declared hand must retain an outcome')
    for i,a in enumerate(report['assessments']):
        require(type(a['questionIndex']) is int and a['questionIndex']==i and a['heroCards']==HANDS[i],
                'Ordered hand identity differs')
    parent_receipt=size.check_values(request['parent'],report['parent'])
    tree=size.literal_tree(published,dict(binding=published['binding']['physicalCarrier']['sourceBinding']),request['parent'])
    receipts=[check_hand(published,a,tree,parent_receipt) for a in report['assessments']]
    completed=[a['envelope'] for a in report['assessments'] if a['completeOuterBounds']]
    work=dict(scope='COMPLETED_ENVELOPE_AUDITS_ONLY/v1',completedEnvelopes=len(completed),
              envelopeLps=sum(a['work']['lpSolves'] for a in completed),
              compilerUnits=sum(a['work']['compilerUnits'] for a in completed),
              pivots=sum(a['work']['lpPivots'] for a in completed),
              arithmetic=sum(a['work']['lpArithmeticWork'] for a in completed))
    supported=[i for i,r in enumerate(receipts) if r['feedbackSupported']]
    require(equal(report['completedWork'],work) and all(type(report['completedWork'][k]) is int for k in work if k!='scope')
            and report['completeOuterBounds'] is (len(completed)==3)
            and equal(report['supportedQuestionIndices'],supported),'Complete inventory or work totals differ')
    require(work['envelopeLps']<=15 and work['compilerUnits']<=24000000 and work['pivots']<=30000
            and work['arithmetic']<=12000000000,'Cumulative bank reservation exceeded')
    return dict(schemaVersion='pokerlab-root-hand-bank-independent-check/v1',trainerAdmission=False,
                gradingAuthority=False,originalPrivateHands=HANDS,assessments=receipts,
                completeOuterBounds=len(completed)==3,supportedQuestionIndices=supported,
                originalEnvelopeCertificatesChecked=5*len(completed),
                forcedContinuationValuesChecked=9*len(completed),decisionLossesChecked=9*len(completed),
                completedWork=work,parentCheck=parent_receipt,freshLpSolves=0,freshCfrRuns=0)

def check():
    result=check_values(read(REQUEST,128*1024),read(REPORT))
    result.update(checkerSourceSha256=sha(Path(__file__)),requestStoredSha256=sha(REQUEST),reportStoredSha256=sha(REPORT),
                  sourceParentStoredSha256=sha(size.REPORT),immutableHelperSha256={p:sha(ROOT/'scripts'/p) for p in HELPERS})
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--receipt',type=Path);args=parser.parse_args()
    data=(json.dumps(check(),indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as stream:stream.write(data)
    print(data.decode())
