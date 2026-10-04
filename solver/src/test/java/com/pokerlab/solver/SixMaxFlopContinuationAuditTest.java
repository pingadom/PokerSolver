package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxFlopContinuationAuditTest {
    private static SixMaxPreflopSolutionPack pack() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("src/test/resources/six-seat-full-round-pack.json")));
    }

    @Test
    void solvesRealPolicyFlopsWithConditionalQualityAndConservedActualSixSeatValues()
            throws Exception {
        var pack = pack();
        var report =
                SixMaxFlopContinuationAudit.assess(
                        pack.rebuildGame(), pack.solution(), 711, 3, 2_000, 0.5);
        assertEquals("EXACT_RANGE_PRODUCT", report.chanceModel());
        assertEquals("ONE_FLOP_BET_THEN_MANDATORY_CHECKDOWN", report.continuationModel());
        assertEquals(3, report.examples().size());
        assertTrue(report.maximumConditionalBestResponseGapBb() < 0.005);
        for (var example : report.examples()) {
            assertEquals(0.5 * example.potBb(), example.betBb(), 1e-12);
            assertTrue(example.betBb() <= example.remainingStackBb());
            assertEquals(example.posteriorJointDeals() * 666L, example.exactRunouts());
            assertEquals(
                    0,
                    example.solvedUtilitiesBb().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-10);
            assertEquals(
                    0,
                    example.changeFromCheckdownBb().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-10);
            assertFalse(example.firstPlayerDecisions().isEmpty());
            for (var decision : example.firstPlayerDecisions()) {
                assertEquals(example.firstToAct(), decision.actingSeat());
                assertEquals(List.of(), decision.history());
                assertEquals(
                        1,
                        decision.actionFrequency().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
                assertEquals(2, decision.actionEvBb().size());
            }
            var handoff =
                    new SixMaxPolicyFlopTransition(
                            pack.rebuildGame(), pack.solution(), example.preflopHistory());
            var game =
                    new SixMaxHeadsUpFlopGame(
                            handoff.conditionOnFlop(
                                    example.board().stream()
                                            .map(com.pokerlab.core.card.Card::parse)
                                            .toList()),
                            example.betBb());
            assertEquals(
                    example.bestResponse(), HeadsUpBestResponse.assess(game, example.solution()));
            assertEquals(example.solvedUtilitiesBb(), game.profileUtilitiesBb(example.solution()));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopContinuationAudit.assess(
                                pack.rebuildGame(), pack.solution(), 711, 1, 0, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopContinuationAudit.assess(
                                pack.rebuildGame(), pack.solution(), 711, 1, 100_001, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopContinuationAudit.assess(
                                pack.rebuildGame(), pack.solution(), 711, 1, 100, Double.NaN));
    }

    @Test
    void zeroReachedFlopsCannotBeReportedAsAZeroGapSolve() throws Exception {
        var pack = pack();
        java.util.Map<String, java.util.Map<String, Double>> foldOnly =
                new java.util.LinkedHashMap<>();
        pack.solution()
                .strategy()
                .forEach(
                        (key, original) -> {
                            var weights = new java.util.LinkedHashMap<String, Double>();
                            String selected =
                                    original.containsKey("fold")
                                            ? "fold"
                                            : original.keySet().stream()
                                                    .sorted()
                                                    .findFirst()
                                                    .orElseThrow();
                            original.keySet()
                                    .forEach(
                                            action ->
                                                    weights.put(
                                                            action,
                                                            action.equals(selected) ? 1.0 : 0.0));
                            foldOnly.put(key, weights);
                        });
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopContinuationAudit.assess(
                                pack.rebuildGame(),
                                new CfrSolution(1, foldOnly),
                                711,
                                1,
                                100,
                                0.5));
    }

    @Test
    void cliExportsReproducibleSolvedStrategiesWithoutOverwritingTheSource(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("source.json");
        Files.copy(Path.of("src/test/resources/six-seat-full-round-pack.json"), source);
        String original = Files.readString(source);
        var output = directory.resolve("audit.json");
        String[] args = {source.toString(), output.toString(), "711", "500", "0.5"};
        SixMaxFlopContinuationAuditMain.main(args);
        var tree = new ObjectMapper().readTree(Files.readString(output));
        assertEquals(
                "six-max-conditional-flop-betting-audit/v1", tree.get("schemaVersion").asText());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(pack()), tree.get("sourcePackHash").asText());
        assertEquals("MANDATORY_CHECKDOWN", tree.get("sourceContinuationModel").asText());
        assertEquals("VALIDATION_ONLY", tree.get("publicationStatus").asText());
        assertEquals(10, tree.path("report").path("examples").size());
        assertFalse(
                tree.path("report")
                        .path("examples")
                        .get(0)
                        .path("solution")
                        .path("strategy")
                        .isEmpty());
        byte[] first = Files.readAllBytes(output);
        SixMaxFlopContinuationAuditMain.main(args);
        assertArrayEquals(first, Files.readAllBytes(output));
        assertEquals(original, Files.readString(source));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopContinuationAuditMain.main(
                                new String[] {
                                    source.toString(), source.toString(), "711", "500", "0.5"
                                }));
        assertEquals(original, Files.readString(source));
    }
}
