package com.pokerlab.solver;

import java.util.*;

/**
 * Bounded perfect-recall sequence form for two active constant-sum decision makers, using the owned
 * signed-RHS LP solver. The checked immutable tree is also used for independent best responses.
 * This is not a multiplayer equilibrium or trainer-admission certificate.
 */
public final class FiniteTwoPlayerSequenceForm {
    public static final String ALGORITHM = "BOUNDED_PERFECT_RECALL_SEQUENCE_FORM_OWNED_LP/v1";
    public static final int MAX_TREE_NODES = 20_000;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_INFORMATION_SETS_PER_ACTOR = 64;
    public static final int MAX_SEQUENCES_PER_ACTOR = 129;
    public static final int MAX_ACTIONS = 16;
    public static final int MAX_LABEL_LENGTH = 1_024;
    public static final int MAX_LABEL_CHARACTERS = 2_000_000;
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record OwnAction(String key, String action) {}

    public record InformationSet(
            String key, int actor, int depth, List<String> actions, List<OwnAction> ownHistory) {
        public InformationSet {
            actions = List.copyOf(actions);
            ownHistory = List.copyOf(ownHistory);
        }
    }

    /** Index zero is the empty sequence, represented by empty key/action labels. */
    public record Sequence(int index, String key, String action) {}

    public record Conservation(String key, int parentSequence, List<Integer> childSequences) {
        public Conservation {
            childSequences = List.copyOf(childSequences);
        }
    }

    public record FlowAudit(
            int actor,
            List<Sequence> sequences,
            List<Conservation> conservation,
            List<Double> realization,
            double maximumResidual) {
        public FlowAudit {
            sequences = List.copyOf(sequences);
            conservation = List.copyOf(conservation);
            realization = List.copyOf(realization);
        }
    }

    public record Audit(
            String algorithm,
            String snapshotHash,
            String reductionHash,
            int treeNodes,
            int firstActor,
            int secondActor,
            double constantSum,
            List<InformationSet> informationSets,
            FlowAudit firstFlow,
            FlowAudit secondFlow,
            BoundedLinearProgram.Certificate firstLp,
            BoundedLinearProgram.Certificate secondLp,
            double lowerValue,
            double upperValue,
            String behavioralPolicyHash,
            MultiPlayerInformationSetBestResponse.Report behavioralQuality) {
        public Audit {
            informationSets = List.copyOf(informationSets);
        }
    }

    /** A JSON audit or caller-supplied realization vector cannot manufacture a solved handle. */
    public static final class Result {
        private final Map<String, Map<String, Double>> strategy;
        private final Audit audit;

        private Result(CfrSolution policy, Audit audit) {
            this.strategy = policy.strategy();
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
            List<String> actions,
            List<Double> probabilities,
            List<Node> children,
            List<Double> utilities) {}

    /** Does not call the input game again after snapshot validation. */
    private record Snapshot(int playerCount, Node initialState)
            implements MultiPlayerCfrGame<Node> {
        public boolean isTerminal(Node node) {
            return node.actor() == -2;
        }

        public double[] terminalUtilities(Node node) {
            return node.utilities().stream().mapToDouble(Double::doubleValue).toArray();
        }

        public int currentPlayer(Node node) {
            return node.actor();
        }

        public List<String> legalActions(Node node) {
            return node.actions();
        }

        public String informationSet(Node node) {
            return node.key().substring(node.key().indexOf(':') + 1);
        }

        public Node afterAction(Node node, String action) {
            int index = node.actions().indexOf(action);
            if (index < 0) throw new IllegalArgumentException("Foreign snapshot action");
            return node.children().get(index);
        }

        public List<ChanceOutcome<Node>> chanceOutcomes(Node node) {
            var result = new ArrayList<ChanceOutcome<Node>>();
            for (int i = 0; i < node.children().size(); i++)
                result.add(
                        new ChanceOutcome<>(node.children().get(i), node.probabilities().get(i)));
            return List.copyOf(result);
        }
    }

    private static final class Builder<S> {
        final MultiPlayerCfrGame<S> game;
        final int players;
        final TreeMap<String, InformationSet> infos = new TreeMap<>();
        final TreeSet<Integer> actors = new TreeSet<>();
        final int[] infoCounts;
        int nodes, characters;

        Builder(MultiPlayerCfrGame<S> game, int players) {
            this.game = game;
            this.players = players;
            this.infoCounts = new int[players];
        }

        String label(String text) {
            if (text == null || text.isBlank() || text.length() > MAX_LABEL_LENGTH)
                throw new IllegalArgumentException(
                        "Bounded nonblank sequence-form labels required");
            characters += text.length();
            if (characters > MAX_LABEL_CHARACTERS)
                throw new IllegalArgumentException("Sequence-form label budget exceeded");
            return text;
        }

        Node collect(S state, int depth, List<List<OwnAction>> histories) {
            if (++nodes > MAX_TREE_NODES || depth > MAX_DEPTH)
                throw new IllegalArgumentException("Sequence-form tree/depth budget exceeded");
            if (game.isTerminal(state)) {
                var raw = game.terminalUtilities(state);
                if (raw == null || raw.length != players)
                    throw new IllegalArgumentException("Terminal utility cardinality differs");
                var utilities = new ArrayList<Double>();
                for (double value : raw.clone()) {
                    if (!Double.isFinite(value)
                            || Math.abs(value) > BoundedLinearProgram.MAX_COEFFICIENT)
                        throw new IllegalArgumentException(
                                "Finite bounded terminal utilities required");
                    utilities.add(value);
                }
                return new Node(-2, "", List.of(), List.of(), List.of(), List.copyOf(utilities));
            }
            int actor = game.currentPlayer(state);
            var children = new ArrayList<Node>();
            if (actor == -1) {
                var raw = game.chanceOutcomes(state);
                if (raw == null || raw.isEmpty() || raw.size() > MAX_TREE_NODES - nodes)
                    throw new IllegalArgumentException("Bounded positive chance support required");
                var outcomes = List.copyOf(raw);
                var probabilities = new ArrayList<Double>();
                double sum = 0;
                for (var outcome : outcomes) {
                    double p = outcome.probability();
                    if (!Double.isFinite(p) || p <= 0 || p > 1)
                        throw new IllegalArgumentException(
                                "Positive finite chance weights required");
                    probabilities.add(p);
                    sum += p;
                }
                if (Math.abs(sum - 1) > 1e-12)
                    throw new IllegalArgumentException("Chance probabilities must sum to one");
                for (var outcome : outcomes)
                    children.add(collect(outcome.state(), depth + 1, histories));
                return new Node(
                        -1,
                        "",
                        List.of(),
                        List.copyOf(probabilities),
                        List.copyOf(children),
                        List.of());
            }
            if (actor < 0 || actor >= players)
                throw new IllegalArgumentException("Invalid sequence-form actor");
            actors.add(actor);
            if (actors.size() > 2)
                throw new IllegalArgumentException("Exactly two decision makers required");
            String key = actor + ":" + label(game.informationSet(state));
            var raw = game.legalActions(state);
            if (raw == null || raw.isEmpty() || raw.size() > MAX_ACTIONS)
                throw new IllegalArgumentException("Bounded legal actions required");
            var actions = List.copyOf(raw);
            for (String action : actions) label(action);
            if (new HashSet<>(actions).size() != actions.size())
                throw new IllegalArgumentException("Distinct legal actions required");
            var own = histories.get(actor);
            if (own.stream().anyMatch(a -> a.key().equals(key)))
                throw new IllegalArgumentException("Repeated information set on an own path");
            var row = new InformationSet(key, actor, depth, actions, own);
            var old = infos.putIfAbsent(key, row);
            if (old != null && !old.equals(row))
                throw new IllegalArgumentException(
                        "Perfect recall, one depth and consistent actions required");
            if (old == null && ++infoCounts[actor] > MAX_INFORMATION_SETS_PER_ACTOR)
                throw new IllegalArgumentException("Sequence-form information-set budget exceeded");
            for (String action : actions) {
                var path = new ArrayList<>(own);
                path.add(new OwnAction(key, action));
                var next = new ArrayList<>(histories);
                next.set(actor, List.copyOf(path));
                children.add(collect(game.afterAction(state, action), depth + 1, next));
            }
            return new Node(actor, key, actions, List.of(), List.copyOf(children), List.of());
        }
    }

    private record Flow(
            int actor,
            List<InformationSet> infos,
            Map<OwnAction, Integer> indices,
            List<Sequence> sequences,
            List<Conservation> conservation,
            double[][] matrix,
            double[] rhs) {}

    private static Flow flow(int actor, List<InformationSet> all) {
        var infos = all.stream().filter(i -> i.actor() == actor).toList();
        var indices = new HashMap<OwnAction, Integer>();
        var sequences = new ArrayList<Sequence>();
        sequences.add(new Sequence(0, "", ""));
        for (var info : infos)
            for (String action : info.actions()) {
                int index = sequences.size();
                if (index >= MAX_SEQUENCES_PER_ACTOR)
                    throw new IllegalArgumentException(
                            "Sequence-form own-sequence budget exceeded");
                indices.put(new OwnAction(info.key(), action), index);
                sequences.add(new Sequence(index, info.key(), action));
            }
        var matrix = new double[infos.size() + 1][sequences.size()];
        var rhs = new double[matrix.length];
        matrix[0][0] = 1;
        rhs[0] = 1;
        var conservation = new ArrayList<Conservation>();
        int row = 1;
        for (var info : infos) {
            int parent = info.ownHistory().isEmpty() ? 0 : indices.get(info.ownHistory().getLast());
            var children =
                    info.actions().stream()
                            .map(a -> indices.get(new OwnAction(info.key(), a)))
                            .toList();
            matrix[row][parent] = -1;
            for (int child : children) matrix[row][child] = 1;
            conservation.add(new Conservation(info.key(), parent, children));
            row++;
        }
        return new Flow(
                actor,
                infos,
                Map.copyOf(indices),
                List.copyOf(sequences),
                List.copyOf(conservation),
                matrix,
                rhs);
    }

    private static int last(Flow flow, OwnAction action) {
        return action == null ? 0 : flow.indices().get(action);
    }

    private static void accumulate(
            Node node,
            double chance,
            OwnAction first,
            OwnAction second,
            Flow f,
            Flow s,
            double[][] payoff,
            double[] reference) {
        if (!Double.isFinite(chance) || chance <= 0)
            throw new IllegalArgumentException("Sequence-form joint chance underflow");
        if (node.actor() == -2) {
            double constant = node.utilities().get(f.actor()) + node.utilities().get(s.actor());
            if (Double.isNaN(reference[0])) {
                reference[0] = constant;
                for (int p = 0; p < node.utilities().size(); p++)
                    reference[p + 1] = node.utilities().get(p);
            }
            if (Math.abs(constant - reference[0]) > 1e-12)
                throw new IllegalArgumentException(
                        "Terminal active utilities must be constant-sum");
            for (int p = 0; p < node.utilities().size(); p++)
                if (p != f.actor()
                        && p != s.actor()
                        && Math.abs(node.utilities().get(p) - reference[p + 1]) > 1e-12)
                    throw new IllegalArgumentException(
                            "Inactive seat payoff depends on the outcome");
            payoff[last(f, first)][last(s, second)] += chance * node.utilities().get(f.actor());
        } else
            for (int i = 0; i < node.children().size(); i++) {
                if (node.actor() == -1)
                    accumulate(
                            node.children().get(i),
                            chance * node.probabilities().get(i),
                            first,
                            second,
                            f,
                            s,
                            payoff,
                            reference);
                else {
                    var action = new OwnAction(node.key(), node.actions().get(i));
                    accumulate(
                            node.children().get(i),
                            chance,
                            node.actor() == f.actor() ? action : first,
                            node.actor() == s.actor() ? action : second,
                            f,
                            s,
                            payoff,
                            reference);
                }
            }
    }

    private static BoundedLinearProgram.Result optimize(
            double[][] payoff, Flow own, Flow other, boolean row) throws Exception {
        int primal = own.sequences().size(), free = other.rhs().length;
        int variables = primal + 2 * free, inequalities = other.sequences().size();
        var matrix = new double[inequalities + 2 * own.rhs().length][variables];
        var rhs = new double[matrix.length];
        var cost = new double[variables];
        for (int j = 0; j < free; j++) {
            cost[primal + j] = (row ? 1 : -1) * other.rhs()[j];
            cost[primal + free + j] = -cost[primal + j];
        }
        for (int i = 0; i < inequalities; i++) {
            for (int j = 0; j < primal; j++) matrix[i][j] = row ? -payoff[j][i] : payoff[i][j];
            for (int j = 0; j < free; j++) {
                matrix[i][primal + j] = (row ? 1 : -1) * other.matrix()[j][i];
                matrix[i][primal + free + j] = -matrix[i][primal + j];
            }
        }
        // Keep all positive flow equalities followed by all negative ones in canonical order.
        for (int i = 0; i < own.rhs().length; i++) {
            System.arraycopy(own.matrix()[i], 0, matrix[inequalities + i], 0, primal);
            for (int j = 0; j < primal; j++)
                matrix[inequalities + own.rhs().length + i][j] = -own.matrix()[i][j];
            rhs[inequalities + i] = own.rhs()[i];
            rhs[inequalities + own.rhs().length + i] = -own.rhs()[i];
        }
        return BoundedLinearProgram.solve(matrix, rhs, cost);
    }

    private static FlowAudit behavior(
            Flow flow,
            BoundedLinearProgram.Result solved,
            Map<String, Map<String, Double>> strategy) {
        var point = List.copyOf(solved.point().subList(0, flow.sequences().size()));
        double residual = Math.abs(point.getFirst() - 1);
        for (double p : point)
            if (!Double.isFinite(p) || p < 0 || p > 1 + TOLERANCE)
                throw new IllegalStateException("Invalid own realization mass");
        for (int i = 0; i < flow.infos().size(); i++) {
            var info = flow.infos().get(i);
            var conservation = flow.conservation().get(i);
            double mass = point.get(conservation.parentSequence());
            double children = 0, sum = 0;
            var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < info.actions().size(); j++) {
                double child = point.get(conservation.childSequences().get(j));
                children += child;
                double probability = mass > 1e-12 ? child / mass : 1.0 / info.actions().size();
                if (!Double.isFinite(probability)
                        || probability < -TOLERANCE
                        || probability > 1 + TOLERANCE)
                    throw new IllegalStateException("Invalid behavioral ratio");
                double bounded = Math.max(0, Math.min(1, probability));
                sum += bounded;
                row.put(info.actions().get(j), bounded);
            }
            residual = Math.max(residual, Math.abs(children - mass));
            if (Math.abs(sum - 1) > TOLERANCE)
                throw new IllegalStateException("Behavioral normalization failed");
            final double normalization = sum;
            row.replaceAll((a, p) -> p / normalization);
            strategy.put(info.key(), Map.copyOf(row));
        }
        if (residual > TOLERANCE)
            throw new IllegalStateException("Original own-sequence flow failed");
        return new FlowAudit(flow.actor(), flow.sequences(), flow.conservation(), point, residual);
    }

    private FiniteTwoPlayerSequenceForm() {}

    public static <S> Result solve(MultiPlayerCfrGame<S> game) throws Exception {
        Objects.requireNonNull(game, "game");
        int players = game.playerCount();
        if (players < 2 || players > 6)
            throw new IllegalArgumentException("Two to six seats required");
        var histories = new ArrayList<List<OwnAction>>();
        for (int p = 0; p < players; p++) histories.add(List.of());
        var builder = new Builder<>(game, players);
        var snapshot = new Snapshot(players, builder.collect(game.initialState(), 0, histories));
        if (builder.actors.size() != 2)
            throw new IllegalArgumentException("Exactly two decision makers required");
        int first = builder.actors.first(), second = builder.actors.last();
        var infos = List.copyOf(builder.infos.values());
        var f = flow(first, infos);
        var s = flow(second, infos);
        var payoff = new double[f.sequences().size()][s.sequences().size()];
        var reference = new double[players + 1];
        reference[0] = Double.NaN;
        accumulate(snapshot.initialState(), 1, null, null, f, s, payoff, reference);
        var row = optimize(payoff, f, s, true);
        var column = optimize(payoff, s, f, false);
        double lower = row.certificate().primalValue(), upper = -column.certificate().primalValue();
        if (Math.abs(upper - lower) > TOLERANCE)
            throw new IllegalStateException("Independent sequence-form LP bounds disagree");
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        var firstFlow = behavior(f, row, strategy);
        var secondFlow = behavior(s, column, strategy);
        // Existing container sentinel, not a training iteration or resumable CFR checkpoint.
        var policy = new CfrSolution(1, strategy);
        var quality = MultiPlayerInformationSetBestResponse.assess(snapshot, policy);
        if (!Double.isFinite(quality.nashConvBb())
                || quality.nashConvBb() > TOLERANCE
                || Math.abs(quality.profileUtilitiesBb().get(first) - lower) > TOLERANCE
                || Math.abs(quality.profileUtilitiesBb().get(second) - (reference[0] - upper))
                        > TOLERANCE
                || Math.abs(quality.bestResponseUtilitiesBb().get(first) - upper) > TOLERANCE
                || Math.abs(reference[0] - quality.bestResponseUtilitiesBb().get(second) - lower)
                        > TOLERANCE)
            throw new IllegalStateException(
                    "Independent snapshot behavioral best-response certificate failed");
        String reductionHash =
                SixMaxHistoryPhysicalConditionalRefinement.hash(
                        Map.of(
                                "firstActor",
                                first,
                                "secondActor",
                                second,
                                "informationSets",
                                infos,
                                "firstSequences",
                                f.sequences(),
                                "secondSequences",
                                s.sequences(),
                                "firstConservation",
                                f.conservation(),
                                "secondConservation",
                                s.conservation(),
                                "payoff",
                                payoff));
        var audit =
                new Audit(
                        ALGORITHM,
                        SixMaxHistoryPhysicalConditionalRefinement.hash(snapshot),
                        reductionHash,
                        builder.nodes,
                        first,
                        second,
                        reference[0],
                        infos,
                        firstFlow,
                        secondFlow,
                        row.certificate(),
                        column.certificate(),
                        lower,
                        upper,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        quality);
        return new Result(policy, audit);
    }
}
