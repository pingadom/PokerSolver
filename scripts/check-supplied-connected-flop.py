"""Independent fixed-case physical/card, literal-tree and pure best-response check; no optimizer.

Full preflop counts come from a byte-pinned, previously physically replayed artifact. Fixed-flop
counts are regenerated here using literal best-of-21 five-card rankings. Java replay regenerates
both and reruns both LPs. This checker does not independently certify the LP matrices.
"""
import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import gzip
import hashlib
from itertools import combinations
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PARENT = ROOT / 'docs/data/supplied-four-button-hand-reach-bounds-report.json.gz'
PARENT_BYTES = 'dd73681551f5489052a7934cbd86a8704e25208a3226f2ec0a1d5c13c6948ead'
LIMIT = 8 * 1024 * 1024
RANKS = '23456789TJQKA'
DECK = tuple((r+2, s) for r in range(13) for s in 'cdhs')
SEATS = ('UTG', 'HJ', 'CO', 'BTN', 'SB', 'BB')
FLOP = ('2s', '8s', 'Ts')
P = 1 / 9880
FLOOR = .0001


def fail(message):
    raise ValueError(message)


def require(condition, message):
    if not condition:
        fail(message)


def close(actual, expected, message, mass=False):
    require(isinstance(actual, (int, float)) and math.isfinite(actual), message)
    require(math.isclose(actual, expected, rel_tol=1e-10 if mass else 0,
                         abs_tol=0 if mass else 1e-9), message)


def read(path):
    with path.open('rb') as stream:
        data = stream.read(LIMIT + 1)
    require(len(data) <= LIMIT, 'Stored byte cap exceeded')
    if path.suffix == '.gz':
        import io
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
            expanded = stream.read(LIMIT + 1)
    else:
        expanded = data
    require(len(expanded) <= LIMIT, 'Expanded byte cap exceeded')

    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result

    def number(text):
        result = float(text)
        require(math.isfinite(result), 'Non-finite JSON number')
        return result

    value = json.loads(expanded, object_pairs_hook=pairs, parse_float=number,
                       parse_constant=lambda _: fail('Non-finite JSON constant'))
    return value, data, expanded


def canonical_input(value):
    value = json.loads(json.dumps(value))
    for world in value['worlds']:
        world['dealtCombos'] = [' '.join(sorted(c.split())) for c in world['dealtCombos']]
    value['worlds'].sort(key=lambda w: '|'.join(w['dealtCombos']))
    return value


def card(text):
    return RANKS.index(text[0])+2, text[1]


def five(cards):
    ranks = sorted((c[0] for c in cards), reverse=True)
    counts = Counter(ranks)
    groups = sorted(((n, r) for r, n in counts.items()), reverse=True)
    flush = len({c[1] for c in cards}) == 1
    straight = (ranks[0] if len(counts) == 5 and ranks[0]-ranks[4] == 4 else
                5 if ranks == [14, 5, 4, 3, 2] else 0)
    if flush and straight:
        return 8, straight
    if groups[0][0] == 4:
        return 7, groups[0][1], groups[1][1]
    if [g[0] for g in groups] == [3, 2]:
        return 6, groups[0][1], groups[1][1]
    if flush:
        return (5, *ranks)
    if straight:
        return 4, straight
    if groups[0][0] == 3:
        return (3, groups[0][1], *sorted((r for n, r in groups[1:]), reverse=True))
    pairs = sorted((r for n, r in groups if n == 2), reverse=True)
    kickers = sorted((r for n, r in groups if n == 1), reverse=True)
    if pairs:
        return (2 if len(pairs) == 2 else 1, *pairs, *kickers)
    return (0, *ranks)


def seven(cards):
    return max(map(five, combinations(cards, 5)))


def count_flop(world):
    hands = [tuple(map(card, h.split())) for h in world['dealtCombos']]
    flop = tuple(map(card, FLOP))
    dead = {c for h in hands for c in h} | set(flop)
    require(len(dead) == 15, 'Physical card collision')
    remaining = tuple(c for c in DECK if c not in dead)
    wins = losses = ties = 0
    for runout in combinations(remaining, 2):
        a, b = seven(hands[3]+flop+runout), seven(hands[5]+flop+runout)
        wins += a > b
        losses += a < b
        ties += a == b
    require(wins+losses+ties == 666, 'Physical runout count differs')
    return wins, losses, ties


def utility(committed, share=None, winner=None):
    values = [-x for x in committed]
    pot = math.fsum(committed)
    if winner is not None:
        values[winner] += pot
    else:
        values[3] += pot*share
        values[5] += pot*(1-share)
    close(math.fsum(values), 0, 'Literal chip conservation failed')
    return tuple(values)


@dataclass(eq=False)
class Node:
    actor: int
    key: str = ''
    edges: tuple = ()
    utilities: tuple = ()


def evaluate(node, policy, target=None, response=None):
    if node.actor == -2:
        return node.utilities
    if node.actor == -1:
        weights = node.edges
    elif node.actor == target:
        action = response[node.key]
        weights = tuple((1.0, child) for label, child in node.edges if label == action)
    else:
        weights = tuple((policy[node.key][label], child) for label, child in node.edges)
    outcomes = [(p, evaluate(child, policy, target, response)) for p, child in weights if p > 0]
    return tuple(math.fsum(p*v[seat] for p, v in outcomes) for seat in range(6))


def best_response(root, policy, target):
    infos = defaultdict(list)
    identities = {}

    def collect(node, reach, depth, own):
        if node.actor == -2:
            return
        if node.actor == target:
            identity = (depth, tuple(a for a, _ in node.edges), own)
            require(node.key not in identities or identities[node.key] == identity,
                    'Independent perfect recall failed')
            identities[node.key] = identity
            infos[node.key].append((node, reach))
        for label, child in node.edges:
            factor = label if node.actor == -1 else 1 if node.actor == target else policy[node.key][label]
            collect(child, reach*factor, depth+1, own+((node.key, label),) if node.actor == target else own)

    collect(root, 1.0, 0, ())
    response = {}
    for key in sorted(infos, key=lambda k: identities[k][0], reverse=True):
        actions = identities[key][1]
        values = []
        for action in actions:
            values.append(math.fsum(reach*evaluate(dict(node.edges)[action], policy, target, response)[target]
                                    for node, reach in infos[key]))
        response[key] = actions[max(range(len(actions)), key=lambda i: values[i])]
    return evaluate(root, policy, target, response)[target]


def quality(root, policy):
    profile = evaluate(root, policy)
    best = list(profile)
    for target in (3, 5):
        best[target] = best_response(root, policy, target)
    gains = [max(0, best[i]-profile[i]) for i in range(6)]
    return profile, best, gains, math.fsum(gains)


def compare_quality(actual, expected):
    for field, values in zip(('profileUtilitiesBb', 'bestResponseUtilitiesBb', 'deviationGainsBb'), expected[:3]):
        require(len(actual[field]) == 6, 'Quality seat count differs')
        for a, b in zip(actual[field], values):
            close(a, b, 'Independent literal best-response disagreement')
    close(actual['nashConvBb'], expected[3], 'Independent literal best-response gap differs')


def check(request_path, report_path, *, include_context=False):
    request, request_bytes, _ = read(request_path)
    report, report_bytes, report_expanded = read(report_path)
    parent, parent_bytes, _ = read(PARENT)
    require(hashlib.sha256(parent_bytes).hexdigest() == PARENT_BYTES, 'Pinned source artifact differs')
    require(set(request) == {'schemaVersion', 'input', 'flop', 'betPotFraction'}
            and request['schemaVersion'] == 'pokerlab-supplied-connected-flop-request/v1'
            and tuple(sorted(request['flop'])) == FLOP and request['betPotFraction'] == .5
            and canonical_input(request['input']) == parent['request']['input'], 'Unsupported fixed-case request')
    require(set(report) == {'schemaVersion', 'publicationStatus', 'trainerAdmission', 'request', 'binding',
            'originalJointPrior', 'sourcePayoffs', 'handoffs', 'residuals', 'solutionHash', 'candidate', 'solve',
            'reachedFlopQuality'}, 'Unsupported report fields')
    require(report['schemaVersion'] == 'pokerlab-supplied-connected-flop-report/v1'
            and report['publicationStatus'] == 'CONNECTED_SINGLE_FLOP_RESEARCH_ONLY'
            and report['trainerAdmission'] is False, 'Unsupported connected identity')
    declared = dict(request, input=canonical_input(request['input']), flop=list(FLOP))
    require(report['request'] == declared and report['originalJointPrior'] == parent['prior']
            and report['sourcePayoffs'] == parent['payoffs'], 'Original physical prior/payoffs differ')
    binding = report['binding']
    require(binding['model'] == 'SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1'
            and binding['chanceModel'] == 'ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1'
            and binding['trainerAdmission'] is False and binding['sourceHistoryReachStatus'] == 'UNAVAILABLE_NO_SOURCE_POLICY'
            and binding['sourceBinding'] == parent['binding'] and binding['flop'] == list(FLOP)
            and binding['betPotFraction'] == .5, 'Connected model or source binding differs')
    require(binding['budget'] == dict(sourceBudget=parent['binding']['budget'], nonAllInBoundaries=4,
            compatibleDeals=24, completeTreeStates=1321, informationSets={'BTN': 40, 'BB': 33},
            sequences={'BTN': 89, 'BB': 73}, additionalEnumeratedRunouts=63936,
            additionalHandEvaluations=127872), 'Connected budget differs')
    policy = report['candidate']['strategy']
    require(report['candidate']['iterations'] == 1 and len(policy) == 73, 'Candidate policy dimensions differ')
    for row in policy.values():
        require(all(math.isfinite(p) and 0 <= p <= 1 for p in row.values()), 'Invalid policy probability')
        close(math.fsum(row.values()), 1, 'Policy row does not sum to one')

    hands = report['originalJointPrior']
    physical = [count_flop(w) for w in hands]
    handoffs = {h['publicHistory']: h for h in report['handoffs']}
    residuals = {(r['publicHistory'], r['originalDealIndex']): r for r in report['residuals']}
    require(len(handoffs) == 4 and len(residuals) == 96, 'Continuation inventory differs')
    selected = {}
    public = {}
    rows_used = set()
    node_count = 0

    def decision(actor, key, children):
        require(key in policy and set(policy[key]) == {a for a, _ in children}, 'Candidate action identity differs')
        rows_used.add(key)
        return Node(actor, key, tuple(children))

    def postflop(i, history, committed, path=''):
        nonlocal node_count
        node_count += 1
        share = (physical[i][0]+.5*physical[i][2])/666
        amount = min(100-committed[3], math.floor(math.fsum(committed)*.5*1e6+.5)/1e6)
        if path in ('check|check', 'bet|fold', 'bet|call', 'check|bet|fold', 'check|bet|call'):
            chips = list(committed)
            if path != 'check|check':
                bettor = 5 if path.startswith('bet') else 3
                chips[bettor] += amount
                if path.endswith('call'):
                    chips[3 if bettor == 5 else 5] += amount
                if path.endswith('fold'):
                    return Node(-2, utilities=utility(chips, winner=bettor))
            return Node(-2, utilities=utility(chips, share))
        actor = 5 if path in ('', 'check|bet') else 3
        actions = ('fold', 'call') if path in ('bet', 'check|bet') else ('check', 'bet')
        key = f'{actor}:supplied-flop-one-bet:{hands[i]["dealtCombos"][actor]}:{history}:2s 8s Ts:{path}'
        return decision(actor, key, [(a, postflop(i, history, committed, path+'|'+a if path else a)) for a in actions])

    def continuation(i, history, committed, actions):
        nonlocal node_count
        # The boundary itself is counted by the preflop caller; add residual and nine flop nodes.
        node_count += 1
        flop_root = postflop(i, history, committed)
        selected[i, history] = flop_root
        full = report['sourcePayoffs'][i]['counts']
        residual = tuple(10*full[k]-v for k, v in zip(('firstWins', 'secondWins', 'ties'), physical[i]))
        denominator = 6579414
        share = (residual[0]+.5*residual[2])/denominator
        values = utility(committed, share)
        saved = residuals[history, i]
        require(saved['counts'] == dict(firstSeat=3, secondSeat=5, firstWins=residual[0],
                secondWins=residual[1], ties=residual[2], boardFlopPartitions=denominator), 'Residual partition counts differ')
        for a, b in zip(saved['utilitiesBb'], values):
            close(a, b, 'Residual literal settlement differs')
        public[history] = (committed, actions)
        return Node(-1, edges=((P, flop_root), (1-P, Node(-2, utilities=values))))

    def preflop(i, level=0, history='', committed=(0, 0, 0, 3, .5, 1), actions=()):
        nonlocal node_count
        node_count += 1
        actor = 5 if level % 2 == 0 else 3
        opponent = 3 if actor == 5 else 5
        fold = Node(-2, utilities=utility(committed, winner=opponent))
        matched = list(committed)
        matched[actor] = matched[opponent]
        call_history = history+f'|{SEATS[actor]}:call'
        call_actions = actions+('call',)
        node_count += 2  # fold leaf and matched call boundary/leaf.
        if level < 4:
            called = continuation(i, call_history, tuple(matched), call_actions)
        else:
            counts = report['sourcePayoffs'][i]['counts']
            called = Node(-2, utilities=utility(matched, (counts['firstWins']+.5*counts['ties'])/658008))
        children = [('fold', fold), ('call', called)]
        if level < 4:
            target = (9, 22, 50, 100)[level]
            raised = list(committed)
            raised[actor] = target
            action = f'raise:{target}.0'
            children.append((action, preflop(i, level+1, history+f'|{SEATS[actor]}:{action}', tuple(raised), actions+(action,))))
        key = f'{actor}:supplied-connected-single-flop:supplied-conditional-preflop:{hands[i]["dealtCombos"][actor]}:{history}'
        return decision(actor, key, children)

    root = Node(-1, edges=tuple((w['conditionalProbability'], preflop(i)) for i, w in enumerate(hands)))
    node_count += 1
    require(node_count == 1321 and rows_used == set(policy), 'Independent complete tree or policy coverage differs')
    expected_quality = quality(root, policy)
    require(report['solve']['algorithm'] == 'BOUNDED_PERFECT_RECALL_AFFINE_SEQUENCE_FORM_ASCENDING_L1_ORIGINAL_CERTIFICATES/v1'
            and all(report['solve'][k]['algorithm'] == 'OWNED_STABLE_ASCENDING_L1_ROWS_ORIGINAL_CERTIFICATE/v2'
                    for k in ('firstLp', 'secondLp')), 'Explicit row representation differs')
    compare_quality(report['solve']['behavioralQuality'], expected_quality)
    require(expected_quality[3] <= 1e-8, 'Independent full-parent best-response gap exceeds tolerance')
    close(report['solve']['lowerValue'], expected_quality[0][3], 'Parent lower value differs')
    close(report['solve']['upperValue'], expected_quality[1][3], 'Parent upper value differs')
    require(report['solve']['treeNodes'] == 1321 and len(report['solve']['informationSets']) == 73,
            'Solve inventory differs')

    locals_by_history = {a['publicHistory']: a for a in report['reachedFlopQuality']}
    require(set(handoffs) == set(public) == set(locals_by_history), 'Public continuation identities differ')
    reached_quality_checks = 0
    for history, (committed, actions) in public.items():
        h = handoffs[history]
        require(h['sourceBinding'] == parent['binding'] and h['trainerAdmission'] is False
                and h['model'] == 'SUPPLIED_JOINT_COUNTERFACTUAL_FIXED_FLOP_CHECKDOWN/v1'
                and h['beliefs'] == 'ORIGINAL_JOINT_SUPPORT_CONDITIONED_ONLY_ON_PHYSICAL_FLOP/v1'
                and h['sourceHistoryReachStatus'] == 'UNAVAILABLE_NO_SOURCE_POLICY'
                and h['flop'] == list(FLOP) and h['continuationActions'] == list(actions)
                and h['firstToAct'] == 'BB' and h['secondToAct'] == 'BTN', 'Handoff physical identity differs')
        close(h['potBb'], math.fsum(committed), 'Handoff pot differs')
        require(h['committedBb'] == list(committed) and h['remainingStackBb'] == [100-c for c in committed]
                and h['folded'] == [True, True, True, False, True, False], 'Handoff stacks/fold flags differ')
        moves = [dict(seat='SB', kind='POST_SMALL_BLIND', amountBb=.5), dict(seat='BB', kind='POST_BIG_BLIND', amountBb=1)]
        for action in declared['input']['specification']['history']:
            text = action['action']
            moves.append(dict(seat=action['seat'], kind='FOLD' if text == 'fold' else 'RAISE_TO',
                              amountBb=0 if text == 'fold' else float(text.split(':')[1])))
        chips, actor = [0, 0, 0, 3, .5, 1], 5
        for action in actions:
            amount = chips[3 if actor == 5 else 5] if action == 'call' else float(action.split(':')[1])
            moves.append(dict(seat=SEATS[actor], kind='CALL' if action == 'call' else 'RAISE_TO', amountBb=amount))
            chips[actor] = amount
            actor = 3 if actor == 5 else 5
        require(h['completePreflopHistory'] == moves, 'Complete literal public history differs')
        close(h['compatibleOriginalPriorMass'], 1, 'Compatible support differs')
        close(h['physicalFlopMass'], P, 'Physical chance was renormalized', mass=True)
        require(h['enumeratedRunouts'] == 15984 and h['handEvaluations'] == 31968 and len(h['worlds']) == 24,
                'Fixed-flop physical budget differs')
        masses = []
        for i, world in enumerate(h['worlds']):
            w, l, t = physical[i]
            require(world['originalDealIndex'] == i and world['sixDealtCombos'] == hands[i]['dealtCombos']
                    and world['counts'] == dict(firstSeat=3, secondSeat=5, firstWins=w, secondWins=l,
                    ties=t, runouts=666, handEvaluations=1332), 'Independent physical runout counts differ')
            close(world['originalJointProbability'], 1/24, 'Original joint weight differs', mass=True)
            close(world['privateGivenPhysicalFlopWeight'], 1/24, 'Conditional physical support differs', mass=True)
            close(world['physicalFlopProbabilityGivenDeal'], P, 'Physical chance was renormalized', mass=True)
            for a, b in zip(world['checkdownUtilitiesBb'], utility(committed, (w+.5*t)/666)):
                close(a, b, 'Flop literal checkdown differs')
            mass, prefix, actor = P/24, '', 5
            for action in actions:
                key = f'{actor}:supplied-connected-single-flop:supplied-conditional-preflop:{hands[i]["dealtCombos"][actor]}:{prefix}'
                mass *= policy[key][action]
                prefix += f'|{SEATS[actor]}:{action}'
                actor = 3 if actor == 5 else 5
            if mass > 0:
                masses.append((i, mass))
        local = locals_by_history[history]
        require(local['scope'] == 'CANDIDATE_POLICY_REACHED_FLOP_POSTERIOR_NOT_FULL_SECURITY_FACE/v1', 'Local quality scope differs')
        total = math.fsum(m for _, m in masses)
        close(local['candidateRootFlopReach'], total, 'Raw candidate reach differs', mass=True)
        close(local['physicalSupportFlopMass'], P, 'Local physical support differs', mass=True)
        close(local['candidateBoundaryReachGivenPhysicalFlop'], total/P, 'Boundary reach differs', mass=True)
        require(local['conditionalReachFloor'] == FLOOR and len(local['rawWorldMasses']) == len(masses), 'Conditional floor or mass inventory differs')
        for actual, (i, mass) in zip(local['rawWorldMasses'], masses):
            require(actual['originalDealIndex'] == i, 'Raw mass deal mapping differs')
            close(actual['jointRootFlopReach'], mass, 'Raw world mass differs', mass=True)
        if total/P < FLOOR:
            require(local['conditioning'] == 'BELOW_CANDIDATE_REACH_FLOOR' and local['reachedWorlds'] == []
                    and local['conditionalBestResponse'] is None, 'Tiny reach must not manufacture a posterior')
        else:
            require(local['conditioning'] == 'ABOVE_CANDIDATE_REACH_FLOOR'
                    and len(local['reachedWorlds']) == len(masses), 'Supported conditional identity differs')
            for actual, (i, mass) in zip(local['reachedWorlds'], masses):
                require(actual['originalDealIndex'] == i, 'Reached deal mapping differs')
                close(actual['jointRootFlopReach'], mass, 'Reached raw mass differs', mass=True)
                close(actual['conditionalWeight'], mass/total, 'Reached posterior differs', mass=True)
            conditional = Node(-1, edges=tuple((m/total, selected[i, history]) for i, m in masses))
            compare_quality(local['conditionalBestResponse'], quality(conditional, policy))
            reached_quality_checks += 1

    receipt = dict(schemaVersion='pokerlab-independent-connected-flop-check/v1', trainerAdmission=False,
                freshLpSolves=0, originalLpCertificatesIndependentlyChecked=False,
                method='LITERAL_BEST_OF_21_CARD_RANKING_AND_INFORMATION_SET_PURE_BEST_RESPONSE',
                originalPreflopCounts='BYTE_PINNED_PRIOR_PHYSICAL_ARTIFACT_JAVA_REPLAY_REGENERATES',
                physicalWorlds=24, independentFixedFlopRunouts=15984, independentHandEvaluations=31968,
                completeParentTreeStates=node_count, fullParentBestResponseGapBb=expected_quality[3],
                reachedFlopQualityChecks=reached_quality_checks, suppressedTinyReachPosteriors=4-reached_quality_checks,
                checkerSourceSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                requestStoredBytesSha256=hashlib.sha256(request_bytes).hexdigest(),
                reportStoredBytesSha256=hashlib.sha256(report_bytes).hexdigest(),
                reportExpandedBytesSha256=hashlib.sha256(report_expanded).hexdigest(),
                pinnedSourceStoredBytesSha256=PARENT_BYTES)
    if include_context:
        return receipt, dict(root=root, selected=selected, prior=hands)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('request', type=Path)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'Output receipt must be new')
    receipt = check(args.request, args.report)
    with args.output.open('x', encoding='utf-8') as output:
        output.write(json.dumps(receipt, indent=2)+'\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
