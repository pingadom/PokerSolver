package com.pokerlab.solver;

import java.util.Objects;

/** Offline only: solve a physical full-deck game and verify its measured gap. */
public final class FlopTurnRiverPackBuilder {
    private FlopTurnRiverPackBuilder() {}

    public static FlopTurnRiverSolutionPack generate(
            FlopTurnRiverSpot spot, int iterations, String generatedAt, double maximumGapBb) {
        Objects.requireNonNull(spot, "spot");
        if (!spot.withFullTurnDeck().contentHash().equals(spot.contentHash()))
            throw new IllegalArgumentException("Cannot pack a restricted turn deck");
        if (!Double.isFinite(maximumGapBb) || maximumGapBb < 0)
            throw new IllegalArgumentException("Maximum gap must be finite and nonnegative");
        FlopTurnRiverGame game = spot.game();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        double gap = HeadsUpBestResponse.assess(game, solution).gap();
        if (gap > maximumGapBb)
            throw new IllegalStateException(
                    String.format(
                            "Full-deck flop game gap %.9f bb exceeds requested %.9f bb",
                            gap, maximumGapBb));
        FlopTurnRiverSolutionPack pack =
                new FlopTurnRiverSolutionPack(
                        FlopTurnRiverSolutionPack.SCHEMA_VERSION,
                        FlopTurnRiverSolutionPack.CFR_PLUS_SOLVER_VERSION,
                        FlopTurnRiverSolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        spot,
                        spot.contentHash(),
                        iterations,
                        gap,
                        solution);
        pack.validate();
        return pack;
    }
}
