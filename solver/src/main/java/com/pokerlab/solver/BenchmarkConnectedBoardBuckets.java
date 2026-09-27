package com.pokerlab.solver;

import java.util.Locale;

/** Compares exact-public-card and coarse-bucket information sets on held-out physical runouts. */
public final class BenchmarkConnectedBoardBuckets {
    private BenchmarkConnectedBoardBuckets() {}

    public static void main(String[] args) {
        if (args.length > 4)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkConnectedBoardBuckets [iterations] [seed] [held-out-runouts] [deviation-trials-per-deal]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 1_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int trials = args.length >= 3 ? Integer.parseInt(args[2]) : 10_000;
        int deviationTrials = args.length == 4 ? Integer.parseInt(args[3]) : 2_000;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        for (ButtonBigBlindPhysicalDeckGame game :
                java.util.List.of(
                        ButtonBigBlindPhysicalDeckFixture.create(),
                        ButtonBigBlindPhysicalDeckFixture.createBucketed())) {
            long started = System.nanoTime();
            CfrSolution solution =
                    new CfrSolver<>(
                                    game,
                                    CfrSolver.Variant.VANILLA,
                                    CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                    seed)
                            .solve(iterations);
            double solveSeconds = (System.nanoTime() - started) / 1e9;
            PhysicalConnectedStrategyAudit.Report audit =
                    PhysicalConnectedStrategyAudit.assess(game, solution, trials, seed + 1);
            PhysicalConnectedPreflopDeviationAudit.Report deviations =
                    PhysicalConnectedPreflopDeviationAudit.assess(
                            game, solution, deviationTrials, seed + 2);
            System.out.printf(
                    Locale.ROOT,
                    "%s: %d information sets, solve %.2fs, held-out self-play BB EV %+.4f +/- %.4fbb, missing policy %.1f%% (%d/%d decisions)%n",
                    game.informationMode(),
                    solution.strategy().size(),
                    solveSeconds,
                    audit.meanBigBlindBb(),
                    1.96 * audit.samplingStandardErrorBb(),
                    100 * audit.missingStrategyRate(),
                    audit.missingStrategyDecisions(),
                    audit.decisions());
            System.out.println("Game hash: " + game.contentHash());
            solution.strategy().entrySet().stream()
                    .filter(entry -> entry.getKey().contains(":P:"))
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    System.out.println(entry.getKey() + " -> " + entry.getValue()));
            for (var decision : deviations.decisions())
                System.out.printf(
                        Locale.ROOT,
                        "  %s %s: fold %+.3fbb, continue %+.3f +/- %.3fbb, policy continue %.1f%%, estimated one-decision gain %.3fbb%n",
                        decision.player(),
                        decision.ownHand(),
                        decision.foldUtilityBb(),
                        decision.continueUtilityBb(),
                        1.96 * decision.continueStandardErrorBb(),
                        100 * decision.policyContinueProbability(),
                        decision.estimatedImprovementBb());
        }
        System.out.println(
                "Research only: unvisited decisions use uniform actions; self-play EV and one-decision gains are not a full best-response gap or trainer certification.");
    }
}
