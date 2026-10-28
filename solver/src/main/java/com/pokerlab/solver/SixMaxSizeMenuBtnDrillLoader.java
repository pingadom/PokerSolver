package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Pins captured bytes before one physical replay; HTTP consumers receive only the owned cache. */
public final class SixMaxSizeMenuBtnDrillLoader {
    public static final String SCHEMA = "pokerlab-size-menu-btn-drill-manifest/v1";
    public static final String STUDY_HASH =
            "c55d598cedc094e836d2d4f4f5ca97897721218f9bc557a9830cc75b45fac5bc";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;

    public record StartupBudget(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumIntervalLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentSolveCalls != 9
                    || maximumParentLpSolves != 18
                    || maximumIntervalLpSolves != 32
                    || freshReferenceRuns != 2
                    || freshIterations != 240_000)
                throw new IllegalArgumentException("Complete BTN startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 9, 18, 32, 2, 240_000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String studyHash,
            String requestFile,
            String requestSha256,
            String reportFile,
            String reportStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !pin(requestSha256)
                    || !pin(reportStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException("Pinned BTN source files and budget required");
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

    private SixMaxSizeMenuBtnDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static boolean pin(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Captured capture(Path path, Path root, int limit) throws Exception {
        var real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException("BTN dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > limit)
            throw new IllegalArgumentException("Bounded regular BTN source required");
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
            throw new IllegalArgumentException("BTN source changed while reading");
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
            var result = gzip.readNBytes(SixMaxSizeMenuBtnStudy.MAX_BYTES + 1);
            if (result.length > SixMaxSizeMenuBtnStudy.MAX_BYTES)
                throw new IllegalArgumentException("Expanded BTN report cap exceeded");
            return result;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var source = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(source.bytes(), Manifest.class);
    }

    public static SixMaxSizeMenuBtnDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published BTN study pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxSizeMenuBtnStudy.MAX_BYTES);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxSizeMenuBtnStudy.MAX_BYTES);
        var sources = List.of(manifestBytes, requestBytes, reportBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased BTN startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256()))
            throw new IllegalArgumentException("BTN source byte pin differs");
        var request =
                mapper.readValue(expanded(requestBytes), SixMaxSizeMenuBtnStudy.Request.class);
        var saved = mapper.readValue(expanded(reportBytes), SixMaxSizeMenuBtnStudy.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.qualifiedIndices().equals(List.of(0, 1, 2, 3)))
            throw new IllegalArgumentException("BTN complete report identity differs");

        // Parsing never grants capabilities: replay every endpoint and both fresh references once.
        var owned = SixMaxSizeMenuBtnStudy.solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete physical BTN startup replay differs");
        var result = SixMaxSizeMenuBtnDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 4)
            throw new IllegalArgumentException("Owned BTN cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxSizeMenuBtnStudy.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256()))
                throw new IllegalArgumentException("BTN sources changed during complete startup");
        }
        return result;
    }
}
