package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Four explicit BTN contexts, one complete parent and two shared fresh reference runs. */
public final class SixMaxBtnFlopCurriculum {
    public static final String REQUEST_SCHEMA = "pokerlab-four-hand-btn-flop-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-four-hand-btn-flop-report/v1";
    public static final String STATUS = "FOUR_BTN_FLOP_CONTEXTS_QUALIFIED_FEEDBACK_RESEARCH_ONLY";
    public static final String SELECTION = "ALL_FOUR_SUPPLIED_BTN_HANDS_AFTER_BB_CHECK/v1";
    public static final List<String> HANDS = List.of("9s Js", "Ah Kh", "As Qs", "Qh Th");
    public static final int MAX_BYTES = SixMaxSuppliedFlopBettingStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            String selection,
            SixMaxSuppliedFlopBettingStudy.Request connected,
            String intervalAlgorithm,
            SixMaxBtnFlopFeedback.ReferenceBudget referenceBudget,
            List<String> heroHands,
            String publicPreflopHistory,
            List<String> flopActions,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !SELECTION.equals(selection)
                    || connected == null
                    || referenceBudget == null
                    || !FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_REFINED_DIRECTION_ALGORITHM
                            .equals(intervalAlgorithm)
                    || !HANDS.equals(heroHands)
                    || !"|BB:call".equals(publicPreflopHistory)
                    || !List.of("check").equals(flopActions)
                    || !connected.input().worlds().stream()
                            .map(w -> w.dealtCombos().get(3))
                            .distinct()
                            .sorted()
                            .toList()
                            .equals(HANDS))
                throw new IllegalArgumentException("Unsupported four-hand BTN curriculum request");
            heroHands = List.copyOf(heroHands);
            flopActions = List.copyOf(flopActions);
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Decision(
            String heroCards,
            String informationSet,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references,
            boolean qualified,
            List<String> rejectionReasons) {
        public Decision {
            actions = List.copyOf(actions);
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!HANDS.contains(heroCards)
                    || informationSet == null
                    || context == null
                    || context.actingSeat().ordinal() != 3
                    || !context.publicPreflopHistory().equals("|BB:call")
                    || !context.flopHistory().stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(List.of("check"))
                    || !context.legalActions().equals(List.of("check", "bet"))
                    || !actions.stream()
                            .map(a -> a.action())
                            .toList()
                            .equals(context.legalActions())
                    || !moves.stream().map(m -> m.action()).toList().equals(context.legalActions())
                    || primary == null
                    || !references.stream()
                            .map(Check::iterations)
                            .toList()
                            .equals(List.of(80_000, 160_000))
                    || qualified != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid BTN curriculum decision");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String evScope,
            String reachUnits,
            SixMaxSuppliedFlopBettingStudy.Report parent,
            List<CfrSolution> references,
            List<Decision> decisions,
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
                    || parent == null
                    || !request.connected().equals(parent.request())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedFlopActionBounds.REACH_UNITS.equals(reachUnits)
                    || !references.stream()
                            .map(CfrSolution::iterations)
                            .toList()
                            .equals(request.referenceBudget().iterations())
                    || !decisions.stream()
                            .map(Decision::heroCards)
                            .toList()
                            .equals(request.heroHands())
                    || uniquePoliciesChecked != 3
                    || policyContextChecks != 12
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty()
                    || qualifiedForOfflineDrill != decisions.stream().allMatch(Decision::qualified))
                throw new IllegalArgumentException("Invalid four-hand BTN report identity");
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

    private record OwnedIntervals(
            SixMaxBtnFlopFeedback.OwnedQuestion question,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {}

    private SixMaxBtnFlopCurriculum() {}

    public static Result solve(Request request) throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        var parent = SixMaxSuppliedFlopBettingStudy.solve(connected, game).report();
        var intervals = new ArrayList<OwnedIntervals>();
        // Every declared context and endpoint is owned before spending the shared CFR budget.
        for (String hand : request.heroHands()) intervals.add(intervals(game, request, hand));
        var traversal = new CachedTwoPlayerCfrGame(game);
        var references = new ArrayList<CfrSolution>();
        for (int iterations : request.referenceBudget().iterations()) {
            // New solver for EACH run; no checkpoint, imported strategy or previous regret table.
            references.add(
                    new MultiPlayerCfrSolver<>(traversal, CfrSolver.Variant.CFR_PLUS)
                            .solve(iterations));
        }
        var decisions = new ArrayList<Decision>();
        for (int i = 0; i < intervals.size(); i++) {
            var item = intervals.get(i);
            String hand = request.heroHands().get(i);
            var primary =
                    SixMaxBtnFlopFeedback.assess(
                            game,
                            hand,
                            request.publicPreflopHistory(),
                            item.actions(),
                            parent.candidate());
            var checks =
                    references.stream()
                            .map(
                                    policy ->
                                            SixMaxBtnFlopFeedback.assess(
                                                    game,
                                                    hand,
                                                    request.publicPreflopHistory(),
                                                    item.actions(),
                                                    policy))
                            .toList();
            var failures = decisionFailures(item.moves(), primary, checks);
            decisions.add(
                    new Decision(
                            hand,
                            item.question().informationSet(),
                            item.question().context(),
                            item.actions(),
                            item.moves(),
                            primary,
                            checks,
                            failures.isEmpty(),
                            failures));
        }
        return new Result(assemble(request, parent, references, decisions));
    }

    private static OwnedIntervals intervals(
            SixMaxSuppliedFlopBettingGame game, Request request, String hand) throws Exception {
        var question = SixMaxBtnFlopFeedback.question(game, hand, request.publicPreflopHistory());
        var audits = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
        var lows = new ArrayList<Double>();
        var highs = new ArrayList<Double>();
        for (String action : question.context().legalActions()) {
            var owned =
                    FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlopRefinedDirections(
                            game,
                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                    3, question.informationSet(), action),
                            SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                            SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR);
            double shift = owned.context().upperObjectiveShift();
            double lower =
                    -shift
                            - owned.audit().lowerWitness().certificate().dualValue()
                            - BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            double upper =
                    owned.audit().upperWitnesses().stream()
                                    .mapToDouble(w -> w.certificate().dualValue() - shift)
                                    .max()
                                    .orElseThrow()
                            + BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper)
                throw new IllegalStateException("Invalid original BTN curriculum dual bounds");
            audits.add(owned);
            lows.add(lower);
            highs.add(upper);
        }
        double bestLow = Collections.max(lows), bestHigh = Collections.max(highs);
        var actions = new ArrayList<SixMaxSuppliedFlopActionBounds.Action>();
        var moves = new ArrayList<SixMaxSuppliedIntervalFeedback.Move>();
        for (int i = 0; i < audits.size(); i++) {
            var owned = audits.get(i);
            var audit = owned.audit();
            double lowerLoss = Math.max(0, bestLow - highs.get(i)),
                    upperLoss = Math.max(0, bestHigh - lows.get(i));
            actions.add(
                    new SixMaxSuppliedFlopActionBounds.Action(
                            audit.question().action(),
                            lows.get(i),
                            highs.get(i),
                            lowerLoss,
                            upperLoss,
                            owned.context().upperObjectiveShift(),
                            question.handoff().physicalFlopMass()
                                    * audit.certifiedReachLowerBound(),
                            question.handoff().physicalFlopMass()
                                    * audit.certifiedReachUpperBound(),
                            audit));
            moves.add(
                    new SixMaxSuppliedIntervalFeedback.Move(
                            audit.question().action(),
                            lows.get(i),
                            highs.get(i),
                            lowerLoss,
                            upperLoss,
                            SixMaxSuppliedIntervalFeedback.classify(
                                    lowerLoss, upperLoss, request.maximumLossBb())));
        }
        return new OwnedIntervals(question, List.copyOf(actions), List.copyOf(moves));
    }

    static List<String> decisionFailures(
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references) {
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

    private static Report assemble(
            Request request,
            SixMaxSuppliedFlopBettingStudy.Report parent,
            List<CfrSolution> references,
            List<Decision> decisions) {
        var failures = new TreeSet<String>();
        decisions.forEach(
                d -> d.rejectionReasons().forEach(f -> failures.add(d.heroCards() + ":" + f)));
        return new Report(
                REPORT_SCHEMA,
                STATUS,
                false,
                request,
                SixMaxSuppliedFlopFeedback.EV_SCOPE,
                SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                parent,
                references,
                decisions,
                3,
                12,
                failures.isEmpty(),
                List.copyOf(failures));
    }

    static void verifyReport(Report saved, Result owned) {
        if (owned == null || !owned.report().equals(saved))
            throw new IllegalArgumentException("Physical four-hand BTN replay differs");
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
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded BTN curriculum exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored BTN curriculum exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(report);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("BTN curriculum request differs");
        var owned = solve(request);
        verifyReport(saved, owned);
        return owned;
    }
}
