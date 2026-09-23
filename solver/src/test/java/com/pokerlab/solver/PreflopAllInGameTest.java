package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreflopAllInGameTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    @Test
    void excludesBlockedDealsAndNormalizesRangeWeights() {
        PreflopAllInGame game =
                new PreflopAllInGame(
                        List.of(combo("AS", "AH", 2), combo("KS", "KH", 1)),
                        List.of(combo("AS", "AD", 1), combo("QC", "QD", 3)),
                        10,
                        22,
                        100,
                        0.5,
                        (first, second) -> new EquityEstimate(0.75, 0.004, 10000));

        List<ChanceOutcome<PreflopAllInGame.State>> deals =
                game.chanceOutcomes(game.initialState());
        assertEquals(3, deals.size());
        assertEquals(1, deals.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(
                List.of(0.6, 0.1, 0.3), deals.stream().map(ChanceOutcome::probability).toList());
        assertTrue(
                deals.stream().noneMatch(d -> d.state().first().conflictsWith(d.state().second())));
        assertEquals(0.004, game.maximumEquityStandardError(), 1e-12);
    }

    @Test
    void accountsForCommittedChipsDeadMoneyAndAllInEquity() {
        PreflopAllInGame game =
                new PreflopAllInGame(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KC", "KD", 1)),
                        10,
                        22,
                        100,
                        0.5,
                        (first, second) -> new EquityEstimate(0.75, 0.004, 10000));
        PreflopAllInGame.State dealt = game.chanceOutcomes(game.initialState()).get(0).state();

        assertEquals(-10, game.terminalUtility(game.afterAction(dealt, "f")));
        PreflopAllInGame.State shoved = game.afterAction(dealt, "s");
        assertEquals(22.5, game.terminalUtility(game.afterAction(shoved, "f")));
        assertEquals(50.375, game.terminalUtility(game.afterAction(shoved, "c")));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(dealt, "c"));

        CfrSolution solved = new CfrSolver<>(game).solve(2000);
        assertTrue(solved.at(0, dealt.first().key() + ":").get("s") > 0.95);
        assertTrue(solved.at(1, dealt.second().key() + ":s").get("f") > 0.95);
    }

    @Test
    void seededEquityEstimatesAreRepeatable() {
        WeightedCombo aces = combo("AS", "AH", 1);
        WeightedCombo kings = combo("KC", "KD", 1);
        SeededMonteCarloEquityOracle oracle = new SeededMonteCarloEquityOracle(1000, 42);

        EquityEstimate first = oracle.estimate(aces, kings);
        assertEquals(first, oracle.estimate(aces, kings));
        assertTrue(first.standardError() > 0);
        assertThrows(
                IllegalArgumentException.class, () -> oracle.estimate(aces, combo("AS", "KD", 1)));
    }

    @Test
    void rejectsImpossibleRangesAndCommitments() {
        WeightedCombo aces = combo("AS", "AH", 1);
        WeightedCombo kings = combo("KC", "KD", 1);
        PreflopEquityOracle oracle = (first, second) -> new EquityEstimate(0.5, 0.01, 1000);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreflopAllInGame(List.of(aces), List.of(aces), 10, 22, 100, 0, oracle));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopAllInGame(
                                List.of(aces, aces), List.of(kings), 10, 22, 100, 0, oracle));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreflopAllInGame(List.of(aces), List.of(kings), 101, 22, 100, 0, oracle));
    }
}
