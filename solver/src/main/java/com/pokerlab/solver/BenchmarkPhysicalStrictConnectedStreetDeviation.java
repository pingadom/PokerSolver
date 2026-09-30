package com.pokerlab.solver;

import java.util.Locale;

/** Reproducible strict-policy BB deviation audit for flop, turn and river. */
public final class BenchmarkPhysicalStrictConnectedStreetDeviation {
    private BenchmarkPhysicalStrictConnectedStreetDeviation() {}

    public static void main(String[] args) {
        if (args.length > 6)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalStrictConnectedStreetDeviation [iterations] [attempted-deals] [continuations-per-action] [minimum-discovery-states] [seed] [3x3|5x5]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 3_000;
        int attempts = args.length >= 2 ? Integer.parseInt(args[1]) : 20_000;
        int continuations = args.length >= 3 ? Integer.parseInt(args[2]) : 4;
        int minimum = args.length >= 4 ? Integer.parseInt(args[3]) : 10;
        long seed = args.length >= 5 ? Long.parseLong(args[4]) : 42;
        var profile =
                args.length == 6
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[5])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed + 100_003)
                        .solve(iterations);
        System.out.printf(
                Locale.ROOT,
                "Strict connected BB deviation %s: %d iterations, seed %d, %d information sets, %d attempted deals per street, %d sampled continuation pairs per flop/turn state, exact river continuation, minimum %d discovery states%n",
                profile,
                iterations,
                seed,
                solution.strategy().size(),
                attempts,
                continuations,
                minimum);
        for (var street : PhysicalConnectedStreetDeviationAudit.Street.values()) {
            var report =
                    PhysicalStrictConnectedStreetDeviationAudit.assess(
                            game,
                            solution,
                            street,
                            attempts,
                            continuations,
                            minimum,
                            seed + street.ordinal());
            System.out.printf(
                    Locale.ROOT,
                    "%s (audit seed %d): %d terminal before street, %d missing reach, %d reached; %d missing root, %d missing check and %d missing bet continuation; %d evaluated (%.1f%% of reached); %d/%d held-out supported across %d BB information sets; selected minus learned-mix gain %+.4fbb (SE %.4fbb; approximate 95%% [%.4f, %.4f]bb)%n",
                    street,
                    report.seed(),
                    report.terminalBeforeStreet(),
                    report.missingReachPolicy(),
                    report.reachedStreet(),
                    report.missingRootPolicy(),
                    report.missingCheckContinuation(),
                    report.missingBetContinuation(),
                    report.evaluatedStates(),
                    100 * report.evaluatedReachRate(),
                    report.supportedHeldOutStates(),
                    report.heldOutStates(),
                    report.supportedBuckets(),
                    report.selectedGainBb(),
                    report.selectedGainStandardErrorBb(),
                    report.approximateSelectedGainLower95Bb(),
                    report.approximateSelectedGainUpper95Bb());
        }
        System.out.println(
                "One BB information-set deviation with fixed-policy continuations (sampled cards/actions before river, exact river actions), conditional on learned-key coverage and one discovery split. This is not a full-game best response, exploitability bound or trainer admission.");
    }
}
