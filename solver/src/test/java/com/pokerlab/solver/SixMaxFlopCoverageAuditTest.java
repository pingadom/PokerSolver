package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxFlopCoverageAuditTest {
    @Test
    void widerPhysicalGameReportsMissingPoliciesAndExactConditionalQuality(@TempDir Path temp)
            throws Exception {
        var input = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var source = Files.readString(input);
        var output = temp.resolve("coverage.json");
        String[] args = {input.toString(), output.toString(), "711", "2", "2", "0,0.5"};
        SixMaxFlopCoverageAuditMain.main(args);
        var artifact =
                new ObjectMapper()
                        .readValue(output.toFile(), SixMaxFlopCoverageAuditMain.Artifact.class);
        assertEquals("six-max-flop-coverage-audit/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(MultiwayPackJson.readFullRound(source)),
                artifact.sourcePackHash());
        assertEquals("SAMPLED_AFTER_ROOT", artifact.report().chanceTraversal());
        assertEquals(2, artifact.report().cases().size());
        var narrow = artifact.report().cases().getFirst();
        var wider = artifact.report().cases().getLast();
        assertEquals(1, narrow.selectedFlops());
        assertEquals(2, wider.selectedFlops());
        assertTrue(wider.connectedDealFlops() > narrow.connectedDealFlops());
        assertTrue(
                wider.sourceBettingContinuationProbability()
                        > narrow.sourceBettingContinuationProbability());
        for (var study : artifact.report().cases()) {
            assertEquals(study.selectedFlops() == 1 ? 2 : 3, study.runs().size());
            for (var run : study.runs()) {
                assertEquals(2, run.iterations());
                assertTrue(run.visitedInformationSets() >= 7089);
                assertTrue(run.uniformlyCompletedInformationSets() > 0);
                assertTrue(run.traversalReductionFactor() > 1);
                assertEquals(
                        run.completeTreeVisits() * 6 * 2, run.exhaustiveVisitsAtSameIterations());
                assertEquals(6, run.quality().deviationGainsBb().size());
                assertTrue(run.quality().deviationGainsBb().stream().allMatch(v -> v >= -1e-8));
                assertEquals(
                        0,
                        run.quality().profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-9);
                assertEquals(study.selectedFlops(), run.conditionalPostflop().size());
                assertEquals(64, run.sampledSolutionHash().length());
                assertEquals(64, run.completedSolutionHash().length());
                assertNotEquals(run.sampledSolutionHash(), run.completedSolutionHash());
                assertTrue(
                        run.conditionalPostflop().stream()
                                .allMatch(c -> c.bestResponse().gap() >= -1e-8));
            }
        }
        assertFalse(wider.runs().getLast().checkdownControlVariate());
        assertEquals(0, wider.runs().getLast().sampledTraversal().baselineCorrections());
        assertTrue(
                wider.runs().getLast().traversalReductionFactor()
                        > narrow.runs().getLast().traversalReductionFactor());
        var repeat = temp.resolve("repeat.json");
        args[1] = repeat.toString();
        SixMaxFlopCoverageAuditMain.main(args);
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(repeat));
        assertEquals(source, Files.readString(input));
    }

    @Test
    void rejectsInvalidOrExcessiveStudySettingsBeforeBuildingGame() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var source = new MultiPlayerCfrSolver<>(base, CfrSolver.Variant.CFR_PLUS).solve(1);
        for (var seeds : List.of(List.<Long>of(), List.of(1L, 1L), List.of(1L, 2L, 3L, 4L)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxFlopCoverageAudit.assess(
                                    base, source, 711, seeds, 1, 1, List.of(.5)));
        for (var mixtures :
                List.of(List.<Double>of(), List.of(.5, .5), List.of(Double.NaN), List.of(.96)))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxFlopCoverageAudit.assess(
                                    base, source, 711, List.of(1L), 1, 1, mixtures));
        for (int budget : List.of(0, 3001))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxFlopCoverageAudit.assess(
                                    base, source, 711, List.of(1L), budget, 1, List.of(.5)));
        for (int width : List.of(0, 5))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxFlopCoverageAudit.assess(
                                    base, source, 711, List.of(1L), 1, width, List.of(.5)));
        String input = "src/test/resources/six-seat-full-round-pack.json";
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxFlopCoverageAuditMain.main(
                                new String[] {input, input, "711", "1", "1", ".5"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxFlopCoverageAuditMain.main(new String[] {}));
    }
}
