package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Saved complete evidence replay, with independent physical payoffs and pure-plan responses. */
class SixMaxSuitRefinementArtifactTest {
    private static final String PREFIX = "../docs/data/sixmax-staged-suit-refinement";
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact parent;
    private static SixMaxSuitRefinementPayoffTable.Artifact table;

    @BeforeAll
    static void load() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        parent =
                SixMaxRankTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-rank-texture-payoffs.json.gz"), source);
        table =
                SixMaxSuitRefinementPayoffTable.read(
                        Path.of(PREFIX + "-payoffs.json.gz"), source, parent);
    }

    @Test
    void everyRefinedPhysicalFlopAndFifteenPairCountsMatchIndependentObjectEnumeration()
            throws Exception {
        assertEquals(
                "152c0c523036238d2b783b473b353a17c4219c2d0c04bd8f555df30cd675c390",
                SixMaxSuitRefinementPayoffTable.hash(table));
        assertEquals(15, table.refinedSignals().size());
        assertEquals(1301, table.observations().size());
        assertEquals(
                134,
                table.observations().stream()
                        .filter(SixMaxSuitRefinementPayoffTable.Observation::physical)
                        .count());
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        long examined = 0;
        for (int world = 0; world < table.deals().size(); world++) {
            var hands = game.dealtHands(roots.get(world).state());
            var row = table.deals().get(world);
            var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
            for (int o = 0; o < table.observations().size(); o++) {
                var observation = table.observations().get(o);
                if (!observation.physical() || row.flopCounts().get(o) == 0) continue;
                assertEquals(1L, row.flopCounts().get(o));
                var board = observation.board().stream().map(Card::parse).toList();
                var remaining = deck.stream().filter(c -> !board.contains(c)).toList();
                assertEquals(37, remaining.size());
                long[] wins = new long[64], ties = new long[64];
                for (int a = 0; a < remaining.size() - 1; a++)
                    for (int b = a + 1; b < remaining.size(); b++) {
                        var evaluations = new com.pokerlab.core.hand.EvaluatedHand[6];
                        for (int seat = 0; seat < 6; seat++) {
                            var cards = new ArrayList<>(board);
                            cards.add(remaining.get(a));
                            cards.add(remaining.get(b));
                            cards.add(hands.get(seat).first());
                            cards.add(hands.get(seat).second());
                            evaluations[seat] = HandEvaluator.evaluateBest(cards);
                        }
                        for (int first = 0; first < 6; first++)
                            for (int second = first + 1; second < 6; second++) {
                                int mask = (1 << first) | (1 << second);
                                int comparison = evaluations[first].compareTo(evaluations[second]);
                                if (comparison > 0) wins[mask]++;
                                if (comparison == 0) ties[mask]++;
                            }
                    }
                for (var pair : row.pairs()) {
                    assertEquals(wins[pair.activeMask()], pair.firstWins().get(o));
                    assertEquals(ties[pair.activeMask()], pair.ties().get(o));
                }
                examined++;
            }
        }
        assertTrue(examined > 800, "All legal refined world/flop entries must be checked");
    }

    private record Case(
            SixMaxFlopConditionalDiagnostics.History history,
            SixMaxFlopConditionalDiagnostics.Case signal) {}

    @ParameterizedTest
    @ValueSource(ints = {100, 500})
    void completePolicyAllConditionalCasesAndPhysicalBoardWitnessesReplay(int iterations)
            throws Exception {
        var cp =
                SixMaxSuitRefinementStudy.read(
                        Path.of(PREFIX + "-policy-" + iterations + ".json.gz"),
                        source,
                        parent,
                        table);
        var report =
                SixMaxSuitRefinementStudy.replay(
                        Path.of(PREFIX + "-study-" + iterations + ".json.gz"),
                        source,
                        parent,
                        table,
                        cp);
        var game = SixMaxSuitRefinementStudy.rebuild(source, parent, table, cp);
        assertEquals(
                "19c76977afa8005adcfc1714bb2bcd24c0089f6df0f7cecf2a468e158fe0af15", cp.gameHash());
        assertEquals(935455, game.completeTreeStates());
        assertEquals(70703, cp.solution().strategy().size());
        assertEquals(iterations, cp.solution().iterations());
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        var diagnostics = report.jointlySolvedDiagnostics();
        assertEquals(7806, diagnostics.summary().auditedSignals());
        assertEquals(0, diagnostics.summary().zeroReachHistories());
        assertEquals(0, diagnostics.summary().signalsWithoutReachedPrivateSupport());
        var recomputed = new double[6];
        for (var history : diagnostics.histories()) {
            assertEquals(1, history.signalProbabilitiesSum(), 1e-12);
            assertEquals(1301, history.signals().size());
            for (var signal : history.signals()) {
                assertEquals(
                        table.observations().get(signal.observation()).key(),
                        signal.observationKey());
                assertEquals(
                        1,
                        signal.firstMarginal().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
                assertEquals(
                        1,
                        signal.secondMarginal().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-12);
                assertEquals(
                        0,
                        signal.quality().profileUtilitiesBb().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum(),
                        1e-10);
                for (int player = 0; player < 6; player++)
                    recomputed[player] +=
                            history.historyProbability()
                                    * signal.signalProbabilityGivenHistory()
                                    * signal.quality().deviationGainsBb().get(player);
            }
        }
        for (int player = 0; player < 6; player++) {
            assertEquals(
                    recomputed[player],
                    diagnostics.parentWitness().reachWeightedLocalGainsBb().get(player),
                    1e-12);
            assertEquals(0, diagnostics.parentWitness().embeddingErrorsBb().get(player), 1e-9);
            assertTrue(
                    recomputed[player]
                            <= diagnostics
                                            .parentWitness()
                                            .parentQuality()
                                            .deviationGainsBb()
                                            .get(player)
                                    + 1e-9);
        }
        var worst =
                diagnostics.histories().stream()
                        .flatMap(h -> h.signals().stream().map(s -> new Case(h, s)))
                        .sorted(
                                Comparator.comparingDouble(
                                                (Case c) -> c.signal().quality().nashConvBb())
                                        .reversed())
                        .limit(5)
                        .toList();
        for (var c : worst)
            SixMaxRankTextureConditionalAuditTest.bruteCheck(
                    game,
                    game.sourceGame(),
                    SixMaxSuitRefinementPayoffTable.view(table),
                    cp.solution(),
                    c.history().history(),
                    c.signal().observation(),
                    c.signal().signalProbabilityGivenHistory(),
                    c.signal().quality());
        var boards =
                SixMaxSuitRefinementBoardAudit.replay(
                        Path.of(PREFIX + "-board-witnesses-" + iterations + ".json.gz"),
                        source,
                        parent,
                        table,
                        cp);
        assertEquals(17, boards.requestedBoards().size());
        assertEquals(551448, boards.enumeratedRunouts());
        assertTrue(boards.maximumPrivateWorldShareDifference() < 1e-12);
        assertTrue(boards.maximumWitnessShareDifference() < 1e-12);
        assertEquals(
                2,
                boards.histories().stream()
                        .flatMap(h -> h.blockedBoards().stream())
                        .distinct()
                        .count());
        for (var history : boards.histories())
            for (var witness : history.witnesses()) {
                assertEquals(
                        witness.boardProbabilityGivenHistory(),
                        witness.modelObservationProbabilityGivenHistory(),
                        1e-15);
                assertEquals(0, witness.shareDifference(), 1e-12);
                assertEquals(0, witness.maximumPrivateWorldShareDifference(), 1e-12);
            }
    }
}
