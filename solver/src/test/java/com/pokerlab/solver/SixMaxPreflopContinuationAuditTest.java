package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxPreflopContinuationAuditTest {
    private static SixMaxPreflopSolutionPack pack() throws Exception {
        return MultiwayPackJson.readFullRound(
                Files.readString(Path.of("src/test/resources/six-seat-full-round-pack.json")));
    }

    @Test
    void auditsTheRealSavedPolicyAndRecomputesEveryExampleFromItsPosterior() throws Exception {
        var pack = pack();
        var game = pack.rebuildGame();
        var report = SixMaxPreflopContinuationAudit.assess(game, pack.solution(), 711, 3);
        assertEquals(
                1,
                report.terminalProbability().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-10);
        assertTrue(report.headsUpContinuationProbability() > 0);
        assertTrue(report.reachedHeadsUpHistories() >= 3);
        assertEquals(3, report.examples().size());
        assertEquals(9_880, report.flopsPerSixHandDeal());
        assertEquals("EXACT_RANGE_PRODUCT", report.chanceModel());
        assertEquals(report, SixMaxPreflopContinuationAudit.assess(game, pack.solution(), 711, 3));
        for (var example : report.examples()) {
            var transition =
                    new SixMaxPolicyFlopTransition(game, pack.solution(), example.history());
            assertEquals(transition.reachProbability(), example.reachProbability(), 1e-10);
            assertTrue(example.remainingStackBb() == 97 || example.remainingStackBb() == 99);
            assertEquals(
                    example.flopPosteriorJointDeals() * 666L,
                    example.exactFlopCheckdown().runouts());
            assertEquals(
                    0,
                    example.exactFlopCheckdown().utilitiesBb().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-10);
            assertEquals(6, example.exactFlopCheckdown().utilitiesBb().size());
            assertTrue(example.exactFlopCheckdown().shares().containsKey(example.firstToAct()));
            assertTrue(example.exactFlopCheckdown().shares().containsKey(example.secondToAct()));
            assertNotEquals(example.firstToAct(), example.secondToAct());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopContinuationAudit.assess(game, pack.solution(), 711, 21));
    }

    @Test
    void cliBindsTheReportToTheFullSourceArtifactAndCannotOverwriteIt(@TempDir Path directory)
            throws Exception {
        var source = directory.resolve("source.json");
        Files.copy(Path.of("src/test/resources/six-seat-full-round-pack.json"), source);
        var output = directory.resolve("audit.json");
        SixMaxPolicyFlopAuditMain.main(new String[] {source.toString(), output.toString(), "711"});
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(Files.readString(output));
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(pack()), json.get("sourcePackHash").asText());
        assertEquals("MANDATORY_CHECKDOWN", json.get("sourceContinuationModel").asText());
        assertEquals("VALIDATION_ONLY", json.get("publicationStatus").asText());
        assertEquals(10, json.get("report").get("examples").size());
        assertTrue(json.get("interpretation").asText().contains("not solved postflop"));
        String firstReport = Files.readString(output);
        SixMaxPolicyFlopAuditMain.main(new String[] {source.toString(), output.toString(), "711"});
        assertEquals(firstReport, Files.readString(output));
        String unchanged = Files.readString(source);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPolicyFlopAuditMain.main(
                                new String[] {source.toString(), source.toString(), "711"}));
        assertEquals(unchanged, Files.readString(source));
    }
}
