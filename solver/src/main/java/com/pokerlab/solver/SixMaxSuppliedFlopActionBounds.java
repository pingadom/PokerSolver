package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete-parent numerical security-face bounds; never a trainer admission handle. */
public final class SixMaxSuppliedFlopActionBounds {
    public static final String SCHEMA = "pokerlab-supplied-flop-action-bounds/v1";
    public static final String REACH_UNITS =
            "COUNTERFACTUAL_QUESTION_GIVEN_PHYSICAL_FLOP_HERO_PAST_CANCELLED/v1";
    public static final double SECURITY_SLACK = 1e-8;
    public static final double CONDITIONAL_REACH_FLOOR = .0001;
    public static final int MAX_QUESTIONS = 16;

    public record Incidence(
            int originalDealIndex,
            double originalJointProbability,
            double physicalFlopProbability) {}

    public record Action(
            String action,
            double lowerUtilityBb,
            double upperUtilityBb,
            double lowerLossBb,
            double upperLossBb,
            double upperObjectiveShift,
            double rawCertifiedReachLowerBound,
            double rawCertifiedReachUpperBound,
            FiniteTwoPlayerConditionalActionIntervals.Audit diagnostic) {}

    public record Decision(int hero, String informationSet, List<Action> actions) {
        public Decision {
            actions = List.copyOf(actions);
        }
    }

    public record Reference(
            CfrSolution policy,
            String solutionHash,
            MultiPlayerInformationSetBestResponse.Report fullParentQuality,
            SixMaxSuppliedFlopBettingStudy.LocalAudit reachedFlopQuality) {}

    public record Report(
            String schemaVersion,
            boolean trainerAdmission,
            String reachUnits,
            SixMaxSuppliedFlopBettingStudy.Report parent,
            String publicHistory,
            double physicalFlopMass,
            double rawRequiredMinimumReach,
            List<Incidence> physicalIncidence,
            List<Decision> decisions,
            List<Reference> freshReferences) {
        public Report {
            physicalIncidence = List.copyOf(physicalIncidence);
            decisions = List.copyOf(decisions);
            freshReferences = List.copyOf(freshReferences);
            if (!SCHEMA.equals(schemaVersion)
                    || trainerAdmission
                    || !REACH_UNITS.equals(reachUnits)
                    || parent == null
                    || publicHistory == null
                    || !(physicalFlopMass > 0)
                    || !Double.isFinite(physicalFlopMass)
                    || rawRequiredMinimumReach != physicalFlopMass * CONDITIONAL_REACH_FLOOR
                    || decisions.isEmpty()
                    || decisions.size() > MAX_QUESTIONS
                    || freshReferences.size() != 2)
                throw new IllegalArgumentException("Invalid flop action bounds identity");
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

    private SixMaxSuppliedFlopActionBounds() {}

    public static Result solve(SixMaxSuppliedFlopBettingStudy.Request request) throws Exception {
        return solve(
                request,
                new SixMaxSuppliedFlopBettingGame(
                        request.input(), request.flop(), request.betPotFraction()));
    }

    static Result solve(
            SixMaxSuppliedFlopBettingStudy.Request request, SixMaxSuppliedFlopBettingGame game)
            throws Exception {
        var handoff = game.handoffs().getFirst();
        var questions = new TreeMap<String, Integer>();
        var menus = new TreeMap<String, List<String>>();
        var compatible = new HashSet<Integer>();
        for (var world : handoff.worlds()) compatible.add(world.originalDealIndex());
        for (var deal : game.chanceOutcomes(game.initialState())) {
            if (!compatible.contains(deal.state().originalDealIndex())) continue;
            var state = deal.state();
            for (var action : handoff.continuationActions())
                state = game.afterAction(state, action);
            state = game.chanceOutcomes(state).getFirst().state();
            int hero = game.currentPlayer(state);
            String key = hero + ":" + game.informationSet(state);
            questions.put(key, hero);
            menus.put(key, game.legalActions(state));
        }
        if (questions.isEmpty() || questions.size() > MAX_QUESTIONS)
            throw new IllegalArgumentException("Flop question budget exceeded");
        var parent = SixMaxSuppliedFlopBettingStudy.solve(request, game).report();
        var decisions = new ArrayList<Decision>();
        for (var question : questions.entrySet()) {
            var audits = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
            var lowers = new ArrayList<Double>();
            var uppers = new ArrayList<Double>();
            for (var action : menus.get(question.getKey())) {
                var owned =
                        FiniteTwoPlayerConditionalActionIntervals.solvePhysicalFlop(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        question.getValue(), question.getKey(), action),
                                SECURITY_SLACK,
                                CONDITIONAL_REACH_FLOOR);
                double shift = owned.context().upperObjectiveShift();
                // Use original dual bounds, then pad by the declared numerical certificate
                // tolerance.
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
                    throw new IllegalStateException("Invalid padded original-dual utility bounds");
                audits.add(owned);
                lowers.add(lower);
                uppers.add(upper);
            }
            double bestLower = lowers.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            double bestUpper = uppers.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            var actions = new ArrayList<Action>();
            for (int i = 0; i < audits.size(); i++) {
                var owned = audits.get(i);
                var audit = owned.audit();
                actions.add(
                        new Action(
                                audit.question().action(),
                                lowers.get(i),
                                uppers.get(i),
                                Math.max(0, bestLower - uppers.get(i)),
                                Math.max(0, bestUpper - lowers.get(i)),
                                owned.context().upperObjectiveShift(),
                                handoff.physicalFlopMass() * audit.certifiedReachLowerBound(),
                                handoff.physicalFlopMass() * audit.certifiedReachUpperBound(),
                                audit));
            }
            decisions.add(new Decision(question.getValue(), question.getKey(), actions));
        }
        var references = new ArrayList<Reference>();
        for (int iterations : List.of(2000, 4000)) {
            var policy =
                    new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            references.add(
                    new Reference(
                            policy,
                            SixMaxConnectedPostflopAudit.solutionHash(policy),
                            MultiPlayerInformationSetBestResponse.assess(game, policy),
                            SixMaxSuppliedFlopBettingStudy.localAudit(game, handoff, policy)));
        }
        var incidence =
                game.source().prior().stream()
                        .map(
                                w ->
                                        new Incidence(
                                                w.dealIndex(),
                                                w.conditionalProbability(),
                                                compatible.contains(w.dealIndex())
                                                        ? 1.0
                                                                / ExactDeadCardFlopShowdown
                                                                        .FLOPS_PER_DEAL
                                                        : 0))
                        .toList();
        return new Result(
                new Report(
                        SCHEMA,
                        false,
                        REACH_UNITS,
                        parent,
                        handoff.publicHistory(),
                        handoff.physicalFlopMass(),
                        handoff.physicalFlopMass() * CONDITIONAL_REACH_FLOOR,
                        incidence,
                        decisions,
                        references));
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SixMaxSuppliedFlopBettingStudy.MAX_BYTES)
            throw new IllegalArgumentException("Expanded action bounds report exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > SixMaxSuppliedFlopBettingStudy.MAX_BYTES)
            throw new IllegalArgumentException("Stored action bounds report exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        report, SixMaxSuppliedFlopBettingStudy.MAX_BYTES),
                                Report.class);
        var input = SixMaxSuppliedFlopBettingStudy.readRequest(request);
        if (!input.equals(saved.parent().request()))
            throw new IllegalArgumentException("Flop action bounds request lineage differs");
        var expected = solve(input);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Physical flop action bounds replay differs");
        return expected;
    }
}
