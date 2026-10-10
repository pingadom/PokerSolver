package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedConditionalDecisionIntervalsTest {
    static SixMaxSuppliedConditionalDecisionIntervals.Result result;
    static SixMaxSuppliedRangePreflopGame game;

    static Path data(String suffix) {
        return SixMaxSuppliedRangePreflopGameTest.data(
                "supplied-conditional-five-target-99-decision-" + suffix + ".json");
    }

    @BeforeAll
    static void solve() throws Exception {
        var request = SixMaxSuppliedConditionalDecisionIntervals.readRequest(data("request"));
        game = new SixMaxSuppliedRangePreflopGame(request.input());
        result =
                SixMaxSuppliedConditionalDecisionIntervals.solve(
                        game,
                        request,
                        FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                        BoundedLinearProgram.MAX_PIVOTS,
                        BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        assertEquals(
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                Files.readAllBytes(data("report")),
                                SixMaxSuppliedConditionalDecisionIntervals.Report.class),
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
        assertEquals(SixMaxSuppliedConditionalDecisionIntervals.STATUS, r.publicationStatus());
        assertEquals(SixMaxSuppliedConditionalDecisionIntervals.CONDITIONING, r.conditioning());
        assertFalse(r.trainerAdmission());
        assertEquals(
                List.of("fold", "call", "raise:50.0"),
                r.moves().stream()
                        .map(SixMaxSuppliedConditionalDecisionIntervals.Move::action)
                        .toList());
        assertEquals(
                1,
                r.moves().stream().map(m -> m.diagnostic().securityFaceHash()).distinct().count());
        assertEquals(1, r.moves().stream().map(m -> m.diagnostic().baseline()).distinct().count());
        assertTrue(
                r.moves().stream()
                        .allMatch(
                                m ->
                                        !m.diagnostic().trainerAdmission()
                                                && m.diagnostic().requiredMinimumReach() == .01
                                                && m.diagnostic().securitySlack() == 1e-8
                                                && m.diagnostic()
                                                        .fixedHeroPast()
                                                        .equals(
                                                                List.of(
                                                                        new FiniteTwoPlayerSequenceForm
                                                                                .OwnAction(
                                                                                "5:supplied-conditional-preflop:9d 9h:",
                                                                                "raise:9.0")))));
    }

    @Test
    void actionDependentPosteriorBoundsMatchIndependentLiteralNormalFormControls() {
        var moves = result.report().moves();
        assertEquals(0, moves.get(0).lowerEvBb(), 1e-12);
        assertEquals(0, moves.get(0).upperEvBb(), 1e-12);
        assertEquals(9.112156463485075, moves.get(1).lowerEvBb(), 2e-8);
        assertEquals(9.112156583600541, moves.get(1).upperEvBb(), 2e-8);
        assertEquals(8.628929683792302, moves.get(2).lowerEvBb(), 2e-8);
        assertEquals(9.11215658360054, moves.get(2).upperEvBb(), 2e-8);
        assertTrue(moves.get(2).upperEvBb() - moves.get(2).lowerEvBb() > .48);
        assertEquals(2, moves.get(2).diagnostic().numeratorPlans().size());
        assertTrue(
                moves.stream()
                        .noneMatch(
                                SixMaxSuppliedConditionalDecisionIntervals.Move
                                        ::robustlyBestAtCertificateTolerance));
        // Interval overlap does not prove exact joint dominance; regret bounds are conservative.
        assertTrue(moves.get(1).upperRegretBb() > 1e-8 && moves.get(1).upperRegretBb() < 2e-7);
        assertEquals(.483226899808238, moves.get(2).upperRegretBb(), 2e-8);
        assertEquals(
                SixMaxSuppliedRootDecisionIntervals.REGRET_SCOPE, result.report().regretScope());
    }

    @Test
    void everyWitnessHasPositiveReachAndCallValueUsesItsOwnHiddenHandPosterior() {
        var r = result.report();
        var worlds =
                r.prior().stream().filter(w -> w.dealtCombos().get(5).equals("9d 9h")).toList();
        for (var move : r.moves()) {
            var audit = move.diagnostic();
            assertEquals(.22222203735735516, audit.minimumReachWitness().questionReach(), 2e-8);
            assertEquals(.22222223099956337, audit.maximumReachWitness().questionReach(), 2e-8);
            assertTrue(audit.certifiedReachLowerBound() >= .01);
            var witnesses = new ArrayList<>(audit.upperWitnesses());
            witnesses.addAll(
                    List.of(
                            audit.minimumReachWitness(),
                            audit.maximumReachWitness(),
                            audit.lowerWitness()));
            for (var w : witnesses) {
                assertEquals(9, w.posterior().size());
                assertEquals(
                        1,
                        w.posterior().stream()
                                .mapToDouble(
                                        FiniteTwoPlayerConditionalActionIntervals.Posterior
                                                ::probability)
                                .sum(),
                        1e-12);
                assertTrue(w.posteriorTotalVariation() <= 1e-8);
                assertTrue(
                        w.globalHeroBestResponse()
                                <= audit.globalHeroUpperValue() + audit.securitySlack() + 1e-8);
                if (move.action().equals("call")) {
                    double call = 0;
                    for (int i = 0; i < worlds.size(); i++) {
                        var counts = r.payoffs().get(worlds.get(i).dealIndex()).counts();
                        double share = (counts.firstWins() + .5 * counts.ties()) / counts.boards();
                        call += w.posterior().get(i).probability() * (31.5 - 44.5 * share);
                    }
                    assertEquals(call, w.conditionalHeroBestResponse() + 9, 1e-10);
                }
            }
        }
    }

    @Test
    void allMovesShareCompilerAndLpCapsIncludingTheirReachControls() throws Exception {
        var r = result.report();
        assertEquals(13, r.work().intervalLpSolves());
        assertEquals(
                r.work().intervalLpArithmeticWork(),
                r.moves().stream()
                        .mapToLong(m -> m.diagnostic().work().intervalLpArithmeticWork())
                        .sum());
        assertTrue(r.work().compilerUnits() < r.work().compilerLimit());
        assertTrue(r.work().intervalLpArithmeticWork() < BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionIntervals.solve(
                                game,
                                r.request(),
                                r.work().compilerUnits() - 1,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        SixMaxSuppliedConditionalDecisionIntervals.solve(
                                game,
                                r.request(),
                                8_000_000,
                                r.work().intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        SixMaxSuppliedConditionalDecisionIntervals.solve(
                                game,
                                r.request(),
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                r.work().intervalLpArithmeticWork() - 1));
    }

    @Test
    void compressedReplayIsExactImmutableAndCannotOverwrite(@TempDir Path dir) throws Exception {
        Path gzip = dir.resolve("report.json.gz");
        SixMaxSuppliedConditionalDecisionIntervals.write(gzip, result);
        assertEquals(
                result.report(),
                SixMaxSuppliedConditionalDecisionIntervals.replay(data("request"), gzip).report());
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedConditionalDecisionIntervals.write(gzip, result));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionIntervals.replay(gzip, gzip));
        assertThrows(UnsupportedOperationException.class, () -> result.report().moves().clear());
        assertTrue(
                Arrays.stream(
                                SixMaxSuppliedConditionalDecisionIntervals.Result.class
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
                Exception.class,
                () -> SixMaxSuppliedConditionalDecisionIntervals.readRequest(request));
        tree.remove("callerCommitment");
        tree.put("minimumReach", 0);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                Exception.class,
                () -> SixMaxSuppliedConditionalDecisionIntervals.readRequest(request));
        tree.put("minimumReach", .02);
        Files.writeString(request, mapper.writeValueAsString(tree));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionIntervals.replay(request, data("report")));
    }

    @Test
    void poisonedPosteriorAndLpEvidenceCannotSurviveFullReplay(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        var witness =
                (ObjectNode) tree.path("moves").get(2).path("diagnostic").path("lowerWitness");
        ((ObjectNode) witness.path("posterior").get(0)).put("probability", .9);
        ((ObjectNode) witness.path("certificate")).put("primalValue", -999);
        Path tampered = dir.resolve("tampered.json");
        Files.writeString(tampered, mapper.writeValueAsString(tree));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedConditionalDecisionIntervals.replay(
                                        data("request"), tampered));
        assertTrue(error.getMessage().contains("Exact conditional decision replay differs"));
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
                                SixMaxSuppliedConditionalDecisionIntervals.replay(
                                        data("request"), tampered));
        assertTrue(error.getMessage().contains("Exact conditional decision replay differs"));
    }

    @Test
    void cliRejectsAmbiguousPathsAndExistingOutputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedConditionalDecisionIntervalsMain.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionIntervalsMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("request").toString()
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedConditionalDecisionIntervalsMain.main(
                                new String[] {
                                    "solve", data("request").toString(), data("report").toString()
                                }));
    }
}
