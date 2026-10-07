package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuitDecisionStabilityTest {
    @Test
    void freshReferencesReplayEveryDecisionAndRejectTamperedEvidence(@TempDir Path temp)
            throws Exception {
        var f = SixMaxSuitConditionalRefinementTest.fixture();
        var derived =
                SixMaxSuitConditionalRefinement.refine(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        SixMaxSuitConditionalRefinementTest.settings(64, List.of(1000)),
                        b -> {});
        assertTrue(derived.report().accepted());
        var settings = new SixMaxSuitDecisionStability.Settings(3, List.of(500, 1000), 1);
        var callbacks = new ArrayList<SixMaxSuitDecisionStability.Branch>();
        var report =
                SixMaxSuitDecisionStability.screen(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        derived,
                        settings,
                        callbacks::add);
        assertEquals(3, report.branches().size());
        assertEquals(report.branches(), callbacks);
        assertFalse(report.trainerAdmission());
        assertTrue(
                report.branches().stream().allMatch(SixMaxSuitDecisionStability.Branch::retained));
        for (var branch : report.branches()) {
            assertTrue(branch.observationKey().startsWith("board:"));
            assertEquals(
                    List.of(500, 1000),
                    branch.references().stream()
                            .map(SixMaxSuitDecisionStability.Reference::iterations)
                            .toList());
            assertTrue(
                    branch.references().stream()
                            .allMatch(
                                    r ->
                                            r.traversal().sampledChanceNodes() == 0
                                                    && r.traversal().baselineCorrections() == 0));
            assertTrue(
                    branch.questions().stream()
                            .filter(SixMaxSuitDecisionStability.Question::material)
                            .allMatch(q -> q.stable() && q.references().size() == 2));
        }
        var path = temp.resolve("report.json.gz");
        SixMaxSuitDecisionStability.write(path, report);
        assertEquals(
                report,
                SixMaxSuitDecisionStability.replay(
                        path, f.source(), f.parent(), f.table(), f.checkpoint(), derived));
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().readTree(SixMaxTextureStudy.json(report));
        tree.put("retainedPhysicalReach", 0);
        var raw = temp.resolve("tampered.json");
        Files.writeString(raw, tree.toString());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSuitDecisionStability.replay(
                                raw, f.source(), f.parent(), f.table(), f.checkpoint(), derived));
        tree.put("derivedArtifactHash", "0".repeat(64));
        Files.writeString(raw, tree.toString());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSuitDecisionStability.replay(
                                raw, f.source(), f.parent(), f.table(), f.checkpoint(), derived));
        tree.put("trainerAdmission", true);
        Files.writeString(raw, tree.toString());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxSuitDecisionStability.replay(
                                raw, f.source(), f.parent(), f.table(), f.checkpoint(), derived));
        var noCandidates =
                SixMaxSuitDecisionStability.screen(
                        f.source(),
                        f.parent(),
                        f.table(),
                        f.checkpoint(),
                        derived,
                        new SixMaxSuitDecisionStability.Settings(3, List.of(500, 1000), 2),
                        b -> {});
        assertTrue(noCandidates.branches().isEmpty());
        assertEquals(0, noCandidates.eligiblePhysicalReach());
    }

    @Test
    void equalEvMixedStrategiesAreNotRejectedForFrequencyChangesButEvChangesFail()
            throws Exception {
        var f = SixMaxSuitConditionalRefinementTest.fixture();
        var roots = SixMaxOneBetDecisionValuesTest.roots(f);
        var baseline =
                f.game()
                        .checkdownBaseline(
                                SixMaxPreflopContinuationFeedback.preflopPolicy(
                                        f.checkpoint().solution()));
        var primary =
                SixMaxOneBetDecisionValues.assess(f.game().core(), roots, baseline).getFirst();
        var original = primary.row().values();
        assertEquals(original.actionEvBb().get("b"), original.actionEvBb().get("k"), 1e-12);
        var changedMix =
                new SixMaxOneBetDecisionValues.Values(
                        Map.of("k", 0.0, "b", 1.0),
                        original.actionEvBb(),
                        original.profileEvBb(),
                        0,
                        0,
                        0);
        String localSuffix =
                ":postflop:suit-refinement:"
                        + roots.getFirst().state().preflop().publicHistory()
                        + ":"
                        + f.table().observations().get(roots.getFirst().state().signal()).key()
                        + ":";
        var localRows = new LinkedHashMap<String, Map<String, Double>>();
        baseline.strategy()
                .forEach(
                        (key, row) -> {
                            if (key.contains(localSuffix)) localRows.put(key, row);
                        });
        var quality =
                SixMaxConnectedPreflopAudit.Quality.of(
                        MultiPlayerInformationSetBestResponse.assess(
                                new SixMaxRankTextureConditionalAudit.ConditionalGame(
                                        f.game(), roots),
                                new CfrSolution(1, localRows)));
        var reference =
                new SixMaxSuitDecisionStability.Reference(
                        1000,
                        "0".repeat(64),
                        quality,
                        new MultiPlayerCfrSolver<>(
                                        new SixMaxRankTextureConditionalAudit.ConditionalGame(
                                                f.game(), roots),
                                        CfrSolver.Variant.CFR_PLUS)
                                .statistics(),
                        0);
        var comparison =
                SixMaxSuitDecisionStability.compare(reference, primary, primary, changedMix);
        assertEquals(1, comparison.maximumFrequencyDrift());
        assertTrue(comparison.failures().isEmpty());
        var changedEv =
                new SixMaxOneBetDecisionValues.Values(
                        changedMix.frequencies(), Map.of("k", 0.0, "b", 1.0), 0, 1, 1, 0);
        assertTrue(
                SixMaxSuitDecisionStability.compare(reference, primary, primary, changedEv)
                        .failures()
                        .contains("ACTION_EV_DRIFT"));
        assertTrue(
                SixMaxSuitDecisionStability.compare(reference, primary, primary, changedEv)
                        .failures()
                        .contains("PRIMARY_MIX_UNDER_REFERENCE"));
        assertTrue(
                SixMaxSuitDecisionStability.compare(reference, primary, null, changedMix)
                        .failures()
                        .contains("ZERO_REFERENCE_REACH"));
        var oneWorld =
                new SixMaxOneBetDecisionValues.Decision(
                        primary.row(),
                        List.of(new ChanceOutcome<>(primary.roots().getFirst().state(), 1)));
        assertTrue(
                SixMaxSuitDecisionStability.compare(reference, primary, oneWorld, changedMix)
                        .failures()
                        .contains("PRIVATE_POSTERIOR_DRIFT"));
    }

    @Test
    void settingsAndCliAliasesFailBeforeReadingInputs(@TempDir Path temp) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuitDecisionStability.Settings(65, List.of(500, 1000), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuitDecisionStability.Settings(1, List.of(1000), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuitDecisionStability.Settings(1, List.of(1000, 500), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuitDecisionStability.Settings(1, List.of(500, 1001), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSuitDecisionStability.Settings(1, List.of(500, 1000), 0));
        String same = temp.resolve("absent.json").toString();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuitDecisionStabilityMain.main(
                                new String[] {"screen", same, same, same, same, same, same, same}));
    }
}
