package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuitConditionalRefinement.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared bounded optimization; model wrappers own lineage, schemas and publication decisions. */
final class SixMaxConditionalRefinementEngine {
    private static final int MAX_LOCAL_STATES = 1000;
    private static final double COMPARISON_TOLERANCE_BB = 1e-9;

    record Computed(
            CfrSolution candidate,
            Set<String> changedKeys,
            List<Branch> branches,
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<String> reasons) {}

    record Candidate(
            SixMaxFlopConditionalDiagnostics.History history,
            SixMaxFlopConditionalDiagnostics.Case signal) {}

    private SixMaxConditionalRefinementEngine() {}

    static Computed refine(
            SixMaxOneBetFlopGame game,
            CfrSolution original,
            SixMaxFlopConditionalDiagnostics.Result before,
            List<Candidate> selected,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(original);
        var updated = new LinkedHashMap<>(original.strategy());
        var changedKeys = new HashSet<String>();
        var branches = new ArrayList<Branch>();
        for (var candidate : selected) {
            var h = candidate.history();
            var s = candidate.signal();
            var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, h.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(game, transition, s.observation());
            if (posterior.roots().isEmpty()
                    || posterior.signalProbability() != s.signalProbabilityGivenHistory())
                throw new IllegalStateException("Selected conditional posterior differs");
            var local =
                    new SixMaxRankTextureConditionalAudit.ConditionalGame(game, posterior.roots());
            var trials = new ArrayList<Trial>();
            CfrSolution best = null;
            var bestQuality = s.quality();
            for (int iterations : settings.iterationBudgets()) {
                var solver =
                        new MultiPlayerCfrSolver<>(
                                local,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                var solved = solver.solve(iterations);
                if (MultiPlayerStrategyCompletion.uniformAtUnseen(local, solved, MAX_LOCAL_STATES)
                                .addedInformationSets()
                        != 0) throw new IllegalStateException("Local solve has incomplete support");
                var quality =
                        SixMaxConnectedPreflopAudit.Quality.of(
                                MultiPlayerInformationSetBestResponse.assess(local, solved));
                trials.add(
                        new Trial(
                                iterations,
                                SixMaxConnectedPostflopAudit.solutionHash(solved),
                                quality,
                                solver.statistics(),
                                solver.inactiveUtilityPrunedNodes()));
                if (quality.nashConvBb() < bestQuality.nashConvBb()) {
                    best = solved;
                    bestQuality = quality;
                }
                if (bestQuality.nashConvBb() <= settings.targetGapBb()) break;
            }
            int replaced = 0;
            if (best != null) {
                for (var row : best.strategy().entrySet()) {
                    if (!row.getKey().contains(":postflop:" + game.payoffView().namespace() + ":")
                            || !updated.containsKey(row.getKey())
                            || !changedKeys.add(row.getKey()))
                        throw new IllegalStateException("Local rows are foreign or overlap");
                    updated.put(row.getKey(), row.getValue());
                    replaced++;
                }
            }
            var branch =
                    new Branch(
                            h.history(),
                            s.observation(),
                            s.observationKey(),
                            h.historyProbability(),
                            s.signalProbabilityGivenHistory(),
                            posterior.roots().size(),
                            bestQuality.nashConvBb() <= settings.targetGapBb()
                                    ? "TARGET_MET"
                                    : best == null ? "NO_LOCAL_IMPROVEMENT" : "BUDGET_EXHAUSTED",
                            replaced,
                            best == null ? 0 : best.iterations(),
                            s.quality(),
                            bestQuality,
                            trials);
            branches.add(branch);
            progress.accept(branch);
        }
        var candidate = new CfrSolution(original.iterations(), updated);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, candidate, SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES);
        if (complete.addedInformationSets() != 0
                || !candidate.strategy().keySet().equals(original.strategy().keySet())
                || !pre.equals(SixMaxPreflopContinuationFeedback.preflopPolicy(candidate)))
            throw new IllegalStateException(
                    "Refinement changed complete support or frozen preflop");
        for (var row : original.strategy().entrySet())
            if (!changedKeys.contains(row.getKey())
                    && !row.getValue().equals(candidate.strategy().get(row.getKey())))
                throw new IllegalStateException("Refinement changed an unselected row");
        var after = SixMaxFlopConditionalDiagnostics.assess(game, candidate);
        verifyCases(before, after, branches);
        var reasons = rejectionReasons(before, after, branches, settings);
        return new Computed(
                candidate, Set.copyOf(changedKeys), List.copyOf(branches), before, after, reasons);
    }

    static List<Candidate> select(
            SixMaxFlopConditionalDiagnostics.Result before,
            Settings settings,
            java.util.function.Predicate<Candidate> eligible) {
        return select(before, settings, eligible, false);
    }

    static List<Candidate> select(
            SixMaxFlopConditionalDiagnostics.Result before,
            Settings settings,
            java.util.function.Predicate<Candidate> eligible,
            boolean rankByReachOnly) {
        var candidates = new ArrayList<Candidate>();
        for (var history : before.histories())
            for (var signal : history.signals())
                if (signal.quality() != null && eligible.test(new Candidate(history, signal)))
                    candidates.add(new Candidate(history, signal));
        // Stable order of the bound public menu then observation index resolves equal priorities.
        var largest = new ArrayList<>(candidates);
        largest.sort(
                Comparator.comparingDouble((Candidate c) -> c.signal().quality().nashConvBb())
                        .reversed());
        candidates.sort(
                Comparator.comparingDouble(
                                (Candidate c) ->
                                        (rankByReachOnly ? 1 : c.signal().quality().nashConvBb())
                                                * c.history().historyProbability()
                                                * c.signal().signalProbabilityGivenHistory())
                        .reversed());
        int count = Math.min(settings.maximumCases(), candidates.size());
        if (settings.priority() != Priority.BALANCED_GAP_AND_REACH) {
            var ordered = settings.priority() == Priority.LARGEST_GAP ? largest : candidates;
            return List.copyOf(ordered.subList(0, count));
        }
        var balanced = new ArrayList<Candidate>();
        var used = new HashSet<String>();
        int gapIndex = 0, reachIndex = 0;
        while (balanced.size() < count) {
            var queue = balanced.size() % 2 == 0 ? largest : candidates;
            int index = balanced.size() % 2 == 0 ? gapIndex : reachIndex;
            Candidate next;
            do {
                next = queue.get(index++);
            } while (!used.add(
                    next.history().history().toString() + ":" + next.signal().observation()));
            if (balanced.size() % 2 == 0) gapIndex = index;
            else reachIndex = index;
            balanced.add(next);
        }
        return List.copyOf(balanced);
    }

    private static void verifyCases(
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<Branch> branches) {
        if (before.histories().size() != after.histories().size())
            throw new IllegalStateException("Conditional support changed");
        for (int i = 0; i < before.histories().size(); i++) {
            var old = before.histories().get(i);
            var now = after.histories().get(i);
            if (!old.history().equals(now.history())
                    || !old.status().equals(now.status())
                    || old.historyProbability() != now.historyProbability()
                    || old.signals().size() != now.signals().size())
                throw new IllegalStateException("Frozen preflop posterior changed");
            for (int j = 0; j < old.signals().size(); j++) {
                var a = old.signals().get(j);
                var b = now.signals().get(j);
                var branch =
                        branches.stream()
                                .filter(
                                        c ->
                                                c.history().equals(old.history())
                                                        && c.observation() == a.observation())
                                .findFirst();
                if (branch.isEmpty()) {
                    if (!a.equals(b)) throw new IllegalStateException("Unselected case changed");
                } else {
                    if (a.signalProbabilityGivenHistory() != b.signalProbabilityGivenHistory()
                            || !a.firstMarginal().equals(b.firstMarginal())
                            || !a.secondMarginal().equals(b.secondMarginal())
                            || !branch.orElseThrow().after().equals(b.quality()))
                        throw new IllegalStateException("Assembled case differs from local solve");
                }
            }
        }
    }

    static List<String> rejectionReasons(
            SixMaxFlopConditionalDiagnostics.Result before,
            SixMaxFlopConditionalDiagnostics.Result after,
            List<Branch> branches,
            Settings settings) {
        var reasons = new ArrayList<String>();
        if (branches.isEmpty()) reasons.add("NO_CASES_ABOVE_TARGET");
        if (branches.stream().anyMatch(b -> b.after().nashConvBb() > settings.targetGapBb()))
            reasons.add("SELECTED_LOCAL_TARGET_NOT_MET");
        if (after.parentWitness().parentQuality().nashConvBb()
                > before.parentWitness().parentQuality().nashConvBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("PARENT_NASHCONV_REGRESSION");
        if (after.parentWitness().reachWeightedLocalNashConvBb()
                > before.parentWitness().reachWeightedLocalNashConvBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("REACH_WEIGHTED_LOCAL_REGRESSION");
        if (after.summary().largestConditionalGapBb()
                > before.summary().largestConditionalGapBb() + COMPARISON_TOLERANCE_BB)
            reasons.add("MAXIMUM_LOCAL_REGRESSION");
        return List.copyOf(reasons);
    }
}
