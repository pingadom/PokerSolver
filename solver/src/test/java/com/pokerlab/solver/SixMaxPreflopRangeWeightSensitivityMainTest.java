package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxPreflopRangeWeightSensitivityMainTest {
    @Test
    void repeatsDeterministicallyAndRejectsAliasesLimitsAndUnmatchedModelsBeforeMutation(
            @TempDir Path temp) throws Exception {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var baseline = temp.resolve("baseline.json");
        var variant = temp.resolve("variant.json");
        var output = temp.resolve("out/report.json");
        Files.writeString(baseline, MultiwayPackJson.writeFullRound(source));
        Files.writeString(
                variant,
                MultiwayPackJson.writeFullRound(
                        SixMaxPreflopRangeWeightSensitivityTest.variant(
                                source, 3, source.solution().iterations())));
        var inputBytes = Files.readAllBytes(baseline);
        String[] args = {baseline.toString(), variant.toString(), output.toString()};
        SixMaxPreflopRangeWeightSensitivityMain.main(args);
        var bytes = Files.readAllBytes(output);
        var report =
                new ObjectMapper()
                        .readValue(bytes, SixMaxPreflopRangeWeightSensitivityMain.Artifact.class);
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        assertEquals(1, report.comparisons().size());
        assertEquals(.25, report.comparisons().getFirst().jointDealTotalVariation(), 1e-12);
        SixMaxPreflopRangeWeightSensitivityMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertArrayEquals(inputBytes, Files.readAllBytes(baseline));
        for (var input : java.util.List.of(baseline, variant)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxPreflopRangeWeightSensitivityMain.main(
                                    new String[] {
                                        baseline.toString(), variant.toString(), input.toString()
                                    }));
            var alias = temp.resolve(input.getFileName() + ".alias");
            Files.createLink(alias, input);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxPreflopRangeWeightSensitivityMain.main(
                                    new String[] {
                                        baseline.toString(), variant.toString(), alias.toString()
                                    }));
        }
        Files.writeString(variant, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivityMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        try (var data = new java.io.RandomAccessFile(variant.toFile(), "rw")) {
            data.setLength(16L * 1024 * 1024 + 1);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivityMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivityMain.main(new String[2]));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivityMain.main(new String[11]));
        try (var files = Files.list(output.getParent())) {
            assertEquals(1, files.count());
        }
    }
}
