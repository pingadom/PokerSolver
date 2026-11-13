package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** Complete-menu offline qualification from the owned original two-hand raise batch. */
public final class SixMaxRootHandBankRaiseFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-bank-raise-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-bank-raise-feedback-report/v1";
    public static final String STATUS = "BANK_RAISE_MINIMA_TWO_HAND_FEEDBACK_RESEARCH_ONLY";
    public static final String SCOPE = SixMaxRootSequenceFeedback.SCOPE;
    public static final String QUALIFICATION_SCOPE =
            "ORIGINAL_BB77_ACJC_COMPLETE_MENU_RAISE_MINIMA_OFFLINE_ONLY/v1";
    public static final String SOURCE_SHA =
            "e01215a5da6ee3ffe3ea4e427c6777ad211881bdbd7ef970c3abb1a9d74df034";
    public static final String SOURCE_HASH =
            "73e4fcde12e37749fa11278c143830f72f16378802affcd9fa5980e26fc8a8be";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;
    private static final List<String> ACTIONS = List.of("fold", "call", "raise:9.0");

    public record Request(
            String schemaVersion,
            SixMaxRootHandBankRaiseStudy.Request minimumRequest,
            String minimumStoredSha256,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || minimumRequest == null
                    || !SOURCE_SHA.equals(minimumStoredSha256)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Original two-hand minima and unchanged loss threshold required");
        }
    }

    public record BoundSource(
            String action,
            double originalLowerBb,
            Double sequenceMinimumLowerBb,
            double selectedLowerBb,
            double originalFullActionUpperBb) {}

    public record PolicyCheck(
            int iterations,
            String solutionHash,
            boolean sourceMinimumSupported,
            int minimumLowerChecks,
            boolean boundsChecked,
            int utilityLossInequalitiesChecked,
            Double maximumBoundViolationBb,
            List<String> failures) {
        public PolicyCheck {
            failures = List.copyOf(failures);
        }
    }

    public record Comparison(
            String action,
            SixMaxSuppliedIntervalFeedback.Move previousMove,
            double lowerEvDifferenceBb,
            double upperEvDifferenceBb,
            double lowerLossDifferenceBb,
            double upperLossDifferenceBb,
            boolean classificationUnchanged) {}

    public record Assessment(
            int sourceQuestionIndex,
            String heroCards,
            SixMaxSizeMenuPreflopPlan.Table context,
            List<BoundSource> lowerBoundSources,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<PolicyCheck> policies,
            List<Comparison> previousMenuComparison,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Assessment {
            lowerBoundSources = List.copyOf(lowerBoundSources);
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            previousMenuComparison = List.copyOf(previousMenuComparison);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String boundsScope,
            String qualificationScope,
            Request request,
            SixMaxRootHandBankRaiseStudy.Report sourceMinimum,
            List<Assessment> assessments,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            assessments = List.copyOf(assessments);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SCOPE.equals(boundsScope)
                    || !QUALIFICATION_SCOPE.equals(qualificationScope))
                throw new IllegalArgumentException(
                        "Original two-hand offline feedback scope required");
            lineage(request, sourceMinimum);
            var expected =
                    SixMaxRootHandBankRaiseFeedback.assessments(
                            sourceMinimum, request.maximumLossBb());
            var reasons = aggregateReasons(expected);
            if (!assessments.equals(expected)
                    || !rejectionReasons.equals(reasons)
                    || qualifiedForOfflineDrill != reasons.isEmpty())
                throw new IllegalArgumentException(
                        "Complete original-hand feedback and qualification inventory differs");
        }
    }

    /** Only an owned physical replay that passes every per-hand gate creates this capability. */
    public static final class QualifiedFeedback {
        private final String reportHash, sourceMinimumHash;

        private QualifiedFeedback(Report report) {
            reportHash = canonical(report);
            sourceMinimumHash = canonical(report.sourceMinimum());
        }

        public String reportHash() {
            return reportHash;
        }

        public String sourceMinimumHash() {
            return sourceMinimumHash;
        }
    }

    public static final class Result {
        private final SixMaxRootHandBankRaiseStudy.Result ownedMinimum;
        private final Report report;
        private final QualifiedFeedback qualified;

        private Result(Request request, SixMaxRootHandBankRaiseStudy.Result owned) {
            ownedMinimum = Objects.requireNonNull(owned);
            var source = ownedMinimum.report();
            lineage(request, source);
            var assessments = assessments(source, request.maximumLossBb());
            var reasons = aggregateReasons(assessments);
            report =
                    new Report(
                            REPORT_SCHEMA,
                            STATUS,
                            false,
                            SCOPE,
                            QUALIFICATION_SCOPE,
                            request,
                            source,
                            assessments,
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
            Path realPath,
            byte[] bytes,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxRootHandBankRaiseFeedback() {}

    private static void lineage(Request request, SixMaxRootHandBankRaiseStudy.Report source) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(source);
        if (!SOURCE_HASH.equals(canonical(source))
                || !request.minimumRequest().equals(source.request())
                || !source.completeMinima()
                || !source.referenceSupportedMinima()
                || !source.rejectionReasons().isEmpty())
            throw new IllegalArgumentException(
                    "Complete original pinned and reference-supported minima required before qualification");
    }

    static List<BoundSource> boundSources(
            SixMaxRootHandBankStudy.Assessment original, double raiseMinimum) {
        if (!Double.isFinite(raiseMinimum)
                || !original.moves().stream()
                        .map(SixMaxSuppliedIntervalFeedback.Move::action)
                        .toList()
                        .equals(ACTIONS))
            throw new IllegalArgumentException(
                    "Finite raise minimum and complete original menu required");
        var result = new ArrayList<BoundSource>();
        for (var move : original.moves()) {
            Double candidate = move.action().equals("raise:9.0") ? raiseMinimum : null;
            double selected =
                    candidate == null ? move.lowerEvBb() : Math.max(move.lowerEvBb(), candidate);
            if (!Double.isFinite(move.lowerEvBb())
                    || !Double.isFinite(move.upperEvBb())
                    || selected > move.upperEvBb())
                throw new IllegalArgumentException(
                        "Consistent finite full-action outer bounds required");
            result.add(
                    new BoundSource(
                            move.action(),
                            move.lowerEvBb(),
                            candidate,
                            selected,
                            move.upperEvBb()));
        }
        return List.copyOf(result);
    }

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(List<BoundSource> inputs, double limit) {
        if (limit != .01 || !inputs.stream().map(BoundSource::action).toList().equals(ACTIONS))
            throw new IllegalArgumentException(
                    "Complete ordered menu and original loss threshold required");
        var result = new ArrayList<SixMaxSuppliedIntervalFeedback.Move>();
        for (var input : inputs) {
            double low = 0, high = 0;
            for (var other : inputs) {
                low = Math.max(low, other.selectedLowerBb() - input.originalFullActionUpperBb());
                high = Math.max(high, other.originalFullActionUpperBb() - input.selectedLowerBb());
            }
            result.add(
                    new SixMaxSuppliedIntervalFeedback.Move(
                            input.action(),
                            input.selectedLowerBb(),
                            input.originalFullActionUpperBb(),
                            low,
                            high,
                            SixMaxSuppliedIntervalFeedback.classify(low, high, limit)));
        }
        return List.copyOf(result);
    }

    static PolicyCheck assess(
            RootEnvelopePolicyChecks.Check original,
            SixMaxRootHandBankRaiseStudy.PolicySupport source,
            List<SixMaxSuppliedIntervalFeedback.Move> menu) {
        if (source.iterations() != original.iterations()
                || !source.solutionHash().equals(original.solutionHash())
                || !menu.stream()
                        .map(SixMaxSuppliedIntervalFeedback.Move::action)
                        .toList()
                        .equals(ACTIONS)
                || !original.actionUtilitiesBb().keySet().equals(new HashSet<>(ACTIONS))
                || !original.actionLossBb().keySet().equals(new HashSet<>(ACTIONS)))
            throw new IllegalArgumentException(
                    "Original policy identity and complete menu required");
        var failures = new TreeSet<>(original.failures());
        if (!source.supported()) failures.add("RAISE_MINIMUM_NOT_SUPPORTED");
        Double violation = null;
        int checked = 0;
        if (original.insideDeclaredFace()) {
            violation = 0.;
            for (var move : menu) {
                double value = original.actionUtilitiesBb().get(move.action()),
                        loss = original.actionLossBb().get(move.action());
                if (!Double.isFinite(value) || !Double.isFinite(loss))
                    throw new IllegalArgumentException("Finite original policy values required");
                violation =
                        Math.max(
                                violation,
                                Math.max(
                                        Math.max(
                                                move.lowerEvBb() - value, value - move.upperEvBb()),
                                        Math.max(
                                                move.lowerLossBb() - loss,
                                                loss - move.upperLossBb())));
                checked += 4;
            }
            if (violation > EPS) failures.add("REFERENCE_OUTSIDE_BANK_MENU_BOUNDS");
        } else failures.add("BANK_MENU_REFERENCE_OUTSIDE_FACE");
        return new PolicyCheck(
                original.iterations(),
                original.solutionHash(),
                source.supported(),
                1,
                violation != null,
                checked,
                violation,
                List.copyOf(failures));
    }

    static List<Comparison> compare(
            List<SixMaxSuppliedIntervalFeedback.Move> original,
            List<SixMaxSuppliedIntervalFeedback.Move> menu) {
        if (original.size() != 3 || menu.size() != 3)
            throw new IllegalArgumentException("Complete previous and new menus required");
        var result = new ArrayList<Comparison>();
        for (int i = 0; i < 3; i++) {
            var before = original.get(i);
            var after = menu.get(i);
            if (!before.action().equals(after.action()))
                throw new IllegalArgumentException("Original action order required");
            result.add(
                    new Comparison(
                            after.action(),
                            before,
                            after.lowerEvBb() - before.lowerEvBb(),
                            after.upperEvBb() - before.upperEvBb(),
                            after.lowerLossBb() - before.lowerLossBb(),
                            after.upperLossBb() - before.upperLossBb(),
                            after.classification() == before.classification()));
        }
        return List.copyOf(result);
    }

    static List<Assessment> assessments(SixMaxRootHandBankRaiseStudy.Report source, double limit) {
        var result = new ArrayList<Assessment>();
        for (int i = 0; i < 2; i++) {
            int index = SixMaxRootHandBankRaiseStudy.INDICES.get(i);
            var original = source.sourceBank().assessments().get(index);
            var inputs =
                    boundSources(original, source.batch().minima().get(i).minimum().lowerBound());
            var menu = moves(inputs, limit);
            var checks = new ArrayList<PolicyCheck>();
            for (int j = 0; j < 3; j++) {
                var support = source.policySupports().get(i * 3 + j);
                if (support.sourceQuestionIndex() != index)
                    throw new IllegalArgumentException(
                            "Source policy belongs to another private hand");
                checks.add(assess(original.policies().get(j), support, menu));
            }
            var reasons =
                    new TreeSet<>(
                            RootEnvelopePolicyChecks.reasons(
                                    menu,
                                    original.policies().getFirst(),
                                    original.policies().subList(1, 3)));
            for (var policy : checks)
                for (String failure : policy.failures())
                    reasons.add(
                            (policy.iterations() == 1 ? "PRIMARY" : policy.iterations())
                                    + ":"
                                    + failure);
            result.add(
                    new Assessment(
                            index,
                            original.heroCards(),
                            original.context(),
                            inputs,
                            menu,
                            checks,
                            compare(original.moves(), menu),
                            reasons.isEmpty(),
                            List.copyOf(reasons)));
        }
        return List.copyOf(result);
    }

    private static List<String> aggregateReasons(List<Assessment> assessments) {
        var result = new TreeSet<String>();
        for (var a : assessments)
            for (String reason : a.rejectionReasons())
                result.add(a.sourceQuestionIndex() + ":" + reason);
        return List.copyOf(result);
    }

    public static Result solve(Request request, Path bankPath, Path minimumPath) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(bankPath, minimumPath));
        var captured = capture(minimumPath);
        var saved = decode(captured.bytes(), SixMaxRootHandBankRaiseStudy.Report.class);
        lineage(request, saved);
        var owned = SixMaxRootHandBankRaiseStudy.solve(request.minimumRequest(), bankPath);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException(
                    "Complete original two-hand minimum replay differs before qualification");
        verify(captured);
        return new Result(request, owned);
    }

    private static String canonical(Object value) {
        try {
            return SixMaxHeadsUpPreflopGame.hash(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Canonical feedback identity failed", e);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Captured capture(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile() || Files.isSymbolicLink(path) || a.size() > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular pinned minimum required");
        byte[] bytes;
        try (var in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES || !SOURCE_SHA.equals(sha(bytes)))
            throw new IllegalArgumentException("Original minimum stored bytes differ");
        return new Captured(path, path.toRealPath(), bytes, a.fileKey(), a.lastModifiedTime());
    }

    private static <T> T decode(byte[] bytes, Class<T> type) throws Exception {
        byte[] raw;
        try (var in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            raw = in.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded feedback/source cap exceeded");
        return SixMaxTexturePayoffTable.mapper().readValue(raw, type);
    }

    private static void verify(Captured source) throws Exception {
        var now = capture(source.path());
        if (!source.realPath().equals(now.realPath())
                || !Objects.equals(source.fileKey(), now.fileKey())
                || !source.modified().equals(now.modified())
                || !Arrays.equals(source.bytes(), now.bytes()))
            throw new IllegalArgumentException("Captured minimum changed during owned replay");
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, 128 * 1024), Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || Files.size(path) > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular feedback report required");
        byte[] bytes;
        try (var in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored feedback report cap exceeded");
        return decode(bytes, Report.class);
    }

    static void write(OutputStream output, Result owned) throws Exception {
        Objects.requireNonNull(owned);
        byte[] raw = SixMaxTextureStudy.json(owned.report()).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded feedback cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored feedback cap exceeded");
        output.write(bytes.toByteArray());
    }

    public static Result replay(Path request, Path bank, Path minimum, Path report)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(request, bank, minimum, report));
        var q = readRequest(request);
        var saved = readReport(report);
        if (!saved.request().equals(q))
            throw new IllegalArgumentException("Published feedback request differs");
        var owned = solve(q, bank, minimum);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete owned feedback replay differs");
        return owned;
    }
}
