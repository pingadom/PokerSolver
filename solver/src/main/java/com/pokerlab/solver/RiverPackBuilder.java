package com.pokerlab.solver;

import java.util.Objects;

/** Offline builder; serving a saved river pack must never trigger a solve. */
public final class RiverPackBuilder {
    private RiverPackBuilder() {}

    public static RiverSolutionPack generate(
            RiverBetSpot spot, int iterations, String generatedAt) {
        Objects.requireNonNull(spot, "spot");
        RiverBetGame game = spot.game();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        RiverSolutionPack pack =
                new RiverSolutionPack(
                        RiverSolutionPack.SCHEMA_VERSION,
                        RiverSolutionPack.CFR_PLUS_SOLVER_VERSION,
                        RiverSolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        spot,
                        spot.contentHash(),
                        iterations,
                        RiverBetBestResponse.assess(game, solution).gap(),
                        solution);
        pack.validate();
        return pack;
    }
}
