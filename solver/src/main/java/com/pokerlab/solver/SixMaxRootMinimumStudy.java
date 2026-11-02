package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * One declared physical minimum; neither its bracket nor parsed evidence grants grading authority.
 */
public final class SixMaxRootMinimumStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-root-minimum-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-minimum-report/v1";
    public static final String STATUS = "PHYSICAL_ROOT_RAISE_MINIMUM_RESEARCH_ONLY";
    public static final String SOURCE_HASH =
            "cc20afe1dee2584956fba058a4cbac5e421886b6bc98e91ff2c602666173a658";
    public static final String SOURCE_STORED_SHA =
            "e3bf6895b71c24dd5202d09e402ee413d9d40d35cce660d9d5edfce5e2d1823d";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Request(
            String schemaVersion,
            SixMaxRootEnvelopeFeedback.Request sourceRequest,
            String sourceReportHash,
            String action) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || sourceRequest == null
                    || !SOURCE_HASH.equals(sourceReportHash)
                    || !"raise:9.0".equals(action)
                    || sourceRequest.envelope().hero() != 5
                    || !"9d 9h".equals(sourceRequest.envelope().heroCards())
                    || sourceRequest.maximumLossBb() != .01)
                throw new IllegalArgumentException("Declared original BB99 raise minimum required");
        }
    }

    public record Failure(String kind, String reason, String detail) {
        public Failure {
            if (!Set.of("LP", "PARENT", "MINIMUM", "STRUCTURE").contains(kind)
                    || reason == null
                    || detail == null)
                throw new IllegalArgumentException("Typed root minimum rejection required");
        }
    }

    public record PolicyAssessment(
            RootEnvelopePolicyChecks.Check original,
            double forcedActionUtilityBb,
            Double minimumLowerViolationBb,
            boolean minimumLowerSupported,
            List<String> failures) {
        public PolicyAssessment {
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
            FiniteTwoPlayerRootMinimax.Audit minimum,
            Failure rejection,
            List<PolicyAssessment> policies,
            Double previousConservativeLowerUtilityBb,
            Double lowerBoundDifferenceBb,
            boolean completeMinimum,
            boolean referenceSupportedMinimum,
            List<String> rejectionReasons) {
        public Report {
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !FiniteTwoPlayerRootMinimax.SCOPE.equals(minimumScope)
                    || parent == null
                    || !parent.request().equals(request.sourceRequest().envelope().parent())
                    || (minimum == null) == (rejection == null)
                    || completeMinimum != (minimum != null))
                throw new IllegalArgumentException("Root minimum study identity differs");
            var q = SixMaxReducedPreflopStudy.rootQuestion(parent.request(), 5, "9d 9h");
            if (!context.equals(q.context())
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
                throw new IllegalArgumentException("Original context and fresh policies required");
            if (minimum != null
                    && (!minimum.baseline().equals(parent.solve())
                            || !minimum.firstParentWitness().equals(parent.firstLpWitness())
                            || !minimum.secondParentWitness().equals(parent.secondLpWitness())
                            || !minimum.question()
                                    .equals(
                                            new FiniteTwoPlayerRootActionIntervals.Question(
                                                    5, q.informationSet(), request.action()))
                            || !FiniteTwoPlayerRootMinimax.ALGORITHM.equals(minimum.algorithm())
                            || minimum.trainerAdmission()
                            || !minimumScope.equals(minimum.scope())
                            || !minimum.reducedPlans().equals(java.math.BigInteger.valueOf(52))
                            || !minimum.cartesianPlans().equals(java.math.BigInteger.valueOf(1296))
                            || minimum.support().size() != 8
                            || minimum.securitySlack() != 1e-8
                            || minimum.requiredMinimumReach() != .0001
                            || previousConservativeLowerUtilityBb == null
                            || lowerBoundDifferenceBb == null
                            || lowerBoundDifferenceBb
                                    != minimum.minimum().lowerBound()
                                            - previousConservativeLowerUtilityBb))
                throw new IllegalArgumentException(
                        "Owned complete original raise minimum required");
            if (minimum == null
                    && (previousConservativeLowerUtilityBb != null
                            || lowerBoundDifferenceBb != null))
                throw new IllegalArgumentException(
                        "Failed minimum cannot publish endpoint comparisons");
            for (var assessment : policies) {
                if (!assessment.equals(assess(assessment.original(), minimum, request.action())))
                    throw new IllegalArgumentException(
                            "Minimum assessment differs from its original policy");
            }
            if (referenceSupportedMinimum
                            != (completeMinimum
                                    && policies.stream()
                                            .allMatch(PolicyAssessment::minimumLowerSupported))
                    || !rejectionReasons.equals(reasons(rejection, policies)))
                throw new IllegalArgumentException("Root minimum research outcome differs");
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

    private SixMaxRootMinimumStudy() {}

    public static Result solve(Request request, Path sourceReport) throws Exception {
        return solve(request, readSource(sourceReport));
    }

    static Result solve(Request request, SixMaxRootEnvelopeFeedback.Report source)
            throws Exception {
        Objects.requireNonNull(request);
        if (!SOURCE_HASH.equals(SixMaxHeadsUpPreflopGame.hash(source))
                || !request.sourceRequest().equals(source.request())
                || !source.qualifiedForOfflineDrill()
                || !source.rejectionReasons().isEmpty())
            throw new IllegalArgumentException(
                    "Unchanged qualified source required before physical work");
        var p = request.sourceRequest().envelope().parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        if (!parent.equals(source.envelope().parent()))
            throw new IllegalStateException("Original full parent physical replay differs");
        var q = SixMaxReducedPreflopStudy.rootQuestion(p, 5, "9d 9h");
        FiniteTwoPlayerRootMinimax.Audit minimum = null;
        Failure failure = null;
        try {
            minimum =
                    FiniteTwoPlayerRootMinimax.solve(
                                    game,
                                    new FiniteTwoPlayerRootActionIntervals.Question(
                                            5, q.informationSet(), request.action()))
                            .audit();
        } catch (BoundedLinearProgram.Rejected f) {
            failure = new Failure("LP", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected f) {
            failure = new Failure("PARENT", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerRootMinimax.Rejected f) {
            failure = new Failure("MINIMUM", f.reason().name(), f.getMessage());
        } catch (ReducedHeroContinuations.PlanLimit f) {
            failure = new Failure("STRUCTURE", "CONTINUATION_PLAN_LIMIT", f.getMessage());
        }
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var policies = new ArrayList<CfrSolution>();
        policies.add(parent.candidate());
        parent.references().forEach(r -> policies.add(r.policy()));
        var assessments = new ArrayList<PolicyAssessment>();
        for (var policy : policies)
            assessments.add(
                    assess(
                            RootEnvelopePolicyChecks.assess(
                                    checked, source.envelope().envelope(), policy, .01),
                            minimum,
                            request.action()));
        Double previous =
                minimum == null
                        ? null
                        : source.envelope().envelope().actions().stream()
                                .filter(a -> a.action().equals(request.action()))
                                .findFirst()
                                .orElseThrow()
                                .lowerUtility();
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        FiniteTwoPlayerRootMinimax.SCOPE,
                        q.context(),
                        parent,
                        minimum,
                        failure,
                        assessments,
                        previous,
                        minimum == null ? null : minimum.minimum().lowerBound() - previous,
                        minimum != null,
                        minimum != null
                                && assessments.stream()
                                        .allMatch(PolicyAssessment::minimumLowerSupported),
                        reasons(failure, assessments)));
    }

    static PolicyAssessment assess(
            RootEnvelopePolicyChecks.Check original,
            FiniteTwoPlayerRootMinimax.Audit minimum,
            String action) {
        double value = original.actionUtilitiesBb().get(action);
        var failures = new TreeSet<>(original.failures());
        Double violation =
                minimum != null && original.insideDeclaredFace()
                        ? Math.max(0, minimum.minimum().lowerBound() - value)
                        : null;
        if (minimum != null && !original.insideDeclaredFace())
            failures.add("MINIMUM_REFERENCE_OUTSIDE_FACE");
        if (violation != null && violation > EPS)
            failures.add("REFERENCE_BELOW_MINIMUM_LOWER_BOUND");
        return new PolicyAssessment(
                original,
                value,
                violation,
                violation != null && failures.isEmpty(),
                List.copyOf(failures));
    }

    private static List<String> reasons(Failure failure, List<PolicyAssessment> policies) {
        var reasons = new TreeSet<String>();
        if (failure != null) reasons.add("MINIMUM:" + failure.kind() + ":" + failure.reason());
        for (var p : policies)
            for (var reason : p.failures()) reasons.add(p.original().iterations() + ":" + reason);
        return List.copyOf(reasons);
    }

    public static SixMaxRootEnvelopeFeedback.Report readSource(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Regular pinned source required");
        byte[] raw;
        try (var stream = Files.newInputStream(path)) {
            raw = stream.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES
                || !SOURCE_STORED_SHA.equals(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))))
            throw new IllegalArgumentException("Original source stored byte pin differs");
        byte[] expanded;
        try (var zip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
            expanded = zip.readNBytes(MAX_BYTES + 1);
        }
        if (expanded.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded source cap exceeded");
        var source =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(expanded, SixMaxRootEnvelopeFeedback.Report.class);
        if (!SOURCE_HASH.equals(SixMaxHeadsUpPreflopGame.hash(source)))
            throw new IllegalArgumentException("Original canonical source pin differs");
        return source;
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded minimum report cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored minimum report cap exceeded");
        path = path.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path requestPath, Path sourcePath, Path reportPath)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, sourcePath, reportPath));
        var request = readRequest(requestPath);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(reportPath, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Saved minimum request differs");
        var result = solve(request, sourcePath);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete root minimum replay differs");
        return result;
    }
}
