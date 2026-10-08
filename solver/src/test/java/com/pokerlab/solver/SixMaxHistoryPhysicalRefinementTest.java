package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Synthetic all-tie controls isolate policy repair, row preservation and publication boundaries.
 */
class SixMaxHistoryPhysicalRefinementTest {
    private static SixMaxHistoryPhysicalStudy.Validated predecessor;
    private static SixMaxHistoryPhysicalConditionalRefinement.Result accepted;
    private static SixMaxHistoryPhysicalMaxmin.Result maxmin;

    @BeforeAll
    static void fixture() throws Exception {
        var ranges = new ArrayList<>(SixMaxPreflopConvergenceMain.ranges("button-mix"));
        ranges.set(
                5,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("9s 9h", 1),
                        SixMaxConnectedPreflopGameTest.combo("8s 8h", 1)));
        var spot =
                new SixMaxPreflopResearchSpot(
                        "physical-refinement-test",
                        new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0)),
                        ranges,
                        CashRakeRule.none(),
                        SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
        var source =
                SixMaxPreflopPackBuilder.build(
                        spot,
                        2,
                        CfrSolver.Variant.CFR_PLUS,
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return new MultiwayShowdownEstimate(
                                    shares,
                                    new double[6],
                                    SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
                        },
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        "2026-10-08T12:00:00Z");
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var menu =
                new SixMaxHistoryPhysicalPayoffTable.Menu(
                        SixMaxRankTextureFlopGameTest.menu(),
                        List.of(
                                new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                        0, List.of("2c", "3c", "4c")),
                                new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                        0, List.of("2c", "3d", "4h")),
                                new SixMaxHistoryPhysicalPayoffTable.Reveal(
                                        0, List.of("2d", "3s", "4h"))));
        var table =
                SixMaxHistoryPhysicalPayoffTable.generate(
                        source,
                        parent,
                        menu,
                        n -> {},
                        (hands, board, remaining, mask) ->
                                new SixMaxConditionalPayoffEnumeration.Pair(
                                        mask, List.of(0L), List.of(666L)));
        var game = new SixMaxHistoryPhysicalFlopGame(source, parent, table);
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
        var mistakes =
                new LinkedHashMap<>(game.checkdownBaseline(new CfrSolution(1, rows)).strategy());
        mistakes.replaceAll(
                (key, row) -> {
                    if (!key.contains(":postflop:") || !row.containsKey("f")) return row;
                    var pure = new LinkedHashMap<String, Double>();
                    row.keySet().forEach(a -> pure.put(a, a.equals("f") ? 1.0 : 0.0));
                    return pure;
                });
        var cp =
                SixMaxHistoryPhysicalStudy.checkpoint(
                        table,
                        new CfrSolution(1, mistakes),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        predecessor = SixMaxHistoryPhysicalStudy.validate(source, parent, table, cp);
        accepted =
                SixMaxHistoryPhysicalConditionalRefinement.refine(
                        predecessor, settings(List.of(500, 1000)), b -> {});
        maxmin =
                SixMaxHistoryPhysicalMaxmin.refine(
                        predecessor, new SixMaxHistoryPhysicalMaxmin.Settings(3, .001), b -> {});
    }

    @Test
    void maxminRepairsSyntheticFoldMistakesWithSeparateWorkAndFrozenSupport() {
        assertTrue(maxmin.report().accepted(), maxmin.report().rejectionReasons().toString());
        var a = maxmin.artifact().orElseThrow();
        assertFalse(a.trainerAdmission());
        assertEquals(SixMaxHistoryPhysicalMaxmin.ALGORITHM, a.algorithm());
        assertEquals(1, a.solution().iterations());
        assertEquals(
                predecessor.checkpoint().solution().strategy().keySet(),
                a.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(
                        predecessor.checkpoint().solution()),
                SixMaxPreflopContinuationFeedback.preflopPolicy(a.solution()));
        assertEquals(3, maxmin.report().branches().size());
        for (var b : maxmin.report().branches()) {
            assertEquals(6.5, b.before().nashConvBb(), 1e-12);
            assertTrue(b.after().nashConvBb() <= 1e-9);
            assertTrue(b.solve().profileNodeVisits() > 0);
            assertTrue(b.solve().matrixSolution().pivots() <= FiniteMatrixMaxmin.MAX_PIVOTS);
        }
        for (var row : predecessor.checkpoint().solution().strategy().entrySet())
            if (!row.getKey().contains("board:"))
                assertEquals(row.getValue(), a.solution().strategy().get(row.getKey()));
    }

    @Test
    void maxminReplayRejectsMatrixWorkMixtureAndPolicyTampering(@TempDir Path dir)
            throws Exception {
        var policy = dir.resolve("maxmin-policy.json.gz");
        var report = dir.resolve("maxmin-report.json.gz");
        SixMaxHistoryPhysicalMaxmin.write(policy, report, maxmin);
        assertEquals(
                maxmin.report(),
                SixMaxHistoryPhysicalMaxmin.replay(policy, report, predecessor).report());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMaxmin.write(policy, report, maxmin));
        var bad = dir.resolve("bad.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.valueToTree(maxmin.report());
        tree.put("predecessorReportHash", "0".repeat(64));
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMaxmin.replay(policy, bad, predecessor));
        for (String field : List.of("matrixHash", "profileNodeVisits")) {
            tree =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            mapper.valueToTree(maxmin.report());
            var solve =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            tree.path("branches").get(0).path("solve");
            if (field.equals("matrixHash")) solve.put(field, "0".repeat(64));
            else solve.put(field, -1);
            Files.writeString(bad, tree.toString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxHistoryPhysicalMaxmin.replay(policy, bad, predecessor));
        }
        tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.valueToTree(maxmin.artifact().orElseThrow());
        tree.put("solutionHash", "0".repeat(64));
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMaxmin.replay(bad, report, predecessor));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalMaxmin.Settings(65, .001));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalMaxmin.Settings(1, Double.NaN));
    }

    @Test
    void maxminCliRejectsOutputAliasesAndExistingFilesBeforeInputLoading(@TempDir Path dir)
            throws Exception {
        String[] args = {
            "refine",
            "missing-source",
            "missing-rank",
            "missing-table",
            "missing-cp",
            "missing-study",
            dir.resolve("policy").toString(),
            dir.resolve("report").toString(),
            "3",
            ".001"
        };
        Files.writeString(Path.of(args[6]), "preserve");
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxHistoryPhysicalMaxminMain.main(args));
        assertEquals("preserve", Files.readString(Path.of(args[6])));
        args[6] = args[7];
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxHistoryPhysicalMaxminMain.main(args));
        assertFalse(Files.exists(Path.of(args[7])));
    }

    static SixMaxSuitConditionalRefinement.Settings settings(List<Integer> budgets) {
        return new SixMaxSuitConditionalRefinement.Settings(
                SixMaxSuitConditionalRefinement.Priority.BALANCED_GAP_AND_REACH, 3, budgets, .001);
    }

    @Test
    void maxminEmptySelectionExportsOnlyDiagnosticsAndCannotBeScreened(@TempDir Path dir)
            throws Exception {
        var empty =
                SixMaxHistoryPhysicalMaxmin.refine(
                        accepted, new SixMaxHistoryPhysicalMaxmin.Settings(3, .001), b -> {});
        assertFalse(empty.report().accepted());
        assertEquals(List.of("NO_MATERIAL_CASES_ABOVE_TARGET"), empty.report().rejectionReasons());
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalMaxmin.write(policy, report, empty);
        assertFalse(Files.exists(policy));
        assertEquals(
                empty.report(),
                SixMaxHistoryPhysicalMaxmin.replay(policy, report, accepted).report());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMaxminDecisionStability.screen(
                                empty, SixMaxSuitDecisionStability.Settings.standard(), b -> {}));
        Files.writeString(policy, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMaxmin.replay(policy, report, accepted));
    }

    @Test
    void repairsAllPhysicalCasesButPreservesEveryOtherRowAndJointIterationCount() {
        assertTrue(accepted.report().accepted(), accepted.report().rejectionReasons().toString());
        var original = predecessor.checkpoint().solution();
        var derived = accepted.artifact().orElseThrow();
        assertEquals(3, accepted.report().branches().size());
        assertFalse(derived.trainerAdmission());
        assertEquals(original.iterations(), derived.solution().iterations());
        assertEquals(original.strategy().keySet(), derived.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                SixMaxPreflopContinuationFeedback.preflopPolicy(derived.solution()));
        for (var row : original.strategy().entrySet())
            if (!row.getKey().contains("board:"))
                assertEquals(row.getValue(), derived.solution().strategy().get(row.getKey()));
        assertEquals(24, accepted.report().replacedInformationSets());
        for (var b : accepted.report().branches()) {
            assertEquals(6.5, b.before().nashConvBb(), 1e-12);
            assertTrue(b.after().nashConvBb() <= .001);
            assertEquals(4, b.posteriorJointDeals());
            assertEquals("TARGET_MET", b.status());
            assertTrue(
                    b.trials().stream()
                            .allMatch(
                                    t ->
                                            t.traversal().sampledChanceNodes() == 0
                                                    && t.traversal().baselineCorrections() == 0));
        }
    }

    @Test
    void rejectsInsufficientBudgetsAndExportsDiagnosticsOnly(@TempDir Path dir) throws Exception {
        var rejected =
                SixMaxHistoryPhysicalConditionalRefinement.refine(
                        predecessor, settings(List.of(1)), b -> {});
        assertFalse(rejected.report().accepted());
        assertTrue(rejected.artifact().isEmpty());
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalConditionalRefinement.write(policy, report, rejected);
        assertFalse(Files.exists(policy));
        assertTrue(Files.exists(report));
        assertEquals(
                rejected.report(),
                SixMaxHistoryPhysicalConditionalRefinement.replay(policy, report, predecessor)
                        .report());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalDecisionStability.screen(
                                rejected,
                                SixMaxSuitDecisionStability.Settings.standard(),
                                b -> {}));
        Files.writeString(policy, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalConditionalRefinement.replay(
                                policy, report, predecessor));
    }

    @Test
    void exactReplayRejectsAlteredPolicyTrialOrParentLineage(@TempDir Path dir) throws Exception {
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalConditionalRefinement.write(policy, report, accepted);
        assertEquals(
                accepted.report(),
                SixMaxHistoryPhysicalConditionalRefinement.replay(policy, report, predecessor)
                        .report());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalConditionalRefinement.write(policy, report, accepted));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(accepted.report());
        tree.put("predecessorReportHash", "0".repeat(64));
        var bad = dir.resolve("bad.json");
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalConditionalRefinement.replay(policy, bad, predecessor));
        tree.put("predecessorReportHash", accepted.report().predecessorReportHash());
        ((com.fasterxml.jackson.databind.node.ObjectNode)
                        tree.path("branches").get(0).path("trials").get(0))
                .put("localSolutionHash", "0".repeat(64));
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalConditionalRefinement.replay(policy, bad, predecessor));
        var cpTree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper()
                                .valueToTree(accepted.artifact().orElseThrow());
        cpTree.put("solutionHash", "0".repeat(64));
        Files.writeString(bad, cpTree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalConditionalRefinement.replay(bad, report, predecessor));
    }

    @Test
    void screensOwnCardQuestionsAndRejectsModifiedCoverageOrAdmission(@TempDir Path dir)
            throws Exception {
        var screen =
                SixMaxHistoryPhysicalDecisionStability.screen(
                        accepted,
                        new SixMaxSuitDecisionStability.Settings(3, List.of(500, 1000), 2),
                        b -> {});
        assertEquals(3, screen.branches().size());
        assertFalse(screen.trainerAdmission());
        assertTrue(
                screen.branches().stream().allMatch(SixMaxSuitDecisionStability.Branch::retained));
        assertEquals(
                screen.retainedPhysicalReach() / screen.allHeadsUpReach(),
                screen.retainedAllHeadsUpFraction(),
                1e-12);
        for (var b : screen.branches()) {
            assertEquals(
                    2,
                    b.questions().stream()
                            .filter(SixMaxSuitDecisionStability.Question::material)
                            .map(q -> q.primary().actor())
                            .distinct()
                            .count());
            assertTrue(
                    b.questions().stream()
                            .filter(SixMaxSuitDecisionStability.Question::material)
                            .allMatch(q -> q.stable() && q.references().size() == 2));
        }
        var path = dir.resolve("screen.json.gz");
        SixMaxHistoryPhysicalDecisionStability.write(path, screen);
        assertEquals(screen, SixMaxHistoryPhysicalDecisionStability.replay(path, accepted));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(screen);
        tree.put("retainedPhysicalReach", 0);
        tree.put("retainedAllHeadsUpFraction", 0);
        var bad = dir.resolve("bad.json");
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalDecisionStability.replay(bad, accepted));
        tree.put("trainerAdmission", true);
        Files.writeString(bad, tree.toString());
        assertThrows(
                Exception.class,
                () -> SixMaxHistoryPhysicalDecisionStability.replay(bad, accepted));
    }

    @Test
    void accurateSubsetCannotRelabelWeakCasesOrPublishAnEmptySelection() throws Exception {
        var result =
                SixMaxHistoryPhysicalConditionalRefinement.refine(
                        predecessor, settings(List.of(500, 1000)), true, b -> {});
        assertEquals(
                SixMaxHistoryPhysicalConditionalRefinement.ACCURATE_SELECTION,
                result.report().selection());
        assertTrue(result.report().branches().isEmpty());
        assertFalse(result.report().accepted());
        assertTrue(result.artifact().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalConditionalRefinement.refine(
                                predecessor,
                                new SixMaxSuitConditionalRefinement.Settings(
                                        SixMaxSuitConditionalRefinement.Priority.LARGEST_GAP,
                                        1,
                                        List.of(500),
                                        .001),
                                b -> {}));
    }

    @Test
    void commandLineRejectsAliasesAndExistingOutputsBeforeLoadingInputs(@TempDir Path dir)
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalConditionalRefinementMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalDecisionStabilityMain.main(new String[0]));
        var paths = new ArrayList<String>();
        for (int i = 0; i < 8; i++) paths.add(dir.resolve("path" + i + ".json").toString());
        var refine = new ArrayList<>(List.of("refine"));
        refine.addAll(paths.subList(0, 7));
        refine.addAll(List.of("64", "500,1000", ".001", "ALL_MATERIAL"));
        Files.writeString(Path.of(paths.get(5)), "existing");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalConditionalRefinementMain.main(
                                refine.toArray(String[]::new)));
        assertEquals("existing", Files.readString(Path.of(paths.get(5))));
        refine.set(1, paths.get(5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalConditionalRefinementMain.main(
                                refine.toArray(String[]::new)));
        var screen = new ArrayList<>(List.of("screen"));
        screen.addAll(paths);
        Files.writeString(Path.of(paths.get(7)), "existing screen");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalDecisionStabilityMain.main(
                                screen.toArray(String[]::new)));
        assertEquals("existing screen", Files.readString(Path.of(paths.get(7))));
    }
}
