package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Saved exact evidence replay; no training or millions-of-boards regeneration in CI. */
class SixMaxRankTextureArtifactTest {
    private static final String PREFIX = "../docs/data/sixmax-staged-rank-texture";
    private static SixMaxPreflopSolutionPack source;
    private static SixMaxRankTexturePayoffTable.Artifact table;

    @BeforeAll
    static void load() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        table = SixMaxRankTexturePayoffTable.read(Path.of(PREFIX + "-payoffs.json.gz"), source);
    }

    @Test
    void everyRankSignalRegroupsExactlyIntoThePreviousTextureTable() throws Exception {
        assertEquals(
                "702409adf91e20bed38facb3328889fcb308bf2724589816a5281d1723705bd3",
                SixMaxRankTexturePayoffTable.hash(table));
        assertEquals(1182, table.signals().size());
        assertEquals(12, table.deals().size());
        var coarse =
                SixMaxTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-texture-payoffs.json"), source);
        for (int world = 0; world < table.deals().size(); world++) {
            var fine = table.deals().get(world);
            var old = coarse.deals().get(world);
            assertEquals(old.hands(), fine.hands());
            assertEquals(9880, fine.flopCounts().stream().mapToLong(Long::longValue).sum());
            long[] counts = new long[6];
            for (int signal = 0; signal < table.signals().size(); signal++)
                counts[table.signals().get(signal).texture().ordinal()] +=
                        fine.flopCounts().get(signal);
            for (int texture = 0; texture < 6; texture++)
                assertEquals(old.flopCounts().get(texture).longValue(), counts[texture]);
            for (var pair : fine.pairs()) {
                long[] wins = new long[6], ties = new long[6];
                for (int signal = 0; signal < table.signals().size(); signal++) {
                    int texture = table.signals().get(signal).texture().ordinal();
                    wins[texture] += pair.firstWins().get(signal);
                    ties[texture] += pair.ties().get(signal);
                }
                for (int texture = 0; texture < 6; texture++) {
                    assertEquals(
                            old.pair(pair.activeMask()).firstWins().get(texture).longValue(),
                            wins[texture]);
                    assertEquals(
                            old.pair(pair.activeMask()).ties().get(texture).longValue(),
                            ties[texture]);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"100,0.0297393909307953", "500,0.0013729729940781645"})
    void completeSavedPoliciesAndNamedBoardDiagnosticsReplay(int iterations, double gap)
            throws Exception {
        var cp =
                SixMaxRankTextureStudy.read(
                        Path.of(PREFIX + "-policy-" + iterations + ".json.gz"), source, table);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        Path.of(PREFIX + "-study-" + iterations + ".json").toFile(),
                        SixMaxRankTextureStudy.Report.class);
        var replay = SixMaxRankTextureStudy.assess(source, table, cp);
        assertEquals(saved, replay);
        assertEquals("VALIDATION_ONLY", replay.publicationStatus());
        assertEquals(SixMaxRankTextureStudy.MODEL, cp.model());
        assertEquals(SixMaxTextureStudy.PRUNED_ALGORITHM, cp.algorithm());
        assertEquals("PARENT_INFORMATION_SET_BEST_RESPONSES_ONLY", replay.qualityScope());
        assertEquals(
                "bb3cf0231e9cb367a84ee805df551e041b46f270876ccba891485babffd66e53", cp.gameHash());
        assertEquals(iterations, cp.solution().iterations());
        assertEquals(6, cp.selections().size());
        assertEquals(888205, cp.completeTreeStates());
        assertEquals(65771, cp.solution().strategy().size());
        assertEquals(9161, replay.preflopInformationSets());
        assertEquals(56610, replay.postflopInformationSets());
        assertEquals(1182, replay.publicSignals());
        assertEquals(gap, replay.jointlySolvedInRankTextureGame().nashConvBb(), 1e-12);
        assertTrue(replay.checkdownBaselineInRankTextureGame().nashConvBb() > gap);
        assertTrue(replay.checkdownRecoveryErrorBb().stream().allMatch(e -> Math.abs(e) < 1e-9));
        assertEquals(
                0,
                replay.jointlySolvedInRankTextureGame().profileUtilitiesBb().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1e-9);
        // An optimistic bound that cannot reject the candidate is not admission or a passed screen.
        assertEquals("NOT_RULED_OUT", replay.jointlySolvedPhysicalFlopFeasibility().status());
        assertFalse(replay.jointlySolvedPhysicalFlopFeasibility().numericReachUnresolved());
        var observation =
                mapper.readTree(Path.of(PREFIX + "-traversal-" + iterations + ".json").toFile());
        assertEquals(cp.solutionHash(), observation.get("solutionHash").asText());
        assertEquals(cp.gameHash(), observation.get("gameHash").asText());
        assertEquals(cp.algorithm(), observation.get("algorithm").asText());
        assertEquals(cp.model(), observation.get("model").asText());
        assertEquals(cp.payoffTableHash(), observation.get("payoffTableHash").asText());
        assertEquals(2007858L * iterations, observation.get("visitedNodes").asLong());
        assertEquals(1072812L * iterations, observation.get("terminalNodes").asLong());
        assertEquals(28800L * iterations, observation.get("inactiveUtilityPrunedNodes").asLong());
        assertEquals(
                6L * iterations * cp.completeTreeStates(),
                observation.get("referenceUnprunedVisitedNodes").asLong());
        assertEquals(0, observation.get("sampledChanceNodes").asLong());
        assertEquals(0, observation.get("baselineCorrections").asLong());
        var witness =
                mapper.readValue(
                        Path.of(PREFIX + "-board-witnesses-" + iterations + ".json").toFile(),
                        SixMaxRankTextureBoardAudit.Report.class);
        var requested =
                witness.requestedBoards().stream()
                        .map(board -> board.stream().map(Card::parse).toList())
                        .toList();
        assertEquals(witness, SixMaxRankTextureBoardAudit.assess(source, table, cp, requested));
        assertEquals(cp.solutionHash(), witness.solutionHash());
        assertEquals(cp.gameHash(), witness.gameHash());
        assertEquals(12, requested.size());
        assertEquals(6, witness.histories().size());
        long runouts = 0;
        int boards = 0;
        for (var history : witness.histories()) {
            assertEquals("AUDITED", history.status());
            assertEquals(12, history.witnesses().size() + history.blockedBoards().size());
            for (var board : history.witnesses()) {
                boards++;
                assertEquals(
                        SixMaxRankTexturePayoffTable.Signal.from(
                                Card.parse(board.board().get(0)),
                                Card.parse(board.board().get(1)),
                                Card.parse(board.board().get(2))),
                        board.signal());
                assertEquals(666L * board.deals().size(), board.enumeratedRunouts());
                runouts += board.enumeratedRunouts();
                assertEquals(
                        1,
                        board.deals().stream()
                                .mapToDouble(
                                        SixMaxRankTextureBoardAudit.DealWitness
                                                ::probabilityGivenBoard)
                                .sum(),
                        1e-12);
                assertEquals(
                        board.exactFirstShareGivenBoard(),
                        board.deals().stream()
                                .mapToDouble(d -> d.probabilityGivenBoard() * d.exactFirstShare())
                                .sum(),
                        1e-12);
                assertEquals(
                        board.rankTextureFirstShareUsingBoardPosterior(),
                        board.deals().stream()
                                .mapToDouble(
                                        d -> d.probabilityGivenBoard() * d.rankTextureFirstShare())
                                .sum(),
                        1e-12);
                assertEquals(
                        board.totalShareDifference(),
                        board.runoutAbstractionShareDifference()
                                + board.posteriorInformationShareDifference(),
                        1e-12);
                assertEquals(
                        history.potBb() * board.totalShareDifference(),
                        board.checkdownFirstUtilityDifferenceBb(),
                        1e-12);
            }
        }
        assertEquals(60, boards);
        assertEquals(439560, runouts);
        assertEquals(runouts, witness.enumeratedRunouts());
        // Revealing ranks still leaves actual-suit and posterior information errors.
        assertTrue(witness.maximumWitnessShareDifference() > .4);
    }
}
