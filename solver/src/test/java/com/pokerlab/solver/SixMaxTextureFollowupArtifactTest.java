package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Independent saved-evidence replay, without training or regenerating the texture table. */
class SixMaxTextureFollowupArtifactTest {
    @Test
    void pairedPruningAndNamedBoardEvidenceReplaysWithoutRelaxingContentGates() throws Exception {
        var source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        var table =
                SixMaxTexturePayoffTable.read(
                        Path.of("../docs/data/sixmax-staged-texture-payoffs.json"), source);
        var original =
                SixMaxTextureStudy.read(
                        Path.of("../docs/data/sixmax-staged-texture-broad-policy-1000.json.gz"),
                        source,
                        table);
        var pruned =
                SixMaxTextureStudy.read(
                        Path.of("../docs/data/sixmax-staged-texture-pruned-policy-1000.json.gz"),
                        source,
                        table);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var savedComparison =
                mapper.readValue(
                        Path.of("../docs/data/sixmax-staged-texture-pruning-comparison-1000.json")
                                .toFile(),
                        SixMaxTexturePruningAudit.Report.class);
        assertEquals(
                savedComparison, SixMaxTexturePruningAudit.assess(source, table, original, pruned));
        assertTrue(savedComparison.frequenciesWithinTolerance());
        assertTrue(savedComparison.maximumActionFrequencyDifference() < 1e-10);
        assertNotEquals(original.solutionHash(), pruned.solutionHash());
        assertEquals(9449, savedComparison.informationSets());
        assertEquals(142681, savedComparison.completeTreeStates());
        var observation =
                mapper.readTree(
                        Path.of("../docs/data/sixmax-staged-texture-pruned-traversal-1000.json")
                                .toFile());
        assertEquals(pruned.solutionHash(), observation.get("solutionHash").asText());
        assertEquals(pruned.gameHash(), observation.get("gameHash").asText());
        assertEquals(pruned.algorithm(), observation.get("algorithm").asText());
        assertEquals(
                6L * 1000 * pruned.completeTreeStates(),
                observation.get("referenceVisitedNodes").asLong());
        assertEquals(516810000, observation.get("visitedNodes").asLong());
        assertEquals(28800000, observation.get("inactiveUtilityPrunedNodes").asLong());
        assertEquals(0, observation.get("sampledChanceNodes").asLong());
        var saved =
                mapper.readValue(
                        Path.of("../docs/data/sixmax-staged-texture-board-witnesses-1000.json")
                                .toFile(),
                        SixMaxTextureBoardAudit.Report.class);
        var requested =
                saved.requestedBoards().stream()
                        .map(board -> board.stream().map(Card::parse).toList())
                        .toList();
        assertEquals(saved, SixMaxTextureBoardAudit.assess(source, table, pruned, requested));
        assertEquals("VALIDATION_ONLY", saved.publicationStatus());
        assertEquals(12, saved.requestedBoards().size());
        assertEquals(6, saved.histories().size());
        long runouts = 0;
        for (var history : saved.histories()) {
            assertEquals("AUDITED", history.status());
            assertEquals(12, history.witnesses().size() + history.blockedBoards().size());
            for (var witness : history.witnesses()) {
                assertEquals(666L * witness.deals().size(), witness.enumeratedRunouts());
                runouts += witness.enumeratedRunouts();
                assertEquals(
                        1,
                        witness.deals().stream()
                                .mapToDouble(
                                        SixMaxTextureBoardAudit.DealWitness::probabilityGivenBoard)
                                .sum(),
                        1e-12);
                assertEquals(
                        witness.exactFirstShareGivenBoard(),
                        witness.deals().stream()
                                .mapToDouble(
                                        deal ->
                                                deal.probabilityGivenBoard()
                                                        * deal.exactFirstShare())
                                .sum(),
                        1e-12);
                assertEquals(
                        witness.totalShareDifference(),
                        witness.runoutAbstractionShareDifference()
                                + witness.posteriorInformationShareDifference(),
                        1e-12);
            }
        }
        assertEquals(runouts, saved.enumeratedRunouts());
        assertTrue(saved.maximumWitnessShareDifference() > .1);
    }
}
