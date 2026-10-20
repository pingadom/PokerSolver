package com.pokerlab.solver;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/** Trusted ordered manifest; every source physically replays once before grades are cached. */
public final class SixMaxExpandedFlopIntervalCollectionLoader {
    public static final String SCHEMA = "pokerlab-flop-interval-collection-manifest/v3";
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;

    public record Source(
            SixMaxExpandedFlopIntervalCollection.Kind kind,
            String requestFile,
            String reportFile,
            String reportHash) {
        public Source {
            if (kind == null
                    || !file(requestFile)
                    || !file(reportFile)
                    || requestFile.equals(reportFile)
                    || reportHash == null
                    || !reportHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException(
                        "Typed sibling source filenames and exact report pin required");
        }
    }

    /** Exact aggregate reference reservation, in addition to every source physical/LP cap. */
    public record StartupBudget(int sourceReplays, int freshReferenceRuns, int freshIterations) {
        public StartupBudget {
            if (sourceReplays != 4 || freshReferenceRuns != 8 || freshIterations != 492000)
                throw new IllegalArgumentException(
                        "Fixed complete four-source startup reservation required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(4, 8, 492000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String collectionHash,
            List<Source> sources,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || collectionHash == null
                    || !collectionHash.matches("[0-9a-f]{64}")
                    || sources == null
                    || sources.size() != 4
                    || startupBudget == null
                    || sources.stream().anyMatch(Objects::isNull)
                    || !sources.stream()
                            .map(Source::kind)
                            .toList()
                            .equals(
                                    List.of(
                                            SixMaxExpandedFlopIntervalCollection.Kind
                                                    .INITIAL_CHECK_BET,
                                            SixMaxExpandedFlopIntervalCollection.Kind.BB_FACING_BET,
                                            SixMaxExpandedFlopIntervalCollection.Kind
                                                    .BTN_AFTER_CHECK,
                                            SixMaxExpandedFlopIntervalCollection.Kind
                                                    .PAIRED_BTN_FACING_BET)))
                throw new IllegalArgumentException(
                        "Ordered BB, BTN-after-check and paired BTN-facing-bet sources required");
            sources = List.copyOf(sources);
        }
    }

    private record Snapshot(
            Path realPath, Object fileKey, long size, String modified, String sha) {}

    private SixMaxExpandedFlopIntervalCollectionLoader() {}

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

    /** Protect every source against output aliases before any expensive source replay. */
    static void distinctOutput(Path manifest, Path output) throws Exception {
        var paths = new ArrayList<Path>();
        paths.add(manifest);
        paths.add(output);
        for (var source : readManifest(manifest).sources()) {
            paths.add(
                    manifest.toAbsolutePath()
                            .normalize()
                            .getParent()
                            .resolve(source.requestFile()));
            paths.add(
                    manifest.toAbsolutePath().normalize().getParent().resolve(source.reportFile()));
        }
        SixMaxTexturePayoffTableMain.distinct(paths);
    }

    public static SixMaxExpandedFlopIntervalCollection load(Path path, String expectedHash)
            throws Exception {
        if (path == null || expectedHash == null || !expectedHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Manifest and exact collection hash pin required");
        path = path.toAbsolutePath().normalize();
        var manifestBefore = snapshot(path, MAX_MANIFEST_BYTES);
        var manifest = readManifest(path);
        if (!expectedHash.equals(manifest.collectionHash())
                || !expectedHash.equals(
                        SixMaxExpandedFlopIntervalCollection.identity(
                                manifest.sources().get(0).reportHash(),
                                manifest.sources().get(1).reportHash(),
                                manifest.sources().get(2).reportHash(),
                                manifest.sources().get(3).reportHash())))
            throw new IllegalArgumentException("Flop collection pin or typed source order differs");
        var paths = new ArrayList<Path>();
        paths.add(path);
        for (var source : manifest.sources()) {
            paths.add(path.getParent().resolve(source.requestFile()));
            paths.add(path.getParent().resolve(source.reportFile()));
        }
        SixMaxTexturePayoffTableMain.distinct(paths);
        var before = new ArrayList<Snapshot>();
        before.add(manifestBefore);
        for (var input : paths.subList(1, paths.size()))
            before.add(snapshot(input, SixMaxLaterFlopFeedback.MAX_BYTES));
        var initialRequest = SixMaxSuppliedFlopFeedback.readRequest(paths.get(1));
        var initialReport =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        paths.get(2), SixMaxSuppliedFlopFeedback.MAX_BYTES),
                                SixMaxSuppliedFlopFeedback.Report.class);
        var laterRequest = SixMaxLaterFlopFeedback.readRequest(paths.get(3));
        var laterReport = SixMaxLaterFlopFeedback.readReport(paths.get(4));
        var btnRequest = SixMaxBtnFlopCurriculum.readRequest(paths.get(5));
        var btnReport = SixMaxBtnFlopCurriculum.readReport(paths.get(6));
        var pairedRequest = SixMaxPairedFlopCurriculum.readRequest(paths.get(7));
        var pairedReport = SixMaxPairedFlopCurriculum.readReport(paths.get(8));
        SixMaxExpandedFlopIntervalCollection.compatible(
                initialRequest, laterRequest, btnRequest, pairedRequest);
        if (!pairedRequest.equals(pairedReport.request())
                || !manifest.sources()
                        .get(3)
                        .reportHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(pairedReport)))
            throw new IllegalArgumentException("Paired collection source request or pin differs");
        int iterations =
                12000 // Two fixed 2k/4k reference pairs in the original BB protocols.
                        + btnRequest.referenceBudget().iterations().stream()
                                .mapToInt(Integer::intValue)
                                .sum()
                        + pairedRequest.referenceBudget().iterations().stream()
                                .mapToInt(Integer::intValue)
                                .sum();
        if (iterations != manifest.startupBudget().freshIterations())
            throw new IllegalArgumentException("Aggregate fresh iteration reservation differs");
        if (!btnRequest.equals(btnReport.request())
                || !manifest.sources()
                        .get(2)
                        .reportHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(btnReport))
                || !initialRequest.equals(initialReport.request())
                || !laterRequest.equals(laterReport.request())
                || !manifest.sources()
                        .getFirst()
                        .reportHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(initialReport))
                || !manifest.sources()
                        .get(1)
                        .reportHash()
                        .equals(SixMaxHeadsUpPreflopGame.hash(laterReport)))
            throw new IllegalArgumentException(
                    "Flop collection source request or report pin differs");
        // All cheap pins, inventory, aliases and request compatibility precede every replay.
        var initial = SixMaxSuppliedFlopFeedback.replay(paths.get(1), paths.get(2));
        var later = SixMaxLaterFlopFeedback.replay(paths.get(3), paths.get(4));
        var btn = SixMaxBtnFlopCurriculum.replay(paths.get(5), paths.get(6));
        var paired = SixMaxPairedFlopCurriculum.replay(paths.get(7), paths.get(8));
        var collection = new SixMaxExpandedFlopIntervalCollection(initial, later, btn, paired);
        if (!expectedHash.equals(collection.collectionHash()))
            throw new IllegalArgumentException("Owned flop collection pin differs");
        for (int i = 0; i < paths.size(); i++)
            if (!before.get(i)
                    .equals(
                            snapshot(
                                    paths.get(i),
                                    i == 0
                                            ? MAX_MANIFEST_BYTES
                                            : SixMaxLaterFlopFeedback.MAX_BYTES)))
                throw new IllegalArgumentException(
                        "Flop collection inputs changed during startup replay");
        return collection;
    }
}
