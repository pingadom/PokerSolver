package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** Bounded research screen, never an admission certificate or a new joint solver checkpoint. */
public final class SixMaxSuitDecisionStability {
    public static final String SCHEMA = "six-max-suit-decision-stability/v1";
    public static final double LOCAL_GAP_BB = .001;
    public static final double DECISION_TOLERANCE_BB = .01;
    public static final double POSTERIOR_TOLERANCE = .01;
    public static final double MIN_HISTORY_REACH = .0001;
    public static final double MIN_PREFIX_REACH = .01;
    public static final double MIN_OWN_HAND_MASS = .05;
    public static final int MAX_REPORT_BYTES = 8 * 1024 * 1024;

    public record Settings(
            int maximumCases, List<Integer> freshBudgets, int minimumMaterialCombos) {
        public Settings {
            freshBudgets = List.copyOf(freshBudgets);
            if (maximumCases < 1
                    || maximumCases > 64
                    || freshBudgets.size() < 2
                    || freshBudgets.size() > 4
                    || minimumMaterialCombos < 1
                    || minimumMaterialCombos > 2)
                throw new IllegalArgumentException("Decision stability settings exceed caps");
            int previous = 0;
            for (int budget : freshBudgets) {
                if (budget <= previous || budget > 1000)
                    throw new IllegalArgumentException(
                            "Fresh reference budgets must increase within 1–1000");
                previous = budget;
            }
        }

        public static Settings standard() {
            return new Settings(32, List.of(500, 1000), 2);
        }
    }

    public record Reference(
            int iterations,
            String localSolutionHash,
            SixMaxConnectedPreflopAudit.Quality quality,
            MultiPlayerCfrSolver.Statistics traversal,
            long inactiveUtilityPrunedNodes) {}

    public record Comparison(
            int iterations,
            String referenceReachStatus,
            double referencePrefixProbability,
            double referenceOwnHandProbabilityGivenPrefix,
            Double posteriorTotalVariation,
            SixMaxOneBetDecisionValues.Values fixedQuestionReferenceValues,
            double maximumActionEvDriftBb,
            double primaryMixRegretUnderReferenceBb,
            double referenceMixRegretUnderPrimaryBb,
            double maximumFrequencyDrift,
            List<String> failures) {
        public Comparison {
            failures = List.copyOf(failures);
        }
    }

    public record Question(
            SixMaxOneBetDecisionValues.Row primary,
            boolean material,
            boolean stable,
            List<Comparison> references,
            List<String> failures) {
        public Question {
            references = List.copyOf(references);
            failures = List.copyOf(failures);
        }
    }

    public record Branch(
            List<PublicAction> history,
            int observation,
            String observationKey,
            double historyProbability,
            double observationProbabilityGivenHistory,
            SixMaxConnectedPreflopAudit.Quality primaryQuality,
            List<Reference> references,
            List<Question> questions,
            boolean retained,
            List<String> failures) {
        public Branch {
            history = List.copyOf(history);
            references = List.copyOf(references);
            questions = List.copyOf(questions);
            failures = List.copyOf(failures);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String posteriorScope,
            String comparisonScope,
            String derivedArtifactHash,
            String derivedReportHash,
            String predecessorCheckpointHash,
            String gameHash,
            String solutionHash,
            String frozenPreflopHash,
            Settings settings,
            Map<String, Integer> eligibilityCounts,
            double selectedHistoryReach,
            double eligiblePhysicalReach,
            double retainedPhysicalReach,
            List<Branch> branches) {
        public Report {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || trainerAdmission
                    || !SixMaxOneBetDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxOneBetDecisionValues.POSTERIOR_SCOPE.equals(posteriorScope)
                    || !"FIXED_PRIMARY_QUESTION_POSTERIOR_POLICY_TRANSFER/v1"
                            .equals(comparisonScope))
                throw new IllegalArgumentException("Unsupported decision stability identity");
            for (String hash :
                    List.of(
                            derivedArtifactHash,
                            derivedReportHash,
                            predecessorCheckpointHash,
                            gameHash,
                            solutionHash,
                            frozenPreflopHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid stability hash");
            Objects.requireNonNull(settings, "settings");
            eligibilityCounts = Map.copyOf(eligibilityCounts);
            branches = List.copyOf(branches);
            if (branches.size() > settings.maximumCases()
                    || !Double.isFinite(selectedHistoryReach)
                    || selectedHistoryReach <= 0
                    || selectedHistoryReach > 1 + 1e-12
                    || !Double.isFinite(eligiblePhysicalReach)
                    || eligiblePhysicalReach < 0
                    || eligiblePhysicalReach > selectedHistoryReach + 1e-12
                    || !Double.isFinite(retainedPhysicalReach)
                    || retainedPhysicalReach < 0
                    || retainedPhysicalReach > eligiblePhysicalReach + 1e-12)
                throw new IllegalArgumentException("Invalid stability reach totals");
        }
    }

    private record Candidate(
            SixMaxFlopConditionalDiagnostics.History history,
            SixMaxFlopConditionalDiagnostics.Case signal) {
        double weight() {
            return history.historyProbability() * signal.signalProbabilityGivenHistory();
        }
    }

    private SixMaxSuitDecisionStability() {}

    public static Report screen(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint predecessor,
            SixMaxSuitConditionalRefinement.Result validated,
            Settings settings,
            Consumer<Branch> progress)
            throws Exception {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(progress, "progress");
        var artifact =
                validated
                        .artifact()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Accepted derived policy required"));
        if (!artifact.predecessorCheckpointHash()
                .equals(SixMaxSuitConditionalRefinement.checkpointHash(predecessor)))
            throw new IllegalArgumentException("Decision screen predecessor differs");
        var game = SixMaxSuitRefinementStudy.rebuild(source, parent, table, predecessor);
        if (!artifact.gameHash().equals(predecessor.gameHash()))
            throw new IllegalArgumentException("Decision screen game differs");
        var counts = new TreeMap<String, Integer>();
        var candidates = new ArrayList<Candidate>();
        double eligibleReach = 0, historyReach = 0;
        for (var history : validated.report().after().histories()) {
            historyReach += history.historyProbability();
            for (var signal : history.signals()) {
                if (!table.observations().get(signal.observation()).physical()) continue;
                String reason = eligibility(history, signal, settings);
                counts.merge(reason, 1, Integer::sum);
                if (reason.equals("ELIGIBLE")) {
                    var candidate = new Candidate(history, signal);
                    candidates.add(candidate);
                    eligibleReach += candidate.weight();
                }
            }
        }
        // Stable sort retains menu/observation order when joint reach ties.
        candidates.sort(Comparator.comparingDouble(Candidate::weight).reversed());
        var branches = new ArrayList<Branch>();
        double retainedReach = 0;
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(artifact.solution());
        for (var candidate : candidates.stream().limit(settings.maximumCases()).toList()) {
            var h = candidate.history();
            var s = candidate.signal();
            var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, h.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(
                            game.core(), transition, s.observation());
            if (posterior.signalProbability() != s.signalProbabilityGivenHistory())
                throw new IllegalStateException(
                        "Decision screen posterior differs from validated audit");
            var local =
                    new SixMaxRankTextureConditionalAudit.ConditionalGame(game, posterior.roots());
            var primary =
                    SixMaxOneBetDecisionValues.assess(
                            game.core(), posterior.roots(), artifact.solution());
            var refs = new ArrayList<Reference>();
            var policies = new ArrayList<CfrSolution>();
            var decisions = new ArrayList<Map<String, SixMaxOneBetDecisionValues.Decision>>();
            for (int budget : settings.freshBudgets()) {
                var solver =
                        new MultiPlayerCfrSolver<>(
                                local,
                                CfrSolver.Variant.CFR_PLUS,
                                MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                var solved = solver.solve(budget);
                if (MultiPlayerStrategyCompletion.uniformAtUnseen(local, solved, 1000)
                                .addedInformationSets()
                        != 0) throw new IllegalStateException("Incomplete fresh reference policy");
                refs.add(
                        new Reference(
                                budget,
                                SixMaxConnectedPostflopAudit.solutionHash(solved),
                                SixMaxConnectedPreflopAudit.Quality.of(
                                        MultiPlayerInformationSetBestResponse.assess(
                                                local, solved)),
                                solver.statistics(),
                                solver.inactiveUtilityPrunedNodes()));
                policies.add(solved);
                var indexed = new TreeMap<String, SixMaxOneBetDecisionValues.Decision>();
                SixMaxOneBetDecisionValues.assess(game.core(), posterior.roots(), solved)
                        .forEach(d -> indexed.put(d.row().informationSet(), d));
                decisions.add(indexed);
            }
            var questions = new ArrayList<Question>();
            var branchFailures = new TreeSet<String>();
            var materialSeats = new HashSet<com.pokerlab.solver.PreflopAllInSpot.Seat>();
            for (var question : primary) {
                var row = question.row();
                boolean material =
                        row.status().equals("REACHED")
                                && row.prefixProbability() >= MIN_PREFIX_REACH
                                && row.ownHandProbabilityGivenPrefix() >= MIN_OWN_HAND_MASS;
                var failures = new TreeSet<String>();
                var comparisons = new ArrayList<Comparison>();
                if (material) {
                    materialSeats.add(row.actor());
                    if (row.values().decisionRegretBb() > DECISION_TOLERANCE_BB)
                        failures.add("PRIMARY_DECISION_REGRET");
                    for (int i = 0; i < refs.size(); i++) {
                        var reference = refs.get(i);
                        var other = decisions.get(i).get(row.informationSet());
                        var transferred =
                                SixMaxOneBetDecisionValues.values(
                                        game.core(), question.roots(), policies.get(i));
                        var comparison = compare(reference, question, other, transferred);
                        comparisons.add(comparison);
                        failures.addAll(comparison.failures());
                    }
                    if (!failures.isEmpty()) branchFailures.add("MATERIAL_DECISION_UNSTABLE");
                }
                questions.add(
                        new Question(
                                row,
                                material,
                                material && failures.isEmpty(),
                                comparisons,
                                new ArrayList<>(failures)));
            }
            if (materialSeats.size() != 2) branchFailures.add("BOTH_ACTIVE_SEATS_NOT_REPRESENTED");
            boolean retained = branchFailures.isEmpty();
            var branch =
                    new Branch(
                            h.history(),
                            s.observation(),
                            s.observationKey(),
                            h.historyProbability(),
                            s.signalProbabilityGivenHistory(),
                            s.quality(),
                            refs,
                            questions,
                            retained,
                            new ArrayList<>(branchFailures));
            branches.add(branch);
            if (retained) retainedReach += candidate.weight();
            progress.accept(branch);
        }
        return new Report(
                SCHEMA,
                "VALIDATION_ONLY",
                false,
                SixMaxOneBetDecisionValues.EV_SCOPE,
                SixMaxOneBetDecisionValues.POSTERIOR_SCOPE,
                "FIXED_PRIMARY_QUESTION_POSTERIOR_POLICY_TRANSFER/v1",
                hash(artifact),
                hash(validated.report()),
                artifact.predecessorCheckpointHash(),
                artifact.gameHash(),
                artifact.solutionHash(),
                artifact.frozenPreflopHash(),
                settings,
                counts,
                historyReach,
                eligibleReach,
                retainedReach,
                branches);
    }

    private static String eligibility(
            SixMaxFlopConditionalDiagnostics.History h,
            SixMaxFlopConditionalDiagnostics.Case s,
            Settings settings) {
        if (!s.status().equals("AUDITED")) return "NO_REACHED_PRIVATE_SUPPORT";
        if (h.historyProbability() < MIN_HISTORY_REACH) return "LOW_HISTORY_REACH";
        if (s.firstCombosAtFivePercent() < settings.minimumMaterialCombos()
                || s.secondCombosAtFivePercent() < settings.minimumMaterialCombos())
            return "INSUFFICIENT_MATERIAL_COMBOS";
        if (s.quality().nashConvBb() > LOCAL_GAP_BB) return "PRIMARY_LOCAL_GAP";
        return "ELIGIBLE";
    }

    static Comparison compare(
            Reference reference,
            SixMaxOneBetDecisionValues.Decision primary,
            SixMaxOneBetDecisionValues.Decision other,
            SixMaxOneBetDecisionValues.Values transferred) {
        var failures = new ArrayList<String>();
        var original = primary.row().values();
        double evDrift = 0, freqDrift = 0;
        for (String action : original.actionEvBb().keySet()) {
            evDrift =
                    Math.max(
                            evDrift,
                            Math.abs(
                                    original.actionEvBb().get(action)
                                            - transferred.actionEvBb().get(action)));
            freqDrift =
                    Math.max(
                            freqDrift,
                            Math.abs(
                                    original.frequencies().get(action)
                                            - transferred.frequencies().get(action)));
        }
        double primaryRegret = mixtureRegret(original.frequencies(), transferred.actionEvBb());
        double referenceRegret = mixtureRegret(transferred.frequencies(), original.actionEvBb());
        Double tv =
                other == null || other.roots().isEmpty()
                        ? null
                        : SixMaxOneBetDecisionValues.posteriorDistance(
                                primary.roots(), other.roots());
        if (reference.quality().nashConvBb() > LOCAL_GAP_BB) failures.add("REFERENCE_LOCAL_GAP");
        if (evDrift > DECISION_TOLERANCE_BB) failures.add("ACTION_EV_DRIFT");
        if (primaryRegret > DECISION_TOLERANCE_BB) failures.add("PRIMARY_MIX_UNDER_REFERENCE");
        if (referenceRegret > DECISION_TOLERANCE_BB) failures.add("REFERENCE_MIX_UNDER_PRIMARY");
        if (tv == null) failures.add("ZERO_REFERENCE_REACH");
        else if (tv > POSTERIOR_TOLERANCE) failures.add("PRIVATE_POSTERIOR_DRIFT");
        return new Comparison(
                reference.iterations(),
                tv == null ? "ZERO_POLICY_REACH" : "REACHED",
                other == null ? 0 : other.row().prefixProbability(),
                other == null ? 0 : other.row().ownHandProbabilityGivenPrefix(),
                tv,
                transferred,
                evDrift,
                primaryRegret,
                referenceRegret,
                freqDrift,
                failures);
    }

    static double mixtureRegret(Map<String, Double> frequencies, Map<String, Double> values) {
        double maximum =
                values.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double mixed = 0;
        for (var action : values.entrySet())
            mixed += frequencies.get(action.getKey()) * action.getValue();
        return Math.max(0, maximum - mixed);
    }

    private static String hash(Object value) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(value)));
    }

    public static void write(Path path, Report report) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8),
                MAX_REPORT_BYTES);
    }

    /**
     * Requires the opaque validated predecessor result; reproduces fresh references and every EV.
     */
    public static Report replay(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementStudy.Checkpoint predecessor,
            SixMaxSuitConditionalRefinement.Result validated)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_REPORT_BYTES),
                                Report.class);
        if (!saved.derivedArtifactHash().equals(hash(validated.artifact().orElseThrow()))
                || !saved.derivedReportHash().equals(hash(validated.report())))
            throw new IllegalArgumentException("Decision screen derived lineage differs");
        var expected =
                screen(source, parent, table, predecessor, validated, saved.settings(), b -> {});
        if (!saved.equals(expected))
            throw new IllegalArgumentException("Decision stability replay differs");
        return expected;
    }
}
