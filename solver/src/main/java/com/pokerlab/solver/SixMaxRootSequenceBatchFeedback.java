package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Offline feedback from an owned complete-menu batch over the original physical source. */
public final class SixMaxRootSequenceBatchFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-root-sequence-batch-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-sequence-batch-feedback-report/v1";
    public static final String STATUS =
            "SHARED_SEQUENCE_MINIMA_COMPLETE_MENU_FEEDBACK_RESEARCH_ONLY";
    public static final String SCOPE = SixMaxRootSequenceFeedback.SCOPE;
    public static final String QUALIFICATION_SCOPE =
            "ORIGINAL_BB99_COMPLETE_MENU_SHARED_SEQUENCE_MINIMA_OFFLINE_ONLY/v1";
    public static final String BATCH_SHA = SixMaxRootSequenceBatchStudy.PUBLISHED_REPORT_SHA;
    public static final String BATCH_CANONICAL =
            "f8dc4b8960ba39469ea773108ab7d6bbbc6284fbdadf424cda29f7c54461e37d";
    public static final String PREVIOUS_SHA =
            "013686942f9d630dbcaad65673afed0263b73d864b6f27e27c161998887ec52b";
    public static final String PREVIOUS_CANONICAL =
            "586e0c0777ca9fa8f48e957f126a8f24c8b9382ba9da1442f684fdcb5f40ff21";
    public static final String PUBLISHED_REPORT_SHA =
            "d8f6897061acbba3e52643be206ca8e0e1927ed2577c2cd053b2cb02db5f4653";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Request(
            String schemaVersion,
            SixMaxRootSequenceBatchStudy.Request batchRequest,
            String batchStoredSha256,
            String previousFeedbackStoredSha256,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || batchRequest == null
                    || !BATCH_SHA.equals(batchStoredSha256)
                    || !PREVIOUS_SHA.equals(previousFeedbackStoredSha256)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Original complete physical batch and unchanged loss threshold required");
        }
    }

    /**
     * Keep both lower-bound inputs, including rounding differences; ceilings come from the original
     * envelope.
     */
    public record BoundSource(
            String action,
            double originalConservativeLowerBb,
            double sequenceMinimumLowerBb,
            double selectedLowerBb,
            double originalFullActionUpperBb) {}

    public record PolicyCheck(
            int iterations,
            String solutionHash,
            boolean sourceMinimaSupported,
            int minimumLowerChecks,
            boolean boundsChecked,
            int utilityLossInequalitiesChecked,
            Double maximumBoundViolationBb,
            List<String> failures) {
        public PolicyCheck {
            failures = List.copyOf(failures);
        }
    }

    public record PreviousMenuComparison(
            String action,
            SixMaxSuppliedIntervalFeedback.Move previousMove,
            double lowerEvDifferenceBb,
            double upperEvDifferenceBb,
            double lowerLossDifferenceBb,
            double upperLossDifferenceBb,
            boolean classificationUnchanged) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String boundsScope,
            String qualificationScope,
            Request request,
            SixMaxRootSequenceBatchStudy.Report sourceBatch,
            SixMaxRootSequenceFeedback.Report previousFeedback,
            List<BoundSource> lowerBoundSources,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<PolicyCheck> policies,
            List<PreviousMenuComparison> previousMenuComparison,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            lowerBoundSources = List.copyOf(lowerBoundSources);
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            previousMenuComparison = List.copyOf(previousMenuComparison);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SCOPE.equals(boundsScope)
                    || !QUALIFICATION_SCOPE.equals(qualificationScope))
                throw new IllegalArgumentException(
                        "Original offline batch feedback scope required");
            lineage(request, sourceBatch, previousFeedback);
            if (!lowerBoundSources.equals(boundSources(sourceBatch, previousFeedback))
                    || !moves.equals(
                            SixMaxRootSequenceBatchFeedback.moves(
                                    lowerBoundSources, request.maximumLossBb()))
                    || !policies.equals(checks(sourceBatch, moves))
                    || !previousMenuComparison.equals(compare(previousFeedback, moves))
                    || !rejectionReasons.equals(reasons(sourceBatch, moves, policies))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException(
                        "Complete source-bound batch feedback derivation differs");
        }
    }

    public static final class QualifiedFeedback {
        private final String reportHash;
        private final String sourceBatchHash;

        private QualifiedFeedback(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
            sourceBatchHash = SixMaxHeadsUpPreflopGame.hash(report.sourceBatch());
        }

        public String reportHash() {
            return reportHash;
        }

        public String sourceBatchHash() {
            return sourceBatchHash;
        }
    }

    public static final class Result {
        private final SixMaxRootSequenceBatchStudy.Result ownedBatch;
        private final Report report;
        private final QualifiedFeedback qualified;

        private Result(
                Request request,
                SixMaxRootSequenceBatchStudy.Result owned,
                SixMaxRootSequenceFeedback.Report previous)
                throws Exception {
            ownedBatch = Objects.requireNonNull(owned);
            var source = ownedBatch.report();
            var inputs = boundSources(source, previous);
            var moves = moves(inputs, request.maximumLossBb());
            var policies = checks(source, moves);
            var reasons = reasons(source, moves, policies);
            report =
                    new Report(
                            REPORT_SCHEMA,
                            STATUS,
                            false,
                            SCOPE,
                            QUALIFICATION_SCOPE,
                            request,
                            source,
                            previous,
                            inputs,
                            moves,
                            policies,
                            compare(previous, moves),
                            reasons.isEmpty(),
                            reasons);
            qualified = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(qualified);
        }
    }

    private record Captured(
            Path path,
            byte[] stored,
            long size,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxRootSequenceBatchFeedback() {}

    private static void lineage(
            Request request,
            SixMaxRootSequenceBatchStudy.Report batch,
            SixMaxRootSequenceFeedback.Report previous) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(batch);
        Objects.requireNonNull(previous);
        if (!canonical(batch).equals(BATCH_CANONICAL)
                || !canonical(previous).equals(PREVIOUS_CANONICAL)
                || !request.batchRequest().equals(batch.request())
                || !batch.completeMinima()
                || !batch.referenceSupportedMinima()
                || !batch.rejectionReasons().isEmpty()
                || !previous.qualifiedForOfflineDrill()
                || !previous.rejectionReasons().isEmpty()
                || !batch.previousMinimum().equals(previous.sourceMinimum())
                || !batch.originalEnvelope()
                        .equals(previous.originalEnvelope().envelope().envelope())
                || !batch.parent().equals(previous.originalEnvelope().envelope().parent())
                || !batch.request().sourceRequest().equals(previous.originalEnvelope().request())
                || !batch.policies().stream()
                        .map(p -> p.original())
                        .toList()
                        .equals(
                                previous.sourceMinimum().policies().stream()
                                        .map(p -> p.original())
                                        .toList()))
            throw new IllegalArgumentException(
                    "Complete pinned original batch and previous feedback lineage required before qualification");
    }

    static List<BoundSource> boundSources(
            SixMaxRootSequenceBatchStudy.Report batch, SixMaxRootSequenceFeedback.Report previous) {
        if (!batch.completeMinima() || batch.batch() == null)
            throw new IllegalArgumentException("Complete original batch required");
        var originals = previous.originalEnvelope().moves();
        if (!originals.stream()
                        .map(m -> m.action())
                        .toList()
                        .equals(SixMaxRootSequenceBatchStudy.ACTIONS)
                || !batch.batch().minima().stream()
                        .map(a -> a.question().action())
                        .toList()
                        .equals(SixMaxRootSequenceBatchStudy.ACTIONS))
            throw new IllegalArgumentException("Complete original action order required");
        var out = new ArrayList<BoundSource>();
        for (int i = 0; i < 3; i++) {
            var original = originals.get(i);
            var audit = batch.batch().minima().get(i);
            double candidate = audit.minimum().lowerBound(),
                    selected = Math.max(original.lowerEvBb(), candidate);
            if (!Double.isFinite(candidate)
                    || !Double.isFinite(original.lowerEvBb())
                    || !Double.isFinite(original.upperEvBb())
                    || selected > original.upperEvBb())
                throw new IllegalArgumentException(
                        "Consistent finite original outer bounds required");
            out.add(
                    new BoundSource(
                            original.action(),
                            original.lowerEvBb(),
                            candidate,
                            selected,
                            original.upperEvBb()));
        }
        return List.copyOf(out);
    }

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(List<BoundSource> inputs, double limit) {
        if (limit != .01
                || !inputs.stream()
                        .map(b -> b.action())
                        .toList()
                        .equals(SixMaxRootSequenceBatchStudy.ACTIONS))
            throw new IllegalArgumentException(
                    "Complete ordered original menu and loss threshold required");
        var moves = new ArrayList<SixMaxSuppliedIntervalFeedback.Move>();
        for (var b : inputs) {
            double lowLoss = 0, highLoss = 0;
            for (var other : inputs) {
                lowLoss =
                        Math.max(lowLoss, other.selectedLowerBb() - b.originalFullActionUpperBb());
                highLoss =
                        Math.max(highLoss, other.originalFullActionUpperBb() - b.selectedLowerBb());
            }
            moves.add(
                    new SixMaxSuppliedIntervalFeedback.Move(
                            b.action(),
                            b.selectedLowerBb(),
                            b.originalFullActionUpperBb(),
                            lowLoss,
                            highLoss,
                            SixMaxSuppliedIntervalFeedback.classify(lowLoss, highLoss, limit)));
        }
        return List.copyOf(moves);
    }

    static PolicyCheck assess(
            SixMaxRootSequenceBatchStudy.PolicyAssessment policy,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        var original = policy.original();
        var failures = new TreeSet<>(policy.failures());
        if (!policy.allMinimumLowerBoundsSupported()) failures.add("BATCH_MINIMA_NOT_SUPPORTED");
        var actions = new HashSet<>(moves.stream().map(m -> m.action()).toList());
        if (moves.size() != 3
                || actions.size() != 3
                || !actions.equals(original.actionUtilitiesBb().keySet())
                || !actions.equals(original.actionLossBb().keySet()))
            throw new IllegalArgumentException("All original policy moves required");
        Double violation = null;
        int checked = 0;
        if (original.insideDeclaredFace()) {
            violation = 0.;
            for (var m : moves) {
                double value = original.actionUtilitiesBb().get(m.action()),
                        loss = original.actionLossBb().get(m.action());
                if (!Double.isFinite(value) || !Double.isFinite(loss))
                    throw new IllegalArgumentException(
                            "Finite original policy utility/loss required");
                violation =
                        Math.max(
                                violation,
                                Math.max(
                                        Math.max(m.lowerEvBb() - value, value - m.upperEvBb()),
                                        Math.max(m.lowerLossBb() - loss, loss - m.upperLossBb())));
                checked += 4;
            }
            if (violation > EPS) failures.add("REFERENCE_OUTSIDE_BATCH_MENU_BOUNDS");
        } else failures.add("BATCH_MENU_REFERENCE_OUTSIDE_FACE");
        return new PolicyCheck(
                original.iterations(),
                original.solutionHash(),
                policy.allMinimumLowerBoundsSupported(),
                policy.minimumLowerViolationsBb().size(),
                violation != null,
                checked,
                violation,
                List.copyOf(failures));
    }

    private static List<PolicyCheck> checks(
            SixMaxRootSequenceBatchStudy.Report source,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        return source.policies().stream().map(p -> assess(p, moves)).toList();
    }

    static List<PreviousMenuComparison> compare(
            SixMaxRootSequenceFeedback.Report previous,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        var out = new ArrayList<PreviousMenuComparison>();
        if (previous.moves().size() != moves.size())
            throw new IllegalArgumentException("Complete previous menu required");
        for (int i = 0; i < moves.size(); i++) {
            var before = previous.moves().get(i);
            var after = moves.get(i);
            if (!before.action().equals(after.action()))
                throw new IllegalArgumentException("Complete ordered comparison required");
            out.add(
                    new PreviousMenuComparison(
                            after.action(),
                            before,
                            after.lowerEvBb() - before.lowerEvBb(),
                            after.upperEvBb() - before.upperEvBb(),
                            after.lowerLossBb() - before.lowerLossBb(),
                            after.upperLossBb() - before.upperLossBb(),
                            after.classification() == before.classification()));
        }
        return List.copyOf(out);
    }

    static List<String> reasons(
            SixMaxRootSequenceBatchStudy.Report source,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<PolicyCheck> checks) {
        var out = new TreeSet<String>();
        if (!source.referenceSupportedMinima()) out.add("BATCH_NOT_REFERENCE_SUPPORTED");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            out.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            out.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        for (var p : checks)
            for (var f : p.failures())
                out.add((p.iterations() == 1 ? "PRIMARY" : p.iterations()) + ":" + f);
        return List.copyOf(out);
    }

    public static Result solve(
            Request request,
            Path envelopePath,
            Path minimumPath,
            Path batchPath,
            Path previousFeedbackPath)
            throws Exception {
        Objects.requireNonNull(request);
        SixMaxTexturePayoffTableMain.distinct(
                List.of(envelopePath, minimumPath, batchPath, previousFeedbackPath));
        var capturedBatch = capture(batchPath, BATCH_SHA);
        var capturedPrevious = capture(previousFeedbackPath, PREVIOUS_SHA);
        var saved = decode(capturedBatch, SixMaxRootSequenceBatchStudy.Report.class);
        var previous = decode(capturedPrevious, SixMaxRootSequenceFeedback.Report.class);
        lineage(request, saved, previous);
        var owned =
                SixMaxRootSequenceBatchStudy.solve(
                        request.batchRequest(), envelopePath, minimumPath);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete original physical batch replay differs before qualification");
        verify(capturedBatch);
        verify(capturedPrevious);
        return new Result(request, owned, previous);
    }

    // No paths are accessed in this entry point; the loader owns capture/integrity checking.
    static Result solve(
            Request request,
            SixMaxRootEnvelopeFeedback.Report envelope,
            SixMaxRootSequenceMinimumStudy.Report minimum,
            SixMaxRootSequenceBatchStudy.Report saved,
            SixMaxRootSequenceFeedback.Report previous)
            throws Exception {
        lineage(request, saved, previous);
        if (!saved.previousMinimum().equals(minimum)
                || !saved.parent().equals(envelope.envelope().parent())
                || !saved.originalEnvelope().equals(envelope.envelope().envelope()))
            throw new IllegalArgumentException("Captured batch source lineage differs");
        var owned = SixMaxRootSequenceBatchStudy.solve(request.batchRequest(), envelope, minimum);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete captured physical batch replay differs");
        return new Result(request, owned, previous);
    }

    private static String canonical(Object value) {
        try {
            return SixMaxHeadsUpPreflopGame.hash(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Canonical source identity failed", e);
        }
    }

    private static Captured capture(Path path, String pin) throws Exception {
        path = path.toAbsolutePath().normalize();
        var a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile() || Files.isSymbolicLink(path) || a.size() > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular pinned evidence required");
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES
                || !HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                        .equals(pin))
            throw new IllegalArgumentException("Original stored evidence byte pin differs");
        return new Captured(path, bytes, a.size(), a.fileKey(), a.lastModifiedTime());
    }

    private static <T> T decode(Captured captured, Class<T> type) throws Exception {
        byte[] raw;
        try (var zip = new GZIPInputStream(new ByteArrayInputStream(captured.stored()))) {
            raw = zip.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded evidence byte cap exceeded");
        return SixMaxTexturePayoffTable.mapper().readValue(raw, type);
    }

    private static void verify(Captured before) throws Exception {
        var after =
                capture(
                        before.path(),
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(before.stored())));
        if (after.size() != before.size()
                || !Objects.equals(after.fileKey(), before.fileKey())
                || !after.modified().equals(before.modified())
                || !Arrays.equals(after.stored(), before.stored()))
            throw new IllegalArgumentException(
                    "Captured evidence changed during owned physical replay");
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        return decode(capture(path, PUBLISHED_REPORT_SHA), Report.class);
    }

    public static void write(Path path, Result owned) throws Exception {
        try (var stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            write(stream, owned);
        }
    }

    static void write(OutputStream stream, Result owned) throws Exception {
        Objects.requireNonNull(owned);
        byte[] raw = SixMaxTextureStudy.json(owned.report()).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded feedback byte cap exceeded");
        var buffer = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(buffer)) {
            zip.write(raw);
        }
        if (buffer.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored feedback byte cap exceeded");
        stream.write(buffer.toByteArray());
    }

    public static Result replay(
            Path requestPath,
            Path envelopePath,
            Path minimumPath,
            Path batchPath,
            Path previousPath,
            Path reportPath)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(
                        requestPath,
                        envelopePath,
                        minimumPath,
                        batchPath,
                        previousPath,
                        reportPath));
        var request = readRequest(requestPath);
        var saved = readReport(reportPath);
        if (!saved.request().equals(request))
            throw new IllegalArgumentException("Original feedback request differs");
        var owned = solve(request, envelopePath, minimumPath, batchPath, previousPath);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete batch feedback replay differs");
        return owned;
    }
}
