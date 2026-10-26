package com.pokerlab.solver;

import static com.pokerlab.solver.SixMaxRefinedFlopCoverageStudy.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete BB hand/path discovery on the original physical game; never grading authority. */
public final class SixMaxBigBlindFlopCoverageStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-big-blind-flop-coverage-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-big-blind-flop-coverage-report/v1";
    public static final String REFINED_REQUEST_SCHEMA =
            "pokerlab-big-blind-flop-coverage-request/v2";
    public static final String REFINED_REPORT_SCHEMA = "pokerlab-big-blind-flop-coverage-report/v2";
    public static final int MAX_BYTES = SixMaxRefinedConditionalStudy.MAX_BYTES;
    private static final List<List<String>> PATHS = List.of(List.of(), List.of("check", "bet"));

    public record Request(
            String schemaVersion,
            SixMaxRefinedParentStudy.Request parent,
            String intervalAlgorithm,
            int hero,
            List<String> heroHands,
            String publicPreflopHistory,
            double maximumLossBb) {
        public Request {
            if (!(REQUEST_SCHEMA.equals(schemaVersion)
                            || REFINED_REQUEST_SCHEMA.equals(schemaVersion))
                    || parent == null
                    || hero != 5
                    || !(REQUEST_SCHEMA.equals(schemaVersion)
                            ? FiniteTwoPlayerConditionalActionIntervals
                                    .PHYSICAL_FLOP_CERTIFIED_REACH_ALGORITHM
                                    .equals(intervalAlgorithm)
                            : FiniteTwoPlayerConditionalActionIntervals
                                    .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM
                                    .equals(intervalAlgorithm))
                    || heroHands == null
                    || heroHands.isEmpty()
                    || heroHands.size() > MAX_HANDS
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Explicit bounded complete BB discovery required");
            heroHands = List.copyOf(heroHands);
            var allHands =
                    parent.connected().input().worlds().stream()
                            .map(w -> w.dealtCombos().get(hero))
                            .distinct()
                            .sorted()
                            .toList();
            if (!heroHands.equals(allHands))
                throw new IllegalArgumentException(
                        "All original BB hands in sorted order required");
            for (String hand : heroHands)
                for (var path : PATHS)
                    conditional(parent, intervalAlgorithm, hand, publicPreflopHistory, path);
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
            if (!reportSchema(request).equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || binding.trainerAdmission()
                    || !request.parent().connected().flop().equals(binding.flop())
                    || request.parent().connected().betPotFraction() != binding.betPotFraction()
                    || !SixMaxBigBlindFlopCoverageStudy.reservation(request).equals(reservation)
                    || cases.size() != 2 * request.heroHands().size()
                    || !SixMaxRefinedFlopCoverageStudy.counts(cases).equals(counts))
                throw new IllegalArgumentException("Invalid complete BB discovery report");
            var planned = plan(request);
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.get(i);
                if (item.index() != i || !item.request().equals(planned.get(i)))
                    throw new IllegalArgumentException("BB discovery order or actor/path differs");
                if (item.conditional() != null
                        && (!binding.equals(item.conditional().parent().binding())
                                || !moves(item.conditional(), request.maximumLossBb())
                                        .equals(item.moves())
                                || separated(item.moves())
                                        != (item.outcome() == Outcome.ACTION_SEPARATION)))
                    throw new IllegalArgumentException("BB source or classification differs");
            }
        }
    }

    /** Private ownership means reading an edited JSON report cannot produce a solver result. */
    public static final class Result {
        private final Report report;

        private Result(Report report) throws Exception {
            if (SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Expanded BB discovery exceeds cap");
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxBigBlindFlopCoverageStudy() {}

    private static String reportSchema(Request request) {
        return request != null && REFINED_REQUEST_SCHEMA.equals(request.schemaVersion())
                ? REFINED_REPORT_SCHEMA
                : REPORT_SCHEMA;
    }

    private static SixMaxRefinedConditionalStudy.Request conditional(
            SixMaxRefinedParentStudy.Request parent,
            String algorithm,
            String hand,
            String history,
            List<String> path) {
        return new SixMaxRefinedConditionalStudy.Request(
                SixMaxRefinedConditionalStudy.REQUEST_SCHEMA,
                parent,
                algorithm,
                5,
                hand,
                history,
                path);
    }

    static List<SixMaxRefinedConditionalStudy.Request> plan(Request request) {
        var result = new ArrayList<SixMaxRefinedConditionalStudy.Request>();
        for (String hand : request.heroHands())
            for (var path : PATHS)
                result.add(
                        conditional(
                                request.parent(),
                                request.intervalAlgorithm(),
                                hand,
                                request.publicPreflopHistory(),
                                path));
        return List.copyOf(result);
    }

    static Reservation reservation(Request request) {
        int n = 2 * request.heroHands().size();
        return new Reservation(1, n, 2 * n, 3 * n, 0, 0);
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        return solve(request, game);
    }

    static Result solve(Request request, SixMaxSuppliedFlopBettingGame game) throws Exception {
        var input = request.parent().connected();
        if (!input.input().equals(game.source().input())
                || !input.flop().equals(game.binding().flop())
                || input.betPotFraction() != game.binding().betPotFraction())
            throw new IllegalArgumentException("BB coverage game identity differs");
        var planned = plan(request);
        // Invalid histories are input failures before any LP, never numerical study outcomes.
        for (var item : planned) SixMaxRefinedConditionalStudy.question(game, item);
        var cases = new ArrayList<Case>();
        for (int i = 0; i < planned.size(); i++) {
            var item = planned.get(i);
            try {
                var report = SixMaxRefinedConditionalStudy.solve(item, game).report();
                var moves = moves(report, request.maximumLossBb());
                cases.add(
                        new Case(
                                i,
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
                                i,
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
                                i,
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
                        reservation(request),
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
            throw new IllegalArgumentException("Expanded BB report exceeds cap");
        path = path.toAbsolutePath().normalize();
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored BB report exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path output) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), output.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(output);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("BB request differs");
        var result = solve(request);
        if (!result.report().equals(saved))
            throw new IllegalArgumentException("Complete BB replay differs");
        return result;
    }
}
