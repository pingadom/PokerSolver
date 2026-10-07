package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.State;
import java.util.*;

/** Own-card and public-action conditioned values; folded players remain in the joint posterior. */
public final class SixMaxOneBetDecisionValues {
    static final String EV_SCOPE = "OPTIMAL_HERO_CONTINUATION_FIXED_OPPONENT_POLICY/v1";
    static final String POSTERIOR_SCOPE = "FULL_JOINT_ON_POLICY_PUBLIC_ACTIONS_AND_OWN_CARDS/v1";

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
            String actions,
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

    private SixMaxOneBetDecisionValues() {}

    static List<Decision> assess(
            SixMaxOneBetFlopGame game, List<ChanceOutcome<State>> boardRoots, CfrSolution policy) {
        validateRoots(game, boardRoots, true);
        var result = new ArrayList<Decision>();
        for (String prefix : List.of("", "k", "b", "kb")) {
            var grouped = new TreeMap<String, List<ChanceOutcome<State>>>();
            var representatives = new TreeMap<String, State>();
            double prefixMass = 0;
            for (var root : boardRoots) {
                var state = root.state();
                double weight = root.probability();
                for (int i = 0; i < prefix.length(); i++) {
                    String action = prefix.substring(i, i + 1);
                    double probability =
                            MultiPlayerStrategyEvaluator.probability(game, policy, state, action);
                    double next = weight * probability;
                    if (weight > 0 && probability > 0 && next < Double.MIN_NORMAL)
                        throw new IllegalArgumentException("Decision posterior underflow");
                    weight = next;
                    state = game.afterAction(state, action);
                }
                String key = game.currentPlayer(state) + ":" + game.informationSet(state);
                representatives.putIfAbsent(key, state);
                grouped.computeIfAbsent(key, unused -> new ArrayList<>());
                if (weight > 0) grouped.get(key).add(new ChanceOutcome<>(state, weight));
                prefixMass += weight;
            }
            for (var entry : grouped.entrySet()) {
                var state = representatives.get(entry.getKey());
                int actor = game.currentPlayer(state);
                double mass =
                        entry.getValue().stream().mapToDouble(ChanceOutcome::probability).sum();
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (var root : entry.getValue())
                    roots.add(new ChanceOutcome<>(root.state(), root.probability() / mass));
                result.add(
                        new Decision(
                                new Row(
                                        entry.getKey(),
                                        Seat.values()[actor],
                                        game.sourceGame()
                                                .dealtHands(state.preflop())
                                                .get(actor)
                                                .key(),
                                        prefix,
                                        mass > 0 ? "REACHED" : "ZERO_POLICY_REACH",
                                        prefixMass,
                                        mass,
                                        mass > 0 ? mass / prefixMass : 0,
                                        roots.size(),
                                        mass > 0 ? values(game, roots, policy) : null),
                                roots));
            }
        }
        return List.copyOf(result);
    }

    /** References are evaluated on this fixed question posterior, including off-policy actions. */
    static Values values(
            SixMaxOneBetFlopGame game, List<ChanceOutcome<State>> roots, CfrSolution policy) {
        validateRoots(game, roots, false);
        var state = roots.getFirst().state();
        int actor = game.currentPlayer(state);
        String rootKey = actor + ":" + game.informationSet(state);
        var rows = new TreeMap<String, Map<String, Double>>();
        var hero = new TreeMap<String, List<String>>();
        for (var root : roots) collect(game, root.state(), policy, actor, rows, hero);
        long count = 1;
        for (var actions : hero.values()) count *= actions.size();
        if (count > 256 || !hero.containsKey(rootKey))
            throw new IllegalArgumentException("Decision pure-plan cap or root identity exceeded");
        var local = new SixMaxRankTextureConditionalAudit.ConditionalGame(game, roots);
        double offset =
                game.sourceGame()
                        .publicBettingState(state.preflop())
                        .committedBb(Seat.values()[actor]);
        double profile =
                MultiPlayerStrategyEvaluator.utilities(local, new CfrSolution(1, rows))[actor]
                        + offset;
        var best = new TreeMap<String, Double>();
        game.legalActions(state).forEach(action -> best.put(action, Double.NEGATIVE_INFINITY));
        enumerate(local, actor, new ArrayList<>(hero.entrySet()), 0, rows, rootKey, best, offset);
        var frequencies = policy.at(actor, game.informationSet(state));
        double mixed = 0;
        for (var action : best.entrySet())
            mixed += frequencies.get(action.getKey()) * action.getValue();
        double maximum =
                best.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        return new Values(
                frequencies,
                best,
                finite(profile),
                nonnegative(maximum - profile),
                nonnegative(maximum - mixed),
                nonnegative(mixed - profile));
    }

    private static void collect(
            SixMaxOneBetFlopGame game,
            State state,
            CfrSolution policy,
            int actor,
            Map<String, Map<String, Double>> rows,
            Map<String, List<String>> hero) {
        if (game.isTerminal(state)) return;
        int player = game.currentPlayer(state);
        String key = player + ":" + game.informationSet(state);
        for (String action : game.legalActions(state)) {
            MultiPlayerStrategyEvaluator.probability(game, policy, state, action);
            collect(game, game.afterAction(state, action), policy, actor, rows, hero);
        }
        rows.put(key, policy.at(player, game.informationSet(state)));
        if (player == actor) hero.put(key, game.legalActions(state));
    }

    private static void enumerate(
            SixMaxRankTextureConditionalAudit.ConditionalGame game,
            int actor,
            List<Map.Entry<String, List<String>>> hero,
            int index,
            Map<String, Map<String, Double>> rows,
            String rootKey,
            Map<String, Double> best,
            double offset) {
        if (index == hero.size()) {
            double value =
                    finite(
                            MultiPlayerStrategyEvaluator.utilities(game, new CfrSolution(1, rows))[
                                            actor]
                                    + offset);
            String action =
                    rows.get(rootKey).entrySet().stream()
                            .filter(e -> e.getValue() == 1)
                            .findFirst()
                            .orElseThrow()
                            .getKey();
            best.put(action, Math.max(best.get(action), value));
            return;
        }
        var entry = hero.get(index);
        var original = rows.get(entry.getKey());
        for (String action : entry.getValue()) {
            var pure = new LinkedHashMap<String, Double>();
            entry.getValue().forEach(a -> pure.put(a, a.equals(action) ? 1.0 : 0.0));
            rows.put(entry.getKey(), pure);
            enumerate(game, actor, hero, index + 1, rows, rootKey, best, offset);
        }
        rows.put(entry.getKey(), original);
    }

    static double posteriorDistance(
            List<ChanceOutcome<State>> first, List<ChanceOutcome<State>> second) {
        var mass = new TreeMap<Integer, Double>();
        first.forEach(
                r -> mass.merge(r.state().preflop().dealIndex(), r.probability(), Double::sum));
        second.forEach(
                r -> mass.merge(r.state().preflop().dealIndex(), -r.probability(), Double::sum));
        return mass.values().stream().mapToDouble(Math::abs).sum() / 2;
    }

    private static void validateRoots(
            SixMaxOneBetFlopGame game, List<ChanceOutcome<State>> roots, boolean board) {
        if (roots.isEmpty() || roots.size() > 12)
            throw new IllegalArgumentException("One to twelve joint roots required");
        var first = roots.getFirst().state();
        var worlds = new HashSet<Integer>();
        double total = 0;
        for (var root : roots) {
            var state = root.state();
            if (!Double.isFinite(root.probability())
                    || root.probability() < Double.MIN_NORMAL
                    || game.currentPlayer(state) < 0
                    || state.signal() == null
                    || !state.signal().equals(first.signal())
                    || !state.preflop().publicHistory().equals(first.preflop().publicHistory())
                    || !state.actions().equals(first.actions())
                    || (board && !state.actions().isEmpty())
                    || (!board && !game.informationSet(state).equals(game.informationSet(first)))
                    || !worlds.add(state.preflop().dealIndex()))
                throw new IllegalArgumentException("Invalid joint decision posterior");
            total += root.probability();
        }
        if (Math.abs(total - 1) > 1e-12)
            throw new IllegalArgumentException("Joint posterior must sum to one");
    }

    private static double nonnegative(double value) {
        if (finite(value) < -1e-9)
            throw new IllegalStateException("Decision regret must be nonnegative");
        return Math.max(0, value);
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalStateException("Nonfinite decision value");
        return value;
    }
}
