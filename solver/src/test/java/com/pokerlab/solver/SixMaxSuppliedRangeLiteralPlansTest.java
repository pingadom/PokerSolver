package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Independent 8x9 normal-form oracle, with literal chips and no game/evaluator/BR calls. */
class SixMaxSuppliedRangeLiteralPlansTest {
    @Test
    void buttonDefensePurePlansVerifyTheOwnedEquilibrium() throws Exception {
        verify("supplied-range-button-defense", 5, .5, 1, 3, 9, .3370787285260969);
    }

    @Test
    void cutoffThreebetPurePlansVerifyTheOwnedEquilibrium() throws Exception {
        verify("supplied-range-cutoff-threebet", 2, 1.5, 3, 9, 22, null);
    }

    private static void verify(
            String name,
            int rootSeat,
            double dead,
            double rootCommit,
            double buttonCommit,
            double raise,
            Double expectedValue)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var report =
                mapper.readValue(
                        Files.readString(
                                SixMaxSuppliedRangePreflopGameTest.data(name + "-report.json")),
                        SixMaxSuppliedRangePreflopStudy.Report.class);
        var artifact =
                mapper.readValue(
                        Files.readString(
                                SixMaxSuppliedRangePreflopGameTest.data(name + "-policy.json")),
                        SixMaxSuppliedRangePreflopStudy.Artifact.class);
        var button =
                report.prior().stream()
                        .map(w -> w.dealtCombos().get(3))
                        .distinct()
                        .sorted()
                        .toList();
        var blind =
                report.prior().stream()
                        .map(w -> w.dealtCombos().get(rootSeat))
                        .distinct()
                        .sorted()
                        .toList();
        assertEquals(3, button.size());
        assertEquals(2, blind.size());
        double[] first = new double[8], second = new double[9];
        for (int plan = 0; plan < 8; plan++) {
            double probability = 1;
            for (int hand = 0; hand < 3; hand++)
                probability *=
                        artifact.solution()
                                .strategy()
                                .get(
                                        "3:supplied-conditional-preflop:"
                                                + button.get(hand)
                                                + ":|"
                                                + PreflopAllInSpot.Seat.values()[rootSeat]
                                                + ":raise:"
                                                + raise)
                                .get((plan & (1 << hand)) == 0 ? "fold" : "call");
            first[plan] = probability;
        }
        var actions = List.of("fold", "call", "raise:" + raise);
        for (int plan = 0; plan < 9; plan++) {
            double probability = 1;
            int code = plan;
            for (int hand = 0; hand < 2; hand++) {
                probability *=
                        artifact.solution()
                                .strategy()
                                .get(
                                        rootSeat
                                                + ":supplied-conditional-preflop:"
                                                + blind.get(hand)
                                                + ":")
                                .get(actions.get(code % 3));
                code /= 3;
            }
            second[plan] = probability;
        }
        assertEquals(1, Arrays.stream(first).sum(), 1e-12);
        assertEquals(1, Arrays.stream(second).sum(), 1e-12);
        double[][] matrix = new double[8][9];
        for (int a = 0; a < 8; a++)
            for (int b = 0; b < 9; b++)
                for (var world : report.prior()) {
                    int hero = button.indexOf(world.dealtCombos().get(3));
                    int opponent = blind.indexOf(world.dealtCombos().get(rootSeat));
                    int move = b / (opponent == 0 ? 1 : 3) % 3;
                    var count = report.payoffs().get(world.dealIndex()).counts();
                    double share =
                            ((count.firstSeat() == 3 ? count.firstWins() : count.secondWins())
                                            + .5 * count.ties())
                                    / count.boards();
                    double payout =
                            move == 0
                                    ? rootCommit + dead
                                    : move == 1
                                            ? (2 * buttonCommit + dead) * share - buttonCommit
                                            : (a & (1 << hero)) == 0
                                                    ? -buttonCommit
                                                    : (2 * raise + dead) * share - raise;
                    matrix[a][b] += world.conditionalProbability() * payout;
                }
        double value = 0;
        for (int a = 0; a < 8; a++)
            for (int b = 0; b < 9; b++) value += first[a] * second[b] * matrix[a][b];
        if (expectedValue != null) assertEquals(expectedValue, value, 1e-12);
        assertEquals(
                report.solve().lowerValue(),
                report.solve().firstActor() == 3 ? value : dead - value,
                1e-12);
        for (int a = 0; a < 8; a++) {
            double response = 0;
            for (int b = 0; b < 9; b++) response += second[b] * matrix[a][b];
            assertTrue(response <= value + 1e-10);
        }
        for (int b = 0; b < 9; b++) {
            double response = 0;
            for (int a = 0; a < 8; a++) response += first[a] * matrix[a][b];
            assertTrue(response >= value - 1e-10);
        }
    }
}
