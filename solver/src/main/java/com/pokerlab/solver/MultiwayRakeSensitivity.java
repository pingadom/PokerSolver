package com.pokerlab.solver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Offline no-rake-to-raked transfer on the same validated finite showdown payoff table. */
public final class MultiwayRakeSensitivity {
    private record PayoffKey(List<String> dealtCombos, int activeMask) {}

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
        Map<PayoffKey, MultiwayShowdownEstimate> estimates = new HashMap<>();
        for (var entry : pack.payoffs())
            estimates.put(new PayoffKey(entry.dealtCombos(), entry.activeMask()), entry.estimate());
        var spot = pack.spot();
        var rakedGame =
                new MultiwayPreflopCallGame(
                        spot.seats(),
                        spot.ranges(),
                        spot.committedBb(),
                        spot.stacksBb(),
                        spot.deadMoneyBb(),
                        (dealt, mask) -> {
                            var estimate =
                                    estimates.get(
                                            new PayoffKey(
                                                    dealt.stream().map(WeightedCombo::key).toList(),
                                                    mask));
                            if (estimate == null)
                                throw new IllegalArgumentException("Missing validated payoff");
                            return estimate;
                        },
                        rakeRule);
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
