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
    private static SixMaxHistoryPhysicalSequenceForm.Result sequenceForm;
    private static SixMaxHistoryPhysicalAffineSequenceForm.Result affineSequenceForm;
    private static SixMaxPreflopSolutionPack storageSource;
    private static SixMaxRankTexturePayoffTable.Artifact storageParent;
    private static SixMaxHistoryPhysicalPayoffTable.Verified storageOriginal;
    private static SixMaxHistoryPhysicalCompactStorage.Verified compact;
    private static SixMaxHistoryPhysicalStorageAudit.Result storageAudit;

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
        storageSource = source;
        storageParent = parent;
        storageOriginal = table;
        compact = SixMaxHistoryPhysicalCompactStorage.project(table);
        storageAudit =
                SixMaxHistoryPhysicalStorageAudit.assess(
                        source, parent, table, predecessor, compact);
        accepted =
                SixMaxHistoryPhysicalConditionalRefinement.refine(
                        predecessor, settings(List.of(500, 1000)), b -> {});
        maxmin =
                SixMaxHistoryPhysicalMaxmin.refine(
                        predecessor, new SixMaxHistoryPhysicalMaxmin.Settings(3, .001), b -> {});
        sequenceForm =
                SixMaxHistoryPhysicalSequenceForm.refine(
                        predecessor,
                        new SixMaxHistoryPhysicalSequenceForm.Settings(3, .001),
                        b -> {});
        affineSequenceForm =
                SixMaxHistoryPhysicalAffineSequenceForm.refine(
                        predecessor,
                        new SixMaxHistoryPhysicalAffineSequenceForm.Settings(3, .001),
                        b -> {});
    }

    @Test
    void sequenceFormRepairsFoldMistakesAndPreservesEveryFrozenRow() {
        var r = sequenceForm.report();
        assertTrue(r.accepted(), r.rejectionReasons().toString());
        var a = sequenceForm.artifact().orElseThrow();
        assertFalse(a.trainerAdmission());
        assertEquals(FiniteTwoPlayerSequenceForm.ALGORITHM, a.algorithm());
        var original = predecessor.checkpoint().solution();
        assertEquals(original.iterations(), a.solution().iterations());
        assertEquals(original.strategy().keySet(), a.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                SixMaxPreflopContinuationFeedback.preflopPolicy(a.solution()));
        assertEquals(3, r.branches().size());
        for (var b : r.branches()) {
            assertEquals(6.5, b.before().nashConvBb(), 1e-12);
            assertTrue(b.after().nashConvBb() < 1e-8);
            assertTrue(b.solve().firstLp().work().basisFactorizations() > 0);
            assertTrue(b.solve().secondLp().absoluteDualityGap() < 1e-8);
        }
        for (var row : original.strategy().entrySet())
            if (!row.getKey().contains("board:"))
                assertEquals(row.getValue(), a.solution().strategy().get(row.getKey()));
    }

    @Test
    void sequenceFormReplaysOriginalFlowsWorkAndLineageRatherThanTrustingSavedCertificates(
            @TempDir Path dir) throws Exception {
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalSequenceForm.write(policy, report, sequenceForm);
        assertEquals(
                sequenceForm.report(),
                SixMaxHistoryPhysicalSequenceForm.replay(policy, report, predecessor).report());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceForm.write(policy, report, sequenceForm));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var bad = dir.resolve("bad.json");
        for (String field :
                List.of("snapshotHash", "reductionHash", "work", "flow", "predecessor", "policy")) {
            var tree =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            mapper.valueToTree(sequenceForm.report());
            var solve =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            tree.path("branches").get(0).path("solve");
            switch (field) {
                case "snapshotHash", "reductionHash" -> solve.put(field, "0".repeat(64));
                case "work" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        solve.path("firstLp").path("work"))
                                .put("arithmeticWork", 0);
                case "flow" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode)
                                        solve.path("firstFlow").path("realization"))
                                .set(0, mapper.valueToTree(.5));
                case "predecessor" -> tree.put("predecessorReportHash", "0".repeat(64));
                case "policy" -> tree.put("candidateSolutionHash", "0".repeat(64));
                default -> throw new AssertionError();
            }
            Files.writeString(bad, tree.toString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxHistoryPhysicalSequenceForm.replay(policy, bad, predecessor));
        }
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.valueToTree(sequenceForm.artifact().orElseThrow());
        tree.put("trainerAdmission", true);
        Files.writeString(bad, tree.toString());
        assertThrows(
                Exception.class,
                () -> SixMaxHistoryPhysicalSequenceForm.replay(bad, report, predecessor));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalSequenceForm.Settings(65, .001));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalSequenceForm.Settings(1, Double.NaN));
    }

    @Test
    void sequenceFormEmptySelectionExportsOnlyDiagnosticsAndCannotBeScreened(@TempDir Path dir)
            throws Exception {
        var empty =
                SixMaxHistoryPhysicalSequenceForm.refine(
                        accepted, new SixMaxHistoryPhysicalSequenceForm.Settings(3, .001), b -> {});
        assertEquals(List.of("NO_MATERIAL_CASES_ABOVE_TARGET"), empty.report().rejectionReasons());
        assertFalse(empty.report().accepted());
        assertTrue(empty.artifact().isEmpty());
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalSequenceForm.write(policy, report, empty);
        assertFalse(Files.exists(policy));
        assertEquals(
                empty.report(),
                SixMaxHistoryPhysicalSequenceForm.replay(policy, report, accepted).report());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalSequenceFormDecisionStability.screen(
                                empty, SixMaxSuitDecisionStability.Settings.standard(), b -> {}));
        Files.writeString(policy, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceForm.replay(policy, report, accepted));
    }

    @Test
    void sequenceFormClisRejectAliasesHardlinksAndExistingOutputsBeforeLoading(@TempDir Path dir)
            throws Exception {
        String[] args = {
            "refine",
            dir.resolve("source").toString(),
            dir.resolve("rank").toString(),
            dir.resolve("table").toString(),
            dir.resolve("cp").toString(),
            dir.resolve("study").toString(),
            dir.resolve("policy").toString(),
            dir.resolve("report").toString(),
            "3",
            ".001"
        };
        Files.writeString(Path.of(args[6]), "preserve");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceFormMain.main(args));
        assertEquals("preserve", Files.readString(Path.of(args[6])));
        args[6] = args[7];
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceFormMain.main(args));
        assertFalse(Files.exists(Path.of(args[7])));
        var source = Path.of(args[1]);
        Files.writeString(source, "protected");
        var hardlink = dir.resolve("hardlink");
        Files.createLink(hardlink, source);
        args[6] = hardlink.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceFormMain.main(args));
        assertEquals("protected", Files.readString(source));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceFormMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalSequenceFormDecisionStabilityMain.main(new String[0]));
        var screen = new ArrayList<>(List.of("screen"));
        screen.addAll(Arrays.asList(args).subList(1, 6));
        screen.add(dir.resolve("missing-policy").toString());
        screen.add(dir.resolve("missing-report").toString());
        screen.add(source.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalSequenceFormDecisionStabilityMain.main(
                                screen.toArray(String[]::new)));
    }

    @Test
    void affineSequenceFormRepairsFoldMistakesAndPreservesEveryFrozenRow() {
        var r = affineSequenceForm.report();
        assertTrue(r.accepted(), r.rejectionReasons().toString());
        var a = affineSequenceForm.artifact().orElseThrow();
        assertFalse(a.trainerAdmission());
        assertEquals(FiniteTwoPlayerAffineSequenceForm.ALGORITHM, a.algorithm());
        var original = predecessor.checkpoint().solution();
        assertEquals(original.iterations(), a.solution().iterations());
        assertEquals(original.strategy().keySet(), a.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                SixMaxPreflopContinuationFeedback.preflopPolicy(a.solution()));
        assertEquals(3, r.branches().size());
        for (var b : r.branches()) {
            assertEquals(6.5, b.before().nashConvBb(), 1e-12);
            assertTrue(b.after().nashConvBb() < 1e-8);
            assertTrue(b.solve().firstLp().work().basisFactorizations() > 0);
            assertTrue(b.solve().secondLp().absoluteDualityGap() < 1e-8);
        }
        for (var row : original.strategy().entrySet())
            if (!row.getKey().contains("board:"))
                assertEquals(row.getValue(), a.solution().strategy().get(row.getKey()));
    }

    @Test
    void affineSequenceFormReplaysOriginalFlowsWorkAndLineageRatherThanTrustingSavedCertificates(
            @TempDir Path dir) throws Exception {
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalAffineSequenceForm.write(policy, report, affineSequenceForm);
        assertEquals(
                affineSequenceForm.report(),
                SixMaxHistoryPhysicalAffineSequenceForm.replay(policy, report, predecessor)
                        .report());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalAffineSequenceForm.write(
                                policy, report, affineSequenceForm));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var bad = dir.resolve("bad.json");
        for (String field :
                List.of(
                        "snapshotHash",
                        "affineReductionHash",
                        "work",
                        "flow",
                        "predecessor",
                        "policy",
                        "projection",
                        "constant",
                        "dimensions",
                        "reductionWork")) {
            var tree =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            mapper.valueToTree(affineSequenceForm.report());
            var solve =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            tree.path("branches").get(0).path("solve");
            switch (field) {
                case "snapshotHash", "affineReductionHash" -> solve.put(field, "0".repeat(64));
                case "projection" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode)
                                        solve.path("firstProjection").path("offset"))
                                .set(0, mapper.valueToTree(0));
                case "constant" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        solve.path("projectedPayoff"))
                                .put("constant", 123.0);
                case "dimensions" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode) solve.path("firstLp"))
                                .put("variables", 0);
                case "reductionWork" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        solve.path("reductionWork"))
                                .put("chargedUnits", 0);
                case "work" ->
                        ((com.fasterxml.jackson.databind.node.ObjectNode)
                                        solve.path("firstLp").path("work"))
                                .put("arithmeticWork", 0);
                case "flow" ->
                        ((com.fasterxml.jackson.databind.node.ArrayNode)
                                        solve.path("firstFlow").path("realization"))
                                .set(0, mapper.valueToTree(.5));
                case "predecessor" -> tree.put("predecessorReportHash", "0".repeat(64));
                case "policy" -> tree.put("candidateSolutionHash", "0".repeat(64));
                default -> throw new AssertionError();
            }
            Files.writeString(bad, tree.toString());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxHistoryPhysicalAffineSequenceForm.replay(policy, bad, predecessor));
        }
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.valueToTree(affineSequenceForm.artifact().orElseThrow());
        tree.put("trainerAdmission", true);
        Files.writeString(bad, tree.toString());
        assertThrows(
                Exception.class,
                () -> SixMaxHistoryPhysicalAffineSequenceForm.replay(bad, report, predecessor));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalAffineSequenceForm.Settings(65, .001));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxHistoryPhysicalAffineSequenceForm.Settings(1, Double.NaN));
    }

    @Test
    void affineSequenceFormEmptySelectionExportsOnlyDiagnosticsAndCannotBeScreened(
            @TempDir Path dir) throws Exception {
        var empty =
                SixMaxHistoryPhysicalAffineSequenceForm.refine(
                        accepted,
                        new SixMaxHistoryPhysicalAffineSequenceForm.Settings(3, .001),
                        b -> {});
        assertEquals(List.of("NO_MATERIAL_CASES_ABOVE_TARGET"), empty.report().rejectionReasons());
        assertFalse(empty.report().accepted());
        assertTrue(empty.artifact().isEmpty());
        var policy = dir.resolve("policy.json.gz");
        var report = dir.resolve("report.json.gz");
        SixMaxHistoryPhysicalAffineSequenceForm.write(policy, report, empty);
        assertFalse(Files.exists(policy));
        assertEquals(
                empty.report(),
                SixMaxHistoryPhysicalAffineSequenceForm.replay(policy, report, accepted).report());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalAffineSequenceFormDecisionStability.screen(
                                empty, SixMaxSuitDecisionStability.Settings.standard(), b -> {}));
        Files.writeString(policy, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalAffineSequenceForm.replay(policy, report, accepted));
    }

    @Test
    void affineSequenceFormClisRejectAliasesHardlinksAndExistingOutputsBeforeLoading(
            @TempDir Path dir) throws Exception {
        String[] args = {
            "refine",
            dir.resolve("source").toString(),
            dir.resolve("rank").toString(),
            dir.resolve("table").toString(),
            dir.resolve("cp").toString(),
            dir.resolve("study").toString(),
            dir.resolve("policy").toString(),
            dir.resolve("report").toString(),
            "3",
            ".001"
        };
        Files.writeString(Path.of(args[6]), "preserve");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalAffineSequenceFormMain.main(args));
        assertEquals("preserve", Files.readString(Path.of(args[6])));
        args[6] = args[7];
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalAffineSequenceFormMain.main(args));
        assertFalse(Files.exists(Path.of(args[7])));
        var source = Path.of(args[1]);
        Files.writeString(source, "protected");
        var hardlink = dir.resolve("hardlink");
        Files.createLink(hardlink, source);
        args[6] = hardlink.toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalAffineSequenceFormMain.main(args));
        assertEquals("protected", Files.readString(source));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalAffineSequenceFormMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalAffineSequenceFormDecisionStabilityMain.main(
                                new String[0]));
        var screen = new ArrayList<>(List.of("screen"));
        screen.addAll(Arrays.asList(args).subList(1, 6));
        screen.add(dir.resolve("missing-policy").toString());
        screen.add(dir.resolve("missing-report").toString());
        screen.add(source.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalAffineSequenceFormDecisionStabilityMain.main(
                                screen.toArray(String[]::new)));
    }

    @Test
    void compactStoragePreservesExactSyntheticVectorsChanceOrderAndBothSolverAlgorithms()
            throws Exception {
        assertEquals(
                storageOriginal.artifact(),
                SixMaxHistoryPhysicalCompactStorage.restore(compact.artifact()));
        assertFalse(storageAudit.report().trainerAdmission());
        assertEquals(predecessor.checkpoint().binding(), storageAudit.report().binding());
        assertEquals(3, storageAudit.report().localControls().size());
        for (var control : storageAudit.report().localControls()) {
            assertEquals(8, control.freshCfrIterations());
            assertTrue(control.freshCfrTraversal().visitedNodes() > 0);
            assertEquals(0, control.freshCfrTraversal().sampledChanceNodes());
        }
        assertEquals(
                predecessor.checkpoint().solution().strategy().size(),
                storageAudit.report().informationSets());
        assertEquals(
                predecessor.core().completeTreeStates(),
                compact.core(storageSource, storageParent).completeTreeStates());
    }

    @Test
    void compactStorageRequiresExactReplayAndCannotOverwriteOutputs(@TempDir Path dir)
            throws Exception {
        Path payoff = dir.resolve("compact.json.gz"), audit = dir.resolve("audit.json");
        SixMaxHistoryPhysicalCompactStorage.write(payoff, compact);
        SixMaxHistoryPhysicalStorageAudit.write(audit, storageAudit);
        assertEquals(
                compact.hash(),
                SixMaxHistoryPhysicalCompactStorage.replay(payoff, storageOriginal).hash());
        assertEquals(
                storageAudit.report(),
                SixMaxHistoryPhysicalStorageAudit.replay(
                                audit,
                                storageSource,
                                storageParent,
                                storageOriginal,
                                predecessor,
                                compact)
                        .report());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalCompactStorage.write(payoff, compact));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalStorageAudit.write(audit, storageAudit));
    }

    @Test
    void validLookingPayoutTamperingCannotGrantCompactVerification(@TempDir Path dir)
            throws Exception {
        var node = SixMaxTexturePayoffTable.mapper().valueToTree(compact.artifact());
        var wins =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        node.at("/histories/0/deals/0/firstWins");
        int nonzero = 0;
        while (compact.artifact().histories().getFirst().deals().getFirst().counts().get(nonzero)
                == 0) nonzero++;
        // Syntactically valid payout that breaks exact original provenance.
        var ties =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        node.at("/histories/0/deals/0/ties");
        ties.set(
                nonzero,
                com.fasterxml.jackson.databind.node.LongNode.valueOf(
                        ties.get(nonzero).asLong() - 1));
        wins.set(nonzero, com.fasterxml.jackson.databind.node.LongNode.valueOf(1));
        Path path = dir.resolve("forged.json");
        Files.writeString(path, SixMaxTextureStudy.json(node));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalCompactStorage.replay(path, storageOriginal));
    }

    @Test
    void compactReaderRejectsIdentitySupportAndByteLimitTampering(@TempDir Path dir)
            throws Exception {
        for (String pointer : List.of("/trainerAdmission", "/originalPayoffHash", "/encoding")) {
            var node =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                            SixMaxTexturePayoffTable.mapper().valueToTree(compact.artifact());
            if (pointer.equals("/trainerAdmission")) node.put("trainerAdmission", true);
            else
                node.put(
                        pointer.substring(1),
                        pointer.equals("/encoding") ? "OTHER" : "0".repeat(64));
            Path path = dir.resolve(pointer.substring(1) + ".json");
            Files.writeString(path, SixMaxTextureStudy.json(node));
            assertThrows(
                    Exception.class,
                    () -> SixMaxHistoryPhysicalCompactStorage.replay(path, storageOriginal));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHistoryPhysicalCompactStorage.History(
                                "h",
                                3,
                                List.of(1, 0),
                                compact.artifact().histories().getFirst().deals()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxHistoryPhysicalCompactStorage.Deal(
                                List.of("a", "b", "c", "d", "e", "f"),
                                List.of(9880L),
                                List.of(Long.MAX_VALUE),
                                List.of(0L)));
        Path huge = dir.resolve("huge.json.gz");
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(huge))) {
            gzip.write(new byte[SixMaxHistoryPhysicalCompactStorage.MAX_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalCompactStorage.replay(huge, storageOriginal));
    }

    @Test
    void auditReconstructsCountersInsteadOfTrustingSavedClaims(@TempDir Path dir) throws Exception {
        var node =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(storageAudit.report());
        node.put("comparedCounts", storageAudit.report().comparedCounts() + 1);
        Path path = dir.resolve("audit.json");
        Files.writeString(path, SixMaxTextureStudy.json(node));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalStorageAudit.replay(
                                path,
                                storageSource,
                                storageParent,
                                storageOriginal,
                                predecessor,
                                compact));
    }

    @Test
    void compactViewIsImmutableAndRejectsAbsentOrWrongPlayerQueries() throws Exception {
        var game = compact.core(storageSource, storageParent);
        var h = storageOriginal.artifact().histories().getFirst();
        var view = game.payoffView();
        assertThrows(
                UnsupportedOperationException.class,
                () -> view.counts(h.publicHistory(), 0).set(0, 0L));
        assertThrows(IllegalArgumentException.class, () -> view.counts(0));
        assertThrows(IllegalArgumentException.class, () -> view.counts("unknown", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> view.share(h.publicHistory(), 0, h.activeMask(), -1, 0));
        assertThrows(
                IllegalArgumentException.class, () -> view.share(h.publicHistory(), 0, 63, 0, 0));
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> view.key(compact.artifact().observations().size()));
        for (int o = 0; o < h.deals().getFirst().flopCounts().size(); o++) {
            if (h.deals().getFirst().flopCounts().get(o) != 0) continue;
            int absent = o;
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            view.share(
                                    h.publicHistory(),
                                    0,
                                    h.activeMask(),
                                    Integer.numberOfTrailingZeros(h.activeMask()),
                                    absent));
        }
    }

    @Test
    void storageCliChecksAliasesAndExistingOutputsBeforeInputLoading(@TempDir Path dir)
            throws Exception {
        var inputs =
                new ArrayList<String>(
                        List.of(
                                "project",
                                dir.resolve("source").toString(),
                                dir.resolve("rank").toString(),
                                dir.resolve("table").toString(),
                                dir.resolve("cp").toString(),
                                dir.resolve("study").toString(),
                                dir.resolve("compact").toString(),
                                dir.resolve("audit").toString()));
        inputs.set(7, dir.resolve("other/../source").toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalStorageMain.main(inputs.toArray(String[]::new)));
        Path source = dir.resolve("source");
        Files.writeString(source, "protected");
        Path alias = dir.resolve("linked");
        Files.createLink(alias, source);
        inputs.set(7, alias.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalStorageMain.main(inputs.toArray(String[]::new)));
        inputs.set(7, dir.resolve("audit").toString());
        Files.writeString(dir.resolve("compact"), "protected output");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalStorageMain.main(inputs.toArray(String[]::new)));
        assertEquals("protected output", Files.readString(dir.resolve("compact")));
        assertEquals("protected", Files.readString(source));
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

    @AfterAll
    static void releaseFixture() {
        predecessor = null;
        accepted = null;
        maxmin = null;
        sequenceForm = null;
        affineSequenceForm = null;
        storageSource = null;
        storageParent = null;
        storageOriginal = null;
        compact = null;
        storageAudit = null;
    }
}
