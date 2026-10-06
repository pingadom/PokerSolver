package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Six-seat CFR with connected betting on explicitly selected physical flops and mandatory checkdown
 * on all other flops. This sparse continuation model is complete, but not full postflop cash poker.
 */
public final class SixMaxConnectedPreflopGame
        implements MultiPlayerCfrGame<SixMaxConnectedPreflopGame.State> {
    public static final int MAX_PRIVATE_DEALS = 12;
    public static final int MAX_CONNECTED_DEAL_FLOPS = 32;
    public static final int MAX_SELECTED_FLOPS = 8;
    public static final int MAX_SELECTED_HISTORIES = 4;

    public record Selection(
            List<PublicAction> history,
            List<List<Card>> flops,
            double flopBetBb,
            double turnBetBb,
            double riverBetBb) {
        public Selection {
            history = List.copyOf(history);
            flops = flops.stream().map(List::copyOf).toList();
            if (flops.isEmpty() || flops.size() > MAX_SELECTED_FLOPS)
                throw new IllegalArgumentException("Select 1–8 flops per history");
            for (double bet : new double[] {flopBetBb, turnBetBb, riverBetBb})
                if (!Double.isFinite(bet) || bet <= 0)
                    throw new IllegalArgumentException(
                            "Requested bets must be finite and positive");
        }
    }

    public record State(
            SixMaxPreflopCheckdownGame.State preflop,
            int flopIndex,
            SixMaxHeadsUpPostflopGame.State postflop,
            boolean otherFlopsCheckdown) {}

    public record Coverage(
            String publicHistory,
            List<PublicAction> actions,
            List<List<String>> flops,
            Seat firstToAct,
            Seat secondToAct,
            double flopBetBb,
            double requestedTurnBetBb,
            double requestedRiverBetBb,
            List<Integer> legalSelectedFlopsByDeal,
            List<Double> bettingFlopProbabilityByDeal) {
        public Coverage {
            actions = List.copyOf(actions);
            flops = flops.stream().map(List::copyOf).toList();
            legalSelectedFlopsByDeal = List.copyOf(legalSelectedFlopsByDeal);
            bettingFlopProbabilityByDeal = List.copyOf(bettingFlopProbabilityByDeal);
        }
    }

    private static final class Prepared {
        private final List<SixMaxHeadsUpPostflopGame> games;
        private final SixMaxHeadsUpPostflopGame.State[][] roots;
        private final double[][] remainder;
        private final double[] foldedUtilities;
        private final Map<SixMaxHeadsUpPostflopGame.State, String>[] keys;
        private final Coverage coverage;

        @SuppressWarnings("unchecked")
        private Prepared(
                List<SixMaxHeadsUpPostflopGame> games,
                SixMaxHeadsUpPostflopGame.State[][] roots,
                double[][] remainder,
                double[] foldedUtilities,
                Coverage coverage) {
            this.games = List.copyOf(games);
            this.roots = roots;
            this.remainder = remainder;
            this.foldedUtilities = foldedUtilities;
            this.coverage = coverage;
            keys = new Map[games.size()];
            for (int i = 0; i < keys.length; i++) keys[i] = new HashMap<>();
        }
    }

    private final SixMaxPreflopCheckdownGame base;
    private final List<Selection> selections;
    private final Map<String, Prepared> prepared;
    private final Map<SixMaxPreflopCheckdownGame.State, String> preflopKeys = new HashMap<>();
    private final Map<State, List<ChanceOutcome<State>>> chance = new HashMap<>();

    private record Node(boolean terminal, int actor, List<String> actions, double[] utilities) {}

    // States and this game's rules are immutable. Cache validation and settlement once per state.
    private final Map<State, Node> nodes = new HashMap<>();

    public SixMaxConnectedPreflopGame(SixMaxPreflopCheckdownGame base, List<Selection> selections) {
        this.base = Objects.requireNonNull(base, "base");
        this.selections = List.copyOf(selections);
        if (selections.isEmpty() || selections.size() > MAX_SELECTED_HISTORIES)
            throw new IllegalArgumentException("Select 1–4 heads-up histories");
        if (base.rakeRule().fraction() > 0 && base.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Connected continuation does not support rake");
        if (base.maximumTerminalPayoffStandardErrorBb() != 0)
            throw new IllegalArgumentException(
                    "Residual checkdown requires source payoffs with no sampling error");
        var sourceDeals = base.chanceOutcomes(base.initialState());
        if (sourceDeals.size() > MAX_PRIVATE_DEALS)
            throw new IllegalArgumentException(
                    "Connected prototype supports at most " + MAX_PRIVATE_DEALS + " private deals");
        var entries = new LinkedHashMap<String, Prepared>();
        int connectedDealFlops = 0;
        for (var selection : selections) {
            var support =
                    SixMaxPolicyFlopTransition.counterfactualSupport(base, selection.history());
            var representative = replay(sourceDeals.getFirst().state(), selection.history());
            String key = representative.publicHistory();
            if (entries.containsKey(key))
                throw new IllegalArgumentException("Duplicate selected preflop history");
            var boards = new ArrayList<List<Card>>();
            var games = new ArrayList<SixMaxHeadsUpPostflopGame>();
            var roots =
                    new SixMaxHeadsUpPostflopGame.State[selection.flops().size()]
                            [sourceDeals.size()];
            var checkdowns = new double[selection.flops().size()][sourceDeals.size()][];
            for (int boardIndex = 0; boardIndex < selection.flops().size(); boardIndex++) {
                var flop = support.conditionOnFlop(selection.flops().get(boardIndex));
                if (boards.contains(flop.board()))
                    throw new IllegalArgumentException("Duplicate selected physical flop");
                boards.add(flop.board());
                connectedDealFlops += flop.deals().size();
                if (connectedDealFlops > MAX_CONNECTED_DEAL_FLOPS)
                    throw new IllegalArgumentException("Connected private-deal/flop cap exceeded");
                var post =
                        new SixMaxHeadsUpPostflopGame(
                                flop,
                                selection.flopBetBb(),
                                selection.turnBetBb(),
                                selection.riverBetBb());
                games.add(post);
                for (var postRoot : post.chanceOutcomes(post.initialState())) {
                    int postIndex = postRoot.state().dealIndex();
                    var physicalHands = flop.deals().get(postIndex).hands();
                    for (var sourceRoot : sourceDeals)
                        if (base.dealtHands(sourceRoot.state()).equals(physicalHands)) {
                            int sourceIndex = sourceRoot.state().dealIndex();
                            roots[boardIndex][sourceIndex] = postRoot.state();
                            var exact = flop.exactCheckdown(postIndex).utilitiesBb();
                            checkdowns[boardIndex][sourceIndex] = new double[6];
                            for (Seat seat : Seat.values())
                                checkdowns[boardIndex][sourceIndex][seat.ordinal()] =
                                        exact.get(seat);
                        }
                }
            }
            var residual = new double[sourceDeals.size()][];
            var counts = new ArrayList<Integer>();
            var masses = new ArrayList<Double>();
            for (var sourceRoot : sourceDeals) {
                int dealIndex = sourceRoot.state().dealIndex();
                residual[dealIndex] =
                        base.terminalUtilities(replay(sourceRoot.state(), selection.history()));
                int count = 0;
                for (int b = 0; b < roots.length; b++)
                    if (roots[b][dealIndex] != null) {
                        count++;
                        for (int seat = 0; seat < 6; seat++)
                            residual[dealIndex][seat] -=
                                    checkdowns[b][dealIndex][seat]
                                            / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL;
                    }
                double mass = (double) count / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL;
                for (int seat = 0; seat < 6; seat++) residual[dealIndex][seat] /= 1 - mass;
                validateRemainder(support, residual[dealIndex]);
                counts.add(count);
                masses.add(mass);
            }
            var folded = new double[6];
            for (Seat seat : Seat.values()) folded[seat.ordinal()] = -support.committedBb(seat);
            var coverage =
                    new Coverage(
                            key,
                            selection.history(),
                            boards.stream()
                                    .map(b -> b.stream().map(Card::compact).toList())
                                    .toList(),
                            support.firstToAct(),
                            support.secondToAct(),
                            games.getFirst().flopBetBb(),
                            selection.turnBetBb(),
                            selection.riverBetBb(),
                            counts,
                            masses);
            entries.put(key, new Prepared(games, roots, residual, folded, coverage));
        }
        prepared = Map.copyOf(entries);
    }

    public SixMaxPreflopCheckdownGame source() {
        return base;
    }

    public List<Selection> selections() {
        return selections;
    }

    public List<Coverage> coverage() {
        return prepared.values().stream()
                .map(p -> p.coverage)
                .sorted(Comparator.comparing(Coverage::publicHistory))
                .toList();
    }

    /**
     * Counts a full traversal before training allocates policy rows. Public-card identities do not
     * change legal actions or stack caps in this game. After a selected flop, all children of a
     * turn/river chance node therefore have the same tree shape: count one and multiply by the
     * physical fanout. Preflop and selected-flop chance are enumerated, including blocked boards
     * and the residual-checkdown child. This counts states, not unique information sets.
     */
    public long completeTreeStateCount() {
        return countTree(initialState());
    }

    private long countTree(State state) {
        if (isTerminal(state)) return 1;
        long count = 1;
        if (currentPlayer(state) == -1) {
            var outcomes = chanceOutcomes(state);
            if (state.postflop() != null)
                return Math.addExact(
                        count,
                        Math.multiplyExact(
                                outcomes.size(), countTree(outcomes.getFirst().state())));
            for (var outcome : outcomes) count = Math.addExact(count, countTree(outcome.state()));
        } else {
            for (String action : legalActions(state))
                count = Math.addExact(count, countTree(afterAction(state, action)));
        }
        return count;
    }

    @Override
    public int playerCount() {
        return 6;
    }

    @Override
    public State initialState() {
        return pre(base.initialState());
    }

    public State replayPreflop(List<PublicAction> history, int dealIndex) {
        var sourceRoot =
                base.chanceOutcomes(base.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(s -> s.dealIndex() == dealIndex)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown private deal"));
        return pre(replay(sourceRoot, history));
    }

    @Override
    public boolean isTerminal(State state) {
        return node(state).terminal();
    }

    @Override
    public int currentPlayer(State state) {
        var node = node(state);
        if (node.terminal()) throw new IllegalArgumentException("Terminal state has no actor");
        return node.actor();
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) == -1) throw new IllegalArgumentException("Chance has no actions");
        return node(state).actions();
    }

    @Override
    public String informationSet(State state) {
        if (currentPlayer(state) == -1)
            throw new IllegalArgumentException("Chance has no information set");
        if (state.postflop() == null)
            return preflopKeys.computeIfAbsent(state.preflop(), base::informationSet);
        var selected = branch(state);
        return selected.keys[state.flopIndex()].computeIfAbsent(
                state.postflop(),
                s -> "postflop:" + selected.games.get(state.flopIndex()).informationSet(s));
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal connected action");
        if (state.postflop() == null) return pre(base.afterAction(state.preflop(), action));
        return new State(
                state.preflop(),
                state.flopIndex(),
                branch(state).games.get(state.flopIndex()).afterAction(state.postflop(), action),
                false);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (currentPlayer(state) != -1) throw new IllegalArgumentException("Not a chance node");
        return chance.computeIfAbsent(
                state,
                s -> {
                    if (base.initialState().equals(s.preflop()))
                        return base.chanceOutcomes(base.initialState()).stream()
                                .map(o -> new ChanceOutcome<>(pre(o.state()), o.probability()))
                                .toList();
                    var selected = branch(s);
                    if (s.postflop() != null)
                        return selected
                                .games
                                .get(s.flopIndex())
                                .chanceOutcomes(s.postflop())
                                .stream()
                                .map(
                                        o ->
                                                new ChanceOutcome<>(
                                                        new State(
                                                                s.preflop(),
                                                                s.flopIndex(),
                                                                o.state(),
                                                                false),
                                                        o.probability()))
                                .toList();
                    var outcomes = new ArrayList<ChanceOutcome<State>>();
                    int count = 0;
                    for (int board = 0; board < selected.games.size(); board++) {
                        var root = selected.roots[board][s.preflop().dealIndex()];
                        if (root != null) {
                            count++;
                            outcomes.add(
                                    new ChanceOutcome<>(
                                            new State(s.preflop(), board, root, false),
                                            1.0 / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL));
                        }
                    }
                    outcomes.add(
                            new ChanceOutcome<>(
                                    new State(s.preflop(), -1, null, true),
                                    1
                                            - (double) count
                                                    / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL));
                    return List.copyOf(outcomes);
                });
    }

    @Override
    public double[] terminalUtilities(State state) {
        var node = node(state);
        if (!node.terminal()) throw new IllegalArgumentException("Expected terminal state");
        return node.utilities().clone();
    }

    @Override
    public OptionalDouble inactivePlayerUtility(State state, int player) {
        if (player < 0 || player >= playerCount())
            throw new IllegalArgumentException("Invalid inactive-utility player");
        node(state); // Keep the same state validation as every other public game operation.
        var selected = branch(state);
        if (selected == null
                || player == selected.coverage.firstToAct().ordinal()
                || player == selected.coverage.secondToAct().ordinal())
            return OptionalDouble.empty();
        // Selection occurs only at a completed preflop history. Folded seats cannot act in any
        // descendant; residual checkdown and every physical betting branch lose their commitment.
        return OptionalDouble.of(selected.foldedUtilities[player]);
    }

    @Override
    public double chanceBaselineUtility(State state, int player) {
        if (player < 0 || player >= playerCount())
            throw new IllegalArgumentException("Invalid baseline player");
        var node = node(state);
        if (node.terminal() || node.actor() != -1)
            throw new IllegalArgumentException("Baseline requires a chance node");
        return node.utilities() == null ? 0 : node.utilities()[player];
    }

    private Node node(State state) {
        return nodes.computeIfAbsent(
                state,
                s -> {
                    requireState(s);
                    boolean terminal = s.otherFlopsCheckdown();
                    int actor = -1;
                    List<String> actions = List.of();
                    if (!terminal && s.postflop() != null) {
                        var game = branch(s).games.get(s.flopIndex());
                        terminal = game.isTerminal(s.postflop());
                        if (!terminal) {
                            int localActor = game.currentPlayer(s.postflop());
                            if (localActor != -1) {
                                actor = game.seat(localActor).ordinal();
                                actions = game.legalActions(s.postflop());
                            }
                        }
                    } else if (!terminal) {
                        terminal = base.isTerminal(s.preflop()) && branch(s) == null;
                        if (!terminal && !base.isTerminal(s.preflop())) {
                            actor = base.currentPlayer(s.preflop());
                            if (actor != -1) actions = base.legalActions(s.preflop());
                        }
                    }
                    double[] values =
                            terminal
                                    ? settle(s)
                                    : s.postflop() == null && base.isTerminal(s.preflop())
                                            ? base.terminalUtilities(s.preflop())
                                            : null;
                    return new Node(terminal, actor, actions, values);
                });
    }

    private double[] settle(State state) {
        if (state.otherFlopsCheckdown())
            return branch(state).remainder[state.preflop().dealIndex()].clone();
        if (state.postflop() == null) return base.terminalUtilities(state.preflop());
        var selected = branch(state);
        var game = selected.games.get(state.flopIndex());
        double centered = game.terminalUtility(state.postflop());
        var utilities = selected.foldedUtilities.clone();
        utilities[game.seat(0).ordinal()] = centered + game.liveUtilityOffsetBb();
        utilities[game.seat(1).ordinal()] = -centered + game.liveUtilityOffsetBb();
        return utilities;
    }

    private static State pre(SixMaxPreflopCheckdownGame.State state) {
        return new State(state, -1, null, false);
    }

    private Prepared branch(State state) {
        return prepared.get(state.preflop().publicHistory());
    }

    private SixMaxPreflopCheckdownGame.State replay(
            SixMaxPreflopCheckdownGame.State state, List<PublicAction> history) {
        for (var action : history) {
            if (base.isTerminal(state) || base.currentPlayer(state) != action.seat().ordinal())
                throw new IllegalArgumentException("Invalid public preflop history");
            state = base.afterAction(state, action.action());
        }
        return state;
    }

    private static void validateRemainder(SixMaxPolicyFlopTransition support, double[] utilities) {
        double sum = 0;
        for (Seat seat : Seat.values()) {
            double value = utilities[seat.ordinal()];
            double contribution = support.committedBb(seat);
            if (!Double.isFinite(value)
                    || value < -contribution - 1e-8
                    || value > support.potBb() - contribution + 1e-8
                    || seat != support.firstToAct()
                            && seat != support.secondToAct()
                            && Math.abs(value + contribution) > 1e-8)
                throw new IllegalArgumentException(
                        "Source payoff cannot support the selected physical-flop remainder");
            sum += value;
        }
        if (Math.abs(sum) > 1e-8)
            throw new IllegalArgumentException("Residual utilities must conserve chips");
    }

    private void requireState(State state) {
        if (state == null || state.preflop() == null || state.flopIndex() < -1)
            throw new IllegalArgumentException("Invalid connected state");
        if (state.preflop().dealIndex() == -1) {
            if (!state.equals(initialState()))
                throw new IllegalArgumentException("Invalid chance root");
            return;
        }
        base.publicBettingState(state.preflop());
        var selected = branch(state);
        if (state.otherFlopsCheckdown()) {
            if (selected == null || state.flopIndex() != -1 || state.postflop() != null)
                throw new IllegalArgumentException("Invalid remainder state");
        } else if (state.postflop() != null) {
            if (selected == null
                    || state.flopIndex() < 0
                    || state.flopIndex() >= selected.games.size())
                throw new IllegalArgumentException("Invalid postflop branch");
            var root = selected.roots[state.flopIndex()][state.preflop().dealIndex()];
            if (root == null || root.dealIndex() != state.postflop().dealIndex())
                throw new IllegalArgumentException(
                        "Postflop private deal disagrees with preflop deal");
            selected.games.get(state.flopIndex()).isTerminal(state.postflop());
        } else if (state.flopIndex() != -1)
            throw new IllegalArgumentException("Missing postflop state");
    }
}
