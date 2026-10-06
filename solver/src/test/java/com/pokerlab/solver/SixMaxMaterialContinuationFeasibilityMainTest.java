package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxMaterialContinuationFeasibilityMainTest {
    @Test
    void readOnlyDeterministicReportsRejectAliasesAndInvalidInputsBeforeReplacement(
            @TempDir Path temp) throws Exception {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var input = temp.resolve("source.json");
        var output = temp.resolve("reports/bound.json");
        Files.writeString(input, MultiwayPackJson.writeFullRound(source));
        var inputBytes = Files.readAllBytes(input);
        String[] args = {input.toString(), output.toString()};
        SixMaxMaterialContinuationFeasibilityMain.main(args);
        var bytes = Files.readAllBytes(output);
        var report =
                new ObjectMapper()
                        .readValue(bytes, SixMaxMaterialContinuationFeasibilityMain.Artifact.class);
        assertEquals("six-max-material-continuation-feasibility/v1", report.schemaVersion());
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals(MultiwayPackJson.fullRoundContentHash(source), report.sourcePackHash());
        assertEquals(source.spotHash(), report.sourceSpotHash());
        SixMaxMaterialContinuationFeasibilityMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertArrayEquals(inputBytes, Files.readAllBytes(input));
        var alias = temp.resolve("hardlink.json");
        Files.createLink(alias, input);
        for (var target : java.util.List.of(input, alias))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxMaterialContinuationFeasibilityMain.main(
                                    new String[] {input.toString(), target.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxMaterialContinuationFeasibilityMain.main(
                                new String[] {input.toString(), output.toString(), "21", "4"}));
        Files.writeString(input, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxMaterialContinuationFeasibilityMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        try (var data = new java.io.RandomAccessFile(input.toFile(), "rw")) {
            data.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxMaterialContinuationFeasibilityMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxMaterialContinuationFeasibilityMain.main(new String[0]));
        try (var files = Files.list(output.getParent())) {
            assertEquals(1, files.count());
        }
    }
}
