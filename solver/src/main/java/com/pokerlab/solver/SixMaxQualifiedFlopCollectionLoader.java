package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Fully replay both owned entries with one outer snapshot covering all twelve dependencies. */
public final class SixMaxQualifiedFlopCollectionLoader {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-manifest/v4";
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;

    public record StartupBudget(int sourceReplays, int freshReferenceRuns, int freshIterations) {
        public StartupBudget {
            if (sourceReplays != 5 || freshReferenceRuns != 10 || freshIterations != 732000)
                throw new IllegalArgumentException(
                        "Complete five-source startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(5, 10, 732000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String collectionHash,
            String legacyManifestFile,
            String legacyCollectionHash,
            String legacyInputHash,
            String weakRequestFile,
            String weakReportFile,
            String weakReportHash,
            String weakInputHash,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !SixMaxQualifiedFlopCollection.pin(collectionHash)
                    || !file(legacyManifestFile)
                    || !file(weakRequestFile)
                    || !file(weakReportFile)
                    || !SixMaxQualifiedFlopCollection.pin(legacyCollectionHash)
                    || !SixMaxQualifiedFlopCollection.pin(legacyInputHash)
                    || !SixMaxQualifiedFlopCollection.pin(weakInputHash)
                    || legacyInputHash.equals(weakInputHash)
                    || !SixMaxWeakHandFlopTrainer.REPORT_HASH.equals(weakReportHash)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned independent typed sources and budget required");
        }
    }

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Snapshot snapshot(Path path, int limit) throws Exception {
        var real = path.toRealPath();
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > limit)
            throw new IllegalArgumentException(
                    "Flop collection input is not a bounded regular file");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(limit + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Flop collection input changed while reading");
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
        Path nested = path.getParent().resolve(manifest.legacyManifestFile());
        paths.add(nested);
        for (var source :
                SixMaxExpandedFlopIntervalCollectionLoader.readManifest(nested).sources()) {
            paths.add(nested.getParent().resolve(source.requestFile()));
            paths.add(nested.getParent().resolve(source.reportFile()));
        }
        paths.add(path.getParent().resolve(manifest.weakRequestFile()));
        paths.add(path.getParent().resolve(manifest.weakReportFile()));
        SixMaxTexturePayoffTableMain.distinct(paths);
        return List.copyOf(paths);
    }

    static void distinctOutput(Path manifest, Path output) throws Exception {
        var paths = new ArrayList<>(dependencies(manifest, readManifest(manifest)));
        paths.add(output);
        SixMaxTexturePayoffTableMain.distinct(paths);
    }

    public static SixMaxQualifiedFlopCollection load(Path path, String pin) throws Exception {
        if (path == null || !SixMaxQualifiedFlopCollection.pin(pin))
            throw new IllegalArgumentException("Manifest and exact collection pin required");
        path = path.toAbsolutePath().normalize();
        var outerBefore = snapshot(path, MAX_MANIFEST_BYTES);
        var manifest = readManifest(path);
        Path nested = path.getParent().resolve(manifest.legacyManifestFile());
        var nestedBefore = snapshot(nested, MAX_MANIFEST_BYTES);
        var old = SixMaxExpandedFlopIntervalCollectionLoader.readManifest(nested);
        if (!pin.equals(manifest.collectionHash())
                || !pin.equals(
                        SixMaxQualifiedFlopCollection.identity(
                                manifest.legacyCollectionHash(),
                                manifest.legacyInputHash(),
                                manifest.weakReportHash(),
                                manifest.weakInputHash()))
                || !manifest.legacyCollectionHash().equals(old.collectionHash()))
            throw new IllegalArgumentException("Qualified collection manifest identity differs");
        var paths = dependencies(path, manifest);
        var before = new ArrayList<Snapshot>();
        before.add(outerBefore);
        before.add(nestedBefore);
        for (var input : paths.subList(2, paths.size()))
            before.add(snapshot(input, SixMaxRefinedFlopFeedback.MAX_BYTES));
        var initial = SixMaxSuppliedFlopFeedback.readRequest(paths.get(2));
        var weakRequest = SixMaxRefinedFlopFeedback.readRequest(paths.get(10));
        var weakReport = SixMaxRefinedFlopFeedback.readReport(paths.get(11));
        if (!manifest.legacyInputHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(initial.connected().input()))
                || !manifest.weakInputHash()
                        .equals(
                                SixMaxHeadsUpPreflopGame.hash(
                                        weakRequest.conditional().parent().connected().input()))
                || !manifest.weakReportHash().equals(SixMaxHeadsUpPreflopGame.hash(weakReport))
                || !weakRequest.equals(weakReport.request())
                || manifest.startupBudget().freshIterations()
                        != old.startupBudget().freshIterations()
                                + weakRequest.referenceBudget().totalIterations())
            throw new IllegalArgumentException(
                    "Source input, report or aggregate work identity differs");
        // Both entries are completely qualified by their original loaders. No gate is bypassed.
        var legacy =
                SixMaxExpandedFlopIntervalCollectionLoader.load(
                        nested, manifest.legacyCollectionHash());
        var weak =
                SixMaxWeakHandFlopTrainer.load(
                        paths.get(10), paths.get(11), manifest.weakReportHash());
        var collection =
                new SixMaxQualifiedFlopCollection(
                        legacy, weak, manifest.legacyInputHash(), manifest.weakInputHash());
        if (!pin.equals(collection.collectionHash()))
            throw new IllegalArgumentException("Owned qualified collection pin differs");
        for (int i = 0; i < paths.size(); i++)
            if (!before.get(i)
                    .equals(
                            snapshot(
                                    paths.get(i),
                                    i < 2
                                            ? MAX_MANIFEST_BYTES
                                            : SixMaxRefinedFlopFeedback.MAX_BYTES)))
                throw new IllegalArgumentException(
                        "Qualified collection dependencies changed during startup");
        return collection;
    }
}
