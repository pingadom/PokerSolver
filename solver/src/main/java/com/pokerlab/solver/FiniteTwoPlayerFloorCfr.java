package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;

import java.util.*;

/** Independent exhaustive CFR+ reference for the explicitly floor-constrained game. */
public final class FiniteTwoPlayerFloorCfr {
    public static final String ALGORITHM = "BOUNDED_BEHAVIOR_FLOOR_ALTERNATING_CFR_PLUS/v1";
    public static final int MAX_ITERATIONS = 10_000;
    public static final long MAX_VISITS = 20_000_000;

    public record Result(
            String algorithm,
            double minimumActionProbability,
            String snapshotHash,
            int iterations,
            long visitedNodes,
            CfrSolution solution,
            FiniteTwoPlayerBehaviorFloor.ConstrainedQuality constrainedQuality,
            MultiPlayerInformationSetBestResponse.Report originalGameQuality) {}

    private record Policy(double[] intent, double[] played) {}

    private static final class Info {
        final List<String> actions;
        final double[] regrets, average;

        Info(List<String> actions) {
            this.actions = actions;
            regrets = new double[actions.size()];
            average = new double[actions.size()];
        }

        Policy policy(double floor) {
            var intent = new double[actions.size()];
            double total = 0;
            for (int i = 0; i < intent.length; i++) total += intent[i] = Math.max(0, regrets[i]);
            for (int i = 0; i < intent.length; i++)
                intent[i] = total == 0 ? 1.0 / intent.length : intent[i] / total;
            var played = new double[intent.length];
            for (int i = 0; i < played.length; i++)
                played[i] = floor + (1 - played.length * floor) * intent[i];
            return new Policy(intent, played);
        }

        Map<String, Double> average() {
            double total = Arrays.stream(average).sum();
            var row = new LinkedHashMap<String, Double>();
            for (int i = 0; i < average.length; i++)
                row.put(actions.get(i), total == 0 ? 1.0 / average.length : average[i] / total);
            return Map.copyOf(row);
        }
    }

    private final Checked checked;
    private final double floor;
    private final long visitLimit;
    private final Map<String, Info> infos = new LinkedHashMap<>();
    private final Map<String, Policy> frozen = new HashMap<>();
    private long visits;

    private FiniteTwoPlayerFloorCfr(Checked checked, double floor, long visitLimit) {
        this.checked = checked;
        this.floor = floor;
        this.visitLimit = visitLimit;
        for (var info : checked.infos()) infos.put(info.key(), new Info(info.actions()));
    }

    public static <S> Result solve(MultiPlayerCfrGame<S> game, double floor, int iterations)
            throws Exception {
        return solve(game, floor, iterations, MAX_VISITS);
    }

    static <S> Result solve(
            MultiPlayerCfrGame<S> game, double floor, int iterations, long visitLimit)
            throws Exception {
        FiniteTwoPlayerBehaviorFloor.requireFloor(floor);
        if (iterations < 1
                || iterations > MAX_ITERATIONS
                || visitLimit < 1
                || visitLimit > MAX_VISITS)
            throw FiniteTwoPlayerBehaviorFloor.reject(
                    FiniteTwoPlayerBehaviorFloor.Failure.INVALID_INPUT, "Invalid floor CFR budget");
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        // Every pass traverses the complete snapshot. Reject before starting an impossible run.
        if ((long) checked.treeNodes() * 2 * iterations > visitLimit)
            throw FiniteTwoPlayerBehaviorFloor.reject(
                    FiniteTwoPlayerBehaviorFloor.Failure.WORK_LIMIT,
                    "Floor CFR visit budget exceeded");
        return new FiniteTwoPlayerFloorCfr(checked, floor, visitLimit).run(iterations);
    }

    private Result run(int iterations) throws Exception {
        for (int t = 1; t <= iterations; t++)
            for (int target : List.of(checked.firstActor(), checked.secondActor())) {
                frozen.clear();
                infos.forEach((key, info) -> frozen.put(key, info.policy(floor)));
                var reach = new double[checked.snapshot().playerCount()];
                Arrays.fill(reach, 1);
                traverse(checked.snapshot().initialState(), target, t, reach, 1);
                infos.values()
                        .forEach(
                                info -> {
                                    for (int a = 0; a < info.regrets.length; a++)
                                        info.regrets[a] = Math.max(0, info.regrets[a]);
                                });
            }
        var strategy = new LinkedHashMap<String, Map<String, Double>>();
        infos.forEach((key, info) -> strategy.put(key, info.average()));
        var solution = new CfrSolution(iterations, strategy);
        return new Result(
                ALGORITHM,
                floor,
                SixMaxHeadsUpPreflopGame.hash(checked.snapshot()),
                iterations,
                visits,
                solution,
                SequenceFormFloorResponse.assess(checked, solution, floor),
                MultiPlayerInformationSetBestResponse.assess(checked.snapshot(), solution));
    }

    private double traverse(Node node, int target, int weight, double[] reach, double chanceReach) {
        if (++visits > visitLimit)
            throw FiniteTwoPlayerBehaviorFloor.reject(
                    FiniteTwoPlayerBehaviorFloor.Failure.WORK_LIMIT,
                    "Floor CFR visit budget exceeded");
        if (node.actor() == -2) return node.utilities().get(target);
        if (node.actor() == -1) {
            double utility = 0;
            for (int a = 0; a < node.children().size(); a++) {
                double probability = node.probabilities().get(a);
                utility +=
                        probability
                                * traverse(
                                        node.children().get(a),
                                        target,
                                        weight,
                                        reach,
                                        chanceReach * probability);
            }
            return utility;
        }
        int actor = node.actor();
        var policy = frozen.get(node.key());
        var utilities = new double[node.actions().size()];
        double playedUtility = 0, intentUtility = 0;
        for (int a = 0; a < utilities.length; a++) {
            var nextReach = reach.clone();
            nextReach[actor] *= policy.played()[a];
            utilities[a] = traverse(node.children().get(a), target, weight, nextReach, chanceReach);
            playedUtility += policy.played()[a] * utilities[a];
            intentUtility += policy.intent()[a] * utilities[a];
        }
        if (actor == target) {
            double counterfactualReach = chanceReach;
            for (int p = 0; p < reach.length; p++) if (p != target) counterfactualReach *= reach[p];
            var info = infos.get(node.key());
            double scale = 1 - utilities.length * floor;
            for (int a = 0; a < utilities.length; a++) {
                // Regret compares intent choices; actual play includes the mandatory floor.
                info.regrets[a] += counterfactualReach * scale * (utilities[a] - intentUtility);
                info.average[a] += weight * reach[target] * chanceReach * policy.played()[a];
            }
        }
        return playedUtility;
    }
}
