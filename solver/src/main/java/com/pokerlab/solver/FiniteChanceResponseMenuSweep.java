package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Compares response chance modes on independently declared finite physical-card menus. Exact
 * responses bound optimizer error on each menu only; menu-to-menu variation is not a full-deck
 * exploitability estimate.
 */
public final class FiniteChanceResponseMenuSweep {
    public record PlayerResult(
            int player,
            double exactResponseGainBb,
            double sampledAverageShortfallBb,
            double sampledFinalShortfallBb,
            double exactRootAverageShortfallBb,
            double exactRootFinalShortfallBb,
            int sampledMissingTargetKeys,
            int exactRootMissingTargetKeys) {
        public double finalRegretImprovementBb() {
            return sampledFinalShortfallBb - exactRootFinalShortfallBb;
        }
    }

    public record MenuResult(
            long chanceSeed,
            List<Double> flopQuantiles,
            List<Double> turnQuantiles,
            List<Double> riverQuantiles,
            int rootDeals,
            int exactRootIterations,
            List<PlayerResult> players) {
        public PlayerResult player(int target) {
            return players.get(target);
        }
    }

    public record Summary(
            int player,
            int menus,
            int exactRootWins,
            double meanSampledFinalShortfallBb,
            double meanExactRootFinalShortfallBb,
            double worstSampledFinalShortfallBb,
            double worstExactRootFinalShortfallBb) {}

    public record Report(
            String physicalGameHash,
            int chancePointsPerStreet,
            int baselineIterations,
            int sampledIterations,
            long responseSeed,
            List<MenuResult> menus) {
        public Summary summary(int player) {
            if (player != 0 && player != 1)
                throw new IllegalArgumentException("Player must be 0 or 1");
            double sampledTotal = 0;
            double rootTotal = 0;
            double sampledWorst = 0;
            double rootWorst = 0;
            int wins = 0;
            for (var menu : menus) {
                var result = menu.player(player);
                double sampled = result.sampledFinalShortfallBb();
                double root = result.exactRootFinalShortfallBb();
                sampledTotal += sampled;
                rootTotal += root;
                sampledWorst = Math.max(sampledWorst, sampled);
                rootWorst = Math.max(rootWorst, root);
                if (root < sampled) wins++;
            }
            return new Summary(
                    player,
                    menus.size(),
                    wins,
                    sampledTotal / menus.size(),
                    rootTotal / menus.size(),
                    sampledWorst,
                    rootWorst);
        }
    }

    private FiniteChanceResponseMenuSweep() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame physical,
            int chancePointsPerStreet,
            List<Long> chanceSeeds,
            int baselineIterations,
            int sampledIterations,
            long responseSeed) {
        Objects.requireNonNull(physical, "physical");
        Objects.requireNonNull(chanceSeeds, "chanceSeeds");
        if (chancePointsPerStreet < 1 || chancePointsPerStreet > 4)
            throw new IllegalArgumentException("Chance points must be in 1-4");
        if (chanceSeeds.isEmpty() || chanceSeeds.size() > 8)
            throw new IllegalArgumentException("Expected 1-8 chance menus");
        if (chanceSeeds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(chanceSeeds).size() != chanceSeeds.size())
            throw new IllegalArgumentException("Chance seeds must be distinct non-null values");
        if (baselineIterations < 1 || baselineIterations > 10_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-10000");
        if (sampledIterations < 1 || sampledIterations > 1_000_000)
            throw new IllegalArgumentException("Sampled iterations must be in 1-1000000");

        List<MenuResult> results = new ArrayList<>();
        for (long chanceSeed : chanceSeeds) {
            var random = new SplittableRandom(chanceSeed);
            var game =
                    new PhysicalDeckChanceSubgame(
                            physical,
                            quantiles(random, chancePointsPerStreet),
                            quantiles(random, chancePointsPerStreet),
                            quantiles(random, chancePointsPerStreet));
            int rootDeals = game.chanceOutcomes(game.initialState()).size();
            int exactRootIterations = sampledIterations / rootDeals;
            if (exactRootIterations < 1)
                throw new IllegalArgumentException(
                        "Sampled budget must cover every root deal at least once");
            var baseline =
                    new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(baselineIterations);
            List<PlayerResult> players = new ArrayList<>();
            for (int player = 0; player <= 1; player++) {
                long seed = responseSeed + player;
                var sampledReport =
                        FiniteChanceResponseCalibration.assess(
                                game,
                                baseline,
                                player,
                                List.of(sampledIterations),
                                List.of(seed),
                                FixedOpponentResponseCfr.ChanceMode.SAMPLED_ALL);
                var rootReport =
                        FiniteChanceResponseCalibration.assess(
                                game,
                                baseline,
                                player,
                                List.of(exactRootIterations),
                                List.of(seed),
                                FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT);
                if (Math.abs(
                                sampledReport.exactBestResponseGainBb()
                                        - rootReport.exactBestResponseGainBb())
                        > 1e-9)
                    throw new IllegalStateException("Chance modes disagree on exact baseline");
                var sampled = sampledReport.candidates().getFirst();
                var root = rootReport.candidates().getFirst();
                if (sampled.missingFixedOpponentQueries() != 0
                        || root.missingFixedOpponentQueries() != 0)
                    throw new IllegalStateException(
                            "Exhaustively solved baseline has missing opponent keys");
                players.add(
                        new PlayerResult(
                                player,
                                sampledReport.exactBestResponseGainBb(),
                                sampled.shortfallToExactBb(),
                                sampled.finalRegretShortfallBb(),
                                root.shortfallToExactBb(),
                                root.finalRegretShortfallBb(),
                                sampled.baselineTargetKeysWithoutResponse(),
                                root.baselineTargetKeysWithoutResponse()));
            }
            results.add(
                    new MenuResult(
                            chanceSeed,
                            game.flopQuantiles(),
                            game.turnQuantiles(),
                            game.riverQuantiles(),
                            rootDeals,
                            exactRootIterations,
                            List.copyOf(players)));
        }
        return new Report(
                physical.contentHash(),
                chancePointsPerStreet,
                baselineIterations,
                sampledIterations,
                responseSeed,
                List.copyOf(results));
    }

    private static List<Double> quantiles(SplittableRandom random, int count) {
        List<Double> values = new ArrayList<>();
        for (int index = 0; index < count; index++) values.add(random.nextDouble());
        return List.copyOf(values);
    }
}
