package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopBettingTest {
    private static SixMaxPreflopBetting reference() {
        return new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
    }

    private static SixMaxPreflopBetting.Move move(
            PreflopAllInSpot.Seat seat, SixMaxPreflopBetting.Kind kind, double amountBb) {
        return new SixMaxPreflopBetting.Move(seat, kind, amountBb);
    }

    @Test
    void replaysTheDocumentedFiveBetAndConservesEveryChipOnTerminalBranches() {
        var game = reference();
        var decision = game.replay(ValidationSpot.create().priorActions());
        assertEquals(DECISION, decision.status());
        assertEquals(UTG, decision.actingSeat());
        assertEquals(List.of(UTG, BTN), decision.liveSeats());
        assertEquals(63.5, decision.potBb());
        assertEquals(18, decision.toCallBb());

        var heroFolds = game.apply(decision, move(UTG, FOLD, 0));
        assertEquals(UNCONTESTED, heroFolds.status());
        assertEquals(BTN, heroFolds.winningSeat());
        assertEquals(23.5, heroFolds.uncontestedProfitBb());

        var shove = game.apply(decision, move(UTG, RAISE_TO, 100));
        assertEquals(BTN, shove.actingSeat());
        assertEquals(141.5, shove.potBb());
        var buttonFolds = game.apply(shove, move(BTN, FOLD, 0));
        assertEquals(UNCONTESTED, buttonFolds.status());
        assertEquals(UTG, buttonFolds.winningSeat());
        assertEquals(41.5, buttonFolds.uncontestedProfitBb());
        var buttonCalls = game.apply(shove, move(BTN, CALL, 100));
        assertEquals(ALL_IN_SHOWDOWN, buttonCalls.status());
        assertEquals(201.5, buttonCalls.potBb());
        assertTrue(buttonCalls.legalActions().isEmpty());
        assertEquals(63.5, decision.potBb(), "Earlier immutable states must not change");
    }

    @Test
    void distinguishesAnUnresolvedFlopContinuationFromAResolvedAllIn() {
        var game = reference();
        var state = game.initialState();
        assertEquals(UTG, state.actingSeat());
        assertEquals(1.5, state.potBb());
        for (var seat : List.of(UTG, HJ, CO, BTN, SB))
            state = game.apply(state, move(seat, CALL, 1));
        assertEquals(BB, state.actingSeat());
        assertEquals(0, state.toCallBb());
        assertFalse(state.legalActions().contains(move(BB, FOLD, 0)));
        state = game.apply(state, move(BB, CHECK, 1));
        assertEquals(POSTFLOP_CONTINUATION_REQUIRED, state.status());
        assertEquals(6, state.potBb());
        assertEquals(6, state.liveSeats().size());
        assertTrue(state.legalActions().isEmpty());
        assertThrows(IllegalStateException.class, state::uncontestedProfitBb);
    }

    @Test
    void awardsFoldedBlindMoneyAndRejectsOutOfTurnOrUnderMinimumRaises() {
        var game = reference();
        var state = game.initialState();
        assertThrows(
                IllegalArgumentException.class,
                () -> game.apply(game.initialState(), move(HJ, FOLD, 0)));
        for (var seat : List.of(UTG, HJ, CO, BTN, SB))
            state = game.apply(state, move(seat, FOLD, 0));
        assertEquals(UNCONTESTED, state.status());
        assertEquals(BB, state.winningSeat());
        assertEquals(1.5, state.potBb());
        assertEquals(0.5, state.uncontestedProfitBb());

        var sized =
                new SixMaxPreflopBetting(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 4.0, 5.0, 100.0)));
        var opened = sized.apply(sized.initialState(), move(UTG, RAISE_TO, 3));
        assertFalse(opened.legalActions().contains(move(HJ, RAISE_TO, 4)));
        assertTrue(opened.legalActions().contains(move(HJ, RAISE_TO, 5)));
        assertThrows(
                IllegalArgumentException.class, () -> sized.apply(opened, move(HJ, RAISE_TO, 4)));
        assertThrows(IllegalArgumentException.class, () -> game.apply(opened, move(HJ, FOLD, 0)));
    }
}
