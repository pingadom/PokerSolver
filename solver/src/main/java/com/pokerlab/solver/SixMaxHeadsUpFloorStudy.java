package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxHeadsUpPreflopDecisionValues.Row;
import com.pokerlab.solver.SixMaxHeadsUpPreflopStudy.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replayable diagnostics for an explicitly different game; never a trainer admission handle. */
public final class SixMaxHeadsUpFloorStudy {
    public static final String SCHEMA = "pokerlab-heads-up-preflop-behavior-floor-diagnostic/v1";
    public static final String STATUS = "DIAGNOSTIC_ONLY_NO_TRAINER_ADMISSION";

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String posteriorScope,
            SixMaxHeadsUpPreflopGame.Binding binding,
            FiniteTwoPlayerBehaviorFloor.Audit solve,
            CfrSolution candidate,
            List<FiniteTwoPlayerFloorCfr.Result> references,
            List<Decision> decisions,
            int materialDecisions,
            int stableDecisions,
            List<String> failures) {
        public Report {
            if (!SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || !SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE.equals(evScope)
                    || !SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE.equals(posteriorScope))
                throw new IllegalArgumentException("Unsupported floor diagnostic identity");
            Objects.requireNonNull(binding);
            Objects.requireNonNull(solve);
            Objects.requireNonNull(candidate);
            references = List.copyOf(references);
            decisions = List.copyOf(decisions);
            failures = List.copyOf(failures);
            if (!references.stream()
                            .map(FiniteTwoPlayerFloorCfr.Result::iterations)
                            .toList()
                            .equals(SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS)
                    || decisions.size() > 128
                    || materialDecisions < 0
                    || stableDecisions < 0
                    || stableDecisions > materialDecisions
                    || materialDecisions > decisions.size())
                throw new IllegalArgumentException("Invalid floor diagnostic counts");
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

    private SixMaxHeadsUpFloorStudy() {}

    public static Result solve(
            SixMaxPreflopSolutionPack source,
            SixMaxHeadsUpPreflopGame.Specification specification,
            double floor)
            throws Exception {
        FiniteTwoPlayerBehaviorFloor.requireFloor(floor);
        var game = new SixMaxHeadsUpPreflopGame(source, specification);
        var solved = FiniteTwoPlayerBehaviorFloor.solve(game, floor);
        var candidate = new CfrSolution(1, solved.strategy());
        var primary = SixMaxHeadsUpPreflopDecisionValues.assess(game, candidate);
        var references = new ArrayList<FiniteTwoPlayerFloorCfr.Result>();
        var comparisons = new TreeMap<String, List<Comparison>>();
        primary.forEach(d -> comparisons.put(d.row().informationSet(), new ArrayList<>()));
        var failures = new ArrayList<String>();
        if (solved.audit().originalGameQuality().nashConvBb()
                > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("PRIMARY_ORIGINAL_GAME_GAP");
        for (int budget : SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS) {
            var ref = FiniteTwoPlayerFloorCfr.solve(game, floor, budget);
            references.add(ref);
            if (ref.originalGameQuality().nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                failures.add("REFERENCE_ORIGINAL_GAME_GAP:" + budget);
            if (ref.constrainedQuality().nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                failures.add("REFERENCE_CONSTRAINED_GAME_GAP:" + budget);
            var otherRows = new TreeMap<String, SixMaxHeadsUpPreflopDecisionValues.Decision>();
            SixMaxHeadsUpPreflopDecisionValues.assess(game, ref.solution())
                    .forEach(d -> otherRows.put(d.row().informationSet(), d));
            if (!otherRows.keySet().equals(comparisons.keySet()))
                throw new IllegalArgumentException("Floor reference support differs");
            for (var d : primary)
                if (material(d.row())) {
                    var other = otherRows.get(d.row().informationSet());
                    // Keep the candidate's question posterior fixed for ALL alternative actions.
                    // These are unrestricted hero-continuation EVs, not constrained response
                    // values.
                    var values =
                            SixMaxHeadsUpPreflopDecisionValues.values(
                                    game, d.roots(), ref.solution());
                    double drift = 0, first = 0, second = 0;
                    for (String action : values.actionEvBb().keySet()) {
                        double a = d.row().values().actionEvBb().get(action),
                                b = values.actionEvBb().get(action);
                        drift = Math.max(drift, Math.abs(a - b));
                        first += d.row().values().frequencies().get(action) * b;
                        second += values.frequencies().get(action) * a;
                    }
                    double firstRegret =
                            Math.max(0, Collections.max(values.actionEvBb().values()) - first);
                    double secondRegret =
                            Math.max(
                                    0,
                                    Collections.max(d.row().values().actionEvBb().values())
                                            - second);
                    double tv =
                            SixMaxHeadsUpPreflopDecisionValues.posteriorDistance(
                                    d.roots(), other.roots());
                    var rowFailures = new ArrayList<String>();
                    if (ref.originalGameQuality().nashConvBb()
                            > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                        rowFailures.add("REFERENCE_LOCAL_GAP");
                    if (ref.constrainedQuality().nashConvBb()
                            > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
                        rowFailures.add("REFERENCE_CONSTRAINED_GAP");
                    if (drift > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        rowFailures.add("ACTION_EV_DRIFT");
                    if (firstRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        rowFailures.add("PRIMARY_MIX_REGRET");
                    if (secondRegret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                        rowFailures.add("REFERENCE_MIX_REGRET");
                    if (tv > SixMaxSuitDecisionStability.POSTERIOR_TOLERANCE)
                        rowFailures.add("PRIVATE_POSTERIOR_DRIFT");
                    comparisons
                            .get(d.row().informationSet())
                            .add(
                                    new Comparison(
                                            budget,
                                            other.row().status(),
                                            tv,
                                            values,
                                            drift,
                                            firstRegret,
                                            secondRegret,
                                            rowFailures));
                }
        }
        var decisions = new ArrayList<Decision>();
        var combos = new TreeMap<Integer, Set<String>>();
        int material = 0, stable = 0;
        for (var d : primary) {
            boolean significant = material(d.row());
            var checks = comparisons.get(d.row().informationSet());
            boolean pass =
                    significant
                            && checks.size() == references.size()
                            && checks.stream().allMatch(c -> c.failures().isEmpty())
                            && d.row().values().decisionRegretBb()
                                    <= SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB;
            if (significant) {
                material++;
                combos.computeIfAbsent(d.row().actor().ordinal(), k -> new HashSet<>())
                        .add(d.row().ownHand());
            }
            if (pass) stable++;
            decisions.add(new Decision(d.row(), significant, pass, checks));
        }
        if (game.binding().historyReach() < SixMaxSuitDecisionStability.MIN_HISTORY_REACH)
            failures.add("LOW_SOURCE_HISTORY_REACH");
        if (game.activeSeats().stream()
                .anyMatch(s -> combos.getOrDefault(s.ordinal(), Set.of()).size() < 2))
            failures.add("INSUFFICIENT_MATERIAL_PRIVATE_COMBOS");
        if (material == 0 || stable != material) failures.add("UNSTABLE_MATERIAL_DECISIONS");
        return new Result(
                new Report(
                        SCHEMA,
                        STATUS,
                        false,
                        SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                        SixMaxHeadsUpPreflopDecisionValues.POSTERIOR_SCOPE,
                        game.binding(),
                        solved.audit(),
                        candidate,
                        references,
                        decisions,
                        material,
                        stable,
                        failures));
    }

    private static boolean material(Row row) {
        return row.values() != null
                && row.prefixProbability() >= SixMaxSuitDecisionStability.MIN_PREFIX_REACH
                && row.ownHandProbabilityGivenPrefix()
                        >= SixMaxSuitDecisionStability.MIN_OWN_HAND_MASS;
    }

    public static void write(Path output, Result result) throws Exception {
        output = output.toAbsolutePath().normalize();
        var bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SixMaxHeadsUpPreflopStudy.MAX_BYTES)
            throw new IllegalArgumentException("Floor diagnostic exceeds byte cap");
        if (output.toString().endsWith(".gz")) {
            var compressed = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(compressed)) {
                zip.write(bytes);
            }
            bytes = compressed.toByteArray();
            if (bytes.length > SixMaxHeadsUpPreflopStudy.MAX_BYTES)
                throw new IllegalArgumentException("Compressed floor diagnostic exceeds byte cap");
        }
        Files.createDirectories(output.getParent());
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path report, SixMaxPreflopSolutionPack source) throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        report, SixMaxHeadsUpPreflopStudy.MAX_BYTES),
                                Report.class);
        if (!saved.binding().sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source)))
            throw new IllegalArgumentException("Floor diagnostic source differs");
        var expected =
                solve(
                        source,
                        saved.binding().specification(),
                        saved.solve().minimumActionProbability());
        if (!expected.report().equals(saved))
            throw new IllegalArgumentException("Floor diagnostic exact replay differs");
        return expected;
    }
}
