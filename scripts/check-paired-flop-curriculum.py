"""Independent complete four-hand paired curriculum, shared policies and all work charges."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value

BASIS_HELPER = ROOT/'scripts/check-refined-basis-conditional-study.py'
FEEDBACK_HELPER = ROOT/'scripts/check-refined-flop-feedback.py'
basis = module('paired_curriculum_basis', BASIS_HELPER)
feedback = module('paired_curriculum_feedback', FEEDBACK_HELPER)
physical = basis.physical
require, close, read = basis.require, basis.close, basis.read
local_check = feedback.local_check
EPS = 1e-8
LABELS = ['js9s', 'ahkh', 'asqs', 'qhth']
HANDS = ['9s Js', 'Ah Kh', 'As Qs', 'Qh Th']
SELECTION = 'ALL_FOUR_SUPPLIED_PAIRED_BTN_HANDS_FACING_BET/v1'
REPORT_HASH = 'c0e778afe5530740ee8d4dad3b9f9716a05fc45d8c1d141880d99212af99f61e'
BUDGET = dict(conditionalStudies=4, compilerUnits=160000000, lpPivots=320000,
              lpArithmeticWork=128000000000, freshCfrRuns=2, freshIterations=240000,
              referenceVisitedNodes=9600960000)
REFERENCE_BUDGET = dict(algorithm='FRESH_CFR_PLUS_LITERAL_INACTIVE_PASSES_80K_160K/v1',
                        iterations=[80000,160000], totalIterations=240000)
REQUEST_SHA = '8ccffe11a441d07bbb00dd587a08c371451cf7ada7db09863f3c94ff9bc41cd1'
REPORT_SHA = '26ee3e3d850abd5e5fa095de935e1337a47ff7c15854c7b7748b333903f06a38'
SOURCE_REQUEST = ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-request.json'
SOURCE_REPORT = ROOT/'docs/data/supplied-paired-btn-facing-bet-feedback-report.json.gz'


def policy_check(actual, policy, expected_hash, context, actions, roots, key):
    require(set(actual) == {'iterations', 'solutionHash', 'fullParentQuality', 'reachedFlopQuality',
            'actualQuestionRootReach', 'actualQuestionReachGivenPhysicalFlop',
            'counterfactualQuestionReachGivenPhysicalFlop', 'globalHeroBestResponseBb', 'globalHeroUpperBb',
            'inclusionMarginBb', 'insideDeclaredFace', 'boundsChecked', 'maximumBoundViolationBb',
            'actionUtilitiesBb', 'actionLossBb', 'decisionRegretBb', 'failures'}, 'Policy check fields differ')
    require(actual['iterations'] == policy['iterations'] and actual['solutionHash'] == expected_hash, 'Policy identity differs')
    strategy = policy['strategy']
    physical.compare_quality(actual['fullParentQuality'], physical.quality(context['root'], strategy))
    local_check(actual['reachedFlopQuality'], strategy, context)
    actual_mass, cf_weights = 0, []
    for i, p, initial, reply in roots:
        preflop = strategy[f'5:supplied-connected-single-flop:supplied-conditional-preflop:{context["prior"][i]["dealtCombos"][5]}:']['call']
        bet = strategy[initial.key]['bet']
        actual_mass += p * physical.P * preflop * bet
        # Both BB call and BB bet remain; BTN has no modeled past action.
        cf_weights.append(p * preflop * bet)
    cf = math.fsum(cf_weights)
    close(actual['actualQuestionRootReach'], actual_mass, 'Actual full-path root reach differs', mass=True)
    close(actual['actualQuestionReachGivenPhysicalFlop'], actual_mass / physical.P, 'Actual full-path conditional reach differs', mass=True)
    close(actual['counterfactualQuestionReachGivenPhysicalFlop'], cf, 'Hero past cancellation or opponent call/bet reach differs', mass=True)
    values = {a['action']: physical.best_response(physical.Node(-1, edges=tuple(
        (p / cf, dict(root[3].edges)[a['action']]) for root, p in zip(roots, cf_weights, strict=True))), strategy, 3)
        for a in actions}
    losses = {a: max(values.values()) - v for a, v in values.items()}
    require(set(actual['actionUtilitiesBb']) == set(values) == set(actual['actionLossBb']), 'Policy action inventory differs')
    for action in values:
        close(actual['actionUtilitiesBb'][action], values[action], 'BTN forced action value differs')
        close(actual['actionLossBb'][action], losses[action], 'BTN action loss differs')
    br = physical.best_response(context['root'], strategy, 3)
    upper = actions[0]['diagnostic']['globalHeroUpperValue']
    margin = upper + EPS - br
    close(actual['globalHeroBestResponseBb'], br, 'Full-parent hero response differs')
    close(actual['globalHeroUpperBb'], upper, 'Full-parent security upper differs')
    close(actual['inclusionMarginBb'], margin, 'Reference inclusion differs')
    included = margin >= -EPS
    require(actual['insideDeclaredFace'] is included and actual['boundsChecked'] is included, 'Reference face status differs')
    failures = []
    if not included:
        failures.append('REFERENCE_OUTSIDE_DECLARED_FACE')
        require(actual['maximumBoundViolationBb'] is None, 'Outside-face bounds must remain unchecked')
    if actual['fullParentQuality']['nashConvBb'] > .001:
        failures.append('FULL_PARENT_GAP')
    if actual['reachedFlopQuality']['conditionalBestResponse']['nashConvBb'] > .001:
        failures.append('REACHED_FLOP_GAP')
    if actual_mass / physical.P < .0001:
        failures.append('INSUFFICIENT_ACTUAL_QUESTION_REACH')
    if cf < .0001:
        failures.append('INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH')
    regret = math.fsum(strategy[key][a] * loss for a, loss in losses.items())
    close(actual['decisionRegretBb'], regret, 'BTN selected decision regret differs')
    if regret > .01:
        failures.append('SELECTED_DECISION_REGRET')
    if included:
        violation = max([0] + [v for a in actions for v in (
            a['lowerUtilityBb'] - values[a['action']], values[a['action']] - a['upperUtilityBb'],
            a['lowerLossBb'] - losses[a['action']], losses[a['action']] - a['upperLossBb'])])
        close(actual['maximumBoundViolationBb'], violation, 'BTN bound comparison differs')
        if violation > EPS:
            failures.append('REFERENCE_OUTSIDE_DECISION_BOUNDS')
    require(actual['failures'] == failures, 'BTN policy failures differ')
    return failures


def work(decisions, references):
    result = dict(conditionalStudies=len(decisions), parentSolves=0, parentLpSolves=0,
                  endpointActions=0, endpointLpSolves=0, compilerUnits=0, lpPivots=0,
                  lpArithmeticWork=0, freshCfrRuns=len(references),
                  freshIterations=sum(r['policy']['iterations'] for r in references),
                  referenceVisitedNodes=sum(r['work']['statistics']['visitedNodes'] for r in references))
    for decision in decisions:
        conditional = decision['conditional']
        parents = [conditional['parent']['solve']]
        for action in conditional['actions']:
            audit = action['diagnostic']; parents.append(audit['baseline'])
            w = audit['work']; result['endpointActions'] += 1
            for source, target in [('intervalLpSolves','endpointLpSolves'), ('compilerUnits','compilerUnits'),
                                   ('intervalLpPivots','lpPivots'), ('intervalLpArithmeticWork','lpArithmeticWork')]:
                result[target] += w[source]
        for parent in parents:
            result['parentSolves'] += 1; result['parentLpSolves'] += 2
            result['compilerUnits'] += parent['reductionWork']['chargedUnits']
            for lp in [parent['firstLp'], parent['secondLp']]:
                w = lp['work']; result['lpArithmeticWork'] += w['arithmeticWork']
                result['lpPivots'] += sum(w[k] for k in ['phaseOnePivots','cleanupPivots','phaseTwoPivots'])
    require([result[k] for k in ['conditionalStudies','parentSolves','parentLpSolves','endpointActions','endpointLpSolves','freshCfrRuns','freshIterations']]
            == [4,12,24,8,32,2,240000], 'Complete owned operation counts differ')
    for name in ['compilerUnits','lpPivots','lpArithmeticWork','referenceVisitedNodes']:
        require(0 <= result[name] <= BUDGET[name], 'Aggregate work cap exceeded')
    return result


def check(request_path, report_path, *, bind_saved_bytes=True):
    request, request_bytes, _ = read(request_path)
    report, raw, expanded = read(report_path)
    require(set(request) == {'schemaVersion','selection','questions','referenceBudget','workBudget','maximumLossBb'}
            and request['schemaVersion'] == 'pokerlab-paired-four-hand-flop-request/v1'
            and request['selection'] == SELECTION and request['referenceBudget'] == REFERENCE_BUDGET
            and request['workBudget'] == BUDGET and request['maximumLossBb'] == .01
            and len(request['questions']) == 4, 'Fixed complete curriculum request differs')
    sources, paths = [], []
    for label in LABELS:
        prefix = ROOT/('docs/data/supplied-paired-basis-btn-'+label+'-conditional')
        paths.append((Path(str(prefix)+'-request.json'),Path(str(prefix)+'-report.json.gz')))
        sources.append(read(paths[-1][1])[0])
    normalized = copy.deepcopy(request)
    for q in normalized['questions']:
        q['parent']['connected']['input'] = physical.canonical_input(q['parent']['connected']['input'])
    require(normalized['questions'] == [s['request'] for s in sources], 'Declared owned questions or shared source differ')
    require(set(report) == {'schemaVersion','publicationStatus','trainerAdmission','request','references','decisions','work',
                           'uniquePoliciesChecked','policyContextChecks','qualifiedForOfflineDrill','rejectionReasons'}
            and report['schemaVersion'] == 'pokerlab-paired-four-hand-flop-report/v1'
            and report['publicationStatus'] == 'FOUR_PAIRED_BTN_FLOP_CONTEXTS_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['request'] == normalized
            and report['uniquePoliciesChecked'] == 3 and report['policyContextChecks'] == 12
            and len(report['decisions']) == 4, 'Complete paired report identity differs')
    old, _, _ = read(SOURCE_REPORT)
    require(report['references'] == [dict(policy=r['policy'],work=r['work']) for r in old['references']],
            'Shared fresh deterministic policies or literal work differ')
    parent = sources[0]['parent']; original, _, _ = read(physical.PARENT)
    root = basis.parent_check.literal_tree(parent, original, parent['request']['connected'])
    selected = {(i,'|BB:call'):dict(node.edges)['call'].edges[0][1] for i,(_,node) in enumerate(root.edges)}
    context = dict(root=root, prior=parent['originalJointPrior'], selected=selected)
    policies = [parent['candidate']] + [r['policy'] for r in report['references']]
    pins = [parent['solutionHash']] + [r['check']['solutionHash'] for r in old['references']]
    failures = []
    for hand, source, decision in zip(HANDS,sources,report['decisions'],strict=True):
        require(set(decision) == {'conditional','moves','primary','references','qualified','rejectionReasons'}
                and decision['conditional'] == source and source['request']['heroCards'] == hand
                and len(decision['references']) == 2, 'Paired decision inventory, provenance or endpoints differ')
        roots = []
        for i, world in enumerate(context['prior']):
            if world['dealtCombos'][3] == hand:
                initial = selected[i,'|BB:call']
                roots.append((i,world['conditionalProbability'],initial,dict(initial.edges)['bet']))
        moves = []
        for a in source['actions']:
            classification = 'WITHIN_LIMIT' if a['upperLossBb'] <= .01 else 'OUTSIDE_LIMIT' if a['lowerLossBb'] > .01 else 'STRATEGY_DEPENDENT'
            moves.append(dict(action=a['action'],lowerEvBb=a['lowerUtilityBb'],upperEvBb=a['upperUtilityBb'],
                              lowerLossBb=a['lowerLossBb'],upperLossBb=a['upperLossBb'],classification=classification))
        require(decision['moves'] == moves, 'Cached classified moves differ')
        reasons = []
        for label, actual, policy, pin in zip(['PRIMARY','80000','160000'],[decision['primary']]+decision['references'],policies,pins,strict=True):
            reasons += [label+':'+f for f in policy_check(actual,policy,pin,context,source['actions'],roots,source['informationSet'])]
        classes = {m['classification'] for m in moves}
        if 'WITHIN_LIMIT' not in classes: reasons.append('NO_ROBUST_MOVE_WITHIN_LIMIT')
        if 'OUTSIDE_LIMIT' not in classes: reasons.append('NO_ROBUST_MOVE_OUTSIDE_LIMIT')
        reasons = sorted(set(reasons))
        require(decision['rejectionReasons'] == reasons and decision['qualified'] is (not reasons), 'Per-hand qualification differs')
        failures += [hand+':'+f for f in reasons]
    require(report['work'] == work(report['decisions'],report['references']), 'Aggregate work omits repeated parent or endpoint work')
    failures = sorted(set(failures))
    require(report['rejectionReasons'] == failures and report['qualifiedForOfflineDrill'] is (not failures), 'Complete qualification differs')
    if bind_saved_bytes:
        require(hashlib.sha256(request_bytes).hexdigest() == REQUEST_SHA and hashlib.sha256(raw).hexdigest() == REPORT_SHA, 'Saved curriculum byte pins differ')
    # Check every original certificate and literal posterior, not merely report hashes.
    proofs = [basis.check(q,r) for q,r in paths]
    reference_proof = feedback.check(SOURCE_REQUEST,SOURCE_REPORT)
    return dict(schemaVersion='pokerlab-independent-paired-curriculum-check/v1',trainerAdmission=False,
                qualifiedForOfflineDrill=not failures,questionsChecked=4,policyContextChecks=12,
                freshLpSolves=0,freshCfrRuns=0,declaredFreshReferenceRuns=2,declaredFreshReferenceIterations=240000,
                originalEndpointCertificatesChecked=sum(p['originalEndpointCertificatesChecked'] for p in proofs),
                work=report['work'],sourceRequestSha256=hashlib.sha256(request_bytes).hexdigest(),
                sourceReportStoredSha256=hashlib.sha256(raw).hexdigest(),sourceReportExpandedSha256=hashlib.sha256(expanded).hexdigest(),
                checkerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                basisHelperSha256=hashlib.sha256(BASIS_HELPER.read_bytes()).hexdigest(),
                policyHelperSha256=hashlib.sha256(FEEDBACK_HELPER.read_bytes()).hexdigest(),
                conditionalChecks=proofs,referenceSourceCheck=reference_proof)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request',type=Path); parser.add_argument('report',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); require(not args.output.exists(),'Receipt output must be new')
    result=check(args.request,args.report)
    with args.output.open('x',encoding='utf-8',newline='\n') as out: out.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


if __name__ == '__main__': main()
