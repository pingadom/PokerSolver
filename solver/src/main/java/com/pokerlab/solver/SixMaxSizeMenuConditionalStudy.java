package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete three-action conditional intervals on the owned size-menu opponent security face. */
public final class SixMaxSizeMenuConditionalStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-size-menu-conditional-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-size-menu-conditional-report/v1";
    public static final String STATUS = "SIZE_MENU_CONDITIONAL_INTERVAL_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            int hero,
            String heroCards,
            String publicPreflopHistory,
            List<String> flopActions) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || hero != 5
                    || !"|BB:call".equals(publicPreflopHistory)
                    || flopActions == null
                    || !flopActions.isEmpty()
                    || heroCards == null
                    || parent.input().worlds().stream()
                            .noneMatch(w -> w.dealtCombos().get(hero).equals(heroCards)))
                throw new IllegalArgumentException("Initial BB size-menu question required");
            flopActions = List.copyOf(flopActions);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String evScope,
            String reachUnits,
            String informationSet,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            SixMaxSuppliedFlopSizeMenuGame.PublicState context,
            List<SixMaxSuppliedFlopActionBounds.Action> actions) {
        public Report {
            actions = List.copyOf(actions);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || parent == null
                    || context == null
                    || !request.parent().equals(parent.request())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedFlopActionBounds.REACH_UNITS.equals(reachUnits)
                    || informationSet == null
                    || !informationSet.startsWith(
                            request.hero() + ":" + SixMaxSuppliedFlopSizeMenuGame.FLOP_KEY)
                    || context.actingSeat().ordinal() != request.hero()
                    || !context.publicPreflopHistory().equals(request.publicPreflopHistory())
                    || !context.flopHistory().stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(request.flopActions())
                    || actions.size() != context.actions().size()
                    || !actions.stream()
                            .map(a -> a.action())
                            .toList()
                            .equals(context.actions().stream().map(a -> a.action()).toList()))
                throw new IllegalArgumentException("Invalid size-menu conditional report");
            for (var action : actions) {
                var audit = action.diagnostic();
                if (audit == null
                        || audit.trainerAdmission()
                        || !FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM
                                .equals(audit.algorithm())
                        || !parent.solve().equals(audit.baseline())
                        || !audit.question()
                                .equals(
                                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                request.hero(), informationSet, action.action()))
                        || audit.securitySlack() != SixMaxSuppliedFlopActionBounds.SECURITY_SLACK
                        || audit.requiredMinimumReach()
                                != SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                    throw new IllegalArgumentException("Size-menu interval provenance differs");
            }
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

    record OwnedQuestion(String key, SixMaxSuppliedFlopSizeMenuGame.PublicState context) {}

    private SixMaxSizeMenuConditionalStudy() {}

    static OwnedQuestion question(SixMaxSuppliedFlopSizeMenuGame game, Request request) {
        OwnedQuestion selected = null;
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(request.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow();
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(request.hero()).equals(request.heroCards())) continue;
            var state =
                    new SixMaxSuppliedFlopBettingGame.State(
                            world.originalDealIndex(),
                            request.publicPreflopHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            var found =
                    new OwnedQuestion(
                            request.hero() + ":" + game.informationSet(state),
                            game.publicFlopState(state));
            if (selected != null && !selected.equals(found))
                throw new IllegalArgumentException("Inconsistent size-menu information set");
            selected = found;
        }
        if (selected == null) throw new IllegalArgumentException("No physical hero support");
        return selected;
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var p = request.parent();
        return solve(request, new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        if (!request.parent().input().equals(game.source().input())
                || !request.parent().flop().equals(game.binding().physicalCarrier().flop())
                || !request.parent().menu().equals(game.binding().menu()))
            throw new IllegalArgumentException("Owned size-menu lineage differs");
        var question = question(game, request);
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(request.parent(), game).report();
        var owned = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Result>();
        var lower = new ArrayList<Double>();
        var upper = new ArrayList<Double>();
        // One declared mode and one attempt per action. No strategy, world or action is removed.
        for (var move : question.context().actions()) {
            var result =
                    FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPhysicalFlop(
                            game,
                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                    request.hero(), question.key(), move.action()),
                            SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                            SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR);
            double shift = result.context().upperObjectiveShift();
            double lo =
                    -shift
                            - result.audit().lowerWitness().certificate().dualValue()
                            - BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            double hi =
                    result.audit().upperWitnesses().stream()
                                    .mapToDouble(w -> w.certificate().dualValue() - shift)
                                    .max()
                                    .orElseThrow()
                            + BoundedLinearProgram.CERTIFICATE_TOLERANCE;
            if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi)
                throw new IllegalStateException("Invalid original-dual size-menu bounds");
            owned.add(result);
            lower.add(lo);
            upper.add(hi);
        }
        double bestLower = Collections.max(lower), bestUpper = Collections.max(upper);
        double mass = game.handoffs().getFirst().physicalFlopMass();
        var actions = new ArrayList<SixMaxSuppliedFlopActionBounds.Action>();
        for (int i = 0; i < owned.size(); i++) {
            var result = owned.get(i);
            var audit = result.audit();
            actions.add(
                    new SixMaxSuppliedFlopActionBounds.Action(
                            audit.question().action(),
                            lower.get(i),
                            upper.get(i),
                            Math.max(0, bestLower - upper.get(i)),
                            Math.max(0, bestUpper - lower.get(i)),
                            result.context().upperObjectiveShift(),
                            mass * audit.certifiedReachLowerBound(),
                            mass * audit.certifiedReachUpperBound(),
                            audit));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        question.key(),
                        parent,
                        question.context(),
                        actions));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded conditional report cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored conditional report cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(input, report));
        var request = readRequest(input);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(report, MAX_BYTES),
                                Report.class);
        if (!saved.request().equals(request))
            throw new IllegalArgumentException("Conditional request differs");
        var owned = solve(request);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Physical size-menu conditional replay differs");
        return owned;
    }
}
