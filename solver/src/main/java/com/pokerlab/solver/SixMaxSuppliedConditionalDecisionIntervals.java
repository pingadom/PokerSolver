package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Replayable, all-move conditional diagnostic with an independently varying hidden-hand posterior.
 */
public final class SixMaxSuppliedConditionalDecisionIntervals {
    public static final String REQUEST_SCHEMA =
            "pokerlab-supplied-conditional-decision-interval-request/v1";
    public static final String REPORT_SCHEMA =
            "pokerlab-supplied-conditional-decision-interval-report/v1";
    public static final String STATUS = "POSITIVE_REACH_CONDITIONAL_DIAGNOSTIC_ONLY";
    public static final String CONDITIONING =
            "CHANCE_AND_OPPONENT_ACTION_REACH_WITH_FIXED_HERO_PAST/v1";
    public static final int MAX_BYTES = SixMaxSuppliedRootActionIntervals.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            int hero,
            String informationSet,
            double securitySlack,
            double minimumReach) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || input == null
                    || hero >= 6
                    || !Double.isFinite(securitySlack)
                    || securitySlack < FiniteTwoPlayerRootActionIntervals.DEFAULT_SECURITY_SLACK
                    || securitySlack > 1e-4
                    || !Double.isFinite(minimumReach)
                    || minimumReach < .0001
                    || minimumReach > 1)
                throw new IllegalArgumentException("Invalid supplied conditional interval request");
            new FiniteTwoPlayerConditionalActionIntervals.Question(
                    hero, informationSet, "validate");
        }
    }

    public record Move(
            String action,
            double lowerEvBb,
            double upperEvBb,
            double lowerRegretBb,
            double upperRegretBb,
            boolean robustlyBestAtCertificateTolerance,
            FiniteTwoPlayerConditionalActionIntervals.Audit diagnostic) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String regretScope,
            String conditioning,
            Request request,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            double heroCommittedBb,
            List<Move> moves,
            FiniteTwoPlayerRootActionIntervals.Work work) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || work == null
                    || !SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE.equals(regretScope)
                    || !CONDITIONING.equals(conditioning))
                throw new IllegalArgumentException("Invalid conditional decision report identity");
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

    private SixMaxSuppliedConditionalDecisionIntervals() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        // Enumerate all physical boards once for all moves. Each baseline retains its old caps.
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
            throw new IllegalArgumentException("Conditional game/input differs");
        var question = find(game, game.initialState(), request.informationSet());
        if (question == null || game.currentPlayer(question) != request.hero())
            throw new IllegalArgumentException("Conditional question does not exist");
        // Derive sunk contributions from the actual target state, including previous hero raises.
        double commitment =
                game.publicBettingState(question).committedBb(Seat.values()[request.hero()]);
        var actions = game.legalActions(question);
        var results = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Audit>();
        long compiler = 0, arithmetic = 0;
        int solves = 0, pivots = 0;
        for (String action : actions) {
            var audit =
                    FiniteTwoPlayerConditionalActionIntervals.solve(
                                    game,
                                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                                            request.hero(), request.informationSet(), action),
                                    request.securitySlack(),
                                    request.minimumReach(),
                                    compilerLimit - compiler,
                                    pivotLimit - pivots,
                                    arithmeticLimit - arithmetic)
                            .audit();
            results.add(audit);
            compiler += audit.work().compilerUnits();
            arithmetic += audit.work().intervalLpArithmeticWork();
            pivots += audit.work().intervalLpPivots();
            solves += audit.work().intervalLpSolves();
        }
        var moves = new ArrayList<Move>();
        for (int i = 0; i < actions.size(); i++) {
            var selected = results.get(i);
            double otherLower = Double.NEGATIVE_INFINITY, otherUpper = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < actions.size(); j++)
                if (j != i) {
                    otherLower = Math.max(otherLower, results.get(j).lowerUtility());
                    otherUpper = Math.max(otherUpper, results.get(j).upperUtility());
                }
            moves.add(
                    new Move(
                            actions.get(i),
                            selected.lowerUtility() + commitment,
                            selected.upperUtility() + commitment,
                            Math.max(0, otherLower - selected.upperUtility()),
                            Math.max(0, otherUpper - selected.lowerUtility()),
                            selected.lowerUtility() + BoundedLinearProgram.CERTIFICATE_TOLERANCE
                                    >= otherUpper,
                            selected));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE,
                        CONDITIONING,
                        request,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        commitment,
                        moves,
                        new FiniteTwoPlayerRootActionIntervals.Work(
                                compiler, compilerLimit, solves, pivots, arithmetic)));
    }

    private static State find(
            SixMaxSuppliedRangePreflopGame game, State state, String informationSet) {
        if (game.isTerminal(state)) return null;
        if (game.currentPlayer(state) == -1) {
            for (var outcome : game.chanceOutcomes(state)) {
                var found = find(game, outcome.state(), informationSet);
                if (found != null) return found;
            }
        } else {
            if ((game.currentPlayer(state) + ":" + game.informationSet(state))
                    .equals(informationSet)) return state;
            for (String action : game.legalActions(state)) {
                var found = find(game, game.afterAction(state, action), informationSet);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Conditional report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed conditional report exceeds byte cap");
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
            throw new IllegalArgumentException("Conditional decision lineage differs");
        // Reenumerate boards and independently recompute every LP, flow, posterior and response.
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact conditional decision replay differs");
        return expected;
    }
}
