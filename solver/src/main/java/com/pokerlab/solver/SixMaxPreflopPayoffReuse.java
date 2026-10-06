package com.pokerlab.solver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fresh solves of revised finite games using exact showdown shares for unchanged physical deals.
 */
public final class SixMaxPreflopPayoffReuse {
    public record Provenance(
            String interpretation,
            String sourcePackHash,
            String sourceSpotHash,
            String targetSpotHash,
            int reusedPayoffEntries,
            int jointDeals,
            SixMaxPreflopBetting.Rules sourceRules,
            SixMaxPreflopBetting.Rules targetRules,
            double sourceNashConvBb,
            double targetNashConvBb) {}

    public record Result(SixMaxPreflopSolutionPack pack, Provenance provenance) {}

    private record Key(List<String> hands, int mask) {}

    private SixMaxPreflopPayoffReuse() {}

    public static Result buildExact(
            SixMaxPreflopSolutionPack source,
            SixMaxPreflopResearchSpot target,
            int iterations,
            CfrSolver.Variant variant,
            String generatedAt) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(variant, "variant");
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be positive");
        java.time.Instant.parse(generatedAt);
        // Validate saved shares, complete source policy and metrics before trusting any entry.
        source.validate();
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod()))
            throw new IllegalArgumentException("Payoff reuse requires an exact-enumeration source");
        Map<Key, MultiwayShowdownEstimate> table = new HashMap<>();
        for (var entry : source.payoffs())
            table.put(new Key(entry.dealtCombos(), entry.activeMask()), entry.estimate());
        var used = new HashSet<Key>();
        MultiwayShowdownOracle reuse =
                (hands, mask) -> {
                    var key = new Key(hands.stream().map(WeightedCombo::key).toList(), mask);
                    var estimate = table.get(key);
                    if (estimate == null)
                        throw new IllegalArgumentException(
                                "Target has a physical deal missing from the source payoff table");
                    used.add(key);
                    return estimate;
                };
        // Check exact physical support before solving. Weights may change; worlds may not
        // disappear.
        var targetGame = target.game(reuse);
        if (!used.equals(table.keySet()))
            throw new IllegalArgumentException(
                    "Target physical support must match the source; private worlds cannot be removed");
        var pack =
                SixMaxPreflopPackBuilder.build(
                        target,
                        iterations,
                        variant,
                        reuse,
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        generatedAt);
        return new Result(
                pack,
                new Provenance(
                        "Exact showdown shares are reused only for identical six-hand deals and active subsets. "
                                + "Betting commitments, rake, chance weights, policy and quality are recomputed in the target game. "
                                + "No source strategy rows or continuation checkpoint are transferred. "
                                + "Scores from different games are not a convergence comparison. VALIDATION_ONLY.",
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        target.contentHash(),
                        used.size(),
                        targetGame.chanceOutcomes(targetGame.initialState()).size(),
                        source.spot().rules(),
                        target.rules(),
                        source.nashConvBb(),
                        pack.nashConvBb()));
    }
}
