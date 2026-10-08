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
        if (mode.equals("balanced")) {
            // Reuse the opaque, fully replayed derivative: no second expensive parent re-audit.
            var screen =
                    SixMaxSuitDecisionStability.replay(
                            Path.of(
                                    "../docs/data/sixmax-staged-suit-decision-stability-500.json.gz"),
                            source,
                            parent,
                            table,
                            predecessor,
                            result);
            assertFalse(screen.trainerAdmission());
            assertEquals(32, screen.branches().size());
            var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(artifact.solution());
            for (var branch : screen.branches()) {
                assertTrue(table.observations().get(branch.observation()).physical());
                assertEquals(
                        List.of(500, 1000),
                        branch.references().stream()
                                .map(SixMaxSuitDecisionStability.Reference::iterations)
                                .toList());
                var transition =
                        new SixMaxPolicyFlopTransition(game.sourceGame(), pre, branch.history());
                var posterior =
                        SixMaxFlopConditionalDiagnostics.posterior(
                                game.core(), transition, branch.observation());
                for (var decision :
                        SixMaxOneBetDecisionValues.assess(
                                game.core(), posterior.roots(), artifact.solution())) {
                    if (decision.roots().isEmpty()) continue;
                    var independentlyCalculated =
                            analyticValues(game.core(), decision, artifact.solution());
                    independentlyCalculated.forEach(
                            (action, ev) ->
                                    assertEquals(
                                            ev,
                                            decision.row().values().actionEvBb().get(action),
                                            1e-10));
                }
                for (var question : branch.questions()) {
                    if (question.material()) assertEquals(2, question.references().size());
                    if (question.stable()) {
                        assertTrue(question.material());
                        assertTrue(question.failures().isEmpty());
                        assertTrue(question.primary().values().decisionRegretBb() <= .01);
                        assertTrue(
                                question.references().stream()
                                        .allMatch(
                                                r ->
                                                        r.posteriorTotalVariation() != null
                                                                && r.posteriorTotalVariation()
                                                                        <= .01
                                                                && r.maximumActionEvDriftBb() <= .01
                                                                && r
                                                                                .primaryMixRegretUnderReferenceBb()
                                                                        <= .01
                                                                && r
                                                                                .referenceMixRegretUnderPrimaryBb()
                                                                        <= .01));
                    }
                }
            }
        }
    }

    /** Closed-form pot/share accounting, independent of terminal traversal and pure-plan search. */
    static java.util.Map<String, Double> analyticValues(
            SixMaxOneBetFlopGame game,
            SixMaxOneBetDecisionValues.Decision decision,
            CfrSolution policy) {
        var first = decision.roots().getFirst().state();
        var coverage =
                game.coverage().stream()
                        .filter(c -> c.history().equals(first.preflop().publicHistory()))
                        .findFirst()
                        .orElseThrow();
        double pot = coverage.potBb(), bet = coverage.betBb();
        int actor = decision.row().actor().ordinal();
        int mask = (1 << coverage.firstToAct().ordinal()) | (1 << coverage.secondToAct().ordinal());
        String prefix = decision.row().actions();
        double check = 0, wager = 0, possibleFutureCall = 0;
        for (var root : decision.roots()) {
            var state = root.state();
            double share =
                    game.payoffView()
                            .share(
                                    state.preflop().publicHistory(),
                                    state.preflop().dealIndex(),
                                    mask,
                                    actor,
                                    state.signal());
            double call = (pot + 2 * bet) * share - bet;
            if (prefix.equals("b") || prefix.equals("kb")) {
                wager += root.probability() * call;
                continue;
            }
            var afterBet = game.afterAction(state, "b");
            double fold = MultiPlayerStrategyEvaluator.probability(game, policy, afterBet, "f");
            wager += root.probability() * (fold * pot + (1 - fold) * call);
            if (prefix.equals("k")) check += root.probability() * pot * share;
            else {
                var afterCheck = game.afterAction(state, "k");
                double responseCheck =
                        MultiPlayerStrategyEvaluator.probability(game, policy, afterCheck, "k");
                check += root.probability() * responseCheck * pot * share;
                possibleFutureCall += root.probability() * (1 - responseCheck) * call;
            }
        }
        return prefix.equals("b") || prefix.equals("kb")
                ? java.util.Map.of("f", 0.0, "c", wager)
                : java.util.Map.of("b", wager, "k", check + Math.max(0, possibleFutureCall));
    }
}
