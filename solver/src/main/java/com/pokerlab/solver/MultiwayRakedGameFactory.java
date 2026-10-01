package com.pokerlab.solver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reuses a fully validated no-rake payoff table under an explicit raked settlement rule. */
final class MultiwayRakedGameFactory {
    private record PayoffKey(List<String> dealtCombos, int activeMask) {}

    private MultiwayRakedGameFactory() {}

    static MultiwayPreflopCallGame fromPack(MultiwaySidePotPack source, CashRakeRule rakeRule) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(rakeRule, "rakeRule");
        source.validate();
        Map<PayoffKey, MultiwayShowdownEstimate> estimates = new HashMap<>();
        for (var entry : source.payoffs())
            estimates.put(new PayoffKey(entry.dealtCombos(), entry.activeMask()), entry.estimate());
        var spot = source.spot();
        return new MultiwayPreflopCallGame(
                spot.seats(),
                spot.ranges(),
                spot.committedBb(),
                spot.stacksBb(),
                spot.deadMoneyBb(),
                (dealt, mask) -> {
                    var estimate =
                            estimates.get(
                                    new PayoffKey(
                                            dealt.stream().map(WeightedCombo::key).toList(), mask));
                    if (estimate == null)
                        throw new IllegalArgumentException("Missing validated payoff");
                    return estimate;
                },
                rakeRule);
    }
}
