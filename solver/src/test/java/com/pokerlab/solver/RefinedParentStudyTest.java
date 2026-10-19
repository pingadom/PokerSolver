package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RefinedParentStudyTest {
    static final Path INPUT =
            Path.of("../docs/data/supplied-paired-flop-refined-parent-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-paired-flop-refined-parent-report.json.gz");
    static SixMaxRefinedParentStudy.Result owned;

    @BeforeAll
    static void solve() throws Exception {
        owned = SixMaxRefinedParentStudy.solve(SixMaxRefinedParentStudy.readRequest(INPUT));
    }

    @Test
    void completeOwnedPairedParentHasOriginalCertificatesAndIndependentBehavioralQuality()
            throws Exception {
        var r = owned.report();
        var a = r.solve();
        assertEquals(List.of("8d", "Kc", "Kd"), r.binding().flop());
        assertEquals(1321, a.treeNodes());
        assertEquals(24, r.originalJointPrior().size());
        assertEquals(4, r.handoffs().size());
        assertEquals(96, r.residuals().size());
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.ASCENDING_REFINED_DIRECTIONS_ALGORITHM,
                a.algorithm());
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                a.firstLp().algorithm());
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                a.secondLp().algorithm());
        assertTrue(a.behavioralQuality().nashConvBb() < 1e-8);
        assertEquals(a.lowerValue(), a.upperValue(), 1e-8);
        assertFalse(r.trainerAdmission());
        assertEquals(1, r.candidate().iterations());
        assertEquals(r, SixMaxRefinedParentStudy.readReport(REPORT));
        assertTrue(
                Arrays.stream(SixMaxRefinedParentStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void unchangedProductionParentStillRejectsThePairedBoard() throws Exception {
        var request = SixMaxRefinedParentStudy.readRequest(INPUT);
        var rejected =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () -> SixMaxSuppliedFlopBettingStudy.solve(request.connected()));
        assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, rejected.reason());
        assertEquals("Singular LP basis", rejected.getMessage());
    }

    @Test
    void fullPhysicalReplayAndPlainGzipPersistenceAreExclusiveAndExact(@TempDir Path temp)
            throws Exception {
        var replay = SixMaxRefinedParentStudy.replay(INPUT, REPORT);
        assertEquals(owned.report(), replay.report());
        for (String name : List.of("parent.json", "parent.json.gz")) {
            var file = temp.resolve(name);
            SixMaxRefinedParentStudy.write(file, owned);
            assertEquals(owned.report(), SixMaxRefinedParentStudy.readReport(file));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxRefinedParentStudy.write(file, owned));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedParentStudy.replay(INPUT, INPUT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedParentStudyMain.main(
                                new String[] {"solve", INPUT.toString(), INPUT.toString()}));
    }

    @Test
    void replayRejectsEditedCertificateEvenWithAnUnchangedRequest(@TempDir Path temp)
            throws Exception {
        var value = (ObjectNode) SixMaxTexturePayoffTable.mapper().valueToTree(owned.report());
        ((ObjectNode) value.path("solve").path("firstLp"))
                .put("primalValue", owned.report().solve().firstLp().primalValue() + .001);
        var changed = temp.resolve("changed.json");
        Files.writeString(changed, value.toString());
        assertThrows(
                com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> SixMaxRefinedParentStudy.readReport(changed));
        ((ObjectNode) value.path("firstLpWitness").path("certificate"))
                .put("primalValue", owned.report().solve().firstLp().primalValue() + .001);
        Files.writeString(changed, value.toString());
        var rejected =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SixMaxRefinedParentStudy.replay(INPUT, changed));
        assertEquals("Complete refined parent replay differs", rejected.getMessage());
    }

    @Test
    void strictRequestIdentityAndBoundedInputRejectBeforeSolving(@TempDir Path temp)
            throws Exception {
        String original = Files.readString(INPUT);
        for (String changed :
                List.of(
                        original.replace(SixMaxRefinedParentStudy.REQUEST_SCHEMA, "old-schema"),
                        original.replace(
                                FiniteTwoPlayerAffineSequenceForm
                                        .ASCENDING_REFINED_DIRECTIONS_ALGORITHM,
                                FiniteTwoPlayerAffineSequenceForm.ASCENDING_ROWS_ALGORITHM),
                        original.replace("\"connected\"", "\"unexpected\""),
                        original + "{}",
                        original.replace(
                                "\"algorithm\":", "\"algorithm\":\"duplicate\",\"algorithm\":"))) {
            var file = temp.resolve("bad.json");
            Files.writeString(file, changed);
            assertThrows(Exception.class, () -> SixMaxRefinedParentStudy.readRequest(file));
        }
        var large = temp.resolve("large.json");
        Files.write(large, new byte[SixMaxRefinedParentStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRefinedParentStudy.readRequest(large));
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.Failure.WORK_LIMIT,
                assertThrows(
                                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                                () ->
                                        FiniteTwoPlayerAffineSequenceForm
                                                .solveAscendingRefinedDirections(new TinyGame(), 0))
                        .reason());
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.Failure.INVALID_INPUT,
                assertThrows(
                                FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                                () ->
                                        FiniteTwoPlayerAffineSequenceForm
                                                .solveAscendingRefinedDirections(
                                                        new TinyGame(),
                                                        FiniteTwoPlayerAffineSequenceForm
                                                                        .MAX_REDUCTION_WORK
                                                                + 1))
                        .reason());
    }

    @Test
    void newAffineModeAlsoCertifiesAnIndependentMatchingPenniesGame() throws Exception {
        var game = new TinyGame();
        var solved = FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(game);
        assertEquals(0, solved.audit().lowerValue(), 1e-12);
        assertEquals(
                0,
                MultiPlayerInformationSetBestResponse.assess(
                                game, new CfrSolution(1, solved.strategy()))
                        .nashConvBb(),
                1e-12);
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.ALGORITHM,
                FiniteTwoPlayerAffineSequenceForm.solve(game).audit().algorithm());
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.ASCENDING_ROWS_ALGORITHM,
                FiniteTwoPlayerAffineSequenceForm.solveAscendingL1Rows(game).audit().algorithm());
    }

    static class TinyGame implements MultiPlayerCfrGame<List<String>> {
        public int playerCount() {
            return 6;
        }

        public List<String> initialState() {
            return List.of();
        }

        public boolean isTerminal(List<String> s) {
            return s.size() == 2;
        }

        public double[] terminalUtilities(List<String> s) {
            double v = s.get(0).equals(s.get(1)) ? 1 : -1;
            return new double[] {0, 0, 0, v, 0, -v};
        }

        public int currentPlayer(List<String> s) {
            return s.isEmpty() ? 3 : 5;
        }

        public List<String> legalActions(List<String> s) {
            return List.of("heads", "tails");
        }

        public String informationSet(List<String> s) {
            return s.isEmpty() ? "row" : "hidden-column";
        }

        public List<String> afterAction(List<String> s, String a) {
            var n = new ArrayList<>(s);
            n.add(a);
            return List.copyOf(n);
        }

        public List<ChanceOutcome<List<String>>> chanceOutcomes(List<String> s) {
            throw new IllegalArgumentException("No chance");
        }
    }
}
