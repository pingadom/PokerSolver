package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxHeadsUpPreflopDecisionValues.Row;
import com.pokerlab.solver.SixMaxHeadsUpPreflopStudy.*;
import java.util.*;

/** Identical local decision gates for both belief models; no claim about source-history reach. */
final class SixMaxHeadsUpDecisionScreen {
    record Screen(
            List<Reference> references,
            List<Decision> decisions,
            int material,
            int stable,
            List<String> rejectionReasons) {
        Screen {
            references = List.copyOf(references);
            decisions = List.copyOf(decisions);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    private static final List<Integer> REFERENCE_BUDGETS =
            SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS;

    private SixMaxHeadsUpDecisionScreen() {}

    static Screen assess(SixMaxHeadsUpDecisionGame game, CfrSolution policy) {
        var primary = SixMaxHeadsUpPreflopDecisionValues.assess(game, policy);
        var references = new ArrayList<Reference>();
        var comparisons = new TreeMap<String, List<Comparison>>();
        primary.forEach(d -> comparisons.put(d.row().informationSet(), new ArrayList<>()));
        for (int budget : REFERENCE_BUDGETS) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.CFR_PLUS,
                            MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
            var ref = solver.solve(budget);
            var quality = MultiPlayerInformationSetBestResponse.assess(game, ref);
            references.add(
                    new Reference(
                            budget,
                            SixMaxConnectedPostflopAudit.solutionHash(ref),
                            quality,
                            solver.statistics()));
            var refRows = new TreeMap<String, SixMaxHeadsUpPreflopDecisionValues.Decision>();
            SixMaxHeadsUpPreflopDecisionValues.assess(game, ref)
                    .forEach(d -> refRows.put(d.row().informationSet(), d));
            if (!refRows.keySet().equals(comparisons.keySet()))
                throw new IllegalArgumentException("Reference policy support differs");
            for (var d : primary)
                if (material(d.row())) {
                    var other = refRows.get(d.row().informationSet());
                    var values = SixMaxHeadsUpPreflopDecisionValues.values(game, d.roots(), ref);
                    double drift = 0, primaryUnderRef = 0, refUnderPrimary = 0;
                    for (String action : d.row().values().actionEvBb().keySet()) {
                        double a = d.row().values().actionEvBb().get(action),
                                b = values.actionEvBb().get(action);
                        drift = Math.max(drift, Math.abs(a - b));
                        primaryUnderRef += d.row().values().frequencies().get(action) * b;
                        refUnderPrimary += values.frequencies().get(action) * a;
                    }
                    double primaryRegret =
                            Math.max(
                                    0,
                                    Collections.max(values.actionEvBb().values())
                                            - primaryUnderRef);
                    double refRegret =
                            Math.max(
                                    0,
                                    Collections.max(d.row().values().actionEvBb().values())
                                            - refUnderPrimary);
                    Double tv =
                            other.roots().isEmpty()
                                    ? null
                                    : SixMaxHeadsUpPreflopDecisionValues.posteriorDistance(
                                            d.roots(), other.roots());
                    var failures = new ArrayList<String>();
                    if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                        failures.add("REFERENCE_LOCAL_GAP");
                    if (drift > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("ACTION_EV_DRIFT");
                    if (primaryRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("PRIMARY_MIX_REGRET");
                    if (refRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("REFERENCE_MIX_REGRET");
                    if (tv == null) failures.add("ZERO_REFERENCE_REACH");
                    else if (tv > SixMaxSuitDecisionStability.POSTERIOR_TOLERANCE)
                        failures.add("PRIVATE_POSTERIOR_DRIFT");
                    comparisons
                            .get(d.row().informationSet())
                            .add(
                                    new Comparison(
                                            budget,
                                            other.row().status(),
                                            tv,
                                            values,
                                            drift,
                                            primaryRegret,
                                            refRegret,
                                            failures));
                }
        }
        var decisions = new ArrayList<Decision>();
        int material = 0, stable = 0;
        var privateCombos = new TreeMap<Integer, Set<String>>();
        for (var d : primary) {
            boolean significant = material(d.row());
            var checked = comparisons.get(d.row().informationSet());
            boolean pass =
                    significant
                            && checked.size() == REFERENCE_BUDGETS.size()
                            && checked.stream().allMatch(c -> c.failures().isEmpty())
                            && d.row().values().decisionRegretBb()
                                    <= SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB;
            if (significant) {
                material++;
                privateCombos
                        .computeIfAbsent(d.row().actor().ordinal(), k -> new HashSet<>())
                        .add(d.row().ownHand());
            }
            if (pass) stable++;
            decisions.add(new Decision(d.row(), significant, pass, checked));
        }
        var rejected = new ArrayList<String>();
        if (game.activeSeats().stream()
                .anyMatch(s -> privateCombos.getOrDefault(s.ordinal(), Set.of()).size() < 2))
            rejected.add("INSUFFICIENT_MATERIAL_PRIVATE_COMBOS");
        if (material == 0 || stable != material) rejected.add("UNSTABLE_MATERIAL_DECISIONS");
        return new Screen(references, decisions, material, stable, rejected);
    }

    private static boolean material(Row row) {
        return row.values() != null
                && row.prefixProbability() >= SixMaxSuitDecisionStability.MIN_PREFIX_REACH
                && row.ownHandProbabilityGivenPrefix()
                        >= SixMaxSuitDecisionStability.MIN_OWN_HAND_MASS;
    }
}
