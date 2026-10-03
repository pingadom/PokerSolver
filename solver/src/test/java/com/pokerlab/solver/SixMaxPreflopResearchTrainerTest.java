package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPreflopResearchTrainerTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static SixMaxPreflopCheckdownGame game() {
        return game(0, 0);
    }

    private static SixMaxPreflopCheckdownGame game(double strongPairError, double weakPairError) {
        return new SixMaxPreflopCheckdownGame(
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                List.of(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KS", "KH", 1)),
                        List.of(combo("QS", "QH", 1)),
                        List.of(combo("JS", "JH", 1), combo("5S", "5H", 3)),
                        List.of(combo("TS", "TH", 1)),
                        List.of(combo("9S", "9H", 1))),
                CashRakeRule.none(),
                (hands, mask) -> {
                    double[] shares = new double[6];
                    if ((mask & (1 << UTG.ordinal())) != 0 && (mask & (1 << BTN.ordinal())) != 0) {
                        int winner =
                                hands.get(BTN.ordinal()).key().contains("Js")
                                        ? UTG.ordinal()
                                        : (mask & (1 << BB.ordinal())) != 0
                                                ? BB.ordinal()
                                                : BTN.ordinal();
                        shares[winner] = 1;
                    } else {
                        int winner = Integer.numberOfTrailingZeros(mask);
                        shares[winner] = 1;
                    }
                    double[] errors = new double[6];
                    double error =
                            hands.get(BTN.ordinal()).key().contains("Js")
                                    ? strongPairError
                                    : weakPairError;
                    for (int seat = 0; seat < 6; seat++)
                        if ((mask & (1 << seat)) != 0) errors[seat] = error;
                    return new MultiwayShowdownEstimate(shares, errors, error == 0 ? 0 : 100);
                });
    }

    private static CfrSolution alwaysCallOrCheck(SixMaxPreflopCheckdownGame game) {
        var solved = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        Map<String, Map<String, Double>> policy = new LinkedHashMap<>();
        for (var info : solved.strategy().entrySet()) {
            String chosen = info.getValue().containsKey("call") ? "call" : "check";
            Map<String, Double> actions = new LinkedHashMap<>();
            for (String action : info.getValue().keySet())
                actions.put(action, action.equals(chosen) ? 1.0 : 0.0);
            policy.put(info.getKey(), actions);
        }
        return new CfrSolution(1, policy);
    }

    private static CfrSolution buttonCallRevealsHand(SixMaxPreflopCheckdownGame game) {
        var baseline = alwaysCallOrCheck(game);
        Map<String, Map<String, Double>> policy = new LinkedHashMap<>(baseline.strategy());
        for (var info : baseline.strategy().entrySet()) {
            if (!info.getKey().startsWith(BTN.ordinal() + ":")
                    || !info.getValue().containsKey("call")) continue;
            double call = info.getKey().contains("Js") ? 0.8 : 0.2;
            Map<String, Double> actions = new LinkedHashMap<>();
            for (String action : info.getValue().keySet())
                actions.put(
                        action,
                        action.equals("call") ? call : action.equals("fold") ? 1 - call : 0);
            policy.put(info.getKey(), actions);
        }
        return new CfrSolution(1, policy);
    }

    @Test
    void gradesShownHandAgainstAllCompatibleHiddenDealsAndFuturePolicy() {
        var game = game();
        var trainer = new SixMaxPreflopResearchTrainer(game, alwaysCallOrCheck(game));
        SixMaxPreflopResearchTrainer.Question utg = null;
        for (long seed = 0; seed < 100 && utg == null; seed++) {
            var candidate = trainer.question(seed);
            if (candidate.actingSeat() == UTG) utg = candidate;
        }
        assertNotNull(utg);
        assertEquals(List.of(), utg.priorActions());
        assertEquals("VALIDATION_ONLY", utg.publicationStatus());
        assertEquals(SixMaxPreflopCheckdownGame.ChanceModel.EXACT_RANGE_PRODUCT, utg.chanceModel());
        assertEquals(0, utg.chanceSamples());
        assertEquals(1.5, utg.potBb(), 1e-12);
        assertEquals(1, utg.toCallBb(), 1e-12);
        assertEquals(0, utg.maximumPayoffStandardErrorBb());
        assertEquals(List.of("fold", "call", "raise:100.0"), utg.legalActions());

        var fold = trainer.grade(utg, "fold");
        var call = trainer.grade(utg, "call");
        var shove = trainer.grade(utg, "raise:100.0");
        assertEquals(0, fold.selectedEvBb(), 1e-10);
        assertEquals(0.5, call.selectedEvBb(), 1e-10);
        assertEquals(50, shove.selectedEvBb(), 1e-9);
        assertEquals(50, fold.evLossBb(), 1e-9);
        assertEquals(49.5, call.evLossBb(), 1e-9);
        assertEquals(0, shove.evLossBb(), 1e-9);
        assertEquals(1, call.actionFrequency().get("call"), 1e-12);
        assertEquals(0, call.actionFrequency().get("raise:100.0"), 1e-12);
        assertEquals(call.actionEvBb(), fold.actionEvBb());
        assertTrue(call.actionPayoffStandardErrorBb().values().stream().allMatch(v -> v == 0));
    }

    @Test
    void reportsActionSpecificPayoffUncertaintyRatherThanOnlyTheGlobalMaximum() {
        var sampledGame = game(0.01, 0.01);
        var trainer = new SixMaxPreflopResearchTrainer(sampledGame, alwaysCallOrCheck(sampledGame));
        SixMaxPreflopResearchTrainer.Question utg = null;
        for (long seed = 0; seed < 100 && utg == null; seed++) {
            var candidate = trainer.question(seed);
            if (candidate.actingSeat() == UTG) utg = candidate;
        }
        assertNotNull(utg);
        var feedback = trainer.grade(utg, "call");
        assertEquals(6, utg.maximumPayoffStandardErrorBb(), 1e-12);
        assertEquals(0, feedback.actionPayoffStandardErrorBb().get("fold"), 1e-12);
        assertEquals(0.06, feedback.actionPayoffStandardErrorBb().get("call"), 1e-12);
        assertEquals(6, feedback.actionPayoffStandardErrorBb().get("raise:100.0"), 1e-12);
        assertEquals(
                feedback.actionEvBb().keySet(), feedback.actionPayoffStandardErrorBb().keySet());
    }

    @Test
    void samplesReachedSeatsReproduciblyAndRejectsTamperedOrIllegalAnswers() {
        var game = game();
        var trainer = new SixMaxPreflopResearchTrainer(game, alwaysCallOrCheck(game));
        boolean sawButton = false;
        for (long seed = 0; seed < 80; seed++) {
            var question = trainer.question(seed);
            assertEquals(question, trainer.question(seed));
            assertFalse(question.legalActions().isEmpty());
            assertEquals("VALIDATION_ONLY", question.publicationStatus());
            if (question.actingSeat() == BTN) {
                sawButton = true;
                assertTrue(question.priorActions().size() >= 3);
                assertTrue(
                        question.heroCombo().contains("J") || question.heroCombo().contains("5"));
            }
            var feedback = trainer.grade(question, question.legalActions().getFirst());
            assertTrue(feedback.evLossBb() >= 0);
            assertEquals(question.legalActions().size(), feedback.actionEvBb().size());
        }
        assertTrue(sawButton);
        var question = trainer.question(1);
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(question, "teleport"));
        var tampered =
                new SixMaxPreflopResearchTrainer.Question(
                        question.seed(),
                        question.actingSeat(),
                        "2S2H",
                        question.priorActions(),
                        question.legalActions(),
                        question.potBb(),
                        question.toCallBb(),
                        question.stackBb(),
                        question.smallBlindBb(),
                        question.nashConvBb(),
                        question.maximumPayoffStandardErrorBb(),
                        question.chanceModel(),
                        question.chanceSamples(),
                        question.publicationStatus());
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(tampered, "call"));
    }

    @Test
    void priorButtonCallReweightsHiddenHandsBeforeBigBlindCheckEv() {
        var game = game();
        var trainer = new SixMaxPreflopResearchTrainer(game, buttonCallRevealsHand(game));
        SixMaxPreflopResearchTrainer.Question bigBlind = null;
        for (long seed = 0; seed < 1000 && bigBlind == null; seed++) {
            var candidate = trainer.question(seed);
            if (candidate.actingSeat() == BB
                    && candidate.priorActions().stream()
                            .anyMatch(
                                    action ->
                                            action.seat() == BTN && action.action().equals("call")))
                bigBlind = candidate;
        }
        assertNotNull(bigBlind);
        assertEquals(6, bigBlind.potBb(), 1e-12);
        assertEquals(0, bigBlind.toCallBb(), 1e-12);
        var feedback = trainer.grade(bigBlind, "check");
        // P(BTN holds 55 | call) = (3 * 0.2) / (1 * 0.8 + 3 * 0.2) = 3/7.
        assertEquals(6.0 * 3 / 7 - 1, feedback.selectedEvBb(), 1e-10);
    }

    @Test
    void conditionalPayoffUncertaintyUsesTheSamePosteriorAsTheActionEv() {
        var sampledGame = game(0.01, 0.02);
        var trainer =
                new SixMaxPreflopResearchTrainer(sampledGame, buttonCallRevealsHand(sampledGame));
        SixMaxPreflopResearchTrainer.Question bigBlind = null;
        for (long seed = 0; seed < 1000 && bigBlind == null; seed++) {
            var candidate = trainer.question(seed);
            if (candidate.actingSeat() == BB
                    && candidate.priorActions().stream()
                            .anyMatch(
                                    action ->
                                            action.seat() == BTN && action.action().equals("call")))
                bigBlind = candidate;
        }
        assertNotNull(bigBlind);
        var feedback = trainer.grade(bigBlind, "check");
        // Same posterior as the EV: P(JJ | call)=4/7 and P(55 | call)=3/7.
        assertEquals(
                6 * (4.0 / 7 * 0.01 + 3.0 / 7 * 0.02),
                feedback.actionPayoffStandardErrorBb().get("check"),
                1e-12);
    }
}
