package com.pokerlab.solver;

/** Reproducible offline convergence check for the exact four-combo turn fixture. */
public final class BenchmarkTurnRiver {
    private BenchmarkTurnRiver() {}

    public static void main(String[] arguments) {
        if (arguments.length != 1)
            throw new IllegalArgumentException("Usage: BenchmarkTurnRiver <iterations>");
        int iterations = Integer.parseInt(arguments[0]);
        TurnRiverGame game = TurnRiverValidationSpot.create().game();
        long start = System.nanoTime();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        long solved = System.nanoTime();
        HeadsUpBestResponse.Report report = HeadsUpBestResponse.assess(game, solution);
        long assessed = System.nanoTime();
        System.out.printf(
                "iterations=%d infosets=%d profile=%.9f bb firstBR=%.9f bb secondBR=%.9f bb gap=%.9f bb solve=%.3f s assess=%.3f s%n",
                iterations,
                solution.strategy().size(),
                report.profileValue(),
                report.firstBestResponse(),
                report.secondBestResponse(),
                report.gap(),
                (solved - start) / 1e9,
                (assessed - solved) / 1e9);
    }
}
