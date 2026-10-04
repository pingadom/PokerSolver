package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxDiverseSourcePackTest {
    @Test
    void exactPayoffsBindFourDealsAndTheDeclaredSuitPairAndBroadwayRanges() throws Exception {
        var pack =
                MultiwayPackJson.readFullRound(
                        Files.readString(Path.of("../docs/data/sixmax-diverse-source-pack.json")));
        var declared =
                MultiwayPackJson.readFullRoundSpot(
                        Files.readString(Path.of("../docs/data/sixmax-diverse-spot.json")));
        assertEquals(declared, pack.spot());
        assertEquals("VALIDATION_ONLY", pack.publicationStatus());
        assertEquals("EXACT_ENUMERATION", pack.payoffMethod());
        assertEquals(500, pack.solution().iterations());
        assertEquals(List.of("4c 4d"), keys(pack, 0));
        assertEquals(List.of("9d Kd"), keys(pack, 1));
        assertEquals(List.of("Qh Th"), keys(pack, 2));
        assertEquals(List.of("8h 8s", "Js Ts"), keys(pack, 3));
        assertEquals(List.of("5c 6c"), keys(pack, 4));
        assertEquals(List.of("7d 7h", "Ac Jc"), keys(pack, 5));
        var game = pack.rebuildGame();
        assertEquals(4, game.chanceOutcomes(game.initialState()).size());
        for (var outcome : game.chanceOutcomes(game.initialState()))
            assertEquals(.25, outcome.probability(), 1e-15);
        assertEquals(4 * 57, pack.payoffs().size());
        for (var payoff : pack.payoffs()) {
            assertEquals(658_008, payoff.estimate().trials());
            for (double error : payoff.estimate().standardErrors()) assertEquals(0, error);
        }
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertEquals(8_305, pack.solution().strategy().size());
        assertEquals(
                pack.solution(),
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(500));
        assertTrue(pack.nashConvBb() < .02);
        assertEquals(
                "3a8781ca8ab82966bc7290b51fcee4db328c37b862636a682dae4cf3703c9fb6",
                MultiwayPackJson.fullRoundContentHash(pack));
    }

    private static List<String> keys(SixMaxPreflopSolutionPack pack, int seat) {
        return pack.spot().ranges().get(seat).stream().map(WeightedCombo::key).toList();
    }
}
