package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fresh-reference qualification of explicitly selected refined conditional action intervals. */
public final class SixMaxRefinedFlopFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-refined-flop-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-refined-flop-feedback-report/v1";
    public static final String STATUS = "REFINED_PHYSICAL_FLOP_FEEDBACK_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxRefinedConditionalStudy.MAX_BYTES;

    public record ReferenceBudget(String algorithm, List<Integer> iterations, int totalIterations) {
        public ReferenceBudget {
            if (!SixMaxOwnedFlopReferences.ALGORITHM.equals(algorithm)
                    || !List.of(80000, 160000).equals(iterations)
                    || totalIterations != 240000)
                throw new IllegalArgumentException("Unsupported owned reference budget");
            iterations = List.copyOf(iterations);
        }

        public static ReferenceBudget fresh() {
            return new ReferenceBudget(
                    SixMaxOwnedFlopReferences.ALGORITHM, List.of(80000, 160000), 240000);
        }
    }

    public record Request(
            String schemaVersion,
            SixMaxRefinedConditionalStudy.Request conditional,
            ReferenceBudget referenceBudget,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || conditional == null
                    || referenceBudget == null)
                throw new IllegalArgumentException("Invalid refined feedback request");
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Reference(CfrSolution policy, Check check, SixMaxOwnedFlopReferences.Audit work) {
        public Reference {
            if (policy == null
                    || check == null
                    || work == null
                    || policy.iterations() != check.iterations()
                    || work.prunedSuffixRoots() != 4L * policy.iterations())
                throw new IllegalArgumentException("Invalid fresh owned reference");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxRefinedConditionalStudy.Report conditional,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Reference> references,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || conditional == null
                    || !request.conditional().equals(conditional.request())
                    || primary == null
                    || primary.iterations() != conditional.parent().candidate().iterations()
                    || !moves.stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(conditional.context().legalActions())
                    || !references.stream()
                            .map(r -> r.policy().iterations())
                            .toList()
                            .equals(request.referenceBudget().iterations())
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid refined feedback report");
        }
    }

    public static final class QualifiedFeedback {
        private final String reportHash;

        private QualifiedFeedback(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
        }

        public String reportHash() {
            return reportHash;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedFeedback qualified;

        private Result(Report report) throws Exception {
            this.report = report;
            qualified = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(qualified);
        }
    }

    record OwnedQuestion(
            SixMaxSuppliedFlopHandoff.Audit handoff,
            List<State> states,
            List<SixMaxSuppliedFlopHandoff.World> worlds,
            String informationSet,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context) {}

    private record AssessmentQuestion(
            int hero, String heroCards, String publicPreflopHistory, List<String> flopActions) {}

    private SixMaxRefinedFlopFeedback() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.conditional().parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var owned = SixMaxRefinedConditionalStudy.solve(request.conditional(), game);
        var conditional = owned.report();
        var moves =
                conditional.actions().stream()
                        .map(
                                a ->
                                        new SixMaxSuppliedIntervalFeedback.Move(
                                                a.action(),
                                                a.lowerUtilityBb(),
                                                a.upperUtilityBb(),
                                                a.lowerLossBb(),
                                                a.upperLossBb(),
                                                SixMaxSuppliedIntervalFeedback.classify(
                                                        a.lowerLossBb(),
                                                        a.upperLossBb(),
                                                        request.maximumLossBb())))
                        .toList();
        var failures = new TreeSet<String>();
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            failures.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            failures.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        var q = request.conditional();
        var selection =
                new AssessmentQuestion(
                        q.hero(), q.heroCards(), q.publicPreflopHistory(), q.flopActions());
        var primary =
                assess(game, selection, conditional.actions(), conditional.parent().candidate());
        primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        var runner = new SixMaxOwnedFlopReferences(game);
        var references = new ArrayList<Reference>();
        for (int iterations : request.referenceBudget().iterations()) {
            var run = runner.train(iterations);
            var check = assess(game, selection, conditional.actions(), run.policy());
            references.add(new Reference(run.policy(), check, run.audit()));
            check.failures().forEach(f -> failures.add(iterations + ":" + f));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        conditional,
                        moves,
                        primary,
                        references,
                        failures.isEmpty(),
                        List.copyOf(failures)));
    }

    private static OwnedQuestion question(
            SixMaxSuppliedFlopBettingGame game, AssessmentQuestion request) {
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(request.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown owned handoff"));
        var states = new ArrayList<State>();
        var worlds = new ArrayList<SixMaxSuppliedFlopHandoff.World>();
        String key = null;
        SixMaxSuppliedFlopBettingGame.PublicFlopState context = null;
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(request.hero()).equals(request.heroCards())) continue;
            var state =
                    new State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            if (game.currentPlayer(state) != 5)
                throw new IllegalArgumentException("BB must act first at selected flop");
            for (String action : request.flopActions()) state = game.afterAction(state, action);
            if (game.currentPlayer(state) != request.hero()
                    || !game.legalActions(state)
                            .equals(
                                    request.flopActions().isEmpty()
                                                    || request.flopActions()
                                                            .equals(List.of("check"))
                                            ? List.of("check", "bet")
                                            : List.of("fold", "call")))
                throw new IllegalArgumentException("Owned BTN check/bet decision required");
            String found = request.hero() + ":" + game.informationSet(state);
            var view = game.publicFlopState(state);
            if ((key != null && !key.equals(found)) || (context != null && !context.equals(view)))
                throw new IllegalStateException("Inconsistent owned BTN question");
            key = found;
            context = view;
            states.add(state);
            worlds.add(world);
        }
        if (states.isEmpty()) throw new IllegalArgumentException("No compatible BTN hero hand");
        return new OwnedQuestion(handoff, List.copyOf(states), List.copyOf(worlds), key, context);
    }

    static Check assess(
            SixMaxSuppliedFlopBettingGame game,
            SixMaxRefinedConditionalStudy.Request request,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            CfrSolution policy) {
        return assess(
                game,
                new AssessmentQuestion(
                        request.hero(),
                        request.heroCards(),
                        request.publicPreflopHistory(),
                        request.flopActions()),
                actions,
                policy);
    }

    private static Check assess(
            SixMaxSuppliedFlopBettingGame game,
            AssessmentQuestion request,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            CfrSolution policy) {
        var question = question(game, request);
        var handoff = question.handoff();
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        var local = SixMaxSuppliedFlopBettingStudy.localAudit(game, handoff, policy);
        var initialActual = new HashMap<Integer, Double>();
        local.rawWorldMasses()
                .forEach(w -> initialActual.put(w.originalDealIndex(), w.jointRootFlopReach()));
        double actual = 0, total = 0;
        var masses = new ArrayList<Double>();
        for (var world : question.worlds()) {
            double reached = initialActual.getOrDefault(world.originalDealIndex(), 0.0);
            double cf =
                    product(
                            world.originalJointProbability(),
                            world.physicalFlopProbabilityGivenDeal());
            var past = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            for (String action : handoff.continuationActions()) {
                int actor = game.source().currentPlayer(past);
                if (actor != request.hero())
                    cf =
                            product(
                                    cf,
                                    policy.at(
                                                    actor,
                                                    "supplied-connected-single-flop:"
                                                            + game.source().informationSet(past))
                                            .get(action));
                past = game.source().afterAction(past, action);
            }
            var state =
                    new State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            for (String action : request.flopActions()) {
                int actor = game.currentPlayer(state);
                double p = policy.at(actor, game.informationSet(state)).get(action);
                reached = product(reached, p);
                if (actor != request.hero()) cf = product(cf, p);
                state = game.afterAction(state, action);
            }
            actual += reached;
            total += cf;
            masses.add(cf);
        }
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        if (total > 0) {
            for (var action : actions) {
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (int i = 0; i < question.states().size(); i++)
                    if (masses.get(i) > 0)
                        roots.add(
                                new ChanceOutcome<>(
                                        game.afterAction(question.states().get(i), action.action()),
                                        masses.get(i) / total));
                values.put(
                        action.action(),
                        MultiPlayerInformationSetBestResponse.assess(
                                        new ForcedRoot(game, List.copyOf(roots)), policy)
                                .bestResponseUtilitiesBb()
                                .get(request.hero()));
            }
            double best = Collections.max(values.values());
            values.forEach((a, v) -> losses.put(a, Math.max(0, best - v)));
        }
        double br = quality.bestResponseUtilitiesBb().get(request.hero());
        double upper = actions.getFirst().diagnostic().globalHeroUpperValue();
        double margin = upper + SixMaxSuppliedFlopActionBounds.SECURITY_SLACK - br;
        boolean included = margin >= -BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        boolean checked = included && total > 0;
        Double violation = null, regret = null;
        var failures = new ArrayList<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        if (local.conditionalBestResponse() == null
                || local.conditionalBestResponse().nashConvBb()
                        > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("REACHED_FLOP_GAP");
        if (actual / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
        if (total / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        if (total > 0) {
            var row = policy.at(request.hero(), game.informationSet(question.states().getFirst()));
            regret =
                    losses.entrySet().stream()
                            .mapToDouble(e -> row.get(e.getKey()) * e.getValue())
                            .sum();
            if (regret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                failures.add("SELECTED_DECISION_REGRET");
            if (checked) {
                double worst = 0;
                for (var a : actions)
                    worst =
                            Math.max(
                                    worst,
                                    Math.max(
                                            Math.max(
                                                    a.lowerUtilityBb() - values.get(a.action()),
                                                    values.get(a.action()) - a.upperUtilityBb()),
                                            Math.max(
                                                    a.lowerLossBb() - losses.get(a.action()),
                                                    losses.get(a.action()) - a.upperLossBb())));
                violation = worst;
                if (worst > BoundedLinearProgram.CERTIFICATE_TOLERANCE)
                    failures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
            }
        }
        return new Check(
                policy.iterations(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                quality,
                local,
                actual,
                actual / handoff.physicalFlopMass(),
                total / handoff.physicalFlopMass(),
                br,
                upper,
                margin,
                included,
                checked,
                violation,
                values,
                losses,
                regret,
                failures);
    }

    private static double product(double mass, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new IllegalArgumentException("Invalid BTN path probability");
        double next = mass * probability;
        if (mass > 0 && probability > 0 && next == 0)
            throw new IllegalArgumentException("BTN question reach underflow");
        return next;
    }

    private record ForcedRoot(SixMaxSuppliedFlopBettingGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT =
                new State(-5, "", SixMaxSuppliedFlopBettingGame.Phase.FLOP, "btn-forced-quality");

        public int playerCount() {
            return game.playerCount();
        }

        public State initialState() {
            return ROOT;
        }

        public boolean isTerminal(State state) {
            return !ROOT.equals(state) && game.isTerminal(state);
        }

        public double[] terminalUtilities(State state) {
            return game.terminalUtilities(state);
        }

        public int currentPlayer(State state) {
            return ROOT.equals(state) ? -1 : game.currentPlayer(state);
        }

        public List<String> legalActions(State state) {
            return game.legalActions(state);
        }

        public String informationSet(State state) {
            return game.informationSet(state);
        }

        public State afterAction(State state, String action) {
            return game.afterAction(state, action);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State state) {
            if (!ROOT.equals(state))
                throw new IllegalArgumentException("BTN forced quality root required");
            return roots;
        }
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Report.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes =
                SixMaxTextureStudy.json(Objects.requireNonNull(result).report())
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded refined feedback exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored refined feedback exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    static void verifyReport(Report saved, Result owned) {
        if (owned == null || !saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete refined feedback replay differs");
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var input = readRequest(request);
        var saved = readReport(report);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("Refined feedback request differs");
        var owned = solve(input);
        verifyReport(saved, owned);
        return owned;
    }
}
