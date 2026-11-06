package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** One declared physical complete-menu batch; its minima do not grant trainer admission. */
public final class SixMaxRootSequenceBatchStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-physical-root-sequence-batch-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-physical-root-sequence-batch-report/v1";
    public static final String STATUS = "PHYSICAL_ROOT_SEQUENCE_COMPLETE_MENU_MINIMA_RESEARCH_ONLY";
    public static final String SOURCE_HASH = SixMaxRootSequenceMinimumStudy.SOURCE_HASH;
    public static final String PRIOR_STORED_SHA =
            "1173300b55be57fd93c68ff02c04cd7318511601b41f1c3839f5e57b2d166b0f";
    public static final String PRIOR_HASH =
            "231336105c0f6f477a7a81d43fd0b57b29adbe83c2cf0531a7ce5412702b1fce";
    public static final String PUBLISHED_REPORT_SHA =
            "be3e7400a75fd86bb617cf4e70f758e2a885d80ba052e6441f28d81c61a0dc8b";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final List<String> ACTIONS = List.of("fold", "call", "raise:9.0");
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Request(
            String schemaVersion,
            SixMaxRootEnvelopeFeedback.Request sourceRequest,
            String sourceReportHash,
            String previousMinimumStoredSha256,
            List<String> actions) {
        public Request {
            actions = List.copyOf(actions);
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || sourceRequest == null
                    || !SOURCE_HASH.equals(sourceReportHash)
                    || !PRIOR_STORED_SHA.equals(previousMinimumStoredSha256)
                    || !ACTIONS.equals(actions)
                    || sourceRequest.envelope().hero() != 5
                    || !"9d 9h".equals(sourceRequest.envelope().heroCards())
                    || sourceRequest.maximumLossBb() != .01)
                throw new IllegalArgumentException(
                        "Declared original BB99 complete root menu required");
        }
    }

    public record Failure(String kind, String reason, String detail) {
        public Failure {
            if (!Set.of("LP", "PARENT", "MINIMUM", "STRUCTURE").contains(kind)
                    || reason == null
                    || detail == null)
                throw new IllegalArgumentException("Typed original batch rejection required");
        }
    }

    public record Comparison(
            String action,
            double originalLowerUtilityBb,
            double fullActionUpperUtilityBb,
            double lowerBoundDifferenceBb,
            Boolean previousRaiseBracketsOverlap,
            Boolean exactPreviousRaiseExceptRemainingCompilerLimit) {}

    public record PolicyAssessment(
            RootEnvelopePolicyChecks.Check original,
            Map<String, Double> minimumLowerViolationsBb,
            boolean allMinimumLowerBoundsSupported,
            List<String> failures) {
        public PolicyAssessment {
            Objects.requireNonNull(original);
            minimumLowerViolationsBb =
                    Collections.unmodifiableMap(new TreeMap<>(minimumLowerViolationsBb));
            failures = List.copyOf(failures);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String minimumScope,
            SixMaxSizeMenuPreflopPlan.Table context,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            FiniteTwoPlayerRootSecurityEnvelope.Audit originalEnvelope,
            SixMaxRootSequenceMinimumStudy.Report previousMinimum,
            FiniteTwoPlayerRootSequenceBatch.Audit batch,
            Failure rejection,
            List<Comparison> comparisons,
            List<PolicyAssessment> policies,
            boolean completeMinima,
            boolean referenceSupportedMinima,
            List<String> rejectionReasons) {
        public Report {
            comparisons = List.copyOf(comparisons);
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || parent == null
                    || originalEnvelope == null
                    || previousMinimum == null
                    || !FiniteTwoPlayerRootSequenceMinimax.SCOPE.equals(minimumScope)
                    || !parent.request().equals(request.sourceRequest().envelope().parent())
                    || !previousMinimum.parent().equals(parent)
                    || !previousMinimum.request().sourceRequest().equals(request.sourceRequest())
                    || !canonical(previousMinimum).equals(PRIOR_HASH)
                    || !previousMinimum.referenceSupportedMinimum()
                    || !originalEnvelope.baseline().equals(parent.solve())
                    || (batch == null) == (rejection == null)
                    || completeMinima != (batch != null))
                throw new IllegalArgumentException("Physical root batch source identity differs");
            var q = SixMaxReducedPreflopStudy.rootQuestion(parent.request(), 5, "9d 9h");
            if (!context.equals(q.context())
                    || !q.actions().stream().map(a -> a.action()).toList().equals(ACTIONS)
                    || !originalEnvelope
                            .question()
                            .equals(
                                    new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                            5, q.informationSet()))
                    || policies.size() != 3
                    || !policies.stream()
                            .map(p -> p.original().iterations())
                            .toList()
                            .equals(List.of(1, 80000, 160000))
                    || !policies.stream()
                            .map(p -> p.original().solutionHash())
                            .toList()
                            .equals(
                                    List.of(
                                            parent.solutionHash(),
                                            parent.references().get(0).policyHash(),
                                            parent.references().get(1).policyHash())))
                throw new IllegalArgumentException(
                        "Original public root and fresh policy identities required");
            if (batch != null) validateBatch(batch, parent, q.informationSet());
            if (!comparisons.equals(compare(batch, originalEnvelope, previousMinimum)))
                throw new IllegalArgumentException("Original minimum comparisons differ");
            for (var p : policies)
                if (!p.equals(assess(p.original(), batch)))
                    throw new IllegalArgumentException("Batch policy support differs");
            if (!rejectionReasons.equals(reasons(rejection, comparisons, policies))
                    || referenceSupportedMinima != (completeMinima && rejectionReasons.isEmpty()))
                throw new IllegalArgumentException("Physical batch outcome differs");
        }
    }

    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private record Captured(
            Path path,
            byte[] stored,
            long size,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxRootSequenceBatchStudy() {}

    public static Result solve(Request request, Path sourcePath, Path previousPath)
            throws Exception {
        Objects.requireNonNull(request);
        SixMaxTexturePayoffTableMain.distinct(List.of(sourcePath, previousPath));
        var capturedSource = capture(sourcePath, SixMaxRootSequenceMinimumStudy.SOURCE_STORED_SHA);
        var capturedPrevious = capture(previousPath, PRIOR_STORED_SHA);
        var source = decode(capturedSource, SixMaxRootEnvelopeFeedback.Report.class);
        var previous = decode(capturedPrevious, SixMaxRootSequenceMinimumStudy.Report.class);
        if (!canonical(source).equals(SOURCE_HASH)
                || !canonical(previous).equals(PRIOR_HASH)
                || !source.qualifiedForOfflineDrill()
                || !source.rejectionReasons().isEmpty()
                || !request.sourceRequest().equals(source.request())
                || !previous.referenceSupportedMinimum()
                || !previous.parent().equals(source.envelope().parent())
                || !previous.request().sourceRequest().equals(source.request()))
            throw new IllegalArgumentException(
                    "Original pinned source and prior minimum required before physical work");
        var p = request.sourceRequest().envelope().parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        if (!parent.equals(source.envelope().parent()))
            throw new IllegalStateException("Complete original parent and fresh references differ");
        var q = SixMaxReducedPreflopStudy.rootQuestion(p, 5, "9d 9h");
        FiniteTwoPlayerRootSequenceBatch.Audit batch = null;
        Failure failure = null;
        try {
            batch =
                    FiniteTwoPlayerRootSequenceBatch.solve(
                                    game,
                                    ACTIONS.stream()
                                            .map(
                                                    a ->
                                                            new FiniteTwoPlayerRootActionIntervals
                                                                    .Question(
                                                                    5, q.informationSet(), a))
                                            .toList())
                            .audit();
        } catch (BoundedLinearProgram.Rejected e) {
            failure = new Failure("LP", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected e) {
            failure = new Failure("PARENT", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerRootSequenceMinimax.Rejected e) {
            failure = new Failure("MINIMUM", e.reason().name(), e.getMessage());
        } catch (IllegalArgumentException e) {
            failure = new Failure("STRUCTURE", "ORIGINAL_CAP_OR_GAME_REJECTION", e.getMessage());
        }
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var originals = new ArrayList<RootEnvelopePolicyChecks.Check>();
        originals.add(
                RootEnvelopePolicyChecks.assess(
                        checked, source.envelope().envelope(), parent.candidate(), .01));
        for (var ref : parent.references())
            originals.add(
                    RootEnvelopePolicyChecks.assess(
                            checked, source.envelope().envelope(), ref.policy(), .01));
        var expected = new ArrayList<RootEnvelopePolicyChecks.Check>();
        expected.add(source.primary());
        expected.addAll(source.references());
        if (!originals.equals(expected))
            throw new IllegalStateException("Original whole-face policy assessments differ");
        var policies = new ArrayList<PolicyAssessment>();
        for (var original : originals) policies.add(assess(original, batch));
        var comparisons = compare(batch, source.envelope().envelope(), previous);
        var reasons = reasons(failure, comparisons, policies);
        verify(capturedSource);
        verify(capturedPrevious);
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        FiniteTwoPlayerRootSequenceMinimax.SCOPE,
                        q.context(),
                        parent,
                        source.envelope().envelope(),
                        previous,
                        batch,
                        failure,
                        comparisons,
                        policies,
                        batch != null,
                        batch != null && reasons.isEmpty(),
                        reasons));
    }

    static PolicyAssessment assess(
            RootEnvelopePolicyChecks.Check original, FiniteTwoPlayerRootSequenceBatch.Audit batch) {
        var violations = new TreeMap<String, Double>();
        var failures = new TreeSet<>(original.failures());
        if (batch != null) {
            if (!original.insideDeclaredFace()) failures.add("MINIMUM_REFERENCE_OUTSIDE_FACE");
            else
                for (var a : batch.minima()) {
                    double violation =
                            Math.max(
                                    0,
                                    a.minimum().lowerBound()
                                            - original.actionUtilitiesBb()
                                                    .get(a.question().action()));
                    violations.put(a.question().action(), violation);
                    if (violation > EPS)
                        failures.add("REFERENCE_BELOW_MINIMUM:" + a.question().action());
                }
        }
        return new PolicyAssessment(
                original,
                violations,
                batch != null && violations.size() == 3 && failures.isEmpty(),
                List.copyOf(failures));
    }

    private static List<Comparison> compare(
            FiniteTwoPlayerRootSequenceBatch.Audit batch,
            FiniteTwoPlayerRootSecurityEnvelope.Audit envelope,
            SixMaxRootSequenceMinimumStudy.Report previous) {
        if (batch == null) return List.of();
        var out = new ArrayList<Comparison>();
        for (var a : batch.minima()) {
            var old =
                    envelope.actions().stream()
                            .filter(x -> x.action().equals(a.question().action()))
                            .findFirst()
                            .orElseThrow();
            boolean raise = a.question().action().equals("raise:9.0");
            boolean overlap =
                    a.minimum().lowerBound() <= previous.minimum().minimum().upperBound()
                            && previous.minimum().minimum().lowerBound()
                                    <= a.minimum().upperBound();
            out.add(
                    new Comparison(
                            a.question().action(),
                            old.lowerUtility(),
                            old.upperUtility(),
                            a.minimum().lowerBound() - old.lowerUtility(),
                            raise ? overlap : null,
                            raise ? sameEndpoint(a, previous.minimum()) : null));
        }
        return List.copyOf(out);
    }

    static boolean sameEndpoint(
            FiniteTwoPlayerRootSequenceMinimax.Audit a,
            FiniteTwoPlayerRootSequenceMinimax.Audit b) {
        com.fasterxml.jackson.databind.node.ObjectNode
                x = SixMaxTexturePayoffTable.mapper().valueToTree(a),
                y = SixMaxTexturePayoffTable.mapper().valueToTree(b);
        ((com.fasterxml.jackson.databind.node.ObjectNode) x.get("work")).remove("compilerLimit");
        ((com.fasterxml.jackson.databind.node.ObjectNode) y.get("work")).remove("compilerLimit");
        return x.equals(y);
    }

    private static List<String> reasons(
            Failure failure, List<Comparison> comparisons, List<PolicyAssessment> policies) {
        var reasons = new TreeSet<String>();
        if (failure != null) reasons.add("BATCH:" + failure.kind() + ":" + failure.reason());
        for (var c : comparisons)
            if (c.previousRaiseBracketsOverlap() != null
                    && (!c.previousRaiseBracketsOverlap()
                            || !Boolean.TRUE.equals(
                                    c.exactPreviousRaiseExceptRemainingCompilerLimit())))
                reasons.add("ORIGINAL_SEQUENCE_RAISE_CHANGED");
        for (var p : policies)
            for (var f : p.failures()) reasons.add(p.original().iterations() + ":" + f);
        return List.copyOf(reasons);
    }

    private static void validateBatch(
            FiniteTwoPlayerRootSequenceBatch.Audit b,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            String informationSet) {
        var w = b.work();
        long compiler = w.preflightCompilerUnits(), arithmetic = 0;
        int pivots = 0;
        if (!FiniteTwoPlayerRootSequenceBatch.ALGORITHM.equals(b.algorithm())
                || !FiniteTwoPlayerRootSequenceBatch.SCOPE.equals(b.scope())
                || b.trainerAdmission()
                || !b.snapshotHash().equals(parent.solve().snapshotHash())
                || b.minima().size() != 3
                || !b.minima().stream().map(a -> a.question().action()).toList().equals(ACTIONS)
                || w.parentSolveCalls() != 1
                || w.parentLpSolves() != 2
                || w.endpointLpSolves() != 3
                || w.enumeratedContinuationPlans() != 0
                || w.compilerLimit() != 8_000_000
                || w.endpointPivotLimit() != 10_000
                || w.endpointArithmeticLimit() != 4_000_000_000L
                || compiler != 3L * parent.solve().treeNodes()
                || w.parentCompilerUnits() != parent.solve().reductionWork().chargedUnits()
                || w.parentLpArithmeticWork()
                        != parent.solve().firstLp().work().arithmeticWork()
                                + parent.solve().secondLp().work().arithmeticWork())
            throw new IllegalArgumentException("Complete owned physical batch work required");
        for (var a : b.minima()) {
            if (!a.baseline().equals(parent.solve())
                    || !a.firstParentWitness().equals(parent.firstLpWitness())
                    || !a.secondParentWitness().equals(parent.secondLpWitness())
                    || a.question().hero() != 5
                    || !a.question().informationSet().equals(informationSet)
                    || a.trainerAdmission()
                    || !FiniteTwoPlayerRootSequenceMinimax.ALGORITHM.equals(a.algorithm())
                    || !FiniteTwoPlayerRootSequenceMinimax.SCOPE.equals(a.scope())
                    || a.requiredMinimumReach() != .0001
                    || a.securitySlack() != 1e-8
                    || a.support().size() != 8
                    || a.work().compilerLimit() != 8_000_000 - compiler
                    || a.work().endpointLpSolves() != 1
                    || a.work().enumeratedContinuationPlans() != 0)
                throw new IllegalArgumentException("Original physical endpoint provenance differs");
            compiler += a.work().compilerUnits();
            arithmetic += a.work().endpointArithmeticWork();
            pivots += a.work().endpointPivots();
        }
        if (compiler != w.compilerUnits()
                || arithmetic != w.endpointArithmeticWork()
                || pivots != w.endpointPivots()
                || compiler > 8_000_000
                || arithmetic > 4_000_000_000L
                || pivots > 10_000)
            throw new IllegalArgumentException("Cumulative original physical batch budgets differ");
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
        var attrs =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || Files.isSymbolicLink(path) || attrs.size() > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular original source required");
        byte[] stored;
        try (var stream = Files.newInputStream(path)) {
            stored = stream.readNBytes(MAX_BYTES + 1);
        }
        if (stored.length > MAX_BYTES
                || !HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(stored))
                        .equals(pin))
            throw new IllegalArgumentException("Original stored source byte pin differs");
        return new Captured(path, stored, attrs.size(), attrs.fileKey(), attrs.lastModifiedTime());
    }

    private static <T> T decode(Captured c, Class<T> type) throws Exception {
        byte[] expanded;
        try (var stream = new GZIPInputStream(new ByteArrayInputStream(c.stored()))) {
            expanded = stream.readNBytes(MAX_BYTES + 1);
        }
        if (expanded.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded source cap exceeded");
        return SixMaxTexturePayoffTable.mapper().readValue(expanded, type);
    }

    private static void verify(Captured c) throws Exception {
        var now =
                capture(
                        c.path(),
                        HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(c.stored())));
        if (now.size() != c.size()
                || !Objects.equals(now.fileKey(), c.fileKey())
                || !now.modified().equals(c.modified())
                || !Arrays.equals(now.stored(), c.stored()))
            throw new IllegalArgumentException(
                    "Captured original source changed during physical work");
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
            throw new IllegalArgumentException("Expanded batch report cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored batch report cap exceeded");
        stream.write(bytes.toByteArray());
    }

    public static Result replay(
            Path requestPath, Path sourcePath, Path previousPath, Path reportPath)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(requestPath, sourcePath, previousPath, reportPath));
        var request = readRequest(requestPath);
        var saved = readReport(reportPath);
        if (!saved.request().equals(request))
            throw new IllegalArgumentException("Published batch request differs");
        var result = solve(request, sourcePath, previousPath);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete original physical batch replay differs");
        return result;
    }
}
