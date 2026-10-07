package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuitConditionalRefinementTest {
    record Fixture(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxSuitRefinementPayoffTable.Artifact table,
            SixMaxSuitRefinementFlopGame game,
            SixMaxSuitRefinementStudy.Checkpoint checkpoint) {}

    static Fixture fixture() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var table = SixMaxSuitRefinementPayoffTableTest.table(source, parent);
        var game =
                new SixMaxSuitRefinementFlopGame(
                        source, parent, table, SixMaxRankTextureFlopGameTest.menu());
        var rows = new LinkedHashMap<>(source.solution().strategy());
        for (var root : game.sourceGame().chanceOutcomes(game.sourceGame().initialState())) {
            var state = root.state();
            for (var action : SixMaxConnectedPreflopGameTest.HISTORY) {
                var pure = new LinkedHashMap<String, Double>();
                game.sourceGame()
                        .legalActions(state)
                        .forEach(a -> pure.put(a, a.equals(action.action()) ? 1.0 : 0.0));
                rows.put(
                        game.sourceGame().currentPlayer(state)
                                + ":"
                                + game.sourceGame().informationSet(state),
                        pure);
                state = game.sourceGame().afterAction(state, action.action());
            }
        }
        var baseline = game.checkdownBaseline(new CfrSolution(1, rows));
        var mistakes = new LinkedHashMap<>(baseline.strategy());
        mistakes.replaceAll(
                (key, row) -> {
                    if (!key.contains(":postflop:") || !row.containsKey("f")) return row;
                    var pure = new LinkedHashMap<String, Double>();
                    row.keySet().forEach(a -> pure.put(a, a.equals("f") ? 1.0 : 0.0));
                    return pure;
                });
        var cp =
                SixMaxSuitRefinementStudy.checkpoint(
                        source,
                        table,
                        game,
                        new CfrSolution(1, mistakes),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        return new Fixture(source, parent, table, game, cp);
    }

    static SixMaxSuitConditionalRefinement.Settings settings(
            int maximumCases, List<Integer> budgets) {
        return new SixMaxSuitConditionalRefinement.Settings(
                SixMaxSuitConditionalRefinement.Priority.LARGEST_GAP, maximumCases, budgets, .01);
    }

    @Test
    void freshTrialsRepairKnownMistakesWithoutChangingPreflopOrOtherCases() throws Exception {
        var f = fixture();
        var callbacks = new java.util.ArrayList<SixMaxSuitConditionalRefinement.Branch>();
        var result =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        settings(3, List.of(1, 10, 100, 1000)),
                        callbacks::add);
        var report = result.report();
        assertEquals(report.branches(), callbacks);
        assertEquals(3, report.branches().size());
        assertTrue(report.accepted(), report.rejectionReasons().toString());
        var artifact = result.artifact().orElseThrow();
        assertEquals(SixMaxSuitConditionalRefinement.ALGORITHM, artifact.algorithm());
        assertNotEquals(SixMaxSuitRefinementStudy.CHECKPOINT_SCHEMA, artifact.schemaVersion());
        assertEquals(f.checkpoint().solution().iterations(), artifact.solution().iterations());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(f.checkpoint().solution()),
                SixMaxPreflopContinuationFeedback.preflopPolicy(artifact.solution()));
        assertEquals(
                f.checkpoint().solution().strategy().keySet(),
                artifact.solution().strategy().keySet());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                f.game(), artifact.solution(), 1000000)
                        .addedInformationSets());
        assertTrue(
                report.after().parentWitness().parentQuality().nashConvBb()
                        <= report.before().parentWitness().parentQuality().nashConvBb() + 1e-9);
        for (var branch : report.branches()) {
            assertEquals(6.5, branch.before().nashConvBb(), 1e-12);
            assertTrue(branch.after().nashConvBb() <= .01);
            assertEquals("TARGET_MET", branch.status());
            assertEquals(100, branch.chosenIterations());
            assertEquals(
                    List.of(1, 10, 100),
                    branch.trials().stream()
                            .map(SixMaxSuitConditionalRefinement.Trial::iterations)
                            .toList());
            assertTrue(
                    branch.trials().stream()
                            .allMatch(t -> t.traversal().sampledChanceNodes() == 0));
            var audited = report.after().histories().getFirst().signals().get(branch.observation());
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    f.game(),
                    f.game().sourceGame(),
                    SixMaxSuitRefinementPayoffTable.view(f.table()),
                    artifact.solution(),
                    branch.history(),
                    branch.observation(),
                    branch.observationProbabilityGivenHistory(),
                    audited.quality());
        }
        var old = report.before().histories().getFirst();
        var now = report.after().histories().getFirst();
        for (int i = 0; i < old.signals().size(); i++) {
            int observation = i;
            if (report.branches().stream().noneMatch(b -> b.observation() == observation))
                assertEquals(old.signals().get(i), now.signals().get(i));
        }
        assertEquals(
                report.inputInformationSets(),
                report.replacedInformationSets() + report.preservedInformationSets());
    }

    @Test
    void rejectedBudgetsExportDiagnosticsAndNeverExportCandidatePolicy(@TempDir Path temp)
            throws Exception {
        var f = fixture();
        var result =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        settings(1, List.of(1)),
                        b -> {});
        assertFalse(result.report().accepted());
        assertTrue(result.artifact().isEmpty());
        assertTrue(result.report().rejectionReasons().contains("SELECTED_LOCAL_TARGET_NOT_MET"));
        var artifact = temp.resolve("policy.json");
        var report = temp.resolve("report.json");
        SixMaxSuitConditionalRefinement.write(artifact, report, result);
        assertFalse(Files.exists(artifact));
        assertEquals(
                result.report(),
                SixMaxSuitConditionalRefinement.replay(
                                artifact, report, f.source(), f.parent(), f.table(), f.checkpoint())
                        .report());
        Files.writeString(artifact, "old-policy");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitConditionalRefinement.write(artifact, report, result));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinement.replay(
                                artifact,
                                report,
                                f.source(),
                                f.parent(),
                                f.table(),
                                f.checkpoint()));
        assertEquals("old-policy", Files.readString(artifact));
    }

    @Test
    void deterministicReplayRejectsChangedLineageStrategiesSettingsAndReports(@TempDir Path temp)
            throws Exception {
        var f = fixture();
        var result =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        settings(1, List.of(100)),
                        b -> {});
        assertTrue(result.report().accepted(), result.report().rejectionReasons().toString());
        var artifact = temp.resolve("policy.json");
        var report = temp.resolve("report.json");
        SixMaxSuitConditionalRefinement.write(artifact, report, result);
        var originalPolicy = Files.readAllBytes(artifact);
        var originalReport = Files.readAllBytes(report);
        var replayed =
                SixMaxSuitConditionalRefinement.replay(
                        artifact, report, f.source(), f.parent(), f.table(), f.checkpoint());
        assertEquals(result.artifact(), replayed.artifact());
        assertEquals(result.report(), replayed.report());
        assertArrayEquals(originalPolicy, Files.readAllBytes(artifact));
        assertArrayEquals(originalReport, Files.readAllBytes(report));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper()
                                .readTree(SixMaxTextureStudy.json(result.artifact().orElseThrow()));
        for (String field :
                List.of(
                        "predecessorCheckpointHash",
                        "predecessorSolutionHash",
                        "gameHash",
                        "frozenPreflopHash",
                        "solutionHash")) {
            String original = tree.get(field).asText();
            tree.put(field, "0".repeat(64));
            Files.writeString(artifact, tree.toString());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSuitConditionalRefinement.replay(
                                    artifact,
                                    report,
                                    f.source(),
                                    f.parent(),
                                    f.table(),
                                    f.checkpoint()));
            tree.put(field, original);
        }
        var strategy =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        tree.get("solution").get("strategy");
        strategy.putObject("0:foreign").put("fold", 1.0);
        Files.writeString(artifact, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinement.replay(
                                artifact,
                                report,
                                f.source(),
                                f.parent(),
                                f.table(),
                                f.checkpoint()));
        Files.write(artifact, originalPolicy);
        var reportTree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper()
                                .readTree(SixMaxTextureStudy.json(result.report()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) reportTree.get("settings"))
                .put("maximumCases", 2);
        Files.writeString(report, reportTree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinement.replay(
                                artifact,
                                report,
                                f.source(),
                                f.parent(),
                                f.table(),
                                f.checkpoint()));
        Files.write(report, originalReport);
        var changedRows = new LinkedHashMap<>(f.checkpoint().solution().strategy());
        var key =
                changedRows.keySet().stream()
                        .filter(
                                k ->
                                        !k.contains(":postflop:")
                                                && changedRows.get(k).values().stream()
                                                                .distinct()
                                                                .count()
                                                        > 1)
                        .findFirst()
                        .orElseThrow();
        var replacement = new LinkedHashMap<String, Double>();
        int size = changedRows.get(key).size();
        changedRows.get(key).keySet().forEach(a -> replacement.put(a, 1.0 / size));
        changedRows.put(key, replacement);
        var different =
                SixMaxSuitRefinementStudy.checkpoint(
                        f.source(),
                        f.table(),
                        f.game(),
                        new CfrSolution(1, changedRows),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinement.replay(
                                artifact, report, f.source(), f.parent(), f.table(), different));
    }

    @Test
    void parentGateRejectsRegressionEvenWhenLocalTargetsPass() throws Exception {
        var f = fixture();
        var result =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        settings(1, List.of(100)),
                        b -> {});
        var report = result.report();
        var witness = report.after().parentWitness();
        var quality = witness.parentQuality();
        var worseQuality =
                new SixMaxConnectedPreflopAudit.Quality(
                        quality.profileUtilitiesBb(),
                        quality.bestResponseUtilitiesBb(),
                        quality.deviationGainsBb(),
                        report.before().parentWitness().parentQuality().nashConvBb() + .1);
        var worseWitness =
                new SixMaxRankTextureConditionalAudit.ParentWitness(
                        worseQuality,
                        witness.reachWeightedLocalGainsBb(),
                        witness.reachWeightedLocalNashConvBb(),
                        witness.embeddedPostflopResponseUtilitiesBb(),
                        witness.embeddedPostflopResponseGainsBb(),
                        witness.embeddingErrorsBb(),
                        witness.responseInformationSets(),
                        witness.responseActionHashes());
        var worse =
                new SixMaxFlopConditionalDiagnostics.Result(
                        report.after().histories(), report.after().summary(), worseWitness);
        assertEquals(
                List.of("PARENT_NASHCONV_REGRESSION"),
                SixMaxSuitConditionalRefinement.rejectionReasons(
                        report.before(), worse, report.branches(), report.settings()));
    }

    @Test
    void weightedPriorityUsesJointReachAndZeroHistoryDoesNotTrain() throws Exception {
        var f = fixture();
        var settings =
                new SixMaxSuitConditionalRefinement.Settings(
                        SixMaxSuitConditionalRefinement.Priority.REACH_WEIGHTED_GAP,
                        1,
                        List.of(100),
                        .01);
        var weighted =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(), f.parent(), f.table(), f.checkpoint(), settings, b -> {});
        var first = weighted.report().branches().getFirst();
        double maximum =
                weighted.report().before().histories().stream()
                        .flatMap(
                                h ->
                                        h.signals().stream()
                                                .filter(s -> s.quality() != null)
                                                .map(
                                                        s ->
                                                                h.historyProbability()
                                                                        * s
                                                                                .signalProbabilityGivenHistory()
                                                                        * s.quality().nashConvBb()))
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElseThrow();
        assertEquals(
                maximum,
                first.historyProbability()
                        * first.observationProbabilityGivenHistory()
                        * first.before().nashConvBb(),
                1e-12);
        var folded = new LinkedHashMap<>(f.checkpoint().solution().strategy());
        folded.replaceAll(
                (key, row) -> {
                    if (key.contains(":postflop:")) return row;
                    var pure = new LinkedHashMap<String, Double>();
                    String action =
                            row.containsKey("fold") ? "fold" : row.keySet().iterator().next();
                    row.keySet().forEach(a -> pure.put(a, a.equals(action) ? 1.0 : 0.0));
                    return pure;
                });
        var unreachable =
                SixMaxSuitRefinementStudy.checkpoint(
                        f.source(),
                        f.table(),
                        f.game(),
                        new CfrSolution(1, folded),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        var zero =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        unreachable,
                        settings,
                        b -> fail("Zero history must not train"));
        assertFalse(zero.report().accepted());
        assertTrue(zero.report().branches().isEmpty());
        assertEquals(List.of("NO_CASES_ABOVE_TARGET"), zero.report().rejectionReasons());
        assertEquals("ZERO_POLICY_REACH", zero.report().before().histories().getFirst().status());
        assertEquals(zero.report().before(), zero.report().after());
        assertEquals(0, zero.report().replacedInformationSets());
    }

    @Test
    void balancedPriorityAlternatesQueuesWithoutRepeatedCases() throws Exception {
        var f = fixture();
        var settings =
                new SixMaxSuitConditionalRefinement.Settings(
                        SixMaxSuitConditionalRefinement.Priority.BALANCED_GAP_AND_REACH,
                        4,
                        List.of(100),
                        .01);
        var result =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(), f.parent(), f.table(), f.checkpoint(), settings, b -> {});
        assertEquals(4, result.report().branches().size());
        assertEquals(
                4,
                result.report().branches().stream()
                        .map(SixMaxSuitConditionalRefinement.Branch::observation)
                        .distinct()
                        .count());
        var original = result.report().before().histories().getFirst().signals();
        var branches = result.report().branches();
        assertEquals(
                original.stream()
                        .filter(s -> s.quality() != null)
                        .max(java.util.Comparator.comparingDouble(s -> s.quality().nashConvBb()))
                        .orElseThrow()
                        .observation(),
                branches.getFirst().observation());
        int first = branches.getFirst().observation();
        assertEquals(
                original.stream()
                        .filter(s -> s.quality() != null && s.observation() != first)
                        .max(
                                java.util.Comparator.comparingDouble(
                                        s ->
                                                s.signalProbabilityGivenHistory()
                                                        * s.quality().nashConvBb()))
                        .orElseThrow()
                        .observation(),
                branches.get(1).observation());
    }

    @Test
    void invalidBudgetsAndAliasedPathsFailBeforeReadingOrWriting(@TempDir Path temp)
            throws Exception {
        for (var budgets : List.of(List.of(0), List.of(1001), List.of(10, 10), List.of(100, 10)))
            assertThrows(IllegalArgumentException.class, () -> settings(1, budgets));
        assertThrows(IllegalArgumentException.class, () -> settings(65, List.of(100)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSuitConditionalRefinement.Settings(
                                SixMaxSuitConditionalRefinement.Priority.LARGEST_GAP,
                                1,
                                List.of(100),
                                Double.NaN));
        var input = temp.resolve("sentinel");
        Files.writeString(input, "sentinel");
        var alias = temp.resolve("alias");
        Files.createLink(alias, input);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinementMain.main(
                                new String[] {
                                    "refine",
                                    input.toString(),
                                    "parent",
                                    "table",
                                    "cp",
                                    alias.toString(),
                                    "report",
                                    "LARGEST_GAP",
                                    "1",
                                    "100",
                                    ".01"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitConditionalRefinementMain.main(
                                new String[] {
                                    "refine",
                                    input.toString(),
                                    "parent",
                                    "table",
                                    "cp",
                                    "artifact",
                                    "report",
                                    "LARGEST_GAP",
                                    "65",
                                    "100",
                                    ".01"
                                }));
        assertEquals("sentinel", Files.readString(input));
    }
}
