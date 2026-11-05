package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Complete-menu qualification derived from an owned, exactly reproduced root minimum. */
public final class SixMaxRootSequenceFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-root-sequence-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-sequence-feedback-report/v1";
    public static final String STATUS = "SEQUENCE_MINIMUM_COMPLETE_MENU_FEEDBACK_RESEARCH_ONLY";
    public static final String SCOPE =
            "COMPLETE_MENU_TERMINAL_PROFIT_OUTER_BOUNDS_NOT_ATTAINED_ENDPOINTS/v1";
    public static final String MINIMUM_SHA =
            "1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f";
    public static final int MAX_BYTES = SixMaxRootSequenceMinimumStudy.MAX_BYTES;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Request(
            String schemaVersion,
            SixMaxRootSequenceMinimumStudy.Request minimumRequest,
            String minimumStoredSha256,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || minimumRequest == null
                    || !MINIMUM_SHA.equals(minimumStoredSha256)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Original minimum and unchanged loss threshold required");
        }
    }

    public record PolicyCheck(
            int iterations,
            String solutionHash,
            boolean boundsChecked,
            Double maximumBoundViolationBb,
            List<String> failures) {
        public PolicyCheck {
            failures = List.copyOf(failures);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String boundsScope,
            Request request,
            SixMaxRootEnvelopeFeedback.Report originalEnvelope,
            SixMaxRootSequenceMinimumStudy.Report sourceMinimum,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<PolicyCheck> policies,
            List<MenuComparison> legacyMenuComparison,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
            legacyMenuComparison = List.copyOf(legacyMenuComparison);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SCOPE.equals(boundsScope)
                    || request == null
                    || originalEnvelope == null
                    || sourceMinimum == null
                    || !request.minimumRequest().equals(sourceMinimum.request())
                    || !sourceMinimum.request().sourceRequest().equals(originalEnvelope.request())
                    || !sourceMinimum.parent().equals(originalEnvelope.envelope().parent())
                    || !sourceMinimum.completeMinimum()
                    || !originalEnvelope.qualifiedForOfflineDrill()
                    || !sourceMinimum.policies().stream()
                            .map(p -> p.original())
                            .toList()
                            .equals(originalPolicies(originalEnvelope))
                    || !moves.equals(
                            SixMaxRootSequenceFeedback.moves(
                                    sourceMinimum, originalEnvelope, request.maximumLossBb()))
                    || !policies.equals(checks(sourceMinimum, moves))
                    || !legacyMenuComparison.equals(compare(sourceMinimum, originalEnvelope, moves))
                    || !rejectionReasons.equals(reasons(sourceMinimum, moves, policies))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException(
                        "Owned complete-menu minimum feedback identity differs");
        }
    }

    /** New-minus-legacy outer-bound changes, not a new endpoint certificate. */
    public record MenuComparison(
            String action,
            SixMaxSuppliedIntervalFeedback.Move legacyMove,
            double lowerEvDifferenceBb,
            double upperEvDifferenceBb,
            double lowerLossDifferenceBb,
            double upperLossDifferenceBb,
            boolean classificationUnchanged) {}

    static List<MenuComparison> compare(
            SixMaxRootSequenceMinimumStudy.Report minimum,
            SixMaxRootEnvelopeFeedback.Report original,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        var legacy = SixMaxRootMinimumFeedback.moves(minimum.legacyMinimum(), original, .01);
        var comparison = new ArrayList<MenuComparison>();
        for (int i = 0; i < legacy.size(); i++) {
            var before = legacy.get(i);
            var after = moves.get(i);
            if (!before.action().equals(after.action()))
                throw new IllegalArgumentException("Complete ordered comparison menu required");
            comparison.add(
                    new MenuComparison(
                            after.action(),
                            before,
                            after.lowerEvBb() - before.lowerEvBb(),
                            after.upperEvBb() - before.upperEvBb(),
                            after.lowerLossBb() - before.lowerLossBb(),
                            after.upperLossBb() - before.upperLossBb(),
                            after.classification() == before.classification()));
        }
        return List.copyOf(comparison);
    }

    public static final class QualifiedFeedback {
        private final String reportHash;

        private QualifiedFeedback(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
        }

        public String reportHash() {
            return reportHash;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedFeedback qualified;

        private Result(Report report) throws Exception {
            this.report = report;
            qualified = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(qualified);
        }
    }

    private SixMaxRootSequenceFeedback() {}

    private static List<RootEnvelopePolicyChecks.Check> originalPolicies(
            SixMaxRootEnvelopeFeedback.Report source) {
        var checks = new ArrayList<>(List.of(source.primary()));
        checks.addAll(source.references());
        return List.copyOf(checks);
    }

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(
            SixMaxRootSequenceMinimumStudy.Report minimum,
            SixMaxRootEnvelopeFeedback.Report source,
            double limit) {
        if (!minimum.completeMinimum())
            throw new IllegalArgumentException("Complete minimum required");
        var original = source.moves();
        if (!original.stream()
                .map(m -> m.action())
                .toList()
                .equals(List.of("fold", "call", "raise:9.0")))
            throw new IllegalArgumentException("Complete original move menu required");
        var lower = new LinkedHashMap<String, Double>();
        var upper = new LinkedHashMap<String, Double>();
        for (var m : original) {
            double value =
                    m.action().equals(minimum.request().action())
                            ? Math.max(m.lowerEvBb(), minimum.minimum().minimum().lowerBound())
                            : m.lowerEvBb();
            if (!Double.isFinite(value) || !Double.isFinite(m.upperEvBb()) || value > m.upperEvBb())
                throw new IllegalArgumentException("Consistent finite outer bounds required");
            lower.put(m.action(), value);
            upper.put(m.action(), m.upperEvBb());
        }
        return original.stream()
                .map(
                        m -> {
                            double lo = 0, hi = 0;
                            for (var action : lower.keySet()) {
                                lo = Math.max(lo, lower.get(action) - upper.get(m.action()));
                                hi = Math.max(hi, upper.get(action) - lower.get(m.action()));
                            }
                            return new SixMaxSuppliedIntervalFeedback.Move(
                                    m.action(),
                                    lower.get(m.action()),
                                    upper.get(m.action()),
                                    lo,
                                    hi,
                                    SixMaxSuppliedIntervalFeedback.classify(lo, hi, limit));
                        })
                .toList();
    }

    static PolicyCheck assess(
            SixMaxRootSequenceMinimumStudy.PolicyAssessment policy,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        var original = policy.original();
        var failures = new TreeSet<>(policy.failures());
        if (!policy.minimumLowerSupported()) failures.add("MINIMUM_NOT_SUPPORTED");
        var actions = new HashSet<>(moves.stream().map(m -> m.action()).toList());
        if (moves.size() != 3
                || actions.size() != 3
                || !actions.equals(original.actionUtilitiesBb().keySet())
                || !actions.equals(original.actionLossBb().keySet()))
            throw new IllegalArgumentException("All policy moves required");
        Double violation = null;
        if (original.insideDeclaredFace()) {
            violation = 0.;
            for (var m : moves) {
                double value = original.actionUtilitiesBb().get(m.action()),
                        loss = original.actionLossBb().get(m.action());
                if (!Double.isFinite(value) || !Double.isFinite(loss))
                    throw new IllegalArgumentException("Finite policy values required");
                violation =
                        Math.max(
                                violation,
                                Math.max(
                                        Math.max(m.lowerEvBb() - value, value - m.upperEvBb()),
                                        Math.max(m.lowerLossBb() - loss, loss - m.upperLossBb())));
            }
            if (violation > EPS) failures.add("REFERENCE_OUTSIDE_MINIMUM_MENU_BOUNDS");
        } else failures.add("MINIMUM_MENU_REFERENCE_OUTSIDE_FACE");
        return new PolicyCheck(
                original.iterations(),
                original.solutionHash(),
                violation != null,
                violation,
                List.copyOf(failures));
    }

    private static List<PolicyCheck> checks(
            SixMaxRootSequenceMinimumStudy.Report source,
            List<SixMaxSuppliedIntervalFeedback.Move> moves) {
        return source.policies().stream().map(p -> assess(p, moves)).toList();
    }

    private static List<String> reasons(
            SixMaxRootSequenceMinimumStudy.Report source,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<PolicyCheck> policies) {
        var reasons = new TreeSet<String>();
        if (!source.referenceSupportedMinimum()) reasons.add("MINIMUM_NOT_REFERENCE_SUPPORTED");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            reasons.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            reasons.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        for (var p : policies)
            for (var f : p.failures())
                reasons.add((p.iterations() == 1 ? "PRIMARY" : p.iterations()) + ":" + f);
        return List.copyOf(reasons);
    }

    private static Result qualify(
            Request request,
            SixMaxRootSequenceMinimumStudy.Result owned,
            SixMaxRootEnvelopeFeedback.Report original)
            throws Exception {
        var source = owned.report();
        var moves = moves(source, original, request.maximumLossBb());
        var policies = checks(source, moves);
        var reasons = reasons(source, moves, policies);
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SCOPE,
                        request,
                        original,
                        source,
                        moves,
                        policies,
                        compare(source, original, moves),
                        reasons.isEmpty(),
                        reasons));
    }

    public static Result solve(
            Request request, Path envelopePath, Path legacyPath, Path minimumPath)
            throws Exception {
        Objects.requireNonNull(request);
        SixMaxTexturePayoffTableMain.distinct(List.of(envelopePath, legacyPath, minimumPath));
        var saved = readMinimum(minimumPath);
        var original = SixMaxRootMinimumStudy.readSource(envelopePath);
        if (!request.minimumRequest().equals(saved.request())
                || !saved.parent().equals(original.envelope().parent())
                || !saved.request().sourceRequest().equals(original.request()))
            throw new IllegalArgumentException(
                    "Pinned sequence minimum and original source differ");
        var owned =
                SixMaxRootSequenceMinimumStudy.solve(
                        request.minimumRequest(), envelopePath, legacyPath);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete original sequence minimum replay differs");
        return qualify(request, owned, original);
    }

    static SixMaxRootSequenceMinimumStudy.Report readMinimum(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Regular pinned minimum source required");
        byte[] raw;
        try (var input = Files.newInputStream(path)) {
            raw = input.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES
                || !MINIMUM_SHA.equals(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))
            throw new IllegalArgumentException("Original minimum stored byte pin differs");
        byte[] expanded;
        try (var zip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
            expanded = zip.readNBytes(MAX_BYTES + 1);
        }
        if (expanded.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded minimum byte cap exceeded");
        return SixMaxTexturePayoffTable.mapper()
                .readValue(expanded, SixMaxRootSequenceMinimumStudy.Report.class);
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded feedback byte cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored feedback byte cap exceeded");
        path = path.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path envelope, Path legacy, Path minimum, Path report)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(input, envelope, legacy, minimum, report));
        var request = readRequest(input);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(report, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Feedback request differs");
        var owned = solve(request, envelope, legacy, minimum);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete feedback replay differs");
        return owned;
    }
}
