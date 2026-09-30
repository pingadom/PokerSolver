package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Repeats the on-policy audit at independent CFR budgets and seeds. */
public final class PhysicalConnectedRiverResponseSweep {
    public record Row(
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            int iterations,
            long seed,
            int primaryInformationSets,
            int alternateInformationSets,
            PhysicalConnectedRiverResponseAudit.Report audit) {}

    private PhysicalConnectedRiverResponseSweep() {}

    public static List<Row> run(
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            List<Integer> iterationBudgets,
            List<Long> seeds,
            int attemptedDeals,
            int minimumDiscoveryStates) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(iterationBudgets, "iterationBudgets");
        Objects.requireNonNull(seeds, "seeds");
        if (iterationBudgets.isEmpty() || seeds.isEmpty())
            throw new IllegalArgumentException("Expected at least one iteration budget and seed");
        if (iterationBudgets.stream().anyMatch(n -> n == null || n < 1 || n > 100_000)
                || iterationBudgets.stream().distinct().count() != iterationBudgets.size())
            throw new IllegalArgumentException(
                    "Iteration budgets must be distinct and in 1-100000");
        if (seeds.stream().anyMatch(Objects::isNull)
                || seeds.stream().distinct().count() != seeds.size())
            throw new IllegalArgumentException("Seeds must be non-null and distinct");
        if (attemptedDeals < 2 || attemptedDeals > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 attempted deals");
        if (minimumDiscoveryStates < 1 || minimumDiscoveryStates > attemptedDeals / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        List<Row> rows = new ArrayList<>();
        for (int iterations : iterationBudgets) {
            for (long seed : seeds) {
                var primary = solve(game, iterations, seed + 100_003);
                var alternate = solve(game, iterations, seed + 200_003);
                var audit =
                        PhysicalConnectedRiverResponseAudit.assess(
                                game,
                                primary,
                                alternate,
                                attemptedDeals,
                                minimumDiscoveryStates,
                                seed);
                rows.add(
                        new Row(
                                profile,
                                iterations,
                                seed,
                                primary.strategy().size(),
                                alternate.strategy().size(),
                                audit));
            }
        }
        return List.copyOf(rows);
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
