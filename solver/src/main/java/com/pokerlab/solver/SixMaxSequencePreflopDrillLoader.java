package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Pins captured bytes before one physical replay; HTTP consumers receive only the owned cache. */
public final class SixMaxSequencePreflopDrillLoader {
    public static final String SCHEMA = "pokerlab-sequence-preflop-drill-manifest/v1";
    public static final String STUDY_HASH =
            "586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;

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
                    || maximumMinimumLpSolves != 1
                    || freshReferenceRuns != 2
                    || freshIterations != 240_000)
                throw new IllegalArgumentException(
                        "Complete sequence preflop startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 2, 4, 1, 2, 240_000);
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
            String legacyMinimumFile,
            String legacyMinimumStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !file(envelopeFile)
                    || !file(minimumFile)
                    || !file(legacyMinimumFile)
                    || !"77861632b5f1260071b89d7c465e812fff74547beef35fa776fb4562b7f13c18"
                            .equals(requestSha256)
                    || !"013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b"
                            .equals(reportStoredSha256)
                    || !SixMaxRootMinimumStudy.SOURCE_STORED_SHA.equals(envelopeStoredSha256)
                    || !SixMaxRootSequenceFeedback.MINIMUM_SHA.equals(minimumStoredSha256)
                    || !SixMaxRootMinimumFeedback.MINIMUM_SHA.equals(legacyMinimumStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned sequence preflop source files and budget required");
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

    private SixMaxSequencePreflopDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int limit) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        var real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "sequence preflop dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > limit)
            throw new IllegalArgumentException("Bounded regular sequence preflop source required");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(limit + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length > limit
                || bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("sequence preflop source changed while reading");
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
        try (var raw = new ByteArrayInputStream(source.bytes());
                var gzip = new GZIPInputStream(raw)) {
            var result = gzip.readNBytes(SixMaxRootSequenceFeedback.MAX_BYTES + 1);
            if (result.length > SixMaxRootSequenceFeedback.MAX_BYTES)
                throw new IllegalArgumentException("Expanded sequence preflop report cap exceeded");
            return result;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var source = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(source.bytes(), Manifest.class);
    }

    public static SixMaxSequencePreflopDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published sequence preflop study pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxRootSequenceFeedback.MAX_BYTES);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxRootSequenceFeedback.MAX_BYTES);
        var envelopeBytes =
                capture(
                        root.resolve(manifest.envelopeFile()),
                        root,
                        SixMaxRootSequenceFeedback.MAX_BYTES);
        var minimumBytes =
                capture(
                        root.resolve(manifest.minimumFile()),
                        root,
                        SixMaxRootSequenceFeedback.MAX_BYTES);
        var legacyBytes =
                capture(
                        root.resolve(manifest.legacyMinimumFile()),
                        root,
                        SixMaxRootSequenceFeedback.MAX_BYTES);
        var sources =
                List.of(
                        manifestBytes,
                        requestBytes,
                        reportBytes,
                        envelopeBytes,
                        minimumBytes,
                        legacyBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased sequence preflop startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.envelopeStoredSha256().equals(envelopeBytes.sha256())
                || !manifest.minimumStoredSha256().equals(minimumBytes.sha256())
                || !manifest.legacyMinimumStoredSha256().equals(legacyBytes.sha256()))
            throw new IllegalArgumentException("sequence preflop source byte pin differs");
        var request =
                mapper.readValue(expanded(requestBytes), SixMaxRootSequenceFeedback.Request.class);
        var saved =
                mapper.readValue(expanded(reportBytes), SixMaxRootSequenceFeedback.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.qualifiedForOfflineDrill())
            throw new IllegalArgumentException("sequence preflop complete report identity differs");

        var envelope =
                mapper.readValue(expanded(envelopeBytes), SixMaxRootEnvelopeFeedback.Report.class);
        var minimum =
                mapper.readValue(
                        expanded(minimumBytes), SixMaxRootSequenceMinimumStudy.Report.class);
        var legacy = mapper.readValue(expanded(legacyBytes), SixMaxRootMinimumStudy.Report.class);
        if (!request.minimumRequest().equals(minimum.request())
                || !SixMaxRootMinimumStudy.SOURCE_HASH.equals(
                        SixMaxHeadsUpPreflopGame.hash(envelope))
                || !saved.originalEnvelope().equals(envelope)
                || !saved.sourceMinimum().equals(minimum)
                || !minimum.legacyMinimum().equals(legacy))
            throw new IllegalArgumentException("Captured minimum source lineage differs");
        // DTOs confer no ownership. Only a complete physical replay can issue qualification.
        var owned = SixMaxRootSequenceFeedback.solve(request, envelope, legacy, minimum);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete physical sequence preflop startup replay differs");
        var result = SixMaxSequencePreflopDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 1)
            throw new IllegalArgumentException("Owned sequence preflop cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxRootSequenceFeedback.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256()))
                throw new IllegalArgumentException(
                        "sequence preflop sources changed during complete startup");
        }
        return result;
    }
}
