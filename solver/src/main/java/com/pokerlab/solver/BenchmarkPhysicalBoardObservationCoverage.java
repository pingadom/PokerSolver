package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible common-reach comparison of connected public-board observations. */
public final class BenchmarkPhysicalBoardObservationCoverage {
    private BenchmarkPhysicalBoardObservationCoverage() {}

    public static void main(String[] args) {
        if (args.length > 4)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalBoardObservationCoverage [attempted-deals] [seed] [minimum-observations] [3x3|5x5]");
        int attempts = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length >= 3 ? Integer.parseInt(args[2]) : 20;
        var profile =
                args.length == 4
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[3])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var report = PhysicalBoardObservationCoverage.assess(attempts, minimum, seed, profile);
        System.out.printf(
                Locale.ROOT,
                "Uniform-action common reach %s: %d physical deals, seed %d, minimum %d observations per bucket%n",
                report.rangeProfile(),
                attempts,
                seed,
                minimum);
        System.out.println("Fine hash: " + report.fineGameHash());
        System.out.println("Texture hash: " + report.textureGameHash());
        System.out.println("Coarse hash: " + report.coarseGameHash());
        System.out.println("Equity hash: " + report.equityGameHash());
        for (var street : report.streets())
            System.out.printf(
                    Locale.ROOT,
                    "%s: %d reached; fine %d buckets, %.1f%% supported; texture %d buckets, %.1f%% supported; coarse %d buckets, %.1f%% supported; equity %d buckets, %.1f%% supported%n",
                    street.street(),
                    street.reached(),
                    street.fineBuckets(),
                    100 * street.fineSupportRate(),
                    street.textureBuckets(),
                    100 * street.textureSupportRate(),
                    street.coarseBuckets(),
                    100 * street.coarseSupportRate(),
                    street.equityBuckets(),
                    100 * street.equitySupportRate());
        System.out.println(
                "Support is descriptive; uniform actions isolate observation grouping and are not solver policy or strategy quality.");
    }
}
