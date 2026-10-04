package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxFlopWidthStudyMainTest {
    @Test
    void exportsNestedCoverageAndIndependentQualityChecks(@TempDir Path temp) throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var original = Files.readAllBytes(source);
        var output = temp.resolve("widths.json");
        String[] args = {
            source.toString(), output.toString(), "711", "1", "1", "1", "711", "1,2", "0.00000001"
        };
        SixMaxFlopWidthStudyMain.main(args);
        var artifact =
                new ObjectMapper()
                        .readValue(output.toFile(), SixMaxFlopWidthStudyMain.Artifact.class);
        var pack = MultiwayPackJson.readFullRound(Files.readString(source));
        assertEquals("six-max-flop-width-study/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals("COMPLETED", artifact.executionStatus());
        assertEquals(List.of(711L), artifact.trainingSeeds());
        assertEquals(1, artifact.jointIterations());
        assertEquals(1, artifact.refinementIterations());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
        assertEquals(pack.spotHash(), artifact.sourceSpotHash());
        assertEquals(List.of(1, 2), artifact.requestedFlopsPerHistory());
        assertEquals(SixMaxContinuationStudyBudget.widerFlops(), artifact.budget());
        var narrow = artifact.widths().getFirst();
        var wide = artifact.widths().getLast();
        assertEquals(
                narrow.selectedHistories().getFirst().coverage().flops(),
                wide.selectedHistories().getFirst().coverage().flops().subList(0, 1));
        assertTrue(wide.cost().completeTreeStates() > narrow.cost().completeTreeStates());
        assertEquals(narrow.cost(), artifact.oneFlopReference().cost());
        assertEquals(narrow.sourcePolicyReach(), artifact.oneFlopReference().sourcePolicyReach());
        assertEquals(narrow.selectedHistories(), artifact.oneFlopReference().selectedHistories());
        assertEquals(1, narrow.sourcePhysicalBettingReachRatioToOneFlop());
        assertEquals(
                wide.sourcePolicyReach().selectedPhysicalFlopProbability()
                        / narrow.sourcePolicyReach().selectedPhysicalFlopProbability(),
                wide.sourcePhysicalBettingReachRatioToOneFlop(),
                1e-12);
        for (var width : artifact.widths()) {
            var run = width.runs().getFirst();
            var attempt = run.refinementAttempts().getFirst();
            var report = attempt.refinement();
            assertEquals(width.cost().completeTreeStates(), run.completeTreeStates());
            assertEquals(
                    run.visitedInformationSets() + run.uniformlyCompletedInformationSets(),
                    report.inputInformationSets());
            assertEquals(width.flopsPerHistory(), report.branches().size());
            assertTrue(attempt.everySelectedBranchRefined());
            assertFalse(attempt.conditionalGapWithinTarget());
            assertNull(run.firstBudgetMeetingBothChecks());
            assertEquals(
                    report.nashConvChangeBb() <= artifact.parentNashConvToleranceBb(),
                    attempt.parentNashConvDidNotIncrease());
            assertEquals(
                    report.bettingContinuationProbability(),
                    run.jointPolicyReach().selectedPhysicalFlopProbability(),
                    1e-15);
        }
        args[1] = temp.resolve("repeat.json").toString();
        SixMaxFlopWidthStudyMain.main(args);
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(Path.of(args[1])));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void canInspectCoverageAndExactCostWithoutRunningCfr(@TempDir Path temp) throws Exception {
        var source = Path.of("../docs/data/sixmax-diverse-source-pack.json");
        var output = temp.resolve("plan.json");
        SixMaxFlopWidthStudyMain.main(
                new String[] {
                    source.toString(),
                    output.toString(),
                    "711,712",
                    "500",
                    "300",
                    "2",
                    "711",
                    "1,2",
                    "0.05",
                    "--plan-only"
                });
        var artifact =
                new ObjectMapper()
                        .readValue(output.toFile(), SixMaxFlopWidthStudyMain.Artifact.class);
        assertEquals("PLANNED", artifact.executionStatus());
        assertEquals(List.of(711L, 712L), artifact.trainingSeeds());
        assertEquals(500, artifact.jointIterations());
        assertEquals(300, artifact.refinementIterations());
        assertEquals(2, artifact.widths().size());
        for (var width : artifact.widths()) {
            assertTrue(width.runs().isEmpty());
            assertTrue(
                    width.cost().completeTreeStates()
                            <= artifact.budget().maximumCompleteTreeStates());
            assertTrue(
                    width.cost().compatibleDealFlops()
                            <= artifact.budget().maximumCompatibleDealFlops());
        }
        assertEquals(artifact.widths().getFirst().cost(), artifact.oneFlopReference().cost());
        assertTrue(artifact.widths().getLast().cost().compatibleDealFlops() > 8);
        assertTrue(artifact.widths().getLast().sourcePhysicalBettingReachRatioToOneFlop() > 1);
    }

    @Test
    void rejectsInvalidWidthsAndOversizedTreesWithoutTouchingFiles(@TempDir Path temp)
            throws Exception {
        var source = Path.of("../docs/data/sixmax-diverse-source-pack.json");
        var original = Files.readAllBytes(source);
        var output = temp.resolve("preserve.json");
        Files.writeString(output, "keep me");
        String[] args = {
            source.toString(), output.toString(), "711", "1", "1", "2", "711", "1,2", "0.05"
        };
        for (String widths : List.of("0", "5", "2,1", "2,2", "1,2,3,4", "1,3")) {
            args[7] = widths;
            assertThrows(IllegalArgumentException.class, () -> SixMaxFlopWidthStudyMain.main(args));
            assertEquals("keep me", Files.readString(output));
        }
        args[7] = "1,2";
        args[1] = source.toString();
        assertThrows(IllegalArgumentException.class, () -> SixMaxFlopWidthStudyMain.main(args));
        assertArrayEquals(original, Files.readAllBytes(source));
    }
}
