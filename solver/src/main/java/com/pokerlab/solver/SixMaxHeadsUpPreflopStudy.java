package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxHeadsUpPreflopDecisionValues.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Owned actual-card solve and complete independent feedback screen; never general trainer
 * admission.
 */
public final class SixMaxHeadsUpPreflopStudy {
    public static final String POLICY_SCHEMA = "pokerlab-heads-up-preflop-conditional-policy/v1";
    public static final String REPORT_SCHEMA = "pokerlab-heads-up-preflop-conditional-study/v1";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final List<Integer> REFERENCE_BUDGETS = List.of(500, 1000);

    public record Reference(
            int iterations,
            String solutionHash,
            MultiPlayerInformationSetBestResponse.Report quality,
            MultiPlayerCfrSolver.Statistics traversal) {}

    public record Comparison(
            int iterations,
            String referenceReachStatus,
            Double posteriorTotalVariation,
            Values fixedQuestionReferenceValues,
            double maximumActionEvDriftBb,
            double primaryMixRegretUnderReferenceBb,
            double referenceMixRegretUnderPrimaryBb,
            List<String> failures) {
        public Comparison {
            failures = List.copyOf(failures);
        }
    }

    public record Decision(
            Row primary, boolean material, boolean stable, List<Comparison> comparisons) {
        public Decision {
            comparisons = List.copyOf(comparisons);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String posteriorScope,
            SixMaxHeadsUpPreflopGame.Binding binding,
            String candidateSolutionHash,
            FiniteTwoPlayerAffineSequenceForm.Audit solve,
            List<SixMaxHeadsUpPreflopGame.Posterior> posterior,
            List<Reference> references,
            List<Decision> decisions,
            int materialDecisions,
            int stableDecisions,
            boolean accepted,
            List<String> rejectionReasons) {
        public Report {
            identity(
                    schemaVersion,
                    REPORT_SCHEMA,
                    publicationStatus,
                    trainerAdmission,
                    binding,
                    candidateSolutionHash);
            if (!SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE.equals(posteriorScope))
                throw new IllegalArgumentException("Unsupported decision scope");
            Objects.requireNonNull(solve);
            posterior = List.copyOf(posterior);
            references = List.copyOf(references);
            decisions = List.copyOf(decisions);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (accepted != rejectionReasons.isEmpty()
                    || materialDecisions < 0
                    || stableDecisions < 0
                    || stableDecisions > materialDecisions
                    || materialDecisions > decisions.size()
                    || decisions.size() > 128
                    || posterior.size() != binding.posteriorJointDeals()
                    || !references.stream()
                            .map(Reference::iterations)
                            .toList()
                            .equals(REFERENCE_BUDGETS))
                throw new IllegalArgumentException("Invalid conditional study counts");
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            SixMaxHeadsUpPreflopGame.Binding binding,
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
            if (reportHash == null || !reportHash.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid report hash");
            Objects.requireNonNull(solution);
        }
    }

    /** Only complete solving or exact replay can manufacture this research-content handle. */
    public static final class Result {
        private final Report report;
        private final Artifact artifact;
        private final SixMaxHeadsUpPreflopGame game;

        private Result(Report report, Artifact artifact, SixMaxHeadsUpPreflopGame game) {
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

        SixMaxHeadsUpPreflopGame core() {
            return game;
        }
    }

    private SixMaxHeadsUpPreflopStudy() {}

    private static void identity(
            String schema,
            String expected,
            String status,
            boolean admitted,
            SixMaxHeadsUpPreflopGame.Binding binding,
            String hash) {
        if (!expected.equals(schema)
                || !"VALIDATION_ONLY".equals(status)
                || admitted
                || hash == null
                || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Unsupported conditional preflop artifact");
        Objects.requireNonNull(binding);
    }

    public static Result solve(
            SixMaxPreflopSolutionPack source, SixMaxHeadsUpPreflopGame.Specification specification)
            throws Exception {
        var game = new SixMaxHeadsUpPreflopGame(source, specification);
        var solved = FiniteTwoPlayerAffineSequenceForm.solve(game);
        var policy = new CfrSolution(1, solved.strategy());
        var primary = SixMaxHeadsUpPreflopDecisionValues.assess(game, policy);
        var references = new ArrayList<Reference>();
        var comparisons = new TreeMap<String, List<Comparison>>();
        primary.forEach(d -> comparisons.put(d.row().informationSet(), new ArrayList<>()));
        for (int budget : REFERENCE_BUDGETS) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.CFR_PLUS,
                            MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
            var ref = solver.solve(budget);
            var quality = MultiPlayerInformationSetBestResponse.assess(game, ref);
            references.add(
                    new Reference(
                            budget,
                            SixMaxConnectedPostflopAudit.solutionHash(ref),
                            quality,
                            solver.statistics()));
            var refRows = new TreeMap<String, SixMaxHeadsUpPreflopDecisionValues.Decision>();
            SixMaxHeadsUpPreflopDecisionValues.assess(game, ref)
                    .forEach(d -> refRows.put(d.row().informationSet(), d));
            if (!refRows.keySet().equals(comparisons.keySet()))
                throw new IllegalArgumentException("Reference policy support differs");
            for (var d : primary)
                if (material(d.row())) {
                    var other = refRows.get(d.row().informationSet());
                    var values = SixMaxHeadsUpPreflopDecisionValues.values(game, d.roots(), ref);
                    double drift = 0, primaryUnderRef = 0, refUnderPrimary = 0;
                    for (String action : d.row().values().actionEvBb().keySet()) {
                        double a = d.row().values().actionEvBb().get(action),
                                b = values.actionEvBb().get(action);
                        drift = Math.max(drift, Math.abs(a - b));
                        primaryUnderRef += d.row().values().frequencies().get(action) * b;
                        refUnderPrimary += values.frequencies().get(action) * a;
                    }
                    double primaryRegret =
                            Math.max(
                                    0,
                                    Collections.max(values.actionEvBb().values())
                                            - primaryUnderRef);
                    double refRegret =
                            Math.max(
                                    0,
                                    Collections.max(d.row().values().actionEvBb().values())
                                            - refUnderPrimary);
                    Double tv =
                            other.roots().isEmpty()
                                    ? null
                                    : SixMaxHeadsUpPreflopDecisionValues.posteriorDistance(
                                            d.roots(), other.roots());
                    var failures = new ArrayList<String>();
                    if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                        failures.add("REFERENCE_LOCAL_GAP");
                    if (drift > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("ACTION_EV_DRIFT");
                    if (primaryRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("PRIMARY_MIX_REGRET");
                    if (refRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        failures.add("REFERENCE_MIX_REGRET");
                    if (tv == null) failures.add("ZERO_REFERENCE_REACH");
                    else if (tv > SixMaxSuitDecisionStability.POSTERIOR_TOLERANCE)
                        failures.add("PRIVATE_POSTERIOR_DRIFT");
                    comparisons
                            .get(d.row().informationSet())
                            .add(
                                    new Comparison(
                                            budget,
                                            other.row().status(),
                                            tv,
                                            values,
                                            drift,
                                            primaryRegret,
                                            refRegret,
                                            failures));
                }
        }
        var decisions = new ArrayList<Decision>();
        int material = 0, stable = 0;
        var privateCombos = new TreeMap<Integer, Set<String>>();
        for (var d : primary) {
            boolean significant = material(d.row());
            var checked = comparisons.get(d.row().informationSet());
            boolean pass =
                    significant
                            && checked.size() == REFERENCE_BUDGETS.size()
                            && checked.stream().allMatch(c -> c.failures().isEmpty())
                            && d.row().values().decisionRegretBb()
                                    <= SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB;
            if (significant) {
                material++;
                privateCombos
                        .computeIfAbsent(d.row().actor().ordinal(), k -> new HashSet<>())
                        .add(d.row().ownHand());
            }
            if (pass) stable++;
            decisions.add(new Decision(d.row(), significant, pass, checked));
        }
        var rejected = new ArrayList<String>();
        if (game.binding().historyReach() < SixMaxSuitDecisionStability.MIN_HISTORY_REACH)
            rejected.add("LOW_SOURCE_HISTORY_REACH");
        if (game.activeSeats().stream()
                .anyMatch(s -> privateCombos.getOrDefault(s.ordinal(), Set.of()).size() < 2))
            rejected.add("INSUFFICIENT_MATERIAL_PRIVATE_COMBOS");
        if (material == 0 || stable != material) rejected.add("UNSTABLE_MATERIAL_DECISIONS");
        String hash = SixMaxConnectedPostflopAudit.solutionHash(policy);
        var report =
                new Report(
                        REPORT_SCHEMA,
                        "VALIDATION_ONLY",
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE,
                        game.binding(),
                        hash,
                        solved.audit(),
                        game.posterior(),
                        references,
                        decisions,
                        material,
                        stable,
                        rejected.isEmpty(),
                        rejected);
        var artifact =
                rejected.isEmpty()
                        ? new Artifact(
                                POLICY_SCHEMA,
                                "VALIDATION_ONLY",
                                false,
                                game.binding(),
                                SixMaxHeadsUpPreflopGame.hash(report),
                                hash,
                                policy)
                        : null;
        return new Result(report, artifact, game);
    }

    private static boolean material(Row row) {
        return row.values() != null
                && row.prefixProbability() >= SixMaxSuitDecisionStability.MIN_PREFIX_REACH
                && row.ownHandProbabilityGivenPrefix()
                        >= SixMaxSuitDecisionStability.MIN_OWN_HAND_MASS;
    }

    public static void write(Path policy, Path report, Result result) throws Exception {
        policy = policy.toAbsolutePath().normalize();
        report = report.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(policy, report));
        if (Files.exists(policy) || Files.exists(report))
            throw new IllegalArgumentException("Conditional outputs must be new paths");
        byte[] diagnostic =
                SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        byte[] strategy =
                result.artifact().isEmpty()
                        ? null
                        : SixMaxTextureStudy.json(result.artifact().orElseThrow())
                                .getBytes(StandardCharsets.UTF_8);
        if (diagnostic.length > MAX_BYTES || strategy != null && strategy.length > MAX_BYTES)
            throw new IllegalArgumentException("Conditional evidence exceeds byte cap");
        writeNew(report, diagnostic);
        if (strategy != null) writeNew(policy, strategy);
    }

    private static void writeNew(Path path, byte[] bytes) throws Exception {
        if (path.toString().endsWith(".gz")) {
            var compressed = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(compressed)) {
                zip.write(bytes);
            }
            bytes = compressed.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Compressed evidence exceeds byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path policy, Path report, SixMaxPreflopSolutionPack source)
            throws Exception {
        policy = policy.toAbsolutePath().normalize();
        report = report.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(policy, report));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(report, MAX_BYTES), Report.class);
        if (!saved.binding().sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source)))
            throw new IllegalArgumentException("Conditional source differs");
        var artifact =
                saved.accepted()
                        ? mapper.readValue(
                                SixMaxRankTexturePayoffTable.readBytes(policy, MAX_BYTES),
                                Artifact.class)
                        : null;
        if (!saved.accepted() && Files.exists(policy))
            throw new IllegalArgumentException("Rejected study must not export a policy");
        if (artifact != null
                && (!artifact.binding().equals(saved.binding())
                        || !artifact.reportHash().equals(SixMaxHeadsUpPreflopGame.hash(saved))
                        || !artifact.solutionHash().equals(saved.candidateSolutionHash())
                        || !artifact.solutionHash()
                                .equals(
                                        SixMaxConnectedPostflopAudit.solutionHash(
                                                artifact.solution()))))
            throw new IllegalArgumentException("Conditional artifact lineage differs");
        var expected = solve(source, saved.binding().specification());
        if (!saved.equals(expected.report())
                || !Objects.equals(artifact, expected.artifact().orElse(null)))
            throw new IllegalArgumentException("Conditional preflop exact replay differs");
        return expected;
    }
}
