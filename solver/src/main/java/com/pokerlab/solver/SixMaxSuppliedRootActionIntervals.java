package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Physical, replayable diagnostic; deliberately produces no trainer policy or admission handle. */
public final class SixMaxSuppliedRootActionIntervals {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-root-action-interval-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-root-action-interval-report/v1";
    public static final String STATUS = "APPROXIMATE_SECURITY_FACE_DIAGNOSTIC_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedRangePreflopStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            FiniteTwoPlayerRootActionIntervals.Question question,
            double securitySlack) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || input == null
                    || question == null
                    || !Double.isFinite(securitySlack)
                    || securitySlack < FiniteTwoPlayerRootActionIntervals.DEFAULT_SECURITY_SLACK
                    || securitySlack > 1e-4)
                throw new IllegalArgumentException("Invalid supplied interval request");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            Request request,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            double heroCommittedBb,
            double lowerEvBb,
            double upperEvBb,
            FiniteTwoPlayerRootActionIntervals.Audit diagnostic) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || request == null
                    || binding == null
                    || diagnostic == null)
                throw new IllegalArgumentException("Invalid supplied interval report identity");
            prior = List.copyOf(prior);
            payoffs = List.copyOf(payoffs);
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
    }

    private SixMaxSuppliedRootActionIntervals() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        var game = new SixMaxSuppliedRangePreflopGame(request.input());
        return solve(game, request);
    }

    static Result solve(SixMaxSuppliedRangePreflopGame game, Request request) throws Exception {
        return solve(
                game,
                request,
                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                BoundedLinearProgram.MAX_PIVOTS,
                BoundedLinearProgram.MAX_ARITHMETIC_WORK);
    }

    static Result solve(
            SixMaxSuppliedRangePreflopGame game,
            Request request,
            long compilerLimit,
            int pivotLimit,
            long arithmeticLimit)
            throws Exception {
        if (!game.input().equals(request.input()))
            throw new IllegalArgumentException("Interval game/input differs");
        var diagnostic =
                FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                request.question(),
                                request.securitySlack(),
                                compilerLimit,
                                pivotLimit,
                                arithmeticLimit)
                        .audit();
        // The commitment is derived from the actual frozen public history, never caller supplied.
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        double commitment =
                game.publicBettingState(root).committedBb(Seat.values()[request.question().hero()]);
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        request,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        commitment,
                        diagnostic.lowerUtility() + commitment,
                        diagnostic.upperUtility() + commitment,
                        diagnostic));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Interval report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed interval report exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path requestPath, Path reportPath) throws Exception {
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var request = readRequest(requestPath);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(reportPath, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request())
                || !SixMaxHeadsUpPreflopGame.hash(request.input())
                        .equals(saved.binding().inputHash()))
            throw new IllegalArgumentException("Interval request lineage differs");
        // Reenumerate all physical boards and recompute every LP, original flow and behavioral BR.
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact root interval replay differs");
        return expected;
    }
}
