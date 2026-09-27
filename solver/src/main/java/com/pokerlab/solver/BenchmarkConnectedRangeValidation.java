package com.pokerlab.solver;

import java.util.Locale;

/** Runs the board-bucket experiment on a disjoint nine-deal synthetic range. */
public final class BenchmarkConnectedRangeValidation {
    private BenchmarkConnectedRangeValidation() {}

    public static void main(String[] args) {
        if (args.length > 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkConnectedRangeValidation [iterations] [seed] [deviation-trials-per-deal] [flop-attempts] [continuations-per-action]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 5_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int trials = args.length >= 3 ? Integer.parseInt(args[2]) : 2_000;
        int flopAttempts = args.length >= 4 ? Integer.parseInt(args[3]) : 10_000;
        int continuations = args.length == 5 ? Integer.parseInt(args[4]) : 2;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        var game = ButtonBigBlindRangeValidationFixture.createBucketed();
        long started = System.nanoTime();
        CfrSolution solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed)
                        .solve(iterations);
        double solveSeconds = (System.nanoTime() - started) / 1e9;
        var deviations =
                PhysicalConnectedPreflopDeviationAudit.assess(game, solution, trials, seed + 2);
        var flop =
                PhysicalConnectedFlopDeviationAudit.assess(
                        game, solution, flopAttempts, continuations, seed + 3);
        System.out.printf(
                Locale.ROOT,
                "Disjoint range: %d iterations, %d information sets, solve %.2fs, hash %s%n",
                iterations,
                solution.strategy().size(),
                solveSeconds,
                game.contentHash());
        for (var decision : deviations.decisions())
            System.out.printf(
                    Locale.ROOT,
                    "%s %s: continue %.1f%%, fold %+.3fbb, continue %+.3f +/- %.3fbb, estimated gain %.3fbb%n",
                    decision.player(),
                    decision.ownHand(),
                    100 * decision.policyContinueProbability(),
                    decision.foldUtilityBb(),
                    decision.continueUtilityBb(),
                    1.96 * decision.continueStandardErrorBb(),
                    decision.estimatedImprovementBb());
        System.out.printf(
                Locale.ROOT,
                "Flop: %d/%d attempted deals reached BB's first flop decision; %d observed buckets, %d missing policies%n",
                flop.reachedFlops(),
                flop.attemptedDeals(),
                flop.decisions().size(),
                flop.missingPolicyDecisions());
        flop.decisions().stream()
                .filter(decision -> decision.heldOutStates() >= 10)
                .sorted(
                        java.util.Comparator.comparingDouble(
                                        (PhysicalConnectedFlopDeviationAudit.Decision decision) ->
                                                decision.reachedFlopWeight()
                                                        * decision
                                                                .discoveryEstimatedImprovementBb())
                                .reversed())
                .limit(5)
                .forEach(
                        decision ->
                                System.out.printf(
                                        Locale.ROOT,
                                        "  %s: discovery=%d held-out=%d, selected %s vs bet %.1f%%, held-out check %+.3fbb, bet %+.3fbb, bet-check SE %.3fbb, held-out gain %+.3fbb%n",
                                        decision.informationSet(),
                                        decision.discoveryStates(),
                                        decision.heldOutStates(),
                                        decision.selectedAction(),
                                        100 * decision.policyBetProbability(),
                                        decision.heldOutCheckUtilityBb(),
                                        decision.heldOutBetUtilityBb(),
                                        decision.heldOutBetMinusCheckStandardErrorBb(),
                                        decision.heldOutPolicyGainBb()));
        System.out.println(
                "Research only: flop action selection and evaluation use separate samples; neither one-decision audit is full-game exploitability.");
    }
}
