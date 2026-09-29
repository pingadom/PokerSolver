package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Public cards and actions through the river; contains no private combo. */
public record PublicRiverHistory(
        String preflopHistory,
        List<Card> flop,
        String flopHistory,
        Card turn,
        String turnHistory,
        Card river,
        String riverHistory) {
    public PublicRiverHistory {
        if (!"oc".equals(preflopHistory)
                || flop == null
                || flop.size() != 3
                || !List.of("kk", "bc", "kbc").contains(flopHistory)
                || turn == null
                || !List.of("kk", "bc", "kbc").contains(turnHistory)
                || river == null
                || riverHistory == null)
            throw new IllegalArgumentException("Expected completed public flop and turn streets");
        flop = List.copyOf(flop);
        List<Card> board = new ArrayList<>(flop);
        board.add(turn);
        board.add(river);
        if (board.stream().distinct().count() != 5)
            throw new IllegalArgumentException("Public river cards must be distinct");
    }

    public static PublicRiverHistory from(ButtonBigBlindPhysicalDeckGame.State state) {
        Objects.requireNonNull(state, "state");
        return new PublicRiverHistory(
                state.preflopHistory(),
                state.flop(),
                state.flopHistory(),
                state.turn(),
                state.turnHistory(),
                state.river(),
                state.riverHistory());
    }

    public List<Card> board() {
        List<Card> board = new ArrayList<>(flop);
        board.add(turn);
        board.add(river);
        return List.copyOf(board);
    }
}
