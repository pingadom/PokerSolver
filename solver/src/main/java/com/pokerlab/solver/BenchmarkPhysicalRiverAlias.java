package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible river information-loss witness for fine and coarse connected observations. */
public final class BenchmarkPhysicalRiverAlias {
    private BenchmarkPhysicalRiverAlias() {}

    public static void main(String[] args) {
        if (args.length > 3)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalRiverAlias [boards] [seed] [3x3|5x5]");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        var profile =
                args.length == 3
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[2])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var report = PhysicalRiverAliasAudit.assess(boards, seed, profile);
        System.out.printf(
                Locale.ROOT,
                "Forced-check/called-bet river counterfactual %s: %d boards, seed %d; %d non-tie margins%n",
                report.rangeProfile(),
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
                "Texture: %d buckets, %d with opposite margin signs, %.1f%% of compared boards in conflicted buckets, %.3fbb observation loss%n",
                report.textureBuckets(),
                report.textureConflictedBuckets(),
                100 * report.textureConflictedRate(),
                report.textureObservationLossBb());
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
