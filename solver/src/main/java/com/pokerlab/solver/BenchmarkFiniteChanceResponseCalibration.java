package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/** Compares sampled response search with exact best responses on one declared chance subgame. */
public final class BenchmarkFiniteChanceResponseCalibration {
    private BenchmarkFiniteChanceResponseCalibration() {}

    public static void main(String[] args) {
        if (args.length > 7)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkFiniteChanceResponseCalibration [baseline-iterations] [short-response-iterations] [long-response-iterations] [chance-points-per-street] [solve-seed] [chance-seed] [3x3|5x5]");
        int baselineIterations = args.length >= 1 ? Integer.parseInt(args[0]) : 300;
        int shortBudget = args.length >= 2 ? Integer.parseInt(args[1]) : 1_000;
        int longBudget = args.length >= 3 ? Integer.parseInt(args[2]) : 10_000;
        int chancePoints = args.length >= 4 ? Integer.parseInt(args[3]) : 2;
        long seed = args.length >= 5 ? Long.parseLong(args[4]) : 42;
        long chanceSeed = args.length >= 6 ? Long.parseLong(args[5]) : 142;
        var profile =
                args.length == 7
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[6])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        if (baselineIterations < 1 || baselineIterations > 10_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-10000");
        if (shortBudget < 1 || shortBudget >= longBudget || longBudget > 1_000_000)
            throw new IllegalArgumentException(
                    "Expected 1 <= short response < long response <= 1000000");
        if (chancePoints < 1 || chancePoints > 4)
            throw new IllegalArgumentException("Chance points must be in 1-4");

        var physical =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var random = new SplittableRandom(chanceSeed);
        var subgame =
                new PhysicalDeckChanceSubgame(
                        physical,
                        quantiles(random, chancePoints),
                        quantiles(random, chancePoints),
                        quantiles(random, chancePoints));
        var baseline =
                new CfrSolver<>(subgame, CfrSolver.Variant.CFR_PLUS).solve(baselineIterations);
        System.out.printf(
                Locale.ROOT,
                "Finite response calibration %s: %d chance points/street (seed %d), %d CFR+ baseline iterations, %d information sets, physical hash %s%n",
                profile,
                chancePoints,
                chanceSeed,
                baselineIterations,
                baseline.strategy().size(),
                subgame.physicalGameHash());
        System.out.println(
                "Flop/turn/river quantiles: "
                        + subgame.flopQuantiles()
                        + " / "
                        + subgame.turnQuantiles()
                        + " / "
                        + subgame.riverQuantiles());
        for (int target = 0; target <= 1; target++) {
            var report =
                    FiniteChanceResponseCalibration.assess(
                            subgame,
                            baseline,
                            target,
                            List.of(shortBudget, longBudget),
                            List.of(seed + 200_003 + target, seed + 200_103 + target));
            System.out.printf(
                    Locale.ROOT,
                    "%s baseline target value %+.6fbb, exact best-response gain %+.6fbb%n",
                    target == 0 ? "BB" : "BTN",
                    report.baselineTargetUtilityBb(),
                    report.exactBestResponseGainBb());
            for (var candidate : report.candidates())
                System.out.printf(
                        Locale.ROOT,
                        "  budget %d seed %d average gain %+.6fbb (shortfall %.6f), final-regret gain %+.6fbb (shortfall %.6f), learned keys %d, missing-opponent queries %d, baseline target keys without response %d%n",
                        candidate.iterations(),
                        candidate.seed(),
                        candidate.candidateGainBb(),
                        candidate.shortfallToExactBb(),
                        candidate.finalRegretGainBb(),
                        candidate.finalRegretShortfallBb(),
                        candidate.learnedInformationSets(),
                        candidate.missingFixedOpponentQueries(),
                        candidate.baselineTargetKeysWithoutResponse());
        }
        System.out.println(
                "Exact values and search shortfall are valid only for this restricted physical-chance game. Quantile menus can change information and strategy; this does not bound full-deck exploitability.");
    }

    private static List<Double> quantiles(SplittableRandom random, int count) {
        List<Double> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(random.nextDouble());
        return List.copyOf(result);
    }
}
