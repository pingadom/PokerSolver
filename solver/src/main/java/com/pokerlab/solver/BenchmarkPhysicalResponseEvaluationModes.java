package com.pokerlab.solver;

import java.util.Locale;

/** Compares held-out estimators for the same fixed full-deck response at equal rollout counts. */
public final class BenchmarkPhysicalResponseEvaluationModes {
    private BenchmarkPhysicalResponseEvaluationModes() {}

    public static void main(String[] args) {
        if (args.length > 7)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalResponseEvaluationModes [baseline-iterations] [response-iterations] [held-out-trials] [seed] [3x3|5x5] [average|final] [replications]");
        int baselineIterations = args.length >= 1 ? Integer.parseInt(args[0]) : 3_000;
        int responseIterations = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        int trials = args.length >= 3 ? Integer.parseInt(args[2]) : 18_000;
        long seed = args.length >= 4 ? Long.parseLong(args[3]) : 42;
        var profile =
                args.length >= 5
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        String variant = args.length >= 6 ? args[5] : "final";
        int replications = args.length == 7 ? Integer.parseInt(args[6]) : 8;
        if (!variant.equals("average") && !variant.equals("final"))
            throw new IllegalArgumentException("Policy variant must be average or final");
        if (replications < 2 || replications > 100)
            throw new IllegalArgumentException("Replications must be in 2-100");
        if (baselineIterations < 1 || baselineIterations > 100_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-100000");
        if (responseIterations < 1 || responseIterations > 1_000_000)
            throw new IllegalArgumentException("Response iterations must be in 1-1000000");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        int rootDeals = game.chanceOutcomes(game.initialState()).size();
        if (trials < 2 * rootDeals || trials > 1_000_000 || trials % rootDeals != 0)
            throw new IllegalArgumentException(
                    "Held-out trials must be a multiple of root deals with at least two batches");
        var baseline =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed + 100_003)
                        .solve(baselineIterations);
        System.out.printf(
                Locale.ROOT,
                "Response evaluation %s: game %s, baseline %d, response %d, variant %s, %d root deals, %d trajectories per replication per estimator, %d evaluation seeds, base seed %d%n",
                profile,
                game.contentHash(),
                baselineIterations,
                responseIterations,
                variant,
                rootDeals,
                trials,
                replications,
                seed);
        for (int target = 0; target <= 1; target++) {
            var trained =
                    new FixedOpponentResponseCfr<>(game, baseline, target, seed + 200_003 + target)
                            .solve(responseIterations);
            var policy =
                    variant.equals("average") ? trained.response() : trained.finalRegretPolicy();
            for (var mode : PhysicalResponseHeldOutAudit.EvaluationMode.values()) {
                long started = System.nanoTime();
                var gains = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
                var reportedErrors = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
                int batches = 0;
                long fallbackPaths = 0;
                for (int replication = 0; replication < replications; replication++) {
                    long evaluationSeed = seed + 300_003 + target + 1_000L * replication;
                    var report =
                            PhysicalResponseHeldOutAudit.assess(
                                    game, baseline, policy, target, trials, evaluationSeed, mode);
                    gains.add(report.responseGainBb());
                    reportedErrors.add(report.pairedStandardErrorBb());
                    batches = report.independentBatches();
                    fallbackPaths += report.responseFallbackTrajectories();
                }
                System.out.printf(
                        Locale.ROOT,
                        "%s %s: mean gain %+.6fbb, empirical SD across seeds %.6fbb, mean reported paired SE %.6fbb, %d independent batches per seed, %d total response fallback paths, %.2fs%n",
                        target == 0 ? "BB" : "BTN",
                        mode,
                        gains.mean(),
                        gains.standardError() * Math.sqrt(replications),
                        reportedErrors.mean(),
                        batches,
                        fallbackPaths,
                        (System.nanoTime() - started) / 1e9);
            }
        }
        System.out.println(
                "Both estimators score the same fixed trained policy with equal trajectory counts. Empirical SD uses only the listed evaluation seeds; reported paired SE conditions on the policy and covers evaluation sampling only. Neither score upper-bounds a best response or certifies exploitability.");
    }
}
