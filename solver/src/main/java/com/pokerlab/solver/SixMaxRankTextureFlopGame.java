package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Coarse public-signal experiment: six-seat preflop, actual board ranks and coarse suit
 * multiplicity, one heads-up betting round and exact integrated turn/river checkdown. Players see
 * their hand, board ranks and texture, not actual suits or rank-to-suit association. Only declared
 * histories expand; unselected and multiway pots retain source checkdown.
 */
public final class SixMaxRankTextureFlopGame
        implements MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> {
    public static final int MAX_SELECTED_HISTORIES = 6;
    public static final int MAX_COMPLETE_STATES = 1_000_000;

    public record State(SixMaxPreflopCheckdownGame.State preflop, Integer signal, String actions) {}

    public record Selection(List<PublicAction> history, double potFraction) {
        public Selection {
            history = List.copyOf(history);
            if (!Double.isFinite(potFraction) || potFraction <= 0 || potFraction > 2)
                throw new IllegalArgumentException("Flop bet fraction must be in (0,2]");
        }
    }

    public record Coverage(
            String history,
            Seat firstToAct,
            Seat secondToAct,
            double potBb,
            double betBb,
            int privateDeals,
            int dealSignalPairs,
            long physicalFlopsPerDeal) {}

    private record Continuation(
            Seat first, Seat second, double pot, double bet, double[] committed) {}

    private static final List<Seat> POST_ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);
    private static final List<String> CHECK_BET = List.of("k", "b"), FOLD_CALL = List.of("f", "c");
    private static final List<String> PREFIXES =
            List.of("", "k", "b", "kb", "kk", "bf", "bc", "kbf", "kbc");
    private static final List<String> TERMINALS = List.of("kk", "bf", "bc", "kbf", "kbc");
    private final SixMaxPreflopCheckdownGame base;
    private final SixMaxRankTexturePayoffTable.Artifact table;
    private final List<Selection> selections;
    private final Map<String, Continuation> continuations;
    private final long completeStates;

    public SixMaxRankTextureFlopGame(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            List<Selection> selections) {
        SixMaxRankTexturePayoffTable.validate(table, source);
        base = source.rebuildGame();
        if (base.rakeRule().fraction() > 0 && base.rakeRule().capBb() > 0)
            throw new IllegalArgumentException("Texture betting does not support applied rake");
        this.table = table;
        this.selections = List.copyOf(selections);
        if (selections.isEmpty() || selections.size() > MAX_SELECTED_HISTORIES)
            throw new IllegalArgumentException("Select one to six heads-up histories");
        var prepared = new LinkedHashMap<String, Continuation>();
        var roots = base.chanceOutcomes(base.initialState());
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
            for (var deal : table.deals())
                states += 9 * deal.flopCounts().stream().filter(n -> n > 0).count();
        }
        if (states > MAX_COMPLETE_STATES)
            throw new IllegalArgumentException("Texture complete-state cap exceeded");
        continuations = Map.copyOf(prepared);
        completeStates = states;
    }

    public SixMaxPreflopCheckdownGame sourceGame() {
        return base;
    }

    SixMaxRankTexturePayoffTable.Artifact payoffTable() {
        return table;
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
                            int count =
                                    table.deals().stream()
                                            .mapToInt(
                                                    d ->
                                                            (int)
                                                                    d.flopCounts().stream()
                                                                            .filter(n -> n > 0)
                                                                            .count())
                                            .sum();
                            return new Coverage(
                                    entry.getKey(),
                                    c.first(),
                                    c.second(),
                                    c.pot(),
                                    c.bet(),
                                    table.deals().size(),
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
            if (entry.getKey().contains(":postflop:rank-texture:")) {
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
                    || state.signal() >= table.signals().size()
                    || table.deals()
                                    .get(state.preflop().dealIndex())
                                    .flopCounts()
                                    .get(state.signal())
                            == 0
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
                + ":postflop:rank-texture:"
                + state.preflop().publicHistory()
                + ":"
                + table.signals().get(state.signal()).key()
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
        var counts = table.deals().get(state.preflop().dealIndex()).flopCounts();
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
            var deal = table.deals().get(state.preflop().dealIndex());
            var pair = deal.pair((1 << c.first().ordinal()) | (1 << c.second().ordinal()));
            long runouts =
                    deal.flopCounts().get(state.signal())
                            * SixMaxRankTexturePayoffTable.RUNOUTS_PER_FLOP;
            for (Seat seat : List.of(c.first(), c.second()))
                result[seat.ordinal()] +=
                        (c.pot() + (called ? 2 * c.bet() : 0))
                                        * pair.share(seat.ordinal(), state.signal(), runouts)
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
