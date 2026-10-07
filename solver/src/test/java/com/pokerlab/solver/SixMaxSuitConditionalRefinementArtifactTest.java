package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Complete derived evidence replay, including fresh training and independent local pure plans. */
class SixMaxSuitConditionalRefinementArtifactTest {
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact parent;
    private static SixMaxSuitRefinementPayoffTable.Artifact table;
    private static SixMaxSuitRefinementStudy.Checkpoint predecessor;
    private static SixMaxSuitRefinementFlopGame game;

    @BeforeAll
    static void load() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        parent =
                SixMaxRankTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-rank-texture-payoffs.json.gz"), source);
        table =
                SixMaxSuitRefinementPayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-suit-refinement-payoffs.json.gz"),
                        source,
                        parent);
        predecessor =
                SixMaxSuitRefinementStudy.read(
                        Path.of("../docs/data/sixmax-staged-suit-refinement-policy-500.json.gz"),
                        source,
                        parent,
                        table);
        game = SixMaxSuitRefinementStudy.rebuild(source, parent, table, predecessor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"largest", "weighted", "balanced"})
    void freshConditionalTrialsAndCompleteParentDiagnosticsReplay(String mode) throws Exception {
        String prefix = "../docs/data/sixmax-staged-suit-conditional-" + mode + "-500";
        var result =
                SixMaxSuitConditionalRefinement.replay(
                        Path.of(prefix + "-policy.json.gz"),
                        Path.of(prefix + "-report.json.gz"),
                        source,
                        parent,
                        table,
                        predecessor);
        var report = result.report();
        var artifact = result.artifact().orElseThrow();
        assertTrue(report.accepted());
        assertEquals("VALIDATION_ONLY", artifact.publicationStatus());
        assertEquals(
                "9387b3eca90b0d1d35b806a6c5d893a0957c6ff03535fc9f5df313eefcf47e9b",
                artifact.predecessorCheckpointHash());
        assertEquals(
                "b6584162768c9196a064c246a63e292e9a06756a7872f66d230a82ab459a1c82",
                artifact.frozenPreflopHash());
        assertEquals(predecessor.gameHash(), artifact.gameHash());
        assertEquals(predecessor.solutionHash(), artifact.predecessorSolutionHash());
        assertEquals(500, artifact.solution().iterations());
        assertEquals(70703, artifact.solution().strategy().size());
        assertEquals(
                predecessor.solution().strategy().keySet(),
                artifact.solution().strategy().keySet());
        assertEquals(
                SixMaxPreflopContinuationFeedback.preflopPolicy(predecessor.solution()),
                SixMaxPreflopContinuationFeedback.preflopPolicy(artifact.solution()));
        assertEquals(mode.equals("balanced") ? 64 : 32, report.branches().size());
        assertEquals(List.of(10, 100, 500, 1000), report.settings().iterationBudgets());
        assertEquals(.001, report.settings().targetGapBb());
        assertEquals(7806, report.before().summary().auditedSignals());
        assertEquals(7806, report.after().summary().auditedSignals());
        assertEquals(0, report.after().summary().zeroReachHistories());
        assertEquals(0, report.after().summary().signalsWithoutReachedPrivateSupport());
        assertEquals(
                .0013595867879100778,
                report.before().parentWitness().parentQuality().nashConvBb(),
                1e-15);
        assertTrue(
                report.after().parentWitness().parentQuality().nashConvBb()
                        < report.before().parentWitness().parentQuality().nashConvBb());
        assertTrue(
                report.after().parentWitness().reachWeightedLocalNashConvBb()
                        < report.before().parentWitness().reachWeightedLocalNashConvBb());
        assertEquals(
                report.replacedInformationSets(),
                report.branches().stream()
                        .mapToInt(SixMaxSuitConditionalRefinement.Branch::replacedInformationSets)
                        .sum());
        for (var branch : report.branches()) {
            assertEquals("TARGET_MET", branch.status());
            assertTrue(branch.after().nashConvBb() <= .001);
            assertTrue(branch.after().nashConvBb() < branch.before().nashConvBb());
            assertTrue(
                    branch.trials().stream()
                            .allMatch(t -> t.traversal().sampledChanceNodes() == 0));
        }
        // Separate pure-plan enumeration validates the five largest selected gaps before AND after.
        for (var branch :
                report.branches().stream()
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
                    SixMaxSuitRefinementPayoffTable.view(table),
                    predecessor.solution(),
                    branch.history(),
                    branch.observation(),
                    branch.observationProbabilityGivenHistory(),
                    branch.before());
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    SixMaxSuitRefinementPayoffTable.view(table),
                    artifact.solution(),
                    branch.history(),
                    branch.observation(),
                    branch.observationProbabilityGivenHistory(),
                    branch.after());
        }
        for (int player = 0; player < 6; player++) {
            assertEquals(0, report.after().parentWitness().embeddingErrorsBb().get(player), 1e-9);
            assertTrue(
                    report.after().parentWitness().reachWeightedLocalGainsBb().get(player)
                            <= report.after()
                                            .parentWitness()
                                            .parentQuality()
                                            .deviationGainsBb()
                                            .get(player)
                                    + 1e-9);
        }
        if (!mode.equals("weighted"))
            assertTrue(report.after().summary().largestConditionalGapBb() < 1.3);
        else
            assertEquals(
                    report.before().summary().largestConditionalGapBb(),
                    report.after().summary().largestConditionalGapBb());
    }
}
