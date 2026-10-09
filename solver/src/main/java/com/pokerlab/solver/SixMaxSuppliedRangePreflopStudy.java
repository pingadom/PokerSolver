package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Owned solve and unchanged local feedback gates for a separately identified supplied-prior game.
 */
public final class SixMaxSuppliedRangePreflopStudy {
    public static final String REPORT_SCHEMA = "pokerlab-supplied-joint-preflop-study/v1";
    public static final String POLICY_SCHEMA = "pokerlab-supplied-joint-preflop-policy/v1";
    public static final String STATUS = "SUPPLIED_RANGE_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxHeadsUpPreflopStudy.MAX_BYTES;

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String posteriorScope,
            SixMaxSuppliedRangePreflopGame.Input input,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> prior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> payoffs,
            String candidateSolutionHash,
            FiniteTwoPlayerAffineSequenceForm.Audit solve,
            List<SixMaxHeadsUpPreflopStudy.Reference> references,
            List<SixMaxHeadsUpPreflopStudy.Decision> decisions,
            int materialDecisions,
            int stableDecisions,
            boolean qualifiedForOfflinePractice,
            List<String> rejectionReasons) {
        public Report {
            identity(
                    schemaVersion,
                    REPORT_SCHEMA,
                    publicationStatus,
                    trainerAdmission,
                    binding,
                    candidateSolutionHash);
            Objects.requireNonNull(input);
            Objects.requireNonNull(solve);
            prior = List.copyOf(prior);
            payoffs = List.copyOf(payoffs);
            references = List.copyOf(references);
            decisions = List.copyOf(decisions);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE.equals(posteriorScope)
                    || prior.size() != binding.budget().jointDeals()
                    || payoffs.size() != prior.size()
                    || input.worlds().size() != prior.size()
                    || materialDecisions < 0
                    || stableDecisions < 0
                    || stableDecisions > materialDecisions
                    || materialDecisions > decisions.size()
                    || decisions.size() > 128
                    || qualifiedForOfflinePractice != rejectionReasons.isEmpty()
                    || !references.stream()
                            .map(SixMaxHeadsUpPreflopStudy.Reference::iterations)
                            .toList()
                            .equals(SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS))
                throw new IllegalArgumentException(
                        "Invalid supplied-range study dimensions or scope");
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            String reportHash,
            String solutionHash,
            CfrSolution solution) {
        public Artifact {
            identity(
                    schemaVersion,
                    POLICY_SCHEMA,
                    publicationStatus,
                    trainerAdmission,
                    binding,
                    solutionHash);
            Objects.requireNonNull(solution);
            if (reportHash == null || !reportHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid supplied study hash");
        }
    }

    /**
     * No public constructor: only solving or exact physical and numerical replay creates a handle.
     */
    public static final class Result {
        private final Report report;
        private final Artifact artifact;
        private final SixMaxSuppliedRangePreflopGame game;

        private Result(Report report, Artifact artifact, SixMaxSuppliedRangePreflopGame game) {
            this.report = report;
            this.artifact = artifact;
            this.game = game;
        }

        public Report report() {
            return report;
        }

        public Optional<Artifact> artifact() {
            return Optional.ofNullable(artifact);
        }

        SixMaxSuppliedRangePreflopGame core() {
            return game;
        }
    }

    private SixMaxSuppliedRangePreflopStudy() {}

    private static void identity(
            String schema,
            String expected,
            String status,
            boolean admission,
            SixMaxSuppliedRangePreflopGame.Binding binding,
            String hash) {
        if (!expected.equals(schema)
                || !STATUS.equals(status)
                || admission
                || binding == null
                || hash == null
                || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Unsupported supplied-range evidence identity");
    }

    public static Result solve(SixMaxSuppliedRangePreflopGame.Input input) throws Exception {
        var game = new SixMaxSuppliedRangePreflopGame(input);
        var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
        var policy = new CfrSolution(1, solved.strategy());
        var screen = SixMaxHeadsUpDecisionScreen.assess(game, policy);
        String hash = SixMaxConnectedPostflopAudit.solutionHash(policy);
        var report =
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE,
                        input,
                        game.binding(),
                        game.prior(),
                        game.payoffs(),
                        hash,
                        solved.audit(),
                        screen.references(),
                        screen.decisions(),
                        screen.material(),
                        screen.stable(),
                        screen.rejectionReasons().isEmpty(),
                        screen.rejectionReasons());
        var artifact =
                report.qualifiedForOfflinePractice()
                        ? new Artifact(
                                POLICY_SCHEMA,
                                STATUS,
                                false,
                                game.binding(),
                                SixMaxHeadsUpPreflopGame.hash(report),
                                hash,
                                policy)
                        : null;
        return new Result(report, artifact, game);
    }

    public static void write(Path policy, Path report, Result result) throws Exception {
        policy = policy.toAbsolutePath().normalize();
        report = report.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(policy, report));
        if (Files.exists(policy) || Files.exists(report))
            throw new IllegalArgumentException("Supplied study outputs must be new paths");
        byte[] diagnostic = bytes(result.report());
        byte[] strategy =
                result.artifact().isPresent() ? bytes(result.artifact().orElseThrow()) : null;
        writeNew(report, diagnostic);
        if (strategy != null) writeNew(policy, strategy);
    }

    private static byte[] bytes(Object value) throws Exception {
        byte[] bytes = SixMaxTextureStudy.json(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Supplied evidence exceeds byte cap");
        return bytes;
    }

    private static void writeNew(Path path, byte[] bytes) throws Exception {
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed evidence exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path policy, Path report) throws Exception {
        input = input.toAbsolutePath().normalize();
        policy = policy.toAbsolutePath().normalize();
        report = report.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, policy, report));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var declared =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(input, MAX_BYTES),
                        SixMaxSuppliedRangePreflopGame.Input.class);
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(report, MAX_BYTES), Report.class);
        SixMaxSuppliedRangePreflopGame.preflight(declared);
        if (!declared.equals(saved.input())
                || !SixMaxHeadsUpPreflopGame.hash(declared).equals(saved.binding().inputHash()))
            throw new IllegalArgumentException("Supplied input lineage differs");
        var artifact =
                saved.qualifiedForOfflinePractice()
                        ? mapper.readValue(
                                SixMaxRankTexturePayoffTable.readBytes(policy, MAX_BYTES),
                                Artifact.class)
                        : null;
        if (artifact == null && Files.exists(policy))
            throw new IllegalArgumentException("Rejected study must not export a policy");
        if (artifact != null
                && (!artifact.binding().equals(saved.binding())
                        || !artifact.reportHash().equals(SixMaxHeadsUpPreflopGame.hash(saved))
                        || !artifact.solutionHash().equals(saved.candidateSolutionHash())
                        || !artifact.solutionHash()
                                .equals(
                                        SixMaxConnectedPostflopAudit.solutionHash(
                                                artifact.solution()))))
            throw new IllegalArgumentException("Supplied artifact lineage differs");
        // Reenumerate every physical board, then rerun owned LP, whole-game BR and both fresh
        // references.
        var expected = solve(declared);
        if (!saved.equals(expected.report())
                || !Objects.equals(artifact, expected.artifact().orElse(null)))
            throw new IllegalArgumentException("Supplied preflop exact replay differs");
        return expected;
    }
}
