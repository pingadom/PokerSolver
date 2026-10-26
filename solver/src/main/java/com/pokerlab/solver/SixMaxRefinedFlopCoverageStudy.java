package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded, ordered interval discovery. No fresh references, grading handle or trainer admission.
 */
public final class SixMaxRefinedFlopCoverageStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-refined-flop-coverage-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-refined-flop-coverage-report/v1";
    public static final String CERTIFIED_REACH_REQUEST_SCHEMA =
            "pokerlab-refined-flop-coverage-request/v2";
    public static final String CERTIFIED_REACH_REPORT_SCHEMA =
            "pokerlab-refined-flop-coverage-report/v2";
    public static final String STATUS = "INTERVAL_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION";
    public static final int MAX_HANDS = 4;
    public static final int MAX_BYTES = SixMaxRefinedConditionalStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxRefinedParentStudy.Request parent,
            String intervalAlgorithm,
            List<String> heroHands,
            String publicPreflopHistory,
            double maximumLossBb) {
        public Request {
            if (!(REQUEST_SCHEMA.equals(schemaVersion)
                            || CERTIFIED_REACH_REQUEST_SCHEMA.equals(schemaVersion))
                    || parent == null
                    || heroHands == null
                    || heroHands.isEmpty()
                    || heroHands.size() > MAX_HANDS
                    || heroHands.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(heroHands).size() != heroHands.size()
                    || !(REQUEST_SCHEMA.equals(schemaVersion)
                            ? FiniteTwoPlayerConditionalActionIntervals
                                    .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM
                                    .equals(intervalAlgorithm)
                            : FiniteTwoPlayerConditionalActionIntervals
                                    .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM
                                    .equals(intervalAlgorithm)))
                throw new IllegalArgumentException(
                        "Bounded distinct BTN hands and schema-bound v8/v9 mode required");
            heroHands = List.copyOf(heroHands);
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
            for (String hand : heroHands)
                for (String action : List.of("check", "bet"))
                    new SixMaxRefinedConditionalStudy.Request(
                            SixMaxRefinedConditionalStudy.REQUEST_SCHEMA,
                            parent,
                            intervalAlgorithm,
                            3,
                            hand,
                            publicPreflopHistory,
                            List.of(action));
        }
    }

    public enum Outcome {
        ACTION_SEPARATION,
        NO_ACTION_SEPARATION,
        CONDITIONAL_REJECTED,
        LP_REJECTED
    }

    public record Rejection(
            String layer,
            String reason,
            String detail,
            FiniteTwoPlayerConditionalActionIntervals.ReachFailureAudit reachFailure) {
        public Rejection {
            if (reason == null || detail == null || detail.isBlank() || detail.length() > 1024)
                throw new IllegalArgumentException("Bounded rejection detail required");
            if ("CONDITIONAL".equals(layer)) {
                var value = FiniteTwoPlayerConditionalActionIntervals.Failure.valueOf(reason);
                if (reachFailure != null
                        && value
                                != FiniteTwoPlayerConditionalActionIntervals.Failure
                                        .INSUFFICIENT_REACH)
                    throw new IllegalArgumentException("Unexpected rejection witness");
            } else if ("LP".equals(layer)) {
                BoundedLinearProgram.Failure.valueOf(reason);
                if (reachFailure != null)
                    throw new IllegalArgumentException("Unexpected LP reach witness");
            } else throw new IllegalArgumentException("Unknown rejection layer");
        }
    }

    public record Case(
            int index,
            SixMaxRefinedConditionalStudy.Request request,
            Outcome outcome,
            SixMaxRefinedConditionalStudy.Report conditional,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Rejection rejection) {
        public Case {
            moves = List.copyOf(moves);
            if (index < 0 || index >= 2 * MAX_HANDS || request == null || outcome == null)
                throw new IllegalArgumentException("Invalid discovery case");
            boolean solved =
                    outcome == Outcome.ACTION_SEPARATION || outcome == Outcome.NO_ACTION_SEPARATION;
            if (solved != (conditional != null)
                    || solved != (rejection == null)
                    || (!solved && !moves.isEmpty())
                    || (solved && !conditional.request().equals(request))
                    || (solved
                            && !moves.stream()
                                    .map(m -> m.action())
                                    .toList()
                                    .equals(conditional.context().legalActions()))
                    || (outcome == Outcome.CONDITIONAL_REJECTED
                            && !"CONDITIONAL".equals(rejection.layer()))
                    || (outcome == Outcome.LP_REJECTED && !"LP".equals(rejection.layer())))
                throw new IllegalArgumentException("Discovery outcome and evidence differ");
            if (rejection != null && rejection.reachFailure() != null) {
                var audit = rejection.reachFailure();
                String expectedProtocol =
                        FiniteTwoPlayerConditionalActionIntervals
                                                .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM
                                                .equals(request.intervalAlgorithm())
                                        || FiniteTwoPlayerConditionalActionIntervals
                                                .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM
                                                .equals(request.intervalAlgorithm())
                                ? FiniteTwoPlayerConditionalActionIntervals
                                        .CERTIFIED_REACH_FAILURE_ALGORITHM
                                : FiniteTwoPlayerConditionalActionIntervals.REACH_FAILURE_ALGORITHM;
                String key =
                        request.hero()
                                + ":supplied-flop-one-bet:"
                                + request.heroCards()
                                + ":"
                                + request.publicPreflopHistory()
                                + ":"
                                + String.join(" ", request.parent().connected().flop())
                                + ":"
                                + String.join("|", request.flopActions());
                if (!expectedProtocol.equals(audit.algorithm())
                        || audit.question().hero() != request.hero()
                        || !key.equals(audit.question().informationSet())
                        || audit.securitySlack() != SixMaxSuppliedFlopActionBounds.SECURITY_SLACK
                        || audit.requiredMinimumReach()
                                != SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                    throw new IllegalArgumentException(
                            "Rejected reach protocol or context differs");
            }
        }
    }

    public record Counts(
            int planned,
            int solved,
            int actionSeparated,
            int conditionalRejected,
            int lpRejected) {}

    public record Reservation(
            int physicalGameBuilds,
            int caseAttempts,
            int maximumActionComputations,
            int maximumParentSolves,
            int freshReferenceRuns,
            int automaticRetries) {
        static Reservation forRequest(Request request) {
            int cases = 2 * request.heroHands().size();
            // One parent study plus a parent reconstruction inside each of two action solves.
            return new Reservation(1, cases, 2 * cases, 3 * cases, 0, 0);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            Reservation reservation,
            Counts counts,
            SixMaxSuppliedFlopBettingGame.Binding binding,
            List<Case> cases) {
        public Report {
            cases = List.copyOf(cases);
            if (request == null
                    || !reportSchema(request).equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || !request.parent().connected().flop().equals(binding.flop())
                    || request.parent().connected().betPotFraction() != binding.betPotFraction()
                    || !Reservation.forRequest(request).equals(reservation)
                    || cases.size() != 2 * request.heroHands().size()
                    || !SixMaxRefinedFlopCoverageStudy.counts(cases).equals(counts))
                throw new IllegalArgumentException("Invalid bounded discovery report");
            var planned = plan(request);
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.get(i);
                if (item.index() != i || !item.request().equals(planned.get(i)))
                    throw new IllegalArgumentException("Discovery order or question differs");
                if (item.conditional() != null) {
                    if (!binding.equals(item.conditional().parent().binding())
                            || !moves(item.conditional(), request.maximumLossBb())
                                    .equals(item.moves())
                            || separated(item.moves())
                                    != (item.outcome() == Outcome.ACTION_SEPARATION))
                        throw new IllegalArgumentException(
                                "Discovery source or classifications differ");
                }
            }
        }
    }

    /**
     * Constructed only after a complete ordered solve/replay; never an offline grading capability.
     */
    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxRefinedFlopCoverageStudy() {}

    private static String reportSchema(Request request) {
        return CERTIFIED_REACH_REQUEST_SCHEMA.equals(request.schemaVersion())
                ? CERTIFIED_REACH_REPORT_SCHEMA
                : REPORT_SCHEMA;
    }

    static List<SixMaxRefinedConditionalStudy.Request> plan(Request request) {
        var planned = new ArrayList<SixMaxRefinedConditionalStudy.Request>();
        for (String hand : request.heroHands())
            for (String action : List.of("check", "bet"))
                planned.add(
                        new SixMaxRefinedConditionalStudy.Request(
                                SixMaxRefinedConditionalStudy.REQUEST_SCHEMA,
                                request.parent(),
                                request.intervalAlgorithm(),
                                3,
                                hand,
                                request.publicPreflopHistory(),
                                List.of(action)));
        return List.copyOf(planned);
    }

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(
            SixMaxRefinedConditionalStudy.Report report, double limit) {
        return report.actions().stream()
                .map(
                        a ->
                                new SixMaxSuppliedIntervalFeedback.Move(
                                        a.action(),
                                        a.lowerUtilityBb(),
                                        a.upperUtilityBb(),
                                        a.lowerLossBb(),
                                        a.upperLossBb(),
                                        SixMaxSuppliedIntervalFeedback.classify(
                                                a.lowerLossBb(), a.upperLossBb(), limit)))
                .toList();
    }

    static boolean separated(List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        return moves.stream()
                        .anyMatch(
                                m ->
                                        m.classification()
                                                == SixMaxSuppliedIntervalFeedback.Classification
                                                        .WITHIN_LIMIT)
                && moves.stream()
                        .anyMatch(
                                m ->
                                        m.classification()
                                                == SixMaxSuppliedIntervalFeedback.Classification
                                                        .OUTSIDE_LIMIT);
    }

    static Counts counts(List<Case> cases) {
        return new Counts(
                cases.size(),
                (int) cases.stream().filter(c -> c.conditional() != null).count(),
                (int) cases.stream().filter(c -> c.outcome() == Outcome.ACTION_SEPARATION).count(),
                (int)
                        cases.stream()
                                .filter(c -> c.outcome() == Outcome.CONDITIONAL_REJECTED)
                                .count(),
                (int) cases.stream().filter(c -> c.outcome() == Outcome.LP_REJECTED).count());
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var planned = plan(request);
        // Invalid handoff/context aborts before any LP, rather than masquerading as a numerical
        // rejection.
        for (var item : planned) SixMaxRefinedConditionalStudy.question(game, item);
        var cases = new ArrayList<Case>();
        for (int index = 0; index < planned.size(); index++) {
            var item = planned.get(index);
            try {
                var report = SixMaxRefinedConditionalStudy.solve(item, game).report();
                var moves = moves(report, request.maximumLossBb());
                cases.add(
                        new Case(
                                index,
                                item,
                                separated(moves)
                                        ? Outcome.ACTION_SEPARATION
                                        : Outcome.NO_ACTION_SEPARATION,
                                report,
                                moves,
                                null));
            } catch (FiniteTwoPlayerConditionalActionIntervals.Rejected rejection) {
                cases.add(
                        new Case(
                                index,
                                item,
                                Outcome.CONDITIONAL_REJECTED,
                                null,
                                List.of(),
                                new Rejection(
                                        "CONDITIONAL",
                                        rejection.reason().name(),
                                        rejection.getMessage(),
                                        rejection.reachFailure().orElse(null))));
            } catch (BoundedLinearProgram.Rejected rejection) {
                cases.add(
                        new Case(
                                index,
                                item,
                                Outcome.LP_REJECTED,
                                null,
                                List.of(),
                                new Rejection(
                                        "LP",
                                        rejection.reason().name(),
                                        rejection.getMessage(),
                                        null)));
            }
        }
        return new Result(
                new Report(
                        reportSchema(request),
                        STATUS,
                        false,
                        request,
                        Reservation.forRequest(request),
                        counts(cases),
                        game.binding(),
                        cases));
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
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded discovery report exceeds cap");
        path = path.toAbsolutePath().normalize();
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored discovery report exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path output) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), output.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(output);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Discovery request differs");
        var result = solve(request);
        if (!result.report().equals(saved))
            throw new IllegalArgumentException("Complete discovery replay differs");
        return result;
    }
}
