package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Independent bottom-up constrained response on an already checked immutable snapshot. */
final class SequenceFormFloorResponse {
    private record Reached(Node node, double opponentChanceReach) {}

    private record Info(int depth, List<String> actions, List<Reached> reached) {}

    private SequenceFormFloorResponse() {}

    static void validate(Checked game, CfrSolution policy, double floor) {
        FiniteTwoPlayerBehaviorFloor.requireFloor(floor);
        var keys = new HashSet<String>();
        for (var info : game.infos()) {
            keys.add(info.key());
            var row = policy.strategy().get(info.key());
            double total = 0;
            if (row == null || !row.keySet().equals(new HashSet<>(info.actions())))
                throw new IllegalArgumentException("Complete floored strategy required");
            for (double p : row.values()) {
                if (!Double.isFinite(p) || p < floor * (1 - 1e-6) || p > 1)
                    throw new IllegalArgumentException(
                            "Strategy violates declared behavioral floor");
                total += p;
            }
            if (Math.abs(total - 1) > 1e-12)
                throw new IllegalArgumentException("Floored strategy must normalize");
        }
        if (!keys.equals(policy.strategy().keySet()))
            throw new IllegalArgumentException("Foreign floored policy rows");
    }

    static FiniteTwoPlayerBehaviorFloor.ConstrainedQuality assess(
            Checked game, CfrSolution policy, double floor) {
        validate(game, policy, floor);
        double[] profile = MultiPlayerStrategyEvaluator.utilities(game.snapshot(), policy);
        var best = new ArrayList<Double>();
        var gains = new ArrayList<Double>();
        var responses = new ArrayList<Map<String, String>>();
        double gap = 0;
        for (int actor = 0; actor < game.snapshot().playerCount(); actor++) {
            var infos = new LinkedHashMap<String, Info>();
            collect(game.snapshot().initialState(), policy, actor, 0, 1, infos);
            var ordered = new ArrayList<>(infos.entrySet());
            ordered.sort(
                    Comparator.comparingInt((Map.Entry<String, Info> e) -> e.getValue().depth())
                            .reversed());
            var response = new LinkedHashMap<String, String>();
            var cache = new IdentityHashMap<Node, Double>();
            for (var entry : ordered) {
                String selected = null;
                double maximum = Double.NEGATIVE_INFINITY;
                for (int a = 0; a < entry.getValue().actions().size(); a++) {
                    double value = 0;
                    for (var reached : entry.getValue().reached())
                        value +=
                                reached.opponentChanceReach()
                                        * continuation(
                                                reached.node().children().get(a),
                                                policy,
                                                actor,
                                                response,
                                                floor,
                                                cache);
                    if (selected == null || value > maximum) {
                        maximum = value;
                        selected = entry.getValue().actions().get(a);
                    }
                }
                response.put(entry.getKey(), selected);
            }
            double
                    value =
                            continuation(
                                    game.snapshot().initialState(),
                                    policy,
                                    actor,
                                    response,
                                    floor,
                                    cache),
                    gain = value - profile[actor];
            if (!Double.isFinite(value) || gain < -1e-8)
                throw FiniteTwoPlayerBehaviorFloor.reject(
                        FiniteTwoPlayerBehaviorFloor.Failure.NUMERICAL_FAILURE,
                        "Constrained response worse than feasible policy");
            gain = Math.max(0, gain);
            gap += gain;
            best.add(value);
            gains.add(gain);
            responses.add(Map.copyOf(response));
        }
        return new FiniteTwoPlayerBehaviorFloor.ConstrainedQuality(
                Arrays.stream(profile).boxed().toList(), best, gains, gap, responses);
    }

    private static void collect(
            Node node, CfrSolution p, int actor, int depth, double reach, Map<String, Info> infos) {
        if (node.actor() == -2) return;
        if (node.actor() == actor) {
            var info =
                    infos.computeIfAbsent(
                            node.key(), k -> new Info(depth, node.actions(), new ArrayList<>()));
            if (info.depth() != depth || !info.actions().equals(node.actions()))
                throw new IllegalArgumentException("Inconsistent response information set");
            info.reached().add(new Reached(node, reach));
        }
        for (int i = 0; i < node.children().size(); i++)
            collect(
                    node.children().get(i),
                    p,
                    actor,
                    depth + 1,
                    reach
                            * (node.actor() == -1
                                    ? node.probabilities().get(i)
                                    : node.actor() == actor
                                            ? 1
                                            : p.strategy()
                                                    .get(node.key())
                                                    .get(node.actions().get(i))),
                    infos);
    }

    private static double continuation(
            Node node,
            CfrSolution p,
            int actor,
            Map<String, String> response,
            double floor,
            Map<Node, Double> cache) {
        var old = cache.get(node);
        if (old != null) return old;
        if (node.actor() == -2) return node.utilities().get(actor);
        double value = 0;
        for (int i = 0; i < node.children().size(); i++) {
            double probability;
            if (node.actor() == -1) probability = node.probabilities().get(i);
            else if (node.actor() != actor)
                probability = p.strategy().get(node.key()).get(node.actions().get(i));
            else {
                String chosen = response.get(node.key());
                if (chosen == null)
                    throw new IllegalArgumentException("Missing downstream constrained response");
                probability =
                        floor
                                + (node.actions().get(i).equals(chosen)
                                        ? 1 - node.actions().size() * floor
                                        : 0);
            }
            value +=
                    probability
                            * continuation(
                                    node.children().get(i), p, actor, response, floor, cache);
        }
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Nonfinite constrained response");
        cache.put(node, value);
        return value;
    }
}
