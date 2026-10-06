package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;

/**
 * Test-scope cost harness; the first two of five 20-iteration solves are warm-up. Setup, hashing
 * and exact best-response assessment are outside the timed solve. The synthetic strategy is
 * validation-only and its quality is not a convergence claim.
 */
public final class BenchmarkSixMaxHistoryValidation {
    public static void main(String[] args) throws Exception {
        if (args.length != 0)
            throw new IllegalArgumentException("This benchmark takes no arguments");
        System.out.println(
                "history_container="
                        + SixMaxHeadsUpPostflopGame.class
                                .getDeclaredField("HISTORIES")
                                .getType()
                                .getName());
        var base = SixMaxConnectedPreflopGameTest.base();
        var flop =
                SixMaxPolicyFlopTransition.counterfactualSupport(
                                base, SixMaxConnectedPreflopGameTest.HISTORY)
                        .conditionOnFlop(
                                List.of(Card.parse("2d"), Card.parse("3d"), Card.parse("4d")));
        String hash = null;
        for (int run = 0; run < 5; run++) {
            var game = new SixMaxHeadsUpPostflopGame(flop, 3.25, 6.5, 13);
            long start = System.nanoTime();
            var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(20);
            double seconds = (System.nanoTime() - start) / 1e9;
            String current = SixMaxConnectedPostflopAudit.solutionHash(solution);
            if (hash != null && !hash.equals(current))
                throw new IllegalStateException("Nonrepeatable policy");
            hash = current;
            System.out.println(
                    "run="
                            + run
                            + " seconds="
                            + seconds
                            + " hash="
                            + hash
                            + " infos="
                            + solution.strategy().size());
            if (run == 4)
                System.out.println(
                        "exact_gap_bb=" + HeadsUpBestResponse.assess(game, solution).gap());
        }
    }
}
