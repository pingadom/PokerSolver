package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MultiwaySolutionPackTest {
    private static final String GENERATED_AT = "2026-09-24T12:00:00Z";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void roundTripsSixSeatPackAndReconstructsWithoutCallingOriginalOracle() {
        AtomicInteger calls = new AtomicInteger();
        MultiwayCallSpot spot = sixSeatSpot();
        MultiwaySolutionPack pack =
                MultiwayPackBuilder.build(
                        spot,
                        50,
                        CfrSolver.Variant.CFR_PLUS,
                        (dealt, mask) -> {
                            calls.incrementAndGet();
                            return exactFixture(dealt, mask);
                        },
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        GENERATED_AT);
        assertEquals(31, calls.get());
        assertEquals(31, pack.payoffs().size());
        assertEquals(31, pack.solution().strategy().size());
        String serialized = MultiwayPackJson.write(pack);
        MultiwaySolutionPack loaded = MultiwayPackJson.read(serialized);
        assertEquals(serialized, MultiwayPackJson.write(loaded));
        assertEquals(0, loaded.maxTerminalPayoffSEBb());
        assertEquals(spot, loaded.spot());
        assertEquals(pack.solution(), loaded.solution());
        assertEquals(31, calls.get());
        var game = loaded.rebuildGame();
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(pack.rebuildGame(), pack.solution()),
                MultiPlayerStrategyEvaluator.utilities(game, loaded.solution()),
                1e-12);
        assertEquals(
                loaded.nashConvBb(),
                MultiwayCallBestResponse.assess(game, loaded.solution()).nashConvBb(),
                1e-12);
    }

    @Test
    void sampledPackIsReproducibleAndOnlyContainsUnblockedDeals() {
        MultiwaySolutionPack pack = sampled();
        assertEquals(9, pack.payoffs().size()); // Three valid deals, three active subsets each.
        assertTrue(pack.maxTerminalPayoffSEBb() > 0);
        assertEquals(MultiwayPackJson.write(pack), MultiwayPackJson.write(sampled()));
        assertEquals(
                MultiwayPackJson.write(pack),
                MultiwayPackJson.write(MultiwayPackJson.read(MultiwayPackJson.write(pack))));
        assertTrue(
                pack.payoffs().stream()
                        .noneMatch(
                                entry ->
                                        entry.dealtCombos().get(0).contains("As")
                                                && entry.dealtCombos().get(1).contains("As")));
        assertTrue(pack.payoffs().stream().allMatch(entry -> entry.estimate().trials() == 300));
    }

    @Test
    void hashesAllAssumptionsAndFullPackIdentityWhileCanonicalizingRangeAndPayoffOrder() {
        var spot = threeSeatSpot();
        var reversedRanges = new ArrayList<List<WeightedCombo>>();
        for (var range : spot.ranges()) {
            var reversed = new ArrayList<>(range);
            Collections.reverse(reversed);
            reversedRanges.add(reversed);
        }
        MultiwayCallSpot reordered =
                new MultiwayCallSpot(
                        spot.id(),
                        spot.seats(),
                        reversedRanges,
                        spot.committedBb(),
                        spot.stackBb(),
                        spot.deadMoneyBb());
        assertEquals(spot, reordered);
        assertEquals(spot.contentHash(), reordered.contentHash());
        assertEquals(spot, MultiwayPackJson.readSpot(MultiwayPackJson.writeSpot(spot)));
        assertNotEquals(
                spot.contentHash(),
                new MultiwayCallSpot(
                                spot.id(),
                                spot.seats(),
                                spot.ranges(),
                                spot.committedBb(),
                                spot.stackBb(),
                                spot.deadMoneyBb() + 1)
                        .contentHash());
        var pack = sampled();
        var reversed = new ArrayList<>(pack.payoffs());
        Collections.reverse(reversed);
        var copy =
                new MultiwaySolutionPack(
                        pack.schemaVersion(),
                        pack.solverVersion(),
                        pack.publicationStatus(),
                        pack.generatedAt(),
                        pack.spot(),
                        pack.spotHash(),
                        pack.payoffMethod(),
                        pack.payoffSeed(),
                        pack.solution(),
                        reversed,
                        pack.nashConvBb(),
                        pack.maxTerminalPayoffSEBb());
        assertEquals(MultiwayPackJson.contentHash(pack), MultiwayPackJson.contentHash(copy));
        assertEquals(64, MultiwayPackJson.contentHash(pack).length());
        var next =
                MultiwayPackBuilder.build(
                        spot,
                        51,
                        CfrSolver.Variant.CFR_PLUS,
                        new SeededMultiwayShowdownOracle(300, 42),
                        MultiwaySolutionPack.SEEDED_MONTE_CARLO,
                        42,
                        GENERATED_AT);
        assertEquals(pack.spotHash(), next.spotHash());
        assertNotEquals(MultiwayPackJson.contentHash(pack), MultiwayPackJson.contentHash(next));
    }

    @Test
    void rejectsMissingDuplicateExtraAndBlockedPayoffKeys() throws Exception {
        var pack = sampled();
        ObjectNode missing = tree(pack);
        ((ArrayNode) missing.get("payoffs")).remove(0);
        reject(missing);
        ObjectNode duplicate = tree(pack);
        ((ArrayNode) duplicate.get("payoffs")).add(duplicate.get("payoffs").get(0).deepCopy());
        reject(duplicate);
        ObjectNode extra = tree(pack);
        ObjectNode foreign = extra.get("payoffs").get(0).deepCopy();
        ((ArrayNode) foreign.get("dealtCombos")).set(0, JSON.getNodeFactory().textNode("2c 2d"));
        ((ArrayNode) extra.get("payoffs")).add(foreign);
        reject(extra);
        ObjectNode blocked = tree(pack);
        ObjectNode badDeal = blocked.get("payoffs").get(0).deepCopy();
        ((ArrayNode) badDeal.get("dealtCombos")).set(0, JSON.getNodeFactory().textNode("Ah As"));
        ((ArrayNode) badDeal.get("dealtCombos")).set(1, JSON.getNodeFactory().textNode("Ad As"));
        ((ArrayNode) blocked.get("payoffs")).add(badDeal);
        reject(blocked);
    }

    @Test
    void rejectsForgedMetricsStrategiesAndVersionMetadata() throws Exception {
        var pack = sampled();
        for (String field :
                List.of(
                        "schemaVersion",
                        "solverVersion",
                        "publicationStatus",
                        "spotHash",
                        "payoffMethod",
                        "generatedAt")) {
            ObjectNode tree = tree(pack);
            tree.put(field, "unknown");
            reject(tree);
        }
        for (String field : List.of("nashConvBb", "maxTerminalPayoffSEBb")) {
            ObjectNode tree = tree(pack);
            tree.put(field, tree.get(field).asDouble() + 1);
            reject(tree);
        }
        ObjectNode missingStrategy = tree(pack);
        ObjectNode strategies = (ObjectNode) missingStrategy.get("solution").get("strategy");
        strategies.remove(strategies.fieldNames().next());
        reject(missingStrategy);
        ObjectNode extraStrategy = tree(pack);
        ((ObjectNode) extraStrategy.get("solution").get("strategy"))
                .set("0:invented:", JSON.createObjectNode().put("c", 1).put("f", 0));
        reject(extraStrategy);
        ObjectNode wrongActions = tree(pack);
        ObjectNode strategy =
                (ObjectNode) wrongActions.get("solution").get("strategy").elements().next();
        strategy.remove("c");
        strategy.put("s", 0.5);
        reject(wrongActions);
        ObjectNode invalidProbabilities = tree(pack);
        ((ObjectNode) invalidProbabilities.get("solution").get("strategy").elements().next())
                .put("c", -0.2)
                .put("f", 1.2);
        reject(invalidProbabilities);
    }

    @Test
    void rejectsImpossibleEstimatesAndFalseExactMetadata() throws Exception {
        var sampled = sampled();
        for (String field : List.of("trials", "activeMask")) {
            ObjectNode tree = tree(sampled);
            if (field.equals("trials"))
                ((ObjectNode) tree.get("payoffs").get(0).get("estimate")).put(field, 0);
            else ((ObjectNode) tree.get("payoffs").get(0)).put(field, 64);
            reject(tree);
        }
        ObjectNode badShares = tree(sampled);
        ((ArrayNode) badShares.get("payoffs").get(0).get("estimate").get("shares"))
                .set(0, JSON.getNodeFactory().numberNode(-1));
        reject(badShares);
        ObjectNode badError = tree(sampled);
        ((ArrayNode) badError.get("payoffs").get(0).get("estimate").get("standardErrors"))
                .set(0, JSON.getNodeFactory().numberNode(1));
        reject(badError);
        ObjectNode mixedTrials = tree(sampled);
        ((ObjectNode) mixedTrials.get("payoffs").get(0).get("estimate")).put("trials", 299);
        reject(mixedTrials);
        var exact =
                MultiwayPackBuilder.build(
                        sixSeatSpot(),
                        20,
                        CfrSolver.Variant.VANILLA,
                        MultiwaySolutionPackTest::exactFixture,
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        GENERATED_AT);
        ObjectNode badSeed = tree(exact);
        badSeed.put("payoffSeed", 1);
        reject(badSeed);
        ObjectNode badCount = tree(exact);
        ((ObjectNode) badCount.get("payoffs").get(0).get("estimate")).put("trials", 1_712_304);
        reject(badCount); // Six dealt hands leave 40 cards, even when only two seats are active.
        ObjectNode exactError = tree(exact);
        ((ArrayNode) exactError.get("payoffs").get(0).get("estimate").get("standardErrors"))
                .set(0, JSON.getNodeFactory().numberNode(0.0001));
        reject(exactError);
    }

    @Test
    void jsonRejectsUnknownDuplicateMissingNullCoercedAndTrailingFields() throws Exception {
        var pack = sampled();
        String json = MultiwayPackJson.write(pack);
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.read(json + " {}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.read("{\"payoffSeed\":42," + json.substring(1)));
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.read("null"));
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.readSpot("null"));
        ObjectNode unknown = tree(pack);
        unknown.put("untrusted", true);
        reject(unknown);
        ObjectNode missing = tree(pack);
        missing.remove("payoffSeed");
        reject(missing);
        ObjectNode nullPrimitive = tree(pack);
        nullPrimitive.putNull("payoffSeed");
        reject(nullPrimitive);
        ObjectNode coerced = tree(pack);
        coerced.put("payoffSeed", "42");
        reject(coerced);
        ObjectNode truncatedInteger = tree(pack);
        truncatedInteger.put("payoffSeed", 42.5);
        reject(truncatedInteger);
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readSpot("{\"id\":\"missing-inputs\"}"));
    }

    @Test
    void validatesSpotAssumptionsAndCopiesNestedCollections() {
        var spot = threeSeatSpot();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayCallSpot(
                                "Bad ID", spot.seats(), spot.ranges(), spot.committedBb(), 10, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayCallSpot(
                                spot.id(),
                                spot.seats(),
                                spot.ranges(),
                                List.of(9.0, 0.5, 1.0),
                                10,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayCallSpot(
                                spot.id(),
                                spot.seats(),
                                spot.ranges(),
                                spot.committedBb(),
                                10,
                                -1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MultiwayCallSpot(
                                spot.id(),
                                spot.seats(),
                                List.of(
                                        List.of(combo("As", "Ah")),
                                        List.of(combo("As", "Ad")),
                                        List.of(combo("Js", "Jh"))),
                                spot.committedBb(),
                                10,
                                0));
        var ranges = new ArrayList<>(spot.ranges());
        var first = new ArrayList<>(ranges.get(0));
        ranges.set(0, first);
        var copied =
                new MultiwayCallSpot(spot.id(), spot.seats(), ranges, spot.committedBb(), 10, 0);
        first.clear();
        ranges.clear();
        assertEquals(2, copied.ranges().get(0).size());
        assertThrows(UnsupportedOperationException.class, () -> copied.ranges().get(0).clear());
    }

    private static MultiwaySolutionPack sampled() {
        return MultiwayPackBuilder.build(
                threeSeatSpot(),
                50,
                CfrSolver.Variant.CFR_PLUS,
                new SeededMultiwayShowdownOracle(300, 42),
                MultiwaySolutionPack.SEEDED_MONTE_CARLO,
                42,
                GENERATED_AT);
    }

    private static MultiwayCallSpot threeSeatSpot() {
        return new MultiwayCallSpot(
                "three-seat-fixture",
                List.of(
                        PreflopAllInSpot.Seat.BTN,
                        PreflopAllInSpot.Seat.SB,
                        PreflopAllInSpot.Seat.BB),
                List.of(
                        List.of(combo("As", "Ah"), combo("Ks", "Kh")),
                        List.of(combo("As", "Ad"), combo("Qc", "Qd")),
                        List.of(combo("Js", "Jh"))),
                List.of(10.0, 0.5, 1.0),
                10,
                0);
    }

    private static MultiwayCallSpot sixSeatSpot() {
        return new MultiwayCallSpot(
                "six-seat-fixture",
                List.of(PreflopAllInSpot.Seat.values()),
                List.of(
                        List.of(combo("As", "Ah")),
                        List.of(combo("Ks", "Kh")),
                        List.of(combo("Qs", "Qh")),
                        List.of(combo("Js", "Jh")),
                        List.of(combo("Ts", "Th")),
                        List.of(combo("9s", "9h"))),
                List.of(10.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                10,
                0);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static MultiwayShowdownEstimate exactFixture(List<WeightedCombo> dealt, int mask) {
        double[] shares = new double[dealt.size()];
        for (int seat = 0; seat < shares.length; seat++)
            if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / Integer.bitCount(mask);
        long trials = 1;
        for (int index = 1; index <= 5; index++)
            trials = trials * (52 - 2 * dealt.size() - index + 1) / index;
        return new MultiwayShowdownEstimate(shares, new double[dealt.size()], trials);
    }

    private static ObjectNode tree(MultiwaySolutionPack pack) throws Exception {
        return (ObjectNode) JSON.readTree(MultiwayPackJson.write(pack));
    }

    private static void reject(ObjectNode tree) {
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.read(tree.toString()));
    }
}
