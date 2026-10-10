package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedRootDecisionIntervalsTest {
    static SixMaxSuppliedRootDecisionIntervals.Result result;

    static Path data(String suffix) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-root-five-target-aj-" + suffix + ".json");
    }

    @BeforeAll
    static void replay() throws Exception {
        result =
                SixMaxSuppliedRootDecisionIntervals.replay(
                        data("decision-request"), data("decision-report"));
    }

    @Test
    void allMovesShareExactPhysicalEvidenceAndRetainEveryExistingModelBoundary() {
        var r = result.report();
        assertFalse(r.trainerAdmission());
        assertEquals(SixMaxSuppliedRootActionIntervals.STATUS, r.publicationStatus());
        assertEquals(
                SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                r.binding().sourceHistoryReachStatus());
        assertEquals(27, r.prior().size());
        assertEquals(17766216, r.binding().budget().enumeratedBoards());
        assertEquals(1, r.heroCommittedBb());
        assertEquals(
                List.of("fold", "call", "raise:9.0"),
                r.moves().stream().map(SixMaxSuppliedRootDecisionIntervals.Move::action).toList());
        assertTrue(
                r.moves().stream()
                        .allMatch(
                                m ->
                                        !m.diagnostic().trainerAdmission()
                                                && m.lowerEvBb() <= m.upperEvBb()
                                                && m.diagnostic().questionChanceMass() == 1.0 / 3
                                                && m.diagnostic().securitySlack() == 1e-8));
        assertEquals(
                1,
                r.moves().stream().map(m -> m.diagnostic().securityFaceHash()).distinct().count());
        assertEquals(1, r.moves().stream().map(m -> m.diagnostic().baseline()).distinct().count());
        assertEquals(
                r.work().intervalLpArithmeticWork(),
                r.moves().stream()
                        .mapToLong(m -> m.diagnostic().work().intervalLpArithmeticWork())
                        .sum());
        assertTrue(r.work().intervalLpArithmeticWork() < BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        assertTrue(r.work().compilerUnits() < r.work().compilerLimit());
        assertEquals(11, r.work().intervalLpSolves());
    }

    @Test
    void literalCheckdownAndFoldValuesAreUniqueWhileTheRaiseHasAWideInterval() {
        var r = result.report();
        double mass = 0, call = 0;
        for (var world : r.prior())
            if (world.dealtCombos().get(5).equals("Ac Jc")) {
                var counts = r.payoffs().get(world.dealIndex()).counts();
                double buttonShare = (counts.firstWins() + .5 * counts.ties()) / counts.boards();
                mass += world.conditionalProbability();
                call += world.conditionalProbability() * (4.5 - 6.5 * buttonShare);
            }
        call /= mass;
        assertEquals(1.273941156068349, call, 1e-12);
        var fold = r.moves().get(0);
        var flat = r.moves().get(1);
        var raise = r.moves().get(2);
        assertEquals(0, fold.lowerEvBb(), 1e-12);
        assertEquals(0, fold.upperEvBb(), 1e-12);
        assertEquals(call, flat.lowerEvBb(), 1e-12);
        assertEquals(call, flat.upperEvBb(), 1e-12);
        assertEquals(.1955340277314056, raise.lowerEvBb(), 2e-8);
        assertEquals(.728954145812905, raise.upperEvBb(), 2e-8);
        assertEquals(6, raise.diagnostic().plans().size());
        assertEquals(7, raise.diagnostic().work().intervalLpSolves());
        assertTrue(raise.upperEvBb() - raise.lowerEvBb() > .53);
        assertEquals(21, raise.diagnostic().upperWitnesses().getFirst().certificate().variables());
        assertEquals(
                22, raise.diagnostic().upperWitnesses().getFirst().certificate().constraints());
    }

    @Test
    void conservativeRegretCanIdentifyTheBestMoveWithoutInventingAUniqueRaiseEv() {
        var moves = result.report().moves();
        assertFalse(moves.get(0).robustlyBestAtCertificateTolerance());
        assertTrue(moves.get(1).robustlyBestAtCertificateTolerance());
        assertFalse(moves.get(2).robustlyBestAtCertificateTolerance());
        assertEquals(0, moves.get(1).upperRegretBb());
        assertEquals(.5449870102549648, moves.get(2).lowerRegretBb(), 2e-8);
        assertEquals(1.0784071283369157, moves.get(2).upperRegretBb(), 2e-8);
        for (var selected : moves) {
            var others = moves.stream().filter(m -> !m.action().equals(selected.action())).toList();
            assertEquals(
                    Math.max(
                            0,
                            others.stream()
                                            .mapToDouble(
                                                    SixMaxSuppliedRootDecisionIntervals.Move
                                                            ::lowerEvBb)
                                            .max()
                                            .orElseThrow()
                                    - selected.upperEvBb()),
                    selected.lowerRegretBb());
            assertEquals(
                    Math.max(
                            0,
                            others.stream()
                                            .mapToDouble(
                                                    SixMaxSuppliedRootDecisionIntervals.Move
                                                            ::upperEvBb)
                                            .max()
                                            .orElseThrow()
                                    - selected.lowerEvBb()),
                    selected.upperRegretBb());
        }
        assertEquals(
                SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE, result.report().regretScope());
    }

    @Test
    void compressedEvidenceIsStrictImmutableAndCannotOverwrite(@TempDir Path dir) throws Exception {
        Path gzip = dir.resolve("report.json.gz");
        SixMaxSuppliedRootDecisionIntervals.write(gzip, result);
        assertEquals(
                result.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        gzip, SixMaxSuppliedRootActionIntervals.MAX_BYTES),
                                SixMaxSuppliedRootDecisionIntervals.Report.class));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedRootDecisionIntervals.write(gzip, result));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRootDecisionIntervals.replay(gzip, gzip));
        assertThrows(UnsupportedOperationException.class, () -> result.report().moves().clear());
        assertTrue(
                Arrays.stream(
                                SixMaxSuppliedRootDecisionIntervals.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void strictLoadingRejectsUnknownFieldsAndDifferentRequestLineage(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report().request());
        tree.put("callerCommitment", 99);
        Path request = dir.resolve("request.json");
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedRootDecisionIntervals.readRequest(request));
        tree.remove("callerCommitment");
        tree.put("securitySlack", 1e-7);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRootDecisionIntervals.replay(request, data("decision-report")));
    }

    @Test
    void rehashedPhysicalCountsStillRequireFullBoardAndNumericalReplay(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        var counts = (ObjectNode) tree.path("payoffs").get(0).path("counts");
        counts.put("firstWins", counts.path("firstWins").asLong() + 1);
        counts.put("secondWins", counts.path("secondWins").asLong() - 1);
        var payoffs =
                mapper.convertValue(
                        tree.path("payoffs"),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                List<SixMaxSuppliedRangePreflopGame.Payoff>>() {});
        ((ObjectNode) tree.path("binding"))
                .put("payoffHash", SixMaxHeadsUpPreflopGame.hash(payoffs));
        Path tampered = dir.resolve("tampered.json");
        Files.writeString(tampered, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedRootDecisionIntervals.replay(
                                        data("decision-request"), tampered));
        assertTrue(error.getMessage().contains("Exact root decision replay differs"));
    }
}
