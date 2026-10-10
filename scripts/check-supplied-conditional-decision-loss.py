"""Optional independent joint decision-loss oracle for the saved BB99 later decision.

NumPy/SciPy are offline research dependencies only. Uses literal contribution arithmetic and
saved integer showdown counts, without production betting, sequence-form, LP or response code.
"""

import argparse
import gzip
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.optimize import linprog


def literal(btn1, btn3, bb0, bb2, bb4, share):
    return np.where(bb0 == 0, 1.5,
        np.where(bb0 == 1, 6.5 * share - 3,
        np.where(btn1 == 0, -3,
        np.where(btn1 == 1, 18.5 * share - 9,
        np.where(bb2 == 0, 9.5,
        np.where(bb2 == 1, 44.5 * share - 22,
        np.where(btn3 == 0, -22,
        np.where(btn3 == 1, 100.5 * share - 50,
        np.where(bb4 == 0, 50.5, 200.5 * share - 100)))))))))


def close(expected, actual):
    if not np.isfinite(actual) or abs(expected - actual) > 2e-8:
        raise ValueError(f'Independent oracle disagreement: {expected} versus {actual}')


def check(path):
    raw = path.read_bytes()
    if len(raw) > 8 * 1024 * 1024:
        raise ValueError('Oracle input exceeds byte cap')
    if path.name.endswith('.gz'):
        with gzip.open(path, 'rb') as source:
            raw = source.read(8 * 1024 * 1024 + 1)
        if len(raw) > 8 * 1024 * 1024:
            raise ValueError('Expanded oracle input exceeds byte cap')
    report = json.loads(raw)
    request = report['request']
    expected_history = [dict(seat=s, action=a) for s, a in [
        ('UTG', 'fold'), ('HJ', 'fold'), ('CO', 'fold'), ('BTN', 'raise:3.0'), ('SB', 'fold')]]
    expected_rules = dict(stackBb=100.0, smallBlindBb=.5,
                          raiseToBb=[3.0, 9.0, 22.0, 50.0, 100.0], raiseSchedule='NEXT_TARGET')
    if (report['schemaVersion'] != 'pokerlab-supplied-conditional-decision-loss-report/v1'
            or report['trainerAdmission'] or request['hero'] != 5
            or request['informationSet'] != '5:supplied-conditional-preflop:9d 9h:|BB:raise:9.0|BTN:raise:22.0'
            or request['input']['specification'] != dict(rules=expected_rules, history=expected_history)
            or report['heroCommittedBb'] != 9 or request['minimumReach'] != .01):
        raise ValueError('Oracle supports only the explicitly declared five-target BB99 fixture')
    prior = report['prior']
    button = sorted({w['dealtCombos'][3] for w in prior})
    blind = sorted({w['dealtCombos'][5] for w in prior})
    if len(prior) != 27 or len(button) != 3 or len(blind) != 3:
        raise ValueError('Oracle requires the saved 27-world / three-by-three support')
    btn_plans, bb_plans = np.arange(729), np.arange(5832)
    matrix = np.zeros((729, 5832))
    denominator = np.zeros(729)
    numerators = {a: np.zeros((2 if a == 'raise:50.0' else 1, 729))
                  for a in ['fold', 'call', 'raise:50.0']}
    question_worlds = []
    for world in prior:
        a = btn_plans // (9 ** button.index(world['dealtCombos'][3])) % 9
        b = bb_plans // (18 ** blind.index(world['dealtCombos'][5])) % 18
        counts = report['payoffs'][world['dealIndex']]['counts']
        share = (counts['firstWins'] + .5 * counts['ties']) / counts['boards']
        p = world['conditionalProbability']
        matrix += p * literal((a % 3)[:, None], (a // 3)[:, None],
            (b % 3)[None, :], (b // 3 % 3)[None, :], (b // 9)[None, :], share)
        if world['dealtCombos'][5] == '9d 9h':
            question_worlds.append(world)
            reached = (a % 3 == 2)
            denominator += p * reached
            for action, bb2 in [('fold', 0), ('call', 1), ('raise:50.0', 2)]:
                for bb4 in range(len(numerators[action])):
                    # Active constant .5 plus BB's actual 9bb sunk contribution minus BTN utility.
                    numerators[action][bb4] += p * reached * (9.5 - literal(
                        a % 3, a // 3, np.full(729, 2), np.full(729, bb2), np.full(729, bb4), share))
    moves = {m['action']: m for m in report['moves']}
    if set(moves) != set(numerators):
        raise ValueError('Unexpected conditional moves')
    audits = {a['action']: a['interval'] for a in report['diagnostic']['actions']}
    value = audits['call']['baseline']['lowerValue']
    slack = request['securitySlack']
    security = -matrix.T
    rhs = np.full(5832, -(value - slack))
    options = dict(dual_feasibility_tolerance=1e-10, primal_feasibility_tolerance=1e-10)

    def solved(cost, **kwargs):
        result = linprog(cost, method='highs', options=options, **kwargs)
        if not result.success:
            raise ValueError(result.message)
        return result

    reach_args = dict(A_ub=security, b_ub=rhs, A_eq=np.ones((1, 729)), b_eq=[1], bounds=(0, None))
    reach_low = solved(denominator, **reach_args).fun
    reach_high = -solved(-denominator, **reach_args).fun
    if reach_low <= request['minimumReach']:
        raise ValueError('Full security face is not bounded away from zero reach')
    scaled = np.column_stack([security, -rhs])
    equality = np.stack([np.r_[np.ones(729), -1], np.r_[denominator, 0]])
    bounds = [(0, None)] * 729 + [(0, 1 / request['minimumReach'])]

    def independent_witness(mixture, rows):
        close(1, mixture.sum())
        global_value = float(np.min(matrix.T @ mixture))
        reach = float(denominator @ mixture)
        if np.min(mixture) < -1e-10 or global_value < value - slack - 2e-8 or reach < request['minimumReach']:
            raise ValueError('Independent original strategy/security/reach failure')
        return float(np.max(rows @ mixture) / reach), global_value, reach

    def reconstructed(witness):
        flow = witness['opponentFlow']
        if flow['actor'] != 3:
            raise ValueError('Expected BTN opponent flow')
        behavior = {}
        for row in flow['conservation']:
            parent = flow['realization'][row['parentSequence']]
            actions = [flow['sequences'][i]['action'] for i in row['childSequences']]
            ratios = np.array([flow['realization'][i] / parent
                if parent > 1e-12 else 1 / len(actions) for i in row['childSequences']])
            if np.min(ratios) < -1e-8 or np.max(ratios) > 1 + 1e-8:
                raise ValueError('Invalid owned behavioral ratio')
            probabilities = np.clip(ratios, 0, 1)
            probabilities /= probabilities.sum()
            behavior[row['key']] = dict(zip(actions, probabilities))
        mixture = np.ones(729)
        for hand_index, hand in enumerate(button):
            code = btn_plans // (9 ** hand_index) % 9
            for digit, history, actions in [
                (code % 3, '|BB:raise:9.0', ['fold', 'call', 'raise:22.0']),
                (code // 3, '|BB:raise:9.0|BTN:raise:22.0|BB:raise:50.0', ['fold', 'call', 'raise:100.0'])]:
                probabilities = behavior['3:supplied-conditional-preflop:' + hand + ':' + history]
                mixture *= np.array([probabilities[actions[i]] for i in digit])
        evs = {action: independent_witness(mixture, rows)[0] for action, rows in numerators.items()}
        _, global_value, reach = independent_witness(mixture, numerators['call'])
        close(.5 - global_value, witness['globalHeroBestResponse'])
        close(reach, witness['questionReach'])
        weights = [w['conditionalProbability'] * behavior[
            '3:supplied-conditional-preflop:' + w['dealtCombos'][3] + ':|BB:raise:9.0']['raise:22.0']
            for w in question_worlds]
        if len(weights) != len(witness['posterior']):
            raise ValueError('Missing posterior world')
        for weight, posterior in zip(weights, witness['posterior']):
            close(weight, posterior['behavioralWeight'])
            close(weight, posterior['originalRealizationWeight'])
            close(weight / reach, posterior['probability'])
        return mixture, evs, reach

    base = np.column_stack([scaled, np.zeros(5832)])
    eq = np.column_stack([equality, np.zeros(2)])
    joint = {m['action']: m for m in report['diagnostic']['moves']}
    output_moves, owned_checks = [], []
    for action, selected in numerators.items():
        others = [(name, index, row) for name, rows in numerators.items() if name != action
                  for index, row in enumerate(rows)]
        other_rows = np.stack([row for _, _, row in others])
        lower_objectives, upper_objectives = [], []
        for index, row in enumerate(selected):
            diff = other_rows - row
            problem = np.vstack([base, np.column_stack([diff, np.zeros(len(diff)), -np.ones(len(diff))])])
            result = solved(np.r_[np.zeros(730), 1], A_ub=problem, b_ub=np.zeros(len(problem)),
                A_eq=eq, b_eq=[0, 1], bounds=bounds + [(0, None)])
            mixture = result.x[:729] / result.x[729]
            evs = {a: independent_witness(mixture, rows)[0] for a, rows in numerators.items()}
            actual = max(0, max(evs.values()) - evs[action])
            if actual > result.fun + 2e-8:
                raise ValueError('Independent lower-control continuation disagreement')
            lower_objectives.append(float(result.fun))
        for _, _, row in others:
            diff = row - selected
            problem = np.vstack([base, np.column_stack([-diff, np.zeros(len(diff)), np.ones(len(diff))])])
            result = solved(np.r_[np.zeros(730), -1], A_ub=problem, b_ub=np.zeros(len(problem)),
                A_eq=eq, b_eq=[0, 1], bounds=bounds + [(None, None)])
            mixture = result.x[:729] / result.x[729]
            evs = {a: independent_witness(mixture, rows)[0] for a, rows in numerators.items()}
            actual = max(0, max(evs.values()) - evs[action])
            if actual + 2e-8 < -result.fun:
                raise ValueError('Independent upper-control continuation disagreement')
            upper_objectives.append(float(-result.fun))
        lower, upper = max(0, min(lower_objectives)), max(0, max(upper_objectives))
        move = moves[action]
        close(lower, move['lowerLossBb'])
        close(upper, move['upperLossBb'])
        close(lower, joint[action]['lowerLoss'])
        close(upper, joint[action]['upperLoss'])
        for kind, objectives in [('lower', lower_objectives), ('upper', upper_objectives)]:
            controls = joint[action][kind + 'Controls']
            if len(controls) != len(objectives):
                raise ValueError('Missing joint controls')
            for index, (control, objective) in enumerate(zip(controls, objectives)):
                close(objective, control['objective'])
                witness = control['witness']
                mixture, evs, reach = reconstructed(witness['selected'])
                for name, ev in evs.items():
                    close(ev, witness['actionUtilities'][name] + 9)
                close(evs[action], witness['selected']['conditionalHeroBestResponse'] + 9)
                loss = max(0, max(evs.values()) - evs[action])
                close(loss, witness['decisionLoss'])
                if kind == 'lower':
                    if control['selectedPlan'] != index:
                        raise ValueError('Wrong selected continuation')
                    planned = max(0, float(np.max((other_rows - selected[index]) @ mixture) / reach))
                else:
                    name, plan, row = others[index]
                    if (control['otherAction'], control['otherPlan']) != (name, plan):
                        raise ValueError('Wrong comparison continuation')
                    planned = float(np.min((row - selected) @ mixture) / reach)
                close(planned, control['objective'])
                if loss < lower - 2e-8 or loss > upper + 2e-8:
                    raise ValueError('Actual joint witness outside endpoints')
                if index == joint[action][kind + 'Control']:
                    close(lower if kind == 'lower' else upper, loss)
                owned_checks.append(dict(action=action, kind=kind, control=index,
                    conditionalEvsBb=evs, actualDecisionLossBb=loss,
                    plannedGapBb=planned, questionChanceOpponentMass=reach))
        for kind in ['Lower', 'Upper']:
            conservative = max(0, max(m[('lowerEvBb' if kind == 'Lower' else 'upperEvBb')]
                for a, m in moves.items() if a != action) - move[('upperEvBb' if kind == 'Lower' else 'lowerEvBb')])
            close(conservative, move['conservative' + kind + 'LossBb'])
        output_moves.append(dict(action=action, lowerLossBb=lower, upperLossBb=upper,
            lowerControlObjectives=lower_objectives, upperControlObjectives=upper_objectives))
    return dict(scope='OPTIONAL_INDEPENDENT_LITERAL_JOINT_LOSS_NORMAL_FORM_ORACLE_NO_ADMISSION',
        reportBytesSha256=hashlib.sha256(raw).hexdigest(), sourceInputHash=report['binding']['inputHash'],
        buttonCompletePlans=729, blindCompletePlans=5832, securitySlackBb=slack,
        minimumQuestionChanceOpponentMass=float(reach_low), maximumQuestionChanceOpponentMass=float(reach_high),
        moves=output_moves, ownedWitnessChecks=owned_checks, comparisonToleranceBb=2e-8)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('new_output', type=Path)
    args = parser.parse_args()
    checked = check(args.report)
    with args.new_output.open('x', encoding='utf-8', newline='\n') as output:
        json.dump(checked, output, indent=2, allow_nan=False)
        output.write('\n')
    print(f"Verified {len(checked['moves'])} joint intervals and {len(checked['ownedWitnessChecks'])} owned witnesses")
