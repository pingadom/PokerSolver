package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxRankTextureArtifactIoTest {
    @Test
    void malformedGzipCanBeReplacedImmediatelyAfterConstructorFailure(@TempDir Path temp)
            throws Exception {
        var path = temp.resolve("broken.json.gz");
        for (byte[] malformed : new byte[][] {new byte[0], {31}, {31, -117}, {'{', '}'}}) {
            Files.write(path, malformed);
            assertThrows(
                    java.io.IOException.class,
                    () -> SixMaxRankTexturePayoffTable.readBytes(path, 100));
            // Windows rejects this atomic replacement while the failed constructor's raw stream
            // remains open. Exercising replacement also verifies that callers can recover safely.
            SixMaxRankTexturePayoffTable.writeBytes(path, new byte[] {1, 2, 3}, 100);
            assertArrayEquals(
                    new byte[] {1, 2, 3}, SixMaxRankTexturePayoffTable.readBytes(path, 100));
            Files.delete(path);
        }
    }

    @Test
    void rawAndCompressedExpansionCapsPreserveExistingOutputs(@TempDir Path temp) throws Exception {
        var path = temp.resolve("report.json.gz");
        SixMaxRankTexturePayoffTable.writeBytes(path, new byte[1000], 1024);
        byte[] original = Files.readAllBytes(path);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.readBytes(path, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.writeBytes(path, new byte[1001], 1000));
        assertArrayEquals(original, Files.readAllBytes(path));
        var raw = temp.resolve("report.json");
        Files.write(raw, new byte[101]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.readBytes(raw, 100));
        // Compression overhead can exceed a cap even when the uncompressed payload fits.
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRankTexturePayoffTable.writeBytes(path, new byte[0], 1));
        assertArrayEquals(original, Files.readAllBytes(path));
    }
}
