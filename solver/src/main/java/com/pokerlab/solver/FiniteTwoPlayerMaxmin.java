package com.pokerlab.solver;

import java.util.*;

/**
 * Exact finite normal-form reduction for two decision makers in a perfect-recall game. Other seats
 * may have fixed utilities. Exhaustive plans are deliberately capped; this is not a general
 * multiplayer equilibrium solver. Strategies condition on information sets, never states.
 */
public final class FiniteTwoPlayerMaxmin {
    public static final String ALGORITHM = "BOUNDED_PERFECT_RECALL_NORMAL_FORM_OWNED_SIMPLEX/v1";
    public static final int MAX_TREE_NODES = 1_000;
    public static final int MAX_DEPTH = 32;
    public static final long MAX_PROFILE_NODE_VISITS = 4_096_000;
    private static final double TOLERANCE = FiniteMatrixMaxmin.CERTIFICATE_TOLERANCE;

    public record OwnAction(String informationSetKey, String action) {}

    public record InformationSet(
            String key, int actor, int depth, List<String> actions, List<OwnAction> ownHistory) {
        public InformationSet {
            actions = List.copyOf(actions);
            ownHistory = List.copyOf(ownHistory);
        }
    }

    public record Audit(
            String algorithm,
            int firstActor,
            int secondActor,
            List<InformationSet> informationSets,
            int treeNodes,
            int firstPlans,
            int secondPlans,
            long profileNodeVisits,
            double constantSum,
            String matrixHash,
            FiniteMatrixMaxmin.Solution matrixSolution,
            String behavioralPolicyHash,
            MultiPlayerInformationSetBestResponse.Report behavioralQuality) {
        public Audit {
            informationSets = List.copyOf(informationSets);
        }
    }

    /** The private constructor prevents a caller from manufacturing a solved result. */
    public static final class Result {
        private final Map<String, Map<String, Double>> strategy;
        private final Audit audit;

        private Result(Map<String, Map<String, Double>> strategy, Audit audit) {
            this.strategy = new CfrSolution(1, strategy).strategy();
            this.audit = audit;
        }

        public Map<String, Map<String, Double>> strategy() {
            return strategy;
        }

        public Audit audit() {
            return audit;
        }
    }

    private record Node(
            int actor,
            String key,
            List<Node> children,
            List<Double> probabilities,
            double[] utilities) {}

    private static final class Builder<S> {
        final MultiPlayerCfrGame<S> game;
        final TreeMap<String, InformationSet> informationSets = new TreeMap<>();
        final TreeSet<Integer> actors = new TreeSet<>();
        int nodes;

        Builder(MultiPlayerCfrGame<S> game) {
            this.game = game;
        }

        Node collect(S state, int depth, List<List<OwnAction>> histories) {
            if (++nodes > MAX_TREE_NODES || depth > MAX_DEPTH)
                throw new IllegalArgumentException("Finite maxmin tree/depth budget exceeded");
            if (game.isTerminal(state)) {
                double[] utilities = game.terminalUtilities(state).clone();
                if (utilities.length != game.playerCount())
                    throw new IllegalArgumentException("Terminal utility cardinality differs");
                for (double value : utilities)
                    if (!Double.isFinite(value) || Math.abs(value) > FiniteMatrixMaxmin.MAX_PAYOFF)
                        throw new IllegalArgumentException("Invalid terminal payoff");
                return new Node(-2, null, List.of(), List.of(), utilities);
            }
            int actor = game.currentPlayer(state);
            var children = new ArrayList<Node>();
            if (actor == -1) {
                var outcomes = game.chanceOutcomes(state);
                if (outcomes.isEmpty() || outcomes.size() > MAX_TREE_NODES)
                    throw new IllegalArgumentException("Invalid bounded chance support");
                var probabilities = new ArrayList<Double>();
                double sum = 0;
                for (var outcome : outcomes) {
                    double p = outcome.probability();
                    if (!Double.isFinite(p) || p <= 0 || p > 1)
                        throw new IllegalArgumentException(
                                "Positive finite chance weights required");
                    sum += p;
                    probabilities.add(p);
                }
                if (Math.abs(sum - 1) > 1e-12)
                    throw new IllegalArgumentException("Chance probabilities must sum to one");
                for (var outcome : outcomes)
                    children.add(collect(outcome.state(), depth + 1, histories));
                return new Node(-1, null, List.copyOf(children), List.copyOf(probabilities), null);
            }
            if (actor < 0 || actor >= game.playerCount())
                throw new IllegalArgumentException("Invalid decision actor");
            actors.add(actor);
            if (actors.size() > 2)
                throw new IllegalArgumentException("Exactly two decision makers required");
            String info = Objects.requireNonNull(game.informationSet(state), "informationSet");
            String key = actor + ":" + info;
            var actions = List.copyOf(game.legalActions(state));
            if (info.isBlank()
                    || actions.isEmpty()
                    || actions.size() > FiniteMatrixMaxmin.MAX_PLANS
                    || new HashSet<>(actions).size() != actions.size()
                    || actions.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Distinct bounded legal actions required");
            var ownHistory = histories.get(actor);
            if (ownHistory.stream().anyMatch(a -> a.informationSetKey().equals(key)))
                throw new IllegalArgumentException("Repeated information set on an own path");
            var row = new InformationSet(key, actor, depth, actions, ownHistory);
            var old = informationSets.putIfAbsent(key, row);
            if (old != null && !old.equals(row))
                throw new IllegalArgumentException(
                        "Perfect recall, one depth and consistent actions required");
            for (String action : actions) {
                var nextOwn = new ArrayList<>(ownHistory);
                nextOwn.add(new OwnAction(key, action));
                var nextHistories = new ArrayList<>(histories);
                nextHistories.set(actor, List.copyOf(nextOwn));
                children.add(collect(game.afterAction(state, action), depth + 1, nextHistories));
            }
            return new Node(actor, key, List.copyOf(children), List.of(), null);
        }
    }

    private FiniteTwoPlayerMaxmin() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game) throws Exception {
        Objects.requireNonNull(game, "game");
        if (game.playerCount() < 2 || game.playerCount() > 6)
            throw new IllegalArgumentException("Two to six seats required");
        var histories = new ArrayList<List<OwnAction>>();
        for (int p = 0; p < game.playerCount(); p++) histories.add(List.of());
        var builder = new Builder<>(game);
        var root = builder.collect(game.initialState(), 0, histories);
        if (builder.actors.size() != 2)
            throw new IllegalArgumentException("Exactly two decision makers required");
        int first = builder.actors.first(), second = builder.actors.last();
        var infos = List.copyOf(builder.informationSets.values());
        var firstInfos = infos.stream().filter(i -> i.actor() == first).toList();
        var secondInfos = infos.stream().filter(i -> i.actor() == second).toList();
        var firstPlans = plans(firstInfos);
        var secondPlans = plans(secondInfos);
        if ((long) firstPlans.size() * secondPlans.size() * builder.nodes > MAX_PROFILE_NODE_VISITS)
            throw new IllegalArgumentException("Finite maxmin profile traversal budget exceeded");
        double[][] values = new double[firstPlans.size()][secondPlans.size()];
        double[] reference = null;
        double constant = Double.NaN;
        long[] visits = {0};
        for (int a = 0; a < firstPlans.size(); a++)
            for (int b = 0; b < secondPlans.size(); b++) {
                var choices = new HashMap<>(firstPlans.get(a));
                choices.putAll(secondPlans.get(b));
                double[] utilities = new double[game.playerCount()];
                evaluate(root, choices, 1, utilities, visits);
                values[a][b] = utilities[first];
                double sum = utilities[first] + utilities[second];
                if (reference == null) {
                    reference = utilities;
                    constant = sum;
                }
                if (Math.abs(sum - constant) > TOLERANCE)
                    throw new IllegalArgumentException("Two active utilities are not constant-sum");
                for (int p = 0; p < game.playerCount(); p++)
                    if (p != first
                            && p != second
                            && Math.abs(utilities[p] - reference[p]) > TOLERANCE)
                        throw new IllegalArgumentException(
                                "Inactive seat payoff depends on decisions");
            }
        var solution = FiniteMatrixMaxmin.solve(values);
        var rows = new LinkedHashMap<String, Map<String, Double>>();
        rows.putAll(behavioral(firstInfos, firstPlans, solution.rowMixture()));
        rows.putAll(behavioral(secondInfos, secondPlans, solution.columnMixture()));
        // CfrSolution is the existing strategy container. Its sentinel 1 is not an LP iteration.
        // Production derivatives retain predecessor joint iterations and record LP work separately.
        var policy = new CfrSolution(1, rows);
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        if (quality.nashConvBb() > TOLERANCE
                || Math.abs(quality.profileUtilitiesBb().get(first) - solution.lowerValue())
                        > TOLERANCE
                || Math.abs(
                                quality.profileUtilitiesBb().get(second)
                                        - (constant - solution.upperValue()))
                        > TOLERANCE)
            throw new IllegalStateException(
                    "Behavioral conversion failed independent game best-response check");
        var audit =
                new Audit(
                        ALGORITHM,
                        first,
                        second,
                        infos,
                        builder.nodes,
                        firstPlans.size(),
                        secondPlans.size(),
                        visits[0],
                        constant,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(values),
                        solution,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        quality);
        return new Result(rows, audit);
    }

    private static List<Map<String, Integer>> plans(List<InformationSet> rows) {
        long count = 1;
        for (var row : rows) {
            count *= row.actions().size();
            if (count > FiniteMatrixMaxmin.MAX_PLANS)
                throw new IllegalArgumentException("Finite maxmin pure-plan cap exceeded");
        }
        var result = new ArrayList<Map<String, Integer>>();
        for (int p = 0; p < count; p++) {
            int residual = p;
            var plan = new LinkedHashMap<String, Integer>();
            for (var row : rows) {
                plan.put(row.key(), residual % row.actions().size());
                residual /= row.actions().size();
            }
            result.add(Map.copyOf(plan));
        }
        return List.copyOf(result);
    }

    private static void evaluate(
            Node node,
            Map<String, Integer> choices,
            double reach,
            double[] utilities,
            long[] visits) {
        if (++visits[0] > MAX_PROFILE_NODE_VISITS)
            throw new IllegalStateException("Finite maxmin evaluation budget exceeded");
        if (node.actor() == -2) {
            for (int p = 0; p < utilities.length; p++) utilities[p] += reach * node.utilities()[p];
        } else if (node.actor() == -1) {
            for (int i = 0; i < node.children().size(); i++)
                evaluate(
                        node.children().get(i),
                        choices,
                        reach * node.probabilities().get(i),
                        utilities,
                        visits);
        } else
            evaluate(
                    node.children().get(choices.get(node.key())),
                    choices,
                    reach,
                    utilities,
                    visits);
    }

    /**
     * Kuhn conversion: condition plan mass on every prior OWN action, not opponent/chance reach.
     */
    static Map<String, Map<String, Double>> behavioral(
            List<InformationSet> infos, List<Map<String, Integer>> plans, List<Double> mixture) {
        if (plans.size() != mixture.size())
            throw new IllegalArgumentException("Plan mixture cardinality differs");
        var byKey = new HashMap<String, InformationSet>();
        infos.forEach(i -> byKey.put(i.key(), i));
        var rows = new LinkedHashMap<String, Map<String, Double>>();
        for (var info : infos) {
            double mass = 0;
            double[] weights = new double[info.actions().size()];
            for (int p = 0; p < plans.size(); p++) {
                var plan = plans.get(p);
                boolean compatible = true;
                for (var prior : info.ownHistory()) {
                    var previous = byKey.get(prior.informationSetKey());
                    if (previous == null
                            || !previous.actions()
                                    .get(plan.get(previous.key()))
                                    .equals(prior.action())) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    mass += mixture.get(p);
                    weights[plan.get(info.key())] += mixture.get(p);
                }
            }
            var row = new LinkedHashMap<String, Double>();
            for (int a = 0; a < weights.length; a++)
                row.put(
                        info.actions().get(a),
                        mass == 0 ? 1.0 / weights.length : weights[a] / mass);
            rows.put(info.key(), Map.copyOf(row));
        }
        return Map.copyOf(rows);
    }
}
