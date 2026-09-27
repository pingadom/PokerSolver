package com.pokerlab.solver;

import java.util.Locale;

/** Offline full-physical-deck, chance-sampled connected-game research run. */
public final class BenchmarkPhysicalConnectedChance {
    private BenchmarkPhysicalConnectedChance() {}

    public static void main(String[] args) {
        if (args.length > 3)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalConnectedChance [iterations] [seed] [audit-trials-per-deal]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 1_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int auditTrials = args.length == 3 ? Integer.parseInt(args[2]) : 5_000;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        ButtonBigBlindPhysicalDeckGame game = ButtonBigBlindPhysicalDeckFixture.create();
        long started = System.nanoTime();
        PhysicalConnectedChanceAudit.Report audit =
                PhysicalConnectedChanceAudit.assess(
                        game, auditTrials, seed, new ExactPreflopEquityOracle());
        double auditSeconds = (System.nanoTime() - started) / 1e9;
        CfrSolution solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed)
                        .solve(iterations);
        double seconds = (System.nanoTime() - started) / 1e9;
        System.out.printf(
                Locale.ROOT,
                "Research-only physical-deck BTN/BB: %d iterations, seed %d, solve %.2fs, %d visited information sets%n",
                iterations,
                seed,
                seconds - auditSeconds,
                solution.strategy().size());
        System.out.println(
                "Every legal 3-card flop (17,296 per deal), 45 turns and 44 rivers are sampled without constructing the full tree.");
        System.out.printf(
                Locale.ROOT,
                "Check-down audit: exact %+.6fbb, sampled %+.6fbb, error %+.6fbb, estimated sampling SE %.6fbb; %.2fs%n",
                audit.exactWeightedBb(),
                audit.sampledWeightedBb(),
                audit.signedErrorBb(),
                audit.weightedSamplingStandardErrorBb(),
                auditSeconds);
        System.out.println("Game hash: " + game.contentHash());
        solution.strategy().entrySet().stream()
                .filter(entry -> entry.getKey().contains(":P:"))
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
        System.out.println(
                "Sparse profile: no full-game best-response gap, convergence certificate or trainer admission.");
    }
}
