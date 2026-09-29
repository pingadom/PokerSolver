package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Sample-split BB river bet/check comparison when both preflop actions follow a declared, fixed
 * likelihood model. This checks an observation under its own model, not equilibrium play.
 */
public final class PhysicalActionBeliefAudit {
    public record PairedGain(double conditionedMinusStaticBb, double standardErrorBb) {
        public double approximateLower95Bb() {
            return conditionedMinusStaticBb - 1.96 * standardErrorBb;
        }

        public double approximateUpper95Bb() {
            return conditionedMinusStaticBb + 1.96 * standardErrorBb;
        }
    }

    public record Result(
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            long seed,
            int sampledBoards,
            PhysicalRiverHeldOutDecisionAudit.ModeResult staticRange,
            PhysicalRiverHeldOutDecisionAudit.ModeResult actionConditioned,
            PairedGain conditionedMinusStatic) {}

    private static final class Discovery {
        int count;
        double sum;

        void add(double value) {
            count++;
            sum += value;
        }
    }

    private record HeldOut(String staticKey, String conditionedKey, double betIncrementBb) {}

    private PhysicalActionBeliefAudit() {}

    public static Result assess(
            int sampledBoards,
            int minimumDiscoveryBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile) {
        if (sampledBoards < 4 || sampledBoards > 1_000_000 || sampledBoards % 2 != 0)
            throw new IllegalArgumentException("Expected an even 4-1000000 sampled boards");
        if (minimumDiscoveryBoards < 1 || minimumDiscoveryBoards > sampledBoards / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
        var staticGame =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        profile);
        var conditionedGame = ButtonBigBlindRangeValidationFixture.createActionBucketed(profile);
        var belief = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var deals = staticGame.chanceOutcomes(staticGame.initialState());
        double[] cumulative = new double[deals.size()];
        double total = 0;
        for (int index = 0; index < deals.size(); index++) {
            var deal = deals.get(index);
            total +=
                    deal.probability()
                            * belief.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN,
                                    deal.state().button())
                            * belief.likelihood(
                                    PreflopActionBelief.ObservedAction.BIG_BLIND_CALL,
                                    deal.state().bigBlind());
            cumulative[index] = total;
        }
        Map<String, Discovery> staticDiscovery = new HashMap<>();
        Map<String, Discovery> conditionedDiscovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>(sampledBoards / 2);
        SplittableRandom random = new SplittableRandom(seed);
        for (int attempt = 0; attempt < sampledBoards; attempt++) {
            double draw = random.nextDouble(total);
            int index = 0;
            while (index < cumulative.length - 1 && draw >= cumulative[index]) index++;
            var state = riverState(staticGame, deals.get(index).state(), random);
            double increment = 8 * exactPosteriorMargin(state, deals, belief);
            String staticKey = staticGame.informationSet(state);
            String conditionedKey = conditionedGame.informationSet(state);
            if (attempt % 2 == 0) {
                staticDiscovery.computeIfAbsent(staticKey, key -> new Discovery()).add(increment);
                conditionedDiscovery
                        .computeIfAbsent(conditionedKey, key -> new Discovery())
                        .add(increment);
            } else heldOut.add(new HeldOut(staticKey, conditionedKey, increment));
        }
        return new Result(
                profile,
                seed,
                sampledBoards,
                evaluate(heldOut, staticDiscovery, minimumDiscoveryBoards, false),
                evaluate(heldOut, conditionedDiscovery, minimumDiscoveryBoards, true),
                paired(heldOut, staticDiscovery, conditionedDiscovery, minimumDiscoveryBoards));
    }

    private static ButtonBigBlindPhysicalDeckGame.State riverState(
            ButtonBigBlindPhysicalDeckGame game,
            ButtonBigBlindPhysicalDeckGame.State state,
            SplittableRandom random) {
        state = game.afterAction(game.afterAction(state, "open3"), "call");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        state = game.sampleChanceOutcome(state, random.nextDouble()).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        return game.sampleChanceOutcome(state, random.nextDouble()).state();
    }

    /** Direct joint-deal conditioning, independent of the bucket's posterior implementation. */
    static double exactPosteriorMargin(
            ButtonBigBlindPhysicalDeckGame.State state,
            List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> deals,
            PreflopActionBelief belief) {
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        int ownScore = score(state.bigBlind(), board);
        double total = 0;
        double signed = 0;
        for (var deal : deals) {
            var candidate = deal.state();
            if (!candidate.bigBlind().equals(state.bigBlind())) continue;
            var button = candidate.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            double weight =
                    deal.probability()
                            * belief.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN, button);
            total += weight;
            signed += weight * Integer.compare(ownScore, score(button, board));
        }
        if (total == 0) throw new IllegalStateException("No legal opponent on sampled board");
        return signed / total;
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
            boolean conditioned) {
        int supported = 0;
        double selected = 0;
        double oracle = 0;
        var regret = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        for (HeldOut board : heldOut) {
            Discovery bucket =
                    discovery.get(conditioned ? board.conditionedKey() : board.staticKey());
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
            Map<String, Discovery> staticDiscovery,
            Map<String, Discovery> conditionedDiscovery,
            int minimum) {
        var differences = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        for (HeldOut board : heldOut) {
            boolean conditioned =
                    selectsBet(conditionedDiscovery.get(board.conditionedKey()), minimum);
            boolean staticRange = selectsBet(staticDiscovery.get(board.staticKey()), minimum);
            differences.add(
                    (conditioned ? 1 : 0) * board.betIncrementBb()
                            - (staticRange ? 1 : 0) * board.betIncrementBb());
        }
        return new PairedGain(differences.mean(), differences.standardError());
    }

    private static boolean selectsBet(Discovery bucket, int minimum) {
        return bucket != null && bucket.count >= minimum && bucket.sum > 0;
    }
}
