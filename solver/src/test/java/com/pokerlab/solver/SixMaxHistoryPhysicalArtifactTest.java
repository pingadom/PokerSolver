package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exact saved payoff/report replay plus independent public-board and evaluator controls. */
class SixMaxHistoryPhysicalArtifactTest {
    private static final String PREFIX = "../docs/data/sixmax-staged-history-physical";
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact parent;
    private static SixMaxHistoryPhysicalPayoffTable.Verified table;
    private static SixMaxSuitRefinementStudy.Checkpoint reference;

    @BeforeAll
    static void load() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        parent =
                SixMaxRankTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-rank-texture-payoffs.json.gz"), source);
        var prior =
                SixMaxSuitRefinementPayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-suit-refinement-payoffs.json.gz"),
                        source,
                        parent);
        reference =
                SixMaxSuitRefinementStudy.read(
                        Path.of("../docs/data/sixmax-staged-suit-refinement-policy-500.json.gz"),
                        source,
                        parent,
                        prior);
        table =
                SixMaxHistoryPhysicalPayoffTable.replay(
                        Path.of(PREFIX + "-payoffs.json.gz"), source, parent);
    }

    @Test
    void deterministicSelectionReplaysAndRejectsTamperingOrUnsupportedBudgets(@TempDir Path temp)
            throws Exception {
        var evidence =
                SixMaxHistoryPhysicalMenuSelection.replay(
                        Path.of(PREFIX + "-menu-selection.json"),
                        source,
                        parent,
                        reference.solution());
        assertEquals(table.artifact().menu(), evidence.menu());
        assertEquals(
                evidence.menu(),
                SixMaxHistoryPhysicalPayoffTable.readMenu(Path.of(PREFIX + "-menu.json")));
        assertEquals(.025, evidence.requestedAllHeadsUpFraction());
        assertEquals(.025037902261829007, evidence.selectedMaterialAllHeadsUpFraction(), 1e-15);
        assertEquals(530, evidence.menu().revelations().size());
        // The density heuristic concentrates this bounded demonstration in one history.
        assertEquals(
                Set.of(5),
                new HashSet<>(
                        evidence.menu().revelations().stream()
                                .map(SixMaxHistoryPhysicalPayoffTable.Reveal::history)
                                .toList()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMenuSelection.select(
                                source, parent, reference.solution(), reference.selections(), .05));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMenuSelection.select(
                                source,
                                parent,
                                reference.solution(),
                                reference.selections(),
                                Double.NaN));
        var tampered = temp.resolve("evidence.json");
        Files.writeString(
                tampered,
                Files.readString(Path.of(PREFIX + "-menu-selection.json"))
                        .replace(evidence.frozenPreflopPolicyHash(), "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMenuSelection.replay(
                                tampered, source, parent, reference.solution()));
        Files.write(tampered, new byte[SixMaxHistoryPhysicalMenuSelection.MAX_EVIDENCE_BYTES + 1]);
        assertThrows(
                Exception.class,
                () ->
                        SixMaxHistoryPhysicalMenuSelection.replay(
                                tampered, source, parent, reference.solution()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHistoryPhysicalMenuSelectionMain.main(
                                new String[] {
                                    "select",
                                    tampered.toString(),
                                    tampered.toString(),
                                    tampered.toString(),
                                    tampered.toString(),
                                    tampered.toString(),
                                    tampered.toString(),
                                    ".025"
                                }));
    }

    @Test
    void impossibleCoverageRequestsFailBeforePolicyOrBoardEnumeration(@TempDir Path temp)
            throws Exception {
        // Deliberately incomplete policy: the coverage impossibility must be diagnosed first.
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxHistoryPhysicalMenuSelection.select(
                                        source,
                                        parent,
                                        new CfrSolution(1, Map.of()),
                                        reference.selections(),
                                        .25));
        assertTrue(failure.getMessage().contains("coverage exceeds revelation cap"));
        assertTrue(failure.getMessage().contains("0.06072874493927125"));
        var cli =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxHistoryPhysicalMenuSelectionMain.main(
                                        new String[] {
                                            "select",
                                            temp.resolve("source.json").toString(),
                                            temp.resolve("rank.json").toString(),
                                            temp.resolve("suit.json").toString(),
                                            temp.resolve("cp.json").toString(),
                                            temp.resolve("menu.json").toString(),
                                            temp.resolve("evidence.json").toString(),
                                            ".25"
                                        }));
        assertTrue(cli.getMessage().contains("coverage exceeds revelation cap"));
        assertFalse(Files.exists(temp.resolve("menu.json")));
        assertFalse(Files.exists(temp.resolve("evidence.json")));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMenuSelection.maximumPhysicalHeadsUpFraction(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalMenuSelection.maximumPhysicalHeadsUpFraction(601));
        // A forged evidence claim above the menu's own optimistic ceiling is rejected on read.
        var evidence =
                SixMaxHistoryPhysicalMenuSelection.replay(
                        Path.of(PREFIX + "-menu-selection.json"),
                        source,
                        parent,
                        reference.solution());
        var tree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        SixMaxTexturePayoffTable.mapper().valueToTree(evidence);
        tree.put("selectedMaterialAllHeadsUpFraction", .25);
        tree.put("selectedMaterialWholeGameReach", .25 * evidence.allHeadsUpProbability());
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(
                                        tree, SixMaxHistoryPhysicalMenuSelection.Evidence.class));
    }

    @Test
    void independentFullDeckClassificationControlsAllHistoryWorldCountsAndExhaustedFallbacks()
            throws Exception {
        var t = table.artifact();
        assertEquals(
                "8a32a8159abbe92f8df53d49f702fe22caf0c6d058105a0c636dd02e197e9e13",
                SixMaxHistoryPhysicalPayoffTable.hash(t));
        assertEquals(1712, t.observations().size());
        assertEquals(915457, t.completeTreeStates());
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        var indices = new HashMap<String, Integer>();
        for (int o = 0; o < t.observations().size(); o++)
            indices.put(t.observations().get(o).key(), o);
        long states = 1L + (long) game.treeSummary().totalStates() * roots.size();
        int removed = 0;
        for (int h = 0; h < t.histories().size(); h++) {
            var exposed = new HashSet<List<String>>();
            for (var r : t.menu().revelations()) if (r.history() == h) exposed.add(r.board());
            for (int d = 0; d < roots.size(); d++) {
                var deck =
                        SixMaxConditionalPayoffEnumeration.undealt(
                                game.dealtHands(roots.get(d).state()));
                long[] counts = new long[t.observations().size()];
                for (int a = 0; a < deck.size() - 2; a++)
                    for (int b = a + 1; b < deck.size() - 1; b++)
                        for (int c = b + 1; c < deck.size(); c++) {
                            var cards = List.of(deck.get(a), deck.get(b), deck.get(c));
                            var text = cards.stream().map(Card::compact).sorted().toList();
                            var signal =
                                    SixMaxRankTexturePayoffTable.Signal.from(
                                            cards.get(0), cards.get(1), cards.get(2));
                            String key =
                                    new SixMaxSuitRefinementPayoffTable.Observation(
                                                    signal,
                                                    exposed.contains(text) ? text : List.of())
                                            .key();
                            counts[indices.get(key)]++;
                        }
                assertEquals(
                        t.histories().get(h).deals().get(d).flopCounts(),
                        Arrays.stream(counts).boxed().toList());
                states += 9L * Arrays.stream(counts).filter(n -> n > 0).count();
                for (int o = 0; o < counts.length; o++)
                    if (!t.observations().get(o).physical()
                            && counts[o] == 0
                            && parent.deals()
                                            .get(d)
                                            .flopCounts()
                                            .get(
                                                    parent.signals()
                                                            .indexOf(
                                                                    t.observations()
                                                                            .get(o)
                                                                            .signal()))
                                    > 0) removed++;
            }
        }
        assertEquals(t.completeTreeStates(), states);
        assertEquals(18, removed);
    }

    @Test
    void physicalPayoffSamplesMatchIndependentObjectEvaluator() {
        var t = table.artifact();
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        int checked = 0;
        for (int h = 0; h < t.histories().size(); h++) {
            var history = t.histories().get(h);
            int first = Integer.numberOfTrailingZeros(history.activeMask());
            int second = Integer.numberOfTrailingZeros(history.activeMask() & ~(1 << first));
            int sampledWorlds = 0;
            for (int d = 0; d < roots.size() && sampledWorlds < 3; d++) {
                var legal = new ArrayList<Integer>();
                for (int o = 0; o < t.observations().size(); o++)
                    if (t.observations().get(o).physical()
                            && history.deals().get(d).flopCounts().get(o) > 0) legal.add(o);
                if (legal.isEmpty()) continue;
                sampledWorlds++;
                var hands = game.dealtHands(roots.get(d).state());
                var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
                for (int index : List.of(0, legal.size() / 2, legal.size() - 1)) {
                    int o = legal.get(index);
                    var board = t.observations().get(o).board().stream().map(Card::parse).toList();
                    var remaining = deck.stream().filter(c -> !board.contains(c)).toList();
                    long wins = 0, ties = 0;
                    for (int a = 0; a < remaining.size() - 1; a++)
                        for (int b = a + 1; b < remaining.size(); b++) {
                            var one = new ArrayList<>(board);
                            one.add(remaining.get(a));
                            one.add(remaining.get(b));
                            var two = new ArrayList<>(one);
                            one.add(hands.get(first).first());
                            one.add(hands.get(first).second());
                            two.add(hands.get(second).first());
                            two.add(hands.get(second).second());
                            int comparison =
                                    HandEvaluator.evaluateBest(one)
                                            .compareTo(HandEvaluator.evaluateBest(two));
                            if (comparison > 0) wins++;
                            if (comparison == 0) ties++;
                        }
                    assertEquals(wins, history.deals().get(d).activePair().firstWins().get(o));
                    assertEquals(ties, history.deals().get(d).activePair().ties().get(o));
                    checked++;
                }
            }
        }
        assertEquals(9, checked);
    }

    @Test
    void exactReplayRejectsACompensatedPhysicalPayoffChange(@TempDir Path temp) throws Exception {
        var menu =
                new SixMaxHistoryPhysicalPayoffTable.Menu(
                        table.artifact().menu().selections(),
                        List.of(table.artifact().menu().revelations().getFirst()));
        var small = SixMaxHistoryPhysicalPayoffTable.generate(source, parent, menu, n -> {});
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(small.artifact());
        int h = menu.revelations().getFirst().history();
        int o =
                small.artifact()
                        .observations()
                        .indexOf(menu.revelations().getFirst().observation());
        int r =
                small.artifact()
                        .observations()
                        .indexOf(
                                new SixMaxSuitRefinementPayoffTable.Observation(
                                        menu.revelations().getFirst().observation().signal(),
                                        List.of()));
        boolean changed = false;
        for (int d = 0; d < parent.deals().size(); d++) {
            var row = small.artifact().histories().get(h).deals().get(d);
            if (row.flopCounts().get(o) == 0 || row.activePair().firstWins().get(o) == 0) continue;
            var wins =
                    (com.fasterxml.jackson.databind.node.ArrayNode)
                            tree.get("histories")
                                    .get(h)
                                    .get("deals")
                                    .get(d)
                                    .get("activePair")
                                    .get("firstWins");
            wins.set(
                    o,
                    com.fasterxml.jackson.databind.node.LongNode.valueOf(
                            row.activePair().firstWins().get(o) - 1));
            wins.set(
                    r,
                    com.fasterxml.jackson.databind.node.LongNode.valueOf(
                            row.activePair().firstWins().get(r) + 1));
            changed = true;
            break;
        }
        assertTrue(changed);
        var artifact =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(tree, SixMaxHistoryPhysicalPayoffTable.Artifact.class);
        // Regrouping still matches; only exact card replay can detect this change.
        SixMaxHistoryPhysicalPayoffTable.validate(artifact, source, parent);
        var path = temp.resolve("tampered.json");
        Files.writeString(path, SixMaxTextureStudy.json(artifact));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHistoryPhysicalPayoffTable.replay(path, source, parent));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 500})
    void freshPoliciesReplayFullParentAndEveryConditionalCaseAndTrainingCounters(int iterations)
            throws Exception {
        var cp =
                SixMaxHistoryPhysicalStudy.read(
                        Path.of(PREFIX + "-policy-" + iterations + ".json.gz"),
                        source,
                        parent,
                        table);
        var report =
                SixMaxHistoryPhysicalStudy.replay(
                        Path.of(PREFIX + "-study-" + iterations + ".json.gz"),
                        source,
                        parent,
                        table,
                        cp);
        assertFalse(report.trainerAdmission());
        assertEquals(iterations, cp.solution().iterations());
        assertEquals(915457, cp.binding().completeTreeStates());
        assertEquals(9161, report.preflopInformationSets());
        assertEquals(530, report.revelations());
        assertEquals(6, report.jointlySolvedDiagnostics().histories().size());
        for (var h : report.jointlySolvedDiagnostics().histories()) {
            assertEquals(1, h.signalProbabilitiesSum(), 1e-12);
            assertEquals(1712, h.signals().size());
        }
        assertTrue(report.physicalCoverage().materialAllHeadsUpFraction() > .02);
        report.checkdownRecoveryErrorBb().forEach(error -> assertEquals(0, error, 1e-9));
        report.jointlySolvedDiagnostics()
                .parentWitness()
                .embeddingErrorsBb()
                .forEach(error -> assertEquals(0, error, 1e-9));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Path.of(PREFIX + "-traversal-" + iterations + ".json").toFile(),
                                SixMaxHistoryPhysicalStudy.TrainingEvidence.class);
        assertEquals(cp.binding(), saved.binding());
        assertEquals(cp.solutionHash(), saved.solutionHash());
        assertEquals(iterations, saved.freshIterations());
        assertEquals(0, saved.initialRegretRows());
        var game = SixMaxHistoryPhysicalStudy.rebuild(source, parent, table, cp);
        int ownQuestions = 0;
        for (var h : report.jointlySolvedDiagnostics().histories()) {
            var transition =
                    new SixMaxPolicyFlopTransition(
                            game.sourceGame(),
                            SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution()),
                            h.history());
            for (var signal : h.signals()) {
                if (!table.artifact().observations().get(signal.observation()).physical()
                        || signal.signalProbabilityGivenHistory() == 0) continue;
                var posterior =
                        SixMaxFlopConditionalDiagnostics.posterior(
                                game.core(), transition, signal.observation());
                for (var decision :
                        SixMaxOneBetDecisionValues.assess(
                                game.core(), posterior.roots(), cp.solution())) {
                    if (decision.roots().isEmpty()) continue;
                    var analytic =
                            SixMaxSuitConditionalRefinementArtifactTest.analyticValues(
                                    game.core(), decision, cp.solution());
                    analytic.forEach(
                            (action, ev) ->
                                    assertEquals(
                                            ev,
                                            decision.row().values().actionEvBb().get(action),
                                            1e-10));
                    ownQuestions++;
                }
            }
        }
        assertTrue(ownQuestions >= 4 * report.physicalCoverage().materialCases());
        var fresh =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.CFR_PLUS,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        fresh.solve(1);
        assertEquals(
                fresh.statistics().visitedNodes() * iterations, saved.traversal().visitedNodes());
        assertEquals(
                fresh.statistics().terminalNodes() * iterations, saved.traversal().terminalNodes());
        assertEquals(
                fresh.inactiveUtilityPrunedNodes() * iterations,
                saved.inactiveUtilityPrunedNodes());
    }
}
