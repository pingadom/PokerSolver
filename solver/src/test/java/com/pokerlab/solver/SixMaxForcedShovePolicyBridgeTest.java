package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxForcedShovePolicyBridgeTest {
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
            if ((mask & (1 << BB.ordinal())) == 0) shares[UTG.ordinal()] = 1;
            else {
                boolean king = hands.get(HJ.ordinal()).key().equals(combo("KS", "KH", 1).key());
                shares[BB.ordinal()] = king ? 0.7 : 0.3;
                shares[UTG.ordinal()] = 1 - shares[BB.ordinal()];
            }
            return MultiwayShowdownEstimate.certain(shares);
        };
    }

    private static MultiwayPreflopCallGame game(MultiwayShowdownOracle oracle) {
        return game(oracle, CashRakeRule.none());
    }

    private static MultiwayPreflopCallGame game(
            MultiwayShowdownOracle oracle, CashRakeRule rakeRule) {
        return new MultiwayPreflopCallGame(
                List.of(UTG, HJ, CO, BTN, SB, BB),
                ranges(),
                List.of(100.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                List.of(100.0, 100.0, 100.0, 100.0, 100.0, 100.0),
                0,
                oracle,
                rakeRule);
    }

    private static CfrSolution solution() {
        var ranges = ranges();
        Map<String, Map<String, Double>> strategy = new HashMap<>();
        strategy.put(
                "1:" + ranges.get(HJ.ordinal()).get(0).key() + ":", Map.of("c", 0.8, "f", 0.2));
        strategy.put(
                "1:" + ranges.get(HJ.ordinal()).get(1).key() + ":", Map.of("c", 0.2, "f", 0.8));
        for (String first : List.of("c", "f")) {
            strategy.put(
                    "2:" + ranges.get(CO.ordinal()).getFirst().key() + ":" + first,
                    Map.of("c", 0.0, "f", 1.0));
            strategy.put(
                    "3:" + ranges.get(BTN.ordinal()).getFirst().key() + ":" + first + "f",
                    Map.of("c", 0.0, "f", 1.0));
            strategy.put(
                    "4:" + ranges.get(SB.ordinal()).getFirst().key() + ":" + first + "ff",
                    Map.of("c", 0.0, "f", 1.0));
        }
        return new CfrSolution(1, strategy);
    }

    @Test
    void observedPriorCallOrFoldChangesThePosteriorAndMatchesSourceGameEv() {
        var oracle = oracle();
        var source = game(oracle);
        var solution = solution();
        var hero = ranges().get(BB.ordinal()).getFirst();
        var afterCall =
                SixMaxForcedShovePolicyBridge.evaluateLastBb(source, solution, "cfff", hero);
        var afterFold =
                SixMaxForcedShovePolicyBridge.evaluateLastBb(source, solution, "ffff", hero);

        assertEquals(2, afterCall.legalJointDeals());
        assertEquals(2, afterFold.legalJointDeals());
        assertEquals(-1, afterCall.foldEvBb());
        assertEquals(-1, afterFold.foldEvBb());
        assertEquals(300.5 * ((0.8 * 0.7 + 0.6 * 0.3) / 1.4) - 100, afterCall.callEvBb(), 1e-9);
        assertEquals(200.5 * ((0.2 * 0.7 + 2.4 * 0.3) / 2.6) - 100, afterFold.callEvBb(), 1e-9);
        assertTrue(afterCall.callEvBb() > afterFold.callEvBb());
        assertEquals(sourceConditionalEv(source, "cfff", true), afterCall.callEvBb(), 1e-9);
        assertEquals(sourceConditionalEv(source, "ffff", true), afterFold.callEvBb(), 1e-9);
        assertEquals(sourceConditionalEv(source, "cfff", false), afterCall.foldEvBb(), 1e-9);
    }

    @Test
    void rejectsImpossibleActionsMissingPoliciesAndMismatchedGames() {
        var oracle = oracle();
        var source = game(oracle);
        var hero = ranges().get(BB.ordinal()).getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShovePolicyBridge.evaluateLastBb(
                                source, solution(), "cfffz", hero));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShovePolicyBridge.evaluateLastBb(
                                source, new CfrSolution(1, Map.of()), "cfff", hero));
        var impossible = new HashMap<>(solution().strategy());
        for (var hand : ranges().get(HJ.ordinal()))
            impossible.put("1:" + hand.key() + ":", Map.of("c", 0.0, "f", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShovePolicyBridge.evaluateLastBb(
                                source, new CfrSolution(1, impossible), "cfff", hero));
        var wrongStack =
                new MultiwayPreflopCallGame(
                        List.of(UTG, HJ, CO, BTN, SB, BB),
                        ranges(),
                        List.of(50.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                        50,
                        0,
                        oracle);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxForcedShovePolicyBridge.evaluateLastBb(
                                wrongStack, solution(), "cfff", hero));
    }

    @Test
    void solvedProfileTrainerAndBettingBridgeAgreeForRakedAndUnrakedQuestions() {
        for (CashRakeRule rule : List.of(CashRakeRule.none(), new CashRakeRule(0.05, 1, true))) {
            var source = game(oracle(), rule);
            var solved = new MultiPlayerCfrSolver<>(source, CfrSolver.Variant.CFR_PLUS).solve(100);
            var trainer = new MultiwayCallTrainer(source, solved);
            for (long seed = 0; seed < 12; seed++) {
                var question = trainer.question(seed, BB.ordinal());
                var feedback = trainer.grade(question, MultiwayCallTrainer.Action.CALL);
                StringBuilder history = new StringBuilder();
                for (var prior : question.priorResponses())
                    history.append(prior.action() == MultiwayCallTrainer.Action.CALL ? 'c' : 'f');
                var bridged =
                        SixMaxForcedShovePolicyBridge.evaluateLastBb(
                                source,
                                solved,
                                history.toString(),
                                ranges().get(BB.ordinal()).getFirst());
                assertEquals(feedback.callEvBb(), bridged.callEvBb(), 1e-9);
                assertEquals(feedback.foldEvBb(), bridged.foldEvBb(), 1e-9);
            }
        }
    }

    private static double sourceConditionalEv(
            MultiwayPreflopCallGame source, String prior, boolean call) {
        double mass = 0;
        double utility = 0;
        for (var outcome : source.chanceOutcomes(source.initialState())) {
            var state = outcome.state();
            boolean king =
                    source.dealtCombos(state)
                            .get(HJ.ordinal())
                            .key()
                            .equals(combo("KS", "KH", 1).key());
            double likelihood = prior.charAt(0) == 'c' ? (king ? 0.8 : 0.2) : (king ? 0.2 : 0.8);
            state = source.afterAction(state, String.valueOf(prior.charAt(0)));
            for (int index = 1; index < prior.length(); index++)
                state = source.afterAction(state, String.valueOf(prior.charAt(index)));
            state = source.afterAction(state, call ? "c" : "f");
            double weight = outcome.probability() * likelihood;
            mass += weight;
            utility += weight * source.terminalUtilities(state)[BB.ordinal()];
        }
        return utility / mass;
    }
}
