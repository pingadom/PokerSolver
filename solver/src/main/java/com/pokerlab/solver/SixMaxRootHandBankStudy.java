package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/**
 * All declared private roots share one owned physical parent; no report grants trainer authority.
 */
public final class SixMaxRootHandBankStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-root-hand-bank-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-hand-bank-report/v1";
    public static final String STATUS = "MULTI_HAND_PREFLOP_ROOT_BANK_RESEARCH_ONLY";
    public static final String SOURCE_SHA = SixMaxSevensRootStudy.SOURCE_SHA;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_REQUEST_BYTES = 128 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;
    private static final List<String> ACTIONS = List.of("fold", "call", "raise:9.0");
    public static final List<String> HANDS = List.of("7d 7h", "9d 9h", "Ac Jc");
    public static final String WORK_SCOPE = "COMPLETED_ENVELOPE_AUDITS_ONLY/v1";

    /** Reservations include failed attempts; each unchanged kernel also enforces its own cap. */
    public record Budget(
            int maximumQuestions,
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
            new Budget(3, 1, 4, 8, 15, 2, 240000, 24000000, 30000, 12000000000L);

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            String parentStoredSha256,
            int hero,
            List<String> heroCards,
            double maximumLossBb) {
        public Request {
            heroCards = List.copyOf(heroCards);
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || !SOURCE_SHA.equals(parentStoredSha256)
                    || hero != 5
                    || !HANDS.equals(heroCards)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Complete ordered original-model root bank required");
            // Metadata only: reject incompatible parent roots before constructing physical payoffs.
            for (String hand : heroCards)
                SixMaxReducedPreflopStudy.rootQuestion(parent, hero, hand);
        }
    }

    public record Assessment(
            int questionIndex,
            String heroCards,
            SixMaxSizeMenuPreflopPlan.Table context,
            FiniteTwoPlayerRootSecurityEnvelope.Audit envelope,
            SixMaxSevensRootStudy.Failure rejection,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<RootEnvelopePolicyChecks.Check> policies,
            boolean completeOuterBounds,
            boolean feedbackSupported,
            List<String> rejectionReasons) {
        public Assessment {
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (questionIndex < 0
                    || questionIndex >= HANDS.size()
                    || !HANDS.get(questionIndex).equals(heroCards)
                    || context == null
                    || (envelope == null) == (rejection == null)
                    || completeOuterBounds != (envelope != null))
                throw new IllegalArgumentException(
                        "One original bank outcome per declared hand required");
            if (envelope == null
                    && (!moves.isEmpty()
                            || !policies.isEmpty()
                            || feedbackSupported
                            || !rejectionReasons.equals(List.of("INCOMPLETE_ROOT_ENVELOPE"))))
                throw new IllegalArgumentException("Rejected hand cannot claim feedback");
        }
    }

    /** Failed kernels do not return spent-work audits; these totals must never claim otherwise. */
    public record CompletedWork(
            String scope,
            int completedEnvelopes,
            int envelopeLps,
            long compilerUnits,
            int pivots,
            long arithmetic) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            Budget maximumWork,
            String evScope,
            String reachUnits,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            List<Assessment> assessments,
            CompletedWork completedWork,
            boolean completeOuterBounds,
            List<Integer> supportedQuestionIndices) {
        public Report {
            assessments = List.copyOf(assessments);
            supportedQuestionIndices = List.copyOf(supportedQuestionIndices);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !BUDGET.equals(maximumWork)
                    || parent == null
                    || !request.parent().equals(parent.request())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !SixMaxRootEnvelopeStudy.REACH_UNITS.equals(reachUnits)
                    || assessments.size() != HANDS.size())
                throw new IllegalArgumentException(
                        "Complete shared-parent research bank identity required");
            var originalPolicies = new ArrayList<CfrSolution>();
            originalPolicies.add(parent.candidate());
            parent.references().forEach(r -> originalPolicies.add(r.policy()));
            var supported = new ArrayList<Integer>();
            for (int i = 0; i < HANDS.size(); i++) {
                var a = assessments.get(i);
                var q = SixMaxReducedPreflopStudy.rootQuestion(request.parent(), 5, HANDS.get(i));
                if (a.questionIndex() != i || !a.context().equals(q.context()))
                    throw new IllegalArgumentException("All original ordered contexts required");
                if (a.envelope() != null) {
                    validateEnvelope(a.envelope(), parent, q.informationSet());
                    if (!a.moves().equals(RootEnvelopePolicyChecks.moves(a.envelope(), .01))
                            || a.policies().size() != 3)
                        throw new IllegalArgumentException(
                                "All moves and original reference checks required");
                    for (int j = 0; j < 3; j++)
                        validatePolicy(
                                a.policies().get(j),
                                originalPolicies.get(j),
                                j == 0
                                        ? parent.solve().behavioralQuality()
                                        : parent.references().get(j - 1).quality(),
                                a.envelope());
                    var reasons =
                            RootEnvelopePolicyChecks.reasons(
                                    a.moves(), a.policies().getFirst(), a.policies().subList(1, 3));
                    if (!a.rejectionReasons().equals(reasons)
                            || a.feedbackSupported() != reasons.isEmpty())
                        throw new IllegalArgumentException(
                                "Per-hand qualification outcome differs");
                }
                if (a.feedbackSupported()) supported.add(i);
            }
            if (!completed(assessments).equals(completedWork)
                    || completeOuterBounds
                            != assessments.stream().allMatch(Assessment::completeOuterBounds)
                    || !supported.equals(supportedQuestionIndices))
                throw new IllegalArgumentException(
                        "Exact complete inventory and work summary required");
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

    private SixMaxRootHandBankStudy() {}

    private static CompletedWork completed(List<Assessment> assessments) {
        int count = 0, lps = 0, pivots = 0;
        long compiler = 0, arithmetic = 0;
        for (var a : assessments)
            if (a.envelope() != null) {
                var w = a.envelope().work();
                count++;
                lps = Math.addExact(lps, w.lpSolves());
                compiler = Math.addExact(compiler, w.compilerUnits());
                pivots = Math.addExact(pivots, w.lpPivots());
                arithmetic = Math.addExact(arithmetic, w.lpArithmeticWork());
            }
        if (lps > BUDGET.maximumEnvelopeLps()
                || compiler > BUDGET.envelopeCompilerUnits()
                || pivots > BUDGET.envelopePivots()
                || arithmetic > BUDGET.envelopeArithmetic())
            throw new IllegalArgumentException("Cumulative bank reservation exceeded");
        return new CompletedWork(WORK_SCOPE, count, lps, compiler, pivots, arithmetic);
    }

    public static Result solve(Request request, Path sourcePath) throws Exception {
        Objects.requireNonNull(request);
        var source = capture(sourcePath);
        var saved = decode(source.stored(), SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        if (!request.parent().equals(saved.request()))
            throw new IllegalArgumentException(
                    "Pinned original parent required before physical work");
        var p = request.parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        // This is the sole parent/reference study for the whole bank.
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        if (!parent.equals(saved))
            throw new IllegalStateException("Complete original parent and fresh references differ");
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var assessments = new ArrayList<Assessment>();
        for (int i = 0; i < HANDS.size(); i++) {
            var hand = HANDS.get(i);
            var q = SixMaxReducedPreflopStudy.rootQuestion(p, request.hero(), hand);
            FiniteTwoPlayerRootSecurityEnvelope.Audit envelope = null;
            SixMaxSevensRootStudy.Failure failure = null;
            try {
                envelope =
                        FiniteTwoPlayerRootSecurityEnvelope.solve(
                                        game,
                                        new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                                5, q.informationSet()))
                                .audit();
            } catch (BoundedLinearProgram.Rejected e) {
                failure =
                        new SixMaxSevensRootStudy.Failure("LP", e.reason().name(), e.getMessage());
            } catch (FiniteTwoPlayerAffineSequenceForm.Rejected e) {
                failure =
                        new SixMaxSevensRootStudy.Failure(
                                "PARENT", e.reason().name(), e.getMessage());
            } catch (FiniteTwoPlayerRootSecurityEnvelope.Rejected e) {
                failure =
                        new SixMaxSevensRootStudy.Failure(
                                "ENVELOPE", "NUMERICAL_VERIFICATION", e.getMessage());
            }
            var moves =
                    envelope == null
                            ? List.<SixMaxSuppliedIntervalFeedback.Move>of()
                            : RootEnvelopePolicyChecks.moves(envelope, request.maximumLossBb());
            var policies = new ArrayList<RootEnvelopePolicyChecks.Check>();
            if (envelope != null) {
                policies.add(
                        RootEnvelopePolicyChecks.assess(
                                checked, envelope, parent.candidate(), .01));
                for (var reference : parent.references())
                    policies.add(
                            RootEnvelopePolicyChecks.assess(
                                    checked, envelope, reference.policy(), .01));
            }
            var reasons =
                    envelope == null
                            ? List.of("INCOMPLETE_ROOT_ENVELOPE")
                            : RootEnvelopePolicyChecks.reasons(
                                    moves, policies.getFirst(), policies.subList(1, 3));
            assessments.add(
                    new Assessment(
                            i,
                            hand,
                            q.context(),
                            envelope,
                            failure,
                            moves,
                            policies,
                            envelope != null,
                            reasons.isEmpty(),
                            reasons));
        }
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
                                parent,
                                assessments,
                                completed(assessments),
                                assessments.stream().allMatch(Assessment::completeOuterBounds),
                                assessments.stream()
                                        .filter(Assessment::feedbackSupported)
                                        .map(Assessment::questionIndex)
                                        .toList()));
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
            throw new IllegalArgumentException("Expanded bank report cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored bank report cap exceeded");
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
            throw new IllegalArgumentException("Saved root bank request differs");
        var result = solve(request, sourcePath);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete root bank physical replay differs");
        return result;
    }
}
