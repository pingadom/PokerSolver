package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/** Exact two-player best responses on a small, explicitly sampled physical-chance subgame. */
public final class BenchmarkPhysicalDeckSubgameBestResponse {
    private BenchmarkPhysicalDeckSubgameBestResponse() {}

    public static void main(String[] args) {
        if (args.length > 7)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalDeckSubgameBestResponse [iterations] [chance-points-per-street] [solve-seed] [chance-seed] [3x3|5x5] [maximum-visited-states] [subgame-calibration-iterations]");
        int iterations = args.length >= 1 ? Integer.parseInt(args[0]) : 3_000;
        int chancePoints = args.length >= 2 ? Integer.parseInt(args[1]) : 2;
        long solveSeed = args.length >= 3 ? Long.parseLong(args[2]) : 42;
        long chanceSeed = args.length >= 4 ? Long.parseLong(args[3]) : 142;
        var profile =
                args.length >= 5
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[4])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        long maximum = args.length >= 6 ? Long.parseLong(args[5]) : 2_000_000;
        int calibrationIterations = args.length == 7 ? Integer.parseInt(args[6]) : 0;
        if (iterations < 1 || iterations > 100_000)
            throw new IllegalArgumentException("Iterations must be between 1 and 100000");
        if (chancePoints < 1 || chancePoints > 4)
            throw new IllegalArgumentException("Chance points must be in 1-4");
        if (calibrationIterations < 0 || calibrationIterations > 10_000)
            throw new IllegalArgumentException("Calibration iterations must be in 0-10000");
        var physical =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        long started = System.nanoTime();
        var solution =
                new CfrSolver<>(
                                physical,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                solveSeed)
                        .solve(iterations);
        double solveSeconds = (System.nanoTime() - started) / 1e9;
        var random = new SplittableRandom(chanceSeed);
        var subgame =
                new PhysicalDeckChanceSubgame(
                        physical,
                        quantiles(random, chancePoints),
                        quantiles(random, chancePoints),
                        quantiles(random, chancePoints));
        var report = PhysicalDeckSubgameBestResponseAudit.assess(subgame, solution, maximum);
        double auditSeconds = (System.nanoTime() - started) / 1e9 - solveSeconds;
        System.out.printf(
                Locale.ROOT,
                "Physical-chance subgame %s: %d CFR iterations (seed %d), %d sampled chance points per street (seed %d), physical solve %.2fs, exact audit %.2fs%n",
                profile,
                iterations,
                solveSeed,
                chancePoints,
                chanceSeed,
                solveSeconds,
                auditSeconds);
        System.out.println("Game hash: " + report.physicalGameHash());
        System.out.println(
                "Flop/turn/river quantiles: "
                        + report.flopQuantiles()
                        + " / "
                        + report.turnQuantiles()
                        + " / "
                        + report.riverQuantiles());
        System.out.printf(
                Locale.ROOT,
                "Restricted tree: %d visited states, %d learned and %d uniformly completed information sets; %.2f%% of completed-profile trajectories touch a fallback key%n",
                report.visitedStates(),
                report.learnedInformationSets(),
                report.completedInformationSets(),
                100 * report.fallbackPathProbability());
        var response = report.bestResponse();
        System.out.printf(
                Locale.ROOT,
                "BB best response %+.6fbb, BTN best-response bound %+.6fbb, profile BB value %+.6fbb, exact restricted-game Nash gap %.6fbb%n",
                response.firstBestResponse(),
                response.secondBestResponse(),
                response.profileValue(),
                response.gap());
        if (calibrationIterations > 0) {
            var calibrated =
                    new CfrSolver<>(subgame, CfrSolver.Variant.CFR_PLUS)
                            .solve(calibrationIterations);
            var control = HeadsUpBestResponse.assess(subgame, calibrated);
            System.out.printf(
                    Locale.ROOT,
                    "Same-subgame CFR+ control: %d iterations, %d information sets, profile BB value %+.6fbb, exact restricted-game Nash gap %.6fbb%n",
                    calibrationIterations,
                    calibrated.strategy().size(),
                    control.profileValue(),
                    control.gap());
        }
        System.out.println(
                "Exact only for this sampled chance subgame and its explicit uniform completion. Different chance menus can change hidden-hand information and value; this is not a full-deck exploitability bound or trainer admission.");
    }

    private static List<Double> quantiles(SplittableRandom random, int count) {
        List<Double> values = new ArrayList<>();
        for (int index = 0; index < count; index++) values.add(random.nextDouble());
        return List.copyOf(values);
    }
}
