package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.SplittableRandom;

/**
 * Sample-split BB one-decision deviation under a connected policy's own physical reach. Missing
 * strategy keys invalidate that reach or continuation; no uniform fallback is used.
 */
public final class PhysicalStrictConnectedStreetDeviationAudit {
    public record Report(
            String gameHash,
            PhysicalConnectedStreetDeviationAudit.Street street,
            long seed,
            int attemptedDeals,
            int terminalBeforeStreet,
            int missingReachPolicy,
            int reachedStreet,
            int missingRootPolicy,
            int missingCheckContinuation,
            int missingBetContinuation,
            int evaluatedStates,
            int heldOutStates,
            int supportedHeldOutStates,
            int supportedBuckets,
            int continuationsPerAction,
            double selectedGainBb,
            double selectedGainStandardErrorBb) {
        public double evaluatedReachRate() {
            return reachedStreet == 0 ? 0 : (double) evaluatedStates / reachedStreet;
        }

        public double supportedHeldOutRate() {
            return heldOutStates == 0 ? 0 : (double) supportedHeldOutStates / heldOutStates;
        }

        public double approximateSelectedGainLower95Bb() {
            return selectedGainBb - 1.96 * selectedGainStandardErrorBb;
        }

        public double approximateSelectedGainUpper95Bb() {
            return selectedGainBb + 1.96 * selectedGainStandardErrorBb;
        }
    }

    private static final class Discovery {
        int count;
        int heldOutCount;
        double betMinusCheckSum;

        void add(double check, double bet) {
            count++;
            betMinusCheckSum += bet - check;
        }
    }

    private record HeldOut(String informationSet, double checkBb, double betBb, double policyBet) {}

    private PhysicalStrictConnectedStreetDeviationAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            PhysicalConnectedStreetDeviationAudit.Street street,
            int attemptedDeals,
            int continuationsPerAction,
            int minimumDiscoveryStates,
            long seed) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(street, "street");
        if (attemptedDeals < 2 || attemptedDeals > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 attempted deals");
        if (continuationsPerAction < 1 || continuationsPerAction > 1_000)
            throw new IllegalArgumentException("Expected 1-1000 continuations per action");
        if (minimumDiscoveryStates < 1 || minimumDiscoveryStates > attemptedDeals / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");

        var random = new SplittableRandom(seed);
        Map<String, Discovery> discovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>();
        int terminal = 0;
        int missingReach = 0;
        int reached = 0;
        int missingRoot = 0;
        int missingCheck = 0;
        int missingBet = 0;
        int evaluated = 0;
        for (int attempt = 0; attempt < attemptedDeals; attempt++) {
            var state = game.sampleChanceOutcome(game.initialState(), random.nextDouble()).state();
            boolean missingEarlier = false;
            while (!game.isTerminal(state)
                    && !PhysicalConnectedStreetDeviationAudit.firstDecision(state, street)) {
                int player = game.currentPlayer(state);
                if (player == -1) {
                    state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                    continue;
                }
                var actions = game.legalActions(state);
                var policy = solution.at(player, game.informationSet(state));
                if (policy == null) {
                    missingEarlier = true;
                    break;
                }
                state =
                        game.afterAction(
                                state,
                                PhysicalConnectedStrategyAudit.drawAction(
                                        actions, policy, random.nextDouble()));
            }
            if (missingEarlier) {
                missingReach++;
                continue;
            }
            if (game.isTerminal(state)) {
                terminal++;
                continue;
            }
            if (game.currentPlayer(state) != 0)
                throw new IllegalStateException("Expected BB first on selected street");
            reached++;
            String key = game.informationSet(state);
            OptionalDouble policyBet = betProbability(solution.at(0, key));
            if (policyBet.isEmpty()) {
                missingRoot++;
                continue;
            }
            double checkSum = 0;
            double betSum = 0;
            boolean checkComplete = true;
            boolean betComplete = true;
            int trials =
                    street == PhysicalConnectedStreetDeviationAudit.Street.RIVER
                            ? 1
                            : continuationsPerAction;
            for (int trial = 0; trial < trials; trial++) {
                long continuationSeed = random.nextLong();
                var check =
                        rollout(
                                game,
                                solution,
                                game.afterAction(state, "k"),
                                new SplittableRandom(continuationSeed));
                var bet =
                        rollout(
                                game,
                                solution,
                                game.afterAction(state, "b"),
                                new SplittableRandom(continuationSeed));
                if (check.isEmpty()) checkComplete = false;
                else checkSum += check.getAsDouble();
                if (bet.isEmpty()) betComplete = false;
                else betSum += bet.getAsDouble();
            }
            if (!checkComplete) missingCheck++;
            if (!betComplete) missingBet++;
            if (!checkComplete || !betComplete) continue;
            evaluated++;
            double check = checkSum / trials;
            double bet = betSum / trials;
            Discovery bucket = discovery.computeIfAbsent(key, ignored -> new Discovery());
            if (bucket.count <= bucket.heldOutCount) bucket.add(check, bet);
            else {
                bucket.heldOutCount++;
                heldOut.add(new HeldOut(key, check, bet, policyBet.getAsDouble()));
            }
        }
        var gain = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        int supported = 0;
        for (HeldOut sample : heldOut) {
            Discovery bucket = discovery.get(sample.informationSet());
            if (bucket.count < minimumDiscoveryStates) continue;
            supported++;
            double policy =
                    sample.policyBet() * sample.betBb()
                            + (1 - sample.policyBet()) * sample.checkBb();
            double selected = bucket.betMinusCheckSum > 0 ? sample.betBb() : sample.checkBb();
            gain.add(selected - policy);
        }
        int supportedBuckets =
                (int)
                        discovery.values().stream()
                                .filter(
                                        bucket ->
                                                bucket.count >= minimumDiscoveryStates
                                                        && bucket.heldOutCount > 0)
                                .count();
        return new Report(
                game.contentHash(),
                street,
                seed,
                attemptedDeals,
                terminal,
                missingReach,
                reached,
                missingRoot,
                missingCheck,
                missingBet,
                evaluated,
                heldOut.size(),
                supported,
                supportedBuckets,
                street == PhysicalConnectedStreetDeviationAudit.Street.RIVER
                        ? 1
                        : continuationsPerAction,
                supported == 0 ? Double.NaN : gain.mean(),
                supported < 2 ? Double.NaN : gain.standardError());
    }

    private static OptionalDouble rollout(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            ButtonBigBlindPhysicalDeckGame.State state,
            SplittableRandom random) {
        while (!game.isTerminal(state)) {
            if (state.river() != null) return exactRiver(game, solution, state);
            int player = game.currentPlayer(state);
            if (player == -1) {
                state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                continue;
            }
            var actions = game.legalActions(state);
            var policy = solution.at(player, game.informationSet(state));
            if (policy == null) return OptionalDouble.empty();
            state =
                    game.afterAction(
                            state,
                            PhysicalConnectedStrategyAudit.drawAction(
                                    actions, policy, random.nextDouble()));
        }
        return OptionalDouble.of(game.terminalUtility(state));
    }

    static OptionalDouble exactRiver(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            ButtonBigBlindPhysicalDeckGame.State state) {
        if (game.isTerminal(state)) return OptionalDouble.of(game.terminalUtility(state));
        int player = game.currentPlayer(state);
        if (player == -1) throw new IllegalStateException("Unexpected river chance node");
        var actions = game.legalActions(state);
        var policy = solution.at(player, game.informationSet(state));
        if (policy == null) return OptionalDouble.empty();
        if (policy.size() != actions.size())
            throw new IllegalArgumentException("River strategy action set differs from game");
        double sum = 0;
        for (String action : actions) {
            Double probability = policy.get(action);
            if (probability == null || !Double.isFinite(probability) || probability < 0)
                throw new IllegalArgumentException("Invalid river action probability");
            sum += probability;
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("River probabilities must sum to one");
        double utility = 0;
        for (String action : actions) {
            double probability = policy.get(action);
            if (probability == 0) continue;
            var child = exactRiver(game, solution, game.afterAction(state, action));
            if (child.isEmpty()) return OptionalDouble.empty();
            utility += probability * child.getAsDouble();
        }
        return OptionalDouble.of(utility);
    }

    private static OptionalDouble betProbability(Map<String, Double> policy) {
        if (policy == null) return OptionalDouble.empty();
        if (policy.size() != 2
                || policy.get("k") == null
                || policy.get("b") == null
                || !Double.isFinite(policy.get("k"))
                || !Double.isFinite(policy.get("b"))
                || policy.get("k") < 0
                || policy.get("b") < 0
                || Math.abs(policy.get("k") + policy.get("b") - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid BB street strategy probabilities");
        return OptionalDouble.of(policy.get("b"));
    }
}
