package com.pokerlab.solver;

import java.util.Locale;

/** Exact bounded-game comparison of seeded chance sampling and exhaustive CFR+. */
public final class BenchmarkChanceSampledCfr {
    private BenchmarkChanceSampledCfr() {}

    public static void main(String[] args) {
        if (args.length > 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkChanceSampledCfr [iterations] [seed] [flops] [turns] [all|after-root|exhaustive-plus]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 10_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int flops = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
        int turns = args.length >= 4 ? Integer.parseInt(args[3]) : 2;
        String sampling = args.length >= 5 ? args[4] : "all";
        CfrSolver.ChanceMode mode =
                switch (sampling) {
                    case "all" -> CfrSolver.ChanceMode.SAMPLED;
                    case "after-root" -> CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT;
                    case "exhaustive-plus" -> CfrSolver.ChanceMode.EXHAUSTIVE;
                    default -> throw new IllegalArgumentException("Unknown traversal mode");
                };
        CfrSolver.Variant variant =
                mode == CfrSolver.ChanceMode.EXHAUSTIVE
                        ? CfrSolver.Variant.CFR_PLUS
                        : CfrSolver.Variant.VANILLA;
        ButtonBigBlindContinuationGame game = ButtonBigBlindResearchFixture.create(flops, turns);
        long started = System.nanoTime();
        CfrSolution sparse = new CfrSolver<>(game, variant, mode, seed).solve(iterations);
        double solveSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        var completed = StrategyCompletion.uniformAtUnseen(game, sparse, 10_000_000);
        var response = HeadsUpBestResponse.assess(game, completed.solution());
        double totalSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(
                Locale.ROOT,
                "%s: %d iterations, seed %d, %d flops/%d turns%n",
                sampling,
                iterations,
                seed,
                flops,
                turns);
        System.out.printf(
                Locale.ROOT,
                "Visited %d/%d information sets; completion visited %d states; game gap %.6fbb%n",
                sparse.strategy().size(),
                completed.solution().strategy().size(),
                completed.visitedStates(),
                response.gap());
        System.out.printf(
                Locale.ROOT,
                "Solve %.2fs, total with exact bounded-game audit %.2fs; hash %s%n",
                solveSeconds,
                totalSeconds,
                game.contentHash());
        System.out.println(
                "Unvisited information sets use uniform actions for this audit; algorithm choice does not repair public-card abstraction error.");
    }
}
