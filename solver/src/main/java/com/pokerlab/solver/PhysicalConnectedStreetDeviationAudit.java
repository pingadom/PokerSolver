package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Samples reachable first-to-act BB information sets on a chosen postflop street. The same
 * check/bet choice serves every hidden state in a bucket; later policies stay fixed. Alternate
 * samples choose an action and evaluate it independently, without granting a hidden-card oracle.
 */
public final class PhysicalConnectedStreetDeviationAudit {
    public enum Street {
        FLOP,
        TURN,
        RIVER
    }

    public record Decision(
            String informationSet,
            int sampledStates,
            int discoveryStates,
            int heldOutStates,
            double reachedStreetWeight,
            String selectedAction,
            double heldOutCheckUtilityBb,
            double heldOutBetUtilityBb,
            double heldOutBetMinusCheckStandardErrorBb,
            double policyBetProbability,
            double discoveryEstimatedImprovementBb,
            double heldOutPolicyGainBb) {}

    public record Report(
            String gameHash,
            Street street,
            long seed,
            int attemptedDeals,
            int reachedStreet,
            int continuationsPerAction,
            int missingPolicyDecisions,
            List<Decision> decisions) {
        public double reachedStreetRate() {
            return (double) reachedStreet / attemptedDeals;
        }

        public int sampledStatesWithHeldOutSupport(int minimumHeldOutStates) {
            if (minimumHeldOutStates < 1)
                throw new IllegalArgumentException("Support threshold must be positive");
            return decisions.stream()
                    .filter(decision -> decision.heldOutStates() >= minimumHeldOutStates)
                    .mapToInt(Decision::sampledStates)
                    .sum();
        }

        public double sampledStateSupportRate(int minimumHeldOutStates) {
            int supported = sampledStatesWithHeldOutSupport(minimumHeldOutStates);
            return reachedStreet == 0 ? 0 : (double) supported / reachedStreet;
        }
    }

    private static final class Samples {
        private int count;
        private double checkSum;
        private double betSum;
        private double meanDifference;
        private double squaredDifferenceSum;

        private void add(double check, double bet) {
            count++;
            checkSum += check;
            betSum += bet;
            double difference = bet - check;
            double delta = difference - meanDifference;
            meanDifference += delta / count;
            squaredDifferenceSum += delta * (difference - meanDifference);
        }

        private double checkMean() {
            return checkSum / count;
        }

        private double betMean() {
            return betSum / count;
        }

        private double differenceStandardError() {
            return count < 2 ? Double.NaN : Math.sqrt(squaredDifferenceSum / (count - 1) / count);
        }
    }

    private static final class Group {
        private final Samples discovery = new Samples();
        private final Samples heldOut = new Samples();
        private final double policyBet;

        private Group(double policyBet) {
            this.policyBet = policyBet;
        }

        private void add(double check, double bet) {
            Samples next = discovery.count <= heldOut.count ? discovery : heldOut;
            next.add(check, bet);
        }

        private Decision report(String key, int reached) {
            int count = discovery.count + heldOut.count;
            String selected = discovery.betSum > discovery.checkSum ? "b" : "k";
            double discoveryCheck = discovery.checkMean();
            double discoveryBet = discovery.betMean();
            double discoveryPolicy = policyBet * discoveryBet + (1 - policyBet) * discoveryCheck;
            double check = heldOut.count == 0 ? Double.NaN : heldOut.checkMean();
            double bet = heldOut.count == 0 ? Double.NaN : heldOut.betMean();
            double policy = policyBet * bet + (1 - policyBet) * check;
            return new Decision(
                    key,
                    count,
                    discovery.count,
                    heldOut.count,
                    (double) count / reached,
                    selected,
                    check,
                    bet,
                    heldOut.differenceStandardError(),
                    policyBet,
                    Math.max(discoveryCheck, discoveryBet) - discoveryPolicy,
                    (selected.equals("b") ? bet : check) - policy);
        }
    }

    private PhysicalConnectedStreetDeviationAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            Street street,
            int attemptedDeals,
            int continuationsPerAction,
            long seed) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(street, "street");
        if (attemptedDeals < 2 || attemptedDeals > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 attempted deals");
        if (continuationsPerAction < 1 || continuationsPerAction > 1_000)
            throw new IllegalArgumentException("Expected 1-1000 continuations per action");
        SplittableRandom random = new SplittableRandom(seed);
        Map<String, Group> groups = new LinkedHashMap<>();
        int reached = 0;
        int missing = 0;
        for (int attempt = 0; attempt < attemptedDeals; attempt++) {
            var state = game.sampleChanceOutcome(game.initialState(), random.nextDouble()).state();
            while (!game.isTerminal(state) && !firstDecision(state, street)) {
                int player = game.currentPlayer(state);
                state =
                        player == -1
                                ? game.sampleChanceOutcome(state, random.nextDouble()).state()
                                : sampleAction(game, solution, state, random);
            }
            if (game.isTerminal(state)) continue;
            if (game.currentPlayer(state) != 0)
                throw new IllegalStateException("Expected BB to act first on the street");
            reached++;
            String key = game.informationSet(state);
            Map<String, Double> policy = solution.at(0, key);
            if (policy == null) missing++;
            double policyBet = requiredBetProbability(policy);
            Group group = groups.computeIfAbsent(key, ignored -> new Group(policyBet));
            double checkValue = 0;
            double betValue = 0;
            for (int trial = 0; trial < continuationsPerAction; trial++) {
                long continuationSeed = random.nextLong();
                checkValue +=
                        rollout(
                                game,
                                solution,
                                game.afterAction(state, "k"),
                                new SplittableRandom(continuationSeed));
                betValue +=
                        rollout(
                                game,
                                solution,
                                game.afterAction(state, "b"),
                                new SplittableRandom(continuationSeed));
            }
            group.add(checkValue / continuationsPerAction, betValue / continuationsPerAction);
        }
        List<Decision> decisions = new ArrayList<>();
        if (reached > 0) {
            int reachedCount = reached;
            groups.forEach((key, group) -> decisions.add(group.report(key, reachedCount)));
        }
        return new Report(
                game.contentHash(),
                street,
                seed,
                attemptedDeals,
                reached,
                continuationsPerAction,
                missing,
                List.copyOf(decisions));
    }

    static boolean firstDecision(ButtonBigBlindPhysicalDeckGame.State state, Street street) {
        if (state.bigBlind() == null || !state.preflopHistory().equals("oc")) return false;
        return switch (street) {
            case FLOP ->
                    state.flop() != null && state.turn() == null && state.flopHistory().isEmpty();
            case TURN ->
                    state.turn() != null && state.river() == null && state.turnHistory().isEmpty();
            case RIVER -> state.river() != null && state.riverHistory().isEmpty();
        };
    }

    private static ButtonBigBlindPhysicalDeckGame.State sampleAction(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            ButtonBigBlindPhysicalDeckGame.State state,
            SplittableRandom random) {
        int player = game.currentPlayer(state);
        List<String> actions = game.legalActions(state);
        Map<String, Double> policy = solution.at(player, game.informationSet(state));
        return game.afterAction(
                state,
                PhysicalConnectedStrategyAudit.drawAction(actions, policy, random.nextDouble()));
    }

    private static double rollout(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            ButtonBigBlindPhysicalDeckGame.State state,
            SplittableRandom random) {
        while (!game.isTerminal(state)) {
            int player = game.currentPlayer(state);
            if (player == -1) {
                state = game.sampleChanceOutcome(state, random.nextDouble()).state();
            } else {
                state = sampleAction(game, solution, state, random);
            }
        }
        return game.terminalUtility(state);
    }

    private static double requiredBetProbability(Map<String, Double> policy) {
        if (policy == null) return 0.5;
        if (policy.size() != 2
                || policy.get("k") == null
                || policy.get("b") == null
                || !Double.isFinite(policy.get("k"))
                || !Double.isFinite(policy.get("b"))
                || policy.get("k") < 0
                || policy.get("b") < 0
                || Math.abs(policy.get("k") + policy.get("b") - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid first-street policy");
        return policy.get("b");
    }
}
