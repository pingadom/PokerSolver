package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.core.card.Card;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiwaySidePotPackTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void committedExactSixSeatPackRoundTripsWithoutBoardEnumeration() throws IOException {
        String json;
        try (var input = getClass().getResourceAsStream("/six-seat-side-pot-pack.json")) {
            assertNotNull(input);
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        MultiwaySidePotPack pack = MultiwayPackJson.readSidePot(json);
        assertEquals(MultiwaySidePotPack.SCHEMA_VERSION, pack.schemaVersion());
        assertEquals(MultiwaySolutionPack.VALIDATION_ONLY, pack.publicationStatus());
        assertEquals(List.of(30.0, 10.0, 20.0, 15.0, 25.0, 5.0), pack.spot().stacksBb());
        assertEquals(31, pack.payoffs().size());
        assertEquals(31, pack.solution().strategy().size());
        assertEquals(0, pack.maxTerminalPayoffSEBb());
        assertTrue(pack.nashConvBb() < 0.001);
        assertEquals(
                "c7d4c313e03650bb1bb7bbd99e5e84725426e8e179587821bd66ab13c3fa4f07",
                MultiwayPackJson.sidePotContentHash(pack));
        assertEquals(json, MultiwayPackJson.writeSidePot(pack));
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.read(json));
        assertEquals(
                pack.nashConvBb(),
                MultiwayCallBestResponse.assess(pack.rebuildGame(), pack.solution()).nashConvBb(),
                1e-12);
    }

    @Test
    void sampledThreeSeatPackIsDeterministicAndStacksAreHashed() {
        MultiwaySidePotSpot spot = threeSeatSpot();
        MultiwaySidePotPack pack = sampled(spot);
        assertEquals(3, pack.payoffs().size());
        assertTrue(pack.maxTerminalPayoffSEBb() > 0);
        assertEquals(
                MultiwayPackJson.writeSidePot(pack), MultiwayPackJson.writeSidePot(sampled(spot)));
        assertEquals(
                spot, MultiwayPackJson.readSidePotSpot(MultiwayPackJson.writeSidePotSpot(spot)));
        MultiwaySidePotSpot changed =
                new MultiwaySidePotSpot(
                        spot.id(),
                        spot.seats(),
                        spot.ranges(),
                        spot.committedBb(),
                        List.of(30.0, 11.0, 20.0),
                        spot.deadMoneyBb());
        assertNotEquals(spot.contentHash(), changed.contentHash());
        assertNotEquals(
                MultiwayPackJson.sidePotContentHash(pack),
                MultiwayPackJson.sidePotContentHash(sampled(changed)));
    }

    @Test
    void rejectsTamperedPayoffsStacksStrategyAndMetrics() throws Exception {
        MultiwaySidePotPack pack = sampled(threeSeatSpot());
        ObjectNode alteredStack = tree(pack);
        ((ArrayNode) alteredStack.get("spot").get("stacksBb"))
                .set(1, JSON.getNodeFactory().numberNode(11));
        reject(alteredStack);
        ObjectNode missingPayoff = tree(pack);
        ((ArrayNode) missingPayoff.get("payoffs")).remove(0);
        reject(missingPayoff);
        ObjectNode duplicatePayoff = tree(pack);
        ((ArrayNode) duplicatePayoff.get("payoffs"))
                .add(duplicatePayoff.get("payoffs").get(0).deepCopy());
        reject(duplicatePayoff);
        ObjectNode badShare = tree(pack);
        ((ArrayNode) badShare.get("payoffs").get(0).get("estimate").get("shares"))
                .set(0, JSON.getNodeFactory().numberNode(-1));
        reject(badShare);
        ObjectNode missingStrategy = tree(pack);
        ObjectNode strategy = (ObjectNode) missingStrategy.get("solution").get("strategy");
        strategy.remove(strategy.fieldNames().next());
        reject(missingStrategy);
        ObjectNode forgedGap = tree(pack);
        forgedGap.put("nashConvBb", forgedGap.get("nashConvBb").asDouble() + 1);
        reject(forgedGap);
        ObjectNode unknown = tree(pack);
        unknown.put("untrusted", true);
        reject(unknown);
        ObjectNode wrongSchema = tree(pack);
        wrongSchema.put("schemaVersion", MultiwaySolutionPack.SCHEMA_VERSION);
        reject(wrongSchema);
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readSidePot(MultiwayPackJson.writeSidePot(pack) + " {}"));
    }

    private static MultiwaySidePotPack sampled(MultiwaySidePotSpot spot) {
        return MultiwaySidePotPackBuilder.build(
                spot,
                100,
                CfrSolver.Variant.CFR_PLUS,
                new SeededMultiwayShowdownOracle(300, 42),
                MultiwaySolutionPack.SEEDED_MONTE_CARLO,
                42,
                "2026-09-26T00:00:00Z");
    }

    private static MultiwaySidePotSpot threeSeatSpot() {
        return new MultiwaySidePotSpot(
                "three-seat-side-pot",
                List.of(
                        PreflopAllInSpot.Seat.UTG,
                        PreflopAllInSpot.Seat.BTN,
                        PreflopAllInSpot.Seat.BB),
                List.of(
                        List.of(combo("As", "Ah")),
                        List.of(combo("Ks", "Kh")),
                        List.of(combo("Qs", "Qh"))),
                List.of(30.0, 1.0, 2.0),
                List.of(30.0, 10.0, 20.0),
                0);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static ObjectNode tree(MultiwaySidePotPack pack) throws Exception {
        return (ObjectNode) JSON.readTree(MultiwayPackJson.writeSidePot(pack));
    }

    private static void reject(ObjectNode tree) {
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readSidePot(tree.toString()));
    }
}
