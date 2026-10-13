package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Operator-only startup boundary: pin and complete physical replay before caching any grades. */
public final class SixMaxLaterFlopTrainerLoader {
    private SixMaxLaterFlopTrainerLoader() {}

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private static Snapshot snapshot(Path path) throws Exception {
        var real = path.toRealPath();
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > SixMaxLaterFlopFeedback.MAX_BYTES)
            throw new IllegalArgumentException("Later flop input is not a bounded regular file");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(SixMaxLaterFlopFeedback.MAX_BYTES + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length != before.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size())
            throw new IllegalArgumentException("Later flop input changed while reading");
        return new Snapshot(
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    public static SixMaxLaterFlopTrainer load(
            Path requestPath, Path reportPath, String expectedReportHash) throws Exception {
        if (requestPath == null
                || reportPath == null
                || expectedReportHash == null
                || !expectedReportHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(
                    "Request, report and exact report hash pin required");
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var requestBefore = snapshot(requestPath);
        var reportBefore = snapshot(reportPath);
        var saved = SixMaxLaterFlopFeedback.readReport(reportPath);
        if (!expectedReportHash.equals(SixMaxHeadsUpPreflopGame.hash(saved)))
            throw new IllegalArgumentException("Later flop qualification report pin differs");
        var owned = SixMaxLaterFlopFeedback.replay(requestPath, reportPath);
        var trainer = new SixMaxLaterFlopTrainer(owned);
        if (!expectedReportHash.equals(trainer.question().reportHash()))
            throw new IllegalArgumentException("Owned later flop qualification report pin differs");
        if (!requestBefore.equals(snapshot(requestPath))
                || !reportBefore.equals(snapshot(reportPath)))
            throw new IllegalArgumentException("Later flop inputs changed during startup replay");
        return trainer;
    }
}
