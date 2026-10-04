package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxConditionalRefinementAuditTest {
    @Test
    void exportBindsCoverageAndSourceAndRepeatsWithoutOverwritingEither(@TempDir Path temp)
            throws Exception {
        var mapper = new ObjectMapper();
        var committed =
                mapper.readValue(
                        Path.of("../docs/data/sixmax-linear-runout-coverage.json").toFile(),
                        SixMaxFlopCoverageAuditMain.Artifact.class);
        var report = committed.report();
        var narrow =
                new SixMaxFlopCoverageAuditMain.Artifact(
                        committed.schemaVersion(),
                        committed.sourcePackHash(),
                        committed.sourceSpotHash(),
                        committed.publicationStatus(),
                        committed.interpretation(),
                        new SixMaxFlopCoverageAudit.Report(
                                report.continuationModel(),
                                report.algorithm(),
                                report.chanceTraversal(),
                                report.flopSelectionSeed(),
                                List.of(report.cases().getFirst())));
        var coverage = temp.resolve("coverage.json");
        mapper.writeValue(coverage.toFile(), narrow);
        var coverageBytes = Files.readAllBytes(coverage);
        var input = Path.of("src/test/resources/six-seat-full-round-pack.json");
        var inputBytes = Files.readAllBytes(input);
        var output = temp.resolve("refinement.json");
        String[] args = {input.toString(), coverage.toString(), output.toString(), "711", "2", "2"};
        SixMaxConditionalRefinementAuditMain.main(args);
        var artifact =
                mapper.readValue(
                        output.toFile(), SixMaxConditionalRefinementAuditMain.Artifact.class);
        assertEquals("six-max-conditional-refinement-audit/v1", artifact.schemaVersion());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(committed.sourcePackHash(), artifact.sourcePackHash());
        assertEquals(narrow.report().cases().getFirst().coverage(), artifact.coverage());
        assertEquals(64, artifact.coverageArtifactHash().length());
        assertEquals(1, artifact.runs().size());
        var run = artifact.runs().getFirst();
        assertEquals("LINEAR_CFR", run.jointAlgorithm());
        assertEquals("SAMPLED_RUNOUTS", run.jointChanceTraversal());
        assertEquals(2, run.refinement().refinementIterations());
        assertEquals(1, run.refinement().branches().size());
        assertEquals(
                run.refinement().inputInformationSets(),
                run.refinement().replacedInformationSets()
                        + run.refinement().preservedInformationSets());
        assertEquals(6, run.refinement().candidateQuality().deviationGainsBb().size());
        assertNotEquals(
                run.refinement().originalSolutionHash(), run.refinement().candidateSolutionHash());
        args[2] = temp.resolve("repeat.json").toString();
        SixMaxConditionalRefinementAuditMain.main(args);
        assertArrayEquals(Files.readAllBytes(output), Files.readAllBytes(Path.of(args[2])));
        assertArrayEquals(inputBytes, Files.readAllBytes(input));
        assertArrayEquals(coverageBytes, Files.readAllBytes(coverage));
        args[2] = coverage.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalRefinementAuditMain.main(args));
        args[2] = input.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalRefinementAuditMain.main(args));
        args[2] = output.toString();
        args[3] = "711,711";
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalRefinementAuditMain.main(args));
        args[3] = "711";
        var wrongSource =
                new SixMaxFlopCoverageAuditMain.Artifact(
                        narrow.schemaVersion(),
                        "0".repeat(64),
                        narrow.sourceSpotHash(),
                        narrow.publicationStatus(),
                        narrow.interpretation(),
                        narrow.report());
        mapper.writeValue(coverage.toFile(), wrongSource);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalRefinementAuditMain.main(args));
        var originalCase = narrow.report().cases().getFirst();
        var originalCoverage = originalCase.coverage().getFirst();
        var forgedCoverage =
                new SixMaxConnectedPreflopGame.Coverage(
                        originalCoverage.publicHistory(),
                        originalCoverage.actions(),
                        originalCoverage.flops(),
                        originalCoverage.secondToAct(),
                        originalCoverage.firstToAct(),
                        originalCoverage.flopBetBb(),
                        originalCoverage.requestedTurnBetBb(),
                        originalCoverage.requestedRiverBetBb(),
                        originalCoverage.legalSelectedFlopsByDeal(),
                        originalCoverage.bettingFlopProbabilityByDeal());
        var forgedCase =
                new SixMaxFlopCoverageAudit.Case(
                        originalCase.selectedFlops(),
                        originalCase.connectedDealFlops(),
                        List.of(forgedCoverage),
                        originalCase.sourceBettingContinuationProbability(),
                        originalCase.runs());
        var forged =
                new SixMaxFlopCoverageAuditMain.Artifact(
                        narrow.schemaVersion(),
                        narrow.sourcePackHash(),
                        narrow.sourceSpotHash(),
                        narrow.publicationStatus(),
                        narrow.interpretation(),
                        new SixMaxFlopCoverageAudit.Report(
                                report.continuationModel(),
                                report.algorithm(),
                                report.chanceTraversal(),
                                report.flopSelectionSeed(),
                                List.of(forgedCase)));
        mapper.writeValue(coverage.toFile(), forged);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConditionalRefinementAuditMain.main(args));
    }
}
