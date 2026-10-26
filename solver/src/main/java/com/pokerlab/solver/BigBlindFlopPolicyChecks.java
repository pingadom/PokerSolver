package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.util.*;

/** BB-specific owned paths; original full-parent, reach, inclusion and accuracy gates. */
final class BigBlindFlopPolicyChecks {
    private BigBlindFlopPolicyChecks() {}

    record OwnedQuestion(
            SixMaxSuppliedFlopHandoff.Audit handoff,
            List<State> states,
            List<SixMaxSuppliedFlopHandoff.World> worlds,
            String informationSet,
            SixMaxSuppliedFlopBettingGame.PublicFlopState context) {}

    private static OwnedQuestion question(
            SixMaxSuppliedFlopBettingGame game, SixMaxRefinedConditionalStudy.Request request) {
        if (request.hero() != 5
                || !List.of(List.of(), List.of("check", "bet")).contains(request.flopActions()))
            throw new IllegalArgumentException("Explicit BB question required");
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(request.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown owned handoff"));
        var states = new ArrayList<State>();
        var worlds = new ArrayList<SixMaxSuppliedFlopHandoff.World>();
        String key = null;
        SixMaxSuppliedFlopBettingGame.PublicFlopState context = null;
        for (var world : handoff.worlds()) {
            if (!world.sixDealtCombos().get(request.hero()).equals(request.heroCards())) continue;
            var state =
                    new State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            if (game.currentPlayer(state) != 5)
                throw new IllegalArgumentException("BB must act first at selected flop");
            for (String action : request.flopActions()) state = game.afterAction(state, action);
            if (game.currentPlayer(state) != request.hero()
                    || !game.legalActions(state)
                            .equals(
                                    request.flopActions().isEmpty()
                                                    || request.flopActions()
                                                            .equals(List.of("check"))
                                            ? List.of("check", "bet")
                                            : List.of("fold", "call")))
                throw new IllegalArgumentException("Owned BB check/bet decision required");
            String found = request.hero() + ":" + game.informationSet(state);
            var view = game.publicFlopState(state);
            if ((key != null && !key.equals(found)) || (context != null && !context.equals(view)))
                throw new IllegalStateException("Inconsistent owned BB question");
            key = found;
            context = view;
            states.add(state);
            worlds.add(world);
        }
        if (states.isEmpty()) throw new IllegalArgumentException("No compatible BB hero hand");
        return new OwnedQuestion(handoff, List.copyOf(states), List.copyOf(worlds), key, context);
    }

    static Check assess(
            SixMaxSuppliedFlopBettingGame game,
            SixMaxRefinedConditionalStudy.Request request,
            List<SixMaxSuppliedFlopActionBounds.Action> actions,
            CfrSolution policy) {
        var question = question(game, request);
        var handoff = question.handoff();
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        var local = SixMaxSuppliedFlopBettingStudy.localAudit(game, handoff, policy);
        var initialActual = new HashMap<Integer, Double>();
        local.rawWorldMasses()
                .forEach(w -> initialActual.put(w.originalDealIndex(), w.jointRootFlopReach()));
        double actual = 0, total = 0;
        var masses = new ArrayList<Double>();
        for (var world : question.worlds()) {
            double reached = initialActual.getOrDefault(world.originalDealIndex(), 0.0);
            double cf =
                    product(
                            world.originalJointProbability(),
                            world.physicalFlopProbabilityGivenDeal());
            var past = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            for (String action : handoff.continuationActions()) {
                int actor = game.source().currentPlayer(past);
                if (actor != request.hero())
                    cf =
                            product(
                                    cf,
                                    policy.at(
                                                    actor,
                                                    "supplied-connected-single-flop:"
                                                            + game.source().informationSet(past))
                                            .get(action));
                past = game.source().afterAction(past, action);
            }
            var state =
                    new State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            for (String action : request.flopActions()) {
                int actor = game.currentPlayer(state);
                double p = policy.at(actor, game.informationSet(state)).get(action);
                reached = product(reached, p);
                if (actor != request.hero()) cf = product(cf, p);
                state = game.afterAction(state, action);
            }
            actual += reached;
            total += cf;
            masses.add(cf);
        }
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        if (total > 0) {
            for (var action : actions) {
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (int i = 0; i < question.states().size(); i++)
                    if (masses.get(i) > 0)
                        roots.add(
                                new ChanceOutcome<>(
                                        game.afterAction(question.states().get(i), action.action()),
                                        masses.get(i) / total));
                values.put(
                        action.action(),
                        MultiPlayerInformationSetBestResponse.assess(
                                        new ForcedRoot(game, List.copyOf(roots)), policy)
                                .bestResponseUtilitiesBb()
                                .get(request.hero()));
            }
            double best = Collections.max(values.values());
            values.forEach((a, v) -> losses.put(a, Math.max(0, best - v)));
        }
        double br = quality.bestResponseUtilitiesBb().get(request.hero());
        double upper = actions.getFirst().diagnostic().globalHeroUpperValue();
        double margin = upper + SixMaxSuppliedFlopActionBounds.SECURITY_SLACK - br;
        boolean included = margin >= -BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        boolean checked = included && total > 0;
        Double violation = null, regret = null;
        var failures = new ArrayList<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        if (local.conditionalBestResponse() == null
                || local.conditionalBestResponse().nashConvBb()
                        > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("REACHED_FLOP_GAP");
        if (actual / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
        if (total / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        if (total > 0) {
            var row = policy.at(request.hero(), game.informationSet(question.states().getFirst()));
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
                actual / handoff.physicalFlopMass(),
                total / handoff.physicalFlopMass(),
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

    private static double product(double mass, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new IllegalArgumentException("Invalid BB path probability");
        double next = mass * probability;
        if (mass > 0 && probability > 0 && next == 0)
            throw new IllegalArgumentException("BB question reach underflow");
        return next;
    }

    private record ForcedRoot(SixMaxSuppliedFlopBettingGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT =
                new State(-5, "", SixMaxSuppliedFlopBettingGame.Phase.FLOP, "bb-forced-quality");

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
                throw new IllegalArgumentException("BB forced quality root required");
            return roots;
        }
    }
}
