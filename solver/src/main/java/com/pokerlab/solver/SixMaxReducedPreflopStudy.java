package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSizeMenuBtnStudy.Utility;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One complete preflop root menu on the unchanged joint game's full opponent security face. */
public final class SixMaxReducedPreflopStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-reduced-preflop-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-reduced-preflop-report/v1";
    public static final String STATUS = "REDUCED_PREFLOP_INTERVAL_RESEARCH_ONLY";
    public static final String REACH_UNITS = "ORIGINAL_JOINT_QUESTION_REACH_HERO_PAST_CANCELLED/v1";

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            int hero,
            String heroCards) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || hero != 5
                    || !"9d 9h".equals(heroCards))
                throw new IllegalArgumentException("Declared BB99 root study required");
            rootQuestion(parent, hero, heroCards);
        }
    }

    public record Failure(
            String kind,
            String reason,
            String detail,
            FiniteTwoPlayerConditionalActionIntervals.ReachFailureAudit reachFailure,
            BigInteger cartesianPlans,
            BigInteger reducedPlans) {
        public Failure {
            if (!Set.of("STRUCTURE", "CONDITIONAL", "LP", "PARENT").contains(kind)
                    || reason == null
                    || detail == null
                    || (kind.equals("STRUCTURE")
                            ? cartesianPlans == null || reducedPlans == null
                            : cartesianPlans != null || reducedPlans != null))
                throw new IllegalArgumentException("Typed original rejection required");
        }
    }

    public record Endpoint(String action, Utility utility, Failure rejection) {
        public Endpoint {
            if (!List.of("fold", "call", "raise:9.0").contains(action)
                    || (utility == null) == (rejection == null)
                    || (utility != null
                            && !action.equals(utility.diagnostic().question().action())))
                throw new IllegalArgumentException("Complete root endpoint required");
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
            SixMaxSizeMenuPreflopPlan.Table context,
            Map<String, BigInteger> cartesianPlanCounts,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            List<Endpoint> endpoints,
            boolean complete) {
        public Report {
            endpoints = List.copyOf(endpoints);
            cartesianPlanCounts = Collections.unmodifiableMap(new TreeMap<>(cartesianPlanCounts));
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || parent == null
                    || !parent.request().equals(request.parent())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !REACH_UNITS.equals(reachUnits))
                throw new IllegalArgumentException("Reduced root report identity differs");
            var q = rootQuestion(request.parent(), request.hero(), request.heroCards());
            var counts = new TreeMap<String, BigInteger>();
            q.actions().forEach(a -> counts.put(a.action(), a.cartesianPlans()));
            if (!q.informationSet().equals(informationSet)
                    || !q.context().equals(context)
                    || !counts.equals(cartesianPlanCounts)
                    || !endpoints.stream()
                            .map(Endpoint::action)
                            .toList()
                            .equals(q.actions().stream().map(a -> a.action()).toList())
                    || complete != endpoints.stream().allMatch(e -> e.utility() != null))
                throw new IllegalArgumentException("Complete original root context required");
            for (var e : endpoints)
                if (e.utility() != null) {
                    var a = e.utility().diagnostic();
                    if (!a.algorithm()
                                    .equals(
                                            FiniteTwoPlayerConditionalActionIntervals
                                                    .PREFLOP_ROOT_REDUCED_ALGORITHM)
                            || a.trainerAdmission()
                            || !a.baseline().equals(parent.solve())
                            || !a.fixedHeroPast().isEmpty()
                            || !a.question()
                                    .equals(
                                            new FiniteTwoPlayerConditionalActionIntervals.Question(
                                                    request.hero(), informationSet, e.action()))
                            || a.securitySlack() != SixMaxSuppliedFlopActionBounds.SECURITY_SLACK
                            || a.requiredMinimumReach()
                                    != SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                        throw new IllegalArgumentException("Reduced endpoint provenance differs");
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

    private SixMaxReducedPreflopStudy() {}

    static SixMaxSizeMenuPreflopPlan.Question rootQuestion(
            SixMaxSuppliedFlopSizeMenuStudy.Request parent, int hero, String hand) {
        return SixMaxSizeMenuPreflopPlan.plan(parent).report().questions().stream()
                .filter(
                        q ->
                                q.hero().ordinal() == hero
                                        && q.heroCards().equals(hand)
                                        && q.context().publicHistory().isEmpty()
                                        && q.actions().stream()
                                                .map(a -> a.action())
                                                .toList()
                                                .equals(List.of("fold", "call", "raise:9.0")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Owned BB root menu required"));
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var p = request.parent();
        return solve(request, new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        var p = request.parent();
        if (!p.input().equals(game.source().input())
                || !p.flop().equals(game.binding().physicalCarrier().flop())
                || !p.menu().equals(game.binding().menu()))
            throw new IllegalArgumentException("Owned reduced root game differs");
        var q = rootQuestion(p, request.hero(), request.heroCards());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        var endpoints = new ArrayList<Endpoint>();
        for (var a : q.actions()) {
            try {
                var owned =
                        FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPreflopRootReduced(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        request.hero(), q.informationSet(), a.action()),
                                SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                                SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR);
                var audit = owned.audit();
                double shift = owned.context().upperObjectiveShift();
                double lo =
                        -shift
                                - audit.lowerWitness().certificate().dualValue()
                                - BoundedLinearProgram.CERTIFICATE_TOLERANCE;
                double hi =
                        audit.upperWitnesses().stream()
                                        .mapToDouble(w -> w.certificate().dualValue() - shift)
                                        .max()
                                        .orElseThrow()
                                + BoundedLinearProgram.CERTIFICATE_TOLERANCE;
                endpoints.add(
                        new Endpoint(
                                a.action(),
                                new Utility(
                                        lo,
                                        hi,
                                        shift,
                                        audit.certifiedReachLowerBound(),
                                        audit.certifiedReachUpperBound(),
                                        audit),
                                null));
            } catch (ReducedHeroContinuations.PlanLimit f) {
                endpoints.add(
                        new Endpoint(
                                a.action(),
                                null,
                                new Failure(
                                        "STRUCTURE",
                                        "CONTINUATION_PLAN_LIMIT",
                                        f.getMessage(),
                                        null,
                                        f.cartesianPlans(),
                                        f.reducedPlans())));
            } catch (FiniteTwoPlayerConditionalActionIntervals.Rejected f) {
                endpoints.add(
                        new Endpoint(
                                a.action(),
                                null,
                                new Failure(
                                        "CONDITIONAL",
                                        f.reason().name(),
                                        f.getMessage(),
                                        f.reachFailure().orElse(null),
                                        null,
                                        null)));
            } catch (BoundedLinearProgram.Rejected f) {
                endpoints.add(
                        new Endpoint(
                                a.action(),
                                null,
                                new Failure(
                                        "LP",
                                        f.reason().name(),
                                        f.getMessage(),
                                        null,
                                        null,
                                        null)));
            } catch (FiniteTwoPlayerAffineSequenceForm.Rejected f) {
                endpoints.add(
                        new Endpoint(
                                a.action(),
                                null,
                                new Failure(
                                        "PARENT",
                                        f.reason().name(),
                                        f.getMessage(),
                                        null,
                                        null,
                                        null)));
            }
        }
        var counts = new TreeMap<String, BigInteger>();
        q.actions().forEach(a -> counts.put(a.action(), a.cartesianPlans()));
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        REACH_UNITS,
                        q.informationSet(),
                        q.context(),
                        counts,
                        parent,
                        endpoints,
                        endpoints.stream().allMatch(e -> e.utility() != null)));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                path, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                        Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Expanded reduced report cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Stored reduced report cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path output) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        var request = readRequest(input);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        output, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Reduced request differs");
        var owned = solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete reduced physical replay differs");
        return owned;
    }
}
