package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopConvergenceAuditTest {
    private static SixMaxPreflopCheckdownGame tinyGame() {
        List<List<WeightedCombo>> ranges =
                List.of(
                        List.of(combo("AS", "AH"), combo("5S", "5H")),
                        List.of(combo("KS", "KH")),
                        List.of(combo("QS", "QH")),
                        List.of(combo("JS", "JH")),
                        List.of(combo("TS", "TH")),
                        List.of(combo("9S", "9H")));
        return new SixMaxPreflopCheckdownGame(
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                ranges,
                CashRakeRule.none(),
                (hands, mask) -> {
                    double[] shares = new double[6];
                    for (int seat = 0; seat < 6; seat++)
                        if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    @Test
    void reportsIndependentExactDeviationAtEveryBudget() {
        var game = tinyGame();
        var rows = SixMaxPreflopConvergenceAudit.run(game, List.of(1, 4));
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).iterations());
        assertEquals(4, rows.get(1).iterations());
        assertTrue(rows.get(0).informationSets() > 0);
        assertEquals(rows.get(0).informationSets(), rows.get(1).informationSets());
        for (var row : rows) {
            assertEquals(6, row.deviationGainsBb().size());
            assertEquals(0, row.profileTotalBb(), 1e-8);
            assertEquals(0, row.maximumTerminalPayoffStandardErrorBb(), 1e-12);
            assertEquals(
                    row.deviationGainsBb().stream().mapToDouble(Double::doubleValue).sum(),
                    row.nashConvBb(),
                    1e-8);
        }
        var independent = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(4);
        var report = MultiPlayerInformationSetBestResponse.assess(game, independent);
        assertEquals(report.nashConvBb(), rows.get(1).nashConvBb(), 1e-8);
        assertEquals(report.deviationGainsBb(), rows.get(1).deviationGainsBb());
    }

    @Test
    void rejectsMisleadingBudgetSequencesBeforeSolving() {
        var game = tinyGame();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopConvergenceAudit.run(game, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopConvergenceAudit.run(game, List.of(2, 2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopConvergenceAudit.run(game, List.of(2, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopConvergenceAudit.run(game, List.of(0)));
        assertEquals(List.of(1, 10, 100), SixMaxPreflopConvergenceMain.parseBudgets("1,10,100"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopConvergenceMain.parseBudgets("1,10,"));
    }
}
