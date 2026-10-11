package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/**
 * Trusted local manifest boundary: pin and inventory checks precede physical replay of EVERY
 * source.
 */
public final class SixMaxSuppliedIntervalCollectionLoader {
    public static final String SCHEMA = "pokerlab-supplied-interval-collection-manifest/v1";

    public record Source(String requestFile, String reportFile, String reportHash) {
        public Source {
            if (!file(requestFile)
                    || !file(reportFile)
                    || requestFile.equals(reportFile)
                    || reportHash == null
                    || !reportHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException(
                        "Sibling source filenames and exact report pin required");
        }
    }

    public record Manifest(String schemaVersion, String collectionHash, List<Source> sources) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || collectionHash == null
                    || !collectionHash.matches("[0-9a-f]{64}")
                    || sources == null
                    || sources.size() < 2
                    || sources.size() > SixMaxSuppliedIntervalCollection.MAX_QUESTIONS
                    || sources.stream().anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Invalid interval collection manifest");
            sources = List.copyOf(sources);
        }
    }

    private SixMaxSuppliedIntervalCollectionLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    public static SixMaxSuppliedIntervalCollection load(Path path, String expectedHash)
            throws Exception {
        if (path == null || expectedHash == null || !expectedHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Manifest and exact collection hash pin required");
        path = path.toAbsolutePath().normalize();
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                path, SixMaxSuppliedIntervalCollection.MAX_BYTES),
                        Manifest.class);
        if (!expectedHash.equals(manifest.collectionHash()))
            throw new IllegalArgumentException("Configured collection pin differs");
        var paths = new ArrayList<Path>();
        paths.add(path);
        var reports = new ArrayList<SixMaxSuppliedIntervalFeedback.Report>();
        for (var source : manifest.sources()) {
            var requestPath = path.getParent().resolve(source.requestFile());
            var reportPath = path.getParent().resolve(source.reportFile());
            paths.add(requestPath);
            paths.add(reportPath);
            var request = SixMaxSuppliedIntervalFeedback.readRequest(requestPath);
            var report =
                    mapper.readValue(
                            SixMaxRankTexturePayoffTable.readBytes(
                                    reportPath, SixMaxSuppliedIntervalFeedback.MAX_BYTES),
                            SixMaxSuppliedIntervalFeedback.Report.class);
            if (!request.equals(report.request())
                    || !source.reportHash().equals(SixMaxHeadsUpPreflopGame.hash(report)))
                throw new IllegalArgumentException(
                        "Collection source request or report pin differs");
            reports.add(report);
        }
        SixMaxTexturePayoffTableMain.distinct(paths);
        if (!expectedHash.equals(SixMaxSuppliedIntervalCollection.identity(reports)))
            throw new IllegalArgumentException("Collection identity/order differs");
        var owned = new ArrayList<SixMaxSuppliedIntervalFeedback.Result>();
        for (var source : manifest.sources()) {
            var result =
                    SixMaxSuppliedIntervalFeedback.replay(
                            path.getParent().resolve(source.requestFile()),
                            path.getParent().resolve(source.reportFile()));
            if (!source.reportHash()
                    .equals(
                            result.qualifiedFeedback()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Source did not qualify"))
                                    .reportHash()))
                throw new IllegalArgumentException("Owned collection source pin differs");
            owned.add(result);
        }
        var collection = new SixMaxSuppliedIntervalCollection(owned);
        if (!expectedHash.equals(collection.collectionHash()))
            throw new IllegalArgumentException("Owned collection pin differs");
        return collection;
    }
}
