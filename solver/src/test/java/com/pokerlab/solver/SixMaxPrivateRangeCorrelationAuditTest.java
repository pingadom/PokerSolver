package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPrivateRangeCorrelationAuditTest {
    static SixMaxPreflopCheckdownGame correlatedBase() throws Exception {
        var spot =
                MultiwayPackJson.readFullRoundSpot(
                        Files.readString(Path.of("../docs/data/sixmax-correlated-spot.json")));
        return new SixMaxPreflopCheckdownGame(
                spot.rules(),
                spot.ranges(),
                spot.rake(),
                (hands, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    @Test
    void physicalBlockingChangesMarginalsAndProducesKnownDependence() throws Exception {
        var base = correlatedBase();
        var roots = base.chanceOutcomes(base.initialState());
        assertEquals(12, roots.size());
        for (var root : roots) {
            assertEquals(1.0 / 12, root.probability(), 1e-15);
            var cards =
                    base.dealtHands(root.state()).stream()
                            .flatMap(h -> List.of(h.first(), h.second()).stream())
                            .toList();
            assertEquals(12, new java.util.HashSet<>(cards).size());
        }
        var report = SixMaxPrivateRangeCorrelationAudit.assess(base);
        assertEquals(15, report.pairs().size());
        assertEquals("EXACT_RANGE_PRODUCT", report.chanceModel());
        var pair =
                report.pairs().stream()
                        .filter(p -> p.firstSeat() == Seat.HJ && p.secondSeat() == Seat.CO)
                        .findFirst()
                        .orElseThrow();
        assertEquals(4, pair.marginalProductPairs());
        assertEquals(3, pair.supportedPairs());
        var expected =
                Map.of(
                        List.of("9d Kd", "Ah Kh"),
                        1.0 / 3,
                        List.of("9d Kd", "Qh Th"),
                        1.0 / 3,
                        List.of("Ah Jh", "Ah Kh"),
                        0.0,
                        List.of("Ah Jh", "Qh Th"),
                        1.0 / 3);
        for (var entry : pair.jointProbabilities())
            assertEquals(
                    expected.get(List.of(entry.firstCombo(), entry.secondCombo())),
                    entry.probability(),
                    1e-15);
        // Three equiprobable compatible pairs give HJ/CO marginals 2/3 and 1/3.
        assertEquals(1.0 / 9, pair.unsupportedIndependentMass(), 1e-15);
        assertEquals(2.0 / 9, pair.totalVariationFromIndependent(), 1e-15);
        assertEquals(Math.log(27.0 / 16) / (3 * Math.log(2)), pair.mutualInformationBits(), 1e-14);
        for (var independent : report.pairs()) {
            assertEquals(
                    1,
                    independent.jointProbabilities().stream()
                            .mapToDouble(SixMaxPrivateRangeCorrelationAudit.ComboPair::probability)
                            .sum(),
                    1e-14);
            if (independent != pair) {
                assertEquals(0, independent.mutualInformationBits(), 1e-14);
                assertEquals(0, independent.totalVariationFromIndependent(), 1e-14);
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> pair.jointProbabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.pairs().clear());
    }

    @Test
    void twelveConnectedWorldsPreserveFoldedBlockersAndConcealOtherHands() throws Exception {
        var base = correlatedBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base, List.of(SixMaxConnectedPreflopGameTest.selection("2c 3c 4h")));
        var policy =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                base, new CfrSolution(1, Map.of()), 200_000)
                        .solution();
        var audit = SixMaxPrivateSupportAudit.assess(game, policy);
        assertEquals(List.of(Seat.HJ, Seat.CO, Seat.BTN, Seat.BB), audit.uncertainSeats());
        assertEquals(2.0 / 3, audit.sourceMarginals().get(Seat.HJ).get("9d Kd"), 1e-14);
        assertEquals(1.0 / 3, audit.sourceMarginals().get(Seat.CO).get("Ah Kh"), 1e-14);
        assertEquals(12, audit.boards().getFirst().counterfactualJointDeals());
        assertEquals(12, audit.boards().getFirst().reachedJointDeals());
        assertEquals(List.of(Seat.HJ, Seat.CO), audit.boards().getFirst().uncertainFoldedSeats());
        assertEquals(2, audit.boards().getFirst().firstPlayerRootInformationSets());
        assertEquals(2, audit.boards().getFirst().secondPlayerRootInformationSets());
        assertEquals(
                12,
                SixMaxContinuationStudyBudget.widerFlops().validate(game).compatibleDealFlops());
        var keys = new LinkedHashMap<String, String>();
        for (var root : base.chanceOutcomes(base.initialState())) {
            var pre =
                    game.replayPreflop(
                            SixMaxConnectedPreflopGameTest.HISTORY, root.state().dealIndex());
            var flop = game.chanceOutcomes(pre).getFirst().state();
            for (int actor = 0; actor < 2; actor++) {
                String own = base.dealtHands(root.state()).get(game.currentPlayer(flop)).key();
                String previous = keys.putIfAbsent(actor + own, game.informationSet(flop));
                if (previous != null) assertEquals(previous, game.informationSet(flop));
                flop = game.afterAction(flop, "check");
            }
            // Continue on shared legal public cards, retaining concealment across both streets.
            for (String card : List.of("2d", "3d")) {
                Card publicCard = Card.parse(card);
                var street =
                        game.chanceOutcomes(flop).stream()
                                .filter(
                                        o ->
                                                publicCard.equals(
                                                        card.equals("2d")
                                                                ? o.state().postflop().turn()
                                                                : o.state().postflop().river()))
                                .findFirst()
                                .orElseThrow()
                                .state();
                for (int actor = 0; actor < 2; actor++) {
                    String own =
                            base.dealtHands(root.state()).get(game.currentPlayer(street)).key();
                    String key = card + actor + own;
                    String previous = keys.putIfAbsent(key, game.informationSet(street));
                    if (previous != null) assertEquals(previous, game.informationSet(street));
                    street = game.afterAction(street, "check");
                }
                flop = street;
            }
            assertTrue(game.isTerminal(flop));
            double total = 0;
            for (double utility : game.terminalUtilities(flop)) total += utility;
            assertEquals(0, total, 1e-12);
        }
        var blocked =
                new SixMaxConnectedPreflopGame(
                        base, List.of(SixMaxConnectedPreflopGameTest.selection("Ah 2c 3c")));
        var blockedAudit = SixMaxPrivateSupportAudit.assess(blocked, policy).boards().getFirst();
        assertEquals(4, blockedAudit.counterfactualJointDeals());
        assertEquals(1.0 / 3, blockedAudit.compatiblePriorMass(), 1e-14);
        assertEquals(Map.of("9d Kd", 1.0), blockedAudit.counterfactualMarginals().get(Seat.HJ));
        assertEquals(Map.of("Qh Th", 1.0), blockedAudit.counterfactualMarginals().get(Seat.CO));
    }

    @Test
    void exceedingStudyBudgetDoesNotTrimTwelvePrivateWorlds() throws Exception {
        var base = correlatedBase();
        var game =
                new SixMaxConnectedPreflopGame(
                        base,
                        List.of(SixMaxConnectedPreflopGameTest.selection("2c 3c 4h", "2d 3d 4s")));
        assertEquals(12, game.chanceOutcomes(game.initialState()).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxContinuationStudyBudget.widerFlops().validate(game));
        assertEquals(
                24,
                game.coverage().getFirst().legalSelectedFlopsByDeal().stream()
                        .mapToInt(Integer::intValue)
                        .sum());
    }
}
