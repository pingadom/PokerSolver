package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Same-game policy disagreement, including reached decisions rather than only unseen rows. */
public final class SixMaxConnectedPolicyStability {
    public record Stage(
            String stage,
            int informationSets,
            int reachedByEitherPolicy,
            double firstExpectedDecisionEncounters,
            double secondExpectedDecisionEncounters,
            double uniformMeanTotalVariation,
            double maximumTotalVariation,
            String maximumDifferenceInformationSet,
            Double reachWeightedTotalVariation) {}

    public record Report(
            String interpretation,
            String firstSolutionHash,
            String secondSolutionHash,
            long visitedStates,
            List<Stage> stages) {
        public Report {
            stages = List.copyOf(stages);
        }
    }

    private SixMaxConnectedPolicyStability() {}

    public static Report assess(
            SixMaxConnectedPreflopGame game,
            CfrSolution first,
            CfrSolution second,
            SixMaxContinuationStudyBudget budget) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(budget, "budget").validate(game);
        for (var policy : List.of(first, second))
            if (MultiPlayerStrategyCompletion.uniformAtUnseen(
                                    game, policy, budget.maximumCompleteTreeStates())
                            .addedInformationSets()
                    != 0)
                throw new IllegalArgumentException(
                        "Stability comparison requires complete same-game policies");
        var walker = new Walker(game, first, second, budget.maximumCompleteTreeStates());
        walker.visit(game.initialState(), 1, 1);
        var stages = new ArrayList<Stage>();
        for (var stage : List.of("PREFLOP", "POSTFLOP")) {
            int count = 0, reached = 0;
            double sum = 0, maximum = -1, firstMass = 0, secondMass = 0, weighted = 0;
            String maximumKey = null;
            for (var key : first.strategy().keySet().stream().sorted().toList()) {
                if (!stage.equals(key.contains(":postflop:") ? "POSTFLOP" : "PREFLOP")) continue;
                double variation = 0;
                var firstRow = first.strategy().get(key);
                var secondRow = second.strategy().get(key);
                for (var action : firstRow.keySet().stream().sorted().toList())
                    variation += Math.abs(firstRow.get(action) - secondRow.get(action)) * .5;
                variation = Math.min(1, variation);
                var mass = walker.reach.get(key);
                if (mass != null) {
                    firstMass += mass[0];
                    secondMass += mass[1];
                    weighted += variation * (mass[0] + mass[1]) * .5;
                    if (mass[0] > 0 || mass[1] > 0) reached++;
                }
                count++;
                sum += variation;
                if (variation > maximum) {
                    maximum = variation;
                    maximumKey = key;
                }
            }
            double symmetricMass = (firstMass + secondMass) * .5;
            stages.add(
                    new Stage(
                            stage,
                            count,
                            reached,
                            firstMass,
                            secondMass,
                            count == 0 ? 0 : sum / count,
                            Math.max(0, maximum),
                            maximumKey,
                            symmetricMass == 0 ? null : Math.min(1, weighted / symmetricMass)));
        }
        return new Report(
                "Same-game action-frequency disagreement, not a convergence certificate or trainer admission. "
                        + "Each row uses total variation (half the sum of absolute action-probability differences). "
                        + "Reach weighting uses the symmetric mean of both policies' full physical on-policy decision encounter masses, "
                        + "including own actions, opponents and chance. Each stage is normalized separately; postflop disagreement is not diluted by rare physical flops. "
                        + "Encounter mass is expected decision count, not hand probability. Uniform means and maxima include unreachable rows. "
                        + "Null weighted disagreement means neither policy reaches any decision in that stage. Double underflow is treated as zero numerical reach.",
                SixMaxConnectedPostflopAudit.solutionHash(first),
                SixMaxConnectedPostflopAudit.solutionHash(second),
                walker.visited,
                stages);
    }

    private static final class Walker {
        private final SixMaxConnectedPreflopGame game;
        private final CfrSolution first, second;
        private final long maximum;
        private final Map<String, double[]> reach = new HashMap<>();
        private long visited;

        private Walker(
                SixMaxConnectedPreflopGame game,
                CfrSolution first,
                CfrSolution second,
                long maximum) {
            this.game = game;
            this.first = first;
            this.second = second;
            this.maximum = maximum;
        }

        private void visit(
                SixMaxConnectedPreflopGame.State state, double firstMass, double secondMass) {
            if (++visited > maximum)
                throw new IllegalArgumentException("Stability traversal exceeds state budget");
            if ((firstMass == 0 && secondMass == 0) || game.isTerminal(state)) return;
            int player = game.currentPlayer(state);
            if (player == -1) {
                for (var outcome : game.chanceOutcomes(state))
                    visit(
                            outcome.state(),
                            firstMass * outcome.probability(),
                            secondMass * outcome.probability());
                return;
            }
            var key = player + ":" + game.informationSet(state);
            var masses = reach.computeIfAbsent(key, ignored -> new double[2]);
            masses[0] += firstMass;
            masses[1] += secondMass;
            var firstRow = first.strategy().get(key);
            var secondRow = second.strategy().get(key);
            for (var action : game.legalActions(state))
                visit(
                        game.afterAction(state, action),
                        firstMass * firstRow.get(action),
                        secondMass * secondRow.get(action));
        }
    }
}
