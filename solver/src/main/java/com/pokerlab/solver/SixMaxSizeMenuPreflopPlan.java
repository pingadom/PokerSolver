package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Payoff-free inventory of the interval compiler's exact Cartesian continuation-plan demand. */
public final class SixMaxSizeMenuPreflopPlan {
    public static final String SCHEMA = "pokerlab-size-menu-preflop-plan/v1";
    public static final String STATUS = "PREFLOP_CONTINUATION_PLAN_PREFLIGHT_ONLY";
    public static final int PLAN_LIMIT = FiniteTwoPlayerRootActionIntervals.MAX_CONTINUATION_PLANS;
    public static final String CHANCE_SCOPE = "ORIGINAL_JOINT_CHANCE_ONLY_NOT_QUESTION_REACH/v1";
    private static final List<Seat> FLOP_ORDER =
            List.of(Seat.SB, Seat.BB, Seat.UTG, Seat.HJ, Seat.CO, Seat.BTN);

    public record FutureDecision(String informationSet, List<String> actions) {
        public FutureDecision {
            actions = List.copyOf(actions);
        }
    }

    public record Action(
            String action,
            List<FutureDecision> futureHeroDecisions,
            BigInteger cartesianPlans,
            BigInteger enumerationPrechargeUnits,
            boolean withinPlanLimit,
            String status) {
        public Action {
            futureHeroDecisions = List.copyOf(futureHeroDecisions);
            var count = BigInteger.ONE;
            String previous = "";
            for (var row : futureHeroDecisions) {
                if (row.informationSet().compareTo(previous) <= 0
                        || row.actions().isEmpty()
                        || row.actions().stream().distinct().count() != row.actions().size())
                    throw new IllegalArgumentException(
                            "Distinct ordered continuation rows required");
                previous = row.informationSet();
                count = count.multiply(BigInteger.valueOf(row.actions().size()));
            }
            boolean fits = count.compareTo(BigInteger.valueOf(PLAN_LIMIT)) <= 0;
            if (!count.equals(cartesianPlans)
                    || fits != withinPlanLimit
                    || !status.equals(fits ? "WITHIN_PLAN_LIMIT_ONLY" : "CONTINUATION_PLAN_LIMIT"))
                throw new IllegalArgumentException("Incorrect Cartesian plan classification");
        }
    }

    public record Table(
            Seat actingSeat,
            String publicHistory,
            List<SixMaxPreflopBetting.Move> history,
            double potBb,
            double toCallBb,
            List<SixMaxSuppliedFlopBettingGame.PublicPlayer> players,
            List<SixMaxSuppliedFlopSizeMenuGame.PublicAction> actions) {
        public Table {
            history = List.copyOf(history);
            players = List.copyOf(players);
            actions = List.copyOf(actions);
        }
    }

    public record Question(
            int index,
            String informationSet,
            Seat hero,
            String heroCards,
            List<Integer> originalWorldIndices,
            double priorChanceMass,
            Table context,
            List<Action> actions,
            boolean allMovesWithinPlanLimit) {
        public Question {
            originalWorldIndices = List.copyOf(originalWorldIndices);
            actions = List.copyOf(actions);
            if (index < 0
                    || originalWorldIndices.isEmpty()
                    || !Double.isFinite(priorChanceMass)
                    || priorChanceMass <= 0
                    || priorChanceMass > 1
                    || !actions.stream()
                            .map(Action::action)
                            .toList()
                            .equals(context.actions().stream().map(a -> a.action()).toList())
                    || allMovesWithinPlanLimit
                            != actions.stream().allMatch(Action::withinPlanLimit))
                throw new IllegalArgumentException("Invalid complete preflop question");
        }
    }

    public record Work(
            int symbolicTreeStates,
            int preflopInformationSets,
            int preflopActions,
            int withinPlanLimitActions,
            int overPlanLimitActions,
            int enumeratedBoards,
            int handEvaluations,
            int lpSolves,
            int cfrIterations) {
        public Work {
            if (symbolicTreeStates < 1
                    || preflopInformationSets < 1
                    || preflopActions < 1
                    || withinPlanLimitActions + overPlanLimitActions != preflopActions
                    || enumeratedBoards != 0
                    || handEvaluations != 0
                    || lpSolves != 0
                    || cfrIterations != 0)
                throw new IllegalArgumentException(
                        "Structural planning must perform zero numerical work");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String sourceRequestHash,
            String chanceMassScope,
            SixMaxSuppliedFlopSizeMenuStudy.Request request,
            SixMaxSuppliedFlopSizeMenuGame.Budget estimatedPhysicalBudget,
            int continuationPlanLimit,
            List<Question> questions,
            Work work) {
        public Report {
            questions = List.copyOf(questions);
            if (!SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !CHANCE_SCOPE.equals(chanceMassScope)
                    || !hash(request).equals(sourceRequestHash)
                    || continuationPlanLimit != PLAN_LIMIT
                    || !SixMaxSuppliedFlopSizeMenuGame.preflight(
                                    request.input(), request.flop(), request.menu())
                            .equals(estimatedPhysicalBudget)
                    || questions.size() != work.preflopInformationSets()
                    || work.symbolicTreeStates() != estimatedPhysicalBudget.completeTreeStates())
                throw new IllegalArgumentException("Invalid structural plan identity");
            String previous = "";
            int total = 0, within = 0;
            for (int i = 0; i < questions.size(); i++) {
                var q = questions.get(i);
                if (q.index() != i || q.informationSet().compareTo(previous) <= 0)
                    throw new IllegalArgumentException("Complete ordered questions required");
                previous = q.informationSet();
                total += q.actions().size();
                within += (int) q.actions().stream().filter(Action::withinPlanLimit).count();
                for (var a : q.actions()) {
                    var expected =
                            a.cartesianPlans()
                                    .multiply(BigInteger.valueOf(work.symbolicTreeStates()));
                    if (!a.enumerationPrechargeUnits().equals(expected))
                        throw new IllegalArgumentException("Enumeration work differs");
                }
            }
            if (total != work.preflopActions() || within != work.withinPlanLimitActions())
                throw new IllegalArgumentException("Incomplete action inventory");
        }
    }

    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }

        /** This passes one structural gate only; it supplies neither EVs nor qualification. */
        public Question requireCompleteMenuWithinPlanLimit(int questionIndex) {
            if (questionIndex < 0 || questionIndex >= report.questions().size())
                throw new IllegalArgumentException("Unknown structural question");
            var question = report.questions().get(questionIndex);
            if (!question.allMovesWithinPlanLimit())
                throw new IllegalArgumentException(
                        "CONTINUATION_PLAN_LIMIT: complete preflop menu exceeds 64 plans");
            return question;
        }
    }

    private record Node(int actor, String key, Map<String, Node> children) {}

    private record Support(int world, double probability, Node node, Table context, String hand) {}

    private final SixMaxSuppliedFlopSizeMenuStudy.Request request;
    private final SixMaxPreflopBetting rules;
    private final Map<String, List<Support>> questions = new TreeMap<>();
    private final Map<String, List<String>> allInfos = new TreeMap<>();
    private int nodes = 1;

    private SixMaxSizeMenuPreflopPlan(SixMaxSuppliedFlopSizeMenuStudy.Request request) {
        this.request = request;
        rules = new SixMaxPreflopBetting(request.input().specification().rules());
    }

    private static String hash(Object value) {
        try {
            return SixMaxHeadsUpPreflopGame.hash(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unhashable plan source", e);
        }
    }

    private Node leaf() {
        nodes++;
        return new Node(-2, "", Map.of());
    }

    private Node decision(int actor, String key, LinkedHashMap<String, Node> children) {
        var actions = List.copyOf(children.keySet());
        var previous = allInfos.putIfAbsent(key, actions);
        if (previous != null && !previous.equals(actions))
            throw new IllegalArgumentException("Hidden-world action menu differs");
        return new Node(actor, key, Collections.unmodifiableMap(new LinkedHashMap<>(children)));
    }

    private boolean compatible(int world) {
        return request.input().worlds().get(world).dealtCombos().stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .noneMatch(request.flop()::contains);
    }

    private Node preflop(
            int world, double probability, SixMaxPreflopBetting.State state, String history) {
        if (state.status() == SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED
                && compatible(world)) {
            nodes++; // Original boundary becomes physical-flop/residual chance, not a player move.
            var children = new LinkedHashMap<String, Node>();
            children.put("physical-flop", flop(world, state, history, ""));
            children.put("residual", leaf());
            return new Node(-1, "", Collections.unmodifiableMap(children));
        }
        if (state.status() != SixMaxPreflopBetting.Status.DECISION) return leaf();
        nodes++;
        int actor = state.actingSeat().ordinal();
        String hand = request.input().worlds().get(world).dealtCombos().get(actor);
        String key =
                actor
                        + ":"
                        + SixMaxSuppliedFlopSizeMenuGame.PREFLOP_KEY
                        + "supplied-conditional-preflop:"
                        + hand
                        + ":"
                        + history;
        var children = new LinkedHashMap<String, Node>();
        for (var move : state.legalActions()) {
            String action = SixMaxHeadsUpPreflopGame.encode(move);
            children.put(
                    action,
                    preflop(
                            world,
                            probability,
                            rules.apply(state, move),
                            history + "|" + state.actingSeat() + ":" + action));
        }
        var node = decision(actor, key, children);
        questions
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(new Support(world, probability, node, table(state, history), hand));
        return node;
    }

    private Node flop(int world, SixMaxPreflopBetting.State boundary, String history, String path) {
        if (path.equals("check|check") || path.endsWith("|fold") || path.endsWith("|call"))
            return leaf();
        nodes++;
        var order = FLOP_ORDER.stream().filter(boundary.liveSeats()::contains).toList();
        // Actor alternates at each public flop action, independent of private deals.
        int depth = path.isEmpty() ? 0 : path.split("\\|").length;
        int actor = order.get(depth % 2).ordinal();
        String hand = request.input().worlds().get(world).dealtCombos().get(actor);
        String key =
                actor
                        + ":"
                        + SixMaxSuppliedFlopSizeMenuGame.FLOP_KEY
                        + hand
                        + ":"
                        + history
                        + ":"
                        + String.join(" ", request.flop())
                        + ":"
                        + path;
        List<String> actions =
                path.contains("bet:")
                        ? List.of("fold", "call")
                        : java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of("check"),
                                        (path.isEmpty()
                                                        ? request.menu().firstToAct()
                                                        : request.menu().afterCheck())
                                                .stream().map(x -> "bet:" + x))
                                .toList();
        var children = new LinkedHashMap<String, Node>();
        for (String a : actions)
            children.put(a, flop(world, boundary, history, path.isEmpty() ? a : path + "|" + a));
        return decision(actor, key, children);
    }

    private static Table table(SixMaxPreflopBetting.State s, String history) {
        var players =
                Arrays.stream(Seat.values())
                        .map(
                                seat ->
                                        new SixMaxSuppliedFlopBettingGame.PublicPlayer(
                                                seat,
                                                s.isFolded(seat)
                                                        ? "FOLDED"
                                                        : seat == s.actingSeat()
                                                                ? "ACTING"
                                                                : "ACTIVE",
                                                s.committedBb(seat),
                                                s.remainingStackBb(seat)))
                        .toList();
        var actions =
                s.legalActions().stream()
                        .map(
                                a ->
                                        new SixMaxSuppliedFlopSizeMenuGame.PublicAction(
                                                SixMaxHeadsUpPreflopGame.encode(a),
                                                a.kind() == SixMaxPreflopBetting.Kind.FOLD
                                                        ? 0
                                                        : a.amountBb()
                                                                - s.committedBb(s.actingSeat())))
                        .toList();
        return new Table(
                s.actingSeat(), history, s.history(), s.potBb(), s.toCallBb(), players, actions);
    }

    private static void future(Node node, int hero, Map<String, List<String>> rows) {
        if (node.actor() == hero) {
            var actions = List.copyOf(node.children().keySet());
            var old = rows.putIfAbsent(node.key(), actions);
            if (old != null && !old.equals(actions))
                throw new IllegalArgumentException("Continuation recall/menu differs");
        }
        node.children().values().forEach(n -> future(n, hero, rows));
    }

    public static Result plan(SixMaxSuppliedFlopSizeMenuStudy.Request request) {
        Objects.requireNonNull(request);
        var budget =
                SixMaxSuppliedFlopSizeMenuGame.preflight(
                        request.input(), request.flop(), request.menu());
        var planner = new SixMaxSizeMenuPreflopPlan(request);
        var root = planner.rules.initialState();
        for (var a : request.input().specification().history()) {
            var move =
                    root.legalActions().stream()
                            .filter(m -> SixMaxHeadsUpPreflopGame.encode(m).equals(a.action()))
                            .findFirst()
                            .orElseThrow();
            root = planner.rules.apply(root, move);
        }
        double max =
                request.input().worlds().stream().mapToDouble(w -> w.weight()).max().orElseThrow();
        double sum = request.input().worlds().stream().mapToDouble(w -> w.weight() / max).sum();
        for (int i = 0; i < request.input().worlds().size(); i++)
            planner.preflop(i, (request.input().worlds().get(i).weight() / max) / sum, root, "");
        if (planner.nodes != budget.completeTreeStates()
                || planner.allInfos.size()
                        != budget.informationSets().values().stream()
                                .mapToInt(Integer::intValue)
                                .sum())
            throw new IllegalStateException(
                    "Complete symbolic topology differs from owned preflight");
        var result = new ArrayList<Question>();
        int total = 0, within = 0;
        for (var entry : planner.questions.entrySet()) {
            var supports = entry.getValue();
            var first = supports.getFirst();
            int hero = first.node().actor();
            var actions = new ArrayList<Action>();
            for (String a : first.node().children().keySet()) {
                var rows = new TreeMap<String, List<String>>();
                for (var s : supports) future(s.node().children().get(a), hero, rows);
                var decisions =
                        rows.entrySet().stream()
                                .map(e -> new FutureDecision(e.getKey(), e.getValue()))
                                .toList();
                var count =
                        decisions.stream()
                                .map(r -> BigInteger.valueOf(r.actions().size()))
                                .reduce(BigInteger.ONE, BigInteger::multiply);
                boolean fits = count.compareTo(BigInteger.valueOf(PLAN_LIMIT)) <= 0;
                actions.add(
                        new Action(
                                a,
                                decisions,
                                count,
                                count.multiply(BigInteger.valueOf(planner.nodes)),
                                fits,
                                fits ? "WITHIN_PLAN_LIMIT_ONLY" : "CONTINUATION_PLAN_LIMIT"));
                total++;
                if (fits) within++;
            }
            if (supports.stream()
                    .anyMatch(
                            s ->
                                    !s.context().equals(first.context())
                                            || !s.hand().equals(first.hand())))
                throw new IllegalStateException("Hidden-world public context differs");
            result.add(
                    new Question(
                            result.size(),
                            entry.getKey(),
                            Seat.values()[hero],
                            first.hand(),
                            supports.stream().map(Support::world).toList(),
                            supports.stream().mapToDouble(Support::probability).sum(),
                            first.context(),
                            actions,
                            actions.stream().allMatch(Action::withinPlanLimit)));
        }
        return new Result(
                new Report(
                        SCHEMA,
                        STATUS,
                        false,
                        hash(request),
                        CHANCE_SCOPE,
                        request,
                        budget,
                        PLAN_LIMIT,
                        result,
                        new Work(
                                planner.nodes,
                                result.size(),
                                total,
                                within,
                                total - within,
                                0,
                                0,
                                0,
                                0)));
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes =
                SixMaxTexturePayoffTable.mapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(result.report());
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Plan byte cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Stored plan byte cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        report, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                                Report.class);
        var owned = plan(SixMaxSuppliedFlopSizeMenuStudy.readRequest(request));
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete preflop structural replay differs");
        return owned;
    }
}
