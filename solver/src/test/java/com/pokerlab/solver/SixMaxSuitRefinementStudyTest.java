package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuitRefinementStudyTest {
    @Test
    void publicCardsConcealFoldedWorldsAndRecoverOriginalChipAccounting() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var table = SixMaxSuitRefinementPayoffTableTest.table(source, parent);
        var game =
                new SixMaxSuitRefinementFlopGame(
                        source, parent, table, SixMaxRankTextureFlopGameTest.menu());
        int spades =
                table.observations()
                        .indexOf(
                                SixMaxSuitRefinementPayoffTable.observe(
                                        SixMaxSuitRefinementPayoffTableTest.cards("2s 3s As"),
                                        table.refinedSignals()));
        int clubs =
                table.observations()
                        .indexOf(
                                SixMaxSuitRefinementPayoffTable.observe(
                                        SixMaxSuitRefinementPayoffTableTest.cards("2c 3c Ac"),
                                        table.refinedSignals()));
        var first = history(game, 0);
        var second = history(game, 1);
        var revealed = new SixMaxRankTextureFlopGame.State(first.preflop(), spades, "");
        var differentSuit = new SixMaxRankTextureFlopGame.State(second.preflop(), clubs, "");
        assertNotEquals(game.informationSet(revealed), game.informationSet(differentSuit));
        var common =
                game.chanceOutcomes(first).stream()
                        .filter(r -> table.deals().get(1).flopCounts().get(r.state().signal()) > 0)
                        .findFirst()
                        .orElseThrow()
                        .state();
        assertEquals(
                game.informationSet(common),
                game.informationSet(
                        new SixMaxRankTextureFlopGame.State(
                                second.preflop(), common.signal(), "")));
        assertTrue(game.informationSet(revealed).contains(":postflop:suit-refinement:"));
        assertTrue(game.informationSet(revealed).contains(":board:2s3sAs:"));
        for (int world = 0; world < 2; world++) {
            var outcomes = game.chanceOutcomes(history(game, world));
            assertEquals(1, outcomes.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
            for (var outcome : outcomes)
                for (String actions : List.of("kk", "bf", "bc", "kbf", "kbc")) {
                    var state = outcome.state();
                    for (char action : actions.toCharArray())
                        state = game.afterAction(state, "" + action);
                    var utilities = game.terminalUtilities(state);
                    assertEquals(0, java.util.Arrays.stream(utilities).sum(), 1e-12);
                    assertEquals(-.5, utilities[4], 0);
                    if (!actions.endsWith("f")) {
                        assertEquals(.25, utilities[3], 1e-12);
                        assertEquals(.25, utilities[5], 1e-12);
                    }
                }
        }
        var baseline = game.checkdownBaseline(source.solution());
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(game.sourceGame(), source.solution()),
                MultiPlayerStrategyEvaluator.utilities(game, baseline),
                1e-9);
        var rankBaseline =
                new SixMaxRankTextureFlopGame(source, parent, SixMaxRankTextureFlopGameTest.menu())
                        .checkdownBaseline(source.solution());
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPlayerStrategyCompletion.uniformAtUnseen(game, rankBaseline, 1000000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.isTerminal(
                                new SixMaxRankTextureFlopGame.State(
                                        first.preflop(), table.observations().size(), "")));
    }

    static SixMaxRankTextureFlopGame.State history(SixMaxSuitRefinementFlopGame game, int world) {
        var state = game.chanceOutcomes(game.initialState()).get(world).state();
        for (var action : SixMaxConnectedPreflopGameTest.HISTORY)
            state = game.afterAction(state, action.action());
        return state;
    }

    @Test
    void freshJointSolvingIsPruningInvariantAndStrictReplayBindsEveryInput(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var table = SixMaxSuitRefinementPayoffTableTest.table(source, parent);
        var menu = SixMaxRankTextureFlopGameTest.menu();
        var plain =
                SixMaxSuitRefinementStudy.solve(
                        source, parent, table, menu, 3, MultiPlayerCfrSolver.InactivePruning.NONE);
        var pruned =
                SixMaxSuitRefinementStudy.solve(
                        source,
                        parent,
                        table,
                        menu,
                        3,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        assertEquals(plain.checkpoint().solution(), pruned.checkpoint().solution());
        assertTrue(pruned.inactiveUtilityPrunedNodes() > 0);
        assertEquals(0, pruned.traversal().sampledChanceNodes());
        assertEquals(SixMaxSuitRefinementStudy.MODEL, plain.report().model());
        assertEquals(SixMaxSuitRefinementStudy.QUALITY_SCOPE, plain.report().qualityScope());
        assertEquals("VALIDATION_ONLY", plain.report().publicationStatus());
        assertEquals(source.solution().strategy().size(), plain.report().preflopInformationSets());
        assertTrue(plain.report().physicalObservations() > 0);
        assertTrue(
                plain.report().checkdownRecoveryErrorBb().stream()
                        .allMatch(e -> Math.abs(e) < 1e-9));
        assertTrue(
                plain
                        .report()
                        .jointlySolvedDiagnostics()
                        .parentWitness()
                        .embeddingErrorsBb()
                        .stream()
                        .allMatch(e -> Math.abs(e) < 1e-9));
        var cp = temp.resolve("cp.json.gz");
        var report = temp.resolve("report.json.gz");
        SixMaxSuitRefinementStudy.write(cp, plain.checkpoint(), source, parent, table);
        var bytes = Files.readAllBytes(cp);
        var loaded = SixMaxSuitRefinementStudy.read(cp, source, parent, table);
        assertEquals(plain.checkpoint(), loaded);
        SixMaxSuitRefinementStudy.write(cp, loaded, source, parent, table);
        assertArrayEquals(bytes, Files.readAllBytes(cp));
        SixMaxSuitRefinementStudy.writeReport(report, plain.report());
        assertEquals(
                plain.report(),
                SixMaxSuitRefinementStudy.replay(report, source, parent, table, loaded));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().readTree(SixMaxTextureStudy.json(loaded));
        for (String field :
                List.of(
                        "sourcePackHash",
                        "sourceSpotHash",
                        "parentRankTableHash",
                        "payoffTableHash",
                        "gameHash",
                        "solutionHash")) {
            String original = tree.get(field).asText();
            tree.put(field, "0".repeat(64));
            Files.writeString(temp.resolve("bad.json"), tree.toString());
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxSuitRefinementStudy.read(
                                    temp.resolve("bad.json"), source, parent, table));
            tree.put(field, original);
        }
        tree.put("model", SixMaxRankTextureStudy.MODEL);
        Files.writeString(temp.resolve("bad.json"), tree.toString());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSuitRefinementStudy.read(
                                temp.resolve("bad.json"), source, parent, table));
        var partial =
                SixMaxSuitRefinementStudy.checkpoint(
                        source,
                        table,
                        SixMaxSuitRefinementStudy.rebuild(source, parent, table, loaded),
                        source.solution(),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitRefinementStudy.rebuild(source, parent, table, partial));
        var foreign = new LinkedHashMap<>(loaded.solution().strategy());
        foreign.put("0:foreign", Map.of("fold", 1.0));
        var bad =
                SixMaxSuitRefinementStudy.checkpoint(
                        source,
                        table,
                        SixMaxSuitRefinementStudy.rebuild(source, parent, table, loaded),
                        new CfrSolution(3, foreign),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitRefinementStudy.rebuild(source, parent, table, bad));
        var changedReport =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper()
                                .readTree(SixMaxTextureStudy.json(plain.report()));
        changedReport.put("iterations", 4);
        Files.writeString(temp.resolve("bad-report.json"), changedReport.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementStudy.replay(
                                temp.resolve("bad-report.json"), source, parent, table, loaded));
    }

    @Test
    void conditionalPhysicalPosteriorAndBestResponsesMatchBruteForce() throws Exception {
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
        var policy = new CfrSolution(1, mistakes);
        var diagnostics = SixMaxFlopConditionalDiagnostics.assess(game.core(), policy);
        var history = diagnostics.histories().getFirst();
        var row =
                history.signals().stream()
                        .filter(s -> s.observationKey().equals("board:2s3sAs"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(.75 / 9880, row.signalProbabilityGivenHistory(), 1e-15);
        assertEquals(1, row.posteriorPrivateDeals());
        assertEquals(6.5, row.quality().nashConvBb(), 1e-12);
        assertEquals(6.5, diagnostics.parentWitness().reachWeightedLocalNashConvBb(), 1e-12);
        SixMaxRankTextureConditionalAuditTest.bruteCheck(
                game,
                game.sourceGame(),
                SixMaxSuitRefinementPayoffTable.view(table),
                policy,
                history.history(),
                row.observation(),
                row.signalProbabilityGivenHistory(),
                row.quality());
    }

    @Test
    void namedBoardAuditRejectsSyntheticPayoffsAndUndeclaredOrDuplicateBoards() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var parent = SixMaxRankTextureFlopGameTest.table(source);
        var table = SixMaxSuitRefinementPayoffTableTest.table(source, parent);
        var game =
                new SixMaxSuitRefinementFlopGame(
                        source, parent, table, SixMaxRankTextureFlopGameTest.menu());
        var cp =
                SixMaxSuitRefinementStudy.checkpoint(
                        source,
                        table,
                        game,
                        game.checkdownBaseline(source.solution()),
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        var physical = SixMaxSuitRefinementPayoffTableTest.cards("2s 3s As");
        assertThrows(
                IllegalStateException.class,
                () ->
                        SixMaxSuitRefinementBoardAudit.assess(
                                source, parent, table, cp, List.of(physical)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementBoardAudit.assess(
                                source,
                                parent,
                                table,
                                cp,
                                List.of(SixMaxSuitRefinementPayoffTableTest.cards("4s 5s 6s"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementBoardAudit.assess(
                                source, parent, table, cp, List.of(physical, physical)));
        // Physical generation cannot silently inherit the fixture's invented all-tie values.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementPayoffTable.generate(
                                source, parent, table.refinedSignals(), n -> {}));
    }

    @Test
    void cliRejectsAliasesAndInvalidBudgetsBeforeWriting(@TempDir Path temp) throws Exception {
        var sentinel = temp.resolve("input.json");
        Files.writeString(sentinel, "sentinel");
        var alias = temp.resolve("alias.json");
        Files.createLink(alias, sentinel);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementStudyMain.main(
                                new String[] {
                                    "solve",
                                    sentinel.toString(),
                                    alias.toString(),
                                    "table",
                                    "cp",
                                    "report",
                                    "1",
                                    "1",
                                    ".5",
                                    "NONE"
                                }));
        for (String iterations : List.of("0", "1001"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxSuitRefinementStudyMain.main(
                                    new String[] {
                                        "solve",
                                        sentinel.toString(),
                                        "parent",
                                        "table",
                                        "cp",
                                        "report",
                                        iterations,
                                        "1",
                                        ".5",
                                        "NONE"
                                    }));
        assertEquals("sentinel", Files.readString(sentinel));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuitRefinementStudyMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementPayoffTableMain.main(
                                new String[] {
                                    sentinel.toString(), "parent", alias.toString(), "2s3s4s"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitRefinementBoardAuditMain.main(
                                new String[] {
                                    "audit",
                                    sentinel.toString(),
                                    "parent",
                                    "table",
                                    "cp",
                                    alias.toString(),
                                    "2s3sAs"
                                }));
        assertEquals("sentinel", Files.readString(sentinel));
    }
}
