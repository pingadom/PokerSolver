package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * One owned physical game, ordered questions and two fresh, explicitly shared reference policies.
 */
public final class SixMaxSharedFlopFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-shared-flop-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-shared-flop-feedback-report/v1";
    public static final String STATUS = "SHARED_REFERENCE_PHYSICAL_FLOP_RESEARCH_ONLY";
    public static final String SHARING = "ONE_GAME_TWO_FRESH_POLICIES_ALL_QUESTIONS_CHECKED/v1";
    public static final int MAX_QUESTIONS = 4;
    public static final int MAX_BYTES = SixMaxRefinedFlopFeedback.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxRefinedParentStudy.Request parent,
            String intervalAlgorithm,
            List<String> heroHands,
            String publicPreflopHistory,
            SixMaxRefinedFlopFeedback.ReferenceBudget referenceBudget,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || !FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM
                            .equals(intervalAlgorithm)
                    || heroHands == null
                    || heroHands.isEmpty()
                    || heroHands.size() > MAX_QUESTIONS
                    || heroHands.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(heroHands).size() != heroHands.size()
                    || referenceBudget == null
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Bounded distinct facing-bet hands and explicit v8 mode required");
            heroHands = List.copyOf(heroHands);
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
            for (String hand : heroHands)
                conditional(parent, intervalAlgorithm, hand, publicPreflopHistory);
        }

        SixMaxRefinedConditionalStudy.Request conditional(int index) {
            return conditional(
                    parent, intervalAlgorithm, heroHands.get(index), publicPreflopHistory);
        }

        private static SixMaxRefinedConditionalStudy.Request conditional(
                SixMaxRefinedParentStudy.Request parent,
                String algorithm,
                String hand,
                String history) {
            return new SixMaxRefinedConditionalStudy.Request(
                    SixMaxRefinedConditionalStudy.REQUEST_SCHEMA,
                    parent,
                    algorithm,
                    3,
                    hand,
                    history,
                    List.of("bet"));
        }
    }

    /** Actual declared calls: parent solves remain separate; only the reference pair is shared. */
    public record Reservation(
            int physicalGameBuilds,
            int conditionalStudies,
            int maximumParentSolves,
            int maximumActionComputations,
            int freshReferenceRuns,
            int totalReferenceIterations,
            int selectedPolicyAssessments,
            int automaticRetries) {
        static Reservation of(Request request) {
            int n = request.heroHands().size();
            return new Reservation(1, n, 3 * n, 2 * n, 2, 240000, 3 * n, 0);
        }
    }

    /**
     * Stored once. Question-dependent checks below identify these exact policies, in this order.
     */
    public record Reference(CfrSolution policy, SixMaxOwnedFlopReferences.Audit work) {
        public Reference {
            Objects.requireNonNull(policy);
            Objects.requireNonNull(work);
            if (!List.of(80000, 160000).contains(policy.iterations())
                    || work.prunedSuffixRoots() != 4L * policy.iterations())
                throw new IllegalArgumentException("Invalid shared reference work");
        }
    }

    public record Case(
            int index,
            SixMaxRefinedConditionalStudy.Report conditional,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            SixMaxSuppliedFlopFeedback.Check primary,
            List<SixMaxSuppliedFlopFeedback.Check> references,
            List<String> rejectionReasons) {
        public Case {
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (index < 0
                    || index >= MAX_QUESTIONS
                    || conditional == null
                    || primary == null
                    || !moves.stream()
                            .map(SixMaxSuppliedIntervalFeedback.Move::action)
                            .toList()
                            .equals(conditional.context().legalActions())
                    || !references.stream()
                            .map(SixMaxSuppliedFlopFeedback.Check::iterations)
                            .toList()
                            .equals(List.of(80000, 160000))
                    || primary.iterations() != conditional.parent().candidate().iterations()
                    || !primary.solutionHash().equals(conditional.parent().solutionHash()))
                throw new IllegalArgumentException("Incomplete shared question evidence");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String referenceSharing,
            Request request,
            Reservation reservation,
            List<Reference> references,
            List<Case> cases,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            references = List.copyOf(references);
            cases = List.copyOf(cases);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SHARING.equals(referenceSharing)
                    || request == null
                    || !Reservation.of(request).equals(reservation)
                    || !references.stream()
                            .map(r -> r.policy().iterations())
                            .toList()
                            .equals(request.referenceBudget().iterations())
                    || cases.size() != request.heroHands().size())
                throw new IllegalArgumentException("Shared batch identity or inventory differs");
            var failures = new TreeSet<String>();
            for (int i = 0; i < cases.size(); i++) {
                var item = cases.get(i);
                if (item.index() != i
                        || !item.conditional().request().equals(request.conditional(i))
                        || !item.conditional()
                                .parent()
                                .equals(cases.getFirst().conditional().parent())
                        || !item.moves()
                                .equals(
                                        SixMaxRefinedFlopCoverageStudy.moves(
                                                item.conditional(), request.maximumLossBb())))
                    throw new IllegalArgumentException(
                            "Shared question order, game or moves differ");
                for (int j = 0; j < references.size(); j++)
                    if (!item.references()
                            .get(j)
                            .solutionHash()
                            .equals(
                                    SixMaxConnectedPostflopAudit.solutionHash(
                                            references.get(j).policy())))
                        throw new IllegalArgumentException(
                                "Shared reference policy identity differs");
                var expected = failures(item.moves(), item.primary(), item.references());
                if (!item.rejectionReasons().equals(expected))
                    throw new IllegalArgumentException(
                            "Shared question rejection inventory differs");
                for (String reason : expected) failures.add(i + ":" + reason);
            }
            if (!rejectionReasons.equals(List.copyOf(failures))
                    || qualifiedForOfflineDrill != failures.isEmpty())
                throw new IllegalArgumentException("Whole shared batch qualification differs");
        }
    }

    public static final class QualifiedBatch {
        private final String reportHash;

        private QualifiedBatch(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
        }

        public String reportHash() {
            return reportHash;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedBatch qualified;

        private Result(Report report) throws Exception {
            if (SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Owned shared report exceeds expanded cap");
            this.report = report;
            qualified = report.qualifiedForOfflineDrill() ? new QualifiedBatch(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedBatch> qualifiedBatch() {
            return Optional.ofNullable(qualified);
        }
    }

    private SixMaxSharedFlopFeedback() {}

    private static List<String> failures(
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            SixMaxSuppliedFlopFeedback.Check primary,
            List<SixMaxSuppliedFlopFeedback.Check> references) {
        var failures = new TreeSet<String>();
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            failures.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            failures.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        references.forEach(r -> r.failures().forEach(f -> failures.add(r.iterations() + ":" + f)));
        return List.copyOf(failures);
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var connected = request.parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        // Reject any incompatible selection before the first optimizer or reference run.
        for (int i = 0; i < request.heroHands().size(); i++)
            SixMaxRefinedConditionalStudy.question(game, request.conditional(i));
        var conditionals = new ArrayList<SixMaxRefinedConditionalStudy.Report>();
        for (int i = 0; i < request.heroHands().size(); i++)
            conditionals.add(
                    SixMaxRefinedConditionalStudy.solve(request.conditional(i), game).report());
        var runner = new SixMaxOwnedFlopReferences(game);
        var references = new ArrayList<Reference>();
        // Each train call starts a new CFR solver. No saved policy, regret or global cache is used.
        for (int iterations : request.referenceBudget().iterations()) {
            var run = runner.train(iterations);
            references.add(new Reference(run.policy(), run.audit()));
        }
        var cases = new ArrayList<Case>();
        var failures = new TreeSet<String>();
        for (int i = 0; i < conditionals.size(); i++) {
            var conditional = conditionals.get(i);
            var moves = SixMaxRefinedFlopCoverageStudy.moves(conditional, request.maximumLossBb());
            var primary =
                    SixMaxRefinedFlopFeedback.assess(
                            game,
                            conditional.request(),
                            conditional.actions(),
                            conditional.parent().candidate());
            var checks =
                    references.stream()
                            .map(
                                    r ->
                                            SixMaxRefinedFlopFeedback.assess(
                                                    game,
                                                    conditional.request(),
                                                    conditional.actions(),
                                                    r.policy()))
                            .toList();
            var rejected = failures(moves, primary, checks);
            cases.add(new Case(i, conditional, moves, primary, checks, rejected));
            for (String reason : rejected) failures.add(i + ":" + reason);
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SHARING,
                        request,
                        Reservation.of(request),
                        references,
                        cases,
                        failures.isEmpty(),
                        List.copyOf(failures)));
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
        byte[] bytes =
                SixMaxTextureStudy.json(Objects.requireNonNull(result).report())
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded shared report exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored shared report exceeds cap");
        path = path.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    static void verifyReport(Report saved, Result owned) {
        if (owned == null || !owned.report().equals(saved))
            throw new IllegalArgumentException("Complete shared feedback replay differs");
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var input = readRequest(request);
        var saved = readReport(report);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("Shared feedback request differs");
        var owned = solve(input);
        verifyReport(saved, owned);
        return owned;
    }
}
