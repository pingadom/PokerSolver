package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exact physical replay of numerical joint decision-loss extrema; no trainer publication. */
public final class SixMaxSuppliedConditionalDecisionLoss {
    public static final String REQUEST_SCHEMA =
            "pokerlab-supplied-conditional-decision-loss-request/v1";
    public static final String REPORT_SCHEMA =
            "pokerlab-supplied-conditional-decision-loss-report/v1";
    public static final String STATUS = "JOINT_CONDITIONAL_LOSS_DIAGNOSTIC_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedConditionalDecisionIntervals.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            int hero,
            String informationSet,
            double securitySlack,
            double minimumReach) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion))
                throw new IllegalArgumentException("Unsupported joint loss request");
            new SixMaxSuppliedConditionalDecisionIntervals.Request(
                    SixMaxSuppliedConditionalDecisionIntervals.REQUEST_SCHEMA,
                    input,
                    hero,
                    informationSet,
                    securitySlack,
                    minimumReach);
        }
    }

    public record Move(
            String action,
            double lowerEvBb,
            double upperEvBb,
            double lowerLossBb,
            double upperLossBb,
            double conservativeLowerLossBb,
            double conservativeUpperLossBb,
            boolean robustAtCertificateTolerance) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String regretScope,
            String conservativeScope,
            String conditioning,
            Request request,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            double heroCommittedBb,
            List<Move> moves,
            FiniteTwoPlayerConditionalDecisionLoss.Audit diagnostic) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || diagnostic == null
                    || !SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !FiniteTwoPlayerConditionalDecisionLoss.SCOPE.equals(regretScope)
                    || !SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE.equals(conservativeScope)
                    || !SixMaxSuppliedConditionalDecisionIntervals.CONDITIONING.equals(
                            conditioning))
                throw new IllegalArgumentException("Invalid joint loss report identity");
            prior = List.copyOf(prior);
            payoffs = List.copyOf(payoffs);
            moves = List.copyOf(moves);
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

    private SixMaxSuppliedConditionalDecisionLoss() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        return solve(
                new SixMaxSuppliedRangePreflopGame(request.input()),
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
            throw new IllegalArgumentException("Joint loss game/input differs");
        var state =
                SixMaxSuppliedConditionalDecisionIntervals.find(
                        game, game.initialState(), request.informationSet());
        if (state == null || game.currentPlayer(state) != request.hero())
            throw new IllegalArgumentException("Joint loss question does not exist");
        double commitment =
                game.publicBettingState(state).committedBb(Seat.values()[request.hero()]);
        var audit =
                FiniteTwoPlayerConditionalDecisionLoss.solve(
                                game,
                                new FiniteTwoPlayerConditionalDecisionLoss.Question(
                                        request.hero(), request.informationSet()),
                                request.securitySlack(),
                                request.minimumReach(),
                                compilerLimit,
                                pivotLimit,
                                arithmeticLimit)
                        .audit();
        var moves = new ArrayList<Move>();
        for (int i = 0; i < audit.moves().size(); i++) {
            var loss = audit.moves().get(i);
            var ev = audit.actions().get(i).interval();
            moves.add(
                    new Move(
                            loss.action(),
                            ev.lowerUtility() + commitment,
                            ev.upperUtility() + commitment,
                            loss.lowerLoss(),
                            loss.upperLoss(),
                            loss.conservativeLowerLoss(),
                            loss.conservativeUpperLoss(),
                            loss.upperLoss() <= BoundedLinearProgram.CERTIFICATE_TOLERANCE));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        FiniteTwoPlayerConditionalDecisionLoss.SCOPE,
                        SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE,
                        SixMaxSuppliedConditionalDecisionIntervals.CONDITIONING,
                        request,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        commitment,
                        moves,
                        audit));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Joint loss report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed joint loss report exceeds byte cap");
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
            throw new IllegalArgumentException("Joint loss lineage differs");
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact joint loss replay differs");
        return expected;
    }
}
