package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Complete BB survey: two fresh references, all contexts checked, capabilities only for survivors.
 */
public final class SixMaxBigBlindPairwiseFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-big-blind-pairwise-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-big-blind-pairwise-feedback-report/v1";
    public static final String STATUS =
            "BIG_BLIND_PAIRWISE_FRESH_REFERENCE_QUALIFICATION_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxBigBlindPairwiseStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxBigBlindFlopCoverageStudy.Request coverage,
            String lossAlgorithm,
            SixMaxRefinedFlopFeedback.ReferenceBudget referenceBudget) {
        public Request {
            SixMaxBigBlindPairwiseStudy.requireMode(coverage);
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !ConditionalPairwiseRegret.ALGORITHM.equals(lossAlgorithm)
                    || referenceBudget == null)
                throw new IllegalArgumentException(
                        "Explicit complete BB pairwise qualification required");
        }
    }

    public record Reservation(
            int physicalGameBuilds,
            int caseAttempts,
            int maximumParentSolves,
            int maximumActionComputations,
            int freshReferenceRuns,
            int totalReferenceIterations,
            int selectedPolicyAssessments,
            int automaticRetries) {
        static Reservation of(Request r) {
            int n = 2 * r.coverage().heroHands().size();
            return new Reservation(1, n, 3 * n, 2 * n, 2, 240000, 3 * n, 0);
        }
    }

    public record Case(
            int index,
            SixMaxSuppliedFlopFeedback.Check primary,
            List<SixMaxSuppliedFlopFeedback.Check> references,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Case {
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            Reservation reservation,
            SixMaxBigBlindPairwiseStudy.Report study,
            List<SixMaxSharedFlopFeedback.Reference> references,
            List<Case> cases,
            List<Integer> qualifiedIndices) {
        public Report {
            references = List.copyOf(references);
            cases = List.copyOf(cases);
            qualifiedIndices = List.copyOf(qualifiedIndices);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !request.coverage().equals(study.source().request())
                    || !Reservation.of(request).equals(reservation)
                    || !references.stream()
                            .map(r -> r.policy().iterations())
                            .toList()
                            .equals(request.referenceBudget().iterations())
                    || cases.size() != study.cases().size())
                throw new IllegalArgumentException(
                        "Complete fresh BB qualification inventory required");
            var qualified = new ArrayList<Integer>();
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.get(i);
                var conditional = study.source().cases().get(i).conditional();
                if (item.index() != i
                        || (conditional == null) != (item.primary() == null)
                        || (conditional == null
                                ? !item.references().isEmpty()
                                : item.references().size() != 2
                                        || !item.references().stream()
                                                .map(c -> c.iterations())
                                                .toList()
                                                .equals(request.referenceBudget().iterations())))
                    throw new IllegalArgumentException("BB policy assessment order differs");
                if (conditional != null) {
                    if (item.primary().iterations() != conditional.parent().candidate().iterations()
                            || !item.primary()
                                    .solutionHash()
                                    .equals(conditional.parent().solutionHash()))
                        throw new IllegalArgumentException("BB primary identity differs");
                    for (int j = 0; j < references.size(); j++)
                        if (!item.references()
                                .get(j)
                                .solutionHash()
                                .equals(policyHash(references.get(j).policy())))
                            throw new IllegalArgumentException("BB fresh policy identity differs");
                }
                var reasons =
                        failures(
                                study.cases().get(i),
                                conditional,
                                item.primary(),
                                item.references());
                if (!reasons.equals(item.rejectionReasons())
                        || item.qualifiedForOfflineDrill() != reasons.isEmpty())
                    throw new IllegalArgumentException("BB qualification failures differ");
                if (reasons.isEmpty()) qualified.add(i);
            }
            if (!qualifiedIndices.equals(qualified))
                throw new IllegalArgumentException("Complete surviving BB inventory differs");
        }
    }

    public static final class QualifiedQuestion {
        private final String reportHash;
        private final int index;

        private QualifiedQuestion(String hash, int index) {
            reportHash = hash;
            this.index = index;
        }

        public String reportHash() {
            return reportHash;
        }

        public int index() {
            return index;
        }
    }

    public static final class Result {
        private final Report report;
        private final List<QualifiedQuestion> qualified;

        private Result(Report report) throws Exception {
            if (SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Expanded BB qualification exceeds cap");
            this.report = report;
            String hash = SixMaxHeadsUpPreflopGame.hash(report);
            qualified =
                    report.qualifiedIndices().stream()
                            .map(i -> new QualifiedQuestion(hash, i))
                            .toList();
        }

        public Report report() {
            return report;
        }

        public List<QualifiedQuestion> qualifiedQuestions() {
            return qualified;
        }
    }

    private SixMaxBigBlindPairwiseFeedback() {}

    private static String policyHash(CfrSolution policy) {
        return SixMaxConnectedPostflopAudit.solutionHash(policy);
    }

    static List<String> failures(
            SixMaxBigBlindPairwiseStudy.Case item,
            SixMaxRefinedConditionalStudy.Report conditional,
            SixMaxSuppliedFlopFeedback.Check primary,
            List<SixMaxSuppliedFlopFeedback.Check> references) {
        var failures = new TreeSet<String>();
        if (conditional == null) failures.add("INTERVAL_SOURCE_REJECTED");
        if (!item.moves().stream()
                .anyMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            failures.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (!item.moves().stream()
                .anyMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            failures.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        if (primary != null) primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        references.forEach(r -> r.failures().forEach(f -> failures.add(r.iterations() + ":" + f)));
        return List.copyOf(failures);
    }

    static List<SixMaxSuppliedFlopActionBounds.Action> pairwiseActions(
            SixMaxRefinedConditionalStudy.Report conditional,
            SixMaxBigBlindPairwiseStudy.Case item) {
        var actions = new ArrayList<SixMaxSuppliedFlopActionBounds.Action>();
        for (int i = 0; i < conditional.actions().size(); i++) {
            var a = conditional.actions().get(i);
            var move = item.moves().get(i);
            if (!a.action().equals(move.action())
                    || a.lowerUtilityBb() != move.lowerEvBb()
                    || a.upperUtilityBb() != move.upperEvBb())
                throw new IllegalArgumentException("BB pairwise action identity differs");
            actions.add(
                    new SixMaxSuppliedFlopActionBounds.Action(
                            a.action(),
                            a.lowerUtilityBb(),
                            a.upperUtilityBb(),
                            move.lowerLossBb(),
                            move.upperLossBb(),
                            a.upperObjectiveShift(),
                            a.rawCertifiedReachLowerBound(),
                            a.rawCertifiedReachUpperBound(),
                            a.diagnostic()));
        }
        return List.copyOf(actions);
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.coverage().parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var source = SixMaxBigBlindFlopCoverageStudy.solve(request.coverage(), game);
        var study = SixMaxBigBlindPairwiseStudy.assess(source).report();
        var runner = new SixMaxOwnedFlopReferences(game);
        var references = new ArrayList<SixMaxSharedFlopFeedback.Reference>();
        for (int iterations : request.referenceBudget().iterations()) {
            var run = runner.train(iterations);
            references.add(new SixMaxSharedFlopFeedback.Reference(run.policy(), run.audit()));
        }
        var cases = new ArrayList<Case>();
        var qualified = new ArrayList<Integer>();
        for (int i = 0; i < study.cases().size(); i++) {
            var conditional = study.source().cases().get(i).conditional();
            var pairwise = study.cases().get(i);
            SixMaxSuppliedFlopFeedback.Check primary = null;
            var checks = new ArrayList<SixMaxSuppliedFlopFeedback.Check>();
            if (conditional != null) {
                var actions = pairwiseActions(conditional, pairwise);
                primary =
                        BigBlindFlopPolicyChecks.assess(
                                game,
                                conditional.request(),
                                actions,
                                conditional.parent().candidate());
                for (var reference : references)
                    checks.add(
                            BigBlindFlopPolicyChecks.assess(
                                    game, conditional.request(), actions, reference.policy()));
            }
            var reasons = failures(pairwise, conditional, primary, checks);
            cases.add(new Case(i, primary, checks, reasons.isEmpty(), reasons));
            if (reasons.isEmpty()) qualified.add(i);
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        Reservation.of(request),
                        study,
                        references,
                        cases,
                        qualified));
    }

    /** A parsed report cannot grade: the capability must come from this owned result. */
    public static SixMaxSuppliedIntervalFeedback.Move grade(
            Result owned, int index, String action) {
        Objects.requireNonNull(owned);
        if (owned.qualifiedQuestions().stream().noneMatch(q -> q.index() == index))
            throw new IllegalArgumentException("BB question did not qualify");
        return owned.report().study().cases().get(index).moves().stream()
                .filter(m -> m.action().equals(action))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown BB action"));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
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
        var input = readRequest(request);
        var saved = readReport(report);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("BB qualification request differs");
        var owned = solve(input);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Complete BB qualification replay differs");
        return owned;
    }
}
