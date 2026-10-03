package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.List;

/** Public chip/status snapshot reconstructed without any private-card or policy information. */
public final class SixMaxPreflopPublicTable {
    public enum PlayerStatus {
        ACTING,
        ACTIVE,
        FOLDED,
        ALL_IN
    }

    public record Player(
            Seat seat,
            PlayerStatus status,
            double committedBb,
            double remainingStackBb,
            SixMaxPreflopBetting.Move lastAction) {}

    public record Snapshot(Seat actingSeat, double potBb, double toCallBb, List<Player> players) {
        public Snapshot {
            players = List.copyOf(players);
        }
    }

    private SixMaxPreflopPublicTable() {}

    public static Snapshot replay(
            SixMaxPreflopBetting.Rules rules,
            List<SixMaxPreflopResearchTrainer.PublicAction> history) {
        if (history == null) throw new IllegalArgumentException("Public history is required");
        var betting = new SixMaxPreflopBetting(rules);
        var state = betting.initialState();
        for (var action : history) {
            if (action == null) throw new IllegalArgumentException("Public action is required");
            var move =
                    state.legalActions().stream()
                            .filter(candidate -> candidate.seat() == action.seat())
                            .filter(
                                    candidate ->
                                            SixMaxPreflopCheckdownGame.actionName(candidate)
                                                    .equals(action.action()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Illegal public preflop history"));
            state = betting.apply(state, move);
        }
        if (state.status() != SixMaxPreflopBetting.Status.DECISION)
            throw new IllegalArgumentException("A table question needs a reached decision");
        List<Player> players = new ArrayList<>();
        for (Seat seat : Seat.values()) {
            double committed = state.committedBb(seat);
            PlayerStatus status =
                    state.isFolded(seat)
                            ? PlayerStatus.FOLDED
                            : state.isAllIn(seat)
                                    ? PlayerStatus.ALL_IN
                                    : seat == state.actingSeat()
                                            ? PlayerStatus.ACTING
                                            : PlayerStatus.ACTIVE;
            SixMaxPreflopBetting.Move last = null;
            for (var move : state.history()) if (move.seat() == seat) last = move;
            players.add(new Player(seat, status, committed, state.remainingStackBb(seat), last));
        }
        return new Snapshot(state.actingSeat(), state.potBb(), state.toCallBb(), players);
    }
}
