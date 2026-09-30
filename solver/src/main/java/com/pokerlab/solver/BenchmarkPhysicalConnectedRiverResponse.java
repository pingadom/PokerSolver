package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible on-policy, sample-split river response audit across independent CFR solves. */
public final class BenchmarkPhysicalConnectedRiverResponse {
    private BenchmarkPhysicalConnectedRiverResponse() {}

    public static void main(String[] args) {
        if (args.length > 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalConnectedRiverResponse [iterations] [attempted-deals] [seed] [minimum-discovery-states] [3x3|5x5]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 3_000;
        int attempts = args.length >= 2 ? Integer.parseInt(args[1]) : 50_000;
        long seed = args.length >= 3 ? Long.parseLong(args[2]) : 42;
        int minimum = args.length >= 4 ? Integer.parseInt(args[3]) : 5;
        var profile =
                args.length == 5
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        long started = System.nanoTime();
        var primary = solve(game, iterations, seed + 100_003);
        var alternate = solve(game, iterations, seed + 200_003);
        double solveSeconds = (System.nanoTime() - started) / 1e9;
        var report =
                PhysicalConnectedRiverResponseAudit.assess(
                        game, primary, alternate, attempts, minimum, seed);
        double auditSeconds = (System.nanoTime() - started) / 1e9 - solveSeconds;
        System.out.printf(
                Locale.ROOT,
                "On-policy connected river response %s: %d CFR iterations per solve, %d attempted physical deals, seed %d, minimum %d discovery states; solve %.2fs, audit %.2fs%n",
                profile,
                iterations,
                attempts,
                seed,
                minimum,
                solveSeconds,
                auditSeconds);
        System.out.printf(
                Locale.ROOT,
                "Reach: %d terminal earlier, %d missing earlier policy, %d reached river (%.2f%% of attempts)%n",
                report.terminalBeforeRiver(),
                report.missingReachPolicy(),
                report.reachedRiver(),
                100 * report.reachedRiverRate());
        System.out.printf(
                Locale.ROOT,
                "River coverage: %d missing BB decision, %d primary and %d alternate BTN call, %d check continuation; %d fully evaluated; %d/%d held-out states in %d supported BB information sets%n",
                report.missingBigBlindRiverPolicy(),
                report.missingPrimaryResponse(),
                report.missingAlternateResponse(),
                report.missingCheckContinuation(),
                report.evaluatedStates(),
                report.supportedHeldOutStates(),
                report.heldOutStates(),
                report.supportedBuckets());
        System.out.printf(
                Locale.ROOT,
                "Independent BTN responses: mean absolute call-frequency difference %.4f, opposite-majority %.2f%%, alternate-minus-primary bet utility %+.4fbb (paired SE %.4fbb)%n",
                report.meanAbsoluteCallDifference(),
                100 * report.oppositeMajorityRate(),
                report.meanBetUtilityDifferenceBb(),
                report.betUtilityDifferenceStandardErrorBb());
        System.out.printf(
                Locale.ROOT,
                "Held-out BB choice against primary BTN: primary-informed gain %+.4fbb, alternate-informed gain %+.4fbb, alternate-minus-primary %+.4fbb (paired SE %.4fbb; approximate 95%% [%.4f, %.4f]bb)%n",
                report.primarySelectedGainBb(),
                report.alternateSelectedGainBb(),
                report.alternateMinusPrimaryBb(),
                report.alternateMinusPrimaryStandardErrorBb(),
                report.approximateAlternateMinusPrimaryLower95Bb(),
                report.approximateAlternateMinusPrimaryUpper95Bb());
        System.out.println(
                "The held-out comparison is conditional on complete learned reach and the sampled primary policy. It is one BB river decision, not a full-game best response, equilibrium certificate or trainer admission.");
    }

    private static CfrSolution solve(
            ButtonBigBlindPhysicalDeckGame game, int iterations, long seed) {
        return new CfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                        seed)
                .solve(iterations);
    }
}
