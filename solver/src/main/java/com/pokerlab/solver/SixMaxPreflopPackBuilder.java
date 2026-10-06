package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Offline full-round generation; stores every physical-deal/active-subset payoff. */
public final class SixMaxPreflopPackBuilder {
    private SixMaxPreflopPackBuilder() {}

    public static SixMaxPreflopSolutionPack build(
            SixMaxPreflopResearchSpot spot,
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
        var game =
                spot.game(
                        (hands, mask) -> {
                            var estimate = oracle.estimate(hands, mask);
                            payoffs.add(
                                    new MultiwaySolutionPack.PayoffEntry(
                                            hands.stream().map(WeightedCombo::key).toList(),
                                            mask,
                                            estimate));
                            return estimate;
                        });
        var solution = new MultiPlayerCfrSolver<>(game, variant).solve(iterations);
        var pack =
                new SixMaxPreflopSolutionPack(
                        SixMaxPreflopSolutionPack.schemaFor(spot.rules()),
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
                        MultiPlayerInformationSetBestResponse.assess(game, solution).nashConvBb(),
                        game.maximumTerminalPayoffStandardErrorBb());
        pack.validate();
        return pack;
    }
}
