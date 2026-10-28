package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.Phase;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.util.*;

/** Policy checks for owned size-menu paths; opponent actions stay in the posterior. */
final class SizeMenuPolicyChecks {
    record Selection(
            int hero,
            String heroCards,
            String publicPreflopHistory,
            List<String> flopActions,
            String informationSet) {
        Selection {
            flopActions = List.copyOf(flopActions);
        }
    }

    private SizeMenuPolicyChecks() {}

    private static double product(double mass, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new IllegalArgumentException("Invalid size-menu path probability");
        double next = mass * probability;
        if (mass > 0 && probability > 0 && next == 0)
            throw new IllegalArgumentException("Positive path underflow");
        return next;
    }

    private static void validatePolicy(
            SixMaxSuppliedFlopSizeMenuStudy.Report source, CfrSolution policy) {
        var expected = source.candidate().strategy();
        if (!policy.strategy().keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Incomplete size-menu policy");
        for (var entry : expected.entrySet()) {
            var row = policy.strategy().get(entry.getKey());
            if (!row.keySet().equals(entry.getValue().keySet())
                    || row.values().stream()
                            .anyMatch(p -> p == null || !Double.isFinite(p) || p < 0 || p > 1)
                    || Math.abs(row.values().stream().mapToDouble(Double::doubleValue).sum() - 1)
                            > 1e-9)
                throw new IllegalArgumentException("Invalid size-menu policy row");
        }
    }

    static Check assess(
            SixMaxSuppliedFlopSizeMenuGame game,
            SixMaxSuppliedFlopSizeMenuStudy.Report source,
            Selection q,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            CfrSolution policy) {
        if (!game.binding().equals(source.binding()))
            throw new IllegalArgumentException("Size-menu assessment game differs");
        validatePolicy(source, policy);
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(q.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow();
        var allRoots = new ArrayList<ChanceOutcome<State>>();
        var raw = new ArrayList<SixMaxSuppliedFlopBettingStudy.WorldMass>();
        var selectedStates = new ArrayList<State>();
        var selectedMasses = new ArrayList<Double>();
        double reachedBoundary = 0, actual = 0, total = 0;
        for (var world : handoff.worlds()) {
            double cf =
                    product(
                            world.originalJointProbability(),
                            world.physicalFlopProbabilityGivenDeal());
            double reached = cf;
            var parent = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            for (String action : handoff.continuationActions()) {
                int actor = game.source().currentPlayer(parent);
                double p =
                        policy.at(
                                        actor,
                                        SixMaxSuppliedFlopSizeMenuGame.PREFLOP_KEY
                                                + game.source().informationSet(parent))
                                .get(action);
                reached = product(reached, p);
                if (actor != q.hero()) cf = product(cf, p);
                parent = game.source().afterAction(parent, action);
            }
            var state =
                    new State(world.originalDealIndex(), handoff.publicHistory(), Phase.FLOP, "");
            if (reached > 0) {
                raw.add(
                        new SixMaxSuppliedFlopBettingStudy.WorldMass(
                                world.originalDealIndex(), reached));
                allRoots.add(new ChanceOutcome<>(state, reached));
                reachedBoundary += reached;
            }
            if (world.sixDealtCombos().get(q.hero()).equals(q.heroCards())) {
                for (String action : q.flopActions()) {
                    int actor = game.currentPlayer(state);
                    double p = policy.at(actor, game.informationSet(state)).get(action);
                    reached = product(reached, p);
                    if (actor != q.hero()) cf = product(cf, p);
                    state = game.afterAction(state, action);
                }
                if (game.currentPlayer(state) != q.hero()
                        || !q.informationSet().equals(q.hero() + ":" + game.informationSet(state)))
                    throw new IllegalArgumentException("Owned selected path differs");
                selectedStates.add(state);
                selectedMasses.add(cf);
                actual += reached;
                total += cf;
            }
        }
        double physical = handoff.physicalFlopMass();
        var reachedWorlds = new ArrayList<SixMaxSuppliedFlopBettingStudy.ReachedWorld>();
        MultiPlayerInformationSetBestResponse.Report localQuality = null;
        boolean supported =
                reachedBoundary / physical
                        >= SixMaxSuppliedFlopBettingStudy.MIN_CONDITIONAL_BOUNDARY_REACH;
        if (supported) {
            var normalized = new ArrayList<ChanceOutcome<State>>();
            for (int i = 0; i < allRoots.size(); i++) {
                var root = allRoots.get(i);
                double p = root.probability() / reachedBoundary;
                normalized.add(new ChanceOutcome<>(root.state(), p));
                reachedWorlds.add(
                        new SixMaxSuppliedFlopBettingStudy.ReachedWorld(
                                raw.get(i).originalDealIndex(), root.probability(), p));
            }
            localQuality =
                    MultiPlayerInformationSetBestResponse.assess(
                            new ForcedRoot(game, List.copyOf(normalized)), policy);
        }
        var local =
                new SixMaxSuppliedFlopBettingStudy.LocalAudit(
                        SixMaxSuppliedFlopBettingStudy.LOCAL_SCOPE,
                        handoff.publicHistory(),
                        reachedBoundary,
                        physical,
                        reachedBoundary / physical,
                        SixMaxSuppliedFlopBettingStudy.MIN_CONDITIONAL_BOUNDARY_REACH,
                        supported
                                ? SixMaxSuppliedFlopBettingStudy.Conditioning
                                        .ABOVE_CANDIDATE_REACH_FLOOR
                                : SixMaxSuppliedFlopBettingStudy.Conditioning
                                        .BELOW_CANDIDATE_REACH_FLOOR,
                        raw,
                        reachedWorlds,
                        localQuality);
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        double br = quality.bestResponseUtilitiesBb().get(q.hero());
        double upper = actions.getFirst().diagnostic().globalHeroUpperValue();
        double margin = upper + SixMaxSuppliedFlopActionBounds.SECURITY_SLACK - br;
        boolean included = margin >= -BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        var failures = new ArrayList<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        if (localQuality == null
                || localQuality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("REACHED_FLOP_GAP");
        if (actual / physical < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
        if (total / physical < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        Double regret = null, violation = null;
        boolean checked = included && total > 0;
        if (total > 0) {
            for (var action : actions) {
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (int i = 0; i < selectedStates.size(); i++)
                    if (selectedMasses.get(i) > 0)
                        roots.add(
                                new ChanceOutcome<>(
                                        game.afterAction(selectedStates.get(i), action.action()),
                                        selectedMasses.get(i) / total));
                values.put(
                        action.action(),
                        MultiPlayerInformationSetBestResponse.assess(
                                        new ForcedRoot(game, List.copyOf(roots)), policy)
                                .bestResponseUtilitiesBb()
                                .get(q.hero()));
            }
            double best = Collections.max(values.values());
            values.forEach((a, v) -> losses.put(a, Math.max(0, best - v)));
            var row = policy.at(q.hero(), game.informationSet(selectedStates.getFirst()));
            regret =
                    losses.entrySet().stream()
                            .mapToDouble(e -> row.get(e.getKey()) * e.getValue())
                            .sum();
            if (regret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                failures.add("SELECTED_DECISION_REGRET");
            if (checked) {
                double worst = 0;
                for (var a : actions)
                    worst =
                            Math.max(
                                    worst,
                                    Math.max(
                                            Math.max(
                                                    a.lowerUtilityBb() - values.get(a.action()),
                                                    values.get(a.action()) - a.upperUtilityBb()),
                                            Math.max(
                                                    a.lowerLossBb() - losses.get(a.action()),
                                                    losses.get(a.action()) - a.upperLossBb())));
                violation = worst;
                if (worst > BoundedLinearProgram.CERTIFICATE_TOLERANCE)
                    failures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
            }
        }
        return new Check(
                policy.iterations(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                quality,
                local,
                actual,
                actual / physical,
                total / physical,
                br,
                upper,
                margin,
                included,
                checked,
                violation,
                values,
                losses,
                regret,
                failures);
    }

    private record ForcedRoot(SixMaxSuppliedFlopSizeMenuGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT = new State(-202, "", Phase.FLOP, "size-menu-quality-root");

        public int playerCount() {
            return game.playerCount();
        }

        public State initialState() {
            return ROOT;
        }

        public boolean isTerminal(State state) {
            return !ROOT.equals(state) && game.isTerminal(state);
        }

        public double[] terminalUtilities(State state) {
            return game.terminalUtilities(state);
        }

        public int currentPlayer(State state) {
            return ROOT.equals(state) ? -1 : game.currentPlayer(state);
        }

        public List<String> legalActions(State state) {
            return game.legalActions(state);
        }

        public String informationSet(State state) {
            return game.informationSet(state);
        }

        public State afterAction(State state, String action) {
            return game.afterAction(state, action);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State state) {
            if (!ROOT.equals(state))
                throw new IllegalArgumentException("Owned quality root required");
            return roots;
        }
    }
}
