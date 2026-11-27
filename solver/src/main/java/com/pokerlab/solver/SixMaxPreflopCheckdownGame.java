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
            Map<String, String> children,
            TerminalTemplate terminal) {}

    private record Deal(
            List<WeightedCombo> hands,
            double logWeight,
            MultiwayShowdownEstimate[] estimatesByMask) {}

    /**
     * Public-node settlement template shared across all private deals. Precomputing the tier
     * structure once per terminal node eliminates the O(deals * terminals) payoff map while
     * preserving exact AllInSidePots arithmetic.
     */
    private static final class TerminalTemplate {
        private final double[] baseUtilitiesBb;
        private final int[] tierMasks;
        private final double[] tierPotsBb;
        private final double rakeBb;

        private TerminalTemplate(
                double[] baseUtilitiesBb, int[] tierMasks, double[] tierPotsBb, double rakeBb) {
            this.baseUtilitiesBb = baseUtilitiesBb;
            this.tierMasks = tierMasks;
            this.tierPotsBb = tierPotsBb;
            this.rakeBb = rakeBb;
        }

        private static TerminalTemplate of(
                SixMaxPreflopBetting.State state, CashRakeRule rakeRule) {
            int seats = Seat.values().length;
            double[] committed = new double[seats];
            int activeMask = 0;
            for (Seat seat : Seat.values()) {
                int index = seat.ordinal();
                committed[index] = state.committedBb(seat);
                if (!state.isFolded(seat)) activeMask |= 1 << index;
            }
            boolean flopDealt =
                    state.status() == Status.ALL_IN_SHOWDOWN
                            || state.status() == Status.POSTFLOP_CONTINUATION_REQUIRED;
            if (state.status() == Status.UNCONTESTED
                    ? Integer.bitCount(activeMask) != 1
                    : !flopDealt || Integer.bitCount(activeMask) < 2)
                throw new IllegalStateException("Terminal status disagrees with live seats");

            double[] levels =
                    java.util.Arrays.stream(committed)
                            .distinct()
                            .filter(value -> value > 0)
                            .sorted()
                            .toArray();
            double[] baseUtilities = new double[seats];
            for (int seat = 0; seat < seats; seat++)
                baseUtilities[seat] = committed[seat] == 0 ? 0 : -committed[seat];

            int[] rawMasks = new int[levels.length];
            double[] rawPots = new double[levels.length];
            int tierCount = 0;
            double rake = 0;
            double lower = 0;
            for (double upper : levels) {
                int contributors = 0;
                int eligible = 0;
                for (int seat = 0; seat < seats; seat++) {
                    if (committed[seat] < upper) continue;
                    contributors++;
                    if ((activeMask & (1 << seat)) != 0) eligible |= 1 << seat;
                }
                if (eligible == 0)
                    throw new IllegalArgumentException("A side pot has no eligible player");
                double grossPot = (upper - lower) * contributors;
                double tierRake =
                        rakeRule.forTier(
                                grossPot, contributors, flopDealt, rakeRule.capBb() - rake);
                rake += tierRake;
                double pot = grossPot - tierRake;
                if (Integer.bitCount(eligible) == 1) {
                    baseUtilities[Integer.numberOfTrailingZeros(eligible)] += pot;
                } else {
                    rawMasks[tierCount] = eligible;
                    rawPots[tierCount] = pot;
                    tierCount++;
                }
                lower = upper;
            }
            return new TerminalTemplate(
                    baseUtilities,
                    java.util.Arrays.copyOf(rawMasks, tierCount),
                    java.util.Arrays.copyOf(rawPots, tierCount),
                    rake);
        }

        private double[] utilitiesBb(Deal deal) {
            double[] utilities = baseUtilitiesBb.clone();
            for (int t = 0; t < tierMasks.length; t++) {
                double[] shares = deal.estimatesByMask()[tierMasks[t]].shares();
                double pot = tierPotsBb[t];
                for (int seat = 0; seat < utilities.length; seat++)
                    utilities[seat] += pot * shares[seat];
            }
            return utilities;
        }

        private double errorBoundBb(Deal deal) {
            if (tierMasks.length == 0) return 0;
            if (tierMasks.length == 1) {
                double[] errors = deal.estimatesByMask()[tierMasks[0]].standardErrors();
                double pot = tierPotsBb[0];
                double max = 0;
                for (double error : errors) max = Math.max(max, pot * error);
                return max;
            }
            double[] bounds = new double[baseUtilitiesBb.length];
            for (int t = 0; t < tierMasks.length; t++) {
                double[] errors = deal.estimatesByMask()[tierMasks[t]].standardErrors();
                double pot = tierPotsBb[t];
                for (int seat = 0; seat < bounds.length; seat++) bounds[seat] += pot * errors[seat];
            }
            double max = 0;
            for (double bound : bounds) max = Math.max(max, bound);
            return max;
        }

        private double rakeBb() {
            return rakeBb;
        }
    }

    private final SixMaxPreflopBetting betting;
    private final CashRakeRule rakeRule;
    private final Map<String, PublicNode> publicNodes;
    private final List<Deal> deals;
    private final List<ChanceOutcome<State>> chanceOutcomes;
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
                        .map(deal -> new Deal(deal.hands(), Math.log(deal.occurrences()), null))
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
        List<TerminalTemplate> terminals = new ArrayList<>();
        for (var node : nodes.values()) if (node.terminal() != null) terminals.add(node.terminal());
        double largestError = 0;
        for (Deal deal : prepared) {
            MultiwayShowdownEstimate[] estimates = new MultiwayShowdownEstimate[1 << playerCount()];
            for (int mask = 1; mask < 1 << playerCount(); mask++) {
                if (Integer.bitCount(mask) < 2) continue;
                var estimate = showdown.estimate(deal.hands(), mask);
                validateEstimate(estimate, mask);
                estimates[mask] = estimate;
            }
            Deal resolved = new Deal(deal.hands(), deal.logWeight(), estimates);
            for (TerminalTemplate terminal : terminals) {
                largestError = Math.max(largestError, terminal.errorBoundBb(resolved));
            }
            int index = finished.size();
            finished.add(resolved);
            outcomes.add(
                    new ChanceOutcome<>(
                            new State(index, ""),
                            Math.exp(deal.logWeight() - maxLogWeight) / totalMass));
        }
        deals = List.copyOf(finished);
        chanceOutcomes = List.copyOf(outcomes);
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
        return node(state).terminal().utilitiesBb(deals.get(state.dealIndex()));
    }

    public double terminalRakeBb(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected a terminal state");
        return node(state).terminal().rakeBb();
    }

    /** Conservative maximum of the per-seat payoff SEs at this terminal. */
    public double terminalMaximumPayoffStandardErrorBb(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Expected a terminal state");
        return node(state).terminal().errorBoundBb(deals.get(state.dealIndex()));
    }

    /** A folded player's payoff is fixed; active seats use the conservative terminal maximum. */
    public double terminalPayoffStandardErrorBb(State state, int player) {
        if (player < 0 || player >= playerCount())
            throw new IllegalArgumentException("Invalid player");
        double maximum = terminalMaximumPayoffStandardErrorBb(state);
        return node(state).betting().isFolded(Seat.values()[player]) ? 0 : maximum;
    }

    public Status publicStatus(State state) {
        return node(state).betting().status();
    }

    /** Immutable public betting state, including terminal commitments and live-seat order. */
    public SixMaxPreflopBetting.State publicBettingState(State state) {
        return node(state).betting();
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
        TerminalTemplate terminal =
                state.status() == Status.DECISION ? null : TerminalTemplate.of(state, rakeRule);
        nodes.put(
                history,
                new PublicNode(state, List.copyOf(actions), Map.copyOf(children), terminal));
        for (int index = 0; index < moves.size(); index++)
            buildTree(
                    betting.apply(state, moves.get(index)),
                    children.get(actions.get(index)),
                    nodes);
    }

    /** Canonical decision identifier shared by saved strategies and public-history replay. */
    public static String actionName(Move move) {
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
            deals.add(new Deal(List.copyOf(hands), logWeight, null));
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
}
