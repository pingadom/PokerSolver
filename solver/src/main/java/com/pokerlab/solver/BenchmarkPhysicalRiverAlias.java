package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible river information-loss witness for fine and coarse connected observations. */
public final class BenchmarkPhysicalRiverAlias {
    private BenchmarkPhysicalRiverAlias() {}

    public static void main(String[] args) {
        if (args.length > 2)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalRiverAlias [boards] [seed]");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length == 2 ? Long.parseLong(args[1]) : 42;
        var report = PhysicalRiverAliasAudit.assess(boards, seed);
        System.out.printf(
                Locale.ROOT,
                "Forced-check/called-bet river counterfactual: %d boards, seed %d; %d non-tie margins%n",
                report.sampledBoards(),
                report.seed(),
                report.comparedBoards());
        System.out.printf(
                Locale.ROOT,
                "Fine: %d buckets, %d with opposite margin signs, %.1f%% of compared boards in conflicted buckets, %.3fbb observation loss%n",
                report.fineBuckets(),
                report.fineConflictedBuckets(),
                100 * report.fineConflictedRate(),
                report.fineObservationLossBb());
        System.out.printf(
                Locale.ROOT,
                "Coarse: %d buckets, %d with opposite margin signs, %.1f%% of compared boards in conflicted buckets, %.3fbb observation loss%n",
                report.coarseBuckets(),
                report.coarseConflictedBuckets(),
                100 * report.coarseConflictedRate(),
                report.coarseObservationLossBb());
        System.out.println(
                "This fixed-response witness is not a game-theoretic quality or exploitability estimate.");
    }
}
