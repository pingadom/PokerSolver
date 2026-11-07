package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** A separate supplied-model sevens root; reports never confer trainer authority. */
public final class SixMaxSevensRootStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-sevens-root-study-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-sevens-root-study-report/v1";
    public static final String STATUS = "SEVENS_PREFLOP_ROOT_ENVELOPE_RESEARCH_ONLY";
    public static final String SOURCE_SHA =
            "14cccdffafbb70dbfc54e6ff4467881cbf8d612891277cfcdcb38cab0ca373bf";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_REQUEST_BYTES = 128 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;
    private static final List<String> ACTIONS = List.of("fold", "call", "raise:9.0");

    public record Budget(
            int physicalGames,
            int maximumParentCalls,
            int maximumParentLps,
            int maximumEnvelopeLps,
            int freshReferenceRuns,
            int freshIterations,
            long envelopeCompilerUnits,
            int envelopePivots,
            long envelopeArithmetic) {}

    public static final Budget BUDGET =
            new Budget(1, 2, 4, 5, 2, 240000, 8000000, 10000, 4000000000L);

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            String parentStoredSha256,
            int hero,
            String heroCards,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || !SOURCE_SHA.equals(parentStoredSha256)
                    || hero != 5
                    || !"7d 7h".equals(heroCards)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException("Declared original-model BB77 request required");
        }
    }

    public record Failure(String kind, String reason, String detail) {
        public Failure {
            if (!Set.of("LP", "PARENT", "ENVELOPE").contains(kind)
                    || reason == null
                    || reason.isBlank()
                    || detail == null
                    || detail.isBlank())
                throw new IllegalArgumentException("Typed bounded numerical rejection required");
            if (kind.equals("LP")) BoundedLinearProgram.Failure.valueOf(reason);
            else if (kind.equals("PARENT"))
                FiniteTwoPlayerAffineSequenceForm.Failure.valueOf(reason);
            else if (!reason.equals("NUMERICAL_VERIFICATION"))
                throw new IllegalArgumentException("Original numerical rejection reason required");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            Budget maximumWork,
            String evScope,
            String reachUnits,
            SixMaxSizeMenuPreflopPlan.Table context,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            FiniteTwoPlayerRootSecurityEnvelope.Audit envelope,
            Failure rejection,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<RootEnvelopePolicyChecks.Check> policies,
            boolean completeOuterBounds,
            boolean feedbackSupported,
            List<String> rejectionReasons) {
        public Report {
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !BUDGET.equals(maximumWork)
                    || parent == null
                    || !request.parent().equals(parent.request())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxRootEnvelopeStudy.REACH_UNITS.equals(reachUnits)
                    || (envelope == null) == (rejection == null)
                    || completeOuterBounds != (envelope != null))
                throw new IllegalArgumentException("Sevens research identity differs");
            var q =
                    SixMaxReducedPreflopStudy.rootQuestion(
                            request.parent(), request.hero(), request.heroCards());
            if (!q.context().equals(context))
                throw new IllegalArgumentException("Original root context required");
            if (envelope == null) {
                if (!moves.isEmpty()
                        || !policies.isEmpty()
                        || feedbackSupported
                        || !rejectionReasons.equals(List.of("INCOMPLETE_ROOT_ENVELOPE")))
                    throw new IllegalArgumentException("Rejected envelope cannot claim feedback");
            } else {
                validateEnvelope(envelope, parent, q.informationSet());
                if (!moves.equals(RootEnvelopePolicyChecks.moves(envelope, request.maximumLossBb()))
                        || policies.size() != 3)
                    throw new IllegalArgumentException(
                            "Complete conservative menu and all policies required");
                var originalPolicies = new ArrayList<CfrSolution>();
                originalPolicies.add(parent.candidate());
                parent.references().forEach(r -> originalPolicies.add(r.policy()));
                for (int i = 0; i < 3; i++)
                    validatePolicy(
                            policies.get(i),
                            originalPolicies.get(i),
                            i == 0
                                    ? parent.solve().behavioralQuality()
                                    : parent.references().get(i - 1).quality(),
                            envelope);
                var reasons =
                        RootEnvelopePolicyChecks.reasons(
                                moves, policies.getFirst(), policies.subList(1, 3));
                if (!reasons.equals(rejectionReasons) || feedbackSupported != reasons.isEmpty())
                    throw new IllegalArgumentException("Complete qualification outcome differs");
            }
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
            Path realPath,
            byte[] stored,
            long size,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxSevensRootStudy() {}

    public static Result solve(Request request, Path sourcePath) throws Exception {
        Objects.requireNonNull(request);
        var source = capture(sourcePath);
        var saved = decode(source.stored(), SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        if (!request.parent().equals(saved.request()))
            throw new IllegalArgumentException(
                    "Pinned original parent request required before physical work");
        var p = request.parent();
        var q = SixMaxReducedPreflopStudy.rootQuestion(p, request.hero(), request.heroCards());
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        if (!parent.equals(saved))
            throw new IllegalStateException("Complete original parent and fresh references differ");
        FiniteTwoPlayerRootSecurityEnvelope.Audit envelope = null;
        Failure failure = null;
        try {
            envelope =
                    FiniteTwoPlayerRootSecurityEnvelope.solve(
                                    game,
                                    new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                            request.hero(), q.informationSet()))
                            .audit();
        } catch (BoundedLinearProgram.Rejected e) {
            failure = new Failure("LP", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected e) {
            failure = new Failure("PARENT", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerRootSecurityEnvelope.Rejected e) {
            failure = new Failure("ENVELOPE", "NUMERICAL_VERIFICATION", e.getMessage());
        }
        var moves =
                envelope == null
                        ? List.<SixMaxSuppliedIntervalFeedback.Move>of()
                        : RootEnvelopePolicyChecks.moves(envelope, request.maximumLossBb());
        var policies = new ArrayList<RootEnvelopePolicyChecks.Check>();
        if (envelope != null) {
            var checked = FiniteTwoPlayerSequenceForm.checked(game);
            policies.add(
                    RootEnvelopePolicyChecks.assess(
                            checked, envelope, parent.candidate(), request.maximumLossBb()));
            for (var reference : parent.references())
                policies.add(
                        RootEnvelopePolicyChecks.assess(
                                checked, envelope, reference.policy(), request.maximumLossBb()));
        }
        var reasons =
                envelope == null
                        ? List.of("INCOMPLETE_ROOT_ENVELOPE")
                        : RootEnvelopePolicyChecks.reasons(
                                moves, policies.getFirst(), policies.subList(1, 3));
        var result =
                new Result(
                        new Report(
                                REPORT_SCHEMA,
                                STATUS,
                                false,
                                request,
                                BUDGET,
                                SixMaxSuppliedFlopFeedback.EV_SCOPE,
                                SixMaxRootEnvelopeStudy.REACH_UNITS,
                                q.context(),
                                parent,
                                envelope,
                                failure,
                                moves,
                                policies,
                                envelope != null,
                                reasons.isEmpty(),
                                reasons));
        verify(source);
        return result;
    }

    private static void validateEnvelope(
            FiniteTwoPlayerRootSecurityEnvelope.Audit a,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            String key) {
        var work = a.work();
        var witnesses = new ArrayList<FiniteTwoPlayerRootSecurityEnvelope.Witness>();
        witnesses.add(a.guaranteeWitness());
        witnesses.add(a.questionUpperWitness());
        a.actions().forEach(x -> witnesses.add(x.lowerWitness()));
        long arithmetic = 0;
        int pivots = 0;
        for (var witness : witnesses) {
            var w = witness.certificate().work();
            arithmetic = Math.addExact(arithmetic, w.arithmeticWork());
            pivots =
                    Math.addExact(
                            pivots, w.phaseOnePivots() + w.cleanupPivots() + w.phaseTwoPivots());
        }
        double mass = 0;
        for (int i = 0; i < a.support().size(); i++) {
            var s = a.support().get(i);
            if (s.rootIndex() != i
                    || !Double.isFinite(s.chanceWeight())
                    || s.chanceWeight() <= 0
                    || s.stateHash() == null
                    || !s.stateHash().matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Original root support identities required");
            mass += s.chanceWeight();
        }
        if (!FiniteTwoPlayerRootSecurityEnvelope.ALGORITHM.equals(a.algorithm())
                || !FiniteTwoPlayerRootSecurityEnvelope.SCOPE.equals(a.scope())
                || a.trainerAdmission()
                || !FiniteTwoPlayerRootSecurityEnvelope.POLICY.equals(a.fixedPolicyAlgorithm())
                || !a.baseline().equals(parent.solve())
                || !a.question().equals(new FiniteTwoPlayerRootSecurityEnvelope.Question(5, key))
                || a.securitySlack() != 1e-8
                || !Double.isFinite(a.questionChanceMass())
                || !Double.isFinite(a.globalHeroUpperValue())
                || Math.abs(a.questionChanceMass() - 1. / 3) > EPS
                || Math.abs(mass - a.questionChanceMass()) > EPS
                || a.support().size() != 8
                || !a.actions().stream().map(x -> x.action()).toList().equals(ACTIONS)
                || witnesses.size() != 5
                || work.lpSolves() != 5
                || work.compilerLimit() != 8000000
                || work.compilerUnits() < 0
                || work.compilerUnits() > work.compilerLimit()
                || arithmetic != work.lpArithmeticWork()
                || arithmetic < 0
                || arithmetic > 4000000000L
                || pivots != work.lpPivots()
                || pivots < 0
                || pivots > 10000)
            throw new IllegalArgumentException(
                    "Original root envelope ownership and work limits required");
    }

    private static void validatePolicy(
            RootEnvelopePolicyChecks.Check c,
            CfrSolution policy,
            MultiPlayerInformationSetBestResponse.Report quality,
            FiniteTwoPlayerRootSecurityEnvelope.Audit a) {
        var values = c.actionUtilitiesBb();
        var losses = c.actionLossBb();
        for (double value :
                new double[] {
                    c.publicRootReach(),
                    c.publicRootNashConvBb(),
                    c.actualQuestionReach(),
                    c.counterfactualQuestionReach(),
                    c.globalHeroBestResponseBb(),
                    c.globalHeroUpperBb(),
                    c.requiredSecuritySlackBb(),
                    c.inclusionMarginBb(),
                    c.decisionRegretBb()
                })
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Finite policy diagnostics required");
        if (c.maximumBoundViolationBb() != null && !Double.isFinite(c.maximumBoundViolationBb()))
            throw new IllegalArgumentException("Finite bound violation required");
        if (c.iterations() != policy.iterations()
                || !c.solutionHash().equals(SixMaxConnectedPostflopAudit.solutionHash(policy))
                || !quality.equals(c.fullParentQuality())
                || !RootEnvelopePolicyChecks.PUBLIC_SCOPE.equals(c.publicRootQualityScope())
                || c.publicRootReach() != 1
                || c.publicRootNashConvBb() != quality.nashConvBb()
                || Math.abs(c.actualQuestionReach() - a.questionChanceMass()) > EPS
                || Math.abs(c.counterfactualQuestionReach() - a.questionChanceMass()) > EPS
                || !values.keySet().equals(new HashSet<>(ACTIONS))
                || !losses.keySet().equals(values.keySet())
                || values.values().stream().anyMatch(v -> v == null || !Double.isFinite(v))
                || losses.values().stream()
                        .anyMatch(v -> v == null || !Double.isFinite(v) || v < 0))
            throw new IllegalArgumentException(
                    "Complete original policy and public-game scope required");
        double global = quality.bestResponseUtilitiesBb().get(5),
                margin = a.globalHeroUpperValue() + EPS - global;
        boolean inside = margin >= -EPS;
        double best = Collections.max(values.values()), regret = 0;
        for (String action : ACTIONS) {
            if (Math.abs(losses.get(action) - Math.max(0, best - values.get(action))) > EPS)
                throw new IllegalArgumentException("Policy loss differs from forced values");
            regret +=
                    policy.strategy().get(a.question().informationSet()).get(action)
                            * losses.get(action);
        }
        var failures = new TreeSet<String>();
        if (!inside) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > .001) {
            failures.add("FULL_PARENT_GAP");
            failures.add("PUBLIC_ROOT_GAP");
        }
        if (a.questionChanceMass() < .0001) {
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        }
        if (regret > .01) failures.add("SELECTED_DECISION_REGRET");
        Double violation = null;
        if (inside) {
            double worst = 0;
            for (var move : RootEnvelopePolicyChecks.moves(a, .01)) {
                double v = values.get(move.action()), loss = losses.get(move.action());
                worst =
                        Math.max(
                                worst,
                                Math.max(
                                        Math.max(move.lowerEvBb() - v, v - move.upperEvBb()),
                                        Math.max(
                                                move.lowerLossBb() - loss,
                                                loss - move.upperLossBb())));
            }
            violation = worst;
            if (worst > EPS) failures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
        }
        if (c.globalHeroBestResponseBb() != global
                || c.globalHeroUpperBb() != a.globalHeroUpperValue()
                || c.requiredSecuritySlackBb() != EPS
                || c.inclusionMarginBb() != margin
                || c.insideDeclaredFace() != inside
                || c.boundsChecked() != inside
                || !Objects.equals(c.maximumBoundViolationBb(), violation)
                || Math.abs(c.decisionRegretBb() - regret) > EPS
                || !c.failures().equals(List.copyOf(failures)))
            throw new IllegalArgumentException("Policy face, bounds or regret assessment differs");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Captured capture(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var attrs =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || Files.isSymbolicLink(path) || attrs.size() > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular pinned parent required");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES || !SOURCE_SHA.equals(sha(bytes)))
            throw new IllegalArgumentException("Original parent byte pin differs");
        return new Captured(
                path,
                path.toRealPath(),
                bytes,
                attrs.size(),
                attrs.fileKey(),
                attrs.lastModifiedTime());
    }

    private static void verify(Captured source) throws Exception {
        var now = capture(source.path());
        if (!now.realPath().equals(source.realPath())
                || now.size() != source.size()
                || !Objects.equals(now.fileKey(), source.fileKey())
                || !now.modified().equals(source.modified())
                || !Arrays.equals(now.stored(), source.stored()))
            throw new IllegalArgumentException("Captured parent changed during physical work");
    }

    private static <T> T decode(byte[] stored, Class<T> type) throws Exception {
        byte[] raw;
        try (var input = new GZIPInputStream(new ByteArrayInputStream(stored))) {
            raw = input.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded report cap exceeded");
        return SixMaxTexturePayoffTable.mapper().readValue(raw, type);
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(path, MAX_REQUEST_BYTES),
                        Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Regular saved research report required");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored report cap exceeded");
        return decode(bytes, Report.class);
    }

    static void write(OutputStream output, Result owned) throws Exception {
        Objects.requireNonNull(owned);
        var raw = SixMaxTextureStudy.json(owned.report()).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded sevens report cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored sevens report cap exceeded");
        output.write(bytes.toByteArray());
    }

    public static Result replay(Path requestPath, Path sourcePath, Path reportPath)
            throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(
                        requestPath.toAbsolutePath().normalize(),
                        sourcePath.toAbsolutePath().normalize(),
                        reportPath.toAbsolutePath().normalize()));
        var request = readRequest(requestPath);
        var saved = readReport(reportPath);
        if (!saved.request().equals(request))
            throw new IllegalArgumentException("Saved sevens request differs");
        var result = solve(request, sourcePath);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete sevens physical replay differs");
        return result;
    }
}
