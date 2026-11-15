package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.FiniteTwoPlayerRootSecurityEnvelope.Question;
import com.pokerlab.solver.FiniteTwoPlayerSequenceForm.Snapshot;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RootSecurityBatchTest {
    record Control(
            String name,
            Snapshot game,
            List<Question> questions,
            FiniteTwoPlayerRootSecurityBatch.Audit audit) {
        Control {
            questions = List.copyOf(questions);
        }
    }

    static List<Control> controls;

    static List<Question> questions(int hero) {
        return List.of(new Question(hero, hero + ":type0"), new Question(hero, hero + ":type1"));
    }

    @BeforeAll
    static void replayOwnedBatchesOnce() throws Exception {
        var captured = new ArrayList<Control>();
        for (int hero = 0; hero < 2; hero++) {
            var game = RootSecurityEnvelopeTest.model(hero, hero == 0 ? 0 : 7, .25);
            var qs = questions(hero);
            captured.add(
                    new Control(
                            "oriented-" + hero,
                            game,
                            qs,
                            FiniteTwoPlayerRootSecurityBatch.solve(game, qs).audit()));
        }
        var game = RootSequenceBatchTest.mixedRoots();
        var qs = new ArrayList<Question>();
        for (int i = 0; i < 4; i++) {
            int h = i % 2 == 0 ? 3 : 5;
            qs.add(new Question(h, h + ":root-" + i));
        }
        captured.add(
                new Control(
                        "mixed-six-seats",
                        game,
                        qs,
                        FiniteTwoPlayerRootSecurityBatch.solve(game, qs).audit()));
        controls = List.copyOf(captured);
        try (var raw =
                RootSecurityBatchTest.class.getResourceAsStream(
                        "/root-security-batch-analytic-controls.json.gz")) {
            assertNotNull(raw);
            byte[] stored = raw.readAllBytes();
            assertTrue(stored.length < 8 * 1024 * 1024);
            assertEquals(
                    "1e5a3f11df5f765b4e12318e790f91cd99b356abe0da73add0a47c9cb7849c15",
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stored)));
            try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
                assertEquals(
                        controls,
                        List.of(SixMaxTexturePayoffTable.mapper().readValue(in, Control[].class)));
            }
        }
    }

    static void sameEnvelope(
            FiniteTwoPlayerRootSecurityEnvelope.Audit expected,
            FiniteTwoPlayerRootSecurityEnvelope.Audit actual) {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var left = mapper.valueToTree(expected);
        var right = mapper.valueToTree(actual);
        // Standalone and shared calls intentionally record different remaining compiler caps.
        ((com.fasterxml.jackson.databind.node.ObjectNode) left.get("work")).remove("compilerLimit");
        ((com.fasterxml.jackson.databind.node.ObjectNode) right.get("work"))
                .remove("compilerLimit");
        assertEquals(left, right);
    }

    @Test
    void allMenusAndOriginalWitnessesMatchStandaloneSolvesExactly() throws Exception {
        for (var c : controls)
            for (int i = 0; i < c.questions().size(); i++)
                sameEnvelope(
                        FiniteTwoPlayerRootSecurityEnvelope.solve(c.game(), c.questions().get(i))
                                .audit(),
                        c.audit().envelopes().get(i));
    }

    @Test
    void oneOwnedParentAndCumulativeCountersCoverEveryQuestion() {
        for (var c : controls) {
            var a = c.audit();
            var w = a.work();
            assertFalse(a.trainerAdmission());
            assertEquals(1, w.parentSolveCalls());
            assertEquals(2, w.parentLpSolves());
            assertEquals(a.parent().snapshotHash(), a.snapshotHash());
            assertEquals(a.parent().firstLp(), a.firstParentWitness().certificate());
            assertEquals(a.parent().secondLp(), a.secondParentWitness().certificate());
            assertTrue(a.envelopes().stream().allMatch(e -> e.baseline() == a.parent()));
            assertEquals(
                    a.envelopes().stream().mapToLong(e -> e.work().compilerUnits()).sum()
                            + w.preflightCompilerUnits(),
                    w.compilerUnits());
            assertEquals(
                    a.envelopes().stream().mapToInt(e -> e.work().lpSolves()).sum(),
                    w.endpointLpSolves());
            assertEquals(
                    a.envelopes().stream().mapToInt(e -> e.work().lpPivots()).sum(),
                    w.endpointPivots());
            assertEquals(
                    a.envelopes().stream().mapToLong(e -> e.work().lpArithmeticWork()).sum(),
                    w.endpointArithmeticWork());
            assertEquals(8_000_000, w.compilerLimit());
            assertEquals(10_000, w.endpointPivotLimit());
            assertEquals(4_000_000_000L, w.endpointArithmeticLimit());
            assertEquals(0, w.enumeratedContinuationPlans());
        }
    }

    @Test
    void bothActorsAndNonzeroConstantSumsKeepOriginalBounds() {
        assertEquals(7, controls.get(1).audit().parent().constantSum());
        var a = controls.get(2).audit();
        assertEquals(
                List.of(3, 5, 3, 5), a.envelopes().stream().map(e -> e.question().hero()).toList());
        for (var e : a.envelopes()) {
            assertEquals(10, e.actions().getFirst().lowerUtility(), 3e-8);
            assertTrue(e.actions().getFirst().upperUtility() >= 10);
            assertTrue(e.actions().get(1).lowerUtility() <= 1);
            assertTrue(e.actions().get(1).upperUtility() >= 1);
        }
    }

    @Test
    void requestAndResultCollectionsCannotBeMutated() throws Exception {
        var c = controls.getFirst();
        var input = new ArrayList<>(c.questions());
        var a = FiniteTwoPlayerRootSecurityBatch.solve(c.game(), input).audit();
        input.clear();
        assertEquals(2, a.envelopes().size());
        assertThrows(UnsupportedOperationException.class, () -> a.envelopes().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> a.envelopes().getFirst().fixedHeroChoices().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> a.firstParentWitness().point().clear());
    }

    @Test
    void emptyDuplicateNullAndOversizedInventoriesReject() {
        var c = controls.getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.solve(c.game(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(), Collections.nCopies(9, c.questions().getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(),
                                List.of(c.questions().getFirst(), c.questions().getFirst())));
        assertThrows(
                NullPointerException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.solve(c.game(), null));
        assertThrows(
                NullPointerException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(), Arrays.asList(c.questions().getFirst(), null)));
    }

    @Test
    void laterMissingAndInactiveActorQuestionsRejectDuringPreflight() {
        var c = controls.get(2);
        for (var q :
                List.of(
                        new Question(3, "3:private-0-0"),
                        new Question(3, "3:missing"),
                        new Question(0, "0:missing")))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSecurityBatch.solve(
                                    c.game(), List.of(c.questions().getFirst(), q)));
    }

    @Test
    void insufficientOriginalReachRejectsRatherThanRenormalising() {
        var game = RootSecurityEnvelopeTest.model(0, 0, .00001);
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.solve(game, questions(0)));
    }

    @Test
    void compilerLimitCannotResetAtQuestionBoundary() {
        var c = controls.getFirst();
        var w = c.audit().work();
        assertTrue(
                w.compilerUnits()
                        > w.preflightCompilerUnits()
                                + c.audit().envelopes().getFirst().work().compilerUnits());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(),
                                c.questions(),
                                w.compilerUnits() - 1,
                                10000,
                                4_000_000_000L));
    }

    @Test
    void pivotsAndArithmeticCannotResetAtQuestionBoundary() {
        var c = controls.getFirst();
        var w = c.audit().work();
        assertTrue(w.endpointPivots() > c.audit().envelopes().getFirst().work().lpPivots());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(),
                                c.questions(),
                                8_000_000,
                                w.endpointPivots() - 1,
                                4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(),
                                c.questions(),
                                8_000_000,
                                10000,
                                w.endpointArithmeticWork() - 1));
    }

    @Test
    void exactCumulativeLimitsStillCompleteWithoutPartialResults() throws Exception {
        var c = controls.getFirst();
        var w = c.audit().work();
        var a =
                FiniteTwoPlayerRootSecurityBatch.solve(
                                c.game(),
                                c.questions(),
                                w.compilerUnits(),
                                w.endpointPivots(),
                                w.endpointArithmeticWork())
                        .audit();
        for (int i = 0; i < a.envelopes().size(); i++)
            sameEnvelope(c.audit().envelopes().get(i), a.envelopes().get(i));
    }

    @Test
    void increasedOrNegativeOriginalLimitsAreRejected() {
        var c = controls.getFirst();
        for (long n : new long[] {-1, 8_000_001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSecurityBatch.solve(
                                    c.game(), c.questions(), n, 10000, 4_000_000_000L));
        for (int n : new int[] {-1, 10001})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSecurityBatch.solve(
                                    c.game(), c.questions(), 8_000_000, n, 4_000_000_000L));
        for (long n : new long[] {-1, 4_000_000_001L})
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSecurityBatch.solve(
                                    c.game(), c.questions(), 8_000_000, 10000, n));
    }

    @Test
    void reorderedQuestionsRetainTheirIdentityAndBounds() throws Exception {
        var c = controls.getFirst();
        var qs = new ArrayList<>(c.questions());
        Collections.reverse(qs);
        var a = FiniteTwoPlayerRootSecurityBatch.solve(c.game(), qs).audit();
        assertEquals(qs, a.envelopes().stream().map(e -> e.question()).toList());
        assertEquals(c.audit().envelopes().getLast().actions(), a.envelopes().getFirst().actions());
    }

    @Test
    void unrelatedNullAndOlderAlgorithmParentsCannotBeReused() throws Exception {
        var c = controls.getFirst();
        var q = c.questions().getFirst();
        var unrelated =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        controls.get(1).game());
        var older = FiniteTwoPlayerAffineSequenceForm.solve(c.game());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solveCapturedParent(
                                c.game(), q, unrelated, 8_000_000, 10000, 4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solveCapturedParent(
                                c.game(), q, older, 8_000_000, 10000, 4_000_000_000L));
        assertThrows(
                NullPointerException.class,
                () ->
                        FiniteTwoPlayerRootSecurityEnvelope.solveCapturedParent(
                                c.game(), q, null, 8_000_000, 10000, 4_000_000_000L));
    }

    @Test
    void auditDeserializationCannotConstructOwnedHandles() {
        for (Class<?> type :
                List.of(
                        FiniteTwoPlayerRootSecurityBatch.Result.class,
                        FiniteTwoPlayerRootSecurityEnvelope.Result.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void maximumEightQuestionInventoryKeepsOneParent() throws Exception {
        var roots = new ArrayList<FiniteTwoPlayerSequenceForm.Node>();
        var qs = new ArrayList<Question>();
        for (int i = 0; i < 8; i++) {
            var safe =
                    FiniteTwoPlayerRootActionIntervalsTest.decision(
                            3,
                            "reply-" + i,
                            List.of("x", "y"),
                            FiniteTwoPlayerRootActionIntervalsTest.terminal(5, 1),
                            FiniteTwoPlayerRootActionIntervalsTest.terminal(5, 1));
            var risk =
                    FiniteTwoPlayerRootActionIntervalsTest.decision(
                            3,
                            "reply-" + i,
                            List.of("x", "y"),
                            FiniteTwoPlayerRootActionIntervalsTest.terminal(5, 0),
                            FiniteTwoPlayerRootActionIntervalsTest.terminal(5, 0));
            roots.add(
                    FiniteTwoPlayerRootActionIntervalsTest.decision(
                            5, "root-" + i, List.of("safe", "risk"), safe, risk));
            qs.add(new Question(5, "5:root-" + i));
        }
        var tree =
                new FiniteTwoPlayerSequenceForm.Node(
                        -1, "", List.of(), Collections.nCopies(8, .125), roots, List.of());
        var a = FiniteTwoPlayerRootSecurityBatch.solve(new Snapshot(6, tree), qs).audit();
        assertEquals(8, a.envelopes().size());
        assertEquals(2, a.work().parentLpSolves());
        assertEquals(32, a.work().endpointLpSolves());
    }

    @Test
    void callerGameIsCapturedOnceBeforeParentAndQuestionReplays() throws Exception {
        var c = controls.getFirst();
        int[] calls = {0};
        MultiPlayerCfrGame<FiniteTwoPlayerSequenceForm.Node> game =
                new MultiPlayerCfrGame<>() {
                    public int playerCount() {
                        return c.game().playerCount();
                    }

                    public FiniteTwoPlayerSequenceForm.Node initialState() {
                        assertEquals(0, calls[0]++);
                        return c.game().initialState();
                    }

                    public boolean isTerminal(FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().isTerminal(n);
                    }

                    public double[] terminalUtilities(FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().terminalUtilities(n);
                    }

                    public int currentPlayer(FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().currentPlayer(n);
                    }

                    public List<String> legalActions(FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().legalActions(n);
                    }

                    public String informationSet(FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().informationSet(n);
                    }

                    public FiniteTwoPlayerSequenceForm.Node afterAction(
                            FiniteTwoPlayerSequenceForm.Node n, String a) {
                        return c.game().afterAction(n, a);
                    }

                    public List<ChanceOutcome<FiniteTwoPlayerSequenceForm.Node>> chanceOutcomes(
                            FiniteTwoPlayerSequenceForm.Node n) {
                        return c.game().chanceOutcomes(n);
                    }
                };
        assertEquals(
                c.audit(), FiniteTwoPlayerRootSecurityBatch.solve(game, c.questions()).audit());
        assertEquals(1, calls[0]);
    }
}
