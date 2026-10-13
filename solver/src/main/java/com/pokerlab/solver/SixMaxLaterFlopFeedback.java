package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned qualification of BB's later fold/call decision; separate from initial-flop feedback. */
public final class SixMaxLaterFlopFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-later-bb-flop-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-later-bb-flop-feedback-report/v1";
    public static final String STATUS = "LATER_BB_PHYSICAL_FLOP_INTERVAL_FEEDBACK_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedFlopBettingStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopBettingStudy.Request connected,
            String intervalAlgorithm,
            int hero,
            String heroCards,
            String publicPreflopHistory,
            List<String> flopActions,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || connected == null
                    || !FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_EXACT_POWER_TWO_ALGORITHM
                            .equals(intervalAlgorithm)
                    || hero != 5
                    || heroCards == null
                    || publicPreflopHistory == null
                    || publicPreflopHistory.length() > 4096
                    || !List.of("check", "bet").equals(flopActions)
                    || connected.input().worlds().stream()
                            .noneMatch(w -> w.dealtCombos().get(hero).equals(heroCards)))
                throw new IllegalArgumentException("Unsupported later BB flop request");
            flopActions = List.copyOf(flopActions);
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Reference(CfrSolution policy, Check check) {
        public Reference {
            if (policy == null || check == null || policy.iterations() != check.iterations())
                throw new IllegalArgumentException("Invalid later flop reference");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String evScope,
            String reachUnits,
            String informationSet,
            SixMaxSuppliedFlopBettingStudy.Report parent,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Reference> references,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            actions = List.copyOf(actions);
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedFlopActionBounds.REACH_UNITS.equals(reachUnits)
                    || informationSet == null
                    || parent == null
                    || !request.connected().equals(parent.request())
                    || context == null
                    || context.actingSeat().ordinal() != request.hero()
                    || !context.publicPreflopHistory().equals(request.publicPreflopHistory())
                    || !context.flopHistory().stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(request.flopActions())
                    || !context.legalActions().equals(List.of("fold", "call"))
                    || !actions.stream()
                            .map(a -> a.action())
                            .toList()
                            .equals(context.legalActions())
                    || !moves.stream().map(m -> m.action()).toList().equals(context.legalActions())
                    || primary == null
                    || !references.stream()
                            .map(r -> r.policy().iterations())
                            .toList()
                            .equals(List.of(2000, 4000))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid later flop feedback identity");
        }
    }

    /** Only a complete owned solve or physical replay can create this capability. */
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
        private final QualifiedFeedback feedback;

        private Result(Report report) throws Exception {
            this.report = report;
            feedback = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(feedback);
        }
    }

    private record OwnedQuestion(
            SixMaxSuppliedFlopHandoff.Audit handoff,
            List<State> states,
            List<SixMaxSuppliedFlopHandoff.World> worlds,
            String informationSet,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context) {}

    private SixMaxLaterFlopFeedback() {}

    private static OwnedQuestion question(SixMaxSuppliedFlopBettingGame game, Request request) {
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
            if (game.currentPlayer(state) != request.hero())
                throw new IllegalArgumentException("BB must act first at selected flop");
            for (String action : request.flopActions()) state = game.afterAction(state, action);
            if (game.currentPlayer(state) != request.hero()
                    || !game.legalActions(state).equals(List.of("fold", "call")))
                throw new IllegalArgumentException("Owned later BB fold/call decision required");
            String found = request.hero() + ":" + game.informationSet(state);
            var view = game.publicFlopState(state);
            if ((key != null && !key.equals(found)) || (context != null && !context.equals(view)))
                throw new IllegalStateException("Inconsistent owned later question");
            key = found;
            context = view;
            states.add(state);
            worlds.add(world);
        }
        if (states.isEmpty()) throw new IllegalArgumentException("No compatible later hero hand");
        return new OwnedQuestion(handoff, List.copyOf(states), List.copyOf(worlds), key, context);
    }

    public static Result solve(Request request) throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var question = question(game, request);
        var parent = SixMaxSuppliedFlopBettingStudy.solve(connected, game).report();
        var intervals = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
        var lowers = new ArrayList<Double>();
        var uppers = new ArrayList<Double>();
        for (String action : question.context().legalActions()) {
            var owned =
                    FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlopExactPowerOfTwo(
                            game,
                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                    request.hero(), question.informationSet(), action),
                            SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                            SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR);
            double shift = owned.context().upperObjectiveShift();
            double lower =
                    -shift
                            - owned.audit().lowerWitness().certificate().dualValue()
                            - BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            double upper =
                    owned.audit().upperWitnesses().stream()
                                    .mapToDouble(w -> w.certificate().dualValue() - shift)
                                    .max()
                                    .orElseThrow()
                            + BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper)
                throw new IllegalStateException("Invalid later original-dual utility bounds");
            intervals.add(owned);
            lowers.add(lower);
            uppers.add(upper);
        }
        double bestLower = Collections.max(lowers), bestUpper = Collections.max(uppers);
        var actions = new ArrayList<SixMaxSuppliedFlopActionBounds.Action>();
        var moves = new ArrayList<SixMaxSuppliedIntervalFeedback.Move>();
        for (int i = 0; i < intervals.size(); i++) {
            var owned = intervals.get(i);
            var audit = owned.audit();
            double lowerLoss = Math.max(0, bestLower - uppers.get(i));
            double upperLoss = Math.max(0, bestUpper - lowers.get(i));
            actions.add(
                    new SixMaxSuppliedFlopActionBounds.Action(
                            audit.question().action(),
                            lowers.get(i),
                            uppers.get(i),
                            lowerLoss,
                            upperLoss,
                            owned.context().upperObjectiveShift(),
                            question.handoff().physicalFlopMass()
                                    * audit.certifiedReachLowerBound(),
                            question.handoff().physicalFlopMass()
                                    * audit.certifiedReachUpperBound(),
                            audit));
            moves.add(
                    new SixMaxSuppliedIntervalFeedback.Move(
                            audit.question().action(),
                            lowers.get(i),
                            uppers.get(i),
                            lowerLoss,
                            upperLoss,
                            SixMaxSuppliedIntervalFeedback.classify(
                                    lowerLoss, upperLoss, request.maximumLossBb())));
        }
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
        var primary = assess(game, request, actions, parent.candidate());
        primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        var references = new ArrayList<Reference>();
        for (int iterations : List.of(2000, 4000)) {
            var policy =
                    new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            var check = assess(game, request, actions, policy);
            references.add(new Reference(policy, check));
            check.failures().forEach(f -> failures.add(iterations + ":" + f));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        question.informationSet(),
                        parent,
                        question.context(),
                        actions,
                        moves,
                        primary,
                        references,
                        failures.isEmpty(),
                        List.copyOf(failures)));
    }

    static Check assess(
            SixMaxSuppliedFlopBettingGame game,
            Request request,
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
            throw new IllegalArgumentException("Invalid later path probability");
        double next = mass * probability;
        if (mass > 0 && probability > 0 && next == 0)
            throw new IllegalArgumentException("Later question reach underflow");
        return next;
    }

    private record ForcedRoot(SixMaxSuppliedFlopBettingGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT =
                new State(-5, "", SixMaxSuppliedFlopBettingGame.Phase.FLOP, "later-forced-quality");

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
                throw new IllegalArgumentException("Later forced quality root required");
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
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded later feedback exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored later feedback exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var saved = readReport(report);
        var input = readRequest(request);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("Later feedback request differs");
        var expected = solve(input);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Physical later feedback replay differs");
        return expected;
    }
}
