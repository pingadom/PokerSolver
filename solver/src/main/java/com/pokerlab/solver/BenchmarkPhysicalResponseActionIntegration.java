package com.pokerlab.solver;

import java.util.Locale;

/** Fixed-policy full-deck comparison of sampled-action and action-integrated held-out estimates. */
public final class BenchmarkPhysicalResponseActionIntegration {
    private BenchmarkPhysicalResponseActionIntegration() {}

    public static void main(String[] args) {
        if (args.length > 7)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalResponseActionIntegration [baseline-iterations] [response-iterations] [traversals-per-seed] [seed] [3x3|5x5] [replications] [sampled-root|stratified-root]");
        int baselineIterations = args.length >= 1 ? Integer.parseInt(args[0]) : 300;
        int responseIterations = args.length >= 2 ? Integer.parseInt(args[1]) : 500;
        int traversals = args.length >= 3 ? Integer.parseInt(args[2]) : 900;
        long seed = args.length >= 4 ? Long.parseLong(args[3]) : 42;
        var profile =
                args.length >= 5
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        int replications = args.length >= 6 ? Integer.parseInt(args[5]) : 4;
        var rootMode =
                args.length == 7
                        ? switch (args[6]) {
                            case "sampled-root" ->
                                    PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT;
                            case "stratified-root" ->
                                    PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT;
                            default ->
                                    throw new IllegalArgumentException(
                                            "Root mode must be sampled-root or stratified-root");
                        }
                        : PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT;
        if (baselineIterations < 1 || baselineIterations > 100_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-100000");
        if (responseIterations < 1 || responseIterations > 1_000_000)
            throw new IllegalArgumentException("Response iterations must be in 1-1000000");
        if (replications < 2 || replications > 100)
            throw new IllegalArgumentException("Replications must be in 2-100");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        int rootDeals = game.chanceOutcomes(game.initialState()).size();
        if (traversals < 2
                || traversals > 100_000
                || (rootMode == PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT
                        && (traversals < 2 * rootDeals || traversals % rootDeals != 0)))
            throw new IllegalArgumentException(
                    "Invalid traversal count for the selected root mode");
        var baseline =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed + 100_003)
                        .solve(baselineIterations);
        System.out.printf(
                Locale.ROOT,
                "Action integration %s: game %s, baseline %d, response %d, final-regret policy, %d private-deal traversals per seed, %d evaluation seeds, root mode %s%n",
                profile,
                game.contentHash(),
                baselineIterations,
                responseIterations,
                traversals,
                replications,
                rootMode);
        for (int target = 0; target <= 1; target++) {
            var response =
                    new FixedOpponentResponseCfr<>(game, baseline, target, seed + 200_003 + target)
                            .solve(responseIterations)
                            .finalRegretPolicy();
            var sampledGains = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
            var integratedGains = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
            var sampledErrors = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
            var integratedErrors = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
            double sampledSeconds = 0;
            double integratedSeconds = 0;
            double integratedFallback = 0;
            double integratedMissingResponse = 0;
            double completionUpper = 0;
            for (int replication = 0; replication < replications; replication++) {
                long evaluationSeed = seed + 300_003 + target + 1_000L * replication;
                long started = System.nanoTime();
                var sampled =
                        PhysicalResponseHeldOutAudit.assess(
                                game,
                                baseline,
                                response,
                                target,
                                traversals,
                                evaluationSeed,
                                rootMode);
                sampledSeconds += (System.nanoTime() - started) / 1e9;
                started = System.nanoTime();
                var integrated =
                        PhysicalResponseActionIntegratedAudit.assess(
                                game,
                                baseline,
                                response,
                                target,
                                traversals,
                                evaluationSeed,
                                rootMode);
                integratedSeconds += (System.nanoTime() - started) / 1e9;
                sampledGains.add(sampled.responseGainBb());
                integratedGains.add(integrated.responseGainBb());
                sampledErrors.add(sampled.pairedStandardErrorBb());
                integratedErrors.add(integrated.pairedStandardErrorBb());
                integratedFallback += integrated.responseFallbackPathProbability();
                integratedMissingResponse += integrated.responseMissingPathProbability();
                completionUpper += integrated.completionGainUpperBb();
            }
            print(
                    target,
                    "sampled actions",
                    sampledGains,
                    sampledErrors,
                    sampledSeconds / replications,
                    replications);
            print(
                    target,
                    "integrated actions",
                    integratedGains,
                    integratedErrors,
                    integratedSeconds / replications,
                    replications);
            System.out.printf(
                    Locale.ROOT,
                    "  integrated any-fallback probability %.4f, missing-response probability %.4f, completion-only upper estimate %+.4fbb; elapsed-time ratio %.1fx%n",
                    integratedFallback / replications,
                    integratedMissingResponse / replications,
                    completionUpper / replications,
                    integratedSeconds / sampledSeconds);
        }
        System.out.println(
                "Both estimators score the same fixed response with equal private-deal counts. Across-seed SD and within-run SE cover evaluation sampling only; neither is an exploitability upper bound. Elapsed times include evaluation, not training.");
    }

    private static void print(
            int target,
            String method,
            PhysicalRiverHeldOutDecisionAudit.SampleMoments gains,
            PhysicalRiverHeldOutDecisionAudit.SampleMoments errors,
            double secondsPerSeed,
            int replications) {
        System.out.printf(
                Locale.ROOT,
                "%s %s: mean gain %+.6fbb, across-seed SD %.6fbb, mean paired SE %.6fbb, %.2fs/seed%n",
                target == 0 ? "BB" : "BTN",
                method,
                gains.mean(),
                gains.standardError() * Math.sqrt(replications),
                errors.mean(),
                secondsPerSeed);
    }
}
