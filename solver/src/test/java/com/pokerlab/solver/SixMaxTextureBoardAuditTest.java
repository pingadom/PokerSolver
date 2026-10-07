package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxTextureBoardAuditTest {
    @Test
    void boardBlockersChangeTheTexturePosteriorAndZeroReachIsReportedExplicitly() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var originalTable = SixMaxTextureFlopGameTest.table(source);
        var rows = new java.util.ArrayList<>(originalTable.deals());
        var original = rows.getFirst();
        var pairs = new java.util.ArrayList<>(original.pairs());
        int mask =
                (1 << PreflopAllInSpot.Seat.BTN.ordinal())
                        | (1 << PreflopAllInSpot.Seat.BB.ordinal());
        var pair = original.pair(mask);
        var wins = new java.util.ArrayList<>(pair.firstWins());
        var ties = new java.util.ArrayList<>(pair.ties());
        // Redistribute one win between two texture totals while retaining the exact .5 marginal.
        wins.set(2, 1L);
        ties.set(2, ties.get(2) - 1);
        ties.set(1, ties.get(1) - 1);
        pairs.set(pairs.indexOf(pair), new SixMaxTexturePayoffTable.Pair(mask, wins, ties));
        rows.set(
                0,
                new SixMaxTexturePayoffTable.Deal(original.hands(), original.flopCounts(), pairs));
        var table =
                new SixMaxTexturePayoffTable.Artifact(
                        originalTable.schemaVersion(),
                        originalTable.publicationStatus(),
                        originalTable.classifier(),
                        originalTable.sourcePackHash(),
                        originalTable.sourceSpotHash(),
                        rows);
        SixMaxTexturePayoffTable.validate(table, source);
        var game =
                new SixMaxTextureFlopGame(
                        source,
                        table,
                        List.of(
                                new SixMaxTextureFlopGame.Selection(
                                        SixMaxConnectedPreflopGameTest.HISTORY, .5)));
        var cp =
                SixMaxTextureStudy.checkpoint(
                        source, table, game, game.checkdownBaseline(source.solution()));
        var boards = List.of(SixMaxTexturePayoffTableTest.cards("Ac 2h 3d"));
        var witness =
                SixMaxTextureBoardAudit.assess(source, table, cp, boards)
                        .histories()
                        .getFirst()
                        .witnesses()
                        .getFirst();
        assertEquals(.5, witness.textureFirstShareUsingBoardPosterior(), 1e-15);
        assertTrue(Math.abs(witness.posteriorInformationShareDifference()) > 1e-9);
        var transition =
                new SixMaxPolicyFlopTransition(
                        game.sourceGame(),
                        source.solution(),
                        game.selections().getFirst().history());
        double sum = 0, mass = 0;
        for (var deal : transition.deals()) {
            var row =
                    table.deals().stream()
                            .filter(
                                    d ->
                                            d.hands()
                                                    .equals(
                                                            deal.hands().stream()
                                                                    .map(WeightedCombo::key)
                                                                    .toList()))
                            .findFirst()
                            .orElseThrow();
            double weight = deal.probability() * row.flopCounts().get(2);
            mass += weight;
            sum +=
                    weight
                            * row.pair(mask)
                                    .share(
                                            transition.firstToAct().ordinal(),
                                            2,
                                            row.flopCounts().get(2) * 666);
        }
        assertEquals(sum / mass, witness.textureFirstShareGivenTexture(), 1e-15);
        var zero = new java.util.LinkedHashMap<>(source.solution().strategy());
        zero.replaceAll(
                (key, row) -> {
                    if (!key.startsWith("0:") || !row.containsKey("raise:3.0")) return row;
                    var replacement = new java.util.LinkedHashMap<String, Double>();
                    row.forEach(
                            (action, value) ->
                                    replacement.put(
                                            action, action.equals("raise:3.0") ? 1.0 : 0.0));
                    return replacement;
                });
        var zeroCp =
                SixMaxTextureStudy.checkpoint(
                        source,
                        table,
                        game,
                        game.checkdownBaseline(
                                new CfrSolution(source.solution().iterations(), zero)));
        var zeroReport = SixMaxTextureBoardAudit.assess(source, table, zeroCp, boards);
        assertEquals("ZERO_POLICY_REACH", zeroReport.histories().getFirst().status());
        assertEquals(0, zeroReport.enumeratedRunouts());
        assertTrue(zeroReport.histories().getFirst().witnesses().isEmpty());
        var tiny = new java.util.LinkedHashMap<>(source.solution().strategy());
        tiny.replaceAll(
                (key, row) -> {
                    if (!key.startsWith("0:")
                            || !key.contains("Ah As")
                            || !row.containsKey("raise:3.0")) return row;
                    var replacement = new java.util.LinkedHashMap<String, Double>();
                    row.forEach(
                            (action, value) ->
                                    replacement.put(
                                            action,
                                            action.equals("raise:3.0")
                                                    ? 1.0
                                                    : action.equals("fold")
                                                            ? Double.MIN_VALUE
                                                            : 0.0));
                    return replacement;
                });
        var tinyCp =
                SixMaxTextureStudy.checkpoint(
                        source,
                        table,
                        game,
                        game.checkdownBaseline(
                                new CfrSolution(source.solution().iterations(), tiny)));
        // Ac blocks the ordinary world, leaving only the positive but numerically lost world.
        // This must fail, rather than claim that the board is physically impossible.
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureBoardAudit.assess(source, table, tinyCp, boards));
    }

    @Test
    void namedBoardsKeepFoldedBlockersAndSeparatePayoffAndPosteriorInformation() throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxTextureFlopGameTest.table(source);
        var game =
                new SixMaxTextureFlopGame(
                        source,
                        table,
                        List.of(
                                new SixMaxTextureFlopGame.Selection(
                                        SixMaxConnectedPreflopGameTest.HISTORY, .5)));
        var cp =
                SixMaxTextureStudy.checkpoint(
                        source, table, game, game.checkdownBaseline(source.solution()));
        var boards =
                List.of(
                        SixMaxTexturePayoffTableTest.cards("As 2c 3d"),
                        SixMaxTexturePayoffTableTest.cards("Ac As 2h"));
        var report = SixMaxTextureBoardAudit.assess(source, table, cp, boards);
        assertEquals("VALIDATION_ONLY", report.publicationStatus());
        var history = report.histories().getFirst();
        assertEquals("AUDITED", history.status());
        assertEquals(1, history.blockedBoards().size());
        assertEquals(List.of("2h", "Ac", "As"), history.blockedBoards().getFirst());
        var witness = history.witnesses().getFirst();
        assertEquals(1, witness.deals().size());
        assertEquals(666, witness.enumeratedRunouts());
        assertEquals(666, report.enumeratedRunouts());
        assertEquals(1, witness.deals().getFirst().probabilityGivenBoard());
        // As belongs to a folded UTG seat in one world; seeing it eliminates that world.
        assertEquals("Ac Ad", witness.deals().getFirst().hands().getFirst());
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        var transition = new SixMaxPolicyFlopTransition(game.sourceGame(), pre, history.history());
        var flop = transition.conditionOnFlop(boards.getFirst());
        assertEquals(flop.probability(), witness.boardProbabilityGivenHistory(), 0);
        var dealt = flop.deals().getFirst().hands();
        var deck = flop.undealtCards(0);
        assertEquals(37, deck.size());
        double score = 0;
        for (int a = 0; a < deck.size(); a++)
            for (int b = a + 1; b < deck.size(); b++) {
                var first = dealt.get(history.firstToAct().ordinal());
                var second = dealt.get(history.secondToAct().ordinal());
                int comparison =
                        HandEvaluator.evaluateBest(
                                        first.first(),
                                        first.second(),
                                        boards.getFirst().get(0),
                                        boards.getFirst().get(1),
                                        boards.getFirst().get(2),
                                        deck.get(a),
                                        deck.get(b))
                                .compareTo(
                                        HandEvaluator.evaluateBest(
                                                second.first(),
                                                second.second(),
                                                boards.getFirst().get(0),
                                                boards.getFirst().get(1),
                                                boards.getFirst().get(2),
                                                deck.get(a),
                                                deck.get(b)));
                score += comparison > 0 ? 1 : comparison == 0 ? .5 : 0;
            }
        assertEquals(score / 666, witness.exactFirstShareGivenBoard(), 1e-15);
        // Synthetic all-tie table makes the coarse reference exactly .5.
        assertEquals(.5, witness.textureFirstShareGivenTexture(), 1e-15);
        assertEquals(
                witness.totalShareDifference(),
                witness.runoutAbstractionShareDifference()
                        + witness.posteriorInformationShareDifference(),
                1e-15);
        assertEquals(
                history.potBb() * witness.totalShareDifference(),
                witness.checkdownFirstUtilityDifferenceBb(),
                1e-15);
        assertEquals(
                (history.potBb() + 2 * history.betBb()) * witness.totalShareDifference(),
                witness.calledPotFirstUtilityDifferenceBb(),
                1e-15);
        assertEquals(
                SixMaxTextureStudy.json(report),
                SixMaxTextureStudy.json(SixMaxTextureBoardAudit.assess(source, table, cp, boards)));
    }

    @Test
    void rejectsInvalidFlopsIncompletePoliciesAndProtectsFiles(@TempDir Path temp)
            throws Exception {
        var source = SixMaxTextureFlopGameTest.source();
        var table = SixMaxTextureFlopGameTest.table(source);
        var game =
                new SixMaxTextureFlopGame(
                        source,
                        table,
                        List.of(
                                new SixMaxTextureFlopGame.Selection(
                                        SixMaxConnectedPreflopGameTest.HISTORY, .5)));
        var cp =
                SixMaxTextureStudy.checkpoint(
                        source, table, game, game.checkdownBaseline(source.solution()));
        var boards = List.of(SixMaxTexturePayoffTableTest.cards("2c 3d 4h"));
        var partial = SixMaxTextureStudy.checkpoint(source, table, game, source.solution());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureBoardAudit.assess(source, table, partial, boards));
        for (var invalid :
                List.of(
                        List.<List<Card>>of(),
                        List.of(SixMaxTexturePayoffTableTest.cards("2c 2c 3d")),
                        List.of(SixMaxTexturePayoffTableTest.cards("2c 3d")),
                        List.of(boards.getFirst(), SixMaxTexturePayoffTableTest.cards("4h 2c 3d")),
                        java.util.Collections.nCopies(25, boards.getFirst())))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxTextureBoardAudit.assess(source, table, cp, invalid));
        var sourcePath = temp.resolve("source.json");
        var tablePath = temp.resolve("table.json");
        var cpPath = temp.resolve("cp.json.gz");
        var output = temp.resolve("report.json");
        Files.writeString(sourcePath, MultiwayPackJson.writeFullRound(source));
        Files.writeString(tablePath, SixMaxTexturePayoffTable.json(table));
        SixMaxTextureStudy.write(cpPath, cp, source, table);
        String[] args = {
            sourcePath.toString(),
            tablePath.toString(),
            cpPath.toString(),
            output.toString(),
            "2c3d4h"
        };
        SixMaxTextureBoardAuditMain.main(args);
        var bytes = Files.readAllBytes(output);
        SixMaxTextureBoardAuditMain.main(args);
        assertArrayEquals(bytes, Files.readAllBytes(output));
        args[4] = "2c3d4h;4h3d2c";
        assertThrows(IllegalArgumentException.class, () -> SixMaxTextureBoardAuditMain.main(args));
        assertArrayEquals(bytes, Files.readAllBytes(output));
        args[4] = "2c3d4h";
        var alias = temp.resolve("hardlink.json");
        Files.createLink(alias, sourcePath);
        args[3] = alias.toString();
        assertThrows(IllegalArgumentException.class, () -> SixMaxTextureBoardAuditMain.main(args));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxTextureBoardAuditMain.main(new String[0]));
    }
}
