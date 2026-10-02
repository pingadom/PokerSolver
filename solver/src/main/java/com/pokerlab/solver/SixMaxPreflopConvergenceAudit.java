package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Re-solves the same finite six-seat game at independent budgets and measures exact deviation. */
public final class SixMaxPreflopConvergenceAudit {
    public record Row(
            int iterations,
            int informationSets,
            double nashConvBb,
            List<Double> deviationGainsBb,
            List<Double> profileUtilitiesBb,
            double maximumTerminalPayoffStandardErrorBb) {
        public Row {
            deviationGainsBb = List.copyOf(deviationGainsBb);
            profileUtilitiesBb = List.copyOf(profileUtilitiesBb);
        }

        public double largestDeviationBb() {
            return deviationGainsBb.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        }

        public double profileTotalBb() {
            return profileUtilitiesBb.stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private SixMaxPreflopConvergenceAudit() {}

    public static List<Row> run(SixMaxPreflopCheckdownGame game, List<Integer> budgets) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(budgets, "budgets");
        if (budgets.isEmpty()) throw new IllegalArgumentException("At least one budget is needed");
        int previous = 0;
        for (Integer budget : budgets) {
            if (budget == null || budget <= previous)
                throw new IllegalArgumentException("Budgets must be positive and increasing");
            previous = budget;
        }
        List<Row> rows = new ArrayList<>();
        for (int budget : budgets) {
            var solution =
                    new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(budget);
            var report = MultiPlayerInformationSetBestResponse.assess(game, solution);
            rows.add(
                    new Row(
                            budget,
                            solution.strategy().size(),
                            report.nashConvBb(),
                            report.deviationGainsBb(),
                            report.profileUtilitiesBb(),
                            game.maximumTerminalPayoffStandardErrorBb()));
        }
        return List.copyOf(rows);
    }
}
