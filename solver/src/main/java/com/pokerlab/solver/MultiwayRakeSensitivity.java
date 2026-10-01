package com.pokerlab.solver;

import java.util.List;
import java.util.Objects;

/** Offline no-rake-to-raked transfer on the same validated finite showdown payoff table. */
public final class MultiwayRakeSensitivity {
    public record Report(
            CashRakeRule rakeRule,
            double noRakeNashConvBb,
            double transferredNashConvBb,
            double resolvedNashConvBb,
            double sourceMaxTerminalPayoffSEBb,
            double rakedMaxTerminalPayoffSEBb,
            double transferredExpectedRakeBb,
            double resolvedExpectedRakeBb,
            List<Double> noRakeUtilitiesBb,
            List<Double> transferredUtilitiesBb,
            List<Double> resolvedUtilitiesBb,
            List<Double> transferredDeviationGainsBb,
            List<Double> resolvedDeviationGainsBb) {}

    private MultiwayRakeSensitivity() {}

    public static Report assess(
            MultiwaySidePotPack pack,
            CashRakeRule rakeRule,
            int iterations,
            CfrSolver.Variant variant) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(rakeRule, "rakeRule");
        Objects.requireNonNull(variant, "variant");
        if (iterations < 1 || iterations > 1_000_000)
            throw new IllegalArgumentException("Expected 1-1000000 solver iterations");
        // The source pack is deliberately validated under its original no-rake contract first.
        var noRakeGame = pack.rebuildGame();
        var spot = pack.spot();
        var rakedGame = MultiwayRakedGameFactory.fromPack(pack, rakeRule);
        var noRake = MultiwayCallBestResponse.assess(noRakeGame, pack.solution());
        var transferred = MultiwayCallBestResponse.assess(rakedGame, pack.solution());
        var resolvedPolicy = new MultiPlayerCfrSolver<>(rakedGame, variant).solve(iterations);
        var resolved = MultiwayCallBestResponse.assess(rakedGame, resolvedPolicy);
        return new Report(
                rakeRule,
                noRake.nashConvBb(),
                transferred.nashConvBb(),
                resolved.nashConvBb(),
                noRakeGame.maximumTerminalPayoffStandardErrorBb(),
                rakedGame.maximumTerminalPayoffStandardErrorBb(),
                expectedRake(spot.deadMoneyBb(), transferred.profileUtilitiesBb()),
                expectedRake(spot.deadMoneyBb(), resolved.profileUtilitiesBb()),
                noRake.profileUtilitiesBb(),
                transferred.profileUtilitiesBb(),
                resolved.profileUtilitiesBb(),
                transferred.deviationGainsBb(),
                resolved.deviationGainsBb());
    }

    private static double expectedRake(double deadMoneyBb, List<Double> utilitiesBb) {
        double rake = deadMoneyBb - utilitiesBb.stream().mapToDouble(Double::doubleValue).sum();
        if (rake < -1e-8) throw new IllegalStateException("Raked game created chips");
        return Math.max(0, rake);
    }
}
