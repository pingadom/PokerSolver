"""Optional independent oracle for three v3 BTN feedback reports.

Fresh literal complete-plan LPs and behavioral witness checks use saved integer
showdown counts. Java replay separately regenerates boards and CFR references.
NumPy/SciPy are optional offline dependencies; this script grants no admission.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
import scipy

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('root_oracle', root / 'scripts/check-supplied-l1-root-feedback.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

base, _, _ = module.read(module.BASE)
oracle = module.Oracle(base)
bb = np.arange(5832)
def mixture(witness):
    flow = witness['opponentFlow']
    if flow['actor'] != 5:
        raise ValueError('Expected BB opponent flow')
    behavior = {}
    for row in flow['conservation']:
        parent = flow['realization'][row['parentSequence']]
        children = row['childSequences']
        ratios = np.array([flow['realization'][i] / parent if parent > 1e-12 else 1 / len(children) for i in children])
        if ratios.min() < -1e-8 or ratios.max() > 1 + 1e-8:
            raise ValueError('Original behavioral ratio outside probability bounds')
        ratios = np.clip(ratios, 0, 1)
        ratios /= ratios.sum()
        behavior[row['key']] = dict(zip([flow['sequences'][i]['action'] for i in children], ratios))
    weights = np.ones(5832)
    for index, hand in enumerate(oracle.blind):
        code = bb // 18 ** index % 18
        for digit, history, names in [
            (code % 3, '', ['fold', 'call', 'raise:9.0']),
            (code // 3 % 3, '|BB:raise:9.0|BTN:raise:22.0', ['fold', 'call', 'raise:50.0']),
            (code // 9, '|BB:raise:9.0|BTN:raise:22.0|BB:raise:50.0|BTN:raise:100.0', ['fold', 'call'])]:
            probabilities = behavior['5:supplied-conditional-preflop:' + hand + ':' + history]
            weights *= np.array([probabilities[names[j]] for j in digit])
    module.close(1, weights.sum())
    return weights


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('reports', type=Path, nargs=3)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if args.output.exists():
    raise ValueError('Output must be a new path')
seen = set()
records = []
for path in args.reports:
    report, raw, stored_hash = module.read(path)
    request = report['request']
    hand = request['informationSet'].split(':')[2]
    if (request['hero'] != 3 or hand not in oracle.hands
            or request['informationSet'] != f'3:supplied-conditional-preflop:{hand}:|BB:raise:9.0'
            or request['securitySlack'] != 1e-4 or request['minimumReach'] != .01
            or request['maximumLossBb'] != .01 or report['heroCommittedBb'] != 3
            or request['input'] != base['request']['input']
            or report['payoffs'] != base['payoffs'] or report['prior'] != base['prior']
            or report['qualifiedForIntervalFeedback'] is not True or report['rejectionReasons']
            or report['trainerAdmission'] is not False
            or request['schemaVersion'] != 'pokerlab-supplied-interval-feedback-request/v3'
            or report['schemaVersion'] != 'pokerlab-supplied-interval-feedback-report/v3'
            or report['publicationStatus'] != 'SELECTED_QUESTION_INTERVAL_FEEDBACK_RESEARCH_ONLY'
            or report['binding'] != base['binding']
            or report['diagnostic']['representation'] != 'UPPER_OBJECTIVE_SHIFT_STABLE_ASCENDING_L1_JOINT_ROWS_ORIGINAL_CERTIFICATES/v2'
            or report['diagnostic']['joint']['algorithm'] != 'CONDITIONED_STABLE_L1_JOINT_CONDITIONAL_DECISION_LOSS_OWNED_LP/v3'
            or report['diagnostic']['joint']['trainerAdmission'] is not False
            or report['primaryQuality']['nashConvBb'] > .001
            or not report['legacySelectedDecision']['material']
            or report['legacySelectedDecision']['primary']['values']['decisionRegretBb'] > .01
            or [r['iterations'] for r in report['referenceChecks']] != [2000, 4000]):
        raise ValueError('Unsupported v3 BTN qualification identity or scope')
    actions = ['fold', 'call', 'raise:22.0']
    joint = report['diagnostic']['joint']
    if ([m['action'] for m in report['moves']] != actions
            or [m['action'] for m in joint['moves']] != actions
            or [a['action'] for a in joint['actions']] != actions
            or [c['iterations'] for c in report['legacySelectedDecision']['comparisons']] != [2000, 4000]
            or any(f != 'ACTION_EV_DRIFT' for c in report['legacySelectedDecision']['comparisons'] for f in c['failures'])):
        raise ValueError('Incomplete action inventory or selected decision checks')
    for move, original, action in zip(report['moves'], joint['moves'], joint['actions']):
        module.close(move['lowerLossBb'], original['lowerLoss'])
        module.close(move['upperLossBb'], original['upperLoss'])
        module.close(move['lowerEvBb'], action['interval']['lowerUtility'] + 3)
        module.close(move['upperEvBb'], action['interval']['upperUtility'] + 3)
        module.close(oracle.lower, action['interval']['baseline']['lowerValue'])
        module.close(oracle.upper, action['interval']['baseline']['upperValue'])
        module.close(oracle.upper, action['interval']['globalHeroUpperValue'])
    if hand in seen:
        raise ValueError('Repeated hand is not additional coverage')
    seen.add(hand)
    worlds = []
    denominator = np.zeros(5832)
    numerators = np.zeros((9, 5832))
    for world in report['prior']:
        if world['dealtCombos'][3] != hand:
            continue
        code = bb // 18 ** oracle.blind.index(world['dealtCombos'][5]) % 18
        reached = (code % 3 == 2)
        count = report['payoffs'][world['dealIndex']]['counts']
        share = (count['firstWins'] + .5 * count['ties']) / count['boards']
        weight = world['conditionalProbability'] * reached
        denominator += weight
        worlds.append((world, weight))
        for plan in range(9):
            numerators[plan] += weight * module.literal.literal(
                plan % 3, plan // 3, code % 3, code // 3 % 3, code // 9, share)
    utilities = {action: np.unique(numerators[np.arange(9) % 3 == index], axis=0)
                 for index, action in enumerate(['fold', 'call', 'raise:22.0'])}
    # y=z/t, d.z=1, sum(z)=t; M.z <= (value+slack)t, t<=1/minReach.
    # This is a fresh opposite-player security face with variable action reach.
    security = np.column_stack([oracle.matrix, np.full(729, -(oracle.upper + 1e-4))])
    equality = np.vstack([np.r_[np.ones(5832), -1], np.r_[denominator, 0]])
    common = dict(A_ub=security, b_ub=np.zeros(729), A_eq=equality, b_eq=[0, 1],
                  bounds=[(0, None)] * 5832 + [(0, 100)])
    def cost(row):
        return np.r_[row, 0]
    def epigraph(diff, minimize, nonnegative):
        return oracle.solve(np.r_[np.zeros(5833), 1 if minimize else -1],
            A_ub=np.vstack([np.column_stack([security, np.zeros(729)]),
                           np.column_stack([diff, np.zeros(len(diff)), (-1 if minimize else 1) * np.ones(len(diff))])]),
            b_ub=np.r_[np.zeros(729), np.zeros(len(diff))],
            A_eq=np.column_stack([equality, np.zeros(2)]), b_eq=[0, 1],
            bounds=common['bounds'] + [(0, None) if nonnegative else (None, None)])
    endpoints = {}
    for action, selected in utilities.items():
        other = np.concatenate([v for a, v in utilities.items() if a != action])
        values = dict(lowerEvBb=epigraph(selected, True, False) + 3,
                      upperEvBb=max(-oracle.solve(-cost(p), **common) for p in selected) + 3,
                      lowerLossBb=min(epigraph(other - p, True, True) for p in selected),
                      upperLossBb=max(0, max(-epigraph(selected - p, False, False) for p in other)))
        move = next(m for m in report['moves'] if m['action'] == action)
        for key, value in values.items():
            module.close(move[key], value)
        endpoints[action] = values
    checked = 0
    for move in report['diagnostic']['joint']['moves']:
        for control in move['lowerControls'] + move['upperControls']:
            witness = control['witness']
            selected = witness['selected']
            if selected['certificate']['algorithm'] != 'OWNED_STABLE_ASCENDING_L1_ROWS_ORIGINAL_CERTIFICATE/v2':
                raise ValueError('Unexpected original certificate representation')
            weights = mixture(selected)
            br = float(np.max(oracle.matrix @ weights))
            if br > oracle.upper + 1e-4 + 2e-8:
                raise ValueError('Independent complete-plan security violation')
            module.close(br, selected['globalHeroBestResponse'])
            reach = float(denominator @ weights)
            if reach < .01 - 1e-8:
                raise ValueError('Independent selected reach failure')
            module.close(reach, selected['questionReach'])
            if len(worlds) != len(selected['posterior']):
                raise ValueError('Incomplete hidden-world posterior')
            for (world, numerator), posterior in zip(worlds, selected['posterior']):
                weight = float(numerator @ weights)
                module.close(world['conditionalProbability'], posterior['chanceWeight'])
                module.close(weight, posterior['behavioralWeight'])
                module.close(weight, posterior['originalRealizationWeight'])
                module.close(weight / reach, posterior['probability'])
            evs = {action: float(np.max(plans @ weights) / reach) for action, plans in utilities.items()}
            module.close(evs[move['action']], selected['conditionalHeroBestResponse'])
            for action, value in evs.items():
                module.close(value, witness['actionUtilities'][action])
            module.close(max(0, max(evs.values()) - evs[move['action']]), witness['decisionLoss'])
            checked += 1
    for reference in report['referenceChecks']:
        if (not reference['insideDeclaredFace'] or not reference['boundsChecked'] or reference['failures']
                or reference['quality']['nashConvBb'] > .001):
            raise ValueError('Reference did not pass declared qualification')
        module.close(reference['globalHeroUpperBb'] + 1e-4 - reference['globalHeroBestResponseBb'], reference['inclusionMarginBb'])
        module.close(oracle.upper, reference['globalHeroUpperBb'])
        module.close(reference['quality']['bestResponseUtilitiesBb'][3], reference['globalHeroBestResponseBb'])
        module.close(max(0, reference['globalHeroBestResponseBb'] - oracle.upper), reference['requiredSecuritySlackBb'])
        if reference['inclusionMarginBb'] < -1e-8:
            raise ValueError('Reference outside declared face')
        values = reference['decision']['values']['actionEvBb']
        for move in report['moves']:
            loss = max(0, max(values.values()) - values[move['action']])
            module.close(loss, reference['actionLossBb'][move['action']])
            if (not move['lowerEvBb'] - 1e-8 <= values[move['action']] <= move['upperEvBb'] + 1e-8
                    or not move['lowerLossBb'] - 1e-8 <= loss <= move['upperLossBb'] + 1e-8):
                raise ValueError('Reference outside EV/loss bounds')
    records.append(dict(selection=request['informationSet'], storedBytesSha256=stored_hash,
                        expandedBytesSha256=hashlib.sha256(raw).hexdigest(), endpoints=endpoints, checkedJointWitnesses=checked, recordedReferenceRowsChecked=2))
    print('Fresh independent endpoints checked', hand, flush=True)
summary = dict(schemaVersion='pokerlab-independent-extended-button-feedback-check/v1',
               oracle='LITERAL_COMPLETE_PLANS_SCIPY_HIGHS_FRESH_EXTREMA_AND_WITNESSES/v1', trainerAdmission=False,
               qualificationAttempted=False, numpyVersion=np.__version__, scipyVersion=scipy.__version__,
               freshLpSolves=oracle.solves, cachedEndpointCases=0,
               sourceScriptBytesSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               rows=records, limitations=['Saved integer board counts; Java independently regenerated physical boards',
                    'Checks saved reference rows; Java replay regenerates CFR reference policies',
                    'Two active players, supplied beliefs, no rake, mandatory checkdown'])
with args.output.open('x', encoding='utf-8') as out:
    out.write(json.dumps(summary, indent=2, allow_nan=False) + '\n')
