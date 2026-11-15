package com.pokerlab.solver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/**
 * Complete physical replay of the original hand bank using the physical producer's owned parent.
 */
public final class SixMaxPhysicalRootBankStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-physical-root-bank-reuse-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-physical-root-bank-reuse-report/v1";
    public static final String STATUS = "PHYSICAL_ROOT_BANK_OWNED_PARENT_REUSE_RESEARCH_ONLY";
    public static final String BANK_SHA = SixMaxRootHandBankRaiseStudy.BANK_SHA;
    public static final String BANK_HASH = SixMaxRootHandBankRaiseStudy.BANK_HASH;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_REQUEST_BYTES = 128 * 1024;

    public record Budget(
            int questions,
            int physicalGames,
            int physicalParentCalls,
            int physicalParentLpSolves,
            int batchParentCalls,
            int endpointLpSolves,
            int freshReferenceRuns,
            int freshIterations,
            long compilerUnits,
            int endpointPivots,
            long endpointArithmetic) {}

    public static final Budget BUDGET =
            new Budget(3, 1, 1, 2, 0, 15, 2, 240000, 8000000, 10000, 4000000000L);

    public record Request(
            String schemaVersion,
            String bankStoredSha256,
            String bankStudyHash,
            List<String> heroCards,
            double maximumLossBb) {
        public Request {
            heroCards = List.copyOf(heroCards);
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !BANK_SHA.equals(bankStoredSha256)
                    || !BANK_HASH.equals(bankStudyHash)
                    || !SixMaxRootHandBankStudy.HANDS.equals(heroCards)
                    || maximumLossBb != .01)
                throw new IllegalArgumentException(
                        "Complete original physical bank and unchanged threshold required");
        }
    }

    public record Comparison(
            int sourceQuestionIndex,
            String heroCards,
            String originalEnvelopeHash,
            String adjustedEnvelopeHash,
            boolean identical) {}

    public record Assessment(
            int sourceQuestionIndex,
            String heroCards,
            SixMaxSizeMenuPreflopPlan.Table context,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            List<RootEnvelopePolicyChecks.Check> policies,
            List<String> rejectionReasons) {
        public Assessment {
            moves = List.copyOf(moves);
            policies = List.copyOf(policies);
            rejectionReasons = List.copyOf(rejectionReasons);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            Budget maximumWork,
            SixMaxRootHandBankStudy.Report historicalSource,
            SixMaxSuppliedFlopSizeMenuStudy.Report physicalParent,
            FiniteTwoPlayerRootSecurityBatch.Audit batch,
            List<Comparison> comparisons,
            List<Assessment> assessments,
            boolean originalMenusReproduced) {
        public Report {
            comparisons = List.copyOf(comparisons);
            assessments = List.copyOf(assessments);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || !BUDGET.equals(maximumWork)
                    || historicalSource == null
                    || !BANK_HASH.equals(hash(historicalSource))
                    || !historicalSource.completeOuterBounds()
                    || !physicalParent.equals(historicalSource.parent())
                    || !FiniteTwoPlayerRootSecurityBatch.REUSED_PARENT_ALGORITHM.equals(
                            batch.algorithm())
                    || !FiniteTwoPlayerRootSecurityBatch.SCOPE.equals(batch.scope())
                    || batch.trainerAdmission()
                    || !batch.parent().equals(physicalParent.solve())
                    || !batch.firstParentWitness().equals(physicalParent.firstLpWitness())
                    || !batch.secondParentWitness().equals(physicalParent.secondLpWitness())
                    || !batch.snapshotHash().equals(physicalParent.solve().snapshotHash())
                    || batch.envelopes().size() != 3)
                throw new IllegalArgumentException(
                        "Complete owned physical parent reuse lineage required");
            validateWork(batch, physicalParent);
            if (!comparisons.equals(compare(batch, historicalSource))
                    || comparisons.stream().anyMatch(c -> !c.identical())
                    || !assessments.equals(originalAssessments(historicalSource))
                    || !originalMenusReproduced)
                throw new IllegalArgumentException(
                        "All original bounds, witnesses and full reference checks must reproduce");
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
            long size,
            Object fileKey,
            java.nio.file.attribute.FileTime modified) {}

    private SixMaxPhysicalRootBankStudy() {}

    private static String hash(Object value) {
        try {
            return SixMaxHistoryPhysicalConditionalRefinement.hash(value);
        } catch (Exception cause) {
            throw new IllegalArgumentException("Invalid physical bank identity", cause);
        }
    }

    static List<Comparison> compare(
            FiniteTwoPlayerRootSecurityBatch.Audit batch, SixMaxRootHandBankStudy.Report bank) {
        var result = new ArrayList<Comparison>();
        if (batch.envelopes().size() != 3)
            throw new IllegalArgumentException("All three source questions required");
        for (int i = 0; i < 3; i++) {
            var original =
                    SixMaxTexturePayoffTable.mapper()
                            .valueToTree(bank.assessments().get(i).envelope());
            var adjusted = SixMaxTexturePayoffTable.mapper().valueToTree(batch.envelopes().get(i));
            ((com.fasterxml.jackson.databind.node.ObjectNode) adjusted.get("work"))
                    .put("compilerLimit", 8000000L);
            result.add(
                    new Comparison(
                            i,
                            SixMaxRootHandBankStudy.HANDS.get(i),
                            hash(original),
                            hash(adjusted),
                            original.equals(adjusted)));
        }
        return List.copyOf(result);
    }

    private static List<Assessment> originalAssessments(SixMaxRootHandBankStudy.Report bank) {
        return bank.assessments().stream()
                .map(
                        a ->
                                new Assessment(
                                        a.questionIndex(),
                                        a.heroCards(),
                                        a.context(),
                                        a.moves(),
                                        a.policies(),
                                        a.rejectionReasons()))
                .toList();
    }

    private static void validateWork(
            FiniteTwoPlayerRootSecurityBatch.Audit batch,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent) {
        var w = batch.work();
        long compiler = 3L * parent.solve().treeNodes(), arithmetic = 0;
        int pivots = 0, solves = 0;
        if (w.parentSolveCalls() != 0
                || w.parentLpSolves() != 0
                || w.parentCompilerUnits() != 0
                || w.parentLpArithmeticWork() != 0
                || w.preflightCompilerUnits() != compiler
                || w.compilerLimit() != 8000000
                || w.endpointPivotLimit() != 10000
                || w.endpointArithmeticLimit() != 4000000000L
                || w.enumeratedContinuationPlans() != 0
                || parent.references().size() != 2
                || parent.references().stream().mapToInt(r -> r.policy().iterations()).sum()
                        != 240000)
            throw new IllegalArgumentException(
                    "One physical parent and original cumulative batch limits required");
        for (var a : batch.envelopes()) {
            var e = a.work();
            if (a.trainerAdmission()
                    || !a.baseline().equals(parent.solve())
                    || e.compilerLimit() != 8000000 - compiler
                    || e.lpSolves() != 5
                    || e.compilerUnits() < 0)
                throw new IllegalArgumentException(
                        "Original per-question work or baseline differs");
            compiler = Math.addExact(compiler, e.compilerUnits());
            pivots = Math.addExact(pivots, e.lpPivots());
            arithmetic = Math.addExact(arithmetic, e.lpArithmeticWork());
            solves = Math.addExact(solves, e.lpSolves());
            if (compiler > 8000000 || pivots > 10000 || arithmetic > 4000000000L)
                throw new IllegalArgumentException("Original cumulative batch cap exceeded");
        }
        if (w.compilerUnits() != compiler
                || w.endpointPivots() != pivots
                || w.endpointArithmeticWork() != arithmetic
                || w.endpointLpSolves() != solves
                || solves != 15)
            throw new IllegalArgumentException("Complete cumulative batch work differs");
    }

    public static Result solve(Request request, Path sourcePath) throws Exception {
        Objects.requireNonNull(request);
        var captured = capture(sourcePath);
        var bank = decode(captured.bytes(), SixMaxRootHandBankStudy.Report.class);
        if (!BANK_HASH.equals(hash(bank))
                || !bank.completeOuterBounds()
                || !bank.request().heroCards().equals(request.heroCards())
                || bank.request().maximumLossBb() != request.maximumLossBb())
            throw new IllegalArgumentException(
                    "Complete pinned original bank required before physical work");
        var p = bank.request().parent();
        var questions =
                request.heroCards().stream()
                        .map(
                                hand ->
                                        new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                                5,
                                                SixMaxReducedPreflopStudy.rootQuestion(p, 5, hand)
                                                        .informationSet()))
                        .toList();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        var physical = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game);
        if (!physical.report().equals(bank.parent()))
            throw new IllegalStateException("Complete physical parent and fresh references differ");
        var batch =
                FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(
                        game, questions, physical.ownedParent());
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var assessments = new ArrayList<Assessment>();
        for (int i = 0; i < 3; i++) {
            var envelope = batch.audit().envelopes().get(i);
            var moves = RootEnvelopePolicyChecks.moves(envelope, .01);
            var checks = new ArrayList<RootEnvelopePolicyChecks.Check>();
            checks.add(
                    RootEnvelopePolicyChecks.assess(
                            checked, envelope, physical.report().candidate(), .01));
            for (var reference : physical.report().references())
                checks.add(
                        RootEnvelopePolicyChecks.assess(
                                checked, envelope, reference.policy(), .01));
            assessments.add(
                    new Assessment(
                            i,
                            request.heroCards().get(i),
                            bank.assessments().get(i).context(),
                            moves,
                            checks,
                            RootEnvelopePolicyChecks.reasons(
                                    moves, checks.getFirst(), checks.subList(1, 3))));
        }
        var report =
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        BUDGET,
                        bank,
                        physical.report(),
                        batch.audit(),
                        compare(batch.audit(), bank),
                        assessments,
                        true);
        verify(captured);
        return new Result(report);
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Captured capture(Path input) throws Exception {
        var path = input.toAbsolutePath().normalize();
        var attrs =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || Files.isSymbolicLink(path) || attrs.size() > MAX_BYTES)
            throw new IllegalArgumentException("Bounded regular pinned bank required");
        byte[] bytes;
        try (var in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES || !BANK_SHA.equals(sha(bytes)))
            throw new IllegalArgumentException("Original bank byte pin differs");
        return new Captured(
                path,
                path.toRealPath(),
                bytes,
                attrs.size(),
                attrs.fileKey(),
                attrs.lastModifiedTime());
    }

    private static void verify(Captured c) throws Exception {
        var now = capture(c.path());
        if (!now.realPath().equals(c.realPath())
                || now.size() != c.size()
                || !Objects.equals(now.fileKey(), c.fileKey())
                || !now.modified().equals(c.modified())
                || !Arrays.equals(now.bytes(), c.bytes()))
            throw new IllegalArgumentException("Captured bank changed during physical work");
    }

    private static <T> T decode(byte[] stored, Class<T> type) throws Exception {
        byte[] raw;
        try (var in = new GZIPInputStream(new ByteArrayInputStream(stored))) {
            raw = in.readNBytes(MAX_BYTES + 1);
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
            throw new IllegalArgumentException("Regular report required");
        byte[] bytes;
        try (var in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored report cap exceeded");
        return decode(bytes, Report.class);
    }

    static void write(OutputStream out, Result owned) throws Exception {
        Objects.requireNonNull(owned);
        byte[] raw = SixMaxTextureStudy.json(owned.report()).getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded report cap exceeded");
        var bytes = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        if (bytes.size() > MAX_BYTES)
            throw new IllegalArgumentException("Stored report cap exceeded");
        out.write(bytes.toByteArray());
    }

    public static Result replay(Path request, Path source, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, report));
        var q = readRequest(request);
        var saved = readReport(report);
        if (!q.equals(saved.request())) throw new IllegalArgumentException("Saved request differs");
        var result = solve(q, source);
        if (!saved.equals(result.report()))
            throw new IllegalArgumentException("Complete physical parent reuse replay differs");
        return result;
    }
}
