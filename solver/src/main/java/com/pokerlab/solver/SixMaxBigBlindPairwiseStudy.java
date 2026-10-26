package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * All BB contexts, derived only from an owned v10 source; candidates require fresh qualification.
 */
public final class SixMaxBigBlindPairwiseStudy {
    public static final String SCHEMA = "pokerlab-big-blind-pairwise-regret-study/v1";
    public static final String STATUS =
            "PAIRWISE_REGRET_DISCOVERY_REQUIRES_FRESH_REFERENCE_QUALIFICATION";
    public static final int MAX_BYTES = SixMaxBigBlindFlopCoverageStudy.MAX_BYTES;

    public record Case(
            int index, List<SixMaxSuppliedIntervalFeedback.Move> moves, boolean actionSeparated) {
        public Case {
            moves = List.copyOf(moves);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String lossAlgorithm,
            SixMaxBigBlindFlopCoverageStudy.Report source,
            List<Case> cases,
            List<Integer> candidateIndices) {
        public Report {
            cases = List.copyOf(cases);
            candidateIndices = List.copyOf(candidateIndices);
            if (!SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !ConditionalPairwiseRegret.ALGORITHM.equals(lossAlgorithm)
                    || source == null
                    || !SixMaxBigBlindFlopCoverageStudy.REFINED_REPORT_SCHEMA.equals(
                            source.schemaVersion())
                    || cases.size() != source.cases().size())
                throw new IllegalArgumentException(
                        "Complete versioned BB pairwise source required");
            var candidates = new ArrayList<Integer>();
            for (int i = 0; i < cases.size(); i++) {
                var original = source.cases().get(i);
                var item = cases.get(i);
                var expected =
                        original.conditional() == null
                                ? List.<SixMaxSuppliedIntervalFeedback.Move>of()
                                : ConditionalPairwiseRegret.moves(
                                        original.conditional(), source.request().maximumLossBb());
                boolean separated =
                        !expected.isEmpty() && SixMaxRefinedFlopCoverageStudy.separated(expected);
                if (item.index() != i
                        || !item.moves().equals(expected)
                        || item.actionSeparated() != separated)
                    throw new IllegalArgumentException(
                            "BB pairwise inventory, source or loss differs");
                if (separated) candidates.add(i);
            }
            if (!candidateIndices.equals(candidates))
                throw new IllegalArgumentException("Complete BB candidate inventory differs");
        }
    }

    public static final class Result {
        private final Report report;

        private Result(Report report) throws Exception {
            if (SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                throw new IllegalArgumentException("Expanded BB pairwise report exceeds cap");
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxBigBlindPairwiseStudy() {}

    public static Result assess(SixMaxBigBlindFlopCoverageStudy.Result owned) throws Exception {
        Objects.requireNonNull(owned);
        var source = owned.report();
        var cases = new ArrayList<Case>();
        var candidates = new ArrayList<Integer>();
        for (var original : source.cases()) {
            var moves =
                    original.conditional() == null
                            ? List.<SixMaxSuppliedIntervalFeedback.Move>of()
                            : ConditionalPairwiseRegret.moves(
                                    original.conditional(), source.request().maximumLossBb());
            boolean separated = !moves.isEmpty() && SixMaxRefinedFlopCoverageStudy.separated(moves);
            cases.add(new Case(original.index(), moves, separated));
            if (separated) candidates.add(original.index());
        }
        return new Result(
                new Report(
                        SCHEMA,
                        STATUS,
                        false,
                        ConditionalPairwiseRegret.ALGORITHM,
                        source,
                        cases,
                        candidates));
    }

    public static Result solve(SixMaxBigBlindFlopCoverageStudy.Request request) throws Exception {
        requireMode(request);
        return assess(SixMaxBigBlindFlopCoverageStudy.solve(request));
    }

    static void requireMode(SixMaxBigBlindFlopCoverageStudy.Request request) {
        if (request == null
                || !SixMaxBigBlindFlopCoverageStudy.REFINED_REQUEST_SCHEMA.equals(
                        request.schemaVersion()))
            throw new IllegalArgumentException("Explicit BB v2 / conditional v10 source required");
    }

    public static Report readReport(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Report.class);
    }

    public static void write(Path path, Result result) throws Exception {
        writeReport(path, result.report());
    }

    static void writeReport(Path path, Object report) throws Exception {
        byte[] bytes = SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded BB report exceeds cap");
        path = path.toAbsolutePath().normalize();
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored BB report exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var input = SixMaxBigBlindFlopCoverageStudy.readRequest(request);
        requireMode(input);
        var saved = readReport(report);
        if (!saved.source().request().equals(input))
            throw new IllegalArgumentException("BB pairwise request differs");
        var owned = solve(input);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Complete BB pairwise replay differs");
        return owned;
    }
}
