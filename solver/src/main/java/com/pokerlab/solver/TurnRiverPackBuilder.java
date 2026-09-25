package com.pokerlab.solver;

import java.util.Objects;

/** Offline solve and validation; loading a pack never starts a solve. */
public final class TurnRiverPackBuilder {
    private TurnRiverPackBuilder() {}

    public static TurnRiverSolutionPack generate(
            TurnRiverSpot spot, int iterations, String generatedAt) {
        Objects.requireNonNull(spot, "spot");
        TurnRiverGame game = spot.game();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        TurnRiverSolutionPack pack =
                new TurnRiverSolutionPack(
                        TurnRiverSolutionPack.SCHEMA_VERSION,
                        TurnRiverSolutionPack.CFR_PLUS_SOLVER_VERSION,
                        TurnRiverSolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        spot,
                        spot.contentHash(),
                        iterations,
                        HeadsUpBestResponse.assess(game, solution).gap(),
                        solution);
        pack.validate();
        return pack;
    }
}
