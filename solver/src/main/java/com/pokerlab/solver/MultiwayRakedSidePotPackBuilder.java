package com.pokerlab.solver;

import java.util.Objects;

/** Builds a new raked research artifact without re-enumerating physical boards. */
public final class MultiwayRakedSidePotPackBuilder {
    private MultiwayRakedSidePotPackBuilder() {}

    public static MultiwayRakedSidePotPack build(
            MultiwaySidePotPack source,
            CashRakeRule rakeRule,
            int iterations,
            CfrSolver.Variant variant,
            String generatedAt) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(rakeRule, "rakeRule");
        Objects.requireNonNull(variant, "variant");
        if (iterations < 1 || iterations > 1_000_000)
            throw new IllegalArgumentException("Expected 1-1000000 solver iterations");
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod())
                || rakeRule.fraction() <= 0
                || rakeRule.capBb() <= 0)
            throw new IllegalArgumentException("Raked packs require exact payoffs and active rake");
        var game = MultiwayRakedGameFactory.fromPack(source, rakeRule);
        var solution = new MultiPlayerCfrSolver<>(game, variant).solve(iterations);
        var report = MultiwayCallBestResponse.assess(game, solution);
        double rake =
                source.spot().deadMoneyBb()
                        - report.profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum();
        var pack =
                new MultiwayRakedSidePotPack(
                        MultiwayRakedSidePotPack.SCHEMA_VERSION,
                        variant == CfrSolver.Variant.CFR_PLUS
                                ? MultiwaySolutionPack.CFR_PLUS_SOLVER_VERSION
                                : MultiwaySolutionPack.SOLVER_VERSION,
                        MultiwaySolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        source,
                        MultiwayPackJson.sidePotContentHash(source),
                        rakeRule,
                        solution,
                        report.nashConvBb(),
                        game.maximumTerminalPayoffStandardErrorBb(),
                        Math.max(0, rake));
        pack.validate();
        return pack;
    }
}
