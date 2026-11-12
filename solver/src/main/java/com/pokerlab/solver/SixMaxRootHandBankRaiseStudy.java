package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/** Owned shared-parent raise minima for the two bank hands without sequence minima. */
public final class SixMaxRootHandBankRaiseStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-bank-raise-sequence-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-bank-raise-sequence-report/v1";
    public static final String STATUS = "BANK_RAISE_SEQUENCE_MINIMA_RESEARCH_ONLY";
    public static final String BANK_SHA =
            "eb31446fa68413b12cfea51d2ac6e52356de91437c8068faf997a2a40491cadd";
    public static final String BANK_HASH =
            "e333144323609afd2d5fd47daf7b2636b56647d3f080c579d1491c10afc8e84d";
    public static final String ACTION = "raise:9.0";
    public static final List<String> HANDS = List.of("7d 7h", "Ac Jc");
    public static final List<Integer> INDICES = List.of(0, 2);
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final double EPS = BoundedLinearProgram.CERTIFICATE_TOLERANCE;

    public record Request(
            String schemaVersion,
            String bankStoredSha256,
            String bankStudyHash,
            List<String> heroCards,
            String action) {
        public Request {
            heroCards = List.copyOf(heroCards);
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !BANK_SHA.equals(bankStoredSha256)
                    || !BANK_HASH.equals(bankStudyHash)
                    || !HANDS.equals(heroCards)
                    || !ACTION.equals(action))
                throw new IllegalArgumentException(
                        "Declared original sevens/AJs raise batch required");
        }
    }

    public record Failure(String kind, String reason, String detail) {
        public Failure {
            if (!Set.of("LP", "PARENT", "MINIMUM", "STRUCTURE").contains(kind)
                    || reason == null
                    || detail == null)
                throw new IllegalArgumentException("Typed batch rejection required");
        }
    }

    public record Comparison(
            int sourceQuestionIndex,
            String heroCards,
            double originalLowerUtilityBb,
            double originalUpperUtilityBb,
            double minimumLowerBb,
            double minimumUpperBb,
            double lowerBoundImprovementBb) {}

    public record PolicySupport(
            int sourceQuestionIndex,
            int iterations,
            String solutionHash,
            double minimumLowerViolationBb,
            boolean insideDeclaredFace,
            boolean supported) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String minimumScope,
            SixMaxRootHandBankStudy.Report sourceBank,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            FiniteTwoPlayerRootSequenceBatch.Audit batch,
            Failure rejection,
            List<Comparison> comparisons,
            List<PolicySupport> policySupports,
            boolean completeMinima,
            boolean referenceSupportedMinima,
            List<String> rejectionReasons) {
        public Report {
            comparisons = List.copyOf(comparisons);
            policySupports = List.copyOf(policySupports);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || sourceBank == null
                    || parent == null
                    || !BANK_HASH.equals(canonical(sourceBank))
                    || !parent.equals(sourceBank.parent())
                    || !FiniteTwoPlayerRootSequenceMinimax.SCOPE.equals(minimumScope)
                    || (batch == null) == (rejection == null)
                    || completeMinima != (batch != null))
                throw new IllegalArgumentException(
                        "Original bank and owned research batch identity required");
            if (batch != null) validateBatch(batch, sourceBank);
            if (!comparisons.equals(compare(batch, sourceBank))
                    || !policySupports.equals(support(batch, sourceBank)))
                throw new IllegalArgumentException(
                        "Exact per-hand comparisons and reference checks required");
            var reasons = reasons(rejection, comparisons, policySupports);
            if (!rejectionReasons.equals(reasons)
                    || referenceSupportedMinima != (batch != null && reasons.isEmpty()))
                throw new IllegalArgumentException("Complete declared batch outcome differs");
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
            byte[] bytes,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxRootHandBankRaiseStudy() {}

    public static Result solve(Request request, Path bankPath) throws Exception {
        Objects.requireNonNull(request);
        var captured = capture(bankPath);
        var bank = decode(captured.bytes());
        if (!BANK_HASH.equals(canonical(bank)))
            throw new IllegalArgumentException(
                    "Original canonical bank required before physical work");
        var p = bank.request().parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        if (!parent.equals(bank.parent()))
            throw new IllegalStateException(
                    "Complete original physical parent and references differ");
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        // Recompute all original policy assessments used by the new lower-bound checks.
        for (int index : INDICES) {
            var assessment = bank.assessments().get(index);
            var policies = new ArrayList<CfrSolution>();
            policies.add(parent.candidate());
            parent.references().forEach(ref -> policies.add(ref.policy()));
            for (int i = 0; i < policies.size(); i++)
                if (!RootEnvelopePolicyChecks.assess(
                                checked, assessment.envelope(), policies.get(i), .01)
                        .equals(assessment.policies().get(i)))
                    throw new IllegalStateException(
                            "Original whole-face policy assessment differs");
        }
        FiniteTwoPlayerRootSequenceBatch.Audit batch = null;
        Failure failure = null;
        try {
            batch = FiniteTwoPlayerRootSequenceBatch.solve(game, questions(bank)).audit();
        } catch (BoundedLinearProgram.Rejected e) {
            failure = new Failure("LP", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected e) {
            failure = new Failure("PARENT", e.reason().name(), e.getMessage());
        } catch (FiniteTwoPlayerRootSequenceMinimax.Rejected e) {
            failure = new Failure("MINIMUM", e.reason().name(), e.getMessage());
        } catch (IllegalArgumentException e) {
            failure = new Failure("STRUCTURE", "ORIGINAL_CAP_OR_GAME_REJECTION", e.getMessage());
        }
        var comparisons = compare(batch, bank);
        var supports = support(batch, bank);
        var reasons = reasons(failure, comparisons, supports);
        var result =
                new Result(
                        new Report(
                                REPORT_SCHEMA,
                                STATUS,
                                false,
                                request,
                                FiniteTwoPlayerRootSequenceMinimax.SCOPE,
                                bank,
                                parent,
                                batch,
                                failure,
                                comparisons,
                                supports,
                                batch != null,
                                batch != null && reasons.isEmpty(),
                                reasons));
        verify(captured);
        return result;
    }

    static List<FiniteTwoPlayerRootActionIntervals.Question> questions(
            SixMaxRootHandBankStudy.Report bank) {
        return HANDS.stream()
                .map(
                        hand ->
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5,
                                        SixMaxReducedPreflopStudy.rootQuestion(
                                                        bank.request().parent(), 5, hand)
                                                .informationSet(),
                                        ACTION))
                .toList();
    }

    private static List<Comparison> compare(
            FiniteTwoPlayerRootSequenceBatch.Audit batch, SixMaxRootHandBankStudy.Report bank) {
        if (batch == null) return List.of();
        var result = new ArrayList<Comparison>();
        for (int i = 0; i < HANDS.size(); i++) {
            int index = INDICES.get(i);
            var a = batch.minima().get(i);
            var old =
                    bank.assessments().get(index).envelope().actions().stream()
                            .filter(x -> ACTION.equals(x.action()))
                            .findFirst()
                            .orElseThrow();
            result.add(
                    new Comparison(
                            index,
                            HANDS.get(i),
                            old.lowerUtility(),
                            old.upperUtility(),
                            a.minimum().lowerBound(),
                            a.minimum().upperBound(),
                            a.minimum().lowerBound() - old.lowerUtility()));
        }
        return List.copyOf(result);
    }

    private static List<PolicySupport> support(
            FiniteTwoPlayerRootSequenceBatch.Audit batch, SixMaxRootHandBankStudy.Report bank) {
        if (batch == null) return List.of();
        var result = new ArrayList<PolicySupport>();
        for (int i = 0; i < HANDS.size(); i++)
            for (var policy : bank.assessments().get(INDICES.get(i)).policies()) {
                double violation =
                        Math.max(
                                0,
                                batch.minima().get(i).minimum().lowerBound()
                                        - policy.actionUtilitiesBb().get(ACTION));
                result.add(
                        new PolicySupport(
                                INDICES.get(i),
                                policy.iterations(),
                                policy.solutionHash(),
                                violation,
                                policy.insideDeclaredFace(),
                                policy.insideDeclaredFace()
                                        && policy.failures().isEmpty()
                                        && violation <= EPS));
            }
        return List.copyOf(result);
    }

    private static List<String> reasons(
            Failure failure, List<Comparison> comparisons, List<PolicySupport> supports) {
        var result = new TreeSet<String>();
        if (failure != null) result.add("BATCH:" + failure.kind() + ":" + failure.reason());
        for (var c : comparisons)
            if (c.lowerBoundImprovementBb() < -EPS)
                result.add("WEAKER_LOWER_BOUND:" + c.sourceQuestionIndex());
        for (var s : supports)
            if (!s.supported())
                result.add(
                        "REFERENCE_BELOW_MINIMUM_OR_OUTSIDE_FACE:"
                                + s.sourceQuestionIndex()
                                + ":"
                                + s.iterations());
        return List.copyOf(result);
    }

    private static void validateBatch(
            FiniteTwoPlayerRootSequenceBatch.Audit b, SixMaxRootHandBankStudy.Report bank) {
        var parent = bank.parent();
        var w = b.work();
        long compiler = w.preflightCompilerUnits(), arithmetic = 0;
        int pivots = 0;
        if (!FiniteTwoPlayerRootSequenceBatch.ALGORITHM.equals(b.algorithm())
                || !FiniteTwoPlayerRootSequenceBatch.SCOPE.equals(b.scope())
                || b.trainerAdmission()
                || !b.snapshotHash().equals(parent.solve().snapshotHash())
                || b.minima().size() != 2
                || !b.minima().stream()
                        .map(FiniteTwoPlayerRootSequenceMinimax.Audit::question)
                        .toList()
                        .equals(questions(bank))
                || w.parentSolveCalls() != 1
                || w.parentLpSolves() != 2
                || w.endpointLpSolves() != 2
                || w.enumeratedContinuationPlans() != 0
                || w.compilerLimit() != 8_000_000
                || w.endpointPivotLimit() != 10_000
                || w.endpointArithmeticLimit() != 4_000_000_000L
                || compiler != 2L * parent.solve().treeNodes()
                || w.parentCompilerUnits() != parent.solve().reductionWork().chargedUnits()
                || w.parentLpArithmeticWork()
                        != parent.solve().firstLp().work().arithmeticWork()
                                + parent.solve().secondLp().work().arithmeticWork())
            throw new IllegalArgumentException(
                    "Complete original two-hand batch and cumulative limits required");
        for (int i = 0; i < 2; i++) {
            var a = b.minima().get(i);
            var envelope = bank.assessments().get(INDICES.get(i)).envelope();
            if (!a.baseline().equals(parent.solve())
                    || !a.firstParentWitness().equals(parent.firstLpWitness())
                    || !a.secondParentWitness().equals(parent.secondLpWitness())
                    || a.trainerAdmission()
                    || !FiniteTwoPlayerRootSequenceMinimax.ALGORITHM.equals(a.algorithm())
                    || !FiniteTwoPlayerRootSequenceMinimax.SCOPE.equals(a.scope())
                    || a.requiredMinimumReach() != .0001
                    || a.securitySlack() != EPS
                    || Math.abs(a.originalQuestionReach() - envelope.questionChanceMass()) > EPS
                    || a.support().size() != envelope.support().size()
                    || a.work().compilerLimit() != 8_000_000 - compiler
                    || a.work().endpointLpSolves() != 1
                    || a.work().enumeratedContinuationPlans() != 0)
                throw new IllegalArgumentException(
                        "Per-hand original physical minimum provenance differs");
            for (int j = 0; j < a.support().size(); j++) {
                var s = a.support().get(j);
                var original = envelope.support().get(j);
                if (original.rootIndex() != j
                        || !s.stateHash().equals(original.stateHash())
                        || s.originalChanceWeight() != original.chanceWeight()
                        || Math.abs(
                                        s.posteriorProbability()
                                                - original.chanceWeight()
                                                        / envelope.questionChanceMass())
                                > EPS)
                    throw new IllegalArgumentException(
                            "Original private-hand root support differs");
            }
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
            throw new IllegalArgumentException(
                    "Cumulative original compiler, pivot and arithmetic work differs");
    }

    private static String canonical(Object value) {
        try {
            return SixMaxHeadsUpPreflopGame.hash(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Canonical identity failed", e);
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
            throw new IllegalArgumentException("Bounded regular bank source required");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES || !BANK_SHA.equals(sha(bytes)))
            throw new IllegalArgumentException("Original bank stored bytes differ");
        return new Captured(path, path.toRealPath(), bytes, a.fileKey(), a.lastModifiedTime());
    }

    private static SixMaxRootHandBankStudy.Report decode(byte[] bytes) throws Exception {
        byte[] raw;
        try (var in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            raw = in.readNBytes(MAX_BYTES + 1);
        }
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded bank cap exceeded");
        return SixMaxTexturePayoffTable.mapper()
                .readValue(raw, SixMaxRootHandBankStudy.Report.class);
    }

    private static void verify(Captured c) throws Exception {
        var now = capture(c.path());
        if (!now.realPath().equals(c.realPath())
                || !Objects.equals(now.fileKey(), c.fileKey())
                || !now.modified().equals(c.modified())
                || !Arrays.equals(now.bytes(), c.bytes()))
            throw new IllegalArgumentException("Captured bank changed during numerical work");
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, 128 * 1024), Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || Files.size(path) > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular report required");
        byte[] raw;
        try (var in = new GZIPInputStream(Files.newInputStream(path))) {
            raw = in.readNBytes(MAX_BYTES + 1);
        }
        if (Files.size(path) > MAX_BYTES || raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Report cap exceeded");
        return SixMaxTexturePayoffTable.mapper().readValue(raw, Report.class);
    }

    static void write(OutputStream stream, Result owned) throws Exception {
        Objects.requireNonNull(owned);
        byte[] raw = SixMaxTextureStudy.json(owned.report()).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded report cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored report cap exceeded");
        stream.write(bytes.toByteArray());
    }

    public static Result replay(Path request, Path bank, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(request, bank, report));
        var q = readRequest(request);
        var saved = readReport(report);
        if (!q.equals(saved.request()))
            throw new IllegalArgumentException("Published request differs");
        var result = solve(q, bank);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete physical raise batch replay differs");
        return result;
    }
}
