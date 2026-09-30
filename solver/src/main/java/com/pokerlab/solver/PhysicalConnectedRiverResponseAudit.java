package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.SplittableRandom;

/**
 * Independent physical river runouts reached by a connected CFR strategy. Discovery chooses a BB
 * check/bet action per information set; held-out states score it against the primary BTN response
 * and compare selection based on a separately trained BTN response. Missing strategies are skipped
 * and counted, never silently replaced by uniform or fixed-response actions.
 */
public final class PhysicalConnectedRiverResponseAudit {
    public record Report(
            String gameHash,
            long seed,
            int attemptedDeals,
            int terminalBeforeRiver,
            int missingReachPolicy,
            int reachedRiver,
            int missingBigBlindRiverPolicy,
            int missingPrimaryResponse,
            int missingAlternateResponse,
            int missingCheckContinuation,
            int evaluatedStates,
            int heldOutStates,
            int supportedHeldOutStates,
            int supportedBuckets,
            double meanAbsoluteCallDifference,
            double oppositeMajorityRate,
            double meanBetUtilityDifferenceBb,
            double betUtilityDifferenceStandardErrorBb,
            double primarySelectedGainBb,
            double alternateSelectedGainBb,
            double alternateMinusPrimaryBb,
            double alternateMinusPrimaryStandardErrorBb) {
        public double reachedRiverRate() {
            return (double) reachedRiver / attemptedDeals;
        }

        public double supportedHeldOutRate() {
            return heldOutStates == 0 ? 0 : (double) supportedHeldOutStates / heldOutStates;
        }

        public double approximateAlternateMinusPrimaryLower95Bb() {
            return alternateMinusPrimaryBb - 1.96 * alternateMinusPrimaryStandardErrorBb;
        }

        public double approximateAlternateMinusPrimaryUpper95Bb() {
            return alternateMinusPrimaryBb + 1.96 * alternateMinusPrimaryStandardErrorBb;
        }
    }

    private static final class Discovery {
        int count;
        int heldOutCount;
        double primaryDifferenceSum;
        double alternateDifferenceSum;

        void add(double primaryDifference, double alternateDifference) {
            count++;
            primaryDifferenceSum += primaryDifference;
            alternateDifferenceSum += alternateDifference;
        }
    }

    private record HeldOut(
            String informationSet,
            double checkUtilityBb,
            double primaryBetUtilityBb,
            double policyBetProbability) {}

    private PhysicalConnectedRiverResponseAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution primary,
            CfrSolution alternate,
            int attemptedDeals,
            int minimumDiscoveryStates,
            long seed) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(alternate, "alternate");
        if (attemptedDeals < 2 || attemptedDeals > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 attempted deals");
        if (minimumDiscoveryStates < 1 || minimumDiscoveryStates > attemptedDeals / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
        var fallback =
                RiverCallPolicy.fixed(PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL);
        var primaryResponse = new ConnectedRiverCallPolicy(game, primary, fallback);
        var alternateResponse = new ConnectedRiverCallPolicy(game, alternate, fallback);
        SplittableRandom random = new SplittableRandom(seed);
        Map<String, Discovery> discovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>();
        var betDifference = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        int terminal = 0;
        int missingReach = 0;
        int reached = 0;
        int missingRoot = 0;
        int missingPrimary = 0;
        int missingAlternate = 0;
        int missingCheck = 0;
        int evaluated = 0;
        int opposite = 0;
        double absoluteCallDifference = 0;
        for (int attempt = 0; attempt < attemptedDeals; attempt++) {
            var state = game.sampleChanceOutcome(game.initialState(), random.nextDouble()).state();
            boolean missingEarlier = false;
            while (!game.isTerminal(state)
                    && !PhysicalConnectedStreetDeviationAudit.firstDecision(
                            state, PhysicalConnectedStreetDeviationAudit.Street.RIVER)) {
                int player = game.currentPlayer(state);
                if (player == -1) {
                    state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                    continue;
                }
                var actions = game.legalActions(state);
                Map<String, Double> policy = primary.at(player, game.informationSet(state));
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
            reached++;
            String bbKey = game.informationSet(state);
            OptionalDouble policyBet = requiredProbability(primary.at(0, bbKey), "b", "k");
            if (policyBet.isEmpty()) {
                missingRoot++;
                continue;
            }
            var publicHistory = PublicRiverHistory.from(state);
            var board = publicHistory.board();
            OptionalDouble primaryCall =
                    primaryResponse.learnedCallProbability(state.button(), board, publicHistory);
            OptionalDouble alternateCall =
                    alternateResponse.learnedCallProbability(state.button(), board, publicHistory);
            if (primaryCall.isEmpty()) missingPrimary++;
            if (alternateCall.isEmpty()) missingAlternate++;
            if (primaryCall.isEmpty() || alternateCall.isEmpty()) continue;
            var afterCheck = game.afterAction(state, "k");
            OptionalDouble buttonBet =
                    requiredProbability(primary.at(1, game.informationSet(afterCheck)), "b", "k");
            if (buttonBet.isEmpty()) {
                missingCheck++;
                continue;
            }
            var afterCheckBet = game.afterAction(afterCheck, "b");
            OptionalDouble bigBlindCall =
                    requiredProbability(
                            primary.at(0, game.informationSet(afterCheckBet)), "c", "f");
            if (bigBlindCall.isEmpty()) {
                missingCheck++;
                continue;
            }
            double checkValue =
                    (1 - buttonBet.getAsDouble())
                                    * game.terminalUtility(game.afterAction(afterCheck, "k"))
                            + buttonBet.getAsDouble()
                                    * (bigBlindCall.getAsDouble()
                                                    * game.terminalUtility(
                                                            game.afterAction(afterCheckBet, "c"))
                                            + (1 - bigBlindCall.getAsDouble())
                                                    * game.terminalUtility(
                                                            game.afterAction(afterCheckBet, "f")));
            var afterBet = game.afterAction(state, "b");
            double called = game.terminalUtility(game.afterAction(afterBet, "c"));
            double folded = game.terminalUtility(game.afterAction(afterBet, "f"));
            double primaryBet =
                    primaryCall.getAsDouble() * called + (1 - primaryCall.getAsDouble()) * folded;
            double alternateBet =
                    alternateCall.getAsDouble() * called
                            + (1 - alternateCall.getAsDouble()) * folded;
            evaluated++;
            absoluteCallDifference +=
                    Math.abs(primaryCall.getAsDouble() - alternateCall.getAsDouble());
            if ((primaryCall.getAsDouble() > 0.5 && alternateCall.getAsDouble() < 0.5)
                    || (primaryCall.getAsDouble() < 0.5 && alternateCall.getAsDouble() > 0.5))
                opposite++;
            betDifference.add(alternateBet - primaryBet);
            Discovery bucket = discovery.computeIfAbsent(bbKey, ignored -> new Discovery());
            if (bucket.count <= bucket.heldOutCount)
                bucket.add(primaryBet - checkValue, alternateBet - checkValue);
            else {
                bucket.heldOutCount++;
                heldOut.add(new HeldOut(bbKey, checkValue, primaryBet, policyBet.getAsDouble()));
            }
        }
        var primaryGain = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var alternateGain = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var paired = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        int supportedHeldOut = 0;
        for (HeldOut sample : heldOut) {
            Discovery bucket = discovery.get(sample.informationSet());
            if (bucket.count < minimumDiscoveryStates) continue;
            supportedHeldOut++;
            double policyValue =
                    sample.policyBetProbability() * sample.primaryBetUtilityBb()
                            + (1 - sample.policyBetProbability()) * sample.checkUtilityBb();
            double primarySelected =
                    bucket.primaryDifferenceSum > 0
                            ? sample.primaryBetUtilityBb()
                            : sample.checkUtilityBb();
            double alternateSelected =
                    bucket.alternateDifferenceSum > 0
                            ? sample.primaryBetUtilityBb()
                            : sample.checkUtilityBb();
            primaryGain.add(primarySelected - policyValue);
            alternateGain.add(alternateSelected - policyValue);
            paired.add(alternateSelected - primarySelected);
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
                seed,
                attemptedDeals,
                terminal,
                missingReach,
                reached,
                missingRoot,
                missingPrimary,
                missingAlternate,
                missingCheck,
                evaluated,
                heldOut.size(),
                supportedHeldOut,
                supportedBuckets,
                evaluated == 0 ? Double.NaN : absoluteCallDifference / evaluated,
                evaluated == 0 ? Double.NaN : (double) opposite / evaluated,
                evaluated == 0 ? Double.NaN : betDifference.mean(),
                evaluated < 2 ? Double.NaN : betDifference.standardError(),
                supportedHeldOut == 0 ? Double.NaN : primaryGain.mean(),
                supportedHeldOut == 0 ? Double.NaN : alternateGain.mean(),
                supportedHeldOut == 0 ? Double.NaN : paired.mean(),
                supportedHeldOut < 2 ? Double.NaN : paired.standardError());
    }

    private static OptionalDouble requiredProbability(
            Map<String, Double> policy, String action, String alternative) {
        if (policy == null) return OptionalDouble.empty();
        Double selected = policy.get(action);
        Double other = policy.get(alternative);
        if (policy.size() != 2
                || selected == null
                || other == null
                || !Double.isFinite(selected)
                || !Double.isFinite(other)
                || selected < 0
                || other < 0
                || Math.abs(selected + other - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid connected strategy probabilities");
        return OptionalDouble.of(selected);
    }
}
