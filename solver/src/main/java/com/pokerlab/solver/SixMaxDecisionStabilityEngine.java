package com.pokerlab.solver;

import static com.pokerlab.solver.SixMaxSuitDecisionStability.*;

import com.pokerlab.solver.SixMaxSuitDecisionStability.*;
import java.util.*;
import java.util.function.Consumer;

/** Shared full-posterior references and own-card decisions, independent of artifact schemas. */
final class SixMaxDecisionStabilityEngine {
    record Computed(
            Map<String, Integer> counts,
            double historyReach,
            double eligibleReach,
            double retainedReach,
            List<Branch> branches) {}

    private record Candidate(
            SixMaxFlopConditionalDiagnostics.History history,
            SixMaxFlopConditionalDiagnostics.Case signal) {
        double weight() {
            return history.historyProbability() * signal.signalProbabilityGivenHistory();
        }
    }

    private SixMaxDecisionStabilityEngine() {}

    static Computed screen(
            SixMaxOneBetFlopGame game,
            CfrSolution policy,
            SixMaxFlopConditionalDiagnostics.Result diagnostics,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        var counts = new TreeMap<String, Integer>();
        var candidates = new ArrayList<Candidate>();
        double eligibleReach = 0, historyReach = 0;
        for (var history : diagnostics.histories()) {
            historyReach += history.historyProbability();
            for (var signal : history.signals()) {
                if (!game.payoffView().key(signal.observation()).startsWith("board:")) continue;
                String reason = eligibility(history, signal, settings);
                counts.merge(reason, 1, Integer::sum);
                if (reason.equals("ELIGIBLE")) {
                    var candidate = new Candidate(history, signal);
                    candidates.add(candidate);
                    eligibleReach += candidate.weight();
                }
            }
        }
        // Stable sort retains menu/observation order when joint reach ties.
        candidates.sort(Comparator.comparingDouble(Candidate::weight).reversed());
        var branches = new ArrayList<Branch>();
        double retainedReach = 0;
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(policy);
        for (var candidate : candidates.stream().limit(settings.maximumCases()).toList()) {
            var h = candidate.history();
            var s = candidate.signal();
            var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, h.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(game, transition, s.observation());
            if (posterior.signalProbability() != s.signalProbabilityGivenHistory())
                throw new IllegalStateException(
                        "Decision screen posterior differs from validated audit");
            var local =
                    new SixMaxRankTextureConditionalAudit.ConditionalGame(game, posterior.roots());
            var primary = SixMaxOneBetDecisionValues.assess(game, posterior.roots(), policy);
            var refs = new ArrayList<Reference>();
            var policies = new ArrayList<CfrSolution>();
            var decisions = new ArrayList<Map<String, SixMaxOneBetDecisionValues.Decision>>();
            for (int budget : settings.freshBudgets()) {
                var solver =
                        new MultiPlayerCfrSolver<>(
                                local,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                var solved = solver.solve(budget);
                if (MultiPlayerStrategyCompletion.uniformAtUnseen(local, solved, 1000)
                                .addedInformationSets()
                        != 0) throw new IllegalStateException("Incomplete fresh reference policy");
                refs.add(
                        new Reference(
                                budget,
                                SixMaxConnectedPostflopAudit.solutionHash(solved),
                                SixMaxConnectedPreflopAudit.Quality.of(
                                        MultiPlayerInformationSetBestResponse.assess(
                                                local, solved)),
                                solver.statistics(),
                                solver.inactiveUtilityPrunedNodes()));
                policies.add(solved);
                var indexed = new TreeMap<String, SixMaxOneBetDecisionValues.Decision>();
                SixMaxOneBetDecisionValues.assess(game, posterior.roots(), solved)
                        .forEach(d -> indexed.put(d.row().informationSet(), d));
                decisions.add(indexed);
            }
            var questions = new ArrayList<Question>();
            var branchFailures = new TreeSet<String>();
            var materialSeats = new HashSet<com.pokerlab.solver.PreflopAllInSpot.Seat>();
            for (var question : primary) {
                var row = question.row();
                boolean material =
                        row.status().equals("REACHED")
                                && row.prefixProbability() >= MIN_PREFIX_REACH
                                && row.ownHandProbabilityGivenPrefix() >= MIN_OWN_HAND_MASS;
                var failures = new TreeSet<String>();
                var comparisons = new ArrayList<Comparison>();
                if (material) {
                    materialSeats.add(row.actor());
                    if (row.values().decisionRegretBb() > DECISION_TOLERANCE_BB)
                        failures.add("PRIMARY_DECISION_REGRET");
                    for (int i = 0; i < refs.size(); i++) {
                        var reference = refs.get(i);
                        var other = decisions.get(i).get(row.informationSet());
                        var transferred =
                                SixMaxOneBetDecisionValues.values(
                                        game, question.roots(), policies.get(i));
                        var comparison =
                                SixMaxSuitDecisionStability.compare(
                                        reference, question, other, transferred);
                        comparisons.add(comparison);
                        failures.addAll(comparison.failures());
                    }
                    if (!failures.isEmpty()) branchFailures.add("MATERIAL_DECISION_UNSTABLE");
                }
                questions.add(
                        new Question(
                                row,
                                material,
                                material && failures.isEmpty(),
                                comparisons,
                                new ArrayList<>(failures)));
            }
            if (materialSeats.size() != 2) branchFailures.add("BOTH_ACTIVE_SEATS_NOT_REPRESENTED");
            boolean retained = branchFailures.isEmpty();
            var branch =
                    new Branch(
                            h.history(),
                            s.observation(),
                            s.observationKey(),
                            h.historyProbability(),
                            s.signalProbabilityGivenHistory(),
                            s.quality(),
                            refs,
                            questions,
                            retained,
                            new ArrayList<>(branchFailures));
            branches.add(branch);
            if (retained) retainedReach += candidate.weight();
            progress.accept(branch);
        }
        return new Computed(
                Map.copyOf(counts),
                historyReach,
                eligibleReach,
                retainedReach,
                List.copyOf(branches));
    }

    private static String eligibility(
            SixMaxFlopConditionalDiagnostics.History h,
            SixMaxFlopConditionalDiagnostics.Case s,
            Settings settings) {
        if (!s.status().equals("AUDITED")) return "NO_REACHED_PRIVATE_SUPPORT";
        if (h.historyProbability() < MIN_HISTORY_REACH) return "LOW_HISTORY_REACH";
        if (s.firstCombosAtFivePercent() < settings.minimumMaterialCombos()
                || s.secondCombosAtFivePercent() < settings.minimumMaterialCombos())
            return "INSUFFICIENT_MATERIAL_COMBOS";
        if (s.quality().nashConvBb() > LOCAL_GAP_BB) return "PRIMARY_LOCAL_GAP";
        return "ELIGIBLE";
    }
}
