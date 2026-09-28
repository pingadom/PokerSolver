package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible sample-split river decision comparison across board resolutions. */
public final class BenchmarkPhysicalRiverHeldOutDecision {
    private BenchmarkPhysicalRiverHeldOutDecision() {}

    public static void main(String[] args) {
        if (args.length > 4)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalRiverHeldOutDecision [boards] [seed] [minimum-discovery-boards] [3x3|5x5]");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
        var profile =
                args.length == 4
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[3])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var report = PhysicalRiverHeldOutDecisionAudit.assess(boards, minimum, seed, profile);
        System.out.printf(
                Locale.ROOT,
                "Sample-split forced-check/called-bet river decision %s: %d boards, seed %d, minimum %d discovery boards per bucket%n",
                report.rangeProfile(),
                report.sampledBoards(),
                report.seed(),
                report.minimumDiscoveryBoards());
        print("Fine", report.fine());
        print("Texture", report.texture());
        print("Coarse", report.coarse());
        System.out.println(
                "Held-out gains are relative to always checking; the physical-board oracle sees exact conditional showdown margin. This is not equilibrium EV.");
    }

    private static void print(String label, PhysicalRiverHeldOutDecisionAudit.ModeResult result) {
        System.out.printf(
                Locale.ROOT,
                "%s: %d discovered buckets, %.1f%% held-out support, selected gain %.3fbb, physical oracle %.3fbb, regret %.3fbb%n",
                label,
                result.discoveredBuckets(),
                100 * result.supportRate(),
                result.selectedGainBb(),
                result.physicalOracleGainBb(),
                result.regretBb());
    }
}
