"""Optional independent oracle for the saved, selected BB99 interval-feedback question.

Checks literal normal-form extrema and owned witnesses; recorded reference rows are checked
for consistency, while full reference-policy regeneration is the Java physical replay's job.
NumPy/SciPy remain offline research dependencies, never runtime or CI dependencies.
"""

import argparse
import copy
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
from scipy.optimize import linprog

spec = importlib.util.spec_from_file_location('joint_oracle', Path(__file__).with_name('check-supplied-conditional-decision-loss.py'))
joint = importlib.util.module_from_spec(spec)
spec.loader.exec_module(joint)
close = joint.close


def check(path):
    stored = path.read_bytes()
    if len(stored) > 8 * 1024 * 1024:
        raise ValueError('Oracle input exceeds byte cap')
    if path.name.endswith('.gz'):
        with gzip.open(path, 'rb') as source:
            raw = source.read(8 * 1024 * 1024 + 1)
    else:
        raw = stored
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError('Expanded oracle input exceeds byte cap')
    report = json.loads(raw)
    request, diagnostic = report['request'], report['diagnostic']
    if (report['schemaVersion'] != 'pokerlab-supplied-interval-feedback-report/v1'
            or request['schemaVersion'] != 'pokerlab-supplied-interval-feedback-request/v1'
            or report['publicationStatus'] != 'SELECTED_QUESTION_INTERVAL_FEEDBACK_RESEARCH_ONLY'
            or report['trainerAdmission']
            or diagnostic['representation'] != 'UPPER_OBJECTIVE_MINUS_MAX_ABSOLUTE_TERMINAL_UTILITY_PLUS_ONE/v1'
            or diagnostic['joint']['algorithm'] != 'CONDITIONED_JOINT_CONDITIONAL_DECISION_LOSS_OWNED_LP/v2'):
        raise ValueError('Unexpected feedback identity')
    audits = {a['action']: a['interval'] for a in diagnostic['joint']['actions']}
    if any(a['algorithm'] != 'POSITIVE_REACH_UPPER_OBJECTIVE_SHIFT_OWNED_LP/v2' for a in audits.values()):
        raise ValueError('Unexpected action-interval identity')
    # Reuse the unchanged literal joint-control oracle, adapting only its report envelope.
    adapter = copy.deepcopy(report)
    adapter['schemaVersion'] = 'pokerlab-supplied-conditional-decision-loss-report/v1'
    adapter['diagnostic'] = diagnostic['joint']
    for move, loss in zip(adapter['moves'], diagnostic['joint']['moves']):
        if move['action'] != loss['action']:
            raise ValueError('Action order mismatch')
        move['conservativeLowerLossBb'] = loss['conservativeLowerLoss']
        move['conservativeUpperLossBb'] = loss['conservativeUpperLoss']
    summary = joint.check_report(adapter, raw)

    prior = report['prior']
    button = sorted({w['dealtCombos'][3] for w in prior})
    blind = sorted({w['dealtCombos'][5] for w in prior})
    btn, bb = np.arange(729), np.arange(5832)
    matrix, denominator = np.zeros((729, 5832)), np.zeros(729)
    numerators = {a: np.zeros((2 if a == 'raise:50.0' else 1, 729)) for a in audits}
    for w in prior:
        a = btn // 9 ** button.index(w['dealtCombos'][3]) % 9
        b = bb // 18 ** blind.index(w['dealtCombos'][5]) % 18
        c = report['payoffs'][w['dealIndex']]['counts']
        share = (c['firstWins'] + .5 * c['ties']) / c['boards']
        p = w['conditionalProbability']
        matrix += p * joint.literal((a % 3)[:, None], (a // 3)[:, None], (b % 3)[None, :], (b // 3 % 3)[None, :], (b // 9)[None, :], share)
        if w['dealtCombos'][5] == '9d 9h':
            denominator += p * (a % 3 == 2)
            for action, bb2 in [('fold', 0), ('call', 1), ('raise:50.0', 2)]:
                for bb4 in range(len(numerators[action])):
                    numerators[action][bb4] += p * (a % 3 == 2) * (9.5 - joint.literal(a % 3, a // 3, np.full(729, 2), np.full(729, bb2), np.full(729, bb4), share))
    rhs = np.full(5832, -(audits['call']['baseline']['lowerValue'] - request['securitySlack']))
    scaled = np.column_stack([-matrix.T, -rhs])
    equality = np.stack([np.r_[np.ones(729), -1], np.r_[denominator, 0]])
    bounds = [(0, None)] * 729 + [(0, 1 / request['minimumReach'])]

    def solve(cost, **kwargs):
        r = linprog(cost, method='highs', options=dict(dual_feasibility_tolerance=1e-10, primal_feasibility_tolerance=1e-10), **kwargs)
        if not r.success:
            raise ValueError(r.message)
        return r

    def mixture(witness):
        flow, behavior = witness['opponentFlow'], {}
        if flow['actor'] != 3:
            raise ValueError('Unexpected opponent')
        for row in flow['conservation']:
            parent = flow['realization'][row['parentSequence']]
            actions = [flow['sequences'][i]['action'] for i in row['childSequences']]
            ratios = np.array([flow['realization'][i] / parent if parent > 1e-12 else 1 / len(actions) for i in row['childSequences']])
            if np.min(ratios) < -1e-8 or np.max(ratios) > 1 + 1e-8:
                raise ValueError('Invalid original behavioral ratio')
            probabilities = np.clip(ratios, 0, 1)
            probabilities /= probabilities.sum()
            behavior[row['key']] = dict(zip(actions, probabilities))
        result = np.ones(729)
        for i, hand in enumerate(button):
            code = btn // 9 ** i % 9
            for digit, history, actions in [(code % 3, '|BB:raise:9.0', ['fold', 'call', 'raise:22.0']), (code // 3, '|BB:raise:9.0|BTN:raise:22.0|BB:raise:50.0', ['fold', 'call', 'raise:100.0'])]:
                probs = behavior['3:supplied-conditional-preflop:' + hand + ':' + history]
                result *= np.array([probs[actions[j]] for j in digit])
        close(1, result.sum())
        close(.5 - np.min(matrix.T @ result), witness['globalHeroBestResponse'])
        if np.min(matrix.T @ result) < audits['call']['baseline']['lowerValue'] - request['securitySlack'] - 2e-8:
            raise ValueError('Witness outside original security face')
        reach = denominator @ result
        close(reach, witness['questionReach'])
        if reach < request['minimumReach']:
            raise ValueError('Insufficient witness reach')
        worlds = [w for w in prior if w['dealtCombos'][5] == '9d 9h']
        if len(worlds) != len(witness['posterior']):
            raise ValueError('Missing posterior world')
        for w, posterior in zip(worlds, witness['posterior']):
            weight = w['conditionalProbability'] * behavior['3:supplied-conditional-preflop:' + w['dealtCombos'][3] + ':|BB:raise:9.0']['raise:22.0']
            close(weight, posterior['originalRealizationWeight'])
            close(weight, posterior['behavioralWeight'])
            close(weight / reach, posterior['probability'])
        return result, reach

    close(-51, diagnostic['upperObjectiveShift'])  # max |BB terminal utility|=50, not caller supplied.
    interval_checks = []
    for move in report['moves']:
        action, audit = move['action'], audits[move['action']]
        rows = numerators[action]
        problem = np.vstack([np.column_stack([scaled, np.zeros(5832)]), np.column_stack([rows, np.zeros(len(rows)), -np.ones(len(rows))])])
        lower = solve(np.r_[np.zeros(730), 1], A_ub=problem, b_ub=np.zeros(len(problem)), A_eq=np.column_stack([equality, np.zeros(2)]), b_eq=[0, 1], bounds=bounds + [(None, None)]).fun
        upper_controls = [-solve(-np.r_[row, 0], A_ub=scaled, b_ub=np.zeros(5832), A_eq=equality, b_eq=[0, 1], bounds=bounds).fun for row in rows]
        upper = max(upper_controls)
        close(lower, move['lowerEvBb'])
        close(upper, move['upperEvBb'])
        close(lower - 9, audit['lowerUtility'])
        close(upper - 9, audit['upperUtility'])
        for index, witness in enumerate(audit['upperWitnesses']):
            weights, reach = mixture(witness)
            planned = rows[index] @ weights / reach
            close(planned, upper_controls[index])
            close(planned - 9 + diagnostic['upperObjectiveShift'], witness['certificate']['primalValue'])
            close(np.max(rows @ weights) / reach - 9, witness['conditionalHeroBestResponse'])
        weights, reach = mixture(audit['lowerWitness'])
        close(lower - 9, np.max(rows @ weights) / reach - 9)
        close(lower - 9, audit['lowerWitness']['conditionalHeroBestResponse'])
        lo, hi, limit = move['lowerLossBb'], move['upperLossBb'], request['maximumLossBb']
        if not 0 < limit <= .01 or not 0 <= lo <= hi:
            raise ValueError('Invalid loss limit/interval')
        classification = 'WITHIN_LIMIT' if hi <= limit else 'OUTSIDE_LIMIT' if lo > limit else 'STRATEGY_DEPENDENT'
        if classification != move['classification']:
            raise ValueError('Wrong feedback classification')
        interval_checks.append(dict(action=action, lowerEvBb=float(lower), upperEvBb=float(upper), upperControlEvsBb=[float(x) for x in upper_controls], classification=classification))
    references = []
    if [r['iterations'] for r in report['referenceChecks']] != [500, 1000]:
        raise ValueError('Missing reference checks')
    for ref in report['referenceChecks']:
        br, upper = ref['globalHeroBestResponseBb'], ref['globalHeroUpperBb']
        close(upper, audits['call']['globalHeroUpperValue'])
        close(br, ref['quality']['bestResponseUtilitiesBb'][5])
        close(max(0, br - upper), ref['requiredSecuritySlackBb'])
        close(upper + request['securitySlack'] - br, ref['inclusionMarginBb'])
        inside = ref['inclusionMarginBb'] >= -1e-8
        checked = inside and ref['decision']['values'] is not None
        if ref['insideDeclaredFace'] != inside or ref['boundsChecked'] != checked:
            raise ValueError('False reference inclusion or bounds check')
        if checked:
            evs = ref['decision']['values']['actionEvBb']
            worst = 0
            for m in report['moves']:
                ev, loss = evs[m['action']], max(evs.values()) - evs[m['action']]
                close(loss, ref['actionLossBb'][m['action']])
                worst = max(worst, m['lowerEvBb'] - ev, ev - m['upperEvBb'], m['lowerLossBb'] - loss, loss - m['upperLossBb'])
            close(worst, ref['maximumBoundViolationBb'])
            if worst > 1e-8:
                raise ValueError('Reference outside interval')
        elif ref['maximumBoundViolationBb'] is not None:
            raise ValueError('Unchecked bounds must not report a numeric violation')
        references.append(dict(iterations=ref['iterations'], insideDeclaredFace=inside, boundsChecked=checked, requiredSecuritySlackBb=ref['requiredSecuritySlackBb']))
    if (not report['qualifiedForIntervalFeedback'] or report['rejectionReasons']
            or report['legacySelectedDecision']['stable']
            or 'UNSTABLE_MATERIAL_DECISIONS' not in report['legacyWholeStudyRejections']):
        raise ValueError('Unexpected saved qualification / legacy scalar result')
    legacy = report['legacySelectedDecision']
    if (not legacy['material'] or legacy['primary']['values']['decisionRegretBb'] > .01
            or report['primaryQuality']['nashConvBb'] > .001
            or 'INSUFFICIENT_MATERIAL_PRIVATE_COMBOS' in report['legacyWholeStudyRejections']
            or [c['iterations'] for c in legacy['comparisons']] != [500, 1000]
            or any(f != 'ACTION_EV_DRIFT' for c in legacy['comparisons'] for f in c['failures'])
            or any(not r['insideDeclaredFace'] or not r['boundsChecked'] or r['failures']
                   or r['quality']['nashConvBb'] > .001 for r in report['referenceChecks'])
            or not any(m['classification'] == 'WITHIN_LIMIT' for m in report['moves'])
            or not any(m['classification'] == 'OUTSIDE_LIMIT' for m in report['moves'])):
        raise ValueError('Saved question does not satisfy qualification gates')
    summary.update(scope='OPTIONAL_INDEPENDENT_LITERAL_INTERVAL_FEEDBACK_ORACLE_NO_TRAINER_ADMISSION',
                   storedReportBytesSha256=hashlib.sha256(stored).hexdigest(), upperObjectiveShift=diagnostic['upperObjectiveShift'],
                   actionIntervalChecks=interval_checks, recordedReferenceConsistencyChecks=references,
                   referencePolicyVerification='FULL_POLICIES_REGENERATED_BY_JAVA_PHYSICAL_REPLAY_NOT_THIS_ORACLE')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('new_output', type=Path)
    args = parser.parse_args()
    checked = check(args.report)
    with args.new_output.open('x', encoding='utf-8', newline='\n') as output:
        json.dump(checked, output, indent=2, allow_nan=False)
        output.write('\n')
    print(f"Verified {len(checked['moves'])} joint losses, {len(checked['ownedWitnessChecks'])} joint witnesses and {len(checked['actionIntervalChecks'])} action intervals")
