package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeadsUpBestResponseTest {
    @Test
    void matchesSpecializedRiverBestResponse() {
        RiverBetGame game = RiverValidationSpot.create().game();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        RiverBetBestResponse.Report expected = RiverBetBestResponse.assess(game, solution);
        HeadsUpBestResponse.Report actual = HeadsUpBestResponse.assess(game, solution);
        assertEquals(expected.firstBestResponse(), actual.firstBestResponse(), 1e-9);
        assertEquals(expected.secondBestResponse(), actual.secondBestResponse(), 1e-9);
        assertEquals(expected.profileValue(), actual.profileValue(), 1e-9);
    }

    @Test
    void boundsKuhnPokerAtItsKnownValue() {
        KuhnPoker game = new KuhnPoker();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(3_000);
        HeadsUpBestResponse.Report report = HeadsUpBestResponse.assess(game, solution);
        assertEquals(-1.0 / 18, report.profileValue(), 0.01);
        assertTrue(report.gap() < 0.001);
    }

    @Test
    void matchesSpecializedPreflopBestResponse() {
        PreflopAllInGame game =
                new PreflopAllInGame(
                        List.of(combo("As", "Ah"), combo("7c", "7d")),
                        List.of(combo("Kc", "Kd"), combo("Qh", "Jh")),
                        1,
                        2,
                        30,
                        0,
                        (first, second) ->
                                new EquityEstimate(
                                        first.first().compact().startsWith("7") ? 0.25 : 0.80,
                                        0,
                                        1));
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        PreflopAllInBestResponse.Report expected = PreflopAllInBestResponse.assess(game, solution);
        HeadsUpBestResponse.Report actual = HeadsUpBestResponse.assess(game, solution);
        assertEquals(expected.firstBestResponse(), actual.firstBestResponse(), 1e-9);
        assertEquals(expected.secondBestResponse(), actual.secondBestResponse(), 1e-9);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(
                com.pokerlab.core.card.Card.parse(first),
                com.pokerlab.core.card.Card.parse(second),
                1);
    }
}
