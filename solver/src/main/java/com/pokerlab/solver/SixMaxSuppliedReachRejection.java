package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persists a failed full-face reach certificate, never a conditional EV or trainer capability. */
public final class SixMaxSuppliedReachRejection {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-reach-rejection-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-reach-rejection-report/v1";
    public static final String STATUS = "REJECTED_FULL_FACE_REACH_DIAGNOSTIC_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedIntervalFeedback.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            int hero,
            String informationSet,
            String action,
            double securitySlack,
            double minimumReach) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion))
                throw new IllegalArgumentException("Unsupported reach rejection request");
            new SixMaxSuppliedConditionalDecisionIntervals.Request(
                    SixMaxSuppliedConditionalDecisionIntervals.REQUEST_SCHEMA,
                    input,
                    hero,
                    informationSet,
                    securitySlack,
                    minimumReach);
            new FiniteTwoPlayerConditionalActionIntervals.Question(hero, informationSet, action);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            FiniteTwoPlayerConditionalActionIntervals.ReachFailureAudit diagnostic) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || diagnostic == null
                    || diagnostic.trainerAdmission()
                    || !diagnostic
                            .question()
                            .equals(
                                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                                            request.hero(),
                                            request.informationSet(),
                                            request.action()))
                    || diagnostic.securitySlack() != request.securitySlack()
                    || diagnostic.requiredMinimumReach() != request.minimumReach())
                throw new IllegalArgumentException("Invalid reach rejection report identity");
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

    private SixMaxSuppliedReachRejection() {}

    public static Result diagnose(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        var game = new SixMaxSuppliedRangePreflopGame(request.input());
        var state =
                SixMaxSuppliedConditionalDecisionIntervals.find(
                        game, game.initialState(), request.informationSet());
        if (state == null
                || game.currentPlayer(state) != request.hero()
                || !game.legalActions(state).contains(request.action()))
            throw new IllegalArgumentException("Reach rejection question/action does not exist");
        try {
            FiniteTwoPlayerConditionalActionIntervals.solve(
                    game,
                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                            request.hero(), request.informationSet(), request.action()),
                    request.securitySlack(),
                    request.minimumReach());
        } catch (FiniteTwoPlayerConditionalActionIntervals.Rejected rejected) {
            if (rejected.reason()
                            != FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH
                    || rejected.reachFailure().isEmpty()) throw rejected;
            return new Result(
                    new Report(
                            REPORT_SCHEMA,
                            STATUS,
                            false,
                            request,
                            game.binding(),
                            game.prior(),
                            game.payoffs(),
                            rejected.reachFailure().orElseThrow()));
        }
        throw new IllegalArgumentException("Question did not fail the full-face reach certificate");
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Reach report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored reach report exceeds byte cap");
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
            throw new IllegalArgumentException("Reach rejection lineage differs");
        var expected = diagnose(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact reach rejection replay differs");
        return expected;
    }
}
