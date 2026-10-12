package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.*;

/**
 * Connected supplied-joint preflop and one selected physical flop, one bet then exact checkdown.
 */
public final class SixMaxSuppliedFlopBettingGame
        implements MultiPlayerCfrGame<SixMaxSuppliedFlopBettingGame.State> {
    public static final String MODEL =
            "SUPPLIED_JOINT_PREFLOP_ONE_SELECTED_FLOP_ONE_BET_EXACT_CHECKDOWN/v1";
    public static final String CHANCE =
            "ORIGINAL_JOINT_DEAL_ONCE_PHYSICAL_FLOP_PLUS_EXACT_RESIDUAL/v1";

    public enum Phase {
        PREFLOP,
        FLOP,
        RESIDUAL_CHECKDOWN
    }

    public record State(
            int originalDealIndex, String preflopHistory, Phase phase, String flopHistory) {}

    public record Budget(
            SixMaxSuppliedRangePreflopGame.Budget sourceBudget,
            int nonAllInBoundaries,
            int compatibleDeals,
            int completeTreeStates,
            Map<Seat, Integer> informationSets,
            Map<Seat, Integer> sequences,
            long additionalEnumeratedRunouts,
            long additionalHandEvaluations) {
        public Budget {
            informationSets = Map.copyOf(informationSets);
            sequences = Map.copyOf(sequences);
            if (sourceBudget == null
                    || nonAllInBoundaries < 1
                    || nonAllInBoundaries > 32
                    || compatibleDeals < 1
                    || compatibleDeals > sourceBudget.jointDeals()
                    || completeTreeStates
                            != sourceBudget.completeTreeStates()
                                    + nonAllInBoundaries * compatibleDeals * 10
                    || completeTreeStates > FiniteTwoPlayerSequenceForm.MAX_TREE_NODES
                    || informationSets.size() != 2
                    || !informationSets.keySet().equals(sequences.keySet())
                    || informationSets.values().stream()
                            .anyMatch(
                                    n ->
                                            n < 1
                                                    || n
                                                            > FiniteTwoPlayerSequenceForm
                                                                    .MAX_INFORMATION_SETS_PER_ACTOR)
                    || sequences.values().stream()
                            .anyMatch(
                                    n ->
                                            n < 2
                                                    || n
                                                            > FiniteTwoPlayerSequenceForm
                                                                    .MAX_SEQUENCES_PER_ACTOR)
                    || additionalEnumeratedRunouts
                            != (long) nonAllInBoundaries
                                    * compatibleDeals
                                    * ExactDeadCardFlopShowdown.RUNOUTS
                    || additionalHandEvaluations != 2 * additionalEnumeratedRunouts)
                throw new IllegalArgumentException(
                        "Connected flop game exceeds an existing measured budget");
        }
    }

    public record Residual(
            String publicHistory,
            int originalDealIndex,
            ExactDeadCardFlopShowdown.ResidualCounts counts,
            List<Double> utilitiesBb) {
        public Residual {
            utilitiesBb = List.copyOf(utilitiesBb);
        }
    }

    public record Binding(
            String model,
            String chanceModel,
            String sourceHistoryReachStatus,
            boolean trainerAdmission,
            SixMaxSuppliedRangePreflopGame.Binding sourceBinding,
            List<String> flop,
            double betPotFraction,
            String handoffsHash,
            String residualsHash,
            Budget budget) {
        public Binding {
            flop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
            fraction(betPotFraction);
            if (!MODEL.equals(model)
                    || !CHANCE.equals(chanceModel)
                    || !SixMaxSuppliedRangePreflopGame.HISTORY_REACH.equals(
                            sourceHistoryReachStatus)
                    || trainerAdmission
                    || sourceBinding == null
                    || budget == null
                    || !sourceBinding.budget().equals(budget.sourceBudget())
                    || handoffsHash == null
                    || !handoffsHash.matches("[0-9a-f]{64}")
                    || residualsHash == null
                    || !residualsHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid connected flop binding");
        }
    }

    private record Node(
            int actor,
            String key,
            List<String> actions,
            Map<String, State> children,
            List<ChanceOutcome<State>> chance,
            List<Double> utilities) {}

    private final SixMaxSuppliedRangePreflopGame source;
    private final Map<State, Node> nodes;
    private final List<SixMaxSuppliedFlopHandoff.Audit> handoffs;
    private final List<Residual> residuals;
    private final Binding binding;
    private static final State ROOT = new State(-1, "", Phase.PREFLOP, "");

    private static void fraction(double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0 || fraction > 1)
            throw new IllegalArgumentException("A finite bet fraction in (0,1] is required");
    }

    private static double bet(SixMaxPreflopBetting.State boundary, double fraction) {
        double remaining =
                boundary.liveSeats().stream()
                        .mapToDouble(boundary::remainingStackBb)
                        .min()
                        .orElseThrow();
        double amount =
                Math.min(
                        remaining,
                        Math.round(boundary.potBb() * fraction * 1_000_000) / 1_000_000.0);
        if (!Double.isFinite(amount) || amount <= 0)
            throw new IllegalArgumentException("Bet rounds to zero or is invalid");
        return amount;
    }

    /** No payoff/evaluator work occurs until both original and connected dimensions pass. */
    public static Budget preflight(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, double fraction) {
        var original = SixMaxSuppliedRangePreflopGame.preflight(input);
        fraction(fraction);
        flop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
        var paths = boundaries(input);
        var physicalFlop = flop;
        var compatible =
                input.worlds().stream()
                        .filter(
                                w ->
                                        w.dealtCombos().stream()
                                                .flatMap(s -> Arrays.stream(s.split(" ")))
                                                .noneMatch(physicalFlop::contains))
                        .toList();
        var infos = new EnumMap<Seat, Integer>(Seat.class);
        infos.putAll(original.informationSets());
        var sequences = new EnumMap<Seat, Integer>(Seat.class);
        sequences.putAll(original.sequences());
        for (var seat : infos.keySet()) {
            int types =
                    (int)
                            compatible.stream()
                                    .map(w -> w.dealtCombos().get(seat.ordinal()))
                                    .distinct()
                                    .count();
            infos.put(seat, infos.get(seat) + paths.size() * 2 * types);
            sequences.put(seat, sequences.get(seat) + paths.size() * 4 * types);
        }
        for (var path : paths.values()) bet(replay(input, path), fraction);
        long runouts = (long) paths.size() * compatible.size() * ExactDeadCardFlopShowdown.RUNOUTS;
        return new Budget(
                original,
                paths.size(),
                compatible.size(),
                original.completeTreeStates() + paths.size() * compatible.size() * 10,
                infos,
                sequences,
                runouts,
                2 * runouts);
    }

    private static SixMaxPreflopBetting.State replay(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> actions) {
        var rules = new SixMaxPreflopBetting(input.specification().rules());
        var state = rules.initialState();
        for (var action : input.specification().history())
            state = rules.apply(state, move(state, action.action()));
        for (String action : actions) state = rules.apply(state, move(state, action));
        return state;
    }

    private static SixMaxPreflopBetting.Move move(SixMaxPreflopBetting.State state, String action) {
        return state.legalActions().stream()
                .filter(m -> SixMaxHeadsUpPreflopGame.encode(m).equals(action))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, List<String>> boundaries(
            SixMaxSuppliedRangePreflopGame.Input input) {
        var result = new LinkedHashMap<String, List<String>>();
        var betting = new SixMaxPreflopBetting(input.specification().rules());
        var root = betting.initialState();
        for (var action : input.specification().history())
            root = betting.apply(root, move(root, action.action()));
        collect(betting, root, "", List.of(), result);
        return Collections.unmodifiableMap(result);
    }

    private static void collect(
            SixMaxPreflopBetting betting,
            SixMaxPreflopBetting.State state,
            String history,
            List<String> actions,
            Map<String, List<String>> result) {
        if (state.status() == SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED) {
            result.put(history, actions);
            return;
        }
        for (var move : state.legalActions()) {
            String action = SixMaxHeadsUpPreflopGame.encode(move);
            var next = new ArrayList<>(actions);
            next.add(action);
            collect(
                    betting,
                    betting.apply(state, move),
                    history + "|" + state.actingSeat() + ":" + action,
                    List.copyOf(next),
                    result);
        }
    }

    public SixMaxSuppliedFlopBettingGame(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, double fraction)
            throws Exception {
        this(buildSource(input, flop, fraction), flop, fraction);
    }

    private static SixMaxSuppliedRangePreflopGame buildSource(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, double fraction)
            throws Exception {
        preflight(input, flop, fraction);
        return new SixMaxSuppliedRangePreflopGame(input);
    }

    public SixMaxSuppliedFlopBettingGame(
            SixMaxSuppliedRangePreflopGame source, List<String> flop, double fraction)
            throws Exception {
        this.source = Objects.requireNonNull(source);
        var budget = preflight(source.input(), flop, fraction);
        var support = new LinkedHashMap<String, SixMaxSuppliedFlopHandoff>();
        for (var entry : boundaries(source.input()).entrySet())
            support.put(
                    entry.getKey(),
                    SixMaxSuppliedFlopHandoff.capture(source, entry.getValue(), flop));
        handoffs = support.values().stream().map(SixMaxSuppliedFlopHandoff::audit).toList();
        var built = new LinkedHashMap<State, Node>();
        var remainder = new ArrayList<Residual>();
        var roots = new ArrayList<ChanceOutcome<State>>();
        for (var outcome : source.chanceOutcomes(source.initialState())) {
            var state = new State(outcome.state().dealIndex(), "", Phase.PREFLOP, "");
            roots.add(new ChanceOutcome<>(state, outcome.probability()));
            buildPreflop(state, built, support, remainder, fraction);
        }
        built.put(ROOT, new Node(-1, "", List.of(), Map.of(), List.copyOf(roots), List.of()));
        nodes = Map.copyOf(built);
        residuals = List.copyOf(remainder);
        if (nodes.size() != budget.completeTreeStates())
            throw new IllegalStateException("Connected state preflight differs");
        binding =
                new Binding(
                        MODEL,
                        CHANCE,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        false,
                        source.binding(),
                        flop,
                        fraction,
                        SixMaxHeadsUpPreflopGame.hash(handoffs),
                        SixMaxHeadsUpPreflopGame.hash(residuals),
                        budget);
    }

    private void buildPreflop(
            State state,
            Map<State, Node> built,
            Map<String, SixMaxSuppliedFlopHandoff> support,
            List<Residual> remainder,
            double fraction) {
        var parent =
                new SixMaxHeadsUpPreflopGame.State(
                        state.originalDealIndex(), state.preflopHistory());
        if (source.isTerminal(parent)) {
            var handoff = support.get(state.preflopHistory());
            var world =
                    handoff == null
                            ? Optional.<SixMaxSuppliedFlopHandoff.World>empty()
                            : handoff.world(state.originalDealIndex());
            if (world.isEmpty()) {
                built.put(state, terminal(source.terminalUtilities(parent)));
                return;
            }
            var selected =
                    new State(state.originalDealIndex(), state.preflopHistory(), Phase.FLOP, "");
            var residual =
                    new State(
                            state.originalDealIndex(),
                            state.preflopHistory(),
                            Phase.RESIDUAL_CHECKDOWN,
                            "");
            double p = 1.0 / ExactDeadCardFlopShowdown.FLOPS_PER_DEAL;
            built.put(
                    state,
                    new Node(
                            -1,
                            "",
                            List.of(),
                            Map.of(),
                            List.of(
                                    new ChanceOutcome<>(selected, p),
                                    new ChanceOutcome<>(residual, 1 - p)),
                            List.of()));
            var counts =
                    ExactDeadCardFlopShowdown.residual(
                            source.payoffs().get(state.originalDealIndex()).counts(),
                            world.orElseThrow().counts());
            var utilities =
                    settle(source.publicBettingState(parent), counts.estimate(), 0, -1, -1, false);
            built.put(residual, terminal(utilities));
            remainder.add(
                    new Residual(
                            state.preflopHistory(),
                            state.originalDealIndex(),
                            counts,
                            Arrays.stream(utilities).boxed().toList()));
            buildFlop(selected, built, handoff, world.orElseThrow(), fraction);
            return;
        }
        var children = new LinkedHashMap<String, State>();
        for (String action : source.legalActions(parent)) {
            var next = source.afterAction(parent, action);
            var child = new State(next.dealIndex(), next.publicHistory(), Phase.PREFLOP, "");
            children.put(action, child);
            buildPreflop(child, built, support, remainder, fraction);
        }
        built.put(
                state,
                new Node(
                        source.currentPlayer(parent),
                        "supplied-connected-single-flop:" + source.informationSet(parent),
                        source.legalActions(parent),
                        Map.copyOf(children),
                        List.of(),
                        List.of()));
    }

    private void buildFlop(
            State state,
            Map<State, Node> built,
            SixMaxSuppliedFlopHandoff handoff,
            SixMaxSuppliedFlopHandoff.World world,
            double fraction) {
        String history = state.flopHistory();
        var boundary =
                source.publicBettingState(
                        new SixMaxHeadsUpPreflopGame.State(
                                state.originalDealIndex(), state.preflopHistory()));
        int first = handoff.audit().firstToAct().ordinal(),
                second = handoff.audit().secondToAct().ordinal();
        boolean facingBet = history.equals("bet") || history.equals("check|bet");
        int actor = history.equals("") || history.equals("check|bet") ? first : second;
        if (Set.of("check|check", "bet|fold", "bet|call", "check|bet|fold", "check|bet|call")
                .contains(history)) {
            boolean folded = history.endsWith("fold");
            int bettor = history.startsWith("bet") ? first : second;
            double amount = history.equals("check|check") ? 0 : bet(boundary, fraction);
            built.put(
                    state,
                    terminal(
                            settle(
                                    boundary,
                                    world.counts().estimate(),
                                    amount,
                                    bettor,
                                    history.endsWith("call")
                                            ? (bettor == first ? second : first)
                                            : -1,
                                    folded)));
            return;
        }
        var actions = facingBet ? List.of("fold", "call") : List.of("check", "bet");
        var children = new LinkedHashMap<String, State>();
        for (String action : actions) {
            var child =
                    new State(
                            state.originalDealIndex(),
                            state.preflopHistory(),
                            Phase.FLOP,
                            history.isEmpty() ? action : history + "|" + action);
            children.put(action, child);
            buildFlop(child, built, handoff, world, fraction);
        }
        built.put(
                state,
                new Node(
                        actor,
                        "supplied-flop-one-bet:"
                                + world.sixDealtCombos().get(actor)
                                + ":"
                                + state.preflopHistory()
                                + ":"
                                + String.join(" ", handoff.audit().flop())
                                + ":"
                                + history,
                        actions,
                        Map.copyOf(children),
                        List.of(),
                        List.of()));
    }

    private static double[] settle(
            SixMaxPreflopBetting.State boundary,
            MultiwayShowdownEstimate shares,
            double bet,
            int bettor,
            int caller,
            boolean folded) {
        double[] committed =
                Arrays.stream(Seat.values()).mapToDouble(boundary::committedBb).toArray();
        int mask = boundary.liveSeats().stream().mapToInt(s -> 1 << s.ordinal()).sum();
        if (bet > 0) committed[bettor] += bet;
        if (caller >= 0) committed[caller] += bet;
        if (folded) mask = 1 << bettor;
        final int expected = mask;
        return AllInSidePots.settle(
                        committed,
                        mask,
                        0,
                        m -> {
                            if (m != expected)
                                throw new IllegalArgumentException(
                                        "Unexpected connected showdown subset");
                            return shares;
                        },
                        CashRakeRule.none(),
                        true)
                .utilitiesBb();
    }

    private static Node terminal(double[] utilities) {
        return new Node(
                -2, "", List.of(), Map.of(), List.of(), Arrays.stream(utilities).boxed().toList());
    }

    private Node node(State state) {
        var node = nodes.get(Objects.requireNonNull(state));
        if (node == null) throw new IllegalArgumentException("Foreign connected flop state");
        return node;
    }

    public Binding binding() {
        return binding;
    }

    public List<SixMaxSuppliedFlopHandoff.Audit> handoffs() {
        return handoffs;
    }

    public List<Residual> residuals() {
        return residuals;
    }

    public SixMaxSuppliedRangePreflopGame source() {
        return source;
    }

    public int playerCount() {
        return 6;
    }

    public State initialState() {
        return ROOT;
    }

    public boolean isTerminal(State state) {
        return node(state).actor() == -2;
    }

    public double[] terminalUtilities(State state) {
        if (!isTerminal(state)) throw new IllegalArgumentException("Terminal required");
        return node(state).utilities().stream().mapToDouble(Double::doubleValue).toArray();
    }

    public int currentPlayer(State state) {
        return node(state).actor();
    }

    public List<String> legalActions(State state) {
        if (node(state).actor() < 0) throw new IllegalArgumentException("Decision required");
        return node(state).actions();
    }

    public String informationSet(State state) {
        legalActions(state);
        return node(state).key();
    }

    public State afterAction(State state, String action) {
        var child = node(state).children().get(action);
        if (child == null) throw new IllegalArgumentException("Illegal connected action");
        return child;
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (node(state).actor() != -1) throw new IllegalArgumentException("Chance required");
        return node(state).chance();
    }
}
