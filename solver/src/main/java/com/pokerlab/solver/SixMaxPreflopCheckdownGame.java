package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopBetting.Move;
import com.pokerlab.solver.SixMaxPreflopBetting.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Six-seat preflop research game with a bounded raise menu and mandatory checkdown after any
 * non-all-in preflop round. This explicit postflop rule closes the game for CFR experiments; it
 * does not approximate a real cash game's future betting strategy.
 */
public final class SixMaxPreflopCheckdownGame
        implements MultiPlayerCfrGame<SixMaxPreflopCheckdownGame.State> {
    public static final int MAX_JOINT_DEALS = 64;
    public static final int MAX_SAMPLED_JOINT_DEALS = 64;
    public static final int MAX_PUBLIC_STATES = 20_000;
    public static final int MAX_DEAL_PUBLIC_STATES = 160_000;

    public enum ChanceModel {
        EXACT_RANGE_PRODUCT,
        EMPIRICAL_JOINT_DEALS
    }

    public record State(int dealIndex, String publicHistory) {}

    public record TreeSummary(
            int decisionStates,
            int uncontestedTerminals,
            int allInTerminals,
            int checkdownTerminals) {
        public int totalStates() {
            return decisionStates + uncontestedTerminals + allInTerminals + checkdownTerminals;
        }
    }

    private record PublicNode(
            SixMaxPreflopBetting.State betting,
            List<String> actions,
            Map<String, String> children) {}

    private record Deal(
            List<WeightedCombo> hands,
            double logWeight,
            Map<Integer, MultiwayShowdownEstimate> estimates) {}

    private record TerminalPayoff(double[] utilitiesBb, double rakeBb, double errorBoundBb) {}

    private final SixMaxPreflopBetting betting;
    private final CashRakeRule rakeRule;
    private final Map<String, PublicNode> publicNodes;
    private final List<Deal> deals;
    private final List<ChanceOutcome<State>> chanceOutcomes;
    private final List<Map<String, TerminalPayoff>> terminalPayoffs;
    private final TreeSummary treeSummary;
    private final double maximumTerminalPayoffStandardErrorBb;
    private final ChanceModel chanceModel;
    private final int chanceSamples;

    public SixMaxPreflopCheckdownGame(
            SixMaxPreflopBetting.Rules rules,
            List<List<WeightedCombo>> ranges,
            CashRakeRule rakeRule,
            MultiwayShowdownOracle showdown) {
        this(
                rules,
                prepareRangeDeals(ranges),
                rakeRule,
                showdown,
                ChanceModel.EXACT_RANGE_PRODUCT,
                0);
    }

    /** Builds a larger bounded game over sampled deal support; chance error is not certified. */
    public static SixMaxPreflopCheckdownGame fromSampledDeals(
            SixMaxPreflopBetting.Rules rules,
            SixMaxJointDealSampler.Sample sample,
            CashRakeRule rakeRule,
            MultiwayShowdownOracle showdown) {
        Objects.requireNonNull(sample, "sample");
        return new SixMaxPreflopCheckdownGame(
                rules,
                sample.deals().stream()
                        .map(deal -> new Deal(deal.hands(), Math.log(deal.occurrences()), Map.of()))
                        .toList(),
                rakeRule,
                showdown,
                ChanceModel.EMPIRICAL_JOINT_DEALS,
                sample.acceptedDraws());
    }

    private SixMaxPreflopCheckdownGame(
            SixMaxPreflopBetting.Rules rules,
            List<Deal> prepared,
            CashRakeRule rakeRule,
            MultiwayShowdownOracle showdown,
            ChanceModel chanceModel,
            int chanceSamples) {
        betting = new SixMaxPreflopBetting(Objects.requireNonNull(rules, "rules"));
        this.rakeRule = Objects.requireNonNull(rakeRule, "rakeRule");
        this.chanceModel = Objects.requireNonNull(chanceModel, "chanceModel");
        this.chanceSamples = chanceSamples;
        Objects.requireNonNull(showdown, "showdown");

        Map<String, PublicNode> nodes = new LinkedHashMap<>();
        buildTree(betting.initialState(), "", nodes);
        publicNodes = Map.copyOf(nodes);
        treeSummary = summarize(nodes);

        if (prepared.isEmpty())
            throw new IllegalArgumentException("Ranges have no unblocked six-seat joint deal");
        int maxDeals =
                chanceModel == ChanceModel.EXACT_RANGE_PRODUCT
                        ? MAX_JOINT_DEALS
                        : MAX_SAMPLED_JOINT_DEALS;
        if (prepared.size() > maxDeals
                || (long) prepared.size() * nodes.size() > MAX_DEAL_PUBLIC_STATES)
            throw new IllegalArgumentException("Six-seat private/public state cap exceeded");
        double maxLogWeight = prepared.stream().mapToDouble(Deal::logWeight).max().orElseThrow();
        double totalMass =
                prepared.stream()
                        .mapToDouble(deal -> Math.exp(deal.logWeight() - maxLogWeight))
                        .sum();
        List<Deal> finished = new ArrayList<>();
        List<ChanceOutcome<State>> outcomes = new ArrayList<>();
        List<Map<String, TerminalPayoff>> payoffs = new ArrayList<>();
        double largestError = 0;
        for (Deal deal : prepared) {
            Map<Integer, MultiwayShowdownEstimate> estimates = new LinkedHashMap<>();
            for (int mask = 1; mask < 1 << playerCount(); mask++) {
                if (Integer.bitCount(mask) < 2) continue;
                var estimate = showdown.estimate(deal.hands(), mask);
                validateEstimate(estimate, mask);
                estimates.put(mask, estimate);
            }
            Deal resolved = new Deal(deal.hands(), deal.logWeight(), Map.copyOf(estimates));
            Map<String, TerminalPayoff> byHistory = new LinkedHashMap<>();
            for (var node : nodes.entrySet()) {
                if (node.getValue().betting().status() == Status.DECISION) continue;
                TerminalPayoff payoff = payoff(resolved, node.getValue().betting());
                byHistory.put(node.getKey(), payoff);
                largestError = Math.max(largestError, payoff.errorBoundBb());
            }
            int index = finished.size();
            finished.add(resolved);
            outcomes.add(
                    new ChanceOutcome<>(
                            new State(index, ""),
                            Math.exp(deal.logWeight() - maxLogWeight) / totalMass));
            payoffs.add(Map.copyOf(byHistory));
        }
        deals = List.copyOf(finished);
        chanceOutcomes = List.copyOf(outcomes);
        terminalPayoffs = List.copyOf(payoffs);
        maximumTerminalPayoffStandardErrorBb = largestError;
    }

    public TreeSummary treeSummary() {
        return treeSummary;
    }

    public double maximumTerminalPayoffStandardErrorBb() {
        return maximumTerminalPayoffStandardErrorBb;
    }

    public ChanceModel chanceModel() {
        return chanceModel;
    }

    public int chanceSamples() {
        return chanceSamples;
    }

    public SixMaxPreflopBetting.Rules rules() {
        return betting.rules();
    }

    public CashRakeRule rakeRule() {
        return rakeRule;
    }

    public List<WeightedCombo> dealtHands(State state) {
        requireDealtState(state);
        return deals.get(state.dealIndex()).hands();
    }

    @Override
    public int playerCount() {
        return Seat.values().length;
    }

    @Override
    public State initialState() {
        return new State(-1, "");
    }

    @Override
    public boolean isTerminal(State state) {
        if (state.dealIndex() == -1) return false;
        return node(state).betting().status() != Status.DECISION;
    }

    @Override
    public double[] terminalUtilities(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected a terminal state");
        return terminalPayoffs
                .get(state.dealIndex())
                .get(state.publicHistory())
                .utilitiesBb()
                .clone();
    }

    public double terminalRakeBb(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected a terminal state");
        return terminalPayoffs.get(state.dealIndex()).get(state.publicHistory()).rakeBb();
    }

    public Status publicStatus(State state) {
        return node(state).betting().status();
    }

    public double publicPotBb(State state) {
        return node(state).betting().potBb();
    }

    public double publicToCallBb(State state) {
        return node(state).betting().toCallBb();
    }

    public double stackBb() {
        return betting.rules().stackBb();
    }

    public double smallBlindBb() {
        return betting.rules().smallBlindBb();
    }

    @Override
    public int currentPlayer(State state) {
        if (state.dealIndex() == -1) return -1;
        PublicNode node = node(state);
        if (node.betting().status() != Status.DECISION)
            throw new IllegalArgumentException("Terminal state has no actor");
        return node.betting().actingSeat().ordinal();
    }

    @Override
    public List<String> legalActions(State state) {
        PublicNode node = node(state);
        if (node.betting().status() != Status.DECISION)
            throw new IllegalArgumentException("Terminal state has no legal action");
        return node.actions();
    }

    @Override
    public String informationSet(State state) {
        int actor = currentPlayer(state);
        return deals.get(state.dealIndex()).hands().get(actor).key() + ":" + state.publicHistory();
    }

    @Override
    public State afterAction(State state, String action) {
        PublicNode node = node(state);
        String child = node.children().get(action);
        if (child == null) throw new IllegalArgumentException("Illegal preflop action");
        return new State(state.dealIndex(), child);
    }

    @Override
    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (!initialState().equals(state))
            throw new IllegalArgumentException("Not the chance root");
        return chanceOutcomes;
    }

    private PublicNode node(State state) {
        requireDealtState(state);
        PublicNode node = publicNodes.get(state.publicHistory());
        if (node == null) throw new IllegalArgumentException("Unknown public history");
        return node;
    }

    private void requireDealtState(State state) {
        if (state == null || state.dealIndex() < 0 || state.dealIndex() >= deals.size())
            throw new IllegalArgumentException("Expected a dealt state");
    }

    private void buildTree(
            SixMaxPreflopBetting.State state, String history, Map<String, PublicNode> nodes) {
        if (nodes.size() == MAX_PUBLIC_STATES)
            throw new IllegalArgumentException("Preflop public-tree cap exceeded");
        List<Move> moves = state.legalActions();
        List<String> actions = new ArrayList<>();
        Map<String, String> children = new LinkedHashMap<>();
        for (Move move : moves) {
            String action = actionName(move);
            String child = history + "|" + move.seat() + ":" + action;
            actions.add(action);
            children.put(action, child);
        }
        if (children.size() != actions.size())
            throw new IllegalStateException("Betting actions need unique names");
        nodes.put(history, new PublicNode(state, List.copyOf(actions), Map.copyOf(children)));
        for (int index = 0; index < moves.size(); index++)
            buildTree(
                    betting.apply(state, moves.get(index)),
                    children.get(actions.get(index)),
                    nodes);
    }

    private static String actionName(Move move) {
        return switch (move.kind()) {
            case FOLD -> "fold";
            case CHECK -> "check";
            case CALL -> "call";
            case RAISE_TO -> "raise:" + move.amountBb();
            default -> throw new IllegalStateException("Blind posts cannot be decision actions");
        };
    }

    private static TreeSummary summarize(Map<String, PublicNode> nodes) {
        int decisions = 0;
        int uncontested = 0;
        int allIn = 0;
        int checkdown = 0;
        for (PublicNode node : nodes.values()) {
            switch (node.betting().status()) {
                case DECISION -> decisions++;
                case UNCONTESTED -> uncontested++;
                case ALL_IN_SHOWDOWN -> allIn++;
                case POSTFLOP_CONTINUATION_REQUIRED -> checkdown++;
            }
        }
        return new TreeSummary(decisions, uncontested, allIn, checkdown);
    }

    private static void validateRanges(List<List<WeightedCombo>> ranges) {
        if (ranges == null || ranges.size() != Seat.values().length)
            throw new IllegalArgumentException("Exactly six seat ranges are required");
        for (List<WeightedCombo> range : ranges) {
            if (range == null || range.isEmpty())
                throw new IllegalArgumentException("Every seat needs a nonempty range");
            Set<String> keys = new HashSet<>();
            for (WeightedCombo combo : range)
                if (combo == null || !keys.add(combo.key()))
                    throw new IllegalArgumentException("Range has a missing or duplicate combo");
        }
    }

    private static List<Deal> prepareRangeDeals(List<List<WeightedCombo>> ranges) {
        validateRanges(ranges);
        List<Deal> prepared = new ArrayList<>();
        enumerate(ranges, 0, new ArrayList<>(), new HashSet<>(), 0, prepared);
        return prepared;
    }

    private static void enumerate(
            List<List<WeightedCombo>> ranges,
            int seat,
            List<WeightedCombo> hands,
            Set<Card> used,
            double logWeight,
            List<Deal> deals) {
        if (seat == ranges.size()) {
            if (deals.size() == MAX_JOINT_DEALS)
                throw new IllegalArgumentException(
                        "More than " + MAX_JOINT_DEALS + " physical joint deals");
            deals.add(new Deal(List.copyOf(hands), logWeight, Map.of()));
            return;
        }
        for (WeightedCombo combo : ranges.get(seat)) {
            if (used.contains(combo.first()) || used.contains(combo.second())) continue;
            used.add(combo.first());
            used.add(combo.second());
            hands.add(combo);
            enumerate(ranges, seat + 1, hands, used, logWeight + Math.log(combo.weight()), deals);
            hands.remove(hands.size() - 1);
            used.remove(combo.first());
            used.remove(combo.second());
        }
    }

    private static void validateEstimate(MultiwayShowdownEstimate estimate, int mask) {
        if (estimate == null) throw new IllegalArgumentException("Missing showdown estimate");
        double[] shares = estimate.shares();
        double[] errors = estimate.standardErrors();
        if (shares.length != Seat.values().length)
            throw new IllegalArgumentException("Showdown estimate needs six seats");
        double sum = 0;
        for (int seat = 0; seat < shares.length; seat++) {
            if (!Double.isFinite(shares[seat])
                    || shares[seat] < 0
                    || shares[seat] > 1
                    || !Double.isFinite(errors[seat])
                    || errors[seat] < 0
                    || ((mask & (1 << seat)) == 0 && (shares[seat] != 0 || errors[seat] != 0)))
                throw new IllegalArgumentException("Invalid showdown share or sampling error");
            sum += shares[seat];
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Showdown shares must sum to one");
    }

    private TerminalPayoff payoff(Deal deal, SixMaxPreflopBetting.State state) {
        if (state.status() == Status.UNCONTESTED) {
            var settled = SixMaxPreflopTerminalPayoff.settleUncontested(state, rakeRule);
            return new TerminalPayoff(
                    toArray(settled.utilitiesBb()),
                    settled.rakeBb(),
                    settled.maximumPayoffStandardErrorBb());
        }
        if (state.status() == Status.ALL_IN_SHOWDOWN) {
            var settled =
                    SixMaxPreflopTerminalPayoff.settle(
                            state,
                            deal.hands(),
                            rakeRule,
                            (hands, mask) -> deal.estimates().get(mask));
            return new TerminalPayoff(
                    toArray(settled.utilitiesBb()),
                    settled.rakeBb(),
                    settled.maximumPayoffStandardErrorBb());
        }
        if (state.status() != Status.POSTFLOP_CONTINUATION_REQUIRED)
            throw new IllegalArgumentException("Expected a completed preflop round");
        double[] committed = new double[playerCount()];
        int activeMask = 0;
        for (Seat seat : Seat.values()) {
            committed[seat.ordinal()] = state.committedBb(seat);
            if (!state.isFolded(seat)) activeMask |= 1 << seat.ordinal();
        }
        var settled =
                AllInSidePots.settle(
                        committed, activeMask, 0, deal.estimates()::get, rakeRule, true);
        return new TerminalPayoff(
                settled.utilitiesBb(), settled.rakeBb(), settled.maximumStandardErrorBb());
    }

    private static double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
