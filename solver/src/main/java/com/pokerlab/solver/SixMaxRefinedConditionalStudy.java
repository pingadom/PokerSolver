package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replayable conditional intervals with an explicit refined parent; no trainer capability. */
public final class SixMaxRefinedConditionalStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-refined-flop-conditional-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-refined-flop-conditional-report/v1";
    public static final String BASIS_REPORT_SCHEMA = "pokerlab-refined-flop-conditional-report/v2";
    public static final String RESOLVABLE_BASIS_REPORT_SCHEMA =
            "pokerlab-refined-flop-conditional-report/v3";
    public static final String CERTIFIED_REACH_REPORT_SCHEMA =
            "pokerlab-refined-flop-conditional-report/v4";
    public static final String PHASE_ONE_REFINED_REPORT_SCHEMA =
            "pokerlab-refined-flop-conditional-report/v5";
    public static final String STATUS = "REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxRefinedParentStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxRefinedParentStudy.Request parent,
            String intervalAlgorithm,
            int hero,
            String heroCards,
            String publicPreflopHistory,
            List<String> flopActions) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || intervalAlgorithm == null
                    || !Set.of(
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM,
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM,
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM,
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM,
                                    FiniteTwoPlayerConditionalActionIntervals
                                            .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM)
                            .contains(intervalAlgorithm)
                    || (hero != 3 && hero != 5)
                    || heroCards == null
                    || publicPreflopHistory == null
                    || publicPreflopHistory.length() > 4096
                    || flopActions == null
                    || !(hero == 3
                            ? List.of(List.of("check"), List.of("bet")).contains(flopActions)
                            : List.of(List.of(), List.of("check", "bet")).contains(flopActions))
                    || parent.connected().input().worlds().stream()
                            .noneMatch(w -> w.dealtCombos().get(hero).equals(heroCards)))
                throw new IllegalArgumentException("Unsupported refined conditional request");
            flopActions = List.copyOf(flopActions);
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
            SixMaxRefinedParentStudy.Report parent,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context,
            List<SixMaxSuppliedFlopActionBounds.Action> actions) {
        public Report {
            actions = List.copyOf(actions);
            if (request == null
                    || !schema(request).equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedFlopActionBounds.REACH_UNITS.equals(reachUnits)
                    || informationSet == null
                    || parent == null
                    || !request.parent().equals(parent.request())
                    || context == null
                    || context.actingSeat().ordinal() != request.hero()
                    || !context.publicPreflopHistory().equals(request.publicPreflopHistory())
                    || !context.flopHistory().stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(request.flopActions())
                    || actions.size() != 2
                    || !actions.stream()
                            .map(a -> a.action())
                            .toList()
                            .equals(context.legalActions()))
                throw new IllegalArgumentException("Invalid refined conditional report");
            for (var action : actions) {
                var audit = action.diagnostic();
                if (audit == null
                        || audit.trainerAdmission()
                        || !request.intervalAlgorithm().equals(audit.algorithm())
                        || !parent.solve().equals(audit.baseline())
                        || !audit.question()
                                .equals(
                                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                request.hero(), informationSet, action.action()))
                        || audit.securitySlack() != SixMaxSuppliedFlopActionBounds.SECURITY_SLACK
                        || audit.requiredMinimumReach()
                                != SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                    throw new IllegalArgumentException("Refined interval provenance differs");
            }
        }
    }

    /** Only complete owned solves or full physical replays construct this research result. */
    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    record OwnedQuestion(String key, SixMaxSuppliedFlopBettingGame.PublicFlopState context) {}

    private SixMaxRefinedConditionalStudy() {}

    static OwnedQuestion question(SixMaxSuppliedFlopBettingGame game, Request request) {
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(request.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown owned handoff"));
        OwnedQuestion selected = null;
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(request.hero()).equals(request.heroCards())) continue;
            var state =
                    new SixMaxSuppliedFlopBettingGame.State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            for (var action : request.flopActions()) state = game.afterAction(state, action);
            if (game.currentPlayer(state) != request.hero())
                throw new IllegalArgumentException("Owned conditional actor differs");
            var found =
                    new OwnedQuestion(
                            request.hero() + ":" + game.informationSet(state),
                            game.publicFlopState(state));
            if (selected != null && !selected.equals(found))
                throw new IllegalStateException("Inconsistent owned conditional question");
            selected = found;
        }
        if (selected == null) throw new IllegalArgumentException("No compatible hero hand");
        return selected;
    }

    private static boolean basis(Request request) {
        return FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM
                .equals(request.intervalAlgorithm());
    }

    private static boolean resolvableBasis(Request request) {
        return FiniteTwoPlayerConditionalActionIntervals
                .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM
                .equals(request.intervalAlgorithm());
    }

    private static boolean phaseOneRefined(Request request) {
        return FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM
                .equals(request.intervalAlgorithm());
    }

    private static boolean certifiedReach(Request request) {
        return FiniteTwoPlayerConditionalActionIntervals.PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM
                .equals(request.intervalAlgorithm());
    }

    private static String schema(Request request) {
        return phaseOneRefined(request)
                ? PHASE_ONE_REFINED_REPORT_SCHEMA
                : certifiedReach(request)
                        ? CERTIFIED_REACH_REPORT_SCHEMA
                        : resolvableBasis(request)
                                ? RESOLVABLE_BASIS_REPORT_SCHEMA
                                : basis(request) ? BASIS_REPORT_SCHEMA : REPORT_SCHEMA;
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var connected = request.parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        return solve(request, game);
    }

    static Result solve(Request request, SixMaxSuppliedFlopBettingGame game) throws Exception {
        var connected = request.parent().connected();
        if (!connected.input().equals(game.source().input())
                || !connected.flop().equals(game.binding().flop())
                || connected.betPotFraction() != game.binding().betPotFraction())
            throw new IllegalArgumentException("Refined conditional game identity differs");
        var question = question(game, request);
        var parent = SixMaxRefinedParentStudy.solve(request.parent(), game).report();
        var owned = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
        var lower = new ArrayList<Double>();
        var upper = new ArrayList<Double>();
        for (var action : question.context().legalActions()) {
            var result =
                    phaseOneRefined(request)
                            ? FiniteTwoPlayerConditionalActionIntervals
                                    .solvePhysicalFlopPhaseOneRefined(
                                            game,
                                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                    request.hero(), question.key(), action),
                                            SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                                            SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                            : certifiedReach(request)
                                    ? FiniteTwoPlayerConditionalActionIntervals
                                            .solvePhysicalFlopCertifiedReach(
                                                    game,
                                                    new FiniteTwoPlayerConditionalActionIntervals
                                                            .Question(
                                                            request.hero(), question.key(), action),
                                                    SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                                                    SixMaxSuppliedFlopActionBounds
                                                            .CONDITIONAL_REACH_FLOOR)
                                    : resolvableBasis(request)
                                            ? FiniteTwoPlayerConditionalActionIntervals
                                                    .solvePhysicalFlopResolvableRefinedBasis(
                                                            game,
                                                            new FiniteTwoPlayerConditionalActionIntervals
                                                                    .Question(
                                                                    request.hero(),
                                                                    question.key(),
                                                                    action),
                                                            SixMaxSuppliedFlopActionBounds
                                                                    .SECURITY_SLACK,
                                                            SixMaxSuppliedFlopActionBounds
                                                                    .CONDITIONAL_REACH_FLOOR)
                                            : basis(request)
                                                    ? FiniteTwoPlayerConditionalActionIntervals
                                                            .solvePhysicalFlopRefinedBasis(
                                                                    game,
                                                                    new FiniteTwoPlayerConditionalActionIntervals
                                                                            .Question(
                                                                            request.hero(),
                                                                            question.key(),
                                                                            action),
                                                                    SixMaxSuppliedFlopActionBounds
                                                                            .SECURITY_SLACK,
                                                                    SixMaxSuppliedFlopActionBounds
                                                                            .CONDITIONAL_REACH_FLOOR)
                                                    : FiniteTwoPlayerConditionalActionIntervals
                                                            .solvePhysicalFlopAscendingRefined(
                                                                    game,
                                                                    new FiniteTwoPlayerConditionalActionIntervals
                                                                            .Question(
                                                                            request.hero(),
                                                                            question.key(),
                                                                            action),
                                                                    SixMaxSuppliedFlopActionBounds
                                                                            .SECURITY_SLACK,
                                                                    SixMaxSuppliedFlopActionBounds
                                                                            .CONDITIONAL_REACH_FLOOR);
            double shift = result.context().upperObjectiveShift();
            double lo =
                    -shift
                            - result.audit().lowerWitness().certificate().dualValue()
                            - BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            double hi =
                    result.audit().upperWitnesses().stream()
                                    .mapToDouble(w -> w.certificate().dualValue() - shift)
                                    .max()
                                    .orElseThrow()
                            + BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi)
                throw new IllegalStateException("Invalid original-dual conditional bounds");
            owned.add(result);
            lower.add(lo);
            upper.add(hi);
        }
        double bestLower = Collections.max(lower), bestUpper = Collections.max(upper);
        double mass = game.handoffs().getFirst().physicalFlopMass();
        var actions = new ArrayList<SixMaxSuppliedFlopActionBounds.Action>();
        for (int i = 0; i < owned.size(); i++) {
            var result = owned.get(i);
            var audit = result.audit();
            actions.add(
                    new SixMaxSuppliedFlopActionBounds.Action(
                            audit.question().action(),
                            lower.get(i),
                            upper.get(i),
                            Math.max(0, bestLower - upper.get(i)),
                            Math.max(0, bestUpper - lower.get(i)),
                            result.context().upperObjectiveShift(),
                            mass * audit.certifiedReachLowerBound(),
                            mass * audit.certifiedReachUpperBound(),
                            audit));
        }
        return new Result(
                new Report(
                        schema(request),
                        STATUS,
                        false,
                        request,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        question.key(),
                        parent,
                        question.context(),
                        actions));
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
            throw new IllegalArgumentException("Expanded refined conditional exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored refined conditional exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(report);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Refined conditional request differs");
        var owned = solve(request);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Complete refined conditional replay differs");
        return owned;
    }
}
