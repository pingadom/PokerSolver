package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Four owned paired-board fold/call decisions with two shared fresh reference runs. */
public final class SixMaxPairedFlopCurriculum {
    public static final String REQUEST_SCHEMA = "pokerlab-paired-four-hand-flop-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-paired-four-hand-flop-report/v1";
    public static final String STATUS = "FOUR_PAIRED_BTN_FLOP_CONTEXTS_RESEARCH_ONLY";
    public static final String SELECTION = "ALL_FOUR_SUPPLIED_PAIRED_BTN_HANDS_FACING_BET/v1";
    public static final List<String> HANDS = SixMaxBtnFlopCurriculum.HANDS;
    public static final int MAX_BYTES = SixMaxRefinedConditionalStudy.MAX_BYTES;

    /** Structural worst-case reservations, fixed before any physical construction or solve. */
    public record WorkBudget(
            int conditionalStudies,
            long compilerUnits,
            int lpPivots,
            long lpArithmeticWork,
            int freshCfrRuns,
            int freshIterations,
            long referenceVisitedNodes) {
        public WorkBudget {
            if (conditionalStudies != 4
                    || compilerUnits != 20L * FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK
                    || lpPivots != 32 * BoundedLinearProgram.MAX_PIVOTS
                    || lpArithmeticWork != 32L * BoundedLinearProgram.MAX_ARITHMETIC_WORK
                    || freshCfrRuns != 2
                    || freshIterations != 240000
                    || referenceVisitedNodes
                            != (2L * FiniteTwoPlayerSequenceForm.MAX_TREE_NODES + 4) * 240000)
                throw new IllegalArgumentException(
                        "Fixed complete paired curriculum budget required");
        }

        public static WorkBudget bounded() {
            return new WorkBudget(
                    4,
                    20L * FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                    32 * BoundedLinearProgram.MAX_PIVOTS,
                    32L * BoundedLinearProgram.MAX_ARITHMETIC_WORK,
                    2,
                    240000,
                    (2L * FiniteTwoPlayerSequenceForm.MAX_TREE_NODES + 4) * 240000);
        }
    }

    public record Request(
            String schemaVersion,
            String selection,
            List<SixMaxRefinedConditionalStudy.Request> questions,
            SixMaxRefinedFlopFeedback.ReferenceBudget referenceBudget,
            WorkBudget workBudget,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !SELECTION.equals(selection)
                    || questions == null
                    || questions.size() != 4
                    || questions.stream().anyMatch(Objects::isNull)
                    || referenceBudget == null
                    || workBudget == null
                    || maximumLossBb != .01
                    || !questions.stream().map(q -> q.heroCards()).toList().equals(HANDS))
                throw new IllegalArgumentException("Complete ordered paired curriculum required");
            questions = List.copyOf(questions);
            var parent = questions.getFirst().parent();
            if (!parent.connected().flop().equals(List.of("8d", "Kc", "Kd"))
                    || parent.connected().betPotFraction() != .5
                    || !parent.connected().input().worlds().stream()
                            .map(w -> w.dealtCombos().get(3))
                            .distinct()
                            .sorted()
                            .toList()
                            .equals(HANDS))
                throw new IllegalArgumentException("Paired curriculum physical game differs");
            for (var question : questions)
                if (!question.parent().equals(parent)
                        || question.hero() != 3
                        || !question.publicPreflopHistory().equals("|BB:call")
                        || !question.flopActions().equals(List.of("bet"))
                        || !FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM
                                .equals(question.intervalAlgorithm()))
                    throw new IllegalArgumentException("Paired question source or path differs");
        }
    }

    public record Reference(CfrSolution policy, SixMaxOwnedFlopReferences.Audit work) {
        public Reference {
            if (policy == null
                    || work == null
                    || !List.of(80000, 160000).contains(policy.iterations())
                    || work.prunedSuffixRoots() != 4L * policy.iterations())
                throw new IllegalArgumentException("Fresh paired reference required");
        }
    }

    public record Decision(
            SixMaxRefinedConditionalStudy.Report conditional,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references,
            boolean qualified,
            List<String> rejectionReasons) {
        public Decision {
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (conditional == null
                    || primary == null
                    || !conditional.context().legalActions().equals(List.of("fold", "call"))
                    || !moves.stream().map(m -> m.action()).toList().equals(List.of("fold", "call"))
                    || !references.stream()
                            .map(Check::iterations)
                            .toList()
                            .equals(List.of(80000, 160000))
                    || qualified != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid paired curriculum decision");
        }
    }

    /** Counts include all twelve parent solves, including the eight endpoint baselines. */
    public record Work(
            int conditionalStudies,
            int parentSolves,
            int parentLpSolves,
            int endpointActions,
            int endpointLpSolves,
            long compilerUnits,
            int lpPivots,
            long lpArithmeticWork,
            int freshCfrRuns,
            int freshIterations,
            long referenceVisitedNodes) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            List<Reference> references,
            List<Decision> decisions,
            Work work,
            int uniquePoliciesChecked,
            int policyContextChecks,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            references = List.copyOf(references);
            decisions = List.copyOf(decisions);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || decisions.size() != 4
                    || !decisions.stream()
                            .map(d -> d.conditional().request())
                            .toList()
                            .equals(request.questions())
                    || !references.stream()
                            .map(r -> r.policy().iterations())
                            .toList()
                            .equals(request.referenceBudget().iterations())
                    || uniquePoliciesChecked != 3
                    || policyContextChecks != 12
                    || !Objects.equals(work, measure(decisions, references))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty()
                    || qualifiedForOfflineDrill != decisions.stream().allMatch(Decision::qualified))
                throw new IllegalArgumentException("Invalid paired curriculum report");
            withinBudget(work, request.workBudget());
            var parent = decisions.getFirst().conditional().parent();
            for (var decision : decisions) {
                if (!parent.equals(decision.conditional().parent())
                        || !parent.solutionHash().equals(decision.primary().solutionHash()))
                    throw new IllegalArgumentException("Paired shared parent differs");
                for (int i = 0; i < references.size(); i++)
                    if (references.get(i).policy().iterations()
                            != decision.references().get(i).iterations())
                        throw new IllegalArgumentException("Paired shared reference differs");
            }
        }
    }

    public static final class QualifiedFeedback {
        private final String reportHash;

        private QualifiedFeedback(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
        }

        public String reportHash() {
            return reportHash;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedFeedback feedback;

        private Result(Report report) throws Exception {
            this.report = report;
            feedback = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(feedback);
        }
    }

    private SixMaxPairedFlopCurriculum() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.questions().getFirst().parent().connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var studies = new ArrayList<SixMaxRefinedConditionalStudy.Report>();
        for (var question : request.questions())
            studies.add(SixMaxRefinedConditionalStudy.solve(question, game).report());
        // Construct one runner only after every owned interval has completed. Each train call
        // constructs a NEW solver and regret table; shared policies never mean resumed training.
        var runner = new SixMaxOwnedFlopReferences(game);
        var references = new ArrayList<Reference>();
        for (int iterations : request.referenceBudget().iterations()) {
            var run = runner.train(iterations);
            references.add(new Reference(run.policy(), run.audit()));
        }
        var decisions = new ArrayList<Decision>();
        var failures = new TreeSet<String>();
        for (var conditional : studies) {
            var moves =
                    conditional.actions().stream()
                            .map(
                                    a ->
                                            new SixMaxSuppliedIntervalFeedback.Move(
                                                    a.action(),
                                                    a.lowerUtilityBb(),
                                                    a.upperUtilityBb(),
                                                    a.lowerLossBb(),
                                                    a.upperLossBb(),
                                                    SixMaxSuppliedIntervalFeedback.classify(
                                                            a.lowerLossBb(),
                                                            a.upperLossBb(),
                                                            request.maximumLossBb())))
                            .toList();
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
            var reasons = SixMaxBtnFlopCurriculum.decisionFailures(moves, primary, checks);
            decisions.add(
                    new Decision(conditional, moves, primary, checks, reasons.isEmpty(), reasons));
            reasons.forEach(
                    reason -> failures.add(conditional.request().heroCards() + ":" + reason));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        references,
                        decisions,
                        measure(decisions, references),
                        3,
                        12,
                        failures.isEmpty(),
                        List.copyOf(failures)));
    }

    private static int pivots(BoundedLinearProgram.Work work) {
        return Math.addExact(
                Math.addExact(work.phaseOnePivots(), work.cleanupPivots()), work.phaseTwoPivots());
    }

    static Work measure(List<Decision> decisions, List<Reference> references) {
        int parents = 0, endpoints = 0, endpointLps = 0, pivots = 0;
        long compiler = 0, arithmetic = 0;
        for (var decision : decisions) {
            var baselines = new ArrayList<FiniteTwoPlayerAffineSequenceForm.Audit>();
            baselines.add(decision.conditional().parent().solve());
            for (var action : decision.conditional().actions()) {
                var audit = action.diagnostic();
                baselines.add(audit.baseline());
                endpoints++;
                endpointLps = Math.addExact(endpointLps, audit.work().intervalLpSolves());
                pivots = Math.addExact(pivots, audit.work().intervalLpPivots());
                compiler = Math.addExact(compiler, audit.work().compilerUnits());
                arithmetic = Math.addExact(arithmetic, audit.work().intervalLpArithmeticWork());
            }
            for (var baseline : baselines) {
                parents++;
                compiler = Math.addExact(compiler, baseline.reductionWork().chargedUnits());
                for (var lp : List.of(baseline.firstLp(), baseline.secondLp())) {
                    pivots = Math.addExact(pivots, pivots(lp.work()));
                    arithmetic = Math.addExact(arithmetic, lp.work().arithmeticWork());
                }
            }
        }
        int iterations = 0;
        long visits = 0;
        for (var reference : references) {
            iterations = Math.addExact(iterations, reference.policy().iterations());
            visits = Math.addExact(visits, reference.work().statistics().visitedNodes());
        }
        return new Work(
                decisions.size(),
                parents,
                2 * parents,
                endpoints,
                endpointLps,
                compiler,
                pivots,
                arithmetic,
                references.size(),
                iterations,
                visits);
    }

    static void withinBudget(Work work, WorkBudget budget) {
        if (work.conditionalStudies() != budget.conditionalStudies()
                || work.parentSolves() != 12
                || work.parentLpSolves() != 24
                || work.endpointActions() != 8
                || work.endpointLpSolves() != 32
                || work.compilerUnits() < 0
                || work.compilerUnits() > budget.compilerUnits()
                || work.lpPivots() < 0
                || work.lpPivots() > budget.lpPivots()
                || work.lpArithmeticWork() < 0
                || work.lpArithmeticWork() > budget.lpArithmeticWork()
                || work.freshCfrRuns() != budget.freshCfrRuns()
                || work.freshIterations() != budget.freshIterations()
                || work.referenceVisitedNodes() < 0
                || work.referenceVisitedNodes() > budget.referenceVisitedNodes())
            throw new IllegalArgumentException(
                    "Complete paired curriculum work exceeds declared bounds");
    }

    static void verifyReport(Report saved, Result owned) {
        if (owned == null || !owned.report().equals(saved))
            throw new IllegalArgumentException("Complete paired curriculum replay differs");
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
        path = path.toAbsolutePath().normalize();
        byte[] bytes =
                SixMaxTextureStudy.json(Objects.requireNonNull(result).report())
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded paired curriculum exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored paired curriculum exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(report);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Paired curriculum request differs");
        var owned = solve(request);
        verifyReport(saved, owned);
        return owned;
    }
}
