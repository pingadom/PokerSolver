package com.pokerlab.solver;

import java.util.Locale;

/** Controlled, research-only comparison of static and preflop-action-conditioned river buckets. */
public final class BenchmarkPhysicalActionBelief {
    private BenchmarkPhysicalActionBelief() {}

    public static void main(String[] args) {
        if (args.length > 6)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalActionBelief [boards] [seed] [minimum-discovery-boards] [3x3|5x5] [correct|shrunk|uninformative|reversed] [call|pair]");
        int boards = args.length >= 1 ? Integer.parseInt(args[0]) : 50_000;
        long seed = args.length >= 2 ? Long.parseLong(args[1]) : 42;
        int minimum = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
        var profile =
                args.length >= 4
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[3])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var assumption =
                args.length >= 5
                        ? ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption.CORRECT;
        var response =
                args.length >= 6
                        ? PhysicalActionBeliefAudit.ResponseModel.parse(args[5])
                        : PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL;
        var report =
                PhysicalActionBeliefAudit.assess(
                        boards,
                        minimum,
                        seed,
                        profile,
                        ButtonBigBlindRangeValidationFixture.actionBelief(profile),
                        ButtonBigBlindRangeValidationFixture.assumedActionBelief(
                                profile, assumption),
                        response);
        System.out.printf(
                Locale.ROOT,
                "Fixed preflop-action model %s: %d boards, seed %d, minimum %d discovery boards per bucket, assumed belief %s, BTN response %s%n",
                report.profile(),
                report.sampledBoards(),
                report.seed(),
                minimum,
                assumption,
                response);
        print("Static", report.staticRange());
        print("Conditioned", report.actionConditioned());
        var paired = report.conditionedMinusStatic();
        System.out.printf(
                Locale.ROOT,
                "Conditioned minus static selected gain: %.4fbb, paired SE %.4fbb, approximate 95%% interval [%.4f, %.4f]bb%n",
                paired.conditionedMinusStaticBb(),
                paired.standardErrorBb(),
                paired.approximateLower95Bb(),
                paired.approximateUpper95Bb());
        System.out.println(
                "Deals and exact action values follow the fixed synthetic generating model; the assumed model changes only the observation. Intervals condition on one discovery split and do not cover model uncertainty, equilibrium EV, or a full-game bound.");
    }

    private static void print(String label, PhysicalRiverHeldOutDecisionAudit.ModeResult result) {
        System.out.printf(
                Locale.ROOT,
                "%s: %d buckets, %.1f%% held-out support, gain %.4fbb, regret %.4fbb%n",
                label,
                result.discoveredBuckets(),
                100 * result.supportRate(),
                result.selectedGainBb(),
                result.regretBb());
    }
}
