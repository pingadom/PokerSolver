package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreflopFlopTransitionTest {
    private static final List<WeightedCombo> BUTTON = List.of(combo("Ac", "Ad"), combo("Kh", "Qh"));
    private static final List<WeightedCombo> BIG_BLIND =
            List.of(combo("Jc", "Jd"), combo("As", "Ks"));

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static SixMaxPreflopBetting.Move move(
            Seat seat, SixMaxPreflopBetting.Kind kind, double bb) {
        return new SixMaxPreflopBetting.Move(seat, kind, bb);
    }

    private static SixMaxPreflopBetting.State buttonOpenBigBlindCall(SixMaxPreflopBetting game) {
        var state = game.initialState();
        for (Seat seat : List.of(UTG, HJ, CO)) state = game.apply(state, move(seat, FOLD, 0));
        state = game.apply(state, move(BTN, RAISE_TO, 3));
        state = game.apply(state, move(SB, FOLD, 0));
        return game.apply(state, move(BB, CALL, 3));
    }

    private static PreflopFlopTransition.ActionConditioning buttonEvidence() {
        return new PreflopFlopTransition.ActionConditioning(
                BTN,
                "synthetic test policy",
                List.of(move(BTN, RAISE_TO, 3)),
                BUTTON,
                Map.of("Ac Ad", 0.8, "Kh Qh", 0.2));
    }

    private static PreflopFlopTransition.ActionConditioning bigBlindEvidence() {
        return new PreflopFlopTransition.ActionConditioning(
                BB,
                "synthetic test policy",
                List.of(move(BB, CALL, 3)),
                BIG_BLIND,
                Map.of("Jc Jd", 0.5, "As Ks", 0.25));
    }

    @Test
    void carriesTheSixSeatPotAndCorrectPostflopOrderIntoAThreeStreetSpot() {
        var game = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        var preflop = buttonOpenBigBlindCall(game);
        var transition =
                new PreflopFlopTransition(game, preflop, bigBlindEvidence(), buttonEvidence());
        assertEquals(BB, transition.firstToAct());
        assertEquals(BTN, transition.secondToAct());
        assertEquals(6.5, transition.potBb());
        assertEquals(97, transition.remainingStackBb());

        var flop = List.of(Card.parse("Ah"), Card.parse("7c"), Card.parse("2d"));
        var projected = transition.project(flop, 2, 4, 8);
        assertEquals(1.0 / 17_296, projected.probability(), 1e-15);
        assertEquals(6.5, projected.spot().potBb());
        assertEquals(97, projected.spot().remainingStackBb());
        assertEquals(49, projected.spot().turnCandidates().size());
        assertEquals(preflop.history(), projected.preflopHistory());
        assertEquals("synthetic test policy", projected.firstActionSource());
        assertEquals("synthetic test policy", projected.secondActionSource());
        assertEquals(2, projected.spot().firstRange().size());
        assertEquals(2, projected.spot().secondRange().size());
        assertEquals("As Ks", projected.spot().firstRange().get(0).key());
        assertEquals(0.25, projected.spot().firstRange().get(0).weight(), 1e-12);
        assertEquals("Jc Jd", projected.spot().firstRange().get(1).key());
        assertEquals(0.5, projected.spot().firstRange().get(1).weight(), 1e-12);
        var flopGame = projected.spot().game();
        var deals = flopGame.chanceOutcomes(flopGame.initialState());
        assertEquals(4, deals.size());
        assertEquals(1, deals.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(0, flopGame.currentPlayer(deals.get(0).state()));
    }

    @Test
    void conditionsFlopChanceAndPrunesBlockedCombosWithoutChangingTheirWeights() {
        var game = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        var transition =
                new PreflopFlopTransition(
                        game, buttonOpenBigBlindCall(game), bigBlindEvidence(), buttonEvidence());
        var blockedAceFlop = List.of(Card.parse("Ac"), Card.parse("7c"), Card.parse("2d"));
        assertEquals(0.2 / 17_296, transition.flopProbability(blockedAceFlop), 1e-15);
        var projected = transition.project(blockedAceFlop, 2, 4, 8);
        assertEquals(1, projected.spot().secondRange().size());
        assertEquals("Kh Qh", projected.spot().secondRange().get(0).key());
        assertEquals(0.2, projected.spot().secondRange().get(0).weight(), 1e-12);

        var impossible = List.of(Card.parse("Ac"), Card.parse("Kh"), Card.parse("2d"));
        assertEquals(0, transition.flopProbability(impossible));
        assertThrows(IllegalArgumentException.class, () -> transition.project(impossible, 2, 4, 8));
    }

    @Test
    void rejectsUnsupportedBranchesAndIncompleteOrInvalidActionEvidence() {
        var game = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition(
                                game, game.initialState(), bigBlindEvidence(), buttonEvidence()));
        var preflop = buttonOpenBigBlindCall(game);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition(
                                game, preflop, buttonEvidence(), bigBlindEvidence()));
        var otherGame = new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition(
                                otherGame, preflop, bigBlindEvidence(), buttonEvidence()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition.ActionConditioning(
                                BTN,
                                "source",
                                List.of(move(BTN, RAISE_TO, 3)),
                                BUTTON,
                                Map.of("Ac Ad", 0.8)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition.ActionConditioning(
                                BTN,
                                "source",
                                List.of(move(BTN, RAISE_TO, 3)),
                                BUTTON,
                                Map.of("Ac Ad", 1.2, "Kh Qh", 0.2)));
        var impossibleHistory =
                new PreflopFlopTransition.ActionConditioning(
                        BTN,
                        "source",
                        List.of(move(BTN, RAISE_TO, 3)),
                        BUTTON,
                        Map.of("Ac Ad", 0.0, "Kh Qh", 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopFlopTransition(
                                game, preflop, bigBlindEvidence(), impossibleHistory));
        var wrongActions =
                new PreflopFlopTransition.ActionConditioning(
                        BTN,
                        "source",
                        List.of(move(BTN, CALL, 3)),
                        BUTTON,
                        Map.of("Ac Ad", 0.8, "Kh Qh", 0.2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreflopFlopTransition(game, preflop, bigBlindEvidence(), wrongActions));
    }
}
