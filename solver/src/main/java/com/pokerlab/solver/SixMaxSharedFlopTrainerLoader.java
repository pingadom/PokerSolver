package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Explicit operator startup only: pin, complete fresh replay, then publish an immutable cache. */
public final class SixMaxSharedFlopTrainerLoader {
    private SixMaxSharedFlopTrainerLoader() {}

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private static Snapshot snapshot(Path path) throws Exception {
        var real = path.toRealPath();
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > SixMaxSharedFlopFeedback.MAX_BYTES)
            throw new IllegalArgumentException("Shared input is not a bounded regular file");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(SixMaxSharedFlopFeedback.MAX_BYTES + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length != before.size()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey()))
            throw new IllegalArgumentException("Shared input changed while reading");
        return new Snapshot(
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    public static SixMaxSharedFlopTrainer load(
            Path requestPath, Path reportPath, String expectedReportHash) throws Exception {
        if (requestPath == null
                || reportPath == null
                || expectedReportHash == null
                || !expectedReportHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(
                    "Request, report and exact shared report hash pin required");
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var beforeRequest = snapshot(requestPath);
        var beforeReport = snapshot(reportPath);
        // Includes aliases/symlinks to the same file, even if the paths look different.
        if (Files.isSameFile(requestPath, reportPath))
            throw new IllegalArgumentException("Distinct shared inputs required");
        var saved = SixMaxSharedFlopFeedback.readReport(reportPath);
        if (!expectedReportHash.equals(SixMaxHeadsUpPreflopGame.hash(saved)))
            throw new IllegalArgumentException("Shared report pin differs");
        var trainer =
                new SixMaxSharedFlopTrainer(
                        SixMaxSharedFlopFeedback.replay(requestPath, reportPath));
        if (!expectedReportHash.equals(trainer.reportHash())
                || !beforeRequest.equals(snapshot(requestPath))
                || !beforeReport.equals(snapshot(reportPath)))
            throw new IllegalArgumentException(
                    "Shared inputs or pin changed during complete startup replay");
        return trainer;
    }
}
