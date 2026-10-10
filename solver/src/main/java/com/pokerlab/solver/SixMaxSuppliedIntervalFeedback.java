package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Selected-question interval feedback qualification, distinct from scalar-EV pack admission. */
public final class SixMaxSuppliedIntervalFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-interval-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-interval-feedback-report/v1";
    public static final String STATUS = "SELECTED_QUESTION_INTERVAL_FEEDBACK_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedConditionalDecisionLoss.MAX_BYTES;
    private static final double TOLERANCE = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public enum Classification {
        WITHIN_LIMIT,
        OUTSIDE_LIMIT,
        STRATEGY_DEPENDENT
    }

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            int hero,
            String informationSet,
            double securitySlack,
            double minimumReach,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !Double.isFinite(maximumLossBb)
                    || maximumLossBb <= 0
                    || maximumLossBb > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                throw new IllegalArgumentException("Unsupported interval feedback request");
            new SixMaxSuppliedConditionalDecisionLoss.Request(
                    SixMaxSuppliedConditionalDecisionLoss.REQUEST_SCHEMA,
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
            Classification classification) {}

    public record ReferenceCheck(
            int iterations,
            String solutionHash,
            MultiPlayerInformationSetBestResponse.Report quality,
            double globalHeroBestResponseBb,
            double globalHeroUpperBb,
            double requiredSecuritySlackBb,
            double inclusionMarginBb,
            boolean insideDeclaredFace,
            boolean boundsChecked,
            Double maximumBoundViolationBb,
            SixMaxHeadsUpPreflopDecisionValues.Row decision,
            Map<String, Double> actionLossBb,
            List<String> failures) {
        public ReferenceCheck {
            actionLossBb = Collections.unmodifiableMap(new TreeMap<>(actionLossBb));
            failures = List.copyOf(failures);
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
            double heroCommittedBb,
            FiniteTwoPlayerConditionedDecisionLoss.Audit diagnostic,
            String primarySolutionHash,
            MultiPlayerInformationSetBestResponse.Report primaryQuality,
            SixMaxHeadsUpPreflopStudy.Decision legacySelectedDecision,
            List<String> legacyWholeStudyRejections,
            List<ReferenceCheck> referenceChecks,
            List<Move> moves,
            boolean qualifiedForIntervalFeedback,
            List<String> rejectionReasons) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || diagnostic == null
                    || primaryQuality == null
                    || legacySelectedDecision == null
                    || qualifiedForIntervalFeedback != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid interval feedback identity");
            prior = List.copyOf(prior);
            payoffs = List.copyOf(payoffs);
            legacyWholeStudyRejections = List.copyOf(legacyWholeStudyRejections);
            referenceChecks = List.copyOf(referenceChecks);
            moves = List.copyOf(moves);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    /** Only a complete owned solve or physical replay can create this capability. */
    public static final class QualifiedFeedback {
        private final Request request;
        private final String reportHash;
        private final List<Move> moves;

        private QualifiedFeedback(Report report) throws Exception {
            request = report.request();
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
            moves = List.copyOf(report.moves());
        }

        public Request request() {
            return request;
        }

        public String reportHash() {
            return reportHash;
        }

        public List<Move> moves() {
            return moves;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedFeedback feedback;

        private Result(Report report) throws Exception {
            this.report = report;
            feedback = report.qualifiedForIntervalFeedback() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(feedback);
        }
    }

    private SixMaxSuppliedIntervalFeedback() {}

    public static Classification classify(double lower, double upper, double maximumLossBb) {
        if (!Double.isFinite(lower)
                || !Double.isFinite(upper)
                || lower < 0
                || upper < lower
                || !Double.isFinite(maximumLossBb)
                || maximumLossBb <= 0
                || maximumLossBb > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
            throw new IllegalArgumentException("Invalid decision-loss classification");
        if (upper <= maximumLossBb) return Classification.WITHIN_LIMIT;
        if (lower > maximumLossBb) return Classification.OUTSIDE_LIMIT;
        return Classification.STRATEGY_DEPENDENT;
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        SixMaxSuppliedRangePreflopGame.preflight(request.input());
        return solve(new SixMaxSuppliedRangePreflopGame(request.input()), request);
    }

    static Result solve(SixMaxSuppliedRangePreflopGame game, Request request) throws Exception {
        if (!game.input().equals(request.input()))
            throw new IllegalArgumentException("Interval feedback game/input differs");
        var state =
                SixMaxSuppliedConditionalDecisionIntervals.find(
                        game, game.initialState(), request.informationSet());
        if (state == null || game.currentPlayer(state) != request.hero())
            throw new IllegalArgumentException("Interval feedback question does not exist");
        double committed =
                game.publicBettingState(state).committedBb(Seat.values()[request.hero()]);
        var diagnostic =
                FiniteTwoPlayerConditionedDecisionLoss.solve(
                                game,
                                new FiniteTwoPlayerConditionalDecisionLoss.Question(
                                        request.hero(), request.informationSet()),
                                request.securitySlack(),
                                request.minimumReach())
                        .audit();
        var joint = diagnostic.joint();
        var primary = FiniteTwoPlayerAffineSequenceForm.solve(game);
        if (!primary.audit().equals(joint.actions().getFirst().interval().baseline()))
            throw new IllegalStateException("Feedback primary and interval baselines differ");
        var policy = new CfrSolution(1, primary.strategy());
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        // Preserve the complete existing scalar screen as evidence; do not rewrite its failures.
        var legacy = SixMaxHeadsUpDecisionScreen.assess(game, policy);
        var selected =
                legacy.decisions().stream()
                        .filter(d -> d.primary().informationSet().equals(request.informationSet()))
                        .findFirst()
                        .orElseThrow();
        var failures = new TreeSet<String>();
        if (!selected.material()) failures.add("NON_MATERIAL_SELECTED_DECISION");
        if (selected.primary().values() == null
                || selected.primary().values().decisionRegretBb()
                        > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
            failures.add("PRIMARY_DECISION_REGRET");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("PRIMARY_LOCAL_GAP");
        if (legacy.rejectionReasons().contains("INSUFFICIENT_MATERIAL_PRIVATE_COMBOS"))
            failures.add("INSUFFICIENT_MATERIAL_PRIVATE_COMBOS");
        for (var comparison : selected.comparisons())
            for (String failure : comparison.failures())
                if (!failure.equals("ACTION_EV_DRIFT"))
                    failures.add(comparison.iterations() + ":" + failure);
        if (selected.comparisons().size() != SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS.size())
            failures.add("MISSING_LEGACY_REFERENCE_CHECKS");
        var moves = new ArrayList<Move>();
        for (int i = 0; i < joint.moves().size(); i++) {
            var loss = joint.moves().get(i);
            var interval = joint.actions().get(i).interval();
            moves.add(
                    new Move(
                            loss.action(),
                            interval.lowerUtility() + committed,
                            interval.upperUtility() + committed,
                            loss.lowerLoss(),
                            loss.upperLoss(),
                            classify(loss.lowerLoss(), loss.upperLoss(), request.maximumLossBb())));
        }
        if (moves.stream().noneMatch(m -> m.classification() == Classification.WITHIN_LIMIT))
            failures.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream().noneMatch(m -> m.classification() == Classification.OUTSIDE_LIMIT))
            failures.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        var references = new ArrayList<ReferenceCheck>();
        for (int budget : SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.CFR_PLUS,
                            MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
            var ref = solver.solve(budget);
            var refQuality = MultiPlayerInformationSetBestResponse.assess(game, ref);
            String hash = SixMaxConnectedPostflopAudit.solutionHash(ref);
            var original =
                    legacy.references().stream()
                            .filter(r -> r.iterations() == budget)
                            .findFirst()
                            .orElseThrow();
            if (!hash.equals(original.solutionHash()) || !refQuality.equals(original.quality()))
                throw new IllegalStateException("Fresh reference evidence differs");
            var decision =
                    SixMaxHeadsUpPreflopDecisionValues.assess(game, ref).stream()
                            .filter(d -> d.row().informationSet().equals(request.informationSet()))
                            .findFirst()
                            .orElseThrow()
                            .row();
            double br = refQuality.bestResponseUtilitiesBb().get(request.hero());
            double upper = joint.actions().getFirst().interval().globalHeroUpperValue();
            double required = Math.max(0, br - upper),
                    margin = upper + request.securitySlack() - br;
            boolean included = margin >= -TOLERANCE;
            boolean checked = included && decision.values() != null;
            Double violation = null;
            var refFailures = new ArrayList<String>();
            var losses = new TreeMap<String, Double>();
            if (!included) refFailures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
            if (refQuality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                refFailures.add("REFERENCE_LOCAL_GAP");
            if (decision.values() == null) refFailures.add("ZERO_REFERENCE_REACH");
            else {
                var evs = decision.values().actionEvBb();
                double best = Collections.max(evs.values());
                for (var move : moves)
                    losses.put(move.action(), Math.max(0, best - evs.get(move.action())));
                if (checked) {
                    double worst = 0;
                    for (var move : moves) {
                        double ev = evs.get(move.action()), loss = losses.get(move.action());
                        worst =
                                Math.max(
                                        worst,
                                        Math.max(
                                                Math.max(
                                                        move.lowerEvBb() - ev,
                                                        ev - move.upperEvBb()),
                                                Math.max(
                                                        move.lowerLossBb() - loss,
                                                        loss - move.upperLossBb())));
                    }
                    violation = worst;
                    if (worst > TOLERANCE) refFailures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
                }
            }
            refFailures.forEach(f -> failures.add(budget + ":" + f));
            references.add(
                    new ReferenceCheck(
                            budget,
                            hash,
                            refQuality,
                            br,
                            upper,
                            required,
                            margin,
                            included,
                            checked,
                            violation,
                            decision,
                            losses,
                            refFailures));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        committed,
                        diagnostic,
                        SixMaxConnectedPostflopAudit.solutionHash(policy),
                        quality,
                        selected,
                        legacy.rejectionReasons(),
                        references,
                        moves,
                        failures.isEmpty(),
                        List.copyOf(failures)));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Interval feedback report exceeds byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException(
                    "Compressed interval feedback report exceeds byte cap");
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
            throw new IllegalArgumentException("Interval feedback lineage differs");
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact interval feedback replay differs");
        return expected;
    }
}
