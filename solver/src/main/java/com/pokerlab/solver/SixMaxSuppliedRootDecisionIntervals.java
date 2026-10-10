package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All root moves and conservative regret bounds on the same numerical security face. */
public final class SixMaxSuppliedRootDecisionIntervals {
    public static final String REQUEST_SCHEMA =
            "pokerlab-supplied-root-decision-interval-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-root-decision-interval-report/v1";

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            int hero,
            String informationSet,
            double securitySlack) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion))
                throw new IllegalArgumentException("Unsupported root decision request");
            // Share strict question/slack/input validation without inventing an actual move.
            new SixMaxSuppliedRootActionIntervals.Request(
                    SixMaxSuppliedRootActionIntervals.REQUEST_SCHEMA,
                    input,
                    new FiniteTwoPlayerRootActionIntervals.Question(
                            hero, informationSet, "validate"),
                    securitySlack);
        }
    }

    public record Move(
            String action,
            double lowerEvBb,
            double upperEvBb,
            double lowerRegretBb,
            double upperRegretBb,
            boolean robustlyBestAtCertificateTolerance,
            FiniteTwoPlayerRootActionIntervals.Audit diagnostic) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String regretScope,
            Request request,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            double heroCommittedBb,
            List<Move> moves,
            FiniteTwoPlayerRootActionIntervals.Work work) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !SixMaxSuppliedRootActionIntervals.STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || !SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !REGRET_SCOPE.equals(regretScope))
                throw new IllegalArgumentException("Invalid root decision report identity");
            prior = List.copyOf(prior);
            payoffs = List.copyOf(payoffs);
            moves = List.copyOf(moves);
        }
    }

    public static final String REGRET_SCOPE =
            "CONSERVATIVE_DIFFERENCE_OF_ACTION_INTERVALS_NOT_JOINT_EXTREMA/v1";

    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxSuppliedRootDecisionIntervals() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        var game = new SixMaxSuppliedRangePreflopGame(request.input());
        var root =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(outcome -> outcome.state())
                        .filter(
                                s ->
                                        (game.currentPlayer(s) + ":" + game.informationSet(s))
                                                .equals(request.informationSet()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("Root question does not exist"));
        var actions = game.legalActions(root);
        var results = new ArrayList<SixMaxSuppliedRootActionIntervals.Report>();
        long compiler = 0, arithmetic = 0;
        int solves = 0, pivots = 0;
        // Enumerate physical boards once and share the interval work caps across every move.
        // Each baseline solve retains its separately declared existing affine-solver budgets.
        for (String action : actions) {
            var report =
                    SixMaxSuppliedRootActionIntervals.solve(
                                    game,
                                    new SixMaxSuppliedRootActionIntervals.Request(
                                            SixMaxSuppliedRootActionIntervals.REQUEST_SCHEMA,
                                            request.input(),
                                            new FiniteTwoPlayerRootActionIntervals.Question(
                                                    request.hero(),
                                                    request.informationSet(),
                                                    action),
                                            request.securitySlack()),
                                    FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK - compiler,
                                    BoundedLinearProgram.MAX_PIVOTS - pivots,
                                    BoundedLinearProgram.MAX_ARITHMETIC_WORK - arithmetic)
                            .report();
            results.add(report);
            var work = report.diagnostic().work();
            compiler += work.compilerUnits();
            arithmetic += work.intervalLpArithmeticWork();
            pivots += work.intervalLpPivots();
            solves += work.intervalLpSolves();
        }
        var moves = new ArrayList<Move>();
        for (int i = 0; i < actions.size(); i++) {
            var selected = results.get(i);
            double otherLower = Double.NEGATIVE_INFINITY, otherUpper = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < actions.size(); j++)
                if (j != i) {
                    otherLower = Math.max(otherLower, results.get(j).lowerEvBb());
                    otherUpper = Math.max(otherUpper, results.get(j).upperEvBb());
                }
            moves.add(
                    new Move(
                            actions.get(i),
                            selected.lowerEvBb(),
                            selected.upperEvBb(),
                            Math.max(0, otherLower - selected.upperEvBb()),
                            Math.max(0, otherUpper - selected.lowerEvBb()),
                            selected.lowerEvBb() + BoundedLinearProgram.CERTIFICATE_TOLERANCE
                                    >= otherUpper,
                            selected.diagnostic()));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        SixMaxSuppliedRootActionIntervals.STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        REGRET_SCOPE,
                        request,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        results.getFirst().heroCommittedBb(),
                        moves,
                        new FiniteTwoPlayerRootActionIntervals.Work(
                                compiler,
                                FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                                solves,
                                pivots,
                                arithmetic)));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                path, SixMaxSuppliedRootActionIntervals.MAX_BYTES),
                        Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SixMaxSuppliedRootActionIntervals.MAX_BYTES)
            throw new IllegalArgumentException("Root decision report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > SixMaxSuppliedRootActionIntervals.MAX_BYTES)
            throw new IllegalArgumentException("Compressed root decision report exceeds byte cap");
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
                                SixMaxRankTexturePayoffTable.readBytes(
                                        reportPath, SixMaxSuppliedRootActionIntervals.MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request())
                || !SixMaxHeadsUpPreflopGame.hash(request.input())
                        .equals(saved.binding().inputHash()))
            throw new IllegalArgumentException("Root decision lineage differs");
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact root decision replay differs");
        return expected;
    }
}
