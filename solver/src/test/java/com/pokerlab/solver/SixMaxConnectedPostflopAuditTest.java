package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokerlab.core.card.Card;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxConnectedPostflopAuditTest {
    @Test
    void eightDealChanceComparisonRetainsFoldedCardsAndExactCheckdownValues() throws Exception {
        var source =
                MultiwayPackJson.readFullRound(
                        Files.readString(
                                Path.of("../docs/data/sixmax-eight-deal-source-pack.json")));
        var report =
                SixMaxConnectedPostflopAudit.assess(
                        source.rebuildGame(), source.solution(), 711, 1, 1, 1, .5, List.of(.1, .9));
        var example = report.examples().getFirst();
        assertEquals(8, example.posteriorJointDeals());
        assertEquals(List.of("3c", "4h", "Ks"), example.flop());
        assertEquals(0, example.exact().signedCheckdownBiasBb(), 1e-10);
        assertEquals(
                8 * 108_900 + 1,
                example.exact().treeSize().chanceNodes()
                        + example.exact().treeSize().decisionNodes()
                        + example.exact().treeSize().terminalNodes());
        assertTrue(example.exact().informationSets() > example.restrictedTurn().informationSets());
        assertTrue(example.exact().bestResponse().gap() > .05);
    }

    private static SixMaxPreflopSolutionPack pack() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("src/test/resources/six-seat-full-round-pack.json")));
    }

    @Test
    void exactAndRestrictedSolvesSeparateQualityFromChanceBias() throws Exception {
        var pack = pack();
        var report =
                SixMaxConnectedPostflopAudit.assess(
                        pack.rebuildGame(), pack.solution(), 711, 1, 2, 10, 0.5, List.of(0.1, 0.9));
        assertEquals("EXACT_RANGE_PRODUCT", report.privateChanceModel());
        assertEquals("ONE_BET_PER_STREET_CONNECTED_FLOP_TURN_RIVER", report.continuationModel());
        assertEquals(1, report.examples().size());
        var example = report.examples().getFirst();
        assertEquals("EXACT_PHYSICAL_TURN_RIVER", example.exact().publicChanceModel());
        assertEquals("QUANTILE_TURN_EXACT_RIVER", example.restrictedTurn().publicChanceModel());
        assertEquals(List.of(0.1, 0.9), example.restrictedTurn().turnQuantiles());
        assertTrue(example.exact().informationSets() > example.restrictedTurn().informationSets());
        assertTrue(
                example.exact().treeSize().terminalNodes()
                        > 10 * example.restrictedTurn().treeSize().terminalNodes());
        assertEquals(0, example.exact().signedCheckdownBiasBb(), 1e-10);
        assertEquals(
                Math.abs(example.restrictedTurn().signedCheckdownBiasBb()),
                report.maximumAbsoluteRestrictedCheckdownBiasBb());
        for (var solve : List.of(example.exact(), example.restrictedTurn())) {
            assertEquals(64, solve.solutionHash().length());
            assertTrue(solve.bestResponse().gap() > 0);
            assertEquals(
                    0,
                    solve.solvedUtilitiesBb().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-10);
            assertFalse(solve.firstPlayerDecisions().isEmpty());
            for (var decision : solve.firstPlayerDecisions()) {
                assertEquals("FLOP", decision.street());
                assertEquals(example.firstToAct(), decision.actingSeat());
                assertEquals(
                        1,
                        decision.actionFrequency().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
            }
        }
        var transition =
                new SixMaxPolicyFlopTransition(
                        pack.rebuildGame(), pack.solution(), example.preflopHistory());
        var exact =
                new SixMaxHeadsUpPostflopGame(
                        transition.conditionOnFlop(
                                example.flop().stream().map(Card::parse).toList()),
                        example.flopBetBb(),
                        example.requestedTurnBetBb(),
                        example.requestedRiverBetBb());
        var reproduced = new CfrSolver<>(exact, CfrSolver.Variant.CFR_PLUS).solve(2);
        assertEquals(
                example.exact().solutionHash(),
                SixMaxConnectedPostflopAudit.solutionHash(reproduced));
        assertEquals(example.exact().bestResponse(), HeadsUpBestResponse.assess(exact, reproduced));
    }

    @Test
    void hashesAreOrderIndependentAndChangeWithPolicyContent() {
        var first = new LinkedHashMap<String, Map<String, Double>>();
        first.put("0:z", Map.of("bet", 0.25, "check", 0.75));
        first.put("1:a", Map.of("call", 1.0, "fold", 0.0));
        var second = new LinkedHashMap<String, Map<String, Double>>();
        second.put("1:a", Map.of("fold", 0.0, "call", 1.0));
        second.put("0:z", Map.of("check", 0.75, "bet", 0.25));
        String hash = SixMaxConnectedPostflopAudit.solutionHash(new CfrSolution(2, first));
        assertEquals(hash, SixMaxConnectedPostflopAudit.solutionHash(new CfrSolution(2, second)));
        second.put("0:z", Map.of("check", 0.5, "bet", 0.5));
        assertNotEquals(
                hash, SixMaxConnectedPostflopAudit.solutionHash(new CfrSolution(2, second)));
    }

    @Test
    void budgetsAndQuantileInputsAreValidatedBeforeExpensiveWork() throws Exception {
        var pack = pack();
        var game = pack.rebuildGame();
        for (int budget : new int[] {0, 1001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxConnectedPostflopAudit.assess(
                                    game, pack.solution(), 711, 1, budget, 1, 0.5, List.of(0.5)));
        for (int budget : new int[] {0, 10001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxConnectedPostflopAudit.assess(
                                    game, pack.solution(), 711, 1, 1, budget, 0.5, List.of(0.5)));
        for (var quantiles :
                List.of(List.<Double>of(), List.of(-0.1), List.of(1.0), List.of(Double.NaN)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxConnectedPostflopAudit.assess(
                                    game, pack.solution(), 711, 1, 1, 1, 0.5, quantiles));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPostflopAudit.assess(
                                game, pack.solution(), 711, 4, 1, 1, 0.5, List.of(0.5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPostflopAudit.assess(
                                game, pack.solution(), 711, 1, 1, 1, Double.NaN, List.of(0.5)));
    }

    @Test
    void cliIsReproducibleSourceBoundAndCannotOverwriteItsInput(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("source.json");
        var output = directory.resolve("audit.json");
        Files.copy(Path.of("src/test/resources/six-seat-full-round-pack.json"), input);
        byte[] before = Files.readAllBytes(input);
        String[] args = {input.toString(), output.toString(), "711", "1", "2", "0.5", "0.1,0.9"};
        SixMaxConnectedPostflopAuditMain.main(args);
        var artifact =
                new ObjectMapper()
                        .readValue(
                                output.toFile(), SixMaxConnectedPostflopAuditMain.Artifact.class);
        assertEquals("six-max-connected-postflop-audit/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals("MANDATORY_CHECKDOWN", artifact.sourceContinuationModel());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack()), artifact.sourcePackHash());
        assertEquals(2, artifact.report().examples().size());
        byte[] first = Files.readAllBytes(output);
        SixMaxConnectedPostflopAuditMain.main(args);
        assertArrayEquals(first, Files.readAllBytes(output));
        assertArrayEquals(before, Files.readAllBytes(input));
        args[1] = input.toString();
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxConnectedPostflopAuditMain.main(args));
        assertArrayEquals(before, Files.readAllBytes(input));
    }
}
