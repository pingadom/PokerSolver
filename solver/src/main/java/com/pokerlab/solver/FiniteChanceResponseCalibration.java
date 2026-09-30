package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Measures sampled response-search error against exact best responses in a finite chance game. Both
 * the benchmark and exact bound refer only to the supplied game's chance distribution.
 */
public final class FiniteChanceResponseCalibration {
    public record Candidate(
            int iterations,
            long seed,
            FixedOpponentResponseCfr.ChanceMode chanceMode,
            double candidateGainBb,
            double shortfallToExactBb,
            double finalRegretGainBb,
            double finalRegretShortfallBb,
            int learnedInformationSets,
            long missingFixedOpponentQueries,
            int baselineTargetKeysWithoutResponse) {}

    public record Report(
            int targetPlayer,
            double baselineTargetUtilityBb,
            double exactBestResponseGainBb,
            List<Candidate> candidates) {}

    private FiniteChanceResponseCalibration() {}

    public static <S> Report assess(
            CfrGame<S> game,
            CfrSolution baseline,
            int target,
            List<Integer> budgets,
            List<Long> seeds) {
        return assess(
                game,
                baseline,
                target,
                budgets,
                seeds,
                FixedOpponentResponseCfr.ChanceMode.SAMPLED_ALL);
    }

    public static <S> Report assess(
            CfrGame<S> game,
            CfrSolution baseline,
            int target,
            List<Integer> budgets,
            List<Long> seeds,
            FixedOpponentResponseCfr.ChanceMode chanceMode) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(budgets, "budgets");
        Objects.requireNonNull(seeds, "seeds");
        Objects.requireNonNull(chanceMode, "chanceMode");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        if (budgets.isEmpty() || seeds.isEmpty())
            throw new IllegalArgumentException("Expected response budgets and seeds");
        for (Integer budget : budgets) {
            if (budget == null || budget < 1 || budget > 1_000_000)
                throw new IllegalArgumentException("Response budgets must be in 1-1000000");
        }
        if (seeds.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Response seeds cannot be null");

        var exact = HeadsUpBestResponse.assess(game, baseline);
        double baselineTarget = target == 0 ? exact.profileValue() : -exact.profileValue();
        double exactTarget = target == 0 ? exact.firstBestResponse() : -exact.secondBestResponse();
        double exactGain = exactTarget - baselineTarget;
        List<Candidate> candidates = new ArrayList<>();
        for (int budget : budgets) {
            for (long seed : seeds) {
                var trained =
                        new FixedOpponentResponseCfr<>(game, baseline, target, seed, chanceMode)
                                .solve(budget);
                double gain =
                        gain(game, baseline, trained.response(), target, baselineTarget, budget);
                double finalRegretGain =
                        gain(
                                game,
                                baseline,
                                trained.finalRegretPolicy(),
                                target,
                                baselineTarget,
                                budget);
                double shortfall = exactGain - gain;
                double finalRegretShortfall = exactGain - finalRegretGain;
                if (shortfall < -1e-7 || finalRegretShortfall < -1e-7)
                    throw new IllegalStateException("Candidate exceeded exact best-response bound");
                long fallback =
                        baseline.strategy().keySet().stream()
                                .filter(key -> key.startsWith(target + ":"))
                                .filter(key -> !trained.response().strategy().containsKey(key))
                                .count();
                candidates.add(
                        new Candidate(
                                budget,
                                seed,
                                chanceMode,
                                gain,
                                Math.max(0, shortfall),
                                finalRegretGain,
                                Math.max(0, finalRegretShortfall),
                                trained.response().strategy().size(),
                                trained.missingFixedOpponentQueries(),
                                Math.toIntExact(fallback)));
            }
        }
        return new Report(target, baselineTarget, exactGain, List.copyOf(candidates));
    }

    private static <S> double gain(
            CfrGame<S> game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            double baselineTarget,
            int iterations) {
        Map<String, Map<String, Double>> merged = new HashMap<>(baseline.strategy());
        merged.putAll(response.strategy());
        double value =
                StrategyEvaluator.playerZeroUtility(game, new CfrSolution(iterations, merged));
        return (target == 0 ? value : -value) - baselineTarget;
    }
}
