package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.*;

/**
 * Conditional two-active-player preflop game at a six-seat table. Past actions use the fixed source
 * policy to condition the full joint deal, including folded cards. Non-all-in calls explicitly
 * check down; this is not a general cash-game continuation model.
 */
public final class SixMaxHeadsUpPreflopGame implements SixMaxHeadsUpDecisionGame {
    public static final String MODEL =
            "TWO_ACTIVE_PREFLOP_FIXED_SOURCE_BELIEFS_MANDATORY_CHECKDOWN/v1";
    public static final String BELIEFS = "FULL_JOINT_FIXED_SOURCE_POLICY_ACTION_CONDITIONED/v1";

    public record Specification(List<PublicAction> history, SixMaxPreflopBetting.Rules rules) {
        public Specification {
            history = List.copyOf(history);
            Objects.requireNonNull(rules);
            if (history.isEmpty()
                    || history.size() > 32
                    || rules.raiseSchedule() != SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET
                    || rules.raiseToBb().isEmpty()
                    || rules.raiseToBb().size() > 5)
                throw new IllegalArgumentException(
                        "Bounded history and 1–5 staged raise targets required");
            for (var action : history)
                if (action.seat() == null
                        || action.action() == null
                        || action.action().length() > 1024)
                    throw new IllegalArgumentException("Invalid public action");
            new SixMaxPreflopBetting(rules);
        }
    }

    public record State(int dealIndex, String publicHistory) {}

    public record Posterior(
            int dealIndex,
            List<String> dealtCombos,
            double sourceProbability,
            double historyLikelihood,
            double conditionalProbability) {
        public Posterior {
            dealtCombos = List.copyOf(dealtCombos);
        }
    }

    public record Binding(
            String model,
            String beliefs,
            String sourcePackHash,
            String sourceSpotHash,
            String sourcePolicyHash,
            String sourcePayoffHash,
            Specification specification,
            String posteriorHash,
            double historyReach,
            int posteriorJointDeals,
            int completeTreeStates) {
        public Binding {
            if (!MODEL.equals(model) || !BELIEFS.equals(beliefs))
                throw new IllegalArgumentException("Unsupported conditional preflop model");
            for (String hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            sourcePolicyHash,
                            sourcePayoffHash,
                            posteriorHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid conditional binding hash");
            Objects.requireNonNull(specification);
            if (!Double.isFinite(historyReach)
                    || historyReach <= 0
                    || historyReach > 1 + 1e-12
                    || posteriorJointDeals < 1
                    || posteriorJointDeals > 64
                    || completeTreeStates < 1
                    || completeTreeStates > FiniteTwoPlayerSequenceForm.MAX_TREE_NODES)
                throw new IllegalArgumentException("Invalid conditional game dimensions or reach");
        }
    }

    private record Node(
            SixMaxPreflopBetting.State betting,
            List<String> actions,
            Map<String, String> children) {}

    private record PayoffKey(List<String> hands, int mask) {}

    private final SixMaxPreflopBetting betting;
    private final Map<String, Node> nodes;
    private final Map<Integer, List<WeightedCombo>> hands;
    private final Map<State, List<Double>> utilities;
    private final List<ChanceOutcome<State>> roots;
    private final List<Posterior> posterior;
    private final List<Seat> activeSeats;
    private final Binding binding;
    private final SixMaxPreflopBetting.State publicRoot;

    public SixMaxHeadsUpPreflopGame(SixMaxPreflopSolutionPack pack, Specification specification)
            throws Exception {
        Objects.requireNonNull(pack);
        Objects.requireNonNull(specification);
        var source = pack.rebuildGame(); // Complete source validation, including every saved payoff
        // and policy row.
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(pack.payoffMethod())
                || pack.maxTerminalPayoffSEBb() != 0
                || !CashRakeRule.none().equals(pack.spot().rake()))
            throw new IllegalArgumentException(
                    "Conditional preflop requires an exact no-rake source");
        if (specification.rules().stackBb() != source.rules().stackBb()
                || specification.rules().smallBlindBb() != source.rules().smallBlindBb())
            throw new IllegalArgumentException("Stacks and blinds must match the source");
        betting = new SixMaxPreflopBetting(specification.rules());
        var publicState = betting.initialState();
        for (var action : specification.history()) {
            if (publicState.status() != SixMaxPreflopBetting.Status.DECISION
                    || publicState.actingSeat() != action.seat())
                throw new IllegalArgumentException("Illegal or out-of-turn frozen history");
            publicState = betting.apply(publicState, move(publicState, action.action()));
        }
        if (publicState.status() != SixMaxPreflopBetting.Status.DECISION
                || publicState.liveSeats().size() != 2
                || publicState.liveSeats().stream().anyMatch(publicState::isAllIn))
            throw new IllegalArgumentException("Exactly two live decision makers required");
        publicRoot = publicState;
        activeSeats = publicRoot.liveSeats();
        var pending = new ArrayList<Posterior>();
        var dealt = new TreeMap<Integer, List<WeightedCombo>>();
        double total = 0;
        for (var root : source.chanceOutcomes(source.initialState())) {
            var state = root.state();
            double likelihood = 1;
            for (var action : specification.history()) {
                if (source.isTerminal(state)
                        || source.currentPlayer(state) != action.seat().ordinal()
                        || !source.legalActions(state).contains(action.action()))
                    throw new IllegalArgumentException("Frozen history is not legal in the source");
                double probability =
                        MultiPlayerStrategyEvaluator.probability(
                                source, pack.solution(), state, action.action());
                likelihood = product(likelihood, probability);
                state = source.afterAction(state, action.action());
            }
            double mass = product(root.probability(), likelihood);
            if (mass > 0) {
                var cards = source.dealtHands(state);
                dealt.put(state.dealIndex(), cards);
                total += mass;
                pending.add(
                        new Posterior(
                                state.dealIndex(),
                                cards.stream().map(WeightedCombo::key).toList(),
                                root.probability(),
                                likelihood,
                                mass));
            }
        }
        if (!Double.isFinite(total) || total <= 0)
            throw new IllegalArgumentException("Frozen source history has zero reach");
        double reach = total;
        posterior =
                pending.stream()
                        .map(
                                p ->
                                        new Posterior(
                                                p.dealIndex(),
                                                p.dealtCombos(),
                                                p.sourceProbability(),
                                                p.historyLikelihood(),
                                                p.conditionalProbability() / reach))
                        .toList();
        hands = Map.copyOf(dealt);
        roots =
                posterior.stream()
                        .map(
                                p ->
                                        new ChanceOutcome<>(
                                                new State(p.dealIndex(), ""),
                                                p.conditionalProbability()))
                        .toList();
        var tree = new LinkedHashMap<String, Node>();
        build(publicRoot, "", 0, tree);
        nodes = Map.copyOf(tree);
        long states = 1L + nodes.size() * roots.size();
        if (states > FiniteTwoPlayerSequenceForm.MAX_TREE_NODES)
            throw new IllegalArgumentException("Conditional tree exceeds solver node bound");
        var estimates = new HashMap<PayoffKey, MultiwayShowdownEstimate>();
        for (var entry : pack.payoffs())
            estimates.put(new PayoffKey(entry.dealtCombos(), entry.activeMask()), entry.estimate());
        var values = new HashMap<State, List<Double>>();
        for (var root : roots)
            for (var entry : nodes.entrySet())
                if (entry.getValue().actions().isEmpty()) {
                    var state = entry.getValue().betting();
                    var cards =
                            hands.get(root.state().dealIndex()).stream()
                                    .map(WeightedCombo::key)
                                    .toList();
                    double[] committed = new double[6];
                    int mask = 0;
                    for (var seat : Seat.values()) {
                        committed[seat.ordinal()] = state.committedBb(seat);
                        if (!state.isFolded(seat)) mask |= 1 << seat.ordinal();
                    }
                    boolean flop = state.status() != SixMaxPreflopBetting.Status.UNCONTESTED;
                    var settled =
                            AllInSidePots.settle(
                                    committed,
                                    mask,
                                    0,
                                    m -> estimates.get(new PayoffKey(cards, m)),
                                    CashRakeRule.none(),
                                    flop);
                    if (settled.maximumStandardErrorBb() != 0)
                        throw new IllegalArgumentException("Exact conditional payouts required");
                    values.put(
                            new State(root.state().dealIndex(), entry.getKey()),
                            Arrays.stream(settled.utilitiesBb()).boxed().toList());
                }
        utilities = Map.copyOf(values);
        binding =
                new Binding(
                        MODEL,
                        BELIEFS,
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        SixMaxConnectedPostflopAudit.solutionHash(pack.solution()),
                        hash(pack.payoffs()),
                        specification,
                        hash(posterior),
                        reach,
                        roots.size(),
                        (int) states);
    }

    static String hash(Object value) throws Exception {
        return SixMaxHistoryPhysicalConditionalRefinement.hash(value);
    }

    private static double product(double a, double b) {
        double value = a * b;
        if (a > 0 && b > 0 && value < Double.MIN_NORMAL)
            throw new IllegalArgumentException("Conditional posterior underflow");
        return value;
    }

    static String encode(SixMaxPreflopBetting.Move move) {
        return switch (move.kind()) {
            case FOLD -> "fold";
            case CHECK -> "check";
            case CALL -> "call";
            case RAISE_TO -> "raise:" + move.amountBb();
            default -> throw new IllegalArgumentException("Unexpected blind action");
        };
    }

    private SixMaxPreflopBetting.Move move(SixMaxPreflopBetting.State state, String action) {
        return state.legalActions().stream()
                .filter(m -> encode(m).equals(action))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Illegal preflop action"));
    }

    private void build(
            SixMaxPreflopBetting.State state, String history, int depth, Map<String, Node> tree) {
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
            build(betting.apply(state, move(state, action)), children.get(action), depth + 1, tree);
    }

    private boolean root(State state) {
        return initialState().equals(state);
    }

    private Node node(State state) {
        Objects.requireNonNull(state);
        var node = nodes.get(state.publicHistory());
        if (!hands.containsKey(state.dealIndex()) || node == null)
            throw new IllegalArgumentException("Foreign conditional preflop state");
        return node;
    }

    public Binding binding() {
        return binding;
    }

    public List<Posterior> posterior() {
        return posterior;
    }

    public List<Seat> activeSeats() {
        return activeSeats;
    }

    public List<WeightedCombo> dealtHands(State state) {
        node(state);
        return hands.get(state.dealIndex());
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
        return !root(state) && node(state).actions().isEmpty();
    }

    public double[] terminalUtilities(State state) {
        node(state);
        var value = utilities.get(state);
        if (value == null) throw new IllegalArgumentException("Expected terminal state");
        return value.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public int currentPlayer(State state) {
        return root(state) ? -1 : node(state).betting().actingSeat().ordinal();
    }

    public List<String> legalActions(State state) {
        var n = node(state);
        if (n.actions().isEmpty()) throw new IllegalArgumentException("Terminal has no actions");
        return n.actions();
    }

    public String informationSet(State state) {
        int actor = currentPlayer(state);
        if (actor < 0) throw new IllegalArgumentException("Chance has no information set");
        return "conditional-preflop:"
                + dealtHands(state).get(actor).key()
                + ":"
                + state.publicHistory();
    }

    public State afterAction(State state, String action) {
        String child = node(state).children().get(action);
        if (child == null) throw new IllegalArgumentException("Illegal conditional action");
        return new State(state.dealIndex(), child);
    }

    public List<ChanceOutcome<State>> chanceOutcomes(State state) {
        if (!root(state)) throw new IllegalArgumentException("Expected chance root");
        return roots;
    }

    public OptionalDouble inactivePlayerUtility(State state, int player) {
        if (player < 0 || player >= 6) throw new IllegalArgumentException("Invalid player");
        if (!root(state)) node(state);
        var seat = Seat.values()[player];
        return publicRoot.isFolded(seat)
                ? OptionalDouble.of(-publicRoot.committedBb(seat))
                : OptionalDouble.empty();
    }
}
