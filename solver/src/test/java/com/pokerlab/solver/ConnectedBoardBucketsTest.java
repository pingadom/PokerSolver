package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectedBoardBucketsTest {
    @Test
    void bucketingSharesSomeBoardsButSeparatesMadeStrengthAndPreservesHistory() {
        var exact = ButtonBigBlindPhysicalDeckFixture.create();
        var bucketed = ButtonBigBlindPhysicalDeckFixture.createBucketed();
        var deal =
                bucketed.chanceOutcomes(bucketed.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.button().key().equals("Kh Qh"))
                        .findFirst()
                        .orElseThrow();
        var first =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2c", "7d", "Th"),
                        "",
                        null,
                        "",
                        null,
                        "");
        var second =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2d", "7c", "Ts"),
                        "",
                        null,
                        "",
                        null,
                        "");
        var paired =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2c", "7d", "Kc"),
                        "",
                        null,
                        "",
                        null,
                        "");
        assertEquals(bucketed.informationSet(first), bucketed.informationSet(second));
        assertNotEquals(exact.informationSet(first), exact.informationSet(second));
        assertNotEquals(bucketed.informationSet(first), bucketed.informationSet(paired));
        var otherBlind =
                bucketed.chanceOutcomes(bucketed.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.button().equals(deal.button())
                                                && !state.bigBlind().equals(deal.bigBlind()))
                        .findFirst()
                        .orElseThrow();
        var sameObservation =
                new ButtonBigBlindPhysicalDeckGame.State(
                        otherBlind.bigBlind(),
                        otherBlind.button(),
                        "oc",
                        first.flop(),
                        "",
                        null,
                        "",
                        null,
                        "");
        assertEquals(
                bucketed.informationSet(bucketed.afterAction(first, "k")),
                bucketed.informationSet(bucketed.afterAction(sameObservation, "k")));
        assertNotEquals(exact.contentHash(), bucketed.contentHash());
        var checked = bucketed.afterAction(first, "k");
        assertNotEquals(bucketed.informationSet(first), bucketed.informationSet(checked));
        assertTrue(bucketed.informationSet(checked).contains("|F:k"));
        assertThrows(
                IllegalArgumentException.class,
                () -> PublicBoardBucket.key(cards("2c", "7d", "Kh"), deal.button()));
    }

    @Test
    void physicalPayoffsAndChanceDistributionAreIndependentOfObservationMode() {
        var exact = ButtonBigBlindPhysicalDeckFixture.create();
        var bucketed = ButtonBigBlindPhysicalDeckFixture.createBucketed();
        var deal = exact.chanceOutcomes(exact.initialState()).getFirst().state();
        var called = exact.afterAction(exact.afterAction(deal, "open3"), "call");
        for (double quantile : List.of(0.0, 0.17, 0.5, 0.93)) {
            var flop = exact.sampleChanceOutcome(called, quantile).state();
            assertEquals(
                    exact.sampleChanceOutcome(called, quantile),
                    bucketed.sampleChanceOutcome(called, quantile));
            var flopFold = exact.afterAction(exact.afterAction(flop, "b"), "f");
            assertEquals(exact.terminalUtility(flopFold), bucketed.terminalUtility(flopFold));
        }
    }

    @Test
    void heldOutAuditReportsFallbackCoverageAndIsReproducible() {
        var game = ButtonBigBlindPhysicalDeckFixture.createBucketed();
        var solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(50);
        var report = PhysicalConnectedStrategyAudit.assess(game, solution, 500, 43);
        assertEquals(report, PhysicalConnectedStrategyAudit.assess(game, solution, 500, 43));
        assertEquals(game.contentHash(), report.gameHash());
        assertTrue(report.decisions() >= 500);
        assertTrue(report.missingStrategyDecisions() > 0);
        assertTrue(report.missingStrategyRate() > 0 && report.missingStrategyRate() < 1);
        assertTrue(Double.isFinite(report.meanBigBlindBb()));
        assertTrue(report.samplingStandardErrorBb() > 0);
    }

    @Test
    void preflopDeviationAuditConditionsBlindOnObservedButtonOpen() {
        var game = ButtonBigBlindPhysicalDeckFixture.createBucketed();
        var solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(50);
        var report = PhysicalConnectedPreflopDeviationAudit.assess(game, solution, 100, 44);
        assertEquals(
                report, PhysicalConnectedPreflopDeviationAudit.assess(game, solution, 100, 44));
        assertEquals(4, report.decisions().size());
        for (String player : List.of("BTN", "BB"))
            assertEquals(
                    1,
                    report.decisions().stream()
                            .filter(decision -> decision.player().equals(player))
                            .mapToDouble(
                                    PhysicalConnectedPreflopDeviationAudit.Decision::reachWeight)
                            .sum(),
                    1e-12);
        assertTrue(
                report.decisions().stream()
                        .allMatch(
                                decision ->
                                        decision.estimatedImprovementBb() >= -1e-12
                                                && decision.continueStandardErrorBb() >= 0));
        var button =
                report.decisions().stream()
                        .filter(
                                decision ->
                                        decision.player().equals("BTN")
                                                && decision.ownHand().equals("Ac Ad"))
                        .findFirst()
                        .orElseThrow();
        var blind =
                report.decisions().stream()
                        .filter(
                                decision ->
                                        decision.player().equals("BB")
                                                && decision.ownHand().equals("Jc Jd"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(button.reachWeight() > 0);
        assertTrue(blind.reachWeight() > 0);
        assertNotEquals(button.foldUtilityBb(), blind.foldUtilityBb());
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
