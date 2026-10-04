package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxConnectedPreflopAuditTest {
    @Test
    void realPackComparisonRecoversCheckdownAndMeasuresPhysicalCoverage(@TempDir Path temp)
            throws Exception {
        var input = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var output = temp.resolve("joint.json");
        var source = Files.readString(input);
        SixMaxConnectedPreflopAuditMain.main(
                new String[] {input.toString(), output.toString(), "711", "2", "0.5"});
        var artifact =
                new ObjectMapper()
                        .readValue(output.toFile(), SixMaxConnectedPreflopAuditMain.Artifact.class);
        assertEquals("six-max-connected-preflop-audit/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(MultiwayPackJson.readFullRound(source)),
                artifact.sourcePackHash());
        var report = artifact.report();
        assertEquals(2, report.sameBudgetCheckdown().iterations());
        assertEquals(2, report.jointlySolved().iterations());
        assertEquals(1, report.coverage().size());
        assertEquals(List.of(1, 1), report.coverage().getFirst().legalSelectedFlopsByDeal());
        for (double error : report.forcedCheckdownRecoveryErrorBb()) assertEquals(0, error, 1e-9);
        for (var solve : List.of(report.sameBudgetCheckdown(), report.jointlySolved())) {
            assertEquals(6, solve.quality().deviationGainsBb().size());
            assertEquals(
                    0,
                    solve.quality().profileUtilitiesBb().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-9);
            assertEquals(64, solve.solutionHash().length());
            assertTrue(solve.bettingContinuationProbability() > 0);
            assertTrue(solve.bettingContinuationProbability() < 1.0 / 9880);
        }
        assertTrue(
                report.jointlySolved().informationSets()
                        > report.sameBudgetCheckdown().informationSets());
        assertEquals(1, report.jointlySolvedConditionalPostflop().size());
        var conditional = report.jointlySolvedConditionalPostflop().getFirst();
        assertEquals(2, conditional.posteriorJointDeals());
        assertEquals(1.0 / 9880, conditional.flopProbabilityGivenHistory(), 1e-15);
        assertEquals(
                report.jointlySolved().bettingContinuationProbability(),
                conditional.preflopReachProbability() * conditional.flopProbabilityGivenHistory(),
                1e-15);
        assertTrue(conditional.bestResponse().gap() >= 0);
        assertFalse(conditional.firstPlayerDecisions().isEmpty());
        assertTrue(
                report.checkdownProfileInConnectedGame().nashConvBb()
                        >= report.sameBudgetCheckdown().quality().nashConvBb() - 1e-9);
        assertEquals(source, Files.readString(input));
        var repeated = temp.resolve("repeat.json");
        SixMaxConnectedPreflopAuditMain.main(
                new String[] {input.toString(), repeated.toString(), "711", "2", "0.5"});
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(repeated));
    }

    @Test
    void rejectsInvalidBudgetsFractionsAndSourceOverwrite() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var source = new MultiPlayerCfrSolver<>(base, CfrSolver.Variant.CFR_PLUS).solve(1);
        for (int budget : List.of(0, 501))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxConnectedPreflopAudit.assess(base, source, 711, budget, 0.5));
        for (double fraction : new double[] {0, Double.NaN, 2.1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxConnectedPreflopAudit.assess(base, source, 711, 1, fraction));
        var input = "src/test/resources/six-seat-full-round-pack.json";
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPreflopAuditMain.main(
                                new String[] {input, input, "711", "1", "0.5"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPreflopAuditMain.main(new String[] {}));
    }
}
