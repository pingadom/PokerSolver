package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostflopActionBeliefTest {
    @Test
    void actionSequenceLikelihoodsFollowCandidateStrengthAndSeat() {
        var belief = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var aces = combo("Ad", "Ah");
        var high = combo("Jh", "Qh");
        var flop = cards("2c", "3d", "4h");
        assertEquals(0.55, belief.checkProbability(aces, flop), 1e-12);
        assertEquals(0.85, belief.checkProbability(high, flop), 1e-12);
        assertEquals(0.55, belief.streetLikelihood(aces, flop, "kk", false), 1e-12);
        assertEquals(0.8, belief.streetLikelihood(aces, flop, "bc", false), 1e-12);
        assertEquals(0.45, belief.streetLikelihood(aces, flop, "kbc", false), 1e-12);
        assertEquals(0.45, belief.streetLikelihood(aces, flop, "bc", true), 1e-12);
        assertEquals(0.55 * 0.8, belief.streetLikelihood(aces, flop, "kbc", true), 1e-12);
        assertThrows(
                IllegalArgumentException.class,
                () -> belief.streetLikelihood(aces, flop, "kbf", false));
    }

    @Test
    void posteriorMatchesDirectJointDealConditioningWithoutHiddenCardLeakage() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var postflop = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var game =
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                        profile, preflop, postflop);
        var deals = game.chanceOutcomes(game.initialState());
        var own = combo("Ac", "Kc");
        var board = cards("2c", "3d", "4h", "5s", "9c");
        var first = state(own, combo("Ad", "Ah"), board);
        var second = state(own, combo("Jh", "Qh"), board);
        assertEquals(game.informationSet(first), game.informationSet(second));
        var opponents = deals.stream().map(deal -> deal.state().button()).distinct().toList();
        var weights =
                postflop.posteriorWeights(
                        preflop.posteriorWeights(
                                opponents, PreflopActionBelief.ObservedAction.BUTTON_OPEN),
                        first,
                        false);
        double posterior = PublicRiverEquityBucket.showdownMargin(board, own, weights);
        double numerator = 0;
        double denominator = 0;
        int ownScore = score(own, board);
        for (var deal : deals) {
            var candidate = deal.state();
            if (!candidate.bigBlind().equals(own)) continue;
            var button = candidate.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            double weight =
                    deal.probability()
                            * preflop.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN, button)
                            * postflop.checkProbability(button, board.subList(0, 3))
                            * postflop.checkProbability(button, board.subList(0, 4));
            denominator += weight;
            numerator += weight * Integer.compare(ownScore, score(button, board));
        }
        assertEquals(numerator / denominator, posterior, 1e-12);
        var changed = new PostflopActionBelief(0.8, 0.4, 0.2, 0.7);
        assertNotEquals(
                game.contentHash(),
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                                profile, preflop, changed)
                        .contentHash());
    }

    @Test
    void rejectsInvalidModelsAndKeepsPhysicalGameUnchanged() {
        assertThrows(
                IllegalArgumentException.class, () -> new PostflopActionBelief(1, 0.5, 0.25, 0.8));
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var postflop = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var game =
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                        profile, preflop, postflop);
        var prior =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        profile);
        assertEquals(
                prior.chanceOutcomes(prior.initialState()),
                game.chanceOutcomes(game.initialState()));
        var river =
                state(combo("Ac", "Kc"), combo("Ad", "Ah"), cards("2c", "3d", "4h", "5s", "9c"));
        assertEquals(
                prior.terminalUtility(prior.afterAction(prior.afterAction(river, "k"), "k")),
                game.terminalUtility(game.afterAction(game.afterAction(river, "k"), "k")));
    }

    @Test
    void priorStreetBetCallHistoryReweightsCandidatesWithoutRevealingTheDeal() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var postflop = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var game =
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                        profile, preflop, postflop);
        var board = cards("2c", "3d", "4h", "5s", "9c");
        var own = combo("Ac", "Kc");
        var checked = state(own, combo("Ad", "Ah"), board);
        var called =
                new ButtonBigBlindPhysicalDeckGame.State(
                        own,
                        combo("Ad", "Ah"),
                        "oc",
                        board.subList(0, 3),
                        "bc",
                        board.get(3),
                        "kk",
                        board.get(4),
                        "");
        var anotherOpponent =
                new ButtonBigBlindPhysicalDeckGame.State(
                        own,
                        combo("Jh", "Qh"),
                        "oc",
                        board.subList(0, 3),
                        "bc",
                        board.get(3),
                        "kk",
                        board.get(4),
                        "");
        assertEquals(game.informationSet(called), game.informationSet(anotherOpponent));
        var prior =
                preflop.posteriorWeights(
                        game.chanceOutcomes(game.initialState()).stream()
                                .map(deal -> deal.state().button())
                                .distinct()
                                .toList(),
                        PreflopActionBelief.ObservedAction.BUTTON_OPEN);
        var checkWeights = postflop.posteriorWeights(prior, checked, false);
        var callWeights = postflop.posteriorWeights(prior, called, false);
        assertTrue(odds(callWeights, "Ad Ah", "Jh Qh") > odds(checkWeights, "Ad Ah", "Jh Qh"));
    }

    @Test
    void connectedSolverCanTraverseEveryStreetWithTheNewObservation() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var game =
                ButtonBigBlindRangeValidationFixture.createPostflopActionBucketed(
                        profile,
                        ButtonBigBlindRangeValidationFixture.actionBelief(profile),
                        ButtonBigBlindRangeValidationFixture.postflopBelief());
        var solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(10);
        assertTrue(solution.strategy().keySet().stream().anyMatch(key -> key.contains("|R:")));
    }

    private static double odds(List<WeightedCombo> range, String first, String second) {
        double firstWeight =
                range.stream()
                        .filter(combo -> combo.key().equals(first))
                        .findFirst()
                        .orElseThrow()
                        .weight();
        double secondWeight =
                range.stream()
                        .filter(combo -> combo.key().equals(second))
                        .findFirst()
                        .orElseThrow()
                        .weight();
        return firstWeight / secondWeight;
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }

    private static ButtonBigBlindPhysicalDeckGame.State state(
            WeightedCombo bb, WeightedCombo button, List<Card> board) {
        return new ButtonBigBlindPhysicalDeckGame.State(
                bb,
                button,
                "oc",
                new ArrayList<>(board.subList(0, 3)),
                "kk",
                board.get(3),
                "kk",
                board.get(4),
                "");
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
}
