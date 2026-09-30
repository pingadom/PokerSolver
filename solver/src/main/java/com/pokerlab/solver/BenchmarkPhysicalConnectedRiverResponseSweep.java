package com.pokerlab.solver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** CSV benchmark for independent-solve response stability as training budget changes. */
public final class BenchmarkPhysicalConnectedRiverResponseSweep {
    private BenchmarkPhysicalConnectedRiverResponseSweep() {}

    public static void main(String[] args) {
        if (args.length > 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalConnectedRiverResponseSweep [3x3|5x5] [iterations-csv] [seeds-csv] [attempted-deals] [minimum-discovery-states]");
        var profile =
                args.length >= 1
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[0])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        List<Integer> iterations =
                args.length >= 2
                        ? Arrays.stream(args[1].split(",")).map(Integer::parseInt).toList()
                        : List.of(300, 1_000, 3_000);
        List<Long> seeds =
                args.length >= 3
                        ? Arrays.stream(args[2].split(",")).map(Long::parseLong).toList()
                        : List.of(42L, 43L);
        int attempts = args.length >= 4 ? Integer.parseInt(args[3]) : 50_000;
        int minimum = args.length == 5 ? Integer.parseInt(args[4]) : 10;
        System.out.println(
                "range,iterations,seed,primary_infosets,alternate_infosets,attempted,reached,missing_reach,missing_bb_root,missing_primary_call,missing_alternate_call,missing_check_continuation,evaluated,held_out,supported_held_out,supported_buckets,mean_absolute_call_difference,opposite_majority_rate,primary_selected_gain_bb,alternate_selected_gain_bb,alternate_minus_primary_bb,paired_se_bb");
        for (var row :
                PhysicalConnectedRiverResponseSweep.run(
                        profile, iterations, seeds, attempts, minimum)) {
            var audit = row.audit();
            System.out.printf(
                    Locale.ROOT,
                    "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    row.profile(),
                    row.iterations(),
                    row.seed(),
                    row.primaryInformationSets(),
                    row.alternateInformationSets(),
                    audit.attemptedDeals(),
                    audit.reachedRiver(),
                    audit.missingReachPolicy(),
                    audit.missingBigBlindRiverPolicy(),
                    audit.missingPrimaryResponse(),
                    audit.missingAlternateResponse(),
                    audit.missingCheckContinuation(),
                    audit.evaluatedStates(),
                    audit.heldOutStates(),
                    audit.supportedHeldOutStates(),
                    audit.supportedBuckets(),
                    audit.meanAbsoluteCallDifference(),
                    audit.oppositeMajorityRate(),
                    audit.primarySelectedGainBb(),
                    audit.alternateSelectedGainBb(),
                    audit.alternateMinusPrimaryBb(),
                    audit.alternateMinusPrimaryStandardErrorBb());
        }
        System.err.println(
                "Each budget has its own learned reach distribution. A falling response difference alone is not a convergence or exploitability proof; paired SE conditions on fixed solves and discovery splits.");
    }
}
