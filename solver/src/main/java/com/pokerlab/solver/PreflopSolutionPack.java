package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Offline solution artifact for one bounded, no-rake preflop all-in spot. */
public record PreflopSolutionPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        PreflopAllInSpot spot,
        String spotHash,
        int iterations,
        String payoffMethod,
        int payoffTrialsPerMatchup,
        long payoffSeed,
        double estimatedGameGapBb,
        double maximumCalledPayoffStandardErrorBb,
        List<MatchupEquity> matchups,
        CfrSolution solution,
        List<HeroDecision> heroDecisions) {
    public static final String SCHEMA_VERSION = "preflop-all-in-pack/v1";
    public static final String SOLVER_VERSION = "alternating-vanilla-cfr/v1";
    public static final String CFR_PLUS_SOLVER_VERSION = "alternating-cfr-plus/v1";
    public static final String VALIDATION_ONLY = "VALIDATION_ONLY";
    public static final String SEEDED_MONTE_CARLO = "SEEDED_MONTE_CARLO";
    public static final String EXACT_ENUMERATION = "EXACT_ENUMERATION";
    private static final double TOLERANCE = 1e-8;

    public record MatchupEquity(String firstCombo, String secondCombo, EquityEstimate estimate) {}

    /** UTG-side EV in big blinds for each legal action, conditional on its exact combo. */
    public record HeroDecision(
            String combo,
            double shoveFrequency,
            double foldFrequency,
            double shoveEvBb,
            double foldEvBb) {}

    private record Pair(String first, String second) {}

    public PreflopSolutionPack {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(solution, "solution");
        matchups = List.copyOf(matchups);
        heroDecisions = List.copyOf(heroDecisions);
    }

    /**
     * Rejects unknown versions, changed assumptions, incomplete payoffs and invalid strategy/EVs.
     */
    public void validate() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || (!SOLVER_VERSION.equals(solverVersion)
                        && !CFR_PLUS_SOLVER_VERSION.equals(solverVersion))
                || !VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported pack or solver version");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("Spot hash does not match inputs");
        if (iterations < 1
                || payoffTrialsPerMatchup < 1
                || solution.iterations() != iterations
                || (!SEEDED_MONTE_CARLO.equals(payoffMethod)
                        && !EXACT_ENUMERATION.equals(payoffMethod))
                || (EXACT_ENUMERATION.equals(payoffMethod) && payoffSeed != 0)
                || (EXACT_ENUMERATION.equals(payoffMethod) && payoffTrialsPerMatchup != 1_712_304)
                || !Double.isFinite(estimatedGameGapBb)
                || estimatedGameGapBb < 0
                || !Double.isFinite(maximumCalledPayoffStandardErrorBb)
                || maximumCalledPayoffStandardErrorBb < 0)
            throw new IllegalArgumentException("Invalid pack generation metadata");

        Map<Pair, EquityEstimate> estimates = new HashMap<>();
        for (MatchupEquity matchup : matchups) {
            if (matchup == null || matchup.estimate() == null)
                throw new IllegalArgumentException("Missing matchup equity");
            if (matchup.estimate().trials() != payoffTrialsPerMatchup)
                throw new IllegalArgumentException("Mixed payoff trial counts");
            if (EXACT_ENUMERATION.equals(payoffMethod) && matchup.estimate().standardError() != 0)
                throw new IllegalArgumentException("Exact payoff cannot have sampling error");
            Pair key = new Pair(matchup.firstCombo(), matchup.secondCombo());
            if (estimates.putIfAbsent(key, matchup.estimate()) != null)
                throw new IllegalArgumentException("Duplicate matchup equity");
        }
        Set<Pair> expected = new HashSet<>();
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (!first.conflictsWith(second)) expected.add(new Pair(first.key(), second.key()));
            }
        }
        if (!expected.equals(estimates.keySet()))
            throw new IllegalArgumentException("Payoff table does not match unblocked ranges");

        Set<String> expectedInformationSets = new HashSet<>();
        for (WeightedCombo combo : spot.firstRange())
            expectedInformationSets.add("0:" + combo.key() + ":");
        for (WeightedCombo combo : spot.secondRange())
            expectedInformationSets.add("1:" + combo.key() + ":s");
        if (!expectedInformationSets.equals(solution.strategy().keySet()))
            throw new IllegalArgumentException("Solution information sets do not match ranges");

        PreflopAllInGame game =
                spot.game((first, second) -> estimates.get(new Pair(first.key(), second.key())));
        double gap = PreflopAllInBestResponse.assess(game, solution).gap();
        if (Math.abs(gap - estimatedGameGapBb) > TOLERANCE
                || Math.abs(
                                game.maximumCalledPayoffStandardError()
                                        - maximumCalledPayoffStandardErrorBb)
                        > TOLERANCE)
            throw new IllegalArgumentException("Quality metrics do not match the payoff table");

        Map<String, HeroDecision> decisions = new HashMap<>();
        for (HeroDecision decision : heroDecisions) {
            if (decision == null
                    || decision.combo() == null
                    || !Double.isFinite(decision.shoveFrequency())
                    || !Double.isFinite(decision.foldFrequency())
                    || !Double.isFinite(decision.shoveEvBb())
                    || !Double.isFinite(decision.foldEvBb())
                    || decision.shoveFrequency() < 0
                    || decision.foldFrequency() < 0
                    || Math.abs(decision.shoveFrequency() + decision.foldFrequency() - 1)
                            > TOLERANCE
                    || decisions.putIfAbsent(decision.combo(), decision) != null)
                throw new IllegalArgumentException("Invalid or duplicate hero decision");
        }
        if (!decisions
                .keySet()
                .equals(
                        spot.firstRange().stream()
                                .map(WeightedCombo::key)
                                .collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("Hero decision coverage does not match the range");
        for (WeightedCombo combo : spot.firstRange()) {
            HeroDecision expectedDecision = heroDecision(game, solution, combo);
            HeroDecision actual = decisions.get(combo.key());
            if (Math.abs(actual.shoveFrequency() - expectedDecision.shoveFrequency()) > TOLERANCE
                    || Math.abs(actual.foldFrequency() - expectedDecision.foldFrequency())
                            > TOLERANCE
                    || Math.abs(actual.shoveEvBb() - expectedDecision.shoveEvBb()) > TOLERANCE
                    || Math.abs(actual.foldEvBb() - expectedDecision.foldEvBb()) > TOLERANCE)
                throw new IllegalArgumentException("Hero decision does not match solution/payoffs");
        }
    }

    static HeroDecision heroDecision(
            PreflopAllInGame game, CfrSolution solution, WeightedCombo combo) {
        double marginal = 0;
        double shoveEv = 0;
        double foldEv = 0;
        for (ChanceOutcome<PreflopAllInGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            PreflopAllInGame.State deal = outcome.state();
            if (!deal.first().key().equals(combo.key())) continue;
            PreflopAllInGame.State shove = game.afterAction(deal, "s");
            Map<String, Double> response = solution.at(1, game.informationSet(shove));
            double call = response.get("c");
            double probability = outcome.probability();
            marginal += probability;
            shoveEv +=
                    probability
                            * (call * game.terminalUtility(game.afterAction(shove, "c"))
                                    + (1 - call)
                                            * game.terminalUtility(game.afterAction(shove, "f")));
            foldEv += probability * game.terminalUtility(game.afterAction(deal, "f"));
        }
        if (marginal <= 0) throw new IllegalArgumentException("Hero combo has no valid deals");
        Map<String, Double> policy = solution.at(0, combo.key() + ":");
        return new HeroDecision(
                combo.key(),
                policy.get("s"),
                policy.get("f"),
                shoveEv / marginal,
                foldEv / marginal);
    }
}
