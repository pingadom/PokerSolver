package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Matched-budget policy sensitivity to declared range weights, not same-game stability. */
public final class SixMaxPreflopRangeWeightSensitivity {
    public record WeightChange(
            PreflopAllInSpot.Seat seat,
            String combo,
            double baselineWeight,
            double variantWeight) {}

    public record PolicyDifference(
            int informationSets,
            int reachedByEitherPolicy,
            double baselineExpectedDecisionEncounters,
            double variantExpectedDecisionEncounters,
            double uniformMeanTotalVariation,
            double maximumTotalVariation,
            String maximumDifferenceInformationSet,
            double reachWeightedTotalVariation,
            long visitedStates) {}

    public record Report(
            String interpretation,
            String baselinePackHash,
            String variantPackHash,
            String baselineSpotHash,
            String variantSpotHash,
            String solverVersion,
            int iterations,
            List<WeightChange> weightChanges,
            int jointDeals,
            double jointDealTotalVariation,
            double baselineNashConvBb,
            double variantNashConvBb,
            PolicyDifference policyDifference) {
        public Report {
            weightChanges = List.copyOf(weightChanges);
        }
    }

    private SixMaxPreflopRangeWeightSensitivity() {}

    public static Report assess(
            SixMaxPreflopSolutionPack baseline, SixMaxPreflopSolutionPack variant) {
        var first = baseline.rebuildGame();
        var second = variant.rebuildGame();
        if (!baseline.spot().rules().equals(variant.spot().rules())
                || !baseline.spot().rake().equals(variant.spot().rake())
                || !baseline.spot().continuationModel().equals(variant.spot().continuationModel())
                || !baseline.solverVersion().equals(variant.solverVersion())
                || baseline.solution().iterations() != variant.solution().iterations())
            throw new IllegalArgumentException(
                    "Sensitivity requires matching rules, rake, continuation, solver and iteration budget");
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(baseline.payoffMethod())
                || !MultiwaySolutionPack.EXACT_ENUMERATION.equals(variant.payoffMethod())
                || baseline.payoffs().size() != variant.payoffs().size())
            throw new IllegalArgumentException(
                    "Sensitivity requires identical exact physical payoffs");
        for (int index = 0; index < baseline.payoffs().size(); index++) {
            var old = baseline.payoffs().get(index);
            var changed = variant.payoffs().get(index);
            if (!old.dealtCombos().equals(changed.dealtCombos())
                    || old.activeMask() != changed.activeMask()
                    || old.estimate().trials() != changed.estimate().trials()
                    || !Arrays.equals(old.estimate().shares(), changed.estimate().shares())
                    || !Arrays.equals(
                            old.estimate().standardErrors(), changed.estimate().standardErrors()))
                throw new IllegalArgumentException(
                        "Sensitivity requires identical exact physical payoffs");
        }
        List<WeightChange> changes = new ArrayList<>();
        for (int seat = 0; seat < 6; seat++) {
            var oldRange = baseline.spot().ranges().get(seat);
            var newRange = variant.spot().ranges().get(seat);
            if (oldRange.size() != newRange.size())
                throw new IllegalArgumentException("Physical range support changed");
            for (int index = 0; index < oldRange.size(); index++) {
                var old = oldRange.get(index);
                var changed = newRange.get(index);
                if (!old.key().equals(changed.key()))
                    throw new IllegalArgumentException("Physical range support changed");
                if (old.weight() != changed.weight())
                    changes.add(
                            new WeightChange(
                                    PreflopAllInSpot.Seat.values()[seat],
                                    old.key(),
                                    old.weight(),
                                    changed.weight()));
            }
        }
        var walker = new Walker(first, second, baseline.solution(), variant.solution());
        var oldDeals = first.chanceOutcomes(first.initialState());
        var newDeals = second.chanceOutcomes(second.initialState());
        if (oldDeals.size() != newDeals.size())
            throw new IllegalArgumentException("Physical deal support changed");
        double jointVariation = 0;
        for (int index = 0; index < oldDeals.size(); index++) {
            var old = oldDeals.get(index);
            var changed = newDeals.get(index);
            if (!first.dealtHands(old.state()).stream()
                    .map(WeightedCombo::key)
                    .toList()
                    .equals(
                            second.dealtHands(changed.state()).stream()
                                    .map(WeightedCombo::key)
                                    .toList()))
                throw new IllegalArgumentException("Physical deal order changed");
            jointVariation += Math.abs(old.probability() - changed.probability()) * .5;
            walker.visit(old.state(), changed.state(), old.probability(), changed.probability());
        }
        int reached = 0, count = 0;
        double sum = 0, maximum = -1, oldMass = 0, newMass = 0, weighted = 0;
        String maximumKey = null;
        for (var key : baseline.solution().strategy().keySet().stream().sorted().toList()) {
            var oldRow = baseline.solution().strategy().get(key);
            var newRow = variant.solution().strategy().get(key);
            if (newRow == null || !oldRow.keySet().equals(newRow.keySet()))
                throw new IllegalArgumentException("Policy information sets or actions changed");
            double variation = 0;
            for (var action : oldRow.keySet().stream().sorted().toList())
                variation += Math.abs(oldRow.get(action) - newRow.get(action)) * .5;
            variation = Math.min(1, variation);
            var masses = walker.reach.get(key);
            if (masses != null) {
                oldMass += masses[0];
                newMass += masses[1];
                weighted += variation * (masses[0] + masses[1]) * .5;
                if (masses[0] > 0 || masses[1] > 0) reached++;
            }
            count++;
            sum += variation;
            if (variation > maximum) {
                maximum = variation;
                maximumKey = key;
            }
        }
        double symmetricMass = (oldMass + newMass) * .5;
        if (symmetricMass <= 0) throw new IllegalArgumentException("No numerical decision reach");
        return new Report(
                "Range-weight model sensitivity at a matched finite solver budget, not same-game stability, convergence or trainer admission. "
                        + "Physical support, exact payoffs, betting rules, rake and continuation are held fixed; each policy is validated in its own prior-weight game. "
                        + "Joint-deal total variation measures changed physical chance probabilities, including blockers. "
                        + "Policy total variation compares matching private information-set action rows. Reach weights use the symmetric mean of both games' own-policy decision encounter masses. "
                        + "Encounter mass is expected decision count, not hand probability. Uniform means and maxima include unreachable rows; double underflow is numerical zero. "
                        + "Different-game NashConv scores cannot establish policy improvement or a causal convergence-rate claim.",
                MultiwayPackJson.fullRoundContentHash(baseline),
                MultiwayPackJson.fullRoundContentHash(variant),
                baseline.spotHash(),
                variant.spotHash(),
                baseline.solverVersion(),
                baseline.solution().iterations(),
                changes,
                oldDeals.size(),
                Math.min(1, jointVariation),
                baseline.nashConvBb(),
                variant.nashConvBb(),
                new PolicyDifference(
                        count,
                        reached,
                        oldMass,
                        newMass,
                        sum / count,
                        Math.max(0, maximum),
                        maximumKey,
                        Math.min(1, weighted / symmetricMass),
                        walker.visited));
    }

    private static final class Walker {
        private final SixMaxPreflopCheckdownGame first, second;
        private final CfrSolution baseline, variant;
        private final Map<String, double[]> reach = new HashMap<>();
        private long visited = 1; // Both chance roots share the same bounded support.

        private Walker(
                SixMaxPreflopCheckdownGame first,
                SixMaxPreflopCheckdownGame second,
                CfrSolution baseline,
                CfrSolution variant) {
            this.first = first;
            this.second = second;
            this.baseline = baseline;
            this.variant = variant;
        }

        private void visit(
                SixMaxPreflopCheckdownGame.State old,
                SixMaxPreflopCheckdownGame.State changed,
                double oldMass,
                double newMass) {
            if (++visited > SixMaxPreflopCheckdownGame.MAX_DEAL_PUBLIC_STATES + 1L)
                throw new IllegalArgumentException("Sensitivity traversal exceeds state budget");
            if ((oldMass == 0 && newMass == 0) || first.isTerminal(old)) return;
            int player = first.currentPlayer(old);
            String key = player + ":" + first.informationSet(old);
            if (player != second.currentPlayer(changed)
                    || !key.equals(player + ":" + second.informationSet(changed))
                    || !first.legalActions(old).equals(second.legalActions(changed)))
                throw new IllegalArgumentException(
                        "Public tree or private information sets changed");
            var masses = reach.computeIfAbsent(key, ignored -> new double[2]);
            masses[0] += oldMass;
            masses[1] += newMass;
            for (var action : first.legalActions(old))
                visit(
                        first.afterAction(old, action),
                        second.afterAction(changed, action),
                        oldMass * baseline.strategy().get(key).get(action),
                        newMass * variant.strategy().get(key).get(action));
        }
    }
}
