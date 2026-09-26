package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreflopPayoffCostTest {
    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    @Test
    void reusesExactEquityAcrossSuitRelabellings() {
        WeightedCombo acesClubsDiamonds = combo("Ac", "Ad");
        WeightedCombo kingsClubsDiamonds = combo("Kc", "Kd");
        WeightedCombo acesHeartsSpades = combo("Ah", "As");
        WeightedCombo kingsHeartsSpades = combo("Kh", "Ks");
        ExactPreflopEquityOracle oracle = new ExactPreflopEquityOracle();

        EquityEstimate original = oracle.estimate(acesClubsDiamonds, kingsClubsDiamonds);
        EquityEstimate relabelled = oracle.estimate(acesHeartsSpades, kingsHeartsSpades);

        assertSame(original, relabelled);
        assertEquals(1, oracle.uniqueMatchupsEnumerated());
        assertEquals(ExactPreflopEquityOracle.BOARD_RUNOUTS, original.trials());
        assertNotEquals(
                ExactPreflopEquityOracle.canonicalMatchupKey(acesClubsDiamonds, kingsClubsDiamonds),
                ExactPreflopEquityOracle.canonicalMatchupKey(acesClubsDiamonds, kingsHeartsSpades));
    }

    @Test
    void countsBlockedDealsAndDistinctSuitPatternsBeforeSolving() {
        PreflopAllInSpot original = ValidationSpot.create();
        PreflopAllInSpot candidate =
                new PreflopAllInSpot(
                        "payoff-cost-check",
                        original.effectiveStackBb(),
                        original.smallBlindBb(),
                        original.firstSeat(),
                        original.secondSeat(),
                        original.priorActions(),
                        List.of(combo("Ac", "Ad"), combo("Ah", "As")),
                        List.of(combo("Kc", "Kd"), combo("Kh", "Ks")));

        PreflopPayoffCost.Report report = PreflopPayoffCost.assess(candidate);
        assertEquals(4, report.candidateMatchups());
        assertEquals(0, report.blockedMatchups());
        assertEquals(4, report.legalMatchups());
        assertEquals(2, report.suitEquivalentClasses());
        assertEquals(
                4L * ExactPreflopEquityOracle.BOARD_RUNOUTS, report.boardRunoutsWithoutReuse());
        assertEquals(2L * ExactPreflopEquityOracle.BOARD_RUNOUTS, report.boardRunoutsWithReuse());
    }

    @Test
    void reportsCurrentFixtureWithoutPretendingSuitReuseCreatesNewHands() {
        PreflopPayoffCost.Report report = PreflopPayoffCost.assess(DiverseValidationSpot.create());
        assertEquals(56, report.candidateMatchups());
        assertEquals(9, report.blockedMatchups());
        assertEquals(47, report.legalMatchups());
        assertEquals(47, report.suitEquivalentClasses());
        assertEquals(report.boardRunoutsWithoutReuse(), report.boardRunoutsWithReuse());
    }

    @Test
    void suitExpandedPairsReduceThirtySixMatchupsToThreeExactEnumerations() {
        PreflopPayoffCost.Report report =
                PreflopPayoffCost.assess(PreflopPayoffCost.suitExpandedProbe());
        assertEquals(36, report.candidateMatchups());
        assertEquals(0, report.blockedMatchups());
        assertEquals(36, report.legalMatchups());
        assertEquals(3, report.suitEquivalentClasses());
        assertEquals(
                36L * ExactPreflopEquityOracle.BOARD_RUNOUTS, report.boardRunoutsWithoutReuse());
        assertEquals(3L * ExactPreflopEquityOracle.BOARD_RUNOUTS, report.boardRunoutsWithReuse());

        ExactPreflopEquityOracle oracle = new ExactPreflopEquityOracle();
        PreflopAllInGame game = PreflopPayoffCost.suitExpandedProbe().game(oracle);
        assertEquals(36, game.chanceOutcomes(game.initialState()).size());
        assertEquals(3, oracle.uniqueMatchupsEnumerated());
    }
}
