package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Research-only river decisions after a sampled flop/turn checkdown. Both players' checks follow
 * the declared generating model; the assumed model affects only the river observation.
 */
public final class PhysicalPostflopActionBeliefAudit {
    public record PairedGain(double postflopMinusPreflopBb, double standardErrorBb) {
        public double approximateLower95Bb() {
            return postflopMinusPreflopBb - 1.96 * standardErrorBb;
        }

        public double approximateUpper95Bb() {
            return postflopMinusPreflopBb + 1.96 * standardErrorBb;
        }
    }

    public record Result(
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            long seed,
            int sampledBoards,
            long attemptedDeals,
            PhysicalActionBeliefAudit.ResponseModel responseModel,
            String generatingPostflopBeliefHash,
            String assumedPostflopBeliefHash,
            PhysicalRiverHeldOutDecisionAudit.ModeResult staticRange,
            PhysicalRiverHeldOutDecisionAudit.ModeResult preflopOnly,
            PhysicalRiverHeldOutDecisionAudit.ModeResult postflopConditioned,
            PairedGain postflopMinusPreflop) {
        public double checkdownReachRate() {
            return (double) sampledBoards / attemptedDeals;
        }
    }

    private static final class Discovery {
        int count;
        double sum;

        void add(double increment) {
            count++;
            sum += increment;
        }
    }

    private record HeldOut(
            String staticKey, String preflopKey, String postflopKey, double betIncrementBb) {}

    private PhysicalPostflopActionBeliefAudit() {}

    public static Result assess(
            int sampledBoards,
            int minimumDiscoveryBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            PostflopActionBelief generatingPostflopBelief,
            PostflopActionBelief assumedPostflopBelief,
            PhysicalActionBeliefAudit.ResponseModel responseModel) {
        if (sampledBoards < 4 || sampledBoards > 1_000_000 || sampledBoards % 2 != 0)
            throw new IllegalArgumentException("Expected an even 4-1000000 sampled boards");
        if (minimumDiscoveryBoards < 1 || minimumDiscoveryBoards > sampledBoards / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
        java.util.Objects.requireNonNull(generatingPostflopBelief, "generatingPostflopBelief");
        java.util.Objects.requireNonNull(assumedPostflopBelief, "assumedPostflopBelief");
        java.util.Objects.requireNonNull(responseModel, "responseModel");
        var preflopBelief = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var staticGame =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        profile);
        var preflopGame =
                ButtonBigBlindRangeValidationFixture.createActionBucketed(profile, preflopBelief);
        var postflopGame =
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                        profile, preflopBelief, assumedPostflopBelief);
        var deals = staticGame.chanceOutcomes(staticGame.initialState());
        double[] cumulative = new double[deals.size()];
        double total = 0;
        for (int index = 0; index < deals.size(); index++) {
            var deal = deals.get(index);
            total +=
                    deal.probability()
                            * preflopBelief.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN,
                                    deal.state().button())
                            * preflopBelief.likelihood(
                                    PreflopActionBelief.ObservedAction.BIG_BLIND_CALL,
                                    deal.state().bigBlind());
            cumulative[index] = total;
        }
        Map<String, Discovery> staticDiscovery = new HashMap<>();
        Map<String, Discovery> preflopDiscovery = new HashMap<>();
        Map<String, Discovery> postflopDiscovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>(sampledBoards / 2);
        SplittableRandom random = new SplittableRandom(seed);
        long attempted = 0;
        int accepted = 0;
        while (accepted < sampledBoards && attempted < (long) sampledBoards * 100) {
            attempted++;
            var state =
                    sampleReachedRiver(
                            staticGame, deals, cumulative, total, generatingPostflopBelief, random);
            if (state == null) continue;
            double increment =
                    exactBetIncrement(
                            state,
                            deals,
                            preflopBelief,
                            generatingPostflopBelief,
                            responseModel,
                            staticGame.potBb() / 2,
                            8);
            String staticKey = staticGame.informationSet(state);
            String preflopKey = preflopGame.informationSet(state);
            String postflopKey = postflopGame.informationSet(state);
            if (accepted % 2 == 0) {
                staticDiscovery.computeIfAbsent(staticKey, key -> new Discovery()).add(increment);
                preflopDiscovery.computeIfAbsent(preflopKey, key -> new Discovery()).add(increment);
                postflopDiscovery
                        .computeIfAbsent(postflopKey, key -> new Discovery())
                        .add(increment);
            } else heldOut.add(new HeldOut(staticKey, preflopKey, postflopKey, increment));
            accepted++;
        }
        if (accepted < sampledBoards)
            throw new IllegalStateException("Checkdown reach too rare for requested sample budget");
        return new Result(
                profile,
                seed,
                sampledBoards,
                attempted,
                responseModel,
                MultiwayCallSpot.sha256(generatingPostflopBelief.contentDefinition()),
                MultiwayCallSpot.sha256(assumedPostflopBelief.contentDefinition()),
                evaluate(heldOut, staticDiscovery, minimumDiscoveryBoards, HeldOut::staticKey),
                evaluate(heldOut, preflopDiscovery, minimumDiscoveryBoards, HeldOut::preflopKey),
                evaluate(heldOut, postflopDiscovery, minimumDiscoveryBoards, HeldOut::postflopKey),
                paired(heldOut, preflopDiscovery, postflopDiscovery, minimumDiscoveryBoards));
    }

    /** Returns null when a sampled player takes an action other than check. */
    private static ButtonBigBlindPhysicalDeckGame.State sampleReachedRiver(
            ButtonBigBlindPhysicalDeckGame game,
            List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> deals,
            double[] cumulative,
            double total,
            PostflopActionBelief belief,
            SplittableRandom random) {
        double draw = random.nextDouble(total);
        int index = 0;
        while (index < cumulative.length - 1 && draw >= cumulative[index]) index++;
        var state = deals.get(index).state();
        state = game.afterAction(game.afterAction(state, "open3"), "call");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        if (random.nextDouble() >= belief.checkProbability(state.bigBlind(), state.flop())
                || random.nextDouble() >= belief.checkProbability(state.button(), state.flop()))
            return null;
        state = game.afterAction(game.afterAction(state, "k"), "k");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        List<Card> turnBoard = new ArrayList<>(state.flop());
        turnBoard.add(state.turn());
        if (random.nextDouble() >= belief.checkProbability(state.bigBlind(), turnBoard)
                || random.nextDouble() >= belief.checkProbability(state.button(), turnBoard))
            return null;
        state = game.afterAction(game.afterAction(state, "k"), "k");
        return game.sampleChanceOutcome(state, random.nextDouble()).state();
    }

    /** Exact BB river bet-minus-check value after the observed preflop and postflop actions. */
    static double exactBetIncrement(
            ButtonBigBlindPhysicalDeckGame.State state,
            List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> deals,
            PreflopActionBelief preflopBelief,
            PostflopActionBelief postflopBelief,
            PhysicalActionBeliefAudit.ResponseModel responseModel,
            double halfPotBb,
            double riverBetBb) {
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        int ownScore = score(state.bigBlind(), board);
        double total = 0;
        double weightedIncrement = 0;
        for (var deal : deals) {
            var candidate = deal.state();
            if (!candidate.bigBlind().equals(state.bigBlind())) continue;
            var button = candidate.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            double weight =
                    deal.probability()
                            * preflopBelief.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN, button)
                            * postflopBelief.likelihood(button, state, false);
            int sign = Integer.compare(ownScore, score(button, board));
            double increment =
                    responseModel == PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL
                                    || PhysicalRiverPairCallResponse.calls(button, board)
                            ? riverBetBb * sign
                            : halfPotBb * (1 - sign);
            total += weight;
            weightedIncrement += weight * increment;
        }
        if (total == 0) throw new IllegalStateException("No legal opponent on sampled board");
        return weightedIncrement / total;
    }

    private static int score(WeightedCombo combo, List<Card> board) {
        return HandEvaluator.evaluateBestScore(
                combo.first(),
                combo.second(),
                board.get(0),
                board.get(1),
                board.get(2),
                board.get(3),
                board.get(4));
    }

    private static PhysicalRiverHeldOutDecisionAudit.ModeResult evaluate(
            List<HeldOut> heldOut,
            Map<String, Discovery> discovery,
            int minimum,
            java.util.function.Function<HeldOut, String> key) {
        int supported = 0;
        double selected = 0;
        double oracle = 0;
        var regret = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        for (HeldOut board : heldOut) {
            Discovery bucket = discovery.get(key.apply(board));
            if (bucket != null && bucket.count >= minimum) supported++;
            double gain = selectsBet(bucket, minimum) ? board.betIncrementBb() : 0;
            double bestGain = Math.max(0, board.betIncrementBb());
            selected += gain;
            oracle += bestGain;
            regret.add(bestGain - gain);
        }
        return new PhysicalRiverHeldOutDecisionAudit.ModeResult(
                discovery.size(),
                supported,
                heldOut.size(),
                selected / heldOut.size(),
                oracle / heldOut.size(),
                regret.standardError());
    }

    private static PairedGain paired(
            List<HeldOut> heldOut,
            Map<String, Discovery> preflopDiscovery,
            Map<String, Discovery> postflopDiscovery,
            int minimum) {
        var difference = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        for (HeldOut board : heldOut) {
            double preflopGain =
                    selectsBet(preflopDiscovery.get(board.preflopKey()), minimum)
                            ? board.betIncrementBb()
                            : 0;
            double postflopGain =
                    selectsBet(postflopDiscovery.get(board.postflopKey()), minimum)
                            ? board.betIncrementBb()
                            : 0;
            difference.add(postflopGain - preflopGain);
        }
        return new PairedGain(difference.mean(), difference.standardError());
    }

    private static boolean selectsBet(Discovery bucket, int minimum) {
        return bucket != null && bucket.count >= minimum && bucket.sum > 0;
    }
}
