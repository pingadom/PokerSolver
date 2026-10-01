package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxForcedShoveResponderEvTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<List<WeightedCombo>> ranges() {
        return List.of(
                List.of(combo("AS", "AH", 1)),
                List.of(combo("KS", "KH", 1), combo("QS", "QH", 3)),
                List.of(combo("JS", "JH", 1)),
                List.of(combo("TS", "TH", 1)),
                List.of(combo("9S", "9H", 1)),
                List.of(combo("8S", "8H", 1)));
    }

    private static MultiwayShowdownOracle oracle() {
        return (hands, mask) -> {
            double[] shares = new double[6];
            int live = Integer.bitCount(mask);
            for (int seat = 0; seat < 6; seat++)
                if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / live;
            return MultiwayShowdownEstimate.certain(shares);
        };
    }

    private static MultiwayPreflopCallGame game(CashRakeRule rule) {
        return new MultiwayPreflopCallGame(
                List.of(UTG, HJ, CO, BTN, SB, BB),
                ranges(),
                List.of(100.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                List.of(100.0, 100.0, 100.0, 100.0, 100.0, 100.0),
                0,
                oracle(),
                rule);
    }

    @Test
    void allFiveResponderSeatsMatchSolvedTrainerAcrossPublicHistoriesAndRake() {
        for (CashRakeRule rule : List.of(CashRakeRule.none(), new CashRakeRule(0.05, 1, true))) {
            var source = game(rule);
            var solution =
                    new MultiPlayerCfrSolver<>(source, CfrSolver.Variant.CFR_PLUS).solve(120);
            var trainer = new MultiwayCallTrainer(source, solution);
            for (int player = 1; player < 6; player++) {
                for (long seed = 0; seed < 12; seed++) {
                    var question = trainer.question(seed, player);
                    StringBuilder prior = new StringBuilder();
                    for (var action : question.priorResponses())
                        prior.append(
                                action.action() == MultiwayCallTrainer.Action.CALL ? 'c' : 'f');
                    WeightedCombo hero =
                            source.ranges().get(player).stream()
                                    .filter(combo -> combo.key().equals(question.heroCombo()))
                                    .findFirst()
                                    .orElseThrow();
                    var bridged =
                            SixMaxForcedShoveResponderEv.evaluate(
                                    source, solution, prior.toString(), hero);
                    var feedback = trainer.grade(question, MultiwayCallTrainer.Action.CALL);
                    assertEquals(question.actingSeat(), bridged.heroSeat());
                    assertEquals(question.heroCombo(), bridged.heroCombo());
                    assertEquals(feedback.callEvBb(), bridged.callEvBb(), 1e-8);
                    assertEquals(feedback.foldEvBb(), bridged.foldEvBb(), 1e-8);
                    assertEquals(
                            feedback.callEvBb() - feedback.foldEvBb(),
                            bridged.callAdvantageBb(),
                            1e-8);
                    assertEquals(0, bridged.callPayoffStandardErrorBoundBb());
                }
            }
        }
    }

    @Test
    void bbResultMatchesTheSeparateLastDecisionRangeEvaluator() {
        var source = game(CashRakeRule.none());
        var solution = new MultiPlayerCfrSolver<>(source, CfrSolver.Variant.CFR_PLUS).solve(120);
        var hero = source.ranges().get(BB.ordinal()).getFirst();
        for (String history : List.of("cfff", "ffff", "cccc")) {
            var responder = SixMaxForcedShoveResponderEv.evaluate(source, solution, history, hero);
            var last =
                    SixMaxForcedShovePolicyBridge.evaluateLastBb(source, solution, history, hero);
            assertEquals(last.callEvBb(), responder.callEvBb(), 1e-8);
            assertEquals(last.foldEvBb(), responder.foldEvBb(), 1e-8);
            assertEquals(last.legalJointDeals(), responder.reachableJointDeals());
        }
    }

    @Test
    void rejectsMissingFuturePolicyWrongHistoryAndHeroOutsideRange() {
        var source = game(CashRakeRule.none());
        var hero = source.ranges().get(HJ.ordinal()).getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShoveResponderEv.evaluate(
                                source, new CfrSolution(1, Map.of()), "", hero));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShoveResponderEv.evaluate(
                                source, new CfrSolution(1, Map.of()), "ccccc", hero));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShoveResponderEv.evaluate(
                                source, new CfrSolution(1, Map.of()), "", combo("7C", "7D", 1)));
    }

    @Test
    void sampledShowdownErrorIsCarriedThroughLaterPolicy() {
        var fixedRanges = ranges().stream().map(range -> List.of(range.getFirst())).toList();
        MultiwayShowdownOracle sampled =
                (hands, mask) -> {
                    double[] shares = new double[6];
                    double[] errors = new double[6];
                    int live = Integer.bitCount(mask);
                    for (int seat = 0; seat < 6; seat++) {
                        if ((mask & (1 << seat)) == 0) continue;
                        shares[seat] = 1.0 / live;
                        errors[seat] = 0.01;
                    }
                    return new MultiwayShowdownEstimate(shares, errors, 10_000);
                };
        var source =
                new MultiwayPreflopCallGame(
                        List.of(UTG, HJ, CO, BTN, SB, BB),
                        fixedRanges,
                        List.of(100.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                        100,
                        0,
                        sampled);
        Map<String, Map<String, Double>> strategy =
                Map.of(
                        "2:" + fixedRanges.get(CO.ordinal()).getFirst().key() + ":c",
                        Map.of("c", 0.0, "f", 1.0),
                        "3:" + fixedRanges.get(BTN.ordinal()).getFirst().key() + ":cf",
                        Map.of("c", 0.0, "f", 1.0),
                        "4:" + fixedRanges.get(SB.ordinal()).getFirst().key() + ":cff",
                        Map.of("c", 0.0, "f", 1.0),
                        "5:" + fixedRanges.get(BB.ordinal()).getFirst().key() + ":cfff",
                        Map.of("c", 0.0, "f", 1.0));
        var result =
                SixMaxForcedShoveResponderEv.evaluate(
                        source,
                        new CfrSolution(1, strategy),
                        "",
                        fixedRanges.get(HJ.ordinal()).getFirst());
        assertEquals(0, result.foldEvBb());
        assertEquals(0.75, result.callEvBb(), 1e-9);
        assertEquals(2.015, result.callPayoffStandardErrorBoundBb(), 1e-9);
    }
}
