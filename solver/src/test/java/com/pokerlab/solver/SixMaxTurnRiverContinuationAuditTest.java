package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxTurnRiverContinuationAuditTest {
    private static SixMaxPreflopSolutionPack pack() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("src/test/resources/six-seat-full-round-pack.json")));
    }

    @Test
    void committedLargerAuditRebuildsWithItsPublishedBoundsAndPolicies() throws Exception {
        var artifact =
                new ObjectMapper()
                        .readValue(
                                Path.of("../docs/data/sixmax-conditional-turn-river-betting.json")
                                        .toFile(),
                                SixMaxTurnRiverContinuationAuditMain.Artifact.class);
        var pack = pack();
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(5000, artifact.report().turnRiverIterations());
        assertEquals(5, artifact.report().examples().size());
        double maximum = 0;
        int informationSets = 0;
        for (var example : artifact.report().examples()) {
            var preflop =
                    new SixMaxPolicyFlopTransition(
                            pack.rebuildGame(), pack.solution(), example.preflopHistory());
            var flop =
                    new SixMaxHeadsUpFlopGame(
                            preflop.conditionOnFlop(
                                    example.board().subList(0, 3).stream()
                                            .map(Card::parse)
                                            .toList()),
                            example.flopBetBb());
            var transition =
                    new SixMaxPolicyTurnTransition(
                            flop, example.sourceFlopSolution(), example.flopHistory());
            var game =
                    new SixMaxHeadsUpTurnRiverGame(
                            transition.conditionOnTurn(Card.parse(example.board().get(3))),
                            example.turnBetBb(),
                            example.requestedRiverBetBb());
            var quality = HeadsUpBestResponse.assess(game, example.solution());
            assertEquals(example.bestResponse().gap(), quality.gap(), 1e-12);
            maximum = Math.max(maximum, quality.gap());
            informationSets += example.solution().strategy().size();
            var expectedCoverage = SixMaxHeadsUpTurnRiverGameTest.policy(game, (s, a) -> 0.5);
            assertEquals(
                    expectedCoverage.strategy().keySet(), example.solution().strategy().keySet());
            for (Seat seat : Seat.values())
                assertEquals(
                        example.solvedUtilitiesBb().get(seat),
                        game.profileUtilitiesBb(example.solution()).get(seat),
                        1e-12);
            var evaluator = new SixMaxHeadsUpTurnRiverDecisionEvaluator(game, example.solution());
            for (var decision : example.firstPlayerDecisions()) {
                var rebuilt =
                        evaluator.evaluate(
                                java.util.List.of(),
                                null,
                                java.util.List.of(),
                                decision.heroCombo());
                assertEquals(decision.actionFrequency(), rebuilt.actionFrequency());
                for (String action : decision.actionEvBb().keySet())
                    assertEquals(
                            decision.actionEvBb().get(action),
                            rebuilt.actionEvBb().get(action),
                            1e-12);
            }
        }
        assertEquals(2434, informationSets);
        assertEquals(artifact.report().maximumConditionalBestResponseGapBb(), maximum, 1e-12);
        assertTrue(maximum < 0.00003);
    }

    @Test
    void savedPolicyRebuildsTheReportedPosteriorPolicyAndExactQuality() throws Exception {
        var pack = pack();
        var source = pack.rebuildGame();
        var report =
                SixMaxTurnRiverContinuationAudit.assess(
                        source, pack.solution(), 711, 2, 500, 1000, 0.5);
        assertEquals("EXACT_RANGE_PRODUCT", report.chanceModel());
        assertEquals("ONE_TURN_BET_ONE_RIVER_BET_EXACT_RIVER", report.continuationModel());
        assertEquals(2, report.examples().size());
        assertTrue(
                report.maximumConditionalBestResponseGapBb() < 0.05,
                "gap=" + report.maximumConditionalBestResponseGapBb());
        for (var example : report.examples()) {
            assertTrue(example.flopHistoryProbability() > 0);
            assertTrue(example.turnProbability() > 0 && example.turnProbability() <= 1.0 / 37);
            assertEquals(example.posteriorJointDeals() * 36L, example.exactRivers());
            assertTrue(example.informationSets() > 100);
            var handoff =
                    new SixMaxPolicyFlopTransition(
                            source, pack.solution(), example.preflopHistory());
            var flop =
                    new SixMaxHeadsUpFlopGame(
                            handoff.conditionOnFlop(
                                    example.board().subList(0, 3).stream()
                                            .map(Card::parse)
                                            .toList()),
                            example.flopBetBb());
            var transition =
                    new SixMaxPolicyTurnTransition(
                            flop, example.sourceFlopSolution(), example.flopHistory());
            var game =
                    new SixMaxHeadsUpTurnRiverGame(
                            transition.conditionOnTurn(Card.parse(example.board().get(3))),
                            example.turnBetBb(),
                            example.requestedRiverBetBb());
            assertEquals(
                    example.bestResponse(), HeadsUpBestResponse.assess(game, example.solution()));
            assertEquals(example.solvedUtilitiesBb(), game.profileUtilitiesBb(example.solution()));
            assertEquals(example.checkdownUtilitiesBb(), game.exactCheckdownUtilitiesBb());
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
            for (Seat seat : Seat.values())
                assertEquals(
                        example.solvedUtilitiesBb().get(seat)
                                - example.checkdownUtilitiesBb().get(seat),
                        example.changeFromCheckdownBb().get(seat));
            assertFalse(example.firstPlayerDecisions().isEmpty());
            for (var decision : example.firstPlayerDecisions()) {
                assertNull(decision.river());
                assertEquals(2, decision.actionEvBb().size());
                assertEquals(
                        1,
                        decision.actionFrequency().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
            }
        }
    }

    @Test
    void budgetsAndMissingContinuationCannotProduceMisleadingReports() throws Exception {
        var pack = pack();
        var game = pack.rebuildGame();
        for (int budget : new int[] {0, 100_001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxTurnRiverContinuationAudit.assess(
                                    game, pack.solution(), 711, 1, 100, budget, 0.5));
        for (int limit : new int[] {0, 11})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxTurnRiverContinuationAudit.assess(
                                    game, pack.solution(), 711, limit, 100, 100, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxTurnRiverContinuationAudit.assess(
                                game, pack.solution(), 711, 1, 0, 100, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxTurnRiverContinuationAudit.assess(
                                game, pack.solution(), 711, 1, 100, 100, Double.NaN));
    }

    @Test
    void cliExportsCompleteReproducibleStrategiesAndPreservesSource(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("source.json");
        Files.copy(Path.of("src/test/resources/six-seat-full-round-pack.json"), source);
        var before = Files.readAllBytes(source);
        var output = directory.resolve("audit.json");
        String[] args = {source.toString(), output.toString(), "711", "100", "100", "0.5"};
        SixMaxTurnRiverContinuationAuditMain.main(args);
        var tree = new ObjectMapper().readTree(output.toFile());
        assertEquals(
                "six-max-conditional-turn-river-betting-audit/v1",
                tree.path("schemaVersion").asText());
        assertEquals("VALIDATION_ONLY", tree.path("publicationStatus").asText());
        assertEquals("MANDATORY_CHECKDOWN", tree.path("sourceContinuationModel").asText());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(pack()),
                tree.path("sourcePackHash").asText());
        assertEquals(5, tree.path("report").path("examples").size());
        var example = tree.path("report").path("examples").get(0);
        assertFalse(example.path("sourceFlopSolution").path("strategy").isEmpty());
        assertTrue(example.path("solution").path("strategy").size() > 100);
        var first = Files.readAllBytes(output);
        SixMaxTurnRiverContinuationAuditMain.main(args);
        assertArrayEquals(first, Files.readAllBytes(output));
        assertArrayEquals(before, Files.readAllBytes(source));
        args[1] = source.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTurnRiverContinuationAuditMain.main(args));
        assertArrayEquals(before, Files.readAllBytes(source));
    }
}
