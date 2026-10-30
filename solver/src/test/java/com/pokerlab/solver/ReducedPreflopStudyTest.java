package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ReducedPreflopStudyTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-reduced-preflop-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-reduced-preflop-report.json.gz");
    static SixMaxReducedPreflopStudy.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;
    static SixMaxReducedPreflopStudy.Result owned;

    @BeforeAll
    static void oneCompleteDeclaredPhysicalReplay() throws Exception {
        request = SixMaxReducedPreflopStudy.readRequest(REQUEST);
        var p = request.parent();
        game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        owned = SixMaxReducedPreflopStudy.solve(request, game);
    }

    @Test
    void completeOriginalMenuAndPhysicalParentReproduceExactly() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(REPORT, 8 * 1024 * 1024),
                        SixMaxReducedPreflopStudy.Report.class);
        assertEquals(saved, owned.report());
        var original =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/supplied-flop-size-menu-report.json.gz"),
                                8 * 1024 * 1024),
                        SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(original, owned.report().parent());
        assertEquals(1609, original.solve().treeNodes());
        assertEquals(
                List.of("fold", "call", "raise:9.0"),
                owned.report().endpoints().stream().map(e -> e.action()).toList());
        assertEquals(
                List.of(80000, 160000),
                original.references().stream().map(r -> r.policy().iterations()).toList());
    }

    @Test
    void arithmeticFailureIsRetainedAndCannotBecomeACompleteMenu() {
        assertFalse(owned.report().complete());
        assertFalse(owned.report().trainerAdmission());
        var raise = owned.report().endpoints().getLast();
        assertNull(raise.utility());
        assertEquals("LP", raise.rejection().kind());
        assertEquals("WORK_LIMIT", raise.rejection().reason());
        assertEquals("LP arithmetic work budget exceeded", raise.rejection().detail());
        assertNull(raise.rejection().reachFailure());
        assertNull(raise.rejection().reducedPlans());
        var call = owned.report().endpoints().get(1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxReducedPreflopStudy.Endpoint("raise:9.0", call.utility(), null));
    }

    @Test
    void successfulCertificatesUseOriginalJointReachAndOneSharedHeroPosterior() {
        for (var e : owned.report().endpoints().subList(0, 2)) {
            var u = e.utility();
            assertNotNull(u);
            var a = u.diagnostic();
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.PREFLOP_ROOT_REDUCED_ALGORITHM,
                    a.algorithm());
            assertEquals(owned.report().parent().solve(), a.baseline());
            assertTrue(a.fixedHeroPast().isEmpty());
            assertEquals(1. / 3, u.rawCertifiedReachLowerBound(), 2e-8);
            assertEquals(1. / 3, u.rawCertifiedReachUpperBound(), 2e-8);
            assertEquals(8, a.lowerWitness().posterior().size());
            for (var p : a.lowerWitness().posterior()) {
                assertEquals(0, p.opponentSequence());
                assertEquals(1. / 24, p.chanceWeight());
                assertEquals(1. / 8, p.probability(), 1e-15);
            }
            assertEquals(.0001, a.requiredMinimumReach());
            assertEquals(1e-8, a.securitySlack());
        }
        var fold = owned.report().endpoints().getFirst().utility();
        assertEquals(-1, fold.lowerBb(), 2e-8);
        var call = owned.report().endpoints().get(1).utility();
        assertTrue(call.lowerBb() > .78);
        assertTrue(call.upperBb() - call.lowerBb() < 1e-6);
    }

    @Test
    void actualOwnedRaiseForestHas52PlansWhileTheOriginalModeStillRejects1296() throws Exception {
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        String key = owned.report().informationSet();
        var roots =
                checked.snapshot().initialState().children().stream()
                        .filter(n -> n.key().equals(key))
                        .map(n -> n.children().get(n.actions().indexOf("raise:9.0")))
                        .toList();
        assertEquals(8, roots.size());
        var compiled =
                ReducedHeroContinuations.compile(
                        roots, 5, checked.treeNodes(), new SequenceFormAffineProjection.Budget());
        assertEquals(BigInteger.valueOf(1296), compiled.cartesianPlans());
        assertEquals(BigInteger.valueOf(52), compiled.reducedPlans());
        assertEquals(52, compiled.choices().size());
        var q = new FiniteTwoPlayerConditionalActionIntervals.Question(5, key, "raise:9.0");
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solve(
                                        game, q, 1e-8, .0001));
        assertEquals("Conditional continuation plan budget exceeded", failure.getMessage());
        assertEquals(64, FiniteTwoPlayerRootActionIntervals.MAX_CONTINUATION_PLANS);
    }

    @Test
    void reducedCallPlansOmitOnlyResponsesMadeUnreachableByTheHerosOwnBet() {
        var e = owned.report().endpoints().get(1);
        var a = e.utility().diagnostic();
        assertEquals(4, a.numeratorPlans().size());
        assertEquals(7, a.work().intervalLpSolves());
        assertEquals(
                1,
                owned.report()
                        .endpoints()
                        .getFirst()
                        .utility()
                        .diagnostic()
                        .numeratorPlans()
                        .size());
        for (var plan : a.numeratorPlans()) {
            var first =
                    plan.continuation().entrySet().stream()
                            .filter(r -> r.getKey().endsWith(":6d Qc Td:"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(first.getValue().equals("check") ? 2 : 1, plan.continuation().size());
        }
    }

    @Test
    void rootAndFlopModesCannotBorrowEachOthersQuestionsBeforeNumericalWork() {
        var root =
                new FiniteTwoPlayerConditionalActionIntervals.Question(
                        5, owned.report().informationSet(), "fold");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPhysicalFlop(
                                game, root, 1e-8, .0001));
        var flop =
                new FiniteTwoPlayerConditionalActionIntervals.Question(
                        5, "5:supplied-size-menu-flop:9d 9h:|BB:call:6d Qc Td:", "check");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPreflopRootReduced(
                                game, flop, 1e-8, .0001));
        var table = owned.report().context();
        assertEquals(4.5, table.potBb());
        assertEquals(2., table.toCallBb());
        assertEquals(List.of(0., 2., 8.), table.actions().stream().map(a -> a.amountBb()).toList());
        assertEquals(6, table.players().size());
    }

    @Test
    void strictReadersRejectForgedAdmissionCompletenessAndBorrowedAlgorithm() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("trainerAdmission", "complete")) {
            var n = (ObjectNode) mapper.valueToTree(owned.report());
            n.put(field, true);
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxReducedPreflopStudy.Report.class));
        }
        var n = (ObjectNode) mapper.valueToTree(owned.report());
        ((ObjectNode) n.withArray("endpoints").get(0).get("utility").get("diagnostic"))
                .put(
                        "algorithm",
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(n, SixMaxReducedPreflopStudy.Report.class));
        String source = Files.readString(REQUEST);
        for (String text :
                List.of(
                        source + " {}",
                        source.replaceFirst("\\{", "{\"extra\":0,"),
                        source.replaceFirst("\\{", "{\"schemaVersion\":\"wrong\",")))
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(text, SixMaxReducedPreflopStudy.Request.class));
    }

    @Test
    void resultOwnershipAndReportsStayImmutable() {
        assertTrue(
                Arrays.stream(SixMaxReducedPreflopStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> owned.report().endpoints().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().cartesianPlanCounts().clear());
    }

    @Test
    void commandAliasesAndExistingOutputsRejectBeforePhysicalWork(@TempDir Path temp)
            throws Exception {
        Path keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReducedPreflopMain.main(
                                new String[] {"solve", REQUEST.toString(), keep.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxReducedPreflopMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxReducedPreflopMain.main(new String[] {"plan"}));
        assertEquals("keep", Files.readString(keep));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) mapper.valueToTree(owned.report());
        n.put("trainerAdmission", true);
        Path bad = temp.resolve("bad.json");
        Files.write(bad, mapper.writeValueAsBytes(n));
        assertThrows(Exception.class, () -> SixMaxReducedPreflopStudy.replay(REQUEST, bad));
    }
}
