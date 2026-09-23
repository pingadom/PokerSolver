package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Provisional automated checks before a human reviews a preflop pack as training content. */
public final class PreflopPackScreening {
    public static final double MAX_GAME_GAP_BB = 0.05;
    public static final double MAX_CALLED_PAYOFF_STANDARD_ERROR_BB = 0.1;
    public static final int MIN_COMBOS_PER_RANGE = 6;
    public static final double MIN_CLEAR_ACTION_EDGE_BB = 1;

    public enum Finding {
        GAME_GAP_TOO_LARGE,
        PAYOFF_UNCERTAINTY_TOO_LARGE,
        HERO_RANGE_TOO_SMALL,
        OPPONENT_RANGE_TOO_SMALL,
        NO_CLEAR_SHOVE,
        NO_CLEAR_FOLD
    }

    public record Report(
            int heroCombos,
            int opponentCombos,
            int clearlyShoveCombos,
            int clearlyFoldCombos,
            double clearActionEdgeBb,
            List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public boolean passesAutomatedChecks() {
            return findings.isEmpty();
        }
    }

    private PreflopPackScreening() {}

    /**
     * Uses a conservative edge screen based on the worst single called-payoff SE. These checks
     * cannot validate range realism, betting-tree coverage, rake, or solver-model error.
     */
    public static Report assess(PreflopSolutionPack pack) {
        Objects.requireNonNull(pack, "pack").validate();
        double clearEdge =
                Math.max(MIN_CLEAR_ACTION_EDGE_BB, 3 * pack.maximumCalledPayoffStandardErrorBb());
        int clearlyShove = 0;
        int clearlyFold = 0;
        for (var decision : pack.heroDecisions()) {
            if (decision.shoveEvBb() - decision.foldEvBb() > clearEdge) clearlyShove++;
            if (decision.foldEvBb() - decision.shoveEvBb() > clearEdge) clearlyFold++;
        }

        List<Finding> findings = new ArrayList<>();
        if (pack.estimatedGameGapBb() > MAX_GAME_GAP_BB) findings.add(Finding.GAME_GAP_TOO_LARGE);
        if (pack.maximumCalledPayoffStandardErrorBb() > MAX_CALLED_PAYOFF_STANDARD_ERROR_BB)
            findings.add(Finding.PAYOFF_UNCERTAINTY_TOO_LARGE);
        if (pack.spot().firstRange().size() < MIN_COMBOS_PER_RANGE)
            findings.add(Finding.HERO_RANGE_TOO_SMALL);
        if (pack.spot().secondRange().size() < MIN_COMBOS_PER_RANGE)
            findings.add(Finding.OPPONENT_RANGE_TOO_SMALL);
        if (clearlyShove == 0) findings.add(Finding.NO_CLEAR_SHOVE);
        if (clearlyFold == 0) findings.add(Finding.NO_CLEAR_FOLD);
        return new Report(
                pack.spot().firstRange().size(),
                pack.spot().secondRange().size(),
                clearlyShove,
                clearlyFold,
                clearEdge,
                findings);
    }
}
