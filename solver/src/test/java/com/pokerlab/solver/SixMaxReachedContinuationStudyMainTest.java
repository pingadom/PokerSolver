package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxReachedContinuationStudyMainTest {
    @Test
    void exportsAReproducibleSourceBoundStudyAndKeepsQualityFlagsSeparate(@TempDir Path temp)
            throws Exception {
        var source = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var original = Files.readAllBytes(source);
        var output = temp.resolve("study.json");
        String[] args = {
            source.toString(), output.toString(), "711", "2", "1,2", "1", "711", "0.00000001"
        };
        SixMaxReachedContinuationStudyMain.main(args);
        var mapper = new ObjectMapper();
        var artifact =
                mapper.readValue(
                        output.toFile(), SixMaxReachedContinuationStudyMain.Artifact.class);
        var pack = MultiwayPackJson.readFullRound(Files.readString(source));
        assertEquals("six-max-reached-continuation-study/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(pack), artifact.sourcePackHash());
        assertEquals(pack.spotHash(), artifact.sourceSpotHash());
        assertEquals(pack.nashConvBb(), artifact.sourceCheckdownNashConvBb());
        assertEquals(1, artifact.selectedHistories().size());
        var run = artifact.runs().getFirst();
        assertEquals(java.util.List.of(1, 2), artifact.refinementBudgets());
        assertEquals(2, run.refinementAttempts().size());
        assertNull(run.firstBudgetMeetingBothChecks());
        var attempt = run.refinementAttempts().getLast();
        assertTrue(attempt.everySelectedBranchRefined());
        assertFalse(attempt.conditionalGapWithinTarget());
        assertTrue(attempt.maximumConditionalGapAfterBb() > artifact.conditionalGapTargetBb());
        assertEquals(
                run.refinementAttempts().getFirst().refinement().originalSolutionHash(),
                attempt.refinement().originalSolutionHash());
        assertEquals(
                run.refinementAttempts().getFirst().refinement().originalQuality(),
                attempt.refinement().originalQuality());
        assertEquals(
                attempt.refinement().bettingContinuationProbability(),
                run.jointPolicyReach().selectedPhysicalFlopProbability(),
                1e-15);
        assertEquals(
                attempt.refinement().nashConvChangeBb() <= artifact.parentNashConvToleranceBb(),
                attempt.parentNashConvDidNotIncrease());
        assertEquals(
                attempt.refinement().inputInformationSets(),
                run.visitedInformationSets() + run.uniformlyCompletedInformationSets());
        args[1] = temp.resolve("repeat.json").toString();
        SixMaxReachedContinuationStudyMain.main(args);
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(Path.of(args[1])));
        assertArrayEquals(original, Files.readAllBytes(source));
        args[1] = source.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReachedContinuationStudyMain.main(args));
        args[1] = output.toString();
        for (String invalid : new String[] {"711,711", "711,712,713,714"}) {
            args[2] = invalid;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxReachedContinuationStudyMain.main(args));
        }
        args[2] = "711";
        for (String invalid : new String[] {"2,1", "2,2", "0", "501", "1,2,3,4"}) {
            args[4] = invalid;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxReachedContinuationStudyMain.main(args));
        }
        args[4] = "1,2";
        for (String invalid : new String[] {"NaN", "Infinity", "0", "-1"}) {
            args[7] = invalid;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxReachedContinuationStudyMain.main(args));
        }
    }

    @Test
    void generatorRejectsSourceOverwriteBeforeAnExpensiveSolve(@TempDir Path temp)
            throws Exception {
        var source = temp.resolve("spot.json");
        Files.copy(Path.of("../docs/data/sixmax-diverse-spot.json"), source);
        var original = Files.readAllBytes(source);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GenerateSixMaxPreflopPack.main(
                                new String[] {
                                    "exact",
                                    source.toString(),
                                    source.toString(),
                                    "500",
                                    "2026-10-04T12:00:00Z"
                                }));
        assertArrayEquals(original, Files.readAllBytes(source));
    }
}
