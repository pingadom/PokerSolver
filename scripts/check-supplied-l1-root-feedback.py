"""Optional independent complete-plan oracle for the three saved L1 root questions.

Uses the declared five-target fixture's integer showdown counts and literal contribution
arithmetic. It does not regenerate boards or reference policies: the owned Java physical
replay performs those checks. Every endpoint LP and behavioral witness check is fresh;
NumPy/SciPy remain offline research dependencies, never service dependencies.
"""

import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
import scipy
from scipy.optimize import linprog

spec = importlib.util.spec_from_file_location(
    'literal_joint', Path(__file__).with_name('check-supplied-conditional-decision-loss.py'))
literal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(literal)
close = literal.close
MAX_BYTES = 8 * 1024 * 1024
HANDS = {'7d 7h', '9d 9h', 'Ac Jc'}
BASE = Path(__file__).resolve().parents[1] / 'docs/data/supplied-conditional-five-target-99-interval-feedback-report.json'


def read(path):
    stored = path.read_bytes()
    if len(stored) > MAX_BYTES:
        raise ValueError('Stored report exceeds byte cap')
    if path.name.endswith('.gz'):
        with gzip.open(path, 'rb') as source:
            raw = source.read(MAX_BYTES + 1)
    else:
        raw = stored
    if len(raw) > MAX_BYTES:
        raise ValueError('Expanded report exceeds byte cap')
    return json.loads(raw), raw, hashlib.sha256(stored).hexdigest()


def validate(report, base):
    r, diagnostic = report['request'], report['diagnostic']
    hand = r['informationSet'].split(':')[2]
    if (report['schemaVersion'] != 'pokerlab-supplied-interval-feedback-report/v2'
            or r['schemaVersion'] != 'pokerlab-supplied-interval-feedback-request/v2'
            or report['publicationStatus'] != 'SELECTED_QUESTION_INTERVAL_FEEDBACK_RESEARCH_ONLY'
            or report['trainerAdmission'] is not False
            or report['qualifiedForIntervalFeedback'] is not True or report['rejectionReasons']
            or r['hero'] != 5 or hand not in HANDS
            or r['informationSet'] != f'5:supplied-conditional-preflop:{hand}:'
            or r['securitySlack'] != 1e-4 or r['minimumReach'] != .01 or r['maximumLossBb'] != .01
            or report['heroCommittedBb'] != 1
            or diagnostic['representation'] != 'UPPER_OBJECTIVE_SHIFT_STABLE_ASCENDING_L1_JOINT_ROWS_ORIGINAL_CERTIFICATES/v2'
            or diagnostic['joint']['algorithm'] != 'CONDITIONED_STABLE_L1_JOINT_CONDITIONAL_DECISION_LOSS_OWNED_LP/v3'
            or diagnostic['joint']['trainerAdmission'] is not False):
        raise ValueError('Unsupported root qualification identity or scope')
    if (r['input'] != base['request']['input'] or report['binding'] != base['binding']
            or report['prior'] != base['prior'] or report['payoffs'] != base['payoffs']):
        raise ValueError('Oracle only supports the declared physically replayed 27-world fixture')
    actions = ['fold', 'call', 'raise:9.0']
    if (len(report['prior']) != 27
            or [m['action'] for m in report['moves']] != actions
            or [m['action'] for m in diagnostic['joint']['actions']] != actions
            or [m['action'] for m in diagnostic['joint']['moves']] != actions
            or [r['iterations'] for r in report['referenceChecks']] != [500, 1000]):
        raise ValueError('Incomplete ordered decision or reference inventory')
    for a in diagnostic['joint']['actions']:
        if a['interval']['algorithm'] != 'POSITIVE_REACH_UPPER_OBJECTIVE_SHIFT_OWNED_LP/v2':
            raise ValueError('Action controls must retain their original representation')
    return hand


class Oracle:
    def __init__(self, report):
        self.prior = report['prior']
        self.hands = sorted({w['dealtCombos'][3] for w in self.prior})
        self.blind = sorted({w['dealtCombos'][5] for w in self.prior})
        if len(self.hands) != 3 or len(self.blind) != 3:
            raise ValueError('Expected three private hands per active player')
        self.btn, bb = np.arange(729), np.arange(5832)
        self.matrix = np.zeros((729, 5832))
        self.worlds = []
        self.solves = 0
        close(1, sum(w['conditionalProbability'] for w in self.prior))
        for w in self.prior:
            a = self.btn // 9 ** self.hands.index(w['dealtCombos'][3]) % 9
            b = bb // 18 ** self.blind.index(w['dealtCombos'][5]) % 18
            count = report['payoffs'][w['dealIndex']]['counts']
            if count['boards'] <= 0 or count['firstWins'] + count['ties'] + count['secondWins'] != count['boards']:
                raise ValueError('Invalid integer showdown counts')
            share = (count['firstWins'] + .5 * count['ties']) / count['boards']
            self.matrix += w['conditionalProbability'] * literal.literal(
                (a % 3)[:, None], (a // 3)[:, None], (b % 3)[None, :],
                (b // 3 % 3)[None, :], (b // 9)[None, :], share)
            self.worlds.append((w, a, share))
        self.lower = -self.solve(np.r_[np.zeros(729), -1],
            A_ub=np.column_stack([-self.matrix.T, np.ones(5832)]), b_ub=np.zeros(5832),
            A_eq=np.r_[np.ones(729), 0][None, :], b_eq=[1],
            bounds=[(0, None)] * 729 + [(None, None)])
        self.upper = self.solve(np.r_[np.zeros(5832), 1],
            A_ub=np.column_stack([self.matrix, -np.ones(729)]), b_ub=np.zeros(729),
            A_eq=np.r_[np.ones(5832), 0][None, :], b_eq=[1],
            bounds=[(0, None)] * 5832 + [(None, None)])
        close(self.lower, self.upper)

    def solve(self, cost, **kwargs):
        self.solves += 1
        result = linprog(cost, method='highs', options=dict(
            dual_feasibility_tolerance=1e-10, primal_feasibility_tolerance=1e-10), **kwargs)
        if not result.success:
            raise ValueError(result.message)
        return result.fun

    def mixture(self, witness):
        flow, behavior = witness['opponentFlow'], {}
        if flow['actor'] != 3:
            raise ValueError('Expected BTN opponent flow')
        for row in flow['conservation']:
            parent = flow['realization'][row['parentSequence']]
            children = row['childSequences']
            ratios = np.array([flow['realization'][i] / parent if parent > 1e-12
                               else 1 / len(children) for i in children])
            if ratios.min() < -1e-8 or ratios.max() > 1 + 1e-8:
                raise ValueError('Invalid original behavioral ratio')
            ratios = np.clip(ratios, 0, 1)
            ratios /= ratios.sum()
            behavior[row['key']] = dict(zip([flow['sequences'][i]['action'] for i in children], ratios))
        mixture = np.ones(729)
        for i, hand in enumerate(self.hands):
            code = self.btn // 9 ** i % 9
            for digit, history, names in [
                (code % 3, '|BB:raise:9.0', ['fold', 'call', 'raise:22.0']),
                (code // 3, '|BB:raise:9.0|BTN:raise:22.0|BB:raise:50.0', ['fold', 'call', 'raise:100.0'])]:
                probs = behavior['3:supplied-conditional-preflop:' + hand + ':' + history]
                mixture *= np.array([probs[names[j]] for j in digit])
        close(1, mixture.sum())
        return mixture

    def check(self, report, hand):
        diagnostic = report['diagnostic']['joint']
        audits = {a['action']: a['interval'] for a in diagnostic['actions']}
        close(self.lower, audits['call']['baseline']['lowerValue'])
        close(self.upper, audits['call']['baseline']['upperValue'])
        denominator = sum(w['conditionalProbability'] for w in self.prior if w['dealtCombos'][5] == hand)
        close(1 / 3, denominator)
        plans = np.zeros((18, 729))
        for w, a, share in self.worlds:
            if w['dealtCombos'][5] != hand:
                continue
            for j in range(18):
                # BB's root 1bb is sunk. Active utility sum=.5 (folded SB).
                plans[j] += w['conditionalProbability'] * (.5 - literal.literal(
                    a % 3, a // 3, j % 3, j // 3 % 3, j // 9, share)) / denominator
        utilities = {action: np.unique(plans[np.arange(18) % 3 == i], axis=0)
                     for i, action in enumerate(['fold', 'call', 'raise:9.0'])}
        common = dict(A_ub=-self.matrix.T, b_ub=np.full(5832, -(self.lower - 1e-4)),
                      A_eq=np.ones((1, 729)), b_eq=[1], bounds=(0, None))

        def epigraph(diff, minimize, nonnegative):
            return self.solve(np.r_[np.zeros(729), 1 if minimize else -1],
                A_ub=np.vstack([np.column_stack([common['A_ub'], np.zeros(5832)]),
                               np.column_stack([diff, (-1 if minimize else 1) * np.ones(len(diff))])]),
                b_ub=np.r_[common['b_ub'], np.zeros(len(diff))],
                A_eq=np.r_[np.ones(729), 0][None, :], b_eq=[1],
                bounds=[(0, None)] * 729 + [(0, None) if nonnegative else (None, None)])

        endpoints, checked = {}, 0
        for action, selected in utilities.items():
            other = np.concatenate([v for a, v in utilities.items() if a != action])
            low_ev = epigraph(selected, True, False)
            high_ev = max(-self.solve(-p, **common) for p in selected)
            low_loss = min(epigraph(other - p, True, True) for p in selected)
            high_loss = max(0, max(-epigraph(selected - p, False, False) for p in other))
            endpoints[action] = dict(lowerEv=low_ev, upperEv=high_ev, lowerLoss=low_loss, upperLoss=high_loss)
            interval = audits[action]
            close(low_ev, interval['lowerUtility'])
            close(high_ev, interval['upperUtility'])
            move = next(m for m in report['moves'] if m['action'] == action)
            close(low_ev + 1, move['lowerEvBb'])
            close(high_ev + 1, move['upperEvBb'])
            close(low_loss, move['lowerLossBb'])
            close(high_loss, move['upperLossBb'])
            expected = ('WITHIN_LIMIT' if move['upperLossBb'] <= .01 else
                        'OUTSIDE_LIMIT' if move['lowerLossBb'] > .01 else 'STRATEGY_DEPENDENT')
            if move['classification'] != expected:
                raise ValueError('Classification differs from original endpoints')
        for move in diagnostic['moves']:
            close(endpoints[move['action']]['lowerLoss'], move['lowerLoss'])
            close(endpoints[move['action']]['upperLoss'], move['upperLoss'])
            for control in move['lowerControls'] + move['upperControls']:
                witness, selected = control['witness'], control['witness']['selected']
                if selected['certificate']['algorithm'] != 'OWNED_STABLE_ASCENDING_L1_ROWS_ORIGINAL_CERTIFICATE/v2':
                    raise ValueError('Joint witness is not certified against original rows')
                mixture = self.mixture(selected)
                br = .5 - float(np.min(self.matrix.T @ mixture))
                if br > .5 - self.lower + 1e-4 + 2e-8:
                    raise ValueError('Independent full-plan security failure')
                close(br, selected['globalHeroBestResponse'])
                close(denominator, selected['questionReach'])
                worlds = [w for w in self.prior if w['dealtCombos'][5] == hand]
                if len(worlds) != len(selected['posterior']):
                    raise ValueError('Missing posterior world')
                for world, posterior in zip(worlds, selected['posterior']):
                    close(world['conditionalProbability'], posterior['behavioralWeight'])
                    close(world['conditionalProbability'], posterior['originalRealizationWeight'])
                    close(world['conditionalProbability'] / denominator, posterior['probability'])
                evs = {a: float(np.max(v @ mixture)) for a, v in utilities.items()}
                close(evs[move['action']], selected['conditionalHeroBestResponse'])
                for action, ev in evs.items():
                    close(ev, witness['actionUtilities'][action])
                close(max(0, max(evs.values()) - evs[move['action']]), witness['decisionLoss'])
                checked += 1
        for reference in report['referenceChecks']:
            if (not reference['insideDeclaredFace'] or not reference['boundsChecked']
                    or reference['failures']):
                raise ValueError('Reference did not pass qualification')
            close(reference['globalHeroUpperBb'] + 1e-4 - reference['globalHeroBestResponseBb'],
                  reference['inclusionMarginBb'])
            if reference['inclusionMarginBb'] < -1e-8:
                raise ValueError('Reference outside declared security face')
            evs = reference['decision']['values']['actionEvBb']
            for move in report['moves']:
                loss = max(0, max(evs.values()) - evs[move['action']])
                close(loss, reference['actionLossBb'][move['action']])
                if (not move['lowerEvBb'] - 1e-8 <= evs[move['action']] <= move['upperEvBb'] + 1e-8
                        or not move['lowerLossBb'] - 1e-8 <= loss <= move['upperLossBb'] + 1e-8):
                    raise ValueError('Saved reference row outside decision bounds')
        return dict(informationSet=report['request']['informationSet'], endpoints=endpoints,
                    checkedJointWitnesses=checked, recordedReferenceRowsChecked=2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Oracle output must be a new path')
    base, _, base_hash = read(BASE)
    records, seen = [], set()
    for path in args.reports:
        report, raw, stored_hash = read(path)
        hand = validate(report, base)
        if hand in seen:
            raise ValueError('Duplicate question is not additional coverage')
        seen.add(hand)
        records.append((report, hand, dict(storedBytesSha256=stored_hash,
                        expandedBytesSha256=hashlib.sha256(raw).hexdigest())))
    oracle = Oracle(base)
    results = []
    for report, hand, hashes in records:
        results.append(dict(**hashes, **oracle.check(report, hand)))
        print('CHECKED', hand, results[-1]['checkedJointWitnesses'], flush=True)
    summary = dict(schemaVersion='pokerlab-independent-l1-root-feedback-check/v1',
        oracle='LITERAL_COMPLETE_PLANS_SCIPY_HIGHS_FRESH_EXTREMA_AND_WITNESSES/v1',
        numpyVersion=np.__version__, scipyVersion=scipy.__version__, sourceFixtureBytesSha256=base_hash,
        scriptBytesSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        trainerAdmission=False, sourceHistoryReachAvailable=False,
        independentLowerValue=oracle.lower, independentUpperValue=oracle.upper,
        freshLpSolves=oracle.solves, cachedEndpointCases=0, rows=results,
        limitations=['Uses saved integer showdown counts; Java replay regenerates physical boards',
                     'Checks recorded reference rows; Java replay regenerates CFR reference policies',
                     'Two active players, supplied priors, no rake, mandatory postflop checkdown'])
    with args.output.open('x', encoding='utf-8') as out:
        out.write(json.dumps(summary, indent=2, allow_nan=False) + '\n')


if __name__ == '__main__':
    main()
