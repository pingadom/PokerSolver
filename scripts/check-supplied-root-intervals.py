"""Optional independent literal normal-form oracle for the saved five-target AJ root decision.

Requires NumPy/SciPy only for this offline cross-check, never for PokerLab runtime or CI.
It reuses saved integer showdown counts but no production betting, sequence-form, LP, or BR code.
"""

import argparse
import gzip
import hashlib
import json
from pathlib import Path

import numpy as np
from scipy.optimize import linprog


def literal(btn1, btn3, bb0, bb2, bb4, share):
    """BTN terminal utility from literal contributions and the five public raise targets."""
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
    if (report['schemaVersion'] != 'pokerlab-supplied-root-decision-interval-report/v1'
            or report['trainerAdmission'] or request['hero'] != 5
            or request['informationSet'] != '5:supplied-conditional-preflop:Ac Jc:'
            or request['input']['specification'] != dict(rules=expected_rules, history=expected_history)
            or report['heroCommittedBb'] != 1):
        raise ValueError('Oracle supports only the explicitly declared five-target AJ fixture')
    prior = report['prior']
    button = sorted({w['dealtCombos'][3] for w in prior})
    blind = sorted({w['dealtCombos'][5] for w in prior})
    if len(prior) != 27 or len(button) != 3 or len(blind) != 3:
        raise ValueError('Oracle requires the saved 27-world / three-by-three support')
    btn_plans, bb_plans = np.arange(729), np.arange(5832)
    matrix = np.zeros((729, 5832))
    conditional = np.zeros((729, 6))
    mass = sum(w['conditionalProbability'] for w in prior if w['dealtCombos'][5] == 'Ac Jc')
    call_ev = 0.0
    for world in prior:
        a = btn_plans // (9 ** button.index(world['dealtCombos'][3])) % 9
        b = bb_plans // (18 ** blind.index(world['dealtCombos'][5])) % 18
        counts = report['payoffs'][world['dealIndex']]['counts']
        share = (counts['firstWins'] + .5 * counts['ties']) / counts['boards']
        matrix += world['conditionalProbability'] * literal(
            (a % 3)[:, None], (a // 3)[:, None], (b % 3)[None, :],
            (b // 3 % 3)[None, :], (b // 9)[None, :], share)
        if world['dealtCombos'][5] == 'Ac Jc':
            p = world['conditionalProbability'] / mass
            response = np.arange(6)
            conditional += p * literal((a % 3)[:, None], (a // 3)[:, None],
                np.array([[2]]), (response % 3)[None, :], (response // 3)[None, :], share)
            call_ev += p * (4.5 - 6.5 * share)
    counterfactual = (1.5 - conditional).T  # BB incremental EV, with the 1bb root commitment.
    moves = {m['action']: m for m in report['moves']}
    if set(moves) != {'fold', 'call', 'raise:9.0'}:
        raise ValueError('Unexpected root moves')
    raise_report = moves['raise:9.0']['diagnostic']
    value = raise_report['baseline']['lowerValue']  # BTN value; both active utilities sum to .5.
    slack = request['securitySlack']
    security = -matrix.T
    rhs = np.full(5832, -(value - slack))
    options = dict(dual_feasibility_tolerance=1e-10, primal_feasibility_tolerance=1e-10)

    def checked_lp(result):
        if not result.success:
            raise ValueError(result.message)
        policy = result.x[:729]
        security_value = float(np.min(matrix.T @ policy))
        close(1, np.sum(policy))
        if np.min(policy) < -1e-10 or security_value < value - slack - 2e-8:
            raise ValueError('Independent normal-form security violation')
        return float(np.max(counterfactual @ policy)), security_value

    upper_controls = []
    for plan in range(6):
        result = linprog(-counterfactual[plan], A_ub=security, b_ub=rhs,
            A_eq=np.ones((1, 729)), b_eq=[1], bounds=(0, None), method='highs', options=options)
        ev, security_value = checked_lp(result)
        upper_controls.append(dict(plan=plan, optimalContinuationEvBb=ev, securityValueBb=security_value))
    result = linprog(np.r_[np.zeros(729), 1],
        A_ub=np.block([[security, np.zeros((5832, 1))], [counterfactual, -np.ones((6, 1))]]),
        b_ub=np.r_[rhs, np.zeros(6)], A_eq=np.r_[np.ones(729), 0][None, :], b_eq=[1],
        bounds=[(0, None)] * 729 + [(None, None)], method='highs', options=options)
    lower, lower_security = checked_lp(result)
    close(lower, result.fun)
    upper = max(c['optimalContinuationEvBb'] for c in upper_controls)
    close(lower, moves['raise:9.0']['lowerEvBb'])
    close(upper, moves['raise:9.0']['upperEvBb'])
    for action, ev in [('fold', 0), ('call', call_ev)]:
        close(ev, moves[action]['lowerEvBb'])
        close(ev, moves[action]['upperEvBb'])

    # Convert each owned opponent realization to a complete-plan mixture, independently revalue
    # all 5,832 BB deviations and six conditional continuations using the literal matrix above.
    witnesses = []
    for witness in [raise_report['lowerWitness'], *raise_report['upperWitnesses']]:
        flow = witness['opponentFlow']
        if flow['actor'] != 3:
            raise ValueError('Expected a BTN security witness')
        behavior = {}
        for row in flow['conservation']:
            parent = flow['realization'][row['parentSequence']]
            actions = [flow['sequences'][i]['action'] for i in row['childSequences']]
            probabilities = np.array([min(1, max(0, flow['realization'][i] / parent))
                if parent > 1e-12 else 1 / len(actions) for i in row['childSequences']], dtype=float)
            probabilities /= np.sum(probabilities)
            behavior[row['key']] = dict(zip(actions, probabilities))
        mixture = np.ones(729)
        for hand_index, hand in enumerate(button):
            code = btn_plans // (9 ** hand_index) % 9
            for digit, history, actions in [
                (code % 3, '|BB:raise:9.0', ['fold', 'call', 'raise:22.0']),
                (code // 3, '|BB:raise:9.0|BTN:raise:22.0|BB:raise:50.0', ['fold', 'call', 'raise:100.0'])]:
                probabilities = behavior['3:supplied-conditional-preflop:' + hand + ':' + history]
                mixture *= np.array([probabilities[actions[i]] for i in digit])
        close(1, np.sum(mixture))
        security_value = float(np.min(matrix.T @ mixture))
        local = float(np.max(counterfactual @ mixture))
        close(local, witness['conditionalHeroBestResponse'] + 1)
        close(.5 - security_value, witness['globalHeroBestResponse'])
        if security_value < value - slack - 2e-8:
            raise ValueError('Owned witness fails independent complete-plan security check')
        witnesses.append(dict(securityValueBb=security_value, conditionalEvBb=local))
    for action, move in moves.items():
        other = [m for a, m in moves.items() if a != action]
        close(max(0, max(m['lowerEvBb'] for m in other) - move['upperEvBb']), move['lowerRegretBb'])
        close(max(0, max(m['upperEvBb'] for m in other) - move['lowerEvBb']), move['upperRegretBb'])
    return dict(scope='OPTIONAL_INDEPENDENT_LITERAL_NORMAL_FORM_NUMERICAL_ORACLE_NO_ADMISSION',
        reportBytesSha256=hashlib.sha256(raw).hexdigest(), sourceInputHash=report['binding']['inputHash'],
        buttonCompletePlans=729, blindCompletePlans=5832, heroContinuationPlans=6,
        securitySlackBb=slack, lowerEvBb=lower, upperEvBb=upper, callEvBb=call_ev,
        lowerSecurityValueBb=lower_security, allUpperControls=upper_controls,
        ownedWitnessChecks=witnesses, comparisonToleranceBb=2e-8)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('new_output', type=Path)
    args = parser.parse_args()
    checked = check(args.report)
    with args.new_output.open('x', encoding='utf-8', newline='\n') as output:
        json.dump(checked, output, indent=2, allow_nan=False)
        output.write('\n')
    print(f"Verified literal normal-form interval [{checked['lowerEvBb']}, {checked['upperEvBb']}] bb")
