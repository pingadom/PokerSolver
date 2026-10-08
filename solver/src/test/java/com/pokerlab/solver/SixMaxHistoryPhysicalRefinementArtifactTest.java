package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Complete lineage/training replay plus independent pure plans and own-card payoff formulas. */
class SixMaxHistoryPhysicalRefinementArtifactTest {
    private static final String PREFIX = "../docs/data/sixmax-staged-history-physical";
    private static SixMaxHistoryPhysicalStudy.Validated predecessor;
    private static SixMaxHistoryPhysicalConditionalRefinement.Result accepted, rejected;
    private static SixMaxHistoryPhysicalDecisionStability.Report screen;
    private static SixMaxHistoryPhysicalMaxmin.Result maxmin;
    private static SixMaxHistoryPhysicalMaxminDecisionStability.Report maxminScreen;

    @BeforeAll
    static void load() throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var parent =
                SixMaxRankTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-rank-texture-payoffs.json.gz"), source);
        var table =
                SixMaxHistoryPhysicalPayoffTable.replay(
                        Path.of(PREFIX + "-payoffs.json.gz"), source, parent);
        var cp =
                SixMaxHistoryPhysicalStudy.read(
                        Path.of(PREFIX + "-policy-500.json.gz"), source, parent, table);
        predecessor =
                SixMaxHistoryPhysicalStudy.replayValidated(
                        Path.of(PREFIX + "-study-500.json.gz"), source, parent, table, cp);
        rejected =
                SixMaxHistoryPhysicalConditionalRefinement.replay(
                        Path.of(PREFIX + "-all-64-policy.json.gz"),
                        Path.of(PREFIX + "-all-64-refinement.json.gz"),
                        predecessor);
        accepted =
                SixMaxHistoryPhysicalConditionalRefinement.replay(
                        Path.of(PREFIX + "-accurate-64-policy.json.gz"),
                        Path.of(PREFIX + "-accurate-64-refinement.json.gz"),
                        predecessor);
        screen =
                SixMaxHistoryPhysicalDecisionStability.replay(
                        Path.of(PREFIX + "-accurate-64-decisions.json.gz"), accepted);
        // Reuse the fully replayed CFR predecessor; do not repeat payoff/study reconstruction.
        maxmin =
                SixMaxHistoryPhysicalMaxmin.replay(
                        Path.of(PREFIX + "-maxmin-policy.json.gz"),
                        Path.of(PREFIX + "-maxmin-refinement.json.gz"),
                        accepted);
        maxminScreen =
                SixMaxHistoryPhysicalMaxminDecisionStability.replay(
                        Path.of(PREFIX + "-maxmin-decisions.json.gz"), maxmin);
    }

    @Test
    void ownedMaxminRepairsEveryRemainingWeakPhysicalCaseAndPreservesCfrImprovements() {
        var r = maxmin.report();
        var a = maxmin.artifact().orElseThrow();
        assertTrue(r.accepted(), r.rejectionReasons().toString());
        assertFalse(r.trainerAdmission());
        assertEquals(SixMaxHistoryPhysicalMaxmin.CFR_PREDECESSOR, r.predecessorKind());
        assertEquals(500, a.solution().iterations());
        assertEquals(20, r.branches().size());
        assertEquals(144, r.replacedInformationSets());
        assertEquals(69843, r.preservedInformationSets());
        assertEquals(
                286,
                r.branches().stream().mapToInt(b -> b.solve().matrixSolution().pivots()).sum());
        assertEquals(
                104960, r.branches().stream().mapToLong(b -> b.solve().profileNodeVisits()).sum());
        assertEquals(
                .001368808948992867, r.after().parentWitness().parentQuality().nashConvBb(), 1e-14);
        assertTrue(
                r.after().parentWitness().parentQuality().nashConvBb()
                        < r.before().parentWitness().parentQuality().nashConvBb());
        var original = accepted.artifact().orElseThrow().solution();
        assertEquals(original.strategy().keySet(), a.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                SixMaxPreflopContinuationFeedback.preflopPolicy(a.solution()));
        for (var row : original.strategy().entrySet())
            if (r.branches().stream()
                    .noneMatch(b -> row.getKey().contains(":" + b.observationKey() + ":")))
                assertEquals(row.getValue(), a.solution().strategy().get(row.getKey()));
        long physical = 0;
        for (var h : r.after().histories())
            for (var s : h.signals())
                if (s.observationKey().startsWith("board:") && s.quality() != null) {
                    physical++;
                    assertTrue(s.quality().nashConvBb() <= .001);
                }
        assertEquals(530, physical);
        for (var b : r.branches()) {
            assertTrue(b.before().nashConvBb() > .001);
            assertTrue(b.after().nashConvBb() <= 1e-9);
            assertEquals(16, b.solve().firstPlans());
            assertEquals(16, b.solve().secondPlans());
        }
    }

    @Test
    void allMaxminRepairsAgreeWithIndependentPurePlanResponsesBeforeAndAfter() {
        var game = maxmin.core();
        for (var b : maxmin.report().branches()) {
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    game.payoffView(),
                    accepted.artifact().orElseThrow().solution(),
                    b.history(),
                    b.observation(),
                    b.observationProbabilityGivenHistory(),
                    b.before());
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    game.payoffView(),
                    maxmin.artifact().orElseThrow().solution(),
                    b.history(),
                    b.observation(),
                    b.observationProbabilityGivenHistory(),
                    b.after());
        }
    }

    @Test
    void maxminScreenKeepsIndependentReferenceBudgetsAndVerifiesEveryPrimaryActionEv() {
        var game = maxmin.core();
        var policy = maxmin.artifact().orElseThrow().solution();
        assertFalse(maxminScreen.trainerAdmission());
        assertEquals(530, maxminScreen.eligibilityCounts().get("ELIGIBLE"));
        assertEquals(32, maxminScreen.branches().size());
        // Unscreened cases receive no retained credit even though every physical root is accurate.
        double retained = 0;
        for (var b : maxminScreen.branches()) {
            assertEquals(
                    List.of(500, 1000),
                    b.references().stream()
                            .map(SixMaxSuitDecisionStability.Reference::iterations)
                            .toList());
            var transition =
                    new SixMaxPolicyFlopTransition(
                            game.sourceGame(),
                            SixMaxPreflopContinuationFeedback.preflopPolicy(policy),
                            b.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(game, transition, b.observation());
            for (var decision :
                    SixMaxOneBetDecisionValues.assess(game, posterior.roots(), policy)) {
                var question =
                        b.questions().stream()
                                .filter(
                                        q ->
                                                q.primary()
                                                        .informationSet()
                                                        .equals(decision.row().informationSet()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(decision.row(), question.primary());
                if (!decision.roots().isEmpty())
                    SixMaxSuitConditionalRefinementArtifactTest.analyticValues(
                                    game, decision, policy)
                            .forEach(
                                    (action, ev) ->
                                            assertEquals(
                                                    ev,
                                                    question.primary()
                                                            .values()
                                                            .actionEvBb()
                                                            .get(action),
                                                    1e-10));
            }
            if (b.retained()) {
                retained += b.historyProbability() * b.observationProbabilityGivenHistory();
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
                                .allMatch(SixMaxSuitDecisionStability.Question::stable));
            }
        }
        assertEquals(
                retained / maxminScreen.allHeadsUpReach(),
                maxminScreen.retainedAllHeadsUpFraction(),
                1e-14);
    }

    @Test
    void maxminCannotBeReplayedAgainstTheRawJointPolicyOrAForgedDecisionLineage(@TempDir Path dir)
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMaxmin.replay(
                                Path.of(PREFIX + "-maxmin-policy.json.gz"),
                                Path.of(PREFIX + "-maxmin-refinement.json.gz"),
                                predecessor));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(maxminScreen);
        tree.put("derivedArtifactHash", "0".repeat(64));
        var bad = dir.resolve("bad.json");
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMaxminDecisionStability.replay(bad, maxmin));
    }

    @Test
    void weakBoardBudgetFailuresRemainDiagnosticsWithoutCandidateExport() {
        var r = rejected.report();
        assertFalse(r.accepted());
        assertTrue(rejected.artifact().isEmpty());
        assertEquals(List.of("SELECTED_LOCAL_TARGET_NOT_MET"), r.rejectionReasons());
        assertEquals(SixMaxHistoryPhysicalConditionalRefinement.SELECTION, r.selection());
        assertEquals(64, r.branches().size());
        assertEquals(14, r.branches().stream().filter(b -> b.after().nashConvBb() > .001).count());
        assertEquals(296, r.replacedInformationSets());
        assertEquals(69691, r.preservedInformationSets());
        assertFalse(Files.exists(Path.of(PREFIX + "-all-64-policy.json.gz")));
        assertTrue(
                r.after().parentWitness().parentQuality().nashConvBb()
                        < r.before().parentWitness().parentQuality().nashConvBb());
        assertFalse(r.trainerAdmission());
    }

    @Test
    void derivedPolicyPreservesAllFrozenAndUnselectedRowsAndCompleteSupport() {
        var r = accepted.report();
        var a = accepted.artifact().orElseThrow();
        var original = predecessor.checkpoint().solution();
        assertTrue(r.accepted());
        assertEquals(SixMaxHistoryPhysicalConditionalRefinement.ACCURATE_SELECTION, r.selection());
        assertEquals(predecessor.checkpoint().binding(), a.binding());
        assertEquals(500, a.solution().iterations());
        assertEquals(69987, a.solution().strategy().size());
        assertEquals(64, r.branches().size());
        assertEquals(344, r.replacedInformationSets());
        assertEquals(69643, r.preservedInformationSets());
        assertEquals(original.strategy().keySet(), a.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                SixMaxPreflopContinuationFeedback.preflopPolicy(a.solution()));
        int changed = 0;
        for (var row : original.strategy().entrySet()) {
            if (row.getValue().equals(a.solution().strategy().get(row.getKey()))) continue;
            changed++;
            assertTrue(row.getKey().contains(":postflop:history-physical:"));
            assertTrue(
                    r.branches().stream()
                            .filter(b -> b.replacedInformationSets() > 0)
                            .anyMatch(b -> row.getKey().contains(":" + b.observationKey() + ":")));
        }
        assertTrue(changed > 0 && changed <= 344);
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                accepted.core(), a.solution(), 1000000)
                        .addedInformationSets());
        assertEquals(7619, r.after().summary().auditedSignals());
        for (var b : r.branches()) {
            assertTrue(b.before().nashConvBb() <= .001);
            assertTrue(b.after().nashConvBb() <= .001);
            assertEquals(
                    List.of(500),
                    b.trials().stream()
                            .map(SixMaxSuitConditionalRefinement.Trial::iterations)
                            .toList());
            assertTrue(
                    b.trials().stream()
                            .allMatch(
                                    t ->
                                            t.traversal().sampledChanceNodes() == 0
                                                    && t.traversal().baselineCorrections() == 0));
        }
        for (int p = 0; p < 6; p++)
            assertEquals(0, r.after().parentWitness().embeddingErrorsBb().get(p), 1e-9);
    }

    @Test
    void independentPurePlansRecoverSelectedLocalGapsBeforeAndAfter() {
        var game = accepted.core();
        for (var b :
                accepted.report().branches().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (SixMaxSuitConditionalRefinement.Branch b) ->
                                                        b.before().nashConvBb())
                                        .reversed())
                        .limit(5)
                        .toList()) {
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    game.payoffView(),
                    predecessor.checkpoint().solution(),
                    b.history(),
                    b.observation(),
                    b.observationProbabilityGivenHistory(),
                    b.before());
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    game.payoffView(),
                    accepted.artifact().orElseThrow().solution(),
                    b.history(),
                    b.observation(),
                    b.observationProbabilityGivenHistory(),
                    b.after());
        }
    }

    @Test
    void everyScreenedPrimaryActionHasIndependentOwnCardEvAndBothSeatsMustPass() throws Exception {
        var game = accepted.core();
        var solution = accepted.artifact().orElseThrow().solution();
        assertFalse(screen.trainerAdmission());
        assertEquals(32, screen.branches().size());
        assertEquals(
                28,
                screen.branches().stream()
                        .filter(SixMaxSuitDecisionStability.Branch::retained)
                        .count());
        assertEquals(
                151,
                screen.branches().stream()
                        .flatMap(b -> b.questions().stream())
                        .filter(SixMaxSuitDecisionStability.Question::material)
                        .count());
        assertEquals(
                143,
                screen.branches().stream()
                        .flatMap(b -> b.questions().stream())
                        .filter(SixMaxSuitDecisionStability.Question::stable)
                        .count());
        assertEquals(.0013181994341322318, screen.retainedAllHeadsUpFraction(), 1e-14);
        assertEquals(.6663651701203511, screen.allHeadsUpReach(), 1e-14);
        assertEquals(
                screen.retainedPhysicalReach() / screen.allHeadsUpReach(),
                screen.retainedAllHeadsUpFraction(),
                1e-14);
        double retained = 0;
        for (var b : screen.branches()) {
            assertEquals(
                    List.of(500, 1000),
                    b.references().stream()
                            .map(SixMaxSuitDecisionStability.Reference::iterations)
                            .toList());
            var transition =
                    new SixMaxPolicyFlopTransition(
                            game.sourceGame(),
                            SixMaxPreflopContinuationFeedback.preflopPolicy(solution),
                            b.history());
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(game, transition, b.observation());
            var questions = new HashMap<String, SixMaxSuitDecisionStability.Question>();
            b.questions().forEach(q -> questions.put(q.primary().informationSet(), q));
            for (var decision :
                    SixMaxOneBetDecisionValues.assess(game, posterior.roots(), solution)) {
                assertEquals(
                        decision.row(), questions.get(decision.row().informationSet()).primary());
                if (decision.roots().isEmpty()) continue;
                SixMaxSuitConditionalRefinementArtifactTest.analyticValues(game, decision, solution)
                        .forEach(
                                (action, ev) ->
                                        assertEquals(
                                                ev,
                                                decision.row().values().actionEvBb().get(action),
                                                1e-10));
            }
            if (b.retained()) {
                retained += b.historyProbability() * b.observationProbabilityGivenHistory();
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
                                .allMatch(SixMaxSuitDecisionStability.Question::stable));
            }
            for (var q : b.questions())
                if (q.stable()) {
                    assertTrue(q.material());
                    assertEquals(2, q.references().size());
                    for (var reference : q.references()) {
                        assertTrue(reference.maximumActionEvDriftBb() <= .01);
                        assertNotNull(reference.posteriorTotalVariation());
                        assertTrue(reference.posteriorTotalVariation() <= .01);
                        assertTrue(reference.primaryMixRegretUnderReferenceBb() <= .01);
                        assertTrue(reference.referenceMixRegretUnderPrimaryBb() <= .01);
                    }
                }
        }
        assertEquals(retained, screen.retainedPhysicalReach(), 1e-14);
    }

    @Test
    void forgedParentAndDerivedLineageCannotReuseOpaqueAcceptedResults(@TempDir Path dir)
            throws Exception {
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(accepted.report());
        tree.put("predecessorCheckpointHash", "0".repeat(64));
        var bad = dir.resolve("bad.json");
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalConditionalRefinement.replay(
                                Path.of(PREFIX + "-accurate-64-policy.json.gz"), bad, predecessor));
        tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(screen);
        tree.put("derivedReportHash", "0".repeat(64));
        Files.writeString(bad, tree.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalDecisionStability.replay(bad, accepted));
    }
}
