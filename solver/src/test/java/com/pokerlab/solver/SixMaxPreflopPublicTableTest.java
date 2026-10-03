package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopPublicTable.PlayerStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopPublicTableTest {
    private static final SixMaxPreflopBetting.Rules RULES =
            new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0));

    @Test
    void includesBlindPostsAndExactlyOneActorAtTheInitialDecision() {
        var table = SixMaxPreflopPublicTable.replay(RULES, List.of());
        assertEquals(UTG, table.actingSeat());
        assertEquals(1.5, table.potBb());
        assertEquals(1, table.toCallBb());
        assertEquals(6, table.players().size());
        assertEquals(1, table.players().stream().filter(p -> p.status() == ACTING).count());
        assertEquals(0.5, table.players().get(4).committedBb());
        assertEquals(99.5, table.players().get(4).remainingStackBb());
        assertEquals(
                SixMaxPreflopBetting.Kind.POST_SMALL_BLIND,
                table.players().get(4).lastAction().kind());
        assertEquals(
                SixMaxPreflopBetting.Kind.POST_BIG_BLIND,
                table.players().get(5).lastAction().kind());
        assertNull(table.players().getFirst().lastAction());
    }

    @Test
    void replaysReraisesFoldedBlindsAndAllInsBeforeAPlayersSecondDecision() {
        var table =
                SixMaxPreflopPublicTable.replay(
                        RULES,
                        List.of(
                                action(UTG, "raise:3.0"),
                                action(HJ, "fold"),
                                action(CO, "call"),
                                action(BTN, "raise:100.0"),
                                action(SB, "fold"),
                                action(BB, "call")));
        assertEquals(UTG, table.actingSeat());
        assertEquals(206.5, table.potBb());
        assertEquals(97, table.toCallBb());
        assertEquals(ACTING, table.players().get(0).status());
        assertEquals(3, table.players().get(0).committedBb());
        assertEquals(97, table.players().get(0).remainingStackBb());
        assertEquals(FOLDED, table.players().get(1).status());
        assertEquals(ACTIVE, table.players().get(2).status());
        assertEquals(ALL_IN, table.players().get(3).status());
        assertEquals(0, table.players().get(3).remainingStackBb());
        assertEquals(FOLDED, table.players().get(4).status());
        assertEquals(0.5, table.players().get(4).committedBb());
        assertEquals(ALL_IN, table.players().get(5).status());
        assertEquals(SixMaxPreflopBetting.Kind.CALL, table.players().get(5).lastAction().kind());
        assertEquals(
                table.potBb(),
                table.players().stream()
                        .mapToDouble(SixMaxPreflopPublicTable.Player::committedBb)
                        .sum());
    }

    @Test
    void usesIntegerChipAccountingForAllInStatusAndRemainingStack() {
        var rules = new SixMaxPreflopBetting.Rules(100.0000000005, 0.5, List.of(100.0));
        var table = SixMaxPreflopPublicTable.replay(rules, List.of(action(UTG, "raise:100.0")));
        assertEquals(ALL_IN, table.players().getFirst().status());
        assertEquals(0, table.players().getFirst().remainingStackBb());
        assertEquals(99.5, table.players().get(4).remainingStackBb());
    }

    @Test
    void rejectsOutOfTurnIllegalAndFinishedHistories() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopPublicTable.replay(RULES, List.of(action(HJ, "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopPublicTable.replay(RULES, List.of(action(UTG, "raise:4.0"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPublicTable.replay(
                                RULES,
                                List.of(
                                        action(UTG, "fold"),
                                        action(HJ, "fold"),
                                        action(CO, "fold"),
                                        action(BTN, "fold"),
                                        action(SB, "fold"))));
    }

    private static SixMaxPreflopResearchTrainer.PublicAction action(
            PreflopAllInSpot.Seat seat, String action) {
        return new SixMaxPreflopResearchTrainer.PublicAction(seat, action);
    }
}
