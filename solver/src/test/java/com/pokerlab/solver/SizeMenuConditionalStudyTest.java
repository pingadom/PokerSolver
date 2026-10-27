package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizeMenuConditionalStudyTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-size-menu-99-conditional-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-size-menu-99-conditional-report.json.gz");
    static SixMaxSizeMenuConditionalStudy.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxSizeMenuConditionalStudy.Result result;

    @BeforeAll
    static void replayOwnedPhysicsAndAllThreeActions() throws Exception {
        request = SixMaxSizeMenuConditionalStudy.readRequest(REQUEST);
        game =
                new SixMaxSuppliedFlopSizeMenuGame(
                        request.parent().input(), request.parent().flop(), request.parent().menu());
        result = SixMaxSizeMenuConditionalStudy.solve(request, game);
    }

    @Test
    void completeSavedStudyAndOriginalParentReplayExactly() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                REPORT, SixMaxSizeMenuConditionalStudy.MAX_BYTES),
                        SixMaxSizeMenuConditionalStudy.Report.class);
        assertEquals(saved, result.report());
        var original =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/supplied-flop-size-menu-report.json.gz"),
                                SixMaxSizeMenuConditionalStudy.MAX_BYTES),
                        SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(original, result.report().parent());
        assertFalse(result.report().trainerAdmission());
    }

    @Test
    void completeMenuHasAllCertificatesAndContinuationPlans() {
        var report = result.report();
        assertEquals(
                List.of("check", "bet:0.25", "bet:0.5"),
                report.actions().stream().map(a -> a.action()).toList());
        assertEquals(
                List.of(2, 1, 1),
                report.actions().stream()
                        .map(a -> a.diagnostic().numeratorPlans().size())
                        .toList());
        int certificates = 0;
        for (var action : report.actions()) {
            var audit = action.diagnostic();
            assertEquals(report.parent().solve(), audit.baseline());
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM,
                    audit.algorithm());
            assertFalse(audit.trainerAdmission());
            certificates += audit.work().intervalLpSolves();
            assertEquals(3 + audit.numeratorPlans().size(), audit.work().intervalLpSolves());
            assertTrue(
                    audit.work().intervalLpArithmeticWork()
                            <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
            assertTrue(audit.work().intervalLpPivots() <= BoundedLinearProgram.MAX_PIVOTS);
        }
        assertEquals(13, certificates);
    }

    static List<FiniteTwoPlayerConditionalActionIntervals.Witness> witnesses(
            FiniteTwoPlayerConditionalActionIntervals.Audit audit) {
        var values = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Witness>();
        values.add(audit.minimumReachWitness());
        values.add(audit.maximumReachWitness());
        values.add(audit.lowerWitness());
        values.addAll(audit.upperWitnesses());
        return values;
    }

    @Test
    void physicalEventScalesOnlyMeasurementAndHeroCallCancels() {
        for (var action : result.report().actions()) {
            var audit = action.diagnostic();
            assertEquals(1, audit.fixedHeroPast().size());
            assertEquals("call", audit.fixedHeroPast().getFirst().action());
            assertTrue(
                    audit.fixedHeroPast()
                            .getFirst()
                            .key()
                            .startsWith("5:supplied-size-menu-preflop:"));
            assertEquals(.0001, audit.requiredMinimumReach());
            assertEquals(1e-8, audit.securitySlack());
            assertEquals(1.0 / 3, audit.certifiedReachLowerBound(), 2e-8);
            assertEquals(1.0 / 3, audit.certifiedReachUpperBound(), 2e-8);
            assertEquals(
                    audit.certifiedReachLowerBound() / 9880, action.rawCertifiedReachLowerBound());
            assertTrue(action.rawCertifiedReachLowerBound() < audit.requiredMinimumReach());
            for (var witness : witnesses(audit)) {
                assertEquals(1.0 / 3, witness.questionReach(), 1e-12);
                assertEquals(8, witness.posterior().size());
                assertEquals(
                        1,
                        witness.posterior().stream().mapToDouble(p -> p.probability()).sum(),
                        1e-12);
                for (var row : witness.posterior()) {
                    assertEquals(0, row.opponentSequence());
                    assertEquals(1.0 / 24, row.chanceWeight());
                    assertEquals(1.0 / 8, row.probability(), 1e-12);
                }
                assertTrue(witness.globalHeroBestResponse() <= audit.globalHeroUpperValue() + 2e-8);
                assertTrue(witness.posteriorTotalVariation() <= 1e-8);
            }
        }
    }

    @Test
    void paddedOriginalDualUtilitiesAndAllActionLossesAreConservative() {
        var actions = result.report().actions();
        double bestLo = actions.stream().mapToDouble(a -> a.lowerUtilityBb()).max().orElseThrow();
        double bestHi = actions.stream().mapToDouble(a -> a.upperUtilityBb()).max().orElseThrow();
        for (var action : actions) {
            var audit = action.diagnostic();
            double shift = action.upperObjectiveShift();
            assertEquals(
                    -shift - audit.lowerWitness().certificate().dualValue() - 1e-8,
                    action.lowerUtilityBb());
            assertEquals(
                    audit.upperWitnesses().stream()
                                    .mapToDouble(w -> w.certificate().dualValue() - shift)
                                    .max()
                                    .orElseThrow()
                            + 1e-8,
                    action.upperUtilityBb());
            assertTrue(action.lowerUtilityBb() <= action.upperUtilityBb());
            assertEquals(Math.max(0, bestLo - action.upperUtilityBb()), action.lowerLossBb());
            assertEquals(Math.max(0, bestHi - action.lowerUtilityBb()), action.upperLossBb());
        }
    }

    @Test
    void contextMatchesOwnedPublicViewInEveryCompatibleWorld() {
        var q = SixMaxSizeMenuConditionalStudy.question(game, request);
        assertEquals(q.key(), result.report().informationSet());
        assertEquals(q.context(), result.report().context());
        assertEquals(
                List.of(0.0, 1.625, 3.25),
                q.context().actions().stream().map(a -> a.amountBb()).toList());
        assertEquals(1609, result.report().parent().binding().budget().completeTreeStates());
        assertEquals(2, result.report().parent().references().size());
        assertThrows(UnsupportedOperationException.class, () -> result.report().actions().clear());
        assertThrows(UnsupportedOperationException.class, () -> q.context().actions().clear());
    }

    @Test
    void oldPreflopUnknownAndIllegalActionQuestionsRejectBeforeOptimization() {
        var question = SixMaxSizeMenuConditionalStudy.question(game, request);
        for (var q :
                List.of(
                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                5, "5:supplied-flop-one-bet:9d 9h:|BB:call:6d Qc Td:", "check"),
                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                5, "5:supplied-size-menu-preflop:anything", "call"),
                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                5, "5:supplied-size-menu-flop:unknown", "check"),
                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                5, question.key(), "bet")))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPhysicalFlop(
                                    game, q, 1e-8, .0001));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPhysicalFlop(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, question.key(), "check"),
                                1e-8,
                                1e-12));
    }

    @Test
    void requestScopeAndStrictReaderRejectForeignQuestionOrOldParent(@TempDir Path temp)
            throws Exception {
        var json = Files.readString(REQUEST);
        var invalid =
                List.of(
                        json.replace("\"hero\": 5", "\"hero\": 3"),
                        json.replace("9d 9h", "As Ad"),
                        json.replace("\"flopActions\": []", "\"flopActions\": [\"check\"]"),
                        json.replace("\"hero\": 5", "\"hero\":5,\"hero\":5"),
                        json.replace("\"heroCards\"", "\"unknownCards\""),
                        json + " {}",
                        json.replace("0.25", "1e999"),
                        json.replace(
                                "pokerlab-size-menu-conditional-request/v1",
                                "pokerlab-refined-flop-conditional-request/v1"));
        for (int i = 0; i < invalid.size(); i++) {
            var path = temp.resolve(i + ".json");
            Files.writeString(path, invalid.get(i));
            assertThrows(Exception.class, () -> SixMaxSizeMenuConditionalStudy.readRequest(path));
        }
    }

    @Test
    void exclusiveWriterAndCliCannotOverwriteInputsOrOutputs(@TempDir Path temp) throws Exception {
        var path = temp.resolve("report.json.gz");
        SixMaxSizeMenuConditionalStudy.write(path, result);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSizeMenuConditionalStudy.write(path, result));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuConditionalMain.main(
                                new String[] {"solve", REQUEST.toString(), path.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuConditionalMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
    }

    @Test
    void serializedEvidenceCannotCreateResultAndRejectsAdmissionOrMixedParent() throws Exception {
        assertTrue(
                Arrays.stream(SixMaxSizeMenuConditionalStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        var json = SixMaxTextureStudy.json(result.report());
        for (var bad :
                List.of(
                        json.replace("\"trainerAdmission\" : false", "\"trainerAdmission\" : true"),
                        json.replace(
                                "SIZE_MENU_CONDITIONAL_INTERVAL_RESEARCH_ONLY",
                                "REFINED_FLOP_CONDITIONAL_INTERVAL_RESEARCH_ONLY"),
                        json.replace("\"securitySlack\" : 1.0E-8", "\"securitySlack\" : 1.0E-5")))
            assertThrows(
                    Exception.class,
                    () ->
                            SixMaxTexturePayoffTable.mapper()
                                    .readValue(bad, SixMaxSizeMenuConditionalStudy.Report.class));
    }
}
