package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Capture and validate every input, reproduce once, then expose only immutable cached choices. */
public final class SixMaxSevensPreflopDrillLoader {
    public static final String SCHEMA = "pokerlab-sevens-preflop-drill-manifest/v1";
    public static final String STUDY_HASH =
            "5f52b053c7610adbfb4abd2cfb210674b4a325d8b870cd350edc22d192708da6";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;
    public static final String REQUEST_SHA =
            "9c5c26307922844b09b61cf34510b0e31afeb2c38a9e78a98e70f334828c0049";

    public record StartupBudget(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumEnvelopeLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentSolveCalls != 2
                    || maximumParentLpSolves != 4
                    || maximumEnvelopeLpSolves != 5
                    || freshReferenceRuns != 2
                    || freshIterations != 240000)
                throw new IllegalArgumentException(
                        "Complete sevens preflop startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 2, 4, 5, 2, 240000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String studyHash,
            String requestFile,
            String requestSha256,
            String reportFile,
            String reportStoredSha256,
            String parentFile,
            String parentStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !file(parentFile)
                    || !REQUEST_SHA.equals(requestSha256)
                    || !"f6320a28164f3e6f8e4ae78b019d2933bccda5dc9f9cf212f0c7ae6aff59b3c3"
                            .equals(reportStoredSha256)
                    || !SixMaxSevensRootStudy.SOURCE_SHA.equals(parentStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned sevens preflop files and complete budget required");
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

    private SixMaxSevensPreflopDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int cap) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        Path real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "Sevens preflop dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > cap)
            throw new IllegalArgumentException("Bounded regular sevens preflop source required");
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
            throw new IllegalArgumentException("Sevens preflop source changed while reading");
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
            byte[] expanded = gzip.readNBytes(SixMaxSevensRootStudy.MAX_BYTES + 1);
            if (expanded.length > SixMaxSevensRootStudy.MAX_BYTES)
                throw new IllegalArgumentException("Expanded sevens preflop source cap exceeded");
            return expanded;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var captured = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(captured.bytes(), Manifest.class);
    }

    public static SixMaxSevensPreflopDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published sevens preflop pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxSevensRootStudy.MAX_REQUEST_BYTES);
        var reportBytes =
                capture(root.resolve(manifest.reportFile()), root, SixMaxSevensRootStudy.MAX_BYTES);
        var parentBytes =
                capture(root.resolve(manifest.parentFile()), root, SixMaxSevensRootStudy.MAX_BYTES);
        var sources = List.of(manifestBytes, requestBytes, reportBytes, parentBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased sevens preflop startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.parentStoredSha256().equals(parentBytes.sha256()))
            throw new IllegalArgumentException("Sevens preflop stored source byte pin differs");
        var request = mapper.readValue(expanded(requestBytes), SixMaxSevensRootStudy.Request.class);
        var saved = mapper.readValue(expanded(reportBytes), SixMaxSevensRootStudy.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.feedbackSupported()
                || !saved.completeOuterBounds())
            throw new IllegalArgumentException(
                    "Complete sevens feedback identity differs before startup");
        var parent =
                mapper.readValue(
                        expanded(parentBytes), SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        if (!saved.parent().equals(parent) || !request.parent().equals(parent.request()))
            throw new IllegalArgumentException("Captured full sevens source lineage differs");
        // The public study API independently pins and captures the same original parent path.
        // A parsed report cannot create Result; solve reconstructs physical payoffs and all LPs.
        var owned = SixMaxSevensRootStudy.solve(request, parentBytes.path());
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete captured sevens preflop startup replay differs");
        var result = SixMaxSevensPreflopDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 1)
            throw new IllegalArgumentException("Owned sevens preflop cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxSevensRootStudy.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256())
                    || !Arrays.equals(before.bytes(), after.bytes()))
                throw new IllegalArgumentException(
                        "Sevens preflop sources changed during complete startup");
        }
        return result;
    }
}
