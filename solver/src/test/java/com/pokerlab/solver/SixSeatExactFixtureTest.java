package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SixSeatExactFixtureTest {
    @Test
    void savedArtifactRebuildsFullGameAndSupportsSessionReview() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/six-seat-exact-pack.json")) {
            assertNotNull(stream);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var pack = MultiwayPackJson.read(json);
        assertEquals(json.trim(), MultiwayPackJson.write(pack));
        assertEquals(SixSeatValidationSpot.create().contentHash(), pack.spotHash());
        assertEquals(1984, pack.payoffs().size());
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertTrue(pack.nashConvBb() < 0.05);
        assertEquals("EXACT_ENUMERATION", pack.payoffMethod());
        var game = pack.rebuildGame();
        assertEquals(64, game.chanceOutcomes(game.initialState()).size());
        assertEquals(
                0,
                Arrays.stream(MultiPlayerStrategyEvaluator.utilities(game, pack.solution())).sum(),
                1e-8);
        var session = new MultiwayDrillSession(game, pack.solution());
        var review =
                session.review(42, 0, Collections.nCopies(10, MultiwayCallTrainer.Action.CALL));
        assertEquals(10, review.attempts().size());
        assertTrue(Double.isFinite(review.totalEvLossBb()));
        // Cross-check a saved deal against newly enumerated boards, independent of JSON metadata.
        var entry = pack.payoffs().get(0);
        var dealt =
                game.dealtCombos(
                        game.chanceOutcomes(game.initialState()).stream()
                                .map(ChanceOutcome::state)
                                .filter(
                                        state ->
                                                game.dealtCombos(state).stream()
                                                        .map(WeightedCombo::key)
                                                        .toList()
                                                        .equals(entry.dealtCombos()))
                                .findFirst()
                                .orElseThrow());
        var expected = new ExactMultiwayShowdownOracle().estimate(dealt, entry.activeMask());
        assertArrayEquals(expected.shares(), entry.estimate().shares(), 1e-12);
    }
}
