package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SixMaxPreflopSolutionPackTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENERATED_AT = "2026-10-03T12:00:00Z";

    private static SixMaxPreflopResearchSpot spot() {
        return new SixMaxPreflopResearchSpot(
                "pack-test",
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                SixMaxPreflopConvergenceMain.ranges("button-mix"),
                CashRakeRule.none(),
                SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
    }

    private static MultiwayShowdownEstimate syntheticExact(List<WeightedCombo> hands, int mask) {
        double[] shares = new double[6];
        shares[Integer.numberOfTrailingZeros(mask)] = 1;
        return new MultiwayShowdownEstimate(
                shares, new double[6], SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
    }

    private static SixMaxPreflopSolutionPack pack() {
        return SixMaxPreflopPackBuilder.build(
                spot(),
                4,
                CfrSolver.Variant.CFR_PLUS,
                SixMaxPreflopSolutionPackTest::syntheticExact,
                MultiwaySolutionPack.EXACT_ENUMERATION,
                0,
                GENERATED_AT);
    }

    @Test
    void reloadsAllSubsetsIncludingThoseWithoutUtgWithoutCallingOriginalOracle() {
        AtomicInteger calls = new AtomicInteger();
        var pack =
                SixMaxPreflopPackBuilder.build(
                        spot(),
                        4,
                        CfrSolver.Variant.CFR_PLUS,
                        (hands, mask) -> {
                            calls.incrementAndGet();
                            return syntheticExact(hands, mask);
                        },
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        GENERATED_AT);
        assertEquals(114, calls.get());
        assertEquals(114, pack.payoffs().size());
        assertTrue(pack.payoffs().stream().anyMatch(e -> e.activeMask() == 0b000110));
        String json = MultiwayPackJson.writeFullRound(pack);
        var loaded = MultiwayPackJson.readFullRound(json);
        assertEquals(json, MultiwayPackJson.writeFullRound(loaded));
        assertEquals(pack.spot(), loaded.spot());
        assertEquals(pack.solution(), loaded.solution());
        var rebuilt = loaded.rebuildGame();
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(pack.rebuildGame(), pack.solution()),
                MultiPlayerStrategyEvaluator.utilities(rebuilt, loaded.solution()),
                1e-12);
        assertEquals(
                loaded.nashConvBb(),
                MultiPlayerInformationSetBestResponse.assess(rebuilt, loaded.solution())
                        .nashConvBb(),
                1e-12);
        assertEquals(114, calls.get());
    }

    @Test
    void hashesRulesRakeRangesAndContinuationWhileCanonicalizingOrders() {
        var original = spot();
        var ranges = new ArrayList<>(original.ranges());
        var reversed = new ArrayList<>(ranges.get(3));
        Collections.reverse(reversed);
        ranges.set(3, reversed);
        var reordered =
                new SixMaxPreflopResearchSpot(
                        original.id(),
                        original.rules(),
                        ranges,
                        original.rake(),
                        original.continuationModel());
        assertEquals(original, reordered);
        assertEquals(original.contentHash(), reordered.contentHash());
        assertEquals(
                original,
                MultiwayPackJson.readFullRoundSpot(MultiwayPackJson.writeFullRoundSpot(original)));
        var raked =
                new SixMaxPreflopResearchSpot(
                        original.id(),
                        original.rules(),
                        ranges,
                        new CashRakeRule(0.05, 1, true),
                        original.continuationModel());
        assertNotEquals(original.contentHash(), raked.contentHash());
        var otherRules =
                new SixMaxPreflopResearchSpot(
                        original.id(),
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                        ranges,
                        original.rake(),
                        original.continuationModel());
        assertNotEquals(original.contentHash(), otherRules.contentHash());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopResearchSpot(
                                original.id(),
                                original.rules(),
                                ranges,
                                original.rake(),
                                "REAL_POSTFLOP_SOLVER"));
        var pack = pack();
        var reorderedPayoffs = new ArrayList<>(pack.payoffs());
        Collections.reverse(reorderedPayoffs);
        var copy =
                new SixMaxPreflopSolutionPack(
                        pack.schemaVersion(),
                        pack.solverVersion(),
                        pack.publicationStatus(),
                        pack.generatedAt(),
                        pack.spot(),
                        pack.spotHash(),
                        pack.payoffMethod(),
                        pack.payoffSeed(),
                        pack.solution(),
                        reorderedPayoffs,
                        pack.nashConvBb(),
                        pack.maxTerminalPayoffSEBb());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(pack),
                MultiwayPackJson.fullRoundContentHash(copy));
    }

    @Test
    void rejectsMissingDuplicateForeignPayoffsAndForgedStrategyOrMetrics() throws Exception {
        String json = MultiwayPackJson.writeFullRound(pack());
        ObjectNode missing = tree(json);
        ((ArrayNode) missing.get("payoffs")).remove(0);
        reject(missing);
        ObjectNode duplicate = tree(json);
        ((ArrayNode) duplicate.get("payoffs")).add(duplicate.get("payoffs").get(0).deepCopy());
        reject(duplicate);
        ObjectNode foreign = tree(json);
        ((ArrayNode) foreign.get("payoffs").get(0).get("dealtCombos"))
                .set(0, JSON.getNodeFactory().textNode("2c 2d"));
        reject(foreign);
        for (String field :
                List.of(
                        "schemaVersion",
                        "solverVersion",
                        "publicationStatus",
                        "generatedAt",
                        "spotHash",
                        "payoffMethod")) {
            ObjectNode altered = tree(json);
            altered.put(field, "unknown");
            reject(altered);
        }
        for (String field : List.of("nashConvBb", "maxTerminalPayoffSEBb")) {
            ObjectNode altered = tree(json);
            altered.put(field, altered.get(field).asDouble() + 1);
            reject(altered);
        }
        ObjectNode missingStrategy = tree(json);
        ObjectNode strategies = (ObjectNode) missingStrategy.get("solution").get("strategy");
        strategies.remove(strategies.fieldNames().next());
        reject(missingStrategy);
        ObjectNode wrongAction = tree(json);
        ((ObjectNode) wrongAction.get("solution").get("strategy").elements().next())
                .put("teleport", 0);
        reject(wrongAction);
        ObjectNode probabilities = tree(json);
        ((ObjectNode) probabilities.get("solution").get("strategy").elements().next())
                .put("fold", -1);
        reject(probabilities);
    }

    @Test
    void preservesSampledProvenanceAndRejectsFalseExactTrialAndErrorMetadata() throws Exception {
        var sampled =
                SixMaxPreflopPackBuilder.build(
                        spot(),
                        4,
                        CfrSolver.Variant.CFR_PLUS,
                        new SharedBoardMultiwayShowdownOracle(100, 711),
                        SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO,
                        711,
                        GENERATED_AT);
        assertTrue(sampled.maxTerminalPayoffSEBb() > 0);
        String json = MultiwayPackJson.writeFullRound(sampled);
        assertEquals(json, MultiwayPackJson.writeFullRound(MultiwayPackJson.readFullRound(json)));
        ObjectNode mixedTrials = tree(json);
        ((ObjectNode) mixedTrials.get("payoffs").get(0).get("estimate")).put("trials", 99);
        reject(mixedTrials);
        ObjectNode invalidMask = tree(json);
        ((ObjectNode) invalidMask.get("payoffs").get(0)).put("activeMask", 1);
        reject(invalidMask);
        ObjectNode badCount = tree(MultiwayPackJson.writeFullRound(pack()));
        ((ObjectNode) badCount.get("payoffs").get(0).get("estimate")).put("trials", 1);
        reject(badCount);
        ObjectNode badError = tree(MultiwayPackJson.writeFullRound(pack()));
        ((ArrayNode) badError.get("payoffs").get(0).get("estimate").get("standardErrors"))
                .set(0, JSON.getNodeFactory().numberNode(0.1));
        reject(badError);
    }

    @Test
    void strictJsonRejectsCoercionDuplicateFieldsUnknownPropertiesAndTrailingTokens()
            throws Exception {
        String json = MultiwayPackJson.writeFullRound(pack());
        assertThrows(
                IllegalArgumentException.class, () -> MultiwayPackJson.readFullRound(json + " {}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readFullRound("{\"payoffSeed\":0," + json.substring(1)));
        assertThrows(IllegalArgumentException.class, () -> MultiwayPackJson.readFullRound("null"));
        ObjectNode unknown = tree(json);
        unknown.put("unexpected", true);
        reject(unknown);
        ObjectNode coercion = tree(json);
        coercion.put("payoffSeed", "0");
        reject(coercion);
        ObjectNode missing = tree(json);
        missing.remove("payoffSeed");
        reject(missing);
    }

    private static ObjectNode tree(String json) throws Exception {
        return (ObjectNode) JSON.readTree(json);
    }

    private static void reject(ObjectNode node) {
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readFullRound(node.toString()));
    }
}
