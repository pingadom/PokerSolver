package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Capture and validate every input, reproduce once, then expose only immutable cached choices. */
public final class SixMaxBatchPreflopDrillLoader {
    public static final String SCHEMA = "pokerlab-batch-preflop-drill-manifest/v1";
    public static final String STUDY_HASH =
            "a7533a49a06fe2b7376109bce133c1b3c937dbef8915ed47af86290d36b777c8";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;
    public static final String REQUEST_SHA =
            "37cd3aa141d2eeb083d68dd586d9335b470f7e77717bc5dc7c466b55350684b0";

    public record StartupBudget(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumMinimumLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentSolveCalls != 2
                    || maximumParentLpSolves != 4
                    || maximumMinimumLpSolves != 3
                    || freshReferenceRuns != 2
                    || freshIterations != 240000)
                throw new IllegalArgumentException(
                        "Complete batch preflop startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 2, 4, 3, 2, 240000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String studyHash,
            String requestFile,
            String requestSha256,
            String reportFile,
            String reportStoredSha256,
            String envelopeFile,
            String envelopeStoredSha256,
            String minimumFile,
            String minimumStoredSha256,
            String batchFile,
            String batchStoredSha256,
            String previousFeedbackFile,
            String previousFeedbackStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !file(envelopeFile)
                    || !file(minimumFile)
                    || !file(batchFile)
                    || !file(previousFeedbackFile)
                    || !REQUEST_SHA.equals(requestSha256)
                    || !SixMaxRootSequenceBatchFeedback.PUBLISHED_REPORT_SHA.equals(
                            reportStoredSha256)
                    || !SixMaxRootSequenceMinimumStudy.SOURCE_STORED_SHA.equals(
                            envelopeStoredSha256)
                    || !SixMaxRootSequenceBatchStudy.PRIOR_STORED_SHA.equals(minimumStoredSha256)
                    || !SixMaxRootSequenceBatchFeedback.BATCH_SHA.equals(batchStoredSha256)
                    || !SixMaxRootSequenceBatchFeedback.PREVIOUS_SHA.equals(
                            previousFeedbackStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned batch preflop files and complete budget required");
        }
    }

    private record Captured(
            Path path,
            Path realPath,
            Object fileKey,
            long size,
            String modified,
            String sha256,
            byte[] bytes) {}

    private SixMaxBatchPreflopDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int cap) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        Path real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "Batch preflop dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > cap)
            throw new IllegalArgumentException("Bounded regular batch preflop source required");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(cap + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length > cap
                || bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Batch preflop source changed while reading");
        return new Captured(
                path,
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes);
    }

    private static byte[] expanded(Captured source) throws Exception {
        if (!source.path().toString().endsWith(".gz")) return source.bytes();
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(source.bytes()))) {
            byte[] expanded = gzip.readNBytes(SixMaxRootSequenceBatchFeedback.MAX_BYTES + 1);
            if (expanded.length > SixMaxRootSequenceBatchFeedback.MAX_BYTES)
                throw new IllegalArgumentException("Expanded batch preflop source cap exceeded");
            return expanded;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var captured = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(captured.bytes(), Manifest.class);
    }

    public static SixMaxBatchPreflopDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published batch preflop pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var envelopeBytes =
                capture(
                        root.resolve(manifest.envelopeFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var minimumBytes =
                capture(
                        root.resolve(manifest.minimumFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var batchBytes =
                capture(
                        root.resolve(manifest.batchFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var previousBytes =
                capture(
                        root.resolve(manifest.previousFeedbackFile()),
                        root,
                        SixMaxRootSequenceBatchFeedback.MAX_BYTES);
        var sources =
                List.of(
                        manifestBytes,
                        requestBytes,
                        reportBytes,
                        envelopeBytes,
                        minimumBytes,
                        batchBytes,
                        previousBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased batch preflop startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.envelopeStoredSha256().equals(envelopeBytes.sha256())
                || !manifest.minimumStoredSha256().equals(minimumBytes.sha256())
                || !manifest.batchStoredSha256().equals(batchBytes.sha256())
                || !manifest.previousFeedbackStoredSha256().equals(previousBytes.sha256()))
            throw new IllegalArgumentException("Batch preflop stored source byte pin differs");
        var request =
                mapper.readValue(
                        expanded(requestBytes), SixMaxRootSequenceBatchFeedback.Request.class);
        var saved =
                mapper.readValue(
                        expanded(reportBytes), SixMaxRootSequenceBatchFeedback.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.qualifiedForOfflineDrill())
            throw new IllegalArgumentException(
                    "Complete batch feedback identity differs before startup");
        var envelope =
                mapper.readValue(expanded(envelopeBytes), SixMaxRootEnvelopeFeedback.Report.class);
        var minimum =
                mapper.readValue(
                        expanded(minimumBytes), SixMaxRootSequenceMinimumStudy.Report.class);
        var batch =
                mapper.readValue(expanded(batchBytes), SixMaxRootSequenceBatchStudy.Report.class);
        var previous =
                mapper.readValue(expanded(previousBytes), SixMaxRootSequenceFeedback.Report.class);
        if (!saved.sourceBatch().equals(batch)
                || !saved.previousFeedback().equals(previous)
                || !batch.previousMinimum().equals(minimum)
                || !batch.parent().equals(envelope.envelope().parent())
                || !batch.originalEnvelope().equals(envelope.envelope().envelope()))
            throw new IllegalArgumentException("Captured full batch source lineage differs");
        // Parsed DTOs are not capabilities. This call reconstructs the complete physical source.
        var owned =
                SixMaxRootSequenceBatchFeedback.solve(request, envelope, minimum, batch, previous);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete captured batch preflop startup replay differs");
        var result = SixMaxBatchPreflopDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 1)
            throw new IllegalArgumentException("Owned batch preflop cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxRootSequenceBatchFeedback.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256())
                    || !Arrays.equals(before.bytes(), after.bytes()))
                throw new IllegalArgumentException(
                        "Batch preflop sources changed during complete startup");
        }
        return result;
    }
}
