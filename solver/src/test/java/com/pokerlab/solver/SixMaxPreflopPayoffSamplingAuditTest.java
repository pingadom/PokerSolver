package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopPayoffSamplingAuditTest {
    private static final SixMaxPreflopBetting.Rules RULES =
            new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
    private static final List<List<WeightedCombo>> RANGES =
            List.of(
                    List.of(combo("AS", "AH")),
                    List.of(combo("KS", "KH")),
                    List.of(combo("QS", "QH")),
                    List.of(combo("JS", "JH")),
                    List.of(combo("TS", "TH")),
                    List.of(combo("9S", "9H")));

    @Test
    void stopsOnEstimatedTargetAndReportsTheExactFinalBudget() {
        var result =
                SixMaxPreflopPayoffSamplingAudit.run(
                        RULES, RANGES, CashRakeRule.none(), 711, 10, 40, 1000);
        assertTrue(result.targetMet());
        assertEquals(result.game(), result.requireTargetMet());
        assertEquals(10, result.finalBoardsPerDeal());
        assertEquals(1, result.rows().size());
        assertEquals(
                result.game().maximumTerminalPayoffStandardErrorBb(),
                result.rows().getFirst().maximumTerminalPayoffStandardErrorBb());
    }

    @Test
    void exhaustsBudgetWithoutClaimingPrecisionAndKeepsSeededPayoffs() {
        var result =
                SixMaxPreflopPayoffSamplingAudit.run(
                        RULES, RANGES, CashRakeRule.none(), 711, 10, 25, 1e-6);
        assertFalse(result.targetMet());
        assertThrows(IllegalStateException.class, result::requireTargetMet);
        assertEquals(
                List.of(10, 20, 25), result.rows().stream().map(r -> r.boardsPerDeal()).toList());
        assertEquals(25, result.finalBoardsPerDeal());
        var fixed =
                new SixMaxPreflopCheckdownGame(
                        RULES,
                        RANGES,
                        CashRakeRule.none(),
                        new SharedBoardMultiwayShowdownOracle(25, 711));
        var adaptiveLeaf = allInLeaf(result.game());
        var fixedLeaf = allInLeaf(fixed);
        assertArrayEquals(
                fixed.terminalUtilities(fixedLeaf),
                result.game().terminalUtilities(adaptiveLeaf),
                1e-12);
        assertEquals(
                fixed.maximumTerminalPayoffStandardErrorBb(),
                result.game().maximumTerminalPayoffStandardErrorBb(),
                1e-12);
    }

    @Test
    void rejectsInvalidTargetAndBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPayoffSamplingAudit.run(
                                RULES, RANGES, CashRakeRule.none(), 1, 0, 10, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPayoffSamplingAudit.run(
                                RULES, RANGES, CashRakeRule.none(), 1, 10, 9, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPayoffSamplingAudit.run(
                                RULES, RANGES, CashRakeRule.none(), 1, 10, 20, Double.NaN));
    }

    private static SixMaxPreflopCheckdownGame.State allInLeaf(SixMaxPreflopCheckdownGame game) {
        var state = game.chanceOutcomes(game.initialState()).getFirst().state();
        state = game.afterAction(state, "raise:100.0");
        state = game.afterAction(state, "call");
        for (int seat = 0; seat < 4; seat++) state = game.afterAction(state, "fold");
        return state;
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
