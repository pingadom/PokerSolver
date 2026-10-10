package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedConditionalDecisionLossTest {
    static SixMaxSuppliedConditionalDecisionLoss.Result result;
    static SixMaxSuppliedRangePreflopGame game;

    static Path data(String suffix) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-conditional-five-target-99-joint-loss-" + suffix + ".json");
    }

    @BeforeAll
    static void solve() throws Exception {
        var request = SixMaxSuppliedConditionalDecisionLoss.readRequest(data("request"));
        game = new SixMaxSuppliedRangePreflopGame(request.input());
        result =
                SixMaxSuppliedConditionalDecisionLoss.solve(
                        game,
                        request,
                        FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                        BoundedLinearProgram.MAX_PIVOTS,
                        BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        assertEquals(
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Files.readAllBytes(data("report")),
                                SixMaxSuppliedConditionalDecisionLoss.Report.class),
                result.report());
    }

    @Test
    void physicalEvidenceAndActualLaterCommitmentAreSharedAcrossEveryLegalMove() {
        var r = result.report();
        assertEquals(
                9, r.heroCommittedBb()); // BB has already raised to 9, rather than only posted 1.
        assertEquals(27, r.prior().size());
        assertEquals(17766216, r.binding().budget().enumeratedBoards());
        assertEquals(
                SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                r.binding().sourceHistoryReachStatus());
        assertEquals(SixMaxSuppliedConditionalDecisionLoss.STATUS, r.publicationStatus());
        assertEquals(SixMaxSuppliedConditionalDecisionIntervals.CONDITIONING, r.conditioning());
        assertFalse(r.trainerAdmission());
        assertEquals(
                List.of("fold", "call", "raise:50.0"),
                r.moves().stream()
                        .map(SixMaxSuppliedConditionalDecisionLoss.Move::action)
                        .toList());
        assertEquals(
                1,
                r.diagnostic().actions().stream()
                        .map(m -> m.interval().securityFaceHash())
                        .distinct()
                        .count());
        assertEquals(
                1,
                r.diagnostic().actions().stream()
                        .map(m -> m.interval().baseline())
                        .distinct()
                        .count());
        assertTrue(
                r.diagnostic().actions().stream()
                        .allMatch(
                                m ->
                                        !m.interval().trainerAdmission()
                                                && m.interval().requiredMinimumReach() == .01
                                                && m.interval().securitySlack() == 1e-8
                                                && m.interval()
                                                        .fixedHeroPast()
                                                        .equals(
                                                                List.of(
                                                                        new FiniteTwoPlayerSequenceForm
                                                                                .OwnAction(
                                                                                "5:supplied-conditional-preflop:9d 9h:",
                                                                                "raise:9.0")))));
    }

    @Test
    void tightJointLossMatchesIndependentCompletePlanExtrema() {
        var moves = result.report().moves();
        assertEquals(9.112156463485077, moves.get(0).lowerLossBb(), 2e-8);
        assertEquals(9.112156583600543, moves.get(0).upperLossBb(), 2e-8);
        assertEquals(0, moves.get(1).lowerLossBb(), 1e-12);
        assertEquals(4.49999995044692e-8, moves.get(1).upperLossBb(), 1e-10);
        assertEquals(.48322677969277367, moves.get(2).upperLossBb(), 2e-8);
        assertTrue(moves.get(1).upperLossBb() < moves.get(1).conservativeUpperLossBb() / 2);
        assertTrue(
                moves.stream()
                        .noneMatch(
                                SixMaxSuppliedConditionalDecisionLoss.Move
                                        ::robustAtCertificateTolerance));
        assertEquals(FiniteTwoPlayerConditionalDecisionLoss.SCOPE, result.report().regretScope());
        assertEquals(
                SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE,
                result.report().conservativeScope());
        var old = SixMaxSuppliedConditionalDecisionIntervalsTest.data("report");
        assertTrue(Files.exists(old));
        assertEquals(9.112156463485075, moves.get(1).lowerEvBb(), 2e-8);
        assertEquals(9.112156583600541, moves.get(1).upperEvBb(), 2e-8);
    }

    @Test
    void allJointWitnessesRecomputeEveryActionUsingTheSamePosterior() {
        var r = result.report();
        var worlds =
                r.prior().stream().filter(w -> w.dealtCombos().get(5).equals("9d 9h")).toList();
        for (var move : r.diagnostic().moves()) {
            var controls = new ArrayList<>(move.lowerControls());
            controls.addAll(move.upperControls());
            for (var control : controls) {
                var w = control.witness();
                var selected = w.selected();
                assertEquals(9, selected.posterior().size());
                assertTrue(selected.questionReach() > .22 && selected.questionReach() < .23);
                assertTrue(selected.posteriorTotalVariation() <= 1e-8);
                double call = 0;
                for (int i = 0; i < worlds.size(); i++) {
                    var c = r.payoffs().get(worlds.get(i).dealIndex()).counts();
                    double share = (c.firstWins() + .5 * c.ties()) / c.boards();
                    call += selected.posterior().get(i).probability() * (31.5 - 44.5 * share);
                }
                assertEquals(call, w.actionUtilities().get("call") + 9, 1e-10);
                assertEquals(-9, w.actionUtilities().get("fold"), 1e-12);
                assertEquals(
                        Math.max(
                                0,
                                Collections.max(w.actionUtilities().values())
                                        - w.actionUtilities().get(move.action())),
                        w.decisionLoss());
            }
            assertEquals(
                    move.lowerLoss(),
                    move.lowerControls().get(move.lowerControl()).witness().decisionLoss(),
                    1e-8);
            assertEquals(
                    move.upperLoss(),
                    move.upperControls().get(move.upperControl()).witness().decisionLoss(),
                    1e-8);
        }
    }

    @Test
    void jointAndIntervalControlsShareAggregateCaps() throws Exception {
        var r = result.report();
        var work = r.diagnostic().work();
        assertEquals(25, work.intervalLpSolves());
        assertTrue(
                work.intervalLpArithmeticWork()
                        > r.diagnostic().actions().stream()
                                .mapToLong(a -> a.interval().work().intervalLpArithmeticWork())
                                .sum());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionLoss.solve(
                                game,
                                r.request(),
                                work.compilerUnits() - 1,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        SixMaxSuppliedConditionalDecisionLoss.solve(
                                game,
                                r.request(),
                                8_000_000,
                                work.intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        SixMaxSuppliedConditionalDecisionLoss.solve(
                                game,
                                r.request(),
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                work.intervalLpArithmeticWork() - 1));
    }

    @Test
    void compressedReplayIsExactImmutableAndCannotOverwrite(@TempDir Path dir) throws Exception {
        Path gzip = dir.resolve("report.json.gz");
        SixMaxSuppliedConditionalDecisionLoss.write(gzip, result);
        assertEquals(
                result.report(),
                SixMaxSuppliedConditionalDecisionLoss.replay(data("request"), gzip).report());
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedConditionalDecisionLoss.write(gzip, result));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionLoss.replay(gzip, gzip));
        assertThrows(UnsupportedOperationException.class, () -> result.report().moves().clear());
        assertTrue(
                Arrays.stream(
                                SixMaxSuppliedConditionalDecisionLoss.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void strictLoadingRejectsUnknownFieldsInvalidFloorAndDifferentRequestLineage(@TempDir Path dir)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report().request());
        tree.put("callerCommitment", 1);
        Path request = dir.resolve("request.json");
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedConditionalDecisionLoss.readRequest(request));
        tree.remove("callerCommitment");
        tree.put("minimumReach", 0);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                Exception.class, () -> SixMaxSuppliedConditionalDecisionLoss.readRequest(request));
        tree.put("minimumReach", .02);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionLoss.replay(request, data("report")));
    }

    @Test
    void poisonedPosteriorAndLpEvidenceCannotSurviveFullReplay(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        var witness =
                (ObjectNode)
                        tree.path("diagnostic")
                                .path("moves")
                                .get(2)
                                .path("lowerControls")
                                .get(0)
                                .path("witness")
                                .path("selected");
        ((ObjectNode) witness.path("posterior").get(0)).put("probability", .9);
        ((ObjectNode) witness.path("certificate")).put("primalValue", -999);
        Path tampered = dir.resolve("tampered.json");
        Files.writeString(tampered, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedConditionalDecisionLoss.replay(
                                        data("request"), tampered));
        assertTrue(error.getMessage().contains("Exact joint loss replay differs"));
    }

    @Test
    void rehashedPhysicalCountsStillRequireReenumeratingTheBoards(@TempDir Path dir)
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
        Path tampered = dir.resolve("counts.json");
        Files.writeString(tampered, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedConditionalDecisionLoss.replay(
                                        data("request"), tampered));
        assertTrue(error.getMessage().contains("Exact joint loss replay differs"));
    }

    @Test
    void cliRejectsAmbiguousPathsAndExistingOutputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionLossMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionLossMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("request").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionLossMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("report").toString()
                                }));
    }
}
