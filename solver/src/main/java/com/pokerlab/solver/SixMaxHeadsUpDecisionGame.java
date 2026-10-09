package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.util.List;

/** Shared decision mechanics; belief provenance belongs to each concrete game, never this view. */
interface SixMaxHeadsUpDecisionGame extends MultiPlayerCfrGame<State> {
    List<WeightedCombo> dealtHands(State state);

    SixMaxPreflopBetting.State publicBettingState(State state);

    List<Seat> activeSeats();
}
