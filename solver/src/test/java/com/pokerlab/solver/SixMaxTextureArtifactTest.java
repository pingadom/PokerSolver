package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Replays exact-table checks and complete saved policies; never reruns long CFR studies. */
class SixMaxTextureArtifactTest {
    @ParameterizedTest
    @CsvSource({
        "'',500,3,0.0010227107950958438",
        "'',1000,3,0.0002581481955057924",
        "-broad,500,6,0.001148867484634203",
        "-broad,1000,6,0.0002897451039000082",
        "-pruned,1000,6,0.0002897451038998972"
    })
    void savedEvidenceBindsExactSourceAndReplaysCompleteParentConditionalAndContentAudits(
            String suffix, int iterations, int histories, double expectedGap) throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var table =
                SixMaxTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-texture-payoffs.json"), source);
        assertEquals(
                "b30b33fef6af4d3ff44be379c46be5ebca41b6c5e2ac5129a469f2b8024ff9a7",
                SixMaxTexturePayoffTable.hash(table));
        assertEquals(12, table.deals().size());
        for (var deal : table.deals()) {
            assertEquals(9880, deal.flopCounts().stream().mapToLong(Long::longValue).sum());
            assertEquals(15, deal.pairs().size());
            assertTrue(deal.flopCounts().stream().allMatch(n -> n > 0));
        }
        var prefix = "../docs/data/sixmax-staged-texture" + suffix;
        var checkpoint =
                SixMaxTextureStudy.read(
                        Path.of(prefix + "-policy-" + iterations + ".json.gz"), source, table);
        assertEquals(iterations, checkpoint.solution().iterations());
        assertEquals(histories, checkpoint.selections().size());
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Path.of(prefix + "-study-" + iterations + ".json").toFile(),
                                SixMaxTextureStudy.Report.class);
        var replayed = SixMaxTextureStudy.assess(source, table, checkpoint);
        assertEquals(saved, replayed);
        assertEquals(
                suffix.equals("-pruned") ? SixMaxTextureStudy.PRUNED_ALGORITHM : "CFR_PLUS",
                checkpoint.algorithm());
        assertEquals(expectedGap, replayed.jointlySolvedInTextureGame().nashConvBb(), 1e-12);
        assertTrue(
                replayed.checkdownBaselineInTextureGame().nashConvBb()
                        > replayed.jointlySolvedInTextureGame().nashConvBb());
        assertEquals(9161, replayed.preflopInformationSets());
        assertEquals(48 * histories, replayed.postflopInformationSets());
        assertEquals(138793 + 648L * histories, replayed.completeTreeStates());
        assertEquals(6 * histories, replayed.jointlySolvedConditionalTextures().size());
        assertEquals(checkpoint.gameHash(), replayed.gameHash());
        assertEquals(checkpoint.solutionHash(), replayed.solutionHash());
        assertEquals(
                0,
                replayed.jointlySolvedInTextureGame().profileUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-9);
        assertTrue(
                replayed.checkdownRecoveryErrorBb().stream()
                        .allMatch(error -> Math.abs(error) < 1e-10));
        for (var conditional : replayed.jointlySolvedConditionalTextures()) {
            assertEquals(12, conditional.posteriorPrivateDeals());
            assertEquals(
                    1,
                    conditional.firstMarginal().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-12);
            assertEquals(
                    1,
                    conditional.secondMarginal().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-12);
            assertTrue(conditional.quality().nashConvBb() >= 0);
        }
        assertEquals(
                "INFEASIBLE_UNDER_FIXED_POLICY",
                replayed.jointlySolvedPhysicalFlopFeasibility().status());
        // Successful aggregate texture coverage cannot silently bypass the exact-board content
        // gate.
        assertTrue(
                replayed.jointlySolvedPhysicalFlopFeasibility().optimisticHeadsUpFraction() < .25);
        assertFalse(replayed.jointlySolvedPhysicalFlopFeasibility().numericReachUnresolved());
        if (histories == 3)
            assertTrue(replayed.jointlySolvedReach().selectedHeadsUpFraction() < .0001);
        else assertTrue(replayed.jointlySolvedReach().selectedHeadsUpFraction() > .99);
    }
}
