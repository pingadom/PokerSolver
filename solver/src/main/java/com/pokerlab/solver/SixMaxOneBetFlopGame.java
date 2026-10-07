package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.Coverage;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.Selection;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.State;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Shared six-seat preflop/one-bet continuation engine. Observation keys and exact pair shares come
 * from a separately validated payoff view. Model-specific wrappers retain their schemas.
 */
final class SixMaxOneBetFlopGame implements MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> {
    public static final int MAX_SELECTED_HISTORIES = 6;
    public static final int MAX_COMPLETE_STATES = 1_000_000;

    private record Continuation(
            Seat first, Seat second, double pot, double bet, double[] committed) {}

    private static final List<Seat> POST_ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);
    private static final List<String> CHECK_BET = List.of("k", "b"), FOLD_CALL = List.of("f", "c");
    private static final List<String> PREFIXES =
            List.of("", "k", "b", "kb", "kk", "bf", "bc", "kbf", "kbc");
    private static final List<String> TERMINALS = List.of("kk", "bf", "bc", "kbf", "kbc");
    private final SixMaxPreflopCheckdownGame base;
    private final SixMaxFlopPayoffView view;
    private final List<Selection> selections;
    private final Map<String, Continuation> continuations;
    private final long completeStates;

    SixMaxOneBetFlopGame(
            SixMaxPreflopSolutionPack source,
            SixMaxFlopPayoffView view,
            List<Selection> selections) {
        java.util.Objects.requireNonNull(view, "view");
        base = source.rebuildGame();
        if (base.rakeRule().fraction() > 0 && base.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Texture betting does not support applied rake");
        this.view = view;
        this.selections = List.copyOf(selections);
        if (selections.isEmpty() || selections.size() > MAX_SELECTED_HISTORIES)
            throw new IllegalArgumentException("Select one to six heads-up histories");
        var prepared = new LinkedHashMap<String, Continuation>();
        var roots = base.chanceOutcomes(base.initialState());
        if (roots.size() != view.dealCount())
            throw new IllegalArgumentException("Private support differs");
        for (int d = 0; d < roots.size(); d++)
            if (!base.dealtHands(roots.get(d).state()).stream()
                    .map(WeightedCombo::key)
                    .toList()
                    .equals(view.hands(d)))
                throw new IllegalArgumentException("Private support order differs");
        long states = 1L + (long) base.treeSummary().totalStates() * roots.size();
        for (var selection : selections) {
            var state = replay(roots.getFirst().state(), selection.history());
            var betting = base.publicBettingState(state);
            if (betting.status() != SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                    || betting.liveSeats().size() != 2)
                throw new IllegalArgumentException(
                        "Completed non-all-in heads-up history required");
            Seat first =
                    POST_ORDER.stream()
                            .filter(betting.liveSeats()::contains)
                            .findFirst()
                            .orElseThrow();
            Seat second =
                    betting.liveSeats().stream()
                            .filter(seat -> seat != first)
                            .findFirst()
                            .orElseThrow();
            if (betting.committedBb(first) != betting.committedBb(second))
                throw new IllegalArgumentException("Equal live commitments required");
            double bet =
                    Math.min(
                            betting.potBb() * selection.potFraction(),
                            Math.min(
                                    betting.remainingStackBb(first),
                                    betting.remainingStackBb(second)));
            double[] committed = new double[6];
            for (Seat seat : Seat.values()) committed[seat.ordinal()] = betting.committedBb(seat);
            if (prepared.put(
                            state.publicHistory(),
                            new Continuation(first, second, betting.potBb(), bet, committed))
                    != null) throw new IllegalArgumentException("Duplicate selected history");
            for (int d = 0; d < view.dealCount(); d++)
                states += 9 * view.counts(d).stream().filter(n -> n > 0).count();
        }
        if (states > MAX_COMPLETE_STATES)
            throw new IllegalArgumentException("Texture complete-state cap exceeded");
        continuations = Map.copyOf(prepared);
        completeStates = states;
    }

    public SixMaxPreflopCheckdownGame sourceGame() {
        return base;
    }

    SixMaxFlopPayoffView payoffView() {
        return view;
    }

    public List<Selection> selections() {
        return selections;
    }

    public long completeTreeStates() {
        return completeStates;
    }

    public List<Coverage> coverage() {
        return continuations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(
                        entry -> {
                            var c = entry.getValue();
                            int count = 0;
                            for (int d = 0; d < view.dealCount(); d++)
                                count += (int) view.counts(d).stream().filter(n -> n > 0).count();
                            return new Coverage(
                                    entry.getKey(),
                                    c.first(),
                                    c.second(),
                                    c.pot(),
                                    c.bet(),
                                    view.dealCount(),
                                    count,
                                    9880);
                        })
                .toList();
    }

    public CfrSolution checkdownBaseline(CfrSolution preflop) {
        var completed =
                MultiPlayerStrategyCompletion.uniformAtUnseen(this, preflop, MAX_COMPLETE_STATES);
        // Validate the preflop support separately; missing source rows must not be silently filled.
        if (MultiPlayerStrategyCompletion.uniformAtUnseen(base, preflop, MAX_COMPLETE_STATES)
                        .addedInformationSets()
                != 0) throw new IllegalArgumentException("Complete source preflop policy required");
        var rows = new LinkedHashMap<>(completed.solution().strategy());
        for (var entry : rows.entrySet())
            if (entry.getKey().contains(":postflop:" + view.namespace() + ":")) {
                var row = new LinkedHashMap<String, Double>();
                String chosen = entry.getValue().containsKey("k") ? "k" : "c";
                for (String action : entry.getValue().keySet())
                    row.put(action, action.equals(chosen) ? 1.0 : 0.0);
                entry.setValue(row);
            }
        return new CfrSolution(preflop.iterations(), rows);
    }

    @Override
    public int playerCount() {
        return 6;
    }

    @Override
    public State initialState() {
        return new State(base.initialState(), null, "");
    }

    private boolean expanded(State state) {
        return continuations.containsKey(state.preflop().publicHistory());
    }

    private Continuation continuation(State state) {
        var value = continuations.get(state.preflop().publicHistory());
        if (value == null) throw new IllegalArgumentException("No selected continuation");
        return value;
    }

    private void require(State state) {
        if (state.signal() == null) {
            if (!state.actions().isEmpty())
                throw new IllegalArgumentException("Preflop state cannot have flop actions");
            if (state.preflop().dealIndex() < 0 && !state.preflop().equals(base.initialState()))
                throw new IllegalArgumentException("Invalid private chance state");
            // Delegated base operations validate dealt-state/history identity. Avoid an extra
            // public-node lookup here on every nested hot-path call.
        } else {
            continuation(state);
            if (!base.isTerminal(state.preflop())
                    || state.signal() < 0
                    || state.signal() >= view.counts(0).size()
                    || view.counts(state.preflop().dealIndex()).get(state.signal()) == 0
                    || !PREFIXES.contains(state.actions()))
                throw new IllegalArgumentException("Invalid texture betting state");
        }
    }

    @Override
    public boolean isTerminal(State state) {
        require(state);
        return state.signal() == null
                ? base.isTerminal(state.preflop()) && !expanded(state)
                : TERMINALS.contains(state.actions());
    }

    @Override
    public int currentPlayer(State state) {
        if (isTerminal(state)) return -1;
        if (state.signal() == null)
            return base.isTerminal(state.preflop()) ? -1 : base.currentPlayer(state.preflop());
        var c = continuation(state);
        return (state.actions().equals("") || state.actions().equals("kb") ? c.first() : c.second())
                .ordinal();
    }

    @Override
    public List<String> legalActions(State state) {
        if (currentPlayer(state) < 0) return List.of();
        if (state.signal() == null) return base.legalActions(state.preflop());
        return state.actions().equals("") || state.actions().equals("k") ? CHECK_BET : FOLD_CALL;
    }

    @Override
    public String informationSet(State state) {
        int actor = currentPlayer(state);
        if (actor < 0)
            throw new IllegalArgumentException("Information set requires a player decision");
        if (state.signal() == null) return base.informationSet(state.preflop());
        return base.dealtHands(state.preflop()).get(actor).key()
                + ":postflop:"
                + view.namespace()
                + ":"
                + state.preflop().publicHistory()
                + ":"
                + view.key(state.signal())
                + ":"
                + state.actions();
    }

    @Override
    public State afterAction(State state, String action) {
        if (!legalActions(state).contains(action))
            throw new IllegalArgumentException("Illegal texture-game action");
        return state.signal() == null
                ? new State(base.afterAction(state.preflop(), action), null, "")
                : new State(state.preflop(), state.signal(), state.actions() + action);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        require(state);
        if (state.signal() != null || isTerminal(state) || currentPlayer(state) >= 0)
            throw new IllegalArgumentException("Expected chance state");
        if (state.preflop().dealIndex() == -1)
            return base.chanceOutcomes(base.initialState()).stream()
                    .map(
                            root ->
                                    new ChanceOutcome<>(
                                            new State(root.state(), null, ""), root.probability()))
                    .toList();
        var result = new ArrayList<ChanceOutcome<State>>();
        var counts = view.counts(state.preflop().dealIndex());
        for (int t = 0; t < counts.size(); t++)
            if (counts.get(t) > 0)
                result.add(
                        new ChanceOutcome<>(
                                new State(state.preflop(), t, ""), counts.get(t) / 9880.0));
        return List.copyOf(result);
    }

    @Override
    public double[] terminalUtilities(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected terminal state");
        if (state.signal() == null) return base.terminalUtilities(state.preflop());
        var c = continuation(state);
        double[] result = new double[6];
        for (int seat = 0; seat < 6; seat++) result[seat] = -c.committed()[seat];
        if (state.actions().endsWith("f")) {
            int winner = (state.actions().equals("bf") ? c.first() : c.second()).ordinal();
            result[winner] += c.pot(); // Uncalled postflop bet is returned.
        } else {
            boolean called = !state.actions().equals("kk");
            int mask = (1 << c.first().ordinal()) | (1 << c.second().ordinal());
            for (Seat seat : List.of(c.first(), c.second()))
                result[seat.ordinal()] +=
                        (c.pot() + (called ? 2 * c.bet() : 0))
                                        * view.share(
                                                state.preflop().dealIndex(),
                                                mask,
                                                seat.ordinal(),
                                                state.signal())
                                - (called ? c.bet() : 0);
        }
        return result;
    }

    @Override
    public OptionalDouble inactivePlayerUtility(State state, int player) {
        if (player < 0 || player >= 6) throw new IllegalArgumentException("Invalid player");
        require(state);
        if (state.preflop().dealIndex() < 0) return OptionalDouble.empty();
        var betting = base.publicBettingState(state.preflop());
        return betting.isFolded(Seat.values()[player])
                ? OptionalDouble.of(-betting.committedBb(Seat.values()[player]))
                : OptionalDouble.empty();
    }

    private SixMaxPreflopCheckdownGame.State replay(
            SixMaxPreflopCheckdownGame.State state, List<PublicAction> history) {
        for (var action : history) {
            if (base.isTerminal(state) || base.currentPlayer(state) != action.seat().ordinal())
                throw new IllegalArgumentException("Invalid selected action order");
            state = base.afterAction(state, action.action());
        }
        return state;
    }
}
