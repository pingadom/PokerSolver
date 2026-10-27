package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.Phase;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import java.util.*;

/** One jointly solved preflop/flop game with explicit, history-specific bet-size choices. */
public final class SixMaxSuppliedFlopSizeMenuGame implements MultiPlayerCfrGame<State> {
    public static final String MODEL = "SUPPLIED_JOINT_PREFLOP_SELECTED_FLOP_SIZE_MENU/v1";
    public static final String PREFLOP_KEY = "supplied-size-menu-preflop:";
    public static final String FLOP_KEY = "supplied-size-menu-flop:";
    private static final List<Seat> ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);

    public record Menu(List<Double> firstToAct, List<Double> afterCheck) {
        public Menu {
            firstToAct = checked(firstToAct);
            afterCheck = checked(afterCheck);
        }

        private static List<Double> checked(List<Double> sizes) {
            sizes = List.copyOf(sizes);
            if (sizes.isEmpty() || sizes.size() > 4)
                throw new IllegalArgumentException("One to four ordered sizes required");
            double previous = 0;
            for (double size : sizes) {
                if (!Double.isFinite(size) || size <= previous || size > 1)
                    throw new IllegalArgumentException("Sizes must increase strictly within (0,1]");
                previous = size;
            }
            return sizes;
        }
    }

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
                    || completeTreeStates < sourceBudget.completeTreeStates()
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
                        "Size-menu game exceeds existing measured limits");
        }
    }

    public record Binding(
            String model,
            boolean trainerAdmission,
            SixMaxSuppliedFlopBettingGame.Binding physicalCarrier,
            Menu menu,
            Budget budget) {
        public Binding {
            if (!MODEL.equals(model)
                    || trainerAdmission
                    || physicalCarrier == null
                    || menu == null
                    || budget == null
                    || !budget.sourceBudget().equals(physicalCarrier.budget().sourceBudget()))
                throw new IllegalArgumentException("Invalid size-menu binding");
        }
    }

    public record PublicAction(String action, double amountBb) {}

    public record PublicState(
            Seat actingSeat,
            List<String> board,
            String publicPreflopHistory,
            List<SixMaxPreflopBetting.Move> preflopHistory,
            List<SixMaxSuppliedFlopBettingGame.PublicFlopMove> flopHistory,
            double potBb,
            double toCallBb,
            List<SixMaxSuppliedFlopBettingGame.PublicPlayer> players,
            List<PublicAction> actions) {
        public PublicState {
            board = List.copyOf(board);
            preflopHistory = List.copyOf(preflopHistory);
            flopHistory = List.copyOf(flopHistory);
            players = List.copyOf(players);
            actions = List.copyOf(actions);
        }
    }

    private record Node(
            int actor,
            String key,
            List<String> actions,
            Map<String, State> children,
            List<ChanceOutcome<State>> chance,
            List<Double> utilities) {}

    private final SixMaxSuppliedFlopBettingGame physical;
    private final Binding binding;
    private final Map<State, Node> nodes;

    private static double amount(SixMaxPreflopBetting.State boundary, double fraction) {
        double value =
                Math.min(
                        boundary.liveSeats().stream()
                                .mapToDouble(boundary::remainingStackBb)
                                .min()
                                .orElseThrow(),
                        Math.round(boundary.potBb() * fraction * 1_000_000) / 1_000_000.0);
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Size rounds to zero");
        return value;
    }

    private static void distinctAmounts(SixMaxPreflopBetting.State boundary, List<Double> sizes) {
        var amounts = new HashSet<Double>();
        for (double size : sizes)
            if (!amounts.add(amount(boundary, size)))
                throw new IllegalArgumentException("Rounded or stack-capped size aliases");
    }

    /**
     * Structural/card-overlap checks run before either physical hand enumeration or optimization.
     */
    public static Budget preflight(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, Menu menu) {
        Objects.requireNonNull(menu);
        var carrier = SixMaxSuppliedFlopBettingGame.preflight(input, flop, .5);
        var original = carrier.sourceBudget();
        var board = ExactDeadCardFlopShowdown.canonicalFlop(flop);
        var worlds =
                input.worlds().stream()
                        .filter(
                                w ->
                                        w.dealtCombos().stream()
                                                .flatMap(c -> Arrays.stream(c.split(" ")))
                                                .noneMatch(board::contains))
                        .toList();
        var infos = new EnumMap<Seat, Integer>(Seat.class);
        infos.putAll(original.informationSets());
        var sequences = new EnumMap<Seat, Integer>(Seat.class);
        sequences.putAll(original.sequences());
        var rules = new SixMaxPreflopBetting(input.specification().rules());
        var root = rules.initialState();
        for (var action : input.specification().history())
            root = rules.apply(root, move(root, action.action()));
        var boundaries = new ArrayList<SixMaxPreflopBetting.State>();
        collect(rules, root, boundaries);
        int a = menu.firstToAct().size(), b = menu.afterCheck().size();
        for (var boundary : boundaries) {
            distinctAmounts(boundary, menu.firstToAct());
            distinctAmounts(boundary, menu.afterCheck());
            var order = ORDER.stream().filter(boundary.liveSeats()::contains).toList();
            if (order.size() != 2) throw new IllegalArgumentException("Two active seats required");
            for (int position = 0; position < 2; position++) {
                var seat = order.get(position);
                int types =
                        (int)
                                worlds.stream()
                                        .map(w -> w.dealtCombos().get(seat.ordinal()))
                                        .distinct()
                                        .count();
                infos.compute(
                        seat, (s, n) -> n + (1 + (positionIndex(s, order) == 0 ? b : a)) * types);
                int branches = position == 0 ? 1 + a + 2 * b : 1 + b + 2 * a;
                sequences.compute(seat, (s, n) -> n + branches * types);
            }
        }
        int states =
                Math.addExact(
                        original.completeTreeStates(),
                        Math.multiplyExact(boundaries.size() * worlds.size(), 4 + 3 * (a + b)));
        return new Budget(
                original,
                boundaries.size(),
                worlds.size(),
                states,
                infos,
                sequences,
                carrier.additionalEnumeratedRunouts(),
                carrier.additionalHandEvaluations());
    }

    private static int positionIndex(Seat seat, List<Seat> order) {
        return order.indexOf(seat);
    }

    private static SixMaxPreflopBetting.Move move(SixMaxPreflopBetting.State state, String action) {
        return state.legalActions().stream()
                .filter(m -> SixMaxHeadsUpPreflopGame.encode(m).equals(action))
                .findFirst()
                .orElseThrow();
    }

    private static void collect(
            SixMaxPreflopBetting rules,
            SixMaxPreflopBetting.State state,
            List<SixMaxPreflopBetting.State> result) {
        if (state.status() == SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED) {
            result.add(state);
            return;
        }
        for (var action : state.legalActions()) collect(rules, rules.apply(state, action), result);
    }

    public SixMaxSuppliedFlopSizeMenuGame(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, Menu menu)
            throws Exception {
        this(buildPhysical(input, flop, menu), menu);
    }

    private static SixMaxSuppliedFlopBettingGame buildPhysical(
            SixMaxSuppliedRangePreflopGame.Input input, List<String> flop, Menu menu)
            throws Exception {
        preflight(input, flop, menu);
        return new SixMaxSuppliedFlopBettingGame(input, flop, .5);
    }

    // The carrier supplies physical counts only. No policy, betting value or numerical solve is
    // reused.
    SixMaxSuppliedFlopSizeMenuGame(SixMaxSuppliedFlopBettingGame physical, Menu menu) {
        this.physical = Objects.requireNonNull(physical);
        var budget = preflight(physical.source().input(), physical.binding().flop(), menu);
        binding = new Binding(MODEL, false, physical.binding(), menu, budget);
        var built = new LinkedHashMap<State, Node>();
        copyPhysical(physical.initialState(), built);
        nodes = Map.copyOf(built);
        if (nodes.size() != budget.completeTreeStates())
            throw new IllegalStateException("Size-menu state preflight differs");
    }

    private void copyPhysical(State state, Map<State, Node> built) {
        if (state.phase() == Phase.FLOP) {
            var handoff =
                    physical.handoffs().stream()
                            .filter(h -> h.publicHistory().equals(state.preflopHistory()))
                            .findFirst()
                            .orElseThrow();
            var world =
                    handoff.worlds().stream()
                            .filter(w -> w.originalDealIndex() == state.originalDealIndex())
                            .findFirst()
                            .orElseThrow();
            buildFlop(state, built, handoff, world);
        } else if (physical.isTerminal(state)) {
            built.put(state, terminal(physical.terminalUtilities(state)));
        } else if (physical.currentPlayer(state) == -1) {
            var outcomes = physical.chanceOutcomes(state);
            for (var outcome : outcomes) copyPhysical(outcome.state(), built);
            built.put(state, new Node(-1, "", List.of(), Map.of(), outcomes, List.of()));
        } else {
            var children = new LinkedHashMap<String, State>();
            for (String action : physical.legalActions(state)) {
                var child = physical.afterAction(state, action);
                children.put(action, child);
                copyPhysical(child, built);
            }
            var parent =
                    new SixMaxHeadsUpPreflopGame.State(
                            state.originalDealIndex(), state.preflopHistory());
            built.put(
                    state,
                    new Node(
                            physical.currentPlayer(state),
                            PREFLOP_KEY + physical.source().informationSet(parent),
                            physical.legalActions(state),
                            Map.copyOf(children),
                            List.of(),
                            List.of()));
        }
    }

    private void buildFlop(
            State state,
            Map<State, Node> built,
            SixMaxSuppliedFlopHandoff.Audit handoff,
            SixMaxSuppliedFlopHandoff.World world) {
        String path = state.flopHistory();
        var boundary = boundary(state);
        int first = handoff.firstToAct().ordinal(), second = handoff.secondToAct().ordinal();
        String[] moves = path.isEmpty() ? new String[0] : path.split("\\|");
        String betAction =
                Arrays.stream(moves).filter(s -> s.startsWith("bet:")).findFirst().orElse(null);
        if (path.equals("check|check") || path.endsWith("|fold") || path.endsWith("|call")) {
            boolean firstBet = moves[0].startsWith("bet:");
            int bettor = firstBet ? first : second;
            double bet =
                    betAction == null
                            ? 0
                            : amount(boundary, Double.parseDouble(betAction.substring(4)));
            double[] committed =
                    Arrays.stream(Seat.values()).mapToDouble(boundary::committedBb).toArray();
            int mask = (1 << first) | (1 << second);
            if (bet > 0) committed[bettor] += bet;
            if (path.endsWith("|call")) committed[bettor == first ? second : first] += bet;
            if (path.endsWith("|fold")) mask = 1 << bettor;
            final int expected = mask;
            var utilities =
                    AllInSidePots.settle(
                                    committed,
                                    mask,
                                    0,
                                    m -> {
                                        if (m != expected)
                                            throw new IllegalArgumentException(
                                                    "Unexpected showdown subset");
                                        return world.counts().estimate();
                                    },
                                    CashRakeRule.none(),
                                    true)
                            .utilitiesBb();
            built.put(state, terminal(utilities));
            return;
        }
        int actor = moves.length % 2 == 0 ? first : second;
        var actions = new ArrayList<String>();
        if (betAction != null) actions.addAll(List.of("fold", "call"));
        else {
            actions.add("check");
            for (double size :
                    moves.length == 0 ? binding.menu().firstToAct() : binding.menu().afterCheck())
                actions.add("bet:" + Double.toString(size));
        }
        var children = new LinkedHashMap<String, State>();
        for (String action : actions) {
            var child =
                    new State(
                            state.originalDealIndex(),
                            state.preflopHistory(),
                            Phase.FLOP,
                            path.isEmpty() ? action : path + "|" + action);
            children.put(action, child);
            buildFlop(child, built, handoff, world);
        }
        built.put(
                state,
                new Node(
                        actor,
                        FLOP_KEY
                                + world.sixDealtCombos().get(actor)
                                + ":"
                                + state.preflopHistory()
                                + ":"
                                + String.join(" ", handoff.flop())
                                + ":"
                                + path,
                        List.copyOf(actions),
                        Map.copyOf(children),
                        List.of(),
                        List.of()));
    }

    private SixMaxPreflopBetting.State boundary(State state) {
        return source().publicBettingState(
                        new SixMaxHeadsUpPreflopGame.State(
                                state.originalDealIndex(), state.preflopHistory()));
    }

    private static Node terminal(double[] utilities) {
        return new Node(
                -2, "", List.of(), Map.of(), List.of(), Arrays.stream(utilities).boxed().toList());
    }

    private Node node(State state) {
        var value = nodes.get(Objects.requireNonNull(state));
        if (value == null) throw new IllegalArgumentException("Foreign size-menu state");
        return value;
    }

    public PublicState publicFlopState(State state) {
        if (node(state).actor() < 0 || state.phase() != Phase.FLOP)
            throw new IllegalArgumentException("An owned active flop decision required");
        var boundary = boundary(state);
        var handoff =
                physical.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(state.preflopHistory()))
                        .findFirst()
                        .orElseThrow();
        double[] committed =
                Arrays.stream(Seat.values()).mapToDouble(boundary::committedBb).toArray();
        double[] remaining =
                Arrays.stream(Seat.values()).mapToDouble(boundary::remainingStackBb).toArray();
        var history = new ArrayList<SixMaxSuppliedFlopBettingGame.PublicFlopMove>();
        var cursor = new State(state.originalDealIndex(), state.preflopHistory(), Phase.FLOP, "");
        double outstanding = 0;
        if (!state.flopHistory().isEmpty())
            for (String action : state.flopHistory().split("\\|")) {
                int actor = currentPlayer(cursor);
                double paid =
                        action.startsWith("bet:")
                                ? amount(boundary, Double.parseDouble(action.substring(4)))
                                : action.equals("call") ? outstanding : 0;
                if (action.startsWith("bet:")) outstanding = paid;
                committed[actor] += paid;
                remaining[actor] -= paid;
                history.add(
                        new SixMaxSuppliedFlopBettingGame.PublicFlopMove(
                                Seat.values()[actor], action, paid));
                cursor = afterAction(cursor, action);
            }
        if (!cursor.equals(state)) throw new IllegalStateException("Public path differs");
        var players = new ArrayList<SixMaxSuppliedFlopBettingGame.PublicPlayer>();
        for (var seat : Seat.values()) {
            int i = seat.ordinal();
            players.add(
                    new SixMaxSuppliedFlopBettingGame.PublicPlayer(
                            seat,
                            !boundary.liveSeats().contains(seat)
                                    ? "FOLDED"
                                    : currentPlayer(state) == i
                                            ? "ACTING"
                                            : remaining[i] == 0 ? "ALL_IN" : "ACTIVE",
                            committed[i],
                            remaining[i]));
        }
        double toCall = outstanding;
        var actions =
                legalActions(state).stream()
                        .map(
                                a ->
                                        new PublicAction(
                                                a,
                                                a.startsWith("bet:")
                                                        ? amount(
                                                                boundary,
                                                                Double.parseDouble(a.substring(4)))
                                                        : a.equals("call") ? toCall : 0))
                        .toList();
        return new PublicState(
                Seat.values()[currentPlayer(state)],
                handoff.flop(),
                state.preflopHistory(),
                handoff.completePreflopHistory(),
                history,
                Arrays.stream(committed).sum(),
                toCall,
                players,
                actions);
    }

    public Binding binding() {
        return binding;
    }

    public SixMaxSuppliedRangePreflopGame source() {
        return physical.source();
    }

    public List<SixMaxSuppliedFlopHandoff.Audit> handoffs() {
        return physical.handoffs();
    }

    public List<SixMaxSuppliedFlopBettingGame.Residual> residuals() {
        return physical.residuals();
    }

    public int playerCount() {
        return 6;
    }

    public State initialState() {
        return physical.initialState();
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
        legalActions(state);
        var child = node(state).children().get(action);
        if (child == null) throw new IllegalArgumentException("Illegal size-menu action");
        return child;
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (node(state).actor() != -1) throw new IllegalArgumentException("Chance required");
        return node(state).chance();
    }
}
