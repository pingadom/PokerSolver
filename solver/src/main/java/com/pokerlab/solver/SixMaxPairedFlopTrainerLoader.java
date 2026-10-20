package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Operator-only startup boundary: pin and complete physical replay before caching any grades. */
public final class SixMaxPairedFlopTrainerLoader {
    private SixMaxPairedFlopTrainerLoader() {}

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private static Snapshot snapshot(Path path) throws Exception {
        var real = path.toRealPath();
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > SixMaxPairedFlopCurriculum.MAX_BYTES)
            throw new IllegalArgumentException(
                    "paired BTN flop input is not a bounded regular file");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(SixMaxPairedFlopCurriculum.MAX_BYTES + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length != before.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size())
            throw new IllegalArgumentException("paired BTN flop input changed while reading");
        return new Snapshot(
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    public static SixMaxPairedFlopTrainer load(
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
        var request = SixMaxPairedFlopCurriculum.readRequest(requestPath);
        var saved = SixMaxPairedFlopCurriculum.readReport(reportPath);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("paired BTN curriculum request differs");
        if (!expectedReportHash.equals(SixMaxHeadsUpPreflopGame.hash(saved)))
            throw new IllegalArgumentException("paired BTN flop qualification report pin differs");
        var owned = SixMaxPairedFlopCurriculum.replay(requestPath, reportPath);
        var trainer = new SixMaxPairedFlopTrainer(owned);
        if (!expectedReportHash.equals(trainer.reportHash()))
            throw new IllegalArgumentException(
                    "Owned paired BTN flop qualification report pin differs");
        if (!requestBefore.equals(snapshot(requestPath))
                || !reportBefore.equals(snapshot(reportPath)))
            throw new IllegalArgumentException(
                    "paired BTN flop inputs changed during startup replay");
        return trainer;
    }
}
