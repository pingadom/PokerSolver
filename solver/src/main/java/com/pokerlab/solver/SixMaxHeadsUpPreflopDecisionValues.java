package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.util.*;

/**
 * Complete own-card/public-action posteriors and optimal hero continuation against a fixed
 * opponent.
 */
public final class SixMaxHeadsUpPreflopDecisionValues {
    public static final String EV_SCOPE =
            "OPTIMAL_HERO_CONTINUATION_FIXED_OPPONENT_INCREMENTAL_DECISION_EV/v1";
    public static final String POSTERIOR_SCOPE =
            "FULL_JOINT_ON_POLICY_PUBLIC_ACTIONS_AND_OWN_CARDS/v1";

    public record Values(
            Map<String, Double> frequencies,
            Map<String, Double> actionEvBb,
            double profileEvBb,
            double decisionRegretBb,
            double rootMixtureRegretBb,
            double continuationRegretBb) {
        public Values {
            frequencies = Map.copyOf(frequencies);
            actionEvBb = Map.copyOf(actionEvBb);
        }
    }

    public record Row(
            String informationSet,
            Seat actor,
            String ownHand,
            String publicHistory,
            String status,
            double prefixProbability,
            double decisionProbability,
            double ownHandProbabilityGivenPrefix,
            int posteriorJointDeals,
            Values values) {}

    record Decision(Row row, List<ChanceOutcome<State>> roots) {
        Decision {
            roots = List.copyOf(roots);
        }
    }

    private SixMaxHeadsUpPreflopDecisionValues() {}

    static List<Decision> assess(SixMaxHeadsUpPreflopGame game, CfrSolution policy) {
        var groups = new TreeMap<String, List<ChanceOutcome<State>>>();
        var representatives = new TreeMap<String, State>();
        var totals = new TreeMap<String, Double>();
        var seen = new HashSet<String>();
        for (var root : game.chanceOutcomes(game.initialState()))
            collect(
                    game,
                    policy,
                    root.state(),
                    root.probability(),
                    groups,
                    representatives,
                    totals,
                    seen);
        if (!seen.equals(policy.strategy().keySet()))
            throw new IllegalArgumentException("Conditional policy support differs from game");
        var result = new ArrayList<Decision>();
        for (var entry : groups.entrySet()) {
            var state = representatives.get(entry.getKey());
            int actor = game.currentPlayer(state);
            double mass = entry.getValue().stream().mapToDouble(ChanceOutcome::probability).sum(),
                    prefix = totals.get(state.publicHistory());
            var roots =
                    mass == 0
                            ? List.<ChanceOutcome<State>>of()
                            : entry.getValue().stream()
                                    .map(
                                            r ->
                                                    new ChanceOutcome<>(
                                                            r.state(), r.probability() / mass))
                                    .toList();
            result.add(
                    new Decision(
                            new Row(
                                    entry.getKey(),
                                    Seat.values()[actor],
                                    game.dealtHands(state).get(actor).key(),
                                    state.publicHistory(),
                                    mass > 0 ? "REACHED" : "ZERO_POLICY_REACH",
                                    prefix,
                                    mass,
                                    prefix > 0 ? mass / prefix : 0,
                                    roots.size(),
                                    mass > 0 ? values(game, roots, policy) : null),
                            roots));
        }
        return List.copyOf(result);
    }

    private static void collect(
            SixMaxHeadsUpPreflopGame game,
            CfrSolution policy,
            State state,
            double weight,
            Map<String, List<ChanceOutcome<State>>> groups,
            Map<String, State> representatives,
            Map<String, Double> totals,
            Set<String> seen) {
        if (game.isTerminal(state)) return;
        String key = game.currentPlayer(state) + ":" + game.informationSet(state);
        var actions = game.legalActions(state);
        var row = policy.strategy().get(key);
        if (row == null || !row.keySet().equals(new HashSet<>(actions)))
            throw new IllegalArgumentException("Complete legal conditional policy required");
        double sum = 0;
        for (double p : row.values()) {
            if (!Double.isFinite(p) || p < 0 || p > 1)
                throw new IllegalArgumentException("Invalid conditional action probability");
            sum += p;
        }
        if (Math.abs(sum - 1) > 1e-12)
            throw new IllegalArgumentException("Conditional probabilities must sum to one");
        seen.add(key);
        groups.computeIfAbsent(key, k -> new ArrayList<>());
        representatives.putIfAbsent(key, state);
        totals.merge(state.publicHistory(), weight, Double::sum);
        if (weight > 0) groups.get(key).add(new ChanceOutcome<>(state, weight));
        for (String action : actions) {
            double p = row.get(action), next = weight * p;
            if (weight > 0 && p > 0 && next < Double.MIN_NORMAL)
                throw new IllegalArgumentException("Decision posterior underflow");
            collect(
                    game,
                    policy,
                    game.afterAction(state, action),
                    next,
                    groups,
                    representatives,
                    totals,
                    seen);
        }
    }

    /**
     * All alternative actions use this fixed primary question posterior, including off-policy
     * actions.
     */
    static Values values(
            SixMaxHeadsUpPreflopGame game, List<ChanceOutcome<State>> roots, CfrSolution policy) {
        if (roots.isEmpty() || roots.size() > 64)
            throw new IllegalArgumentException("Bounded nonempty question posterior required");
        var first = roots.getFirst().state();
        int actor = game.currentPlayer(first);
        String key = actor + ":" + game.informationSet(first);
        var worlds = new HashSet<Integer>();
        double mass = 0, profile = 0;
        for (var root : roots) {
            if (!worlds.add(root.state().dealIndex())
                    || game.currentPlayer(root.state()) != actor
                    || !game.informationSet(root.state()).equals(game.informationSet(first)))
                throw new IllegalArgumentException("Question posterior mixes decisions");
            mass += root.probability();
            profile +=
                    root.probability()
                            * MultiPlayerStrategyEvaluator.utilitiesFrom(
                                    game, policy, root.state())[actor];
        }
        if (Math.abs(mass - 1) > 1e-12)
            throw new IllegalArgumentException("Question posterior must sum to one");
        double offset = game.publicBettingState(first).committedBb(Seat.values()[actor]);
        var best = new LinkedHashMap<String, Double>();
        for (String action : game.legalActions(first)) {
            var forced =
                    roots.stream()
                            .map(
                                    r ->
                                            new ChanceOutcome<>(
                                                    game.afterAction(r.state(), action),
                                                    r.probability()))
                            .toList();
            double value =
                    MultiPlayerInformationSetBestResponse.assess(
                                            new Conditional(game, forced), policy)
                                    .bestResponseUtilitiesBb()
                                    .get(actor)
                            + offset;
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Nonfinite conditional action EV");
            best.put(action, value);
        }
        var frequencies = policy.strategy().get(key);
        if (frequencies == null || !frequencies.keySet().equals(best.keySet()))
            throw new IllegalArgumentException("Missing decision strategy");
        double mixed = 0;
        for (var entry : frequencies.entrySet())
            mixed += entry.getValue() * best.get(entry.getKey());
        double maximum = Collections.max(best.values());
        profile += offset;
        return new Values(
                frequencies,
                best,
                profile,
                regret(maximum - profile),
                regret(maximum - mixed),
                regret(mixed - profile));
    }

    private static double regret(double value) {
        if (!Double.isFinite(value) || value < -1e-8)
            throw new IllegalArgumentException("Invalid decision regret");
        return Math.max(0, value);
    }

    static double posteriorDistance(
            List<ChanceOutcome<State>> first, List<ChanceOutcome<State>> second) {
        var mass = new TreeMap<Integer, Double>();
        first.forEach(r -> mass.merge(r.state().dealIndex(), r.probability(), Double::sum));
        second.forEach(r -> mass.merge(r.state().dealIndex(), -r.probability(), Double::sum));
        return mass.values().stream().mapToDouble(Math::abs).sum() / 2;
    }

    private record Conditional(SixMaxHeadsUpPreflopGame source, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        public int playerCount() {
            return source.playerCount();
        }

        public State initialState() {
            return source.initialState();
        }

        public boolean isTerminal(State state) {
            return source.isTerminal(state);
        }

        public double[] terminalUtilities(State state) {
            return source.terminalUtilities(state);
        }

        public int currentPlayer(State state) {
            return source.currentPlayer(state);
        }

        public List<String> legalActions(State state) {
            return source.legalActions(state);
        }

        public String informationSet(State state) {
            return source.informationSet(state);
        }

        public State afterAction(State state, String action) {
            return source.afterAction(state, action);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State state) {
            if (!initialState().equals(state))
                throw new IllegalArgumentException("Expected conditional chance root");
            return roots;
        }

        public OptionalDouble inactivePlayerUtility(State state, int player) {
            return source.inactivePlayerUtility(state, player);
        }
    }
}
