package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectedCoarseBoardBucketsTest {
    @Test
    void coarserObservationSharesMoreBoardsWithoutChangingPhysicalPayoffs() {
        var fine = ButtonBigBlindRangeValidationFixture.createBucketed();
        var texture = ButtonBigBlindRangeValidationFixture.createTextureBucketed();
        var coarse = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        assertEquals(
                "25e28ef764e7972baa54710b37d78d3f839a60863b0849f76642abe9beb4ebbf",
                fine.contentHash());
        assertNotEquals(fine.contentHash(), coarse.contentHash());
        assertNotEquals(fine.contentHash(), texture.contentHash());
        assertNotEquals(texture.contentHash(), coarse.contentHash());
        assertEquals(
                fine.chanceOutcomes(fine.initialState()),
                coarse.chanceOutcomes(coarse.initialState()));
        assertEquals(
                fine.chanceOutcomes(fine.initialState()),
                texture.chanceOutcomes(texture.initialState()));
        var deal =
                fine.chanceOutcomes(fine.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.bigBlind().key().equals("Ac Kc")
                                                && state.button().key().equals("Ad Ah"))
                        .findFirst()
                        .orElseThrow();
        var low =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2d", "7s", "Th"),
                        "",
                        null,
                        "",
                        null,
                        "");
        var high =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2d", "7s", "Qh"),
                        "",
                        null,
                        "",
                        null,
                        "");
        assertNotEquals(fine.informationSet(low), fine.informationSet(high));
        assertEquals(coarse.informationSet(low), coarse.informationSet(high));
        assertNotEquals(texture.informationSet(low), texture.informationSet(high));
        var paired =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2d", "2s", "Qh"),
                        "",
                        null,
                        "",
                        null,
                        "");
        assertNotEquals(coarse.informationSet(low), coarse.informationSet(paired));
        var folded = coarse.afterAction(coarse.afterAction(low, "b"), "f");
        assertEquals(fine.terminalUtility(folded), coarse.terminalUtility(folded));
        assertEquals(fine.terminalUtility(folded), texture.terminalUtility(folded));
        var called = fine.afterAction(fine.afterAction(deal, "open3"), "call");
        assertEquals(
                fine.sampleChanceOutcome(called, 0.37), coarse.sampleChanceOutcome(called, 0.37));
        assertEquals(
                fine.sampleChanceOutcome(called, 0.37), texture.sampleChanceOutcome(called, 0.37));
    }

    @Test
    void earlierBucketsAndActionsRemainInLaterInformationSets() {
        var game = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        var deal =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.bigBlind().key().equals("Ac Kc")
                                                && state.button().key().equals("Ad Ah"))
                        .findFirst()
                        .orElseThrow();
        var flop =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deal.bigBlind(),
                        deal.button(),
                        "oc",
                        cards("2d", "7s", "Th"),
                        "",
                        null,
                        "",
                        null,
                        "");
        var checked = game.afterAction(game.afterAction(flop, "k"), "k");
        var turn = game.sampleChanceOutcome(checked, 0.4).state();
        String turnKey = game.informationSet(turn);
        assertTrue(turnKey.contains("|F:kk|T:"));
        var turnChecked = game.afterAction(game.afterAction(turn, "k"), "k");
        var river = game.sampleChanceOutcome(turnChecked, 0.4).state();
        String riverKey = game.informationSet(river);
        assertTrue(riverKey.contains("|F:kk|T:"));
        assertTrue(riverKey.contains("|R:"));
    }

    @Test
    void commonReachMeasuresTheSameHandsForBothObservationModes() {
        var report = PhysicalBoardObservationCoverage.assess(2_000, 10, 42);
        assertEquals(report, PhysicalBoardObservationCoverage.assess(2_000, 10, 42));
        assertEquals(3, report.streets().size());
        assertNotEquals(report.fineGameHash(), report.coarseGameHash());
        for (var street : report.streets()) {
            assertTrue(street.reached() > 0);
            assertTrue(street.coarseBuckets() <= street.fineBuckets());
            assertTrue(street.coarseBuckets() <= street.textureBuckets());
            assertTrue(street.textureBuckets() <= street.fineBuckets());
            assertTrue(street.equityBuckets() >= street.coarseBuckets());
            assertTrue(street.coarseSupportedStates() >= street.fineSupportedStates());
            assertTrue(street.textureSupportedStates() >= street.fineSupportedStates());
            assertTrue(street.coarseSupportedStates() >= street.textureSupportedStates());
            assertTrue(street.coarseSupportedStates() >= street.equitySupportedStates());
            if (street.street() != PhysicalConnectedStreetDeviationAudit.Street.RIVER) {
                assertEquals(street.coarseBuckets(), street.equityBuckets());
                assertEquals(street.coarseSupportedStates(), street.equitySupportedStates());
            }
            assertTrue(street.coarseSupportRate() >= street.fineSupportRate());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalBoardObservationCoverage.assess(2_000, 1, 42));
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
