"""Independent original physical root minimum, certificate and fresh-policy checks; no LP/CFR solve."""
import argparse
import copy
import hashlib
import importlib.util
import json
import math
import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT/'scripts'/file)
    value = importlib.util.module_from_spec(spec); sys.modules[name] = value; spec.loader.exec_module(value); return value

feedback = module('physical_minimum_source', 'check-root-envelope-feedback.py')
reduced = module('physical_minimum_reduced', 'check-reduced-preflop.py')
minimum = module('physical_minimum_lp', 'check-root-minimax.py')
source, size = feedback.source, feedback.source.size
require, close, dot, vector, read = source.require, source.close, source.dot, source.vector, source.read
DATA = ROOT/'docs/data'
REQUEST, REPORT = DATA/'supplied-root-minimum-request.json', DATA/'supplied-root-minimum-report.json.gz'
SOURCE = DATA/'supplied-root-envelope-feedback-report.json.gz'
SOURCE_HASH = 'cc20afe1dee2584956fba058a4cbac5e421886b6bc98e91ff2c602666173a658'
SOURCE_SHA = 'e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d'
EPS = 1e-8


def context():
    require(hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA, 'Original source byte pin differs')
    saved = read(SOURCE)
    checked = feedback.check_values(read(feedback.REQUEST), saved)
    parent = saved['envelope']['parent']
    tree = size.literal_tree(parent, dict(binding=parent['binding']['physicalCarrier']['sourceBinding']), parent['request'])
    key = saved['envelope']['envelope']['question']['informationSet']
    roots = [(world['conditionalProbability'], node) for (_, node), world in zip(tree.edges, parent['originalJointPrior'], strict=True) if node.actor == 5 and node.key == key]
    require(len(roots) == 8, 'All original root worlds required')
    return saved, parent, tree, roots, checked


def check_values(request, report, original=None):
    saved, parent, tree, roots, source_receipt = original if original is not None else context()
    request = copy.deepcopy(request)
    nested = request['sourceRequest']['envelope']['parent']
    nested['input'] = source.physical.canonical_input(nested['input'])
    nested['flop'].sort()
    require(set(request) == {'schemaVersion', 'sourceRequest', 'sourceReportHash', 'action'} and request['schemaVersion'] == 'pokerlab-root-minimum-request/v1'
            and request['sourceRequest'] == saved['request'] and request['sourceReportHash'] == SOURCE_HASH and request['action'] == 'raise:9.0', 'Declared original raise request differs')
    require(report['schemaVersion'] == 'pokerlab-root-minimum-report/v1' and report['publicationStatus'] == 'PHYSICAL_ROOT_RAISE_MINIMUM_RESEARCH_ONLY'
            and report['trainerAdmission'] is False and report['minimumScope'] == minimum.SCOPE and report['request'] == request
            and report['parent'] == parent and report['context'] == saved['envelope']['context'], 'Minimum report scope/parent/context differs')
    a = report['minimum']; complete = a is not None
    require(report['completeMinimum'] == complete and (report['rejection'] is None) == complete, 'Complete minimum or typed failure required')
    coverage = None
    if complete:
        require(a['algorithm'] == minimum.ALGORITHM and a['scope'] == minimum.SCOPE and a['trainerAdmission'] is False
                and a['baseline'] == parent['solve'] and a['firstParentWitness'] == parent['firstLpWitness'] and a['secondParentWitness'] == parent['secondLpWitness']
                and a['question'] == dict(hero=5, informationSet=roots[0][1].key, action='raise:9.0')
                and re.fullmatch('[0-9a-f]{64}', a['securityFaceHash']) is not None
                and a['securitySlack'] == EPS and a['requiredMinimumReach'] == .0001, 'Original minimum provenance differs')
        mass = math.fsum(p for p, _ in roots); close(a['originalQuestionReach'], mass, 'Original chance reach differs')
        require(len(a['support']) == 8, 'Root support incomplete')
        for item, old, (weight, _) in zip(a['support'], saved['envelope']['envelope']['support'], roots, strict=True):
            close(item['originalChanceWeight'], weight, 'Original root weight differs'); close(item['posteriorProbability'], weight/mass, 'Root posterior differs')
            require(item['stateHash'] == old['stateHash'], 'Original root state hash differs')
        normalized = [(p/mass, node) for p, node in roots]
        coverage = reduced.plans(normalized, 'raise:9.0', parent['solve'], dict(numeratorPlans=a['plans'], opponentProjection=a['opponentProjection']))
        require(coverage['cartesianPlansChecked'] == a['cartesianPlans'] == 1296 and coverage['reducedPlansChecked'] == a['reducedPlans'] == 52, 'Original complete reduced plan inventory differs')
        require(a['heroProjection'] == saved['envelope']['envelope']['heroProjection']
                and a['opponentProjection'] == saved['envelope']['envelope']['opponentProjection']
                and a['orientedPayoff'] == saved['envelope']['envelope']['orientedPayoff'], 'Original oriented physical game differs')
        face, rhs, n = reduced.bounds.face(a, parent['solve'])
        d = len(a['heroProjection']['rhs'])
        shift = reduced.bounds.maximum_utility(tree)+1
        matrix = [row+[0.] for row in face]+[p['cost']+[0.]*d+[-1.] for p in a['plans']]
        rhs += [-p['constant']-shift for p in a['plans']]; cost = [0.]*(n+d)+[-1.]
        close(a['problem']['utilityShift'], shift, 'Original utility shift differs')
        minimum.near(a['problem']['matrix'], matrix); vector(a['problem']['rhs'], rhs); vector(a['problem']['cost'], cost)
        w = a['witness']; minimum.certificate(matrix, rhs, cost, w, minimum.LP)
        p = a['opponentProjection']; width = len(p['independentSequences'])
        realization = [off+dot(row, w['point'][:width]) for off, row in zip(p['offset'], p['transform'], strict=True)]
        flow, actual = parent['solve']['firstFlow'], w['opponentFlow']
        require(actual['actor'] == 3 and actual['sequences'] == flow['sequences'] and actual['conservation'] == flow['conservation'], 'Original opponent topology differs')
        require(all(math.isfinite(v) and -EPS <= v <= 1+EPS for v in realization), 'Invalid original realization')
        point = [max(0., v) for v in realization]; vector(actual['realization'], point)
        policy = copy.deepcopy(parent['candidate']['strategy']); residual = abs(point[0]-1)
        for row in flow['conservation']:
            ids = row['childSequences']; mass_at_row = point[row['parentSequence']]
            residual = max(residual, abs(sum(point[i] for i in ids)-mass_at_row))
            weights = [max(0., min(1., point[i]/mass_at_row if mass_at_row > 1e-12 else 1/len(ids))) for i in ids]
            require(abs(sum(weights)-1) <= EPS, 'Original behavioral normalization differs')
            policy[row['key']] = {flow['sequences'][i]['action']: weight/sum(weights) for i, weight in zip(ids, weights, strict=True)}
        close(actual['maximumResidual'], residual, 'Original flow residual differs'); require(residual <= EPS, 'Original flow conservation fails')
        global_br = source.physical.best_response(tree, policy, 5)
        forced = size.Node(-1, edges=tuple((p/mass, dict(node.edges)['raise:9.0']) for p, node in roots))
        local_br = source.physical.best_response(forced, policy, 5)
        close(w['globalHeroBestResponse'], global_br, 'Original whole-game best response differs')
        close(w['conditionalHeroBestResponse'], local_br, 'Forced raise best response differs')
        require(global_br <= a['globalHeroUpperValue']+EPS+EPS, 'Minimum witness outside original security face')
        maximum_plan = max(p['constant']+dot(p['cost'], w['point'][:width]) for p in a['plans'])
        close(maximum_plan, local_br, 'Complete continuation maximum differs')
        objective = -w['certificate']['primalValue']-shift
        close(objective, local_br, 'Original epigraph objective differs')
        close(a['minimum']['witnessUtility'], local_br, 'Minimum witness utility differs')
        close(a['minimum']['lowerBound'], -w['certificate']['dualValue']-shift-EPS, 'Minimum lower bracket differs')
        close(a['minimum']['upperBound'], max(objective, local_br)+EPS, 'Minimum upper bracket differs')
        require(a['minimum']['lowerBound'] <= a['minimum']['upperBound'], 'Minimum bracket reversed')
        old_lower = next(x['lowerUtility'] for x in saved['envelope']['envelope']['actions'] if x['action'] == 'raise:9.0')
        close(report['previousConservativeLowerUtilityBb'], old_lower, 'Retained old lower differs')
        close(report['lowerBoundDifferenceBb'], a['minimum']['lowerBound']-old_lower, 'Lower comparison differs')
        work = a['work']; lp = w['certificate']['work']
        require(work['compilerLimit'] == 8000000 and 0 <= work['compilerUnits'] <= 8000000 and work['endpointLpSolves'] == 1
                and work['endpointArithmeticWork'] == lp['arithmeticWork'] <= 4000000000
                and work['endpointPivots'] == sum(lp[k] for k in ['phaseOnePivots', 'cleanupPivots', 'phaseTwoPivots']) <= 10000, 'Original work caps/endpoint totals differ')
    else:
        failure = report['rejection']
        require(failure['kind'] in ('LP', 'PARENT', 'MINIMUM', 'STRUCTURE') and isinstance(failure['reason'], str) and isinstance(failure['detail'], str)
                and report['previousConservativeLowerUtilityBb'] is None and report['lowerBoundDifferenceBb'] is None, 'Rejected minimum cannot publish values')
    checks = [saved['primary'], *saved['references']]
    require(len(report['policies']) == len(checks) == 3, 'Primary and both references required')
    reasons = set()
    if not complete: reasons.add('MINIMUM:'+report['rejection']['kind']+':'+report['rejection']['reason'])
    all_supported = complete
    for assessment, check in zip(report['policies'], checks, strict=True):
        require(assessment['original'] == check, 'Original fresh policy assessment differs')
        utility = check['actionUtilitiesBb']['raise:9.0']; close(assessment['forcedActionUtilityBb'], utility, 'Forced raise policy value differs')
        failures = set(check['failures']); violation = None
        if complete and check['insideDeclaredFace']: violation = max(0., a['minimum']['lowerBound']-utility)
        if complete and not check['insideDeclaredFace']: failures.add('MINIMUM_REFERENCE_OUTSIDE_FACE')
        if violation is not None and violation > EPS: failures.add('REFERENCE_BELOW_MINIMUM_LOWER_BOUND')
        if violation is None: require(assessment['minimumLowerViolationBb'] is None, 'Unchecked minimum violation claim')
        else: close(assessment['minimumLowerViolationBb'], violation, 'Minimum policy violation differs')
        supported = violation is not None and not failures
        require(assessment['minimumLowerSupported'] == supported and assessment['failures'] == sorted(failures), 'Minimum policy support differs')
        all_supported = all_supported and supported
        reasons.update(str(check['iterations'])+':'+f for f in failures)
    require(report['referenceSupportedMinimum'] == all_supported and report['rejectionReasons'] == sorted(reasons), 'Minimum research outcome differs')
    return dict(schemaVersion='pokerlab-root-minimum-independent-check/v1', trainerAdmission=False, completeMinimum=complete,
                referenceSupportedMinimum=all_supported, originalSourceChecked=source_receipt, policiesChecked=3,
                endpointCertificatesChecked=int(complete), completePlanCoverage=coverage, minimumBracket=a['minimum'] if complete else None,
                fullActionUpperEndpointClaim=False, freshLpSolves=0, freshCfrRuns=0)


def check():
    result = check_values(read(REQUEST), read(REPORT))
    result.update(checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  requestStoredSha256=hashlib.sha256(REQUEST.read_bytes()).hexdigest(), reportStoredSha256=hashlib.sha256(REPORT.read_bytes()).hexdigest(),
                  immutableHelperSha256={p: hashlib.sha256((ROOT/'scripts'/p).read_bytes()).hexdigest() for p in
                    ['check-root-envelope-feedback.py', 'check-root-security-envelope.py', 'check-reduced-preflop.py', 'check-root-minimax.py', 'check-resolvable-refined-lp.py']})
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--receipt', type=Path); args = parser.parse_args()
    data = (json.dumps(check(), indent=2)+'\n').encode()
    if args.receipt:
        with args.receipt.open('xb') as output: output.write(data)
    print(data.decode())
