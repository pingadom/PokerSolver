package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * One declared physical minimum; neither its bracket nor parsed evidence grants grading authority.
 */
public final class SixMaxRootSequenceMinimumStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-root-sequence-minimum-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-sequence-minimum-report/v1";
    public static final String STATUS = "PHYSICAL_ROOT_SEQUENCE_RAISE_MINIMUM_RESEARCH_ONLY";
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
            String action,
            String legacyMinimumStoredSha256) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !SixMaxRootMinimumFeedback.MINIMUM_SHA.equals(legacyMinimumStoredSha256)
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
            SixMaxRootMinimumStudy.Report legacyMinimum,
            FiniteTwoPlayerRootSequenceMinimax.Audit minimum,
            Failure rejection,
            List<PolicyAssessment> policies,
            Double previousConservativeLowerUtilityBb,
            Double lowerBoundDifferenceBb,
            Double legacyMinimumWitnessDifferenceBb,
            Boolean legacyMinimumBracketsOverlap,
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
                    || !FiniteTwoPlayerRootSequenceMinimax.SCOPE.equals(minimumScope)
                    || parent == null
                    || legacyMinimum == null
                    || !legacyMinimum.completeMinimum()
                    || !legacyMinimum.referenceSupportedMinimum()
                    || !legacyMinimum.parent().equals(parent)
                    || !legacyMinimum.request().sourceRequest().equals(request.sourceRequest())
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
                            || !FiniteTwoPlayerRootSequenceMinimax.ALGORITHM.equals(
                                    minimum.algorithm())
                            || minimum.trainerAdmission()
                            || !minimumScope.equals(minimum.scope())
                            || minimum.work().enumeratedContinuationPlans() != 0
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
                            || lowerBoundDifferenceBb != null
                            || legacyMinimumWitnessDifferenceBb != null
                            || legacyMinimumBracketsOverlap != null))
                throw new IllegalArgumentException(
                        "Failed minimum cannot publish endpoint comparisons");
            if (!Objects.equals(
                            legacyMinimumWitnessDifferenceBb,
                            legacyDifference(minimum, legacyMinimum))
                    || !Objects.equals(
                            legacyMinimumBracketsOverlap, legacyOverlap(minimum, legacyMinimum)))
                throw new IllegalArgumentException("Legacy minimum comparison differs");
            for (var assessment : policies) {
                if (!assessment.equals(assess(assessment.original(), minimum, request.action())))
                    throw new IllegalArgumentException(
                            "Minimum assessment differs from its original policy");
            }
            if (referenceSupportedMinimum
                            != (completeMinimum
                                    && Boolean.TRUE.equals(legacyMinimumBracketsOverlap)
                                    && policies.stream()
                                            .allMatch(PolicyAssessment::minimumLowerSupported))
                    || !rejectionReasons.equals(
                            reasons(rejection, policies, minimum, legacyMinimum)))
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

    private SixMaxRootSequenceMinimumStudy() {}

    public static Result solve(Request request, Path sourceReport, Path legacyReport)
            throws Exception {
        Objects.requireNonNull(request);
        SixMaxTexturePayoffTableMain.distinct(List.of(sourceReport, legacyReport));
        var source = SixMaxRootMinimumStudy.readSource(sourceReport);
        var legacy = SixMaxRootMinimumFeedback.readMinimum(legacyReport);
        if (!legacy.completeMinimum()
                || !legacy.referenceSupportedMinimum()
                || !legacy.parent().equals(source.envelope().parent())
                || !legacy.request().sourceRequest().equals(request.sourceRequest()))
            throw new IllegalArgumentException(
                    "Pinned legacy minimum and source parent must agree");
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
        FiniteTwoPlayerRootSequenceMinimax.Audit minimum = null;
        Failure failure = null;
        try {
            minimum =
                    FiniteTwoPlayerRootSequenceMinimax.solve(
                                    game,
                                    new FiniteTwoPlayerRootActionIntervals.Question(
                                            5, q.informationSet(), request.action()))
                            .audit();
        } catch (BoundedLinearProgram.Rejected f) {
            failure = new Failure("LP", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected f) {
            failure = new Failure("PARENT", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerRootSequenceMinimax.Rejected f) {
            failure = new Failure("MINIMUM", f.reason().name(), f.getMessage());
        } catch (IllegalArgumentException f) {
            failure = new Failure("STRUCTURE", "ORIGINAL_CAP_OR_GAME_REJECTION", f.getMessage());
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
                        FiniteTwoPlayerRootSequenceMinimax.SCOPE,
                        q.context(),
                        parent,
                        legacy,
                        minimum,
                        failure,
                        assessments,
                        previous,
                        minimum == null ? null : minimum.minimum().lowerBound() - previous,
                        legacyDifference(minimum, legacy),
                        legacyOverlap(minimum, legacy),
                        minimum != null,
                        minimum != null
                                && Boolean.TRUE.equals(legacyOverlap(minimum, legacy))
                                && assessments.stream()
                                        .allMatch(PolicyAssessment::minimumLowerSupported),
                        reasons(failure, assessments, minimum, legacy)));
    }

    static PolicyAssessment assess(
            RootEnvelopePolicyChecks.Check original,
            FiniteTwoPlayerRootSequenceMinimax.Audit minimum,
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

    private static Double legacyDifference(
            FiniteTwoPlayerRootSequenceMinimax.Audit minimum,
            SixMaxRootMinimumStudy.Report legacy) {
        return minimum == null
                ? null
                : minimum.minimum().witnessUtility() - legacy.minimum().minimum().witnessUtility();
    }

    private static Boolean legacyOverlap(
            FiniteTwoPlayerRootSequenceMinimax.Audit minimum,
            SixMaxRootMinimumStudy.Report legacy) {
        return minimum == null
                ? null
                : minimum.minimum().lowerBound() <= legacy.minimum().minimum().upperBound()
                        && legacy.minimum().minimum().lowerBound()
                                <= minimum.minimum().upperBound();
    }

    private static List<String> reasons(
            Failure failure,
            List<PolicyAssessment> policies,
            FiniteTwoPlayerRootSequenceMinimax.Audit minimum,
            SixMaxRootMinimumStudy.Report legacy) {
        var reasons = new TreeSet<String>();
        if (failure != null) reasons.add("MINIMUM:" + failure.kind() + ":" + failure.reason());
        if (minimum != null && !Boolean.TRUE.equals(legacyOverlap(minimum, legacy)))
            reasons.add("LEGACY_MINIMUM_BRACKETS_DISJOINT");
        for (var p : policies)
            for (var reason : p.failures()) reasons.add(p.original().iterations() + ":" + reason);
        return List.copyOf(reasons);
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

    public static Result replay(Path requestPath, Path sourcePath, Path legacyPath, Path reportPath)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(requestPath, sourcePath, legacyPath, reportPath));
        var request = readRequest(requestPath);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(reportPath, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Saved minimum request differs");
        var result = solve(request, sourcePath, legacyPath);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete root minimum replay differs");
        return result;
    }
}
