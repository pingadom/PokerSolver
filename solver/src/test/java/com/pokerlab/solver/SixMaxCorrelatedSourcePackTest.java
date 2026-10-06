package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxCorrelatedSourcePackTest {
    static SixMaxPreflopSolutionPack source() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("../docs/data/sixmax-correlated-source-pack.json")));
    }

    @Test
    void exactSourceBindsTwelveUnblockedDealsAndEveryActiveSubset() throws Exception {
        var pack = source();
        assertEquals(
                MultiwayPackJson.readFullRoundSpot(
                        Files.readString(Path.of("../docs/data/sixmax-correlated-spot.json"))),
                pack.spot());
        assertEquals("VALIDATION_ONLY", pack.publicationStatus());
        assertEquals("EXACT_ENUMERATION", pack.payoffMethod());
        assertEquals(500, pack.solution().iterations());
        assertEquals(10_172, pack.solution().strategy().size());
        assertEquals(12 * 57, pack.payoffs().size());
        for (var entry : pack.payoffs()) {
            assertEquals(658_008, entry.estimate().trials());
            for (double error : entry.estimate().standardErrors()) assertEquals(0, error);
        }
        var base = pack.rebuildGame();
        assertEquals(12, base.chanceOutcomes(base.initialState()).size());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(base, pack.solution(), 200_000)
                        .addedInformationSets());
        assertEquals(
                pack.nashConvBb(),
                MultiPlayerInformationSetBestResponse.assess(base, pack.solution()).nashConvBb(),
                1e-12);
        assertTrue(pack.nashConvBb() < .05);
        assertEquals(
                "7d077588d06a4100336cc989892cf953c94f82220f73008c8962a6f974b9cbc8",
                MultiwayPackJson.fullRoundContentHash(pack));
    }

    @Test
    void boundedDiverseMenuKeepsAllRootsAndTwelveWorldPostflopSupport() throws Exception {
        var pack = source();
        var base = pack.rebuildGame();
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var settings = SixMaxReachedContinuationStudy.SelectionSettings.diverse(.05);
        // Seed 711 is intentionally not redrawn or pruned to force the larger source to fit.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReachedContinuationStudy.select(
                                base, pack.solution(), 2, 1, 711, budget, settings));
        var plan =
                SixMaxReachedContinuationStudy.select(
                        base, pack.solution(), 2, 1, 715, budget, settings);
        assertEquals(
                new SixMaxContinuationStudyBudget.Cost(16, 1_896_409),
                budget.validate(plan.game()));
        assertEquals(12, plan.game().chanceOutcomes(plan.game().initialState()).size());
        assertEquals(
                List.of(2, 6),
                plan.selectedHistories().stream()
                        .map(SixMaxReachedContinuationStudy.SelectedHistory::sourceReachRank)
                        .toList());
        var audit = SixMaxPrivateSupportAudit.assess(plan.game(), pack.solution());
        assertEquals(List.of(Seat.HJ, Seat.CO, Seat.BTN, Seat.BB), audit.uncertainSeats());
        assertEquals(
                List.of(4, 12),
                audit.boards().stream()
                        .map(SixMaxPrivateSupportAudit.BoardSupport::counterfactualJointDeals)
                        .toList());
        assertEquals(
                List.of(4, 12),
                audit.boards().stream()
                        .map(SixMaxPrivateSupportAudit.BoardSupport::reachedJointDeals)
                        .toList());
        assertEquals(List.of(Seat.HJ, Seat.BB), audit.boards().get(1).uncertainFoldedSeats());
        assertEquals(List.of("3c", "As", "Qh"), audit.boards().getFirst().flop());
        assertEquals(List.of("3c", "5h", "Kc"), audit.boards().get(1).flop());
        for (var board : audit.boards()) {
            assertEquals(2, board.firstPlayerRootInformationSets());
            assertEquals(2, board.secondPlayerRootInformationSets());
        }
    }
}
