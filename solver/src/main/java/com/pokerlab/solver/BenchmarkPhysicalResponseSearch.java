package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible full-deck sampled response search and independent paired evaluation. */
public final class BenchmarkPhysicalResponseSearch {
    private BenchmarkPhysicalResponseSearch() {}

    public static void main(String[] args) {
        if (args.length > 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalResponseSearch [baseline-iterations] [response-iterations] [held-out-trials] [seed] [3x3|5x5]");
        int baselineIterations = args.length >= 1 ? Integer.parseInt(args[0]) : 3_000;
        int responseIterations = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        int heldOutTrials = args.length >= 3 ? Integer.parseInt(args[2]) : 50_000;
        long seed = args.length >= 4 ? Long.parseLong(args[3]) : 42;
        var profile =
                args.length == 5
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        if (baselineIterations < 1 || baselineIterations > 100_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-100000");
        if (responseIterations < 1 || responseIterations > 1_000_000)
            throw new IllegalArgumentException("Response iterations must be in 1-1000000");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        long started = System.nanoTime();
        var baseline =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed + 100_003)
                        .solve(baselineIterations);
        System.out.printf(
                Locale.ROOT,
                "Full-deck response search %s: baseline %d iterations (seed %d), %d information sets, %.2fs; response %d iterations per player, %d held-out deals per player%n",
                profile,
                baselineIterations,
                seed + 100_003,
                baseline.strategy().size(),
                (System.nanoTime() - started) / 1e9,
                responseIterations,
                heldOutTrials);
        for (int target = 0; target <= 1; target++) {
            long responseStarted = System.nanoTime();
            long responseSeed = seed + 200_003 + target;
            var trained =
                    new FixedOpponentResponseCfr<>(game, baseline, target, responseSeed)
                            .solve(responseIterations);
            double responseSeconds = (System.nanoTime() - responseStarted) / 1e9;
            var heldOut =
                    PhysicalResponseHeldOutAudit.assess(
                            game,
                            baseline,
                            trained.response(),
                            target,
                            heldOutTrials,
                            seed + 300_003 + target);
            System.out.printf(
                    Locale.ROOT,
                    "%s response: %d learned information sets in %.2fs; fixed-opponent query support %.3f%% (%d missing queries, %d missing keys); held-out baseline %+.4fbb, response %+.4fbb, gain %+.4fbb (paired SE %.4fbb, approximate 95%% [%.4f, %.4f]bb); held-out baseline missing-action visits %d, response missing-baseline visits %d, response-to-baseline visits %d, fallback trajectories %d/%d%n",
                    target == 0 ? "BB" : "BTN",
                    trained.response().strategy().size(),
                    responseSeconds,
                    100 * trained.fixedOpponentQuerySupportRate(),
                    trained.missingFixedOpponentQueries(),
                    trained.missingFixedOpponentInformationSets(),
                    heldOut.baselineTargetUtilityBb(),
                    heldOut.responseTargetUtilityBb(),
                    heldOut.responseGainBb(),
                    heldOut.pairedStandardErrorBb(),
                    heldOut.approximateGainLower95Bb(),
                    heldOut.approximateGainUpper95Bb(),
                    heldOut.baselineMissingActionDecisions(),
                    heldOut.responseMissingBaselineDecisions(),
                    heldOut.responseFallbackToBaselineDecisions(),
                    heldOut.responseFallbackTrajectories(),
                    heldOut.trials());
        }
        System.out.println(
                "Candidate response gains on held-out full-deck deals are lower-bound diagnostics, not certified best responses or a Nash-gap upper bound. Training and evaluation use explicit uniform fallback for missing baseline opponent keys and baseline fallback for missing response keys.");
    }
}
