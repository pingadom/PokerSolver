package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Complete six-source startup; one outer snapshot covers every nested dependency. */
public final class SixMaxCallFlopCollectionLoader {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-manifest/v5";
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;

    public record StartupBudget(int sourceReplays, int freshReferenceRuns, int freshIterations) {
        public StartupBudget {
            if (sourceReplays != 6 || freshReferenceRuns != 12 || freshIterations != 972000)
                throw new IllegalArgumentException(
                        "Complete six-source startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(6, 12, 972000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String collectionHash,
            String legacyManifestFile,
            String legacyCollectionHash,
            String legacyManifestSha256,
            String batchRequestFile,
            String batchRequestSha256,
            String batchReportFile,
            String batchReportStoredSha256,
            String batchReportHash,
            String sharedInputHash,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !SixMaxCallFlopCollection.pin(collectionHash)
                    || !SixMaxCallFlopCollection.LEGACY_HASH.equals(legacyCollectionHash)
                    || !SixMaxCallFlopCollection.BATCH_HASH.equals(batchReportHash)
                    || !file(legacyManifestFile)
                    || !file(batchRequestFile)
                    || !file(batchReportFile)
                    || !SixMaxCallFlopCollection.pin(legacyManifestSha256)
                    || !SixMaxCallFlopCollection.pin(batchRequestSha256)
                    || !SixMaxCallFlopCollection.pin(batchReportStoredSha256)
                    || !SixMaxCallFlopCollection.pin(sharedInputHash)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Exact typed sources, file pins and budget required");
        }
    }

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private SixMaxCallFlopCollectionLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Snapshot snapshot(Path path, Path root) throws Exception {
        var real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException("Collection dependency escapes manifest root");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        int limit = SixMaxSharedFlopFeedback.MAX_BYTES;
        if (!before.isRegularFile() || before.size() > limit)
            throw new IllegalArgumentException("Bounded regular collection input required");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(limit + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Collection input changed while reading");
        return new Snapshot(
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    public static Manifest readManifest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(path, MAX_MANIFEST_BYTES),
                        Manifest.class);
    }

    static List<Path> dependencies(Path path, Manifest manifest) throws Exception {
        path = path.toAbsolutePath().normalize();
        var paths = new ArrayList<Path>();
        paths.add(path);
        var legacy = path.getParent().resolve(manifest.legacyManifestFile());
        paths.addAll(
                SixMaxQualifiedFlopCollectionLoader.dependencies(
                        legacy, SixMaxQualifiedFlopCollectionLoader.readManifest(legacy)));
        paths.add(path.getParent().resolve(manifest.batchRequestFile()));
        paths.add(path.getParent().resolve(manifest.batchReportFile()));
        SixMaxTexturePayoffTableMain.distinct(paths);
        return List.copyOf(paths);
    }

    static void distinctOutput(Path manifest, Path output) throws Exception {
        var paths = new ArrayList<>(dependencies(manifest, readManifest(manifest)));
        paths.add(output);
        SixMaxTexturePayoffTableMain.distinct(paths);
    }

    // Bind snapshot paths to every manifest parsed before capture, including nested v3 paths.
    static void verifyDependencies(Path path, Manifest manifest, List<Path> paths)
            throws Exception {
        if (!manifest.equals(readManifest(path)) || !paths.equals(dependencies(path, manifest)))
            throw new IllegalArgumentException(
                    "Collection dependency inventory changed before replay");
    }

    public static SixMaxCallFlopCollection load(Path path, String pin) throws Exception {
        if (path == null || !SixMaxCallFlopCollection.pin(pin))
            throw new IllegalArgumentException("Manifest and exact v5 pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var outer = snapshot(path, root);
        var manifest = readManifest(path);
        if (!pin.equals(manifest.collectionHash())
                || !pin.equals(
                        SixMaxCallFlopCollection.identity(
                                manifest.legacyCollectionHash(),
                                manifest.legacyManifestSha256(),
                                manifest.batchReportHash(),
                                manifest.sharedInputHash(),
                                manifest.batchRequestSha256(),
                                manifest.batchReportStoredSha256())))
            throw new IllegalArgumentException("V5 collection identity differs");
        var legacyPath = root.resolve(manifest.legacyManifestFile());
        var legacyBefore = snapshot(legacyPath, root);
        var legacyManifest = SixMaxQualifiedFlopCollectionLoader.readManifest(legacyPath);
        if (!legacyBefore.sha().equals(manifest.legacyManifestSha256())
                || !legacyManifest.collectionHash().equals(manifest.legacyCollectionHash())
                || !legacyManifest.weakInputHash().equals(manifest.sharedInputHash()))
            throw new IllegalArgumentException(
                    "Legacy manifest or deliberate shared-game binding differs");
        var paths = dependencies(path, manifest);
        if (paths.size() != 15)
            throw new IllegalArgumentException(
                    "Complete fifteen-file dependency inventory required");
        var before = new ArrayList<Snapshot>();
        before.add(outer);
        before.add(legacyBefore);
        for (int i = 2; i < paths.size(); i++) before.add(snapshot(paths.get(i), root));
        verifyDependencies(path, manifest, paths);
        for (int i = 0; i < paths.size(); i++)
            for (int j = i + 1; j < paths.size(); j++)
                if (Files.isSameFile(paths.get(i), paths.get(j)))
                    throw new IllegalArgumentException("Aliased collection dependencies");
        Path requestPath = paths.get(13), reportPath = paths.get(14);
        var request = SixMaxSharedFlopFeedback.readRequest(requestPath);
        var report = SixMaxSharedFlopFeedback.readReport(reportPath);
        if (!before.get(13).sha().equals(manifest.batchRequestSha256())
                || !before.get(14).sha().equals(manifest.batchReportStoredSha256())
                || !request.equals(report.request())
                || !SixMaxHeadsUpPreflopGame.hash(report).equals(manifest.batchReportHash())
                || !manifest.sharedInputHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(request.parent().connected().input()))
                || !request.heroHands().equals(List.of("Ah Kh", "As Qs", "Qh Th"))
                || manifest.startupBudget().sourceReplays()
                        != legacyManifest.startupBudget().sourceReplays() + 1
                || manifest.startupBudget().freshReferenceRuns()
                        != legacyManifest.startupBudget().freshReferenceRuns() + 2
                || manifest.startupBudget().freshIterations()
                        != legacyManifest.startupBudget().freshIterations()
                                + request.referenceBudget().totalIterations())
            throw new IllegalArgumentException(
                    "Shared batch bytes, input, report or aggregate reservation differs");
        var legacy =
                SixMaxQualifiedFlopCollectionLoader.load(
                        legacyPath, manifest.legacyCollectionHash());
        var batch =
                SixMaxSharedFlopTrainerLoader.load(
                        requestPath, reportPath, manifest.batchReportHash());
        var collection =
                new SixMaxCallFlopCollection(
                        legacy,
                        batch,
                        legacyBefore.sha(),
                        manifest.sharedInputHash(),
                        before.get(13).sha(),
                        before.get(14).sha());
        if (!pin.equals(collection.collectionHash()))
            throw new IllegalArgumentException("Owned v5 pin differs");
        for (int i = 0; i < paths.size(); i++)
            if (!before.get(i).equals(snapshot(paths.get(i), root)))
                throw new IllegalArgumentException(
                        "Collection dependencies changed during complete startup");
        return collection;
    }
}
