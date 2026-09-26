package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Offline generation only: collect payoffs, solve, and validate a complete reproducible artifact.
 */
public final class MultiwayPackBuilder {
    private MultiwayPackBuilder() {}

    public static MultiwaySolutionPack build(
            MultiwayCallSpot spot,
            int iterations,
            CfrSolver.Variant variant,
            MultiwayShowdownOracle oracle,
            String payoffMethod,
            long payoffSeed,
            String generatedAt) {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(oracle, "oracle");
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        List<MultiwaySolutionPack.PayoffEntry> payoffs = new ArrayList<>();
        MultiwayPreflopCallGame game =
                spot.game(
                        (dealt, mask) -> {
                            MultiwayShowdownEstimate estimate = oracle.estimate(dealt, mask);
                            payoffs.add(
                                    new MultiwaySolutionPack.PayoffEntry(
                                            dealt.stream().map(WeightedCombo::key).toList(),
                                            mask,
                                            estimate));
                            return estimate;
                        });
        CfrSolution solution = new MultiPlayerCfrSolver<>(game, variant).solve(iterations);
        MultiwaySolutionPack pack =
                new MultiwaySolutionPack(
                        MultiwaySolutionPack.SCHEMA_VERSION,
                        variant == CfrSolver.Variant.CFR_PLUS
                                ? MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION
                                : MultiwaySolutionPack.SOLVER_VERSION,
                        MultiwaySolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        spot,
                        spot.contentHash(),
                        payoffMethod,
                        payoffSeed,
                        solution,
                        payoffs,
                        MultiwayCallBestResponse.assess(game, solution).nashConvBb(),
                        game.maximumTerminalPayoffStandardErrorBb());
        pack.validate();
        return pack;
    }
}
