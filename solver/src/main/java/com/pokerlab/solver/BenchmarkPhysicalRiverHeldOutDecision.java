package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible sample-split river decision comparison across board resolutions. */
public final class BenchmarkPhysicalRiverHeldOutDecision {
    private BenchmarkPhysicalRiverHeldOutDecision() {}

    public static void main(String[] args) {
        if (args.length > 6)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalRiverHeldOutDecision [boards] [seed] [minimum-discovery-boards] [3x3|5x5] [BTN-call-probability] [independent|pair]");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
        var profile =
                args.length >= 4
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[3])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        double callProbability = args.length >= 5 ? Double.parseDouble(args[4]) : 1.0;
        var response =
                args.length == 6
                        ? PhysicalRiverHeldOutDecisionAudit.ResponseModel.parse(args[5])
                        : PhysicalRiverHeldOutDecisionAudit.ResponseModel.HAND_INDEPENDENT_CALL;
        var report =
                PhysicalRiverHeldOutDecisionAudit.assess(
                        boards, minimum, seed, profile, callProbability, response);
        String responseDescription =
                response == PhysicalRiverHeldOutDecisionAudit.ResponseModel.PAIR_OR_BETTER_CALL
                        ? "call with pair or better"
                        : String.format(Locale.ROOT, "hand-independent call %.2f", callProbability);
        System.out.printf(
                Locale.ROOT,
                "Sample-split fixed-response river decision %s: %d boards, seed %d, minimum %d discovery boards per bucket, BTN %s%n",
                report.rangeProfile(),
                report.sampledBoards(),
                report.seed(),
                report.minimumDiscoveryBoards(),
                responseDescription);
        print("Fine", report.fine());
        print("Texture", report.texture());
        print("Coarse", report.coarse());
        print("Equity", report.equity());
        var paired = report.equityVersusCoarse();
        System.out.printf(
                Locale.ROOT,
                "Equity minus coarse selected gain: %.4fbb, paired SE %.4fbb, approximate 95%% interval [%.4f, %.4f]bb%n",
                paired.equityMinusCoarseBb(),
                paired.standardErrorBb(),
                paired.approximateLower95Bb(),
                paired.approximateUpper95Bb());
        System.out.println(
                "Held-out gains are relative to always checking. SE and intervals are conditional on the discovery policy and fixed BTN response; they cover held-out sampling only, not solver or model uncertainty. This is not equilibrium EV.");
    }

    private static void print(String label, PhysicalRiverHeldOutDecisionAudit.ModeResult result) {
        System.out.printf(
                Locale.ROOT,
                "%s: %d discovered buckets, %.1f%% held-out support, selected gain %.3fbb, physical oracle %.3fbb, regret %.3fbb (SE %.3fbb, approximate upper 95%% %.3fbb)%n",
                label,
                result.discoveredBuckets(),
                100 * result.supportRate(),
                result.selectedGainBb(),
                result.physicalOracleGainBb(),
                result.regretBb(),
                result.regretStandardErrorBb(),
                result.approximateRegretUpper95Bb());
    }
}
