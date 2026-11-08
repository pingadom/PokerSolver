package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Capture and validate every input, reproduce once, then expose only immutable cached choices. */
public final class SixMaxRootHandBankDrillLoader {
    public static final String SCHEMA = "pokerlab-root-hand-bank-drill-manifest/v1";
    public static final String STUDY_HASH =
            "e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;
    public static final String REQUEST_SHA =
            "8eb03eb2dfd0d09e02be0a7b31f2b152db6a59c3b7c5a94a718ed50986b2d919";

    public record StartupBudget(
            int physicalGames,
            int maximumParentSolveCalls,
            int maximumParentLpSolves,
            int maximumEnvelopeLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentSolveCalls != 4
                    || maximumParentLpSolves != 8
                    || maximumEnvelopeLpSolves != 15
                    || freshReferenceRuns != 2
                    || freshIterations != 240000)
                throw new IllegalArgumentException(
                        "Complete root hand bank startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 4, 8, 15, 2, 240000);
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
                    || !"eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd"
                            .equals(reportStoredSha256)
                    || !SixMaxRootHandBankStudy.SOURCE_SHA.equals(parentStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned root hand bank files and complete budget required");
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

    private SixMaxRootHandBankDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int cap) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        Path real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "Root hand bank dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > cap)
            throw new IllegalArgumentException("Bounded regular root hand bank source required");
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
            throw new IllegalArgumentException("Root hand bank source changed while reading");
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
            byte[] expanded = gzip.readNBytes(SixMaxRootHandBankStudy.MAX_BYTES + 1);
            if (expanded.length > SixMaxRootHandBankStudy.MAX_BYTES)
                throw new IllegalArgumentException("Expanded root hand bank source cap exceeded");
            return expanded;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var captured = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(captured.bytes(), Manifest.class);
    }

    public static SixMaxRootHandBankDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published root hand bank pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes =
                capture(
                        root.resolve(manifest.requestFile()),
                        root,
                        SixMaxRootHandBankStudy.MAX_REQUEST_BYTES);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxRootHandBankStudy.MAX_BYTES);
        var parentBytes =
                capture(
                        root.resolve(manifest.parentFile()),
                        root,
                        SixMaxRootHandBankStudy.MAX_BYTES);
        var sources = List.of(manifestBytes, requestBytes, reportBytes, parentBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased root hand bank startup sources");
        if (!manifest.studyHash().equals(expectedStudyHash)
                || !manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.parentStoredSha256().equals(parentBytes.sha256()))
            throw new IllegalArgumentException("Root hand bank stored source byte pin differs");
        var request =
                mapper.readValue(expanded(requestBytes), SixMaxRootHandBankStudy.Request.class);
        var saved = mapper.readValue(expanded(reportBytes), SixMaxRootHandBankStudy.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.supportedQuestionIndices().equals(List.of(0, 1, 2))
                || !saved.completeOuterBounds())
            throw new IllegalArgumentException(
                    "Complete root hand bank feedback identity differs before startup");
        var parent =
                mapper.readValue(
                        expanded(parentBytes), SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        if (!saved.parent().equals(parent) || !request.parent().equals(parent.request()))
            throw new IllegalArgumentException(
                    "Captured full root hand bank source lineage differs");
        // The public study API independently pins and captures the same original parent path.
        // A parsed report cannot create Result; solve reconstructs physical payoffs and all LPs.
        var owned = SixMaxRootHandBankStudy.solve(request, parentBytes.path());
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete captured root hand bank startup replay differs");
        var result = SixMaxRootHandBankDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 3)
            throw new IllegalArgumentException("Owned root hand bank cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxRootHandBankStudy.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256())
                    || !Arrays.equals(before.bytes(), after.bytes()))
                throw new IllegalArgumentException(
                        "Root hand bank sources changed during complete startup");
        }
        return result;
    }
}
