package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible common-reach comparison of fine and coarse public-board observation support. */
public final class BenchmarkPhysicalBoardObservationCoverage {
    private BenchmarkPhysicalBoardObservationCoverage() {}

    public static void main(String[] args) {
        if (args.length > 3)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalBoardObservationCoverage [attempted-deals] [seed] [minimum-observations]");
        int attempts = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length == 3 ? Integer.parseInt(args[2]) : 20;
        var report = PhysicalBoardObservationCoverage.assess(attempts, minimum, seed);
        System.out.printf(
                Locale.ROOT,
                "Uniform-action common reach: %d physical deals, seed %d, minimum %d observations per bucket%n",
                attempts,
                seed,
                minimum);
        System.out.println("Fine hash: " + report.fineGameHash());
        System.out.println("Coarse hash: " + report.coarseGameHash());
        for (var street : report.streets())
            System.out.printf(
                    Locale.ROOT,
                    "%s: %d reached; fine %d buckets, %.1f%% supported; coarse %d buckets, %.1f%% supported%n",
                    street.street(),
                    street.reached(),
                    street.fineBuckets(),
                    100 * street.fineSupportRate(),
                    street.coarseBuckets(),
                    100 * street.coarseSupportRate());
        System.out.println(
                "Support is descriptive; uniform actions isolate observation grouping and are not solver policy or strategy quality.");
    }
}
