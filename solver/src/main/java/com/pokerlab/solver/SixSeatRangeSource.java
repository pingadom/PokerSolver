package com.pokerlab.solver;

import java.time.Instant;
import java.util.*;

/**
 * Fresh bounded finite-model source solves from owned range/payoff inputs. No trainer admission.
 */
public final class SixSeatRangeSource {
    public static final int MAX_ITERATIONS = 10_000;
    public static final long MAX_CFR_NODE_VISITS = 200_000_000;

    public record PayoffRequest(
            SixSeatPayoffWork.Method method,
            int trialsPerDeal,
            String seed,
            SixSeatPayoffWork.Budget budget) {
        public PayoffRequest {
            Objects.requireNonNull(seed, "seed");
            Objects.requireNonNull(budget, "budget");
            long value = Long.parseLong(seed);
            if (!Long.toString(value).equals(seed))
                throw new IllegalArgumentException("Canonical signed decimal seed string required");
            var c = new SixSeatPayoffWork.Configuration(method, trialsPerDeal, value);
            if (c.method() == SixSeatPayoffWork.Method.SHARED_BOARD_MONTE_CARLO
                    && trialsPerDeal < 2)
                throw new IllegalArgumentException(
                        "Source packs require at least two board trials per world");
        }

        public SixSeatPayoffWork.Configuration configuration() {
            return new SixSeatPayoffWork.Configuration(method, trialsPerDeal, Long.parseLong(seed));
        }
    }

    public record Request(
            String id,
            List<String> ranges,
            SixSeatRangePreflight.Limits preflightLimits,
            SixMaxPreflopBetting.Rules rules,
            CashRakeRule rake,
            PayoffRequest payoff,
            int iterations,
            CfrSolver.Variant variant,
            long maximumCfrNodeVisits,
            String generatedAt) {
        public Request {
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            Objects.requireNonNull(preflightLimits, "preflightLimits");
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(rake, "rake");
            Objects.requireNonNull(payoff, "payoff");
            Objects.requireNonNull(variant, "variant");
            try {
                Instant.parse(Objects.requireNonNull(generatedAt, "generatedAt"));
            } catch (java.time.DateTimeException exception) {
                throw new IllegalArgumentException(
                        "Invalid source generation timestamp", exception);
            }
            if (iterations < 1
                    || iterations > MAX_ITERATIONS
                    || maximumCfrNodeVisits < 1
                    || maximumCfrNodeVisits > MAX_CFR_NODE_VISITS)
                throw new IllegalArgumentException(
                        "Source iterations/node budget outside hard limits");
        }
    }

    public record Work(int jointDeals, int publicStates, long nodesPerPass, long cfrNodeVisits) {}

    public static final class BudgetExceeded extends IllegalArgumentException {
        private final Work required;
        private final long maximum;

        private BudgetExceeded(Work required, long maximum) {
            super("Complete exhaustive CFR work exceeds source budget before payoff execution");
            this.required = required;
            this.maximum = maximum;
        }

        public Work required() {
            return required;
        }

        public long maximum() {
            return maximum;
        }
    }

    public static final class Result {
        private final Plan plan;
        private final SixSeatPayoffWork.Result payoffs;
        private final SixMaxPreflopSolutionPack pack;
        private final MultiPlayerCfrSolver.Statistics statistics;
        private final MultiPlayerInformationSetBestResponse.Report quality;
        private final boolean reusedOwnedPayoffs;
        private final String packJson;

        private Result(
                Plan plan,
                SixSeatPayoffWork.Result payoffs,
                SixMaxPreflopSolutionPack pack,
                MultiPlayerCfrSolver.Statistics statistics,
                MultiPlayerInformationSetBestResponse.Report quality,
                boolean reused) {
            this.plan = plan;
            this.payoffs = payoffs;
            this.pack = pack;
            this.statistics = statistics;
            this.quality = quality;
            reusedOwnedPayoffs = reused;
            packJson = MultiwayPackJson.writeFullRound(pack);
        }

        public Plan plan() {
            return plan;
        }

        public SixSeatPayoffWork.Result payoffs() {
            return payoffs;
        }

        public SixMaxPreflopSolutionPack pack() {
            return pack;
        }

        public MultiPlayerCfrSolver.Statistics statistics() {
            return statistics;
        }

        public MultiPlayerInformationSetBestResponse.Report quality() {
            return quality;
        }

        public boolean reusedOwnedPayoffs() {
            return reusedOwnedPayoffs;
        }

        public String packJson() {
            return packJson;
        }

        public String packHash() {
            return MultiwayCallSpot.sha256(packJson);
        }
    }

    public static final class Plan {
        private final Request request;
        private final SixMaxPreflopResearchSpot spot;
        private final SixSeatPayoffWork.Plan payoffPlan;
        private final Work work;
        private final String contentHash;

        private Plan(Request request) {
            this.request = request;
            var input = SixSeatRangePreflight.plan(request.ranges(), request.preflightLimits());
            spot = input.toResearchSpot(request.id(), request.rules(), request.rake());
            payoffPlan =
                    SixSeatPayoffWork.plan(
                            input, request.payoff().configuration(), request.payoff().budget());
            var control =
                    spot.game(
                            (hands, mask) -> {
                                double[] shares = new double[6];
                                for (int seat = 0; seat < 6; seat++)
                                    if ((mask & (1 << seat)) != 0)
                                        shares[seat] = 1.0 / Integer.bitCount(mask);
                                return MultiwayShowdownEstimate.certain(shares);
                            });
            int publicStates = control.treeSummary().totalStates();
            long perPass =
                    Math.addExact(1, Math.multiplyExact((long) input.deals().size(), publicStates));
            work =
                    new Work(
                            input.deals().size(),
                            publicStates,
                            perPass,
                            Math.multiplyExact(
                                    Math.multiplyExact(perPass, 6), request.iterations()));
            if (work.cfrNodeVisits() > request.maximumCfrNodeVisits())
                throw new BudgetExceeded(work, request.maximumCfrNodeVisits());
            contentHash =
                    MultiwayCallSpot.sha256(
                            "six-seat-range-source/v1|EXHAUSTIVE_NO_PRUNING|"
                                    + spot.contentHash()
                                    + "|"
                                    + payoffPlan.contentHash()
                                    + "|"
                                    + request.iterations()
                                    + "|"
                                    + request.variant());
        }

        public Request request() {
            return request;
        }

        public SixMaxPreflopResearchSpot spot() {
            return spot;
        }

        public SixSeatPayoffWork.Plan payoffPlan() {
            return payoffPlan;
        }

        public Work work() {
            return work;
        }

        public String contentHash() {
            return contentHash;
        }

        public Result execute() {
            return solve(payoffPlan.execute(), false);
        }

        public Result solveWith(SixSeatPayoffWork.Result ownedPayoffs) {
            return solve(ownedPayoffs, true);
        }

        private Result solve(SixSeatPayoffWork.Result payoffs, boolean reused) {
            Objects.requireNonNull(payoffs, "payoffs");
            if (!payoffPlan.contentHash().equals(payoffs.plan().contentHash()))
                throw new IllegalArgumentException(
                        "Owned payoff source/configuration does not match this plan");
            var game = spot.game(payoffs.oracle());
            var solver = new MultiPlayerCfrSolver<>(game, request.variant());
            var solution = solver.solve(request.iterations());
            var stats = solver.statistics();
            if (stats.visitedNodes() != work.cfrNodeVisits()
                    || stats.sampledChanceNodes() != 0
                    || solver.inactiveUtilityPrunedNodes() != 0)
                throw new IllegalStateException(
                        "Exhaustive source traversal differs from planned work");
            var quality = MultiPlayerInformationSetBestResponse.assess(game, solution);
            var rows =
                    payoffs.rows().stream()
                            .map(
                                    r ->
                                            new MultiwaySolutionPack.PayoffEntry(
                                                    r.hands(), r.activeMask(), r.estimate()))
                            .toList();
            String method =
                    request.payoff().method().isExact()
                            ? MultiwaySolutionPack.EXACT_ENUMERATION
                            : SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO;
            var pack =
                    new SixMaxPreflopSolutionPack(
                            SixMaxPreflopSolutionPack.schemaFor(spot.rules()),
                            request.variant() == CfrSolver.Variant.CFR_PLUS
                                    ? MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION
                                    : MultiwaySolutionPack.SOLVER_VERSION,
                            MultiwaySolutionPack.VALIDATION_ONLY,
                            request.generatedAt(),
                            spot,
                            spot.contentHash(),
                            method,
                            Long.parseLong(request.payoff().seed()),
                            solution,
                            rows,
                            quality.nashConvBb(),
                            game.maximumTerminalPayoffStandardErrorBb());
            return new Result(this, payoffs, pack, stats, quality, reused);
        }
    }

    private SixSeatRangeSource() {}

    public static Plan plan(Request request) {
        return new Plan(Objects.requireNonNull(request, "request"));
    }
}
