package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxFinalDecisionEvTest {
    private static SixMaxPreflopBetting game() {
        return new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
    }

    private static SixMaxPreflopBetting.State bbFacesUtgShove(SixMaxPreflopBetting game) {
        var state =
                game.apply(game.initialState(), new SixMaxPreflopBetting.Move(UTG, RAISE_TO, 100));
        for (var seat : List.of(HJ, CO, BTN, SB))
            state = game.apply(state, new SixMaxPreflopBetting.Move(seat, FOLD, 0));
        assertEquals(BB, state.actingSeat());
        return state;
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<List<WeightedCombo>> singleDeal() {
        return List.of(
                List.of(combo("AS", "AH", 1)),
                List.of(combo("QS", "QH", 1)),
                List.of(combo("JS", "JH", 1)),
                List.of(combo("TS", "TH", 1)),
                List.of(combo("9S", "9H", 1)),
                List.of(combo("KS", "KH", 1)));
    }

    @Test
    void capRakeCanReverseAThinCallAtTheFinalDecision() {
        var game = game();
        var state = bbFacesUtgShove(game);
        var ranges = singleDeal();
        var hero = ranges.get(BB.ordinal()).getFirst();
        MultiwayShowdownOracle oracle =
                (hands, mask) -> {
                    assertEquals((1 << UTG.ordinal()) | (1 << BB.ordinal()), mask);
                    return MultiwayShowdownEstimate.certain(
                            new double[] {0.505, 0, 0, 0, 0, 0.495});
                };

        var noRake =
                SixMaxFinalDecisionEv.evaluate(
                        game, state, hero, ranges, CashRakeRule.none(), oracle);
        var rake =
                SixMaxFinalDecisionEv.evaluate(
                        game, state, hero, ranges, new CashRakeRule(0.05, 1, true), oracle);
        assertEquals(BB, noRake.heroSeat());
        assertEquals(hero.key(), noRake.heroCombo());
        assertEquals(1, noRake.legalJointDeals());
        assertEquals(-1, noRake.foldEvBb());
        assertEquals(-0.7525, noRake.callEvBb(), 1e-10);
        assertEquals(-1.2475, rake.callEvBb(), 1e-10);
        assertTrue(noRake.callAdvantageBb() > 0);
        assertTrue(rake.callAdvantageBb() < 0);
        assertEquals(0, rake.advantagePayoffStandardErrorBoundBb());
    }

    @Test
    void conditioningUsesOpponentWeightsAfterHeroAndFoldedCardBlockers() {
        var game = game();
        var state = bbFacesUtgShove(game);
        var ranges = new ArrayList<>(singleDeal());
        var hero = combo("AS", "KS", 7);
        ranges.set(BB.ordinal(), List.of(hero, combo("7C", "7D", 1)));
        ranges.set(
                UTG.ordinal(),
                List.of(
                        combo("AS", "AC", 100), // blocked by the shown hero hand
                        combo("9S", "9D", 100), // blocked by a folded blind hand
                        combo("AH", "AD", 1),
                        combo("8C", "8D", 3)));
        MultiwayShowdownOracle oracle =
                (hands, mask) -> {
                    double bbShare =
                            hands.get(UTG.ordinal()).key().equals(combo("AH", "AD", 1).key())
                                    ? 0.4
                                    : 0.6;
                    return MultiwayShowdownEstimate.certain(
                            new double[] {1 - bbShare, 0, 0, 0, 0, bbShare});
                };
        var result =
                SixMaxFinalDecisionEv.evaluate(
                        game, state, hero, ranges, CashRakeRule.none(), oracle);
        assertEquals(2, result.legalJointDeals());
        assertEquals(-1, result.foldEvBb());
        assertEquals(200.5 * 0.55 - 100, result.callEvBb(), 1e-10);
    }

    @Test
    void foldedHeroLosesOnlyTheirCommitmentEvenWhenOthersShowDown() {
        var game = game();
        var state =
                game.apply(game.initialState(), new SixMaxPreflopBetting.Move(UTG, RAISE_TO, 100));
        state = game.apply(state, new SixMaxPreflopBetting.Move(HJ, CALL, 100));
        for (var seat : List.of(CO, BTN, SB))
            state = game.apply(state, new SixMaxPreflopBetting.Move(seat, FOLD, 0));
        var result =
                SixMaxFinalDecisionEv.evaluate(
                        game,
                        state,
                        singleDeal().get(BB.ordinal()).getFirst(),
                        singleDeal(),
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            assertEquals(0b100011, mask);
                            return MultiwayShowdownEstimate.certain(
                                    new double[] {0.25, 0.25, 0, 0, 0, 0.5});
                        });
        assertEquals(-1, result.foldEvBb());
        assertEquals(300.5 * 0.5 - 100, result.callEvBb(), 1e-10);
    }

    @Test
    void exactPhysicalShowdownMatchesDirectTerminalSettlement() {
        var game = game();
        var state = bbFacesUtgShove(game);
        var ranges = singleDeal();
        var hands = ranges.stream().map(List::getFirst).toList();
        var oracle = new ExactMultiwayShowdownOracle();
        var result =
                SixMaxFinalDecisionEv.evaluate(
                        game, state, hands.get(BB.ordinal()), ranges, CashRakeRule.none(), oracle);
        var called = game.apply(state, new SixMaxPreflopBetting.Move(BB, CALL, 100));
        var terminal =
                SixMaxPreflopTerminalPayoff.settle(called, hands, CashRakeRule.none(), oracle);
        assertEquals(terminal.utilitiesBb().get(BB.ordinal()), result.callEvBb(), 1e-8);
        assertEquals(0, result.callPayoffStandardErrorBoundBb());
    }

    @Test
    void rejectsUnresolvedBranchesAndMalformedOrUnboundedRanges() {
        var game = game();
        var state = bbFacesUtgShove(game);
        var ranges = singleDeal();
        var hero = ranges.get(BB.ordinal()).getFirst();
        MultiwayShowdownOracle oracle =
                (hands, mask) -> {
                    throw new AssertionError("Invalid input should fail before showdown");
                };
        var early =
                game.apply(game.initialState(), new SixMaxPreflopBetting.Move(UTG, RAISE_TO, 100));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFinalDecisionEv.evaluate(
                                game, early, hero, ranges, CashRakeRule.none(), oracle));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFinalDecisionEv.evaluate(
                                game,
                                state,
                                combo("7C", "7D", 1),
                                ranges,
                                CashRakeRule.none(),
                                oracle));
        var duplicate = new ArrayList<>(ranges);
        duplicate.set(
                UTG.ordinal(), List.of(ranges.getFirst().getFirst(), ranges.getFirst().getFirst()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFinalDecisionEv.evaluate(
                                game, state, hero, duplicate, CashRakeRule.none(), oracle));
        var blocked = new ArrayList<>(ranges);
        blocked.set(UTG.ordinal(), List.of(combo("KS", "AH", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFinalDecisionEv.evaluate(
                                game, state, hero, blocked, CashRakeRule.none(), oracle));
        var tooWide = new ArrayList<>(ranges);
        var candidates = new ArrayList<WeightedCombo>();
        for (var rank : List.of("2", "3", "4", "5", "6", "7", "8", "Q", "J"))
            candidates.add(combo(rank + "C", rank + "D", 1));
        tooWide.set(UTG.ordinal(), candidates);
        tooWide.set(HJ.ordinal(), candidates);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFinalDecisionEv.evaluate(
                                game, state, hero, tooWide, CashRakeRule.none(), oracle));
    }
}
