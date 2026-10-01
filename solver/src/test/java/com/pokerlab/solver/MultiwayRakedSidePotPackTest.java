package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MultiwayRakedSidePotPackTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void committedExactSixSeatRakedPackIsReproducible() throws IOException {
        String json;
        try (var input = getClass().getResourceAsStream("/six-seat-raked-side-pot-pack.json")) {
            assertNotNull(input);
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var pack = MultiwayPackJson.readRakedSidePot(json);
        assertEquals(json, MultiwayPackJson.writeRakedSidePot(pack));
        assertEquals("multiway-raked-side-pot-pack/v1", pack.schemaVersion());
        assertEquals(MultiwaySolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertEquals(new CashRakeRule(0.05, 1, true), pack.rakeRule());
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertEquals(31, pack.sourcePack().payoffs().size());
        assertEquals(
                "165a9632591d0d2a1f6500c848c368b17be8858c4f29cd160688c8887ce1a054",
                MultiwayPackJson.rakedSidePotContentHash(pack));
    }

    @Test
    void exactSourceBuildsStrictRakedArtifactAndRoundTrips() throws IOException {
        var pack = build();
        String json = MultiwayPackJson.writeRakedSidePot(pack);
        assertEquals(
                json, MultiwayPackJson.writeRakedSidePot(MultiwayPackJson.readRakedSidePot(json)));
        assertEquals(MultiwayRakedSidePotPack.SCHEMA_VERSION, MultiwayPackJson.schemaVersion(json));
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertTrue(pack.nashConvBb() < 0.01);
        assertTrue(pack.expectedRakeBb() >= 0 && pack.expectedRakeBb() <= 1);
        var game = pack.rebuildGame();
        assertEquals(1, game.chanceOutcomes(game.initialState()).size());
        assertEquals(31, pack.sourcePack().payoffs().size());
        assertEquals(MultiwayPackJson.rakedSidePotContentHash(pack), MultiwayCallSpot.sha256(json));
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.readSidePot(json));
    }

    @Test
    void tamperingWithRuleSourceStrategyOrMetricsIsRejected() throws IOException {
        var pack = build();
        ObjectNode changedRule = tree(pack);
        ((ObjectNode) changedRule.get("rakeRule")).put("fraction", 0.3);
        reject(changedRule);
        ObjectNode changedSourceHash = tree(pack);
        changedSourceHash.put("sourcePackHash", "0".repeat(64));
        reject(changedSourceHash);
        ObjectNode changedSourcePayoff = tree(pack);
        ((ObjectNode) changedSourcePayoff.get("sourcePack").get("payoffs").get(0).get("estimate"))
                .put("trials", 2);
        reject(changedSourcePayoff);
        ObjectNode changedGap = tree(pack);
        changedGap.put("nashConvBb", 1);
        reject(changedGap);
        ObjectNode changedRake = tree(pack);
        changedRake.put("expectedRakeBb", 0.5);
        reject(changedRake);
        ObjectNode missingStrategy = tree(pack);
        ObjectNode strategy = (ObjectNode) missingStrategy.get("solution").get("strategy");
        strategy.remove(strategy.fieldNames().next());
        reject(missingStrategy);
        ObjectNode unknownField = tree(pack);
        unknownField.put("admission", "PUBLISHED");
        reject(unknownField);
    }

    private static MultiwayRakedSidePotPack build() throws IOException {
        String sourceJson;
        try (var input =
                MultiwayRakedSidePotPackTest.class.getResourceAsStream(
                        "/six-seat-side-pot-pack.json")) {
            assertNotNull(input);
            sourceJson = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var source = MultiwayPackJson.readSidePot(sourceJson);
        return MultiwayRakedSidePotPackBuilder.build(
                source,
                new CashRakeRule(0.05, 1, true),
                500,
                CfrSolver.Variant.CFR_PLUS,
                "2026-10-01T00:00:00Z");
    }

    private static ObjectNode tree(MultiwayRakedSidePotPack pack) throws IOException {
        return (ObjectNode) JSON.readTree(MultiwayPackJson.writeRakedSidePot(pack));
    }

    private static void reject(ObjectNode tree) {
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readRakedSidePot(tree.toString()));
    }
}
