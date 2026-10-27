package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Cheap necessary-condition screen: complete owned intervals and primary policy, no CFR or grading.
 */
public final class SixMaxBigBlindPrimaryScreen {
    public static final String SCHEMA = "pokerlab-big-blind-primary-screen/v1";
    public static final String STATUS =
            "PRIMARY_ELIGIBILITY_ONLY_FRESH_REFERENCE_QUALIFICATION_REQUIRED";
    public static final int MAX_BYTES = SixMaxBigBlindPairwiseStudy.MAX_BYTES;

    public record Reservation(
            int physicalGameBuilds,
            int caseAttempts,
            int maximumParentSolves,
            int maximumActionComputations,
            int maximumPrimaryAssessments,
            int freshReferenceRuns,
            int automaticRetries) {
        static Reservation of(SixMaxBigBlindFlopCoverageStudy.Request request) {
            int n = 2 * request.heroHands().size();
            return new Reservation(1, n, 3 * n, 2 * n, n, 0, 0);
        }
    }

    public record Case(
            int index,
            SixMaxSuppliedFlopFeedback.Check primary,
            boolean primaryEligible,
            List<String> rejectionReasons) {
        public Case {
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            SixMaxBigBlindFlopCoverageStudy.Request request,
            Reservation reservation,
            SixMaxBigBlindPairwiseStudy.Report study,
            List<Case> cases,
            List<Integer> primaryEligibleIndices) {
        public Report {
            cases = List.copyOf(cases);
            primaryEligibleIndices = List.copyOf(primaryEligibleIndices);
            if (!SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || study == null
                    || !request.equals(study.source().request())
                    || !Reservation.of(request).equals(reservation)
                    || cases.size() != study.cases().size())
                throw new IllegalArgumentException("Complete owned BB primary screen required");
            var eligible = new ArrayList<Integer>();
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.get(i);
                var conditional = study.source().cases().get(i).conditional();
                if (item.index() != i || (conditional == null) != (item.primary() == null))
                    throw new IllegalArgumentException(
                            "BB primary screen order or evidence differs");
                if (conditional != null
                        && (item.primary().iterations()
                                        != conditional.parent().candidate().iterations()
                                || !item.primary()
                                        .solutionHash()
                                        .equals(conditional.parent().solutionHash())))
                    throw new IllegalArgumentException("BB screen primary identity differs");
                var expected =
                        SixMaxBigBlindPairwiseFeedback.failures(
                                study.cases().get(i), conditional, item.primary(), List.of());
                if (!expected.equals(item.rejectionReasons())
                        || item.primaryEligible() != expected.isEmpty())
                    throw new IllegalArgumentException("BB primary eligibility or failures differ");
                if (expected.isEmpty()) eligible.add(i);
            }
            if (!primaryEligibleIndices.equals(eligible))
                throw new IllegalArgumentException("Complete primary-eligible inventory differs");
        }
    }

    /** A screen result deliberately has no qualified-question capability or grade method. */
    public static final class Result {
        private final Report report;

        private Result(Report report) throws Exception {
            if (SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Expanded BB primary screen exceeds cap");
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxBigBlindPrimaryScreen() {}

    public static Result solve(SixMaxBigBlindFlopCoverageStudy.Request request) throws Exception {
        SixMaxBigBlindPairwiseStudy.requireMode(request);
        var input = request.parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var owned = SixMaxBigBlindFlopCoverageStudy.solve(request, game);
        var study = SixMaxBigBlindPairwiseStudy.assess(owned).report();
        var cases = new ArrayList<Case>();
        var eligible = new ArrayList<Integer>();
        for (int i = 0; i < study.cases().size(); i++) {
            var conditional = study.source().cases().get(i).conditional();
            SixMaxSuppliedFlopFeedback.Check primary = null;
            if (conditional != null)
                primary =
                        BigBlindFlopPolicyChecks.assess(
                                game,
                                conditional.request(),
                                SixMaxBigBlindPairwiseFeedback.pairwiseActions(
                                        conditional, study.cases().get(i)),
                                conditional.parent().candidate());
            var reasons =
                    SixMaxBigBlindPairwiseFeedback.failures(
                            study.cases().get(i), conditional, primary, List.of());
            cases.add(new Case(i, primary, reasons.isEmpty(), reasons));
            if (reasons.isEmpty()) eligible.add(i);
        }
        return new Result(
                new Report(
                        SCHEMA,
                        STATUS,
                        false,
                        request,
                        Reservation.of(request),
                        study,
                        cases,
                        eligible));
    }

    public static Report readReport(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Report.class);
    }

    public static void write(Path path, Result owned) throws Exception {
        SixMaxBigBlindPairwiseStudy.writeReport(path, owned.report());
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var input = SixMaxBigBlindFlopCoverageStudy.readRequest(request);
        var saved = readReport(report);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("BB primary screen request differs");
        var owned = solve(input);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Complete BB primary screen replay differs");
        return owned;
    }
}
