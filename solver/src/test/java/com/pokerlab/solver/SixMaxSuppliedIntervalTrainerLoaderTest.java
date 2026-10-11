package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedIntervalTrainerLoaderTest {
    static final String PIN = "5fb55653ae5f53d0110cea8507be9a37a6af2fe5cc596f433aaab0838b6b0aa9";

    @Test
    void startupReplaysPinnedPhysicalEvidenceAndLeavesInputsUntouched() throws Exception {
        var request = SixMaxSuppliedIntervalFeedbackTest.data("request");
        var report = SixMaxSuppliedIntervalFeedbackTest.data("report");
        byte[] beforeRequest = Files.readAllBytes(request),
                beforeReport = Files.readAllBytes(report);
        var trainer = SixMaxSuppliedIntervalTrainerLoader.load(request, report, PIN);
        assertEquals(PIN, trainer.question().reportHash());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                trainer.grade(trainer.question(), "call").selected().classification());
        assertArrayEquals(beforeRequest, Files.readAllBytes(request));
        assertArrayEquals(beforeReport, Files.readAllBytes(report));
    }

    @Test
    void absentMalformedWrongPinsAndAliasesRejectBeforePhysicalReplay(@TempDir Path dir) {
        var missing = dir.resolve("missing");
        var request = SixMaxSuppliedIntervalFeedbackTest.data("request");
        var report = SixMaxSuppliedIntervalFeedbackTest.data("report");
        for (String pin : new String[] {null, "", "true", PIN.toUpperCase()})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxSuppliedIntervalTrainerLoader.load(missing, report, pin));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalTrainerLoader.load(request, request, PIN));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedIntervalTrainerLoader.load(missing, report, "0".repeat(64)));
    }

    @Test
    void callerRehashedQualifiedReportStillCannotReplacePhysicalSolve(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        ObjectNode tree =
                (ObjectNode)
                        mapper.readTree(
                                Files.readAllBytes(
                                        SixMaxSuppliedIntervalFeedbackTest.data("report")));
        ((ObjectNode) tree.path("moves").get(1)).put("upperEvBb", 100);
        var forged = mapper.treeToValue(tree, SixMaxSuppliedIntervalFeedback.Report.class);
        assertTrue(forged.qualifiedForIntervalFeedback());
        var path = dir.resolve("rehashed.json");
        Files.writeString(path, SixMaxTextureStudy.json(forged));
        String pin = SixMaxHeadsUpPreflopGame.hash(forged);
        assertNotEquals(PIN, pin);
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedIntervalTrainerLoader.load(
                                        SixMaxSuppliedIntervalFeedbackTest.data("request"),
                                        path,
                                        pin));
        assertTrue(error.getMessage().contains("Exact interval feedback replay differs"));
    }
}
