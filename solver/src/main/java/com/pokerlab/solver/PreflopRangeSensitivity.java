package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Re-solves an exact-payoff spot after independently perturbing each range weight. */
public final class PreflopRangeSensitivity {
    public record HeroResult(
            String combo,
            double baselineShoveEdgeBb,
            double minimumShoveEdgeBb,
            String minimumEdgeScenario,
            double maximumShoveEdgeBb,
            String maximumEdgeScenario,
            double baselineShoveFrequency,
            double minimumShoveFrequency,
            double maximumShoveFrequency) {}

    public record Report(
            String spotHash,
            String solverVersion,
            double fractionalWeightChange,
            int scenarioCount,
            double baselineGameGapBb,
            double maximumPerturbedGameGapBb,
            List<HeroResult> heroResults) {
        public Report {
            heroResults = List.copyOf(heroResults);
        }
    }

    private record Pair(String first, String second) {}

    private static final class Aggregate {
        private final String combo;
        private final double baselineEdge;
        private final double baselineFrequency;
        private double minimumEdge;
        private String minimumEdgeScenario;
        private double maximumEdge;
        private String maximumEdgeScenario;
        private double minimumFrequency;
        private double maximumFrequency;

        private Aggregate(PreflopSolutionPack.HeroDecision decision) {
            combo = decision.combo();
            baselineEdge = decision.shoveEvBb() - decision.foldEvBb();
            baselineFrequency = decision.shoveFrequency();
            minimumEdge = baselineEdge;
            minimumEdgeScenario = "BASELINE";
            maximumEdge = baselineEdge;
            maximumEdgeScenario = "BASELINE";
            minimumFrequency = baselineFrequency;
            maximumFrequency = baselineFrequency;
        }

        private void include(PreflopSolutionPack.HeroDecision decision, String scenario) {
            double edge = decision.shoveEvBb() - decision.foldEvBb();
            if (edge < minimumEdge) {
                minimumEdge = edge;
                minimumEdgeScenario = scenario;
            }
            if (edge > maximumEdge) {
                maximumEdge = edge;
                maximumEdgeScenario = scenario;
            }
            minimumFrequency = Math.min(minimumFrequency, decision.shoveFrequency());
            maximumFrequency = Math.max(maximumFrequency, decision.shoveFrequency());
        }

        private HeroResult result() {
            return new HeroResult(
                    combo,
                    baselineEdge,
                    minimumEdge,
                    minimumEdgeScenario,
                    maximumEdge,
                    maximumEdgeScenario,
                    baselineFrequency,
                    minimumFrequency,
                    maximumFrequency);
        }
    }

    private PreflopRangeSensitivity() {}

    /**
     * Varies one hero or opponent combo weight at a time by +/- the given fraction. This is a local
     * model-sensitivity probe, not a statistical confidence interval or a range review.
     */
    public static Report analyze(PreflopSolutionPack pack, double fractionalWeightChange) {
        Objects.requireNonNull(pack, "pack").validate();
        if (!PreflopSolutionPack.EXACT_ENUMERATION.equals(pack.payoffMethod()))
            throw new IllegalArgumentException("Range sensitivity requires exact payoff inputs");
        if (!Double.isFinite(fractionalWeightChange)
                || fractionalWeightChange <= 0
                || fractionalWeightChange >= 1)
            throw new IllegalArgumentException("Weight change must be between zero and one");

        Map<Pair, EquityEstimate> payoffs = new LinkedHashMap<>();
        for (var matchup : pack.matchups()) {
            payoffs.put(new Pair(matchup.firstCombo(), matchup.secondCombo()), matchup.estimate());
        }
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (var decision : pack.heroDecisions()) {
            aggregates.put(decision.combo(), new Aggregate(decision));
        }
        CfrSolver.Variant variant =
                PreflopSolutionPack.CFR_PLUS_SOLVER_VERSION.equals(pack.solverVersion())
                        ? CfrSolver.Variant.CFR_PLUS
                        : CfrSolver.Variant.VANILLA;
        PreflopAllInSpot spot = pack.spot();
        int scenarios = 0;
        double maximumGap = 0;
        for (boolean changeHero : List.of(true, false)) {
            List<WeightedCombo> range = changeHero ? spot.firstRange() : spot.secondRange();
            for (int index = 0; index < range.size(); index++) {
                for (double factor :
                        new double[] {1 - fractionalWeightChange, 1 + fractionalWeightChange}) {
                    PreflopAllInSpot varied = variedSpot(spot, changeHero, index, factor);
                    PreflopAllInGame game =
                            varied.game(
                                    (first, second) ->
                                            payoffs.get(new Pair(first.key(), second.key())));
                    CfrSolution solution = new CfrSolver<>(game, variant).solve(pack.iterations());
                    maximumGap =
                            Math.max(
                                    maximumGap,
                                    PreflopAllInBestResponse.assess(game, solution).gap());
                    String scenario =
                            (changeHero ? "HERO:" : "OPPONENT:")
                                    + range.get(index).key()
                                    + (factor < 1 ? ":LOW" : ":HIGH");
                    for (WeightedCombo hero : varied.firstRange()) {
                        aggregates
                                .get(hero.key())
                                .include(
                                        PreflopSolutionPack.heroDecision(game, solution, hero),
                                        scenario);
                    }
                    scenarios++;
                }
            }
        }
        return new Report(
                pack.spotHash(),
                pack.solverVersion(),
                fractionalWeightChange,
                scenarios,
                pack.estimatedGameGapBb(),
                maximumGap,
                aggregates.values().stream().map(Aggregate::result).toList());
    }

    private static PreflopAllInSpot variedSpot(
            PreflopAllInSpot spot, boolean changeHero, int changedIndex, double factor) {
        List<WeightedCombo> original = changeHero ? spot.firstRange() : spot.secondRange();
        List<WeightedCombo> varied = new ArrayList<>(original);
        WeightedCombo changed = original.get(changedIndex);
        varied.set(
                changedIndex,
                new WeightedCombo(changed.first(), changed.second(), changed.weight() * factor));
        return new PreflopAllInSpot(
                spot.id(),
                spot.effectiveStackBb(),
                spot.smallBlindBb(),
                spot.firstSeat(),
                spot.secondSeat(),
                spot.priorActions(),
                changeHero ? varied : spot.firstRange(),
                changeHero ? spot.secondRange() : varied);
    }
}
