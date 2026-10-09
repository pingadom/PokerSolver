package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.Specification;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.util.*;

/** Two live seats with explicitly supplied joint beliefs, never inferred source-policy reach. */
public final class SixMaxSuppliedRangePreflopGame implements SixMaxHeadsUpDecisionGame {
    public static final String INPUT_SCHEMA = "pokerlab-supplied-joint-preflop-input/v1";
    public static final String MODEL = "TWO_ACTIVE_SUPPLIED_JOINT_PREFLOP_MANDATORY_CHECKDOWN/v1";
    public static final String BELIEFS = "EXPLICIT_SUPPLIED_CONDITIONAL_JOINT_PRIOR/v1";
    public static final String HISTORY_REACH = "UNAVAILABLE_NO_SOURCE_POLICY";
    public static final long MAX_BOARDS = 20_000_000;
    public static final long MAX_HAND_EVALUATIONS = 40_000_000;

    public record World(List<String> dealtCombos, double weight) {
        public World {
            if (dealtCombos == null
                    || dealtCombos.size() != 6
                    || !Double.isFinite(weight)
                    || weight <= 0)
                throw new IllegalArgumentException(
                        "Six dealt combos and positive finite joint weight required");
            dealtCombos =
                    dealtCombos.stream()
                            .map(SixMaxSuppliedRangePreflopGame::combo)
                            .map(WeightedCombo::key)
                            .toList();
            var cards = new HashSet<Card>();
            for (String key : dealtCombos) {
                var hand = combo(key);
                if (!cards.add(hand.first()) || !cards.add(hand.second()))
                    throw new IllegalArgumentException("Joint world contains colliding cards");
            }
        }
    }

    public record Input(String schemaVersion, Specification specification, List<World> worlds) {
        public Input {
            if (!INPUT_SCHEMA.equals(schemaVersion)
                    || specification == null
                    || worlds == null
                    || worlds.isEmpty()
                    || worlds.size() > 64
                    || worlds.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException(
                        "Versioned bounded supplied joint input required");
            worlds =
                    worlds.stream()
                            .sorted(Comparator.comparing(w -> String.join("|", w.dealtCombos())))
                            .toList();
            if (worlds.stream().map(World::dealtCombos).distinct().count() != worlds.size())
                throw new IllegalArgumentException("Duplicate physical joint world");
        }
    }

    public record JointDeal(
            int dealIndex, List<String> dealtCombos, double conditionalProbability) {
        public JointDeal {
            dealtCombos = List.copyOf(dealtCombos);
            if (dealIndex < 0
                    || dealtCombos.size() != 6
                    || !Double.isFinite(conditionalProbability)
                    || conditionalProbability < Double.MIN_NORMAL
                    || conditionalProbability > 1)
                throw new IllegalArgumentException("Invalid supplied joint probability");
        }
    }

    public record Budget(
            int jointDeals,
            int publicStates,
            int completeTreeStates,
            Map<Seat, Integer> informationSets,
            Map<Seat, Integer> sequences,
            long enumeratedBoards,
            long handEvaluations) {
        public Budget {
            informationSets = Map.copyOf(informationSets);
            sequences = Map.copyOf(sequences);
            if (jointDeals < 1
                    || jointDeals > 64
                    || publicStates < 1
                    || completeTreeStates != 1L + jointDeals * publicStates
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
                    || enumeratedBoards != jointDeals * ExactDeadCardHeadsUpShowdown.BOARDS_PER_DEAL
                    || enumeratedBoards > MAX_BOARDS
                    || handEvaluations != 2 * enumeratedBoards
                    || handEvaluations > MAX_HAND_EVALUATIONS)
                throw new IllegalArgumentException(
                        "Supplied conditional game exceeds a measured budget");
        }
    }

    public record Payoff(
            int dealIndex, List<String> dealtCombos, ExactDeadCardHeadsUpShowdown.Counts counts) {
        public Payoff {
            dealtCombos = List.copyOf(dealtCombos);
            if (dealIndex < 0 || dealtCombos.size() != 6 || counts == null)
                throw new IllegalArgumentException("Invalid supplied payoff audit");
        }
    }

    public record Binding(
            String model,
            String beliefs,
            String sourceHistoryReachStatus,
            String inputHash,
            String jointPriorHash,
            String payoffAlgorithm,
            String payoffHash,
            Budget budget) {
        public Binding {
            if (!MODEL.equals(model)
                    || !BELIEFS.equals(beliefs)
                    || !HISTORY_REACH.equals(sourceHistoryReachStatus)
                    || !ExactDeadCardHeadsUpShowdown.ALGORITHM.equals(payoffAlgorithm)
                    || budget == null)
                throw new IllegalArgumentException("Unsupported supplied joint game binding");
            for (String hash : List.of(inputHash, jointPriorHash, payoffHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid binding hash");
        }
    }

    private record Node(
            SixMaxPreflopBetting.State betting,
            List<String> actions,
            Map<String, String> children) {}

    private record Prepared(
            SixMaxPreflopBetting.State root,
            Map<String, Node> nodes,
            List<List<WeightedCombo>> hands,
            List<JointDeal> prior,
            Budget budget) {}

    private final Input input;
    private final Prepared prepared;
    private final List<ChanceOutcome<State>> roots;
    private final Map<State, List<Double>> utilities;
    private final List<Payoff> payoffs;
    private final Binding binding;

    public static Budget preflight(Input input) {
        return prepare(Objects.requireNonNull(input)).budget();
    }

    public SixMaxSuppliedRangePreflopGame(Input input) throws Exception {
        this.input = Objects.requireNonNull(input);
        prepared = prepare(input); // All dimensions, cards, history and work checked before
        // enumeration.
        int activeMask = prepared.root().liveSeats().stream().mapToInt(s -> 1 << s.ordinal()).sum();
        var audit = new ArrayList<Payoff>();
        var values = new HashMap<State, List<Double>>();
        for (var world : prepared.prior()) {
            var counts =
                    ExactDeadCardHeadsUpShowdown.enumerate(
                            prepared.hands().get(world.dealIndex()), activeMask);
            audit.add(new Payoff(world.dealIndex(), world.dealtCombos(), counts));
            var estimate = counts.estimate();
            for (var entry : prepared.nodes().entrySet())
                if (entry.getValue().actions().isEmpty()) {
                    var state = entry.getValue().betting();
                    double[] committed = new double[6];
                    int mask = 0;
                    for (var seat : Seat.values()) {
                        committed[seat.ordinal()] = state.committedBb(seat);
                        if (!state.isFolded(seat)) mask |= 1 << seat.ordinal();
                    }
                    var settled =
                            AllInSidePots.settle(
                                    committed,
                                    mask,
                                    0,
                                    m -> {
                                        if (m != activeMask)
                                            throw new IllegalArgumentException(
                                                    "Unexpected showdown subset");
                                        return estimate;
                                    },
                                    CashRakeRule.none(),
                                    state.status() != SixMaxPreflopBetting.Status.UNCONTESTED);
                    if (settled.maximumStandardErrorBb() != 0 || settled.rakeBb() != 0)
                        throw new IllegalArgumentException("Exact no-rake payouts required");
                    values.put(
                            new State(world.dealIndex(), entry.getKey()),
                            Arrays.stream(settled.utilitiesBb()).boxed().toList());
                }
        }
        payoffs = List.copyOf(audit);
        utilities = Map.copyOf(values);
        roots =
                prepared.prior().stream()
                        .map(
                                w ->
                                        new ChanceOutcome<>(
                                                new State(w.dealIndex(), ""),
                                                w.conditionalProbability()))
                        .toList();
        binding =
                new Binding(
                        MODEL,
                        BELIEFS,
                        HISTORY_REACH,
                        SixMaxHeadsUpPreflopGame.hash(input),
                        SixMaxHeadsUpPreflopGame.hash(prepared.prior()),
                        ExactDeadCardHeadsUpShowdown.ALGORITHM,
                        SixMaxHeadsUpPreflopGame.hash(payoffs),
                        prepared.budget());
    }

    private static WeightedCombo combo(String text) {
        if (text == null || !text.matches("[2-9TJQKA][cdhs] [2-9TJQKA][cdhs]"))
            throw new IllegalArgumentException(
                    "Expected two compact physical cards separated by one space");
        return new WeightedCombo(
                Card.parse(text.substring(0, 2)), Card.parse(text.substring(3, 5)), 1);
    }

    private static Prepared prepare(Input input) {
        var betting = new SixMaxPreflopBetting(input.specification().rules());
        var root = betting.initialState();
        for (var action : input.specification().history()) {
            if (root.status() != SixMaxPreflopBetting.Status.DECISION
                    || root.actingSeat() != action.seat())
                throw new IllegalArgumentException("Illegal or out-of-turn supplied history");
            root = betting.apply(root, move(root, action.action()));
        }
        if (root.status() != SixMaxPreflopBetting.Status.DECISION
                || root.liveSeats().size() != 2
                || root.liveSeats().stream().anyMatch(root::isAllIn))
            throw new IllegalArgumentException("Exactly two live decision makers required");
        var tree = new LinkedHashMap<String, Node>();
        build(betting, root, "", 0, tree);
        var hands =
                input.worlds().stream()
                        .map(
                                w ->
                                        w.dealtCombos().stream()
                                                .map(SixMaxSuppliedRangePreflopGame::combo)
                                                .toList())
                        .toList();
        double maximum = input.worlds().stream().mapToDouble(World::weight).max().orElseThrow();
        double total = input.worlds().stream().mapToDouble(w -> w.weight() / maximum).sum();
        var prior = new ArrayList<JointDeal>();
        for (int i = 0; i < hands.size(); i++)
            prior.add(
                    new JointDeal(
                            i,
                            input.worlds().get(i).dealtCombos(),
                            (input.worlds().get(i).weight() / maximum) / total));
        var infos = new EnumMap<Seat, Integer>(Seat.class);
        var sequences = new EnumMap<Seat, Integer>(Seat.class);
        for (var seat : root.liveSeats()) {
            int types =
                    (int) hands.stream().map(h -> h.get(seat.ordinal()).key()).distinct().count();
            int publicInfos = 0, actionSequences = 0;
            for (var node : tree.values())
                if (!node.actions().isEmpty() && node.betting().actingSeat() == seat) {
                    publicInfos++;
                    actionSequences += node.actions().size();
                }
            infos.put(seat, types * publicInfos);
            sequences.put(seat, 1 + types * actionSequences);
        }
        var budget =
                new Budget(
                        hands.size(),
                        tree.size(),
                        1 + hands.size() * tree.size(),
                        infos,
                        sequences,
                        hands.size() * ExactDeadCardHeadsUpShowdown.BOARDS_PER_DEAL,
                        2 * hands.size() * ExactDeadCardHeadsUpShowdown.BOARDS_PER_DEAL);
        return new Prepared(root, Map.copyOf(tree), hands, List.copyOf(prior), budget);
    }

    private static SixMaxPreflopBetting.Move move(SixMaxPreflopBetting.State state, String action) {
        return state.legalActions().stream()
                .filter(m -> SixMaxHeadsUpPreflopGame.encode(m).equals(action))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Illegal supplied preflop action"));
    }

    private static void build(
            SixMaxPreflopBetting betting,
            SixMaxPreflopBetting.State state,
            String history,
            int depth,
            Map<String, Node> tree) {
        if (depth + 1 > FiniteTwoPlayerSequenceForm.MAX_DEPTH
                || tree.size() >= FiniteTwoPlayerSequenceForm.MAX_TREE_NODES)
            throw new IllegalArgumentException("Bounded conditional public tree required");
        var actions = state.legalActions().stream().map(SixMaxHeadsUpPreflopGame::encode).toList();
        var children = new LinkedHashMap<String, String>();
        for (String action : actions)
            children.put(action, history + "|" + state.actingSeat() + ":" + action);
        if (tree.putIfAbsent(history, new Node(state, actions, Map.copyOf(children))) != null)
            throw new IllegalArgumentException("Duplicate public history");
        for (String action : actions)
            build(
                    betting,
                    betting.apply(state, move(state, action)),
                    children.get(action),
                    depth + 1,
                    tree);
    }

    private Node node(State state) {
        Objects.requireNonNull(state);
        var node = prepared.nodes().get(state.publicHistory());
        if (node == null || state.dealIndex() < 0 || state.dealIndex() >= prepared.hands().size())
            throw new IllegalArgumentException("Foreign supplied conditional state");
        return node;
    }

    public Input input() {
        return input;
    }

    public Binding binding() {
        return binding;
    }

    public List<JointDeal> prior() {
        return prepared.prior();
    }

    public List<Payoff> payoffs() {
        return payoffs;
    }

    public List<Seat> activeSeats() {
        return prepared.root().liveSeats();
    }

    public List<WeightedCombo> dealtHands(State state) {
        node(state);
        return prepared.hands().get(state.dealIndex());
    }

    public SixMaxPreflopBetting.State publicBettingState(State state) {
        return node(state).betting();
    }

    public int playerCount() {
        return 6;
    }

    public State initialState() {
        return new State(-1, "");
    }

    public boolean isTerminal(State state) {
        return !initialState().equals(state) && node(state).actions().isEmpty();
    }

    public double[] terminalUtilities(State state) {
        node(state);
        var value = utilities.get(state);
        if (value == null) throw new IllegalArgumentException("Expected terminal state");
        return value.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public int currentPlayer(State state) {
        return initialState().equals(state) ? -1 : node(state).betting().actingSeat().ordinal();
    }

    public List<String> legalActions(State state) {
        var actions = node(state).actions();
        if (actions.isEmpty()) throw new IllegalArgumentException("Terminal has no actions");
        return actions;
    }

    public String informationSet(State state) {
        int actor = currentPlayer(state);
        if (actor < 0) throw new IllegalArgumentException("Chance has no information set");
        return "supplied-conditional-preflop:"
                + dealtHands(state).get(actor).key()
                + ":"
                + state.publicHistory();
    }

    public State afterAction(State state, String action) {
        String child = node(state).children().get(action);
        if (child == null)
            throw new IllegalArgumentException("Illegal supplied conditional action");
        return new State(state.dealIndex(), child);
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (!initialState().equals(state))
            throw new IllegalArgumentException("Expected chance root");
        return roots;
    }

    public OptionalDouble inactivePlayerUtility(State state, int player) {
        if (player < 0 || player >= 6) throw new IllegalArgumentException("Invalid player");
        if (!initialState().equals(state)) node(state);
        var seat = Seat.values()[player];
        return prepared.root().isFolded(seat)
                ? OptionalDouble.of(-prepared.root().committedBb(seat))
                : OptionalDouble.empty();
    }
}
