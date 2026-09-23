package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds small provisional packs offline; the API must never run this on a request path. */
public final class PreflopPackBuilder {
    private PreflopPackBuilder() {}

    public static PreflopSolutionPack generate(
            PreflopAllInSpot spot,
            int iterations,
            int payoffTrialsPerMatchup,
            long payoffSeed,
            String generatedAt) {
        return generate(
                spot,
                iterations,
                payoffTrialsPerMatchup,
                payoffSeed,
                generatedAt,
                CfrSolver.Variant.VANILLA);
    }

    public static PreflopSolutionPack generate(
            PreflopAllInSpot spot,
            int iterations,
            int payoffTrialsPerMatchup,
            long payoffSeed,
            String generatedAt,
            CfrSolver.Variant variant) {
        return generate(
                spot,
                iterations,
                payoffTrialsPerMatchup,
                payoffSeed,
                generatedAt,
                variant,
                PreflopSolutionPack.SEEDED_MONTE_CARLO,
                new SeededMonteCarloEquityOracle(payoffTrialsPerMatchup, payoffSeed));
    }

    public static PreflopSolutionPack generateExact(
            PreflopAllInSpot spot, int iterations, String generatedAt) {
        return generateExact(spot, iterations, generatedAt, CfrSolver.Variant.VANILLA);
    }

    public static PreflopSolutionPack generateExact(
            PreflopAllInSpot spot, int iterations, String generatedAt, CfrSolver.Variant variant) {
        return generate(
                spot,
                iterations,
                1_712_304,
                0,
                generatedAt,
                variant,
                PreflopSolutionPack.EXACT_ENUMERATION,
                new ExactPreflopEquityOracle());
    }

    private static PreflopSolutionPack generate(
            PreflopAllInSpot spot,
            int iterations,
            int payoffTrialsPerMatchup,
            long payoffSeed,
            String generatedAt,
            CfrSolver.Variant variant,
            String payoffMethod,
            PreflopEquityOracle oracle) {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(variant, "variant");
        PreflopAllInGame game = spot.game(oracle);
        CfrSolution solution = new CfrSolver<>(game, variant).solve(iterations);
        List<PreflopSolutionPack.MatchupEquity> matchups = new ArrayList<>();
        for (ChanceOutcome<PreflopAllInGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            PreflopAllInGame.State deal = outcome.state();
            matchups.add(
                    new PreflopSolutionPack.MatchupEquity(
                            deal.first().key(),
                            deal.second().key(),
                            game.matchupEquity(deal.first(), deal.second())));
        }
        List<PreflopSolutionPack.HeroDecision> decisions =
                spot.firstRange().stream()
                        .map(combo -> PreflopSolutionPack.heroDecision(game, solution, combo))
                        .toList();
        PreflopSolutionPack pack =
                new PreflopSolutionPack(
                        PreflopSolutionPack.SCHEMA_VERSION,
                        variant == CfrSolver.Variant.CFR_PLUS
                                ? PreflopSolutionPack.CFR_PLUS_SOLVER_VERSION
                                : PreflopSolutionPack.SOLVER_VERSION,
                        PreflopSolutionPack.VALIDATION_ONLY,
                        generatedAt,
                        spot,
                        spot.contentHash(),
                        iterations,
                        payoffMethod,
                        payoffTrialsPerMatchup,
                        payoffSeed,
                        PreflopAllInBestResponse.assess(game, solution).gap(),
                        game.maximumCalledPayoffStandardError(),
                        matchups,
                        solution,
                        decisions);
        pack.validate();
        return pack;
    }
}
