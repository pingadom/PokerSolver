package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervals.*;
import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RootSequenceBatchTest {
    private static final String RESOURCE = "/root-sequence-batch-analytic-controls.json.gz";
    private static final String SHA =
            "d91cefea461e1c235784639fc20bf741e956124f6a5fa49d7f83d37aae07c802";

    record Control(
            String name,
            Snapshot game,
            List<Double> expected,
            FiniteTwoPlayerRootSequenceBatch.Audit audit) {
        Control {
            expected = List.copyOf(expected);
        }
    }

    private static List<Control> controls;

    static Snapshot mixedRoots() {
        var roots = new ArrayList<Node>();
        for (int r = 0; r < 4; r++) {
            int hero = r % 2 == 0 ? 3 : 5, other = hero == 3 ? 5 : 3;
            var types = new ArrayList<Node>();
            for (int t = 0; t < 4; t++) {
                double sign = t % 2 == 0 ? 1 : -1;
                var left =
                        decision(
                                other,
                                "reply-" + r,
                                List.of("x", "y"),
                                terminal(hero, sign),
                                terminal(hero, -sign));
                var right =
                        decision(
                                other,
                                "reply-" + r,
                                List.of("x", "y"),
                                terminal(hero, -sign),
                                terminal(hero, sign));
                types.add(
                        decision(
                                hero,
                                "private-" + r + "-" + t,
                                List.of("left", "right"),
                                left,
                                right));
            }
            roots.add(
                    decision(
                            hero,
                            "root-" + r,
                            List.of("safe", "risk"),
                            terminal(hero, 10),
                            new Node(
                                    -1,
                                    "",
                                    List.of(),
                                    List.of(.25, .25, .25, .25),
                                    types,
                                    List.of())));
        }
        return new Snapshot(
                6, new Node(-1, "", List.of(), List.of(.125, .25, .125, .5), roots, List.of()));
    }

    static List<Question> mixedQuestions() {
        var qs = new ArrayList<Question>();
        for (int r = 0; r < 4; r++) {
            int h = r % 2 == 0 ? 3 : 5;
            for (String action : List.of("risk", "safe"))
                qs.add(new Question(h, h + ":root-" + r, action));
        }
        return List.copyOf(qs);
    }

    static List<Control> capture() throws Exception {
        var mixed = mixedRoots();
        var large = RootSequenceMinimaxTest.privateTypes(5, 32);
        var largeQuestions =
                List.of(new Question(5, "5:root", "risk"), new Question(5, "5:root", "safe"));
        return List.of(
                new Control(
                        "mixed-four-roots",
                        mixed,
                        List.of(0., 10., 0., 10., 0., 10., 0., 10.),
                        FiniteTwoPlayerRootSequenceBatch.solve(mixed, mixedQuestions()).audit()),
                new Control(
                        "private32",
                        large,
                        List.of(0., 10.),
                        FiniteTwoPlayerRootSequenceBatch.solve(large, largeQuestions).audit()));
    }

    @BeforeAll
    static void replayOwnedBatchesOnce() throws Exception {
        List<Control> frozen;
        try (var in = RootSequenceBatchTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in);
            byte[] stored = in.readAllBytes();
            assertTrue(stored.length < 8 * 1024 * 1024);
            assertEquals(
                    SHA,
                    HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(stored)));
            try (var gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(stored))) {
                frozen =
                        List.of(SixMaxTexturePayoffTable.mapper().readValue(gzip, Control[].class));
            }
        }
        controls = capture();
        assertEquals(frozen, controls, "Complete original owned batch audits must reproduce");
    }

    @Test
    void completeFrozenBatchesReproduceWithoutPartialPublication() {
        assertEquals(
                List.of("mixed-four-roots", "private32"),
                controls.stream().map(Control::name).toList());
        for (var c : controls)
            for (int i = 0; i < c.expected().size(); i++) {
                var a = c.audit().minima().get(i);
                assertTrue(a.minimum().lowerBound() <= c.expected().get(i) + 1e-8);
                assertTrue(a.minimum().upperBound() >= c.expected().get(i) - 1e-8);
                assertEquals(c.expected().get(i), a.minimum().witnessUtility(), 2e-7);
            }
    }

    @Test
    void everyEndpointEqualsTheStandaloneKernelWithIdenticalRemainingLimits() throws Exception {
        for (var c : controls) {
            int pivots = 0;
            long arithmetic = 0;
            for (var a : c.audit().minima()) {
                var serial =
                        FiniteTwoPlayerRootSequenceMinimax.solve(
                                        c.game(),
                                        a.question(),
                                        DEFAULT_SECURITY_SLACK,
                                        FiniteTwoPlayerRootSequenceMinimax.MINIMUM_REACH,
                                        a.work().compilerLimit(),
                                        BoundedLinearProgram.MAX_PIVOTS - pivots,
                                        BoundedLinearProgram.MAX_ARITHMETIC_WORK - arithmetic)
                                .audit();
                assertEquals(serial, a);
                pivots += a.work().endpointPivots();
                arithmetic += a.work().endpointArithmeticWork();
            }
        }
    }

    @Test
    void accountsForOneParentAndEveryCumulativeCompilerPivotAndArithmeticCharge() {
        for (var c : controls) {
            var b = c.audit();
            var w = b.work();
            assertEquals(FiniteTwoPlayerRootSequenceBatch.ALGORITHM, b.algorithm());
            assertEquals(FiniteTwoPlayerRootSequenceBatch.SCOPE, b.scope());
            assertFalse(b.trainerAdmission());
            assertEquals(1, w.parentSolveCalls());
            assertEquals(2, w.parentLpSolves());
            assertEquals(b.minima().size(), w.endpointLpSolves());
            assertEquals(0, w.enumeratedContinuationPlans());
            long used = w.preflightCompilerUnits(), arithmetic = 0;
            int pivots = 0;
            assertEquals(
                    (long) b.minima().getFirst().baseline().treeNodes() * b.minima().size(), used);
            for (var a : b.minima()) {
                assertEquals(b.snapshotHash(), a.baseline().snapshotHash());
                assertEquals(b.minima().getFirst().baseline(), a.baseline());
                assertEquals(w.compilerLimit() - used, a.work().compilerLimit());
                used += a.work().compilerUnits();
                arithmetic += a.work().endpointArithmeticWork();
                pivots += a.work().endpointPivots();
            }
            assertEquals(used, w.compilerUnits());
            assertEquals(arithmetic, w.endpointArithmeticWork());
            assertEquals(pivots, w.endpointPivots());
            assertTrue(used <= 8_000_000);
            assertTrue(arithmetic <= 4_000_000_000L);
            assertTrue(pivots <= 10_000);
            var p = b.minima().getFirst().baseline();
            assertEquals(p.reductionWork().chargedUnits(), w.parentCompilerUnits());
            assertEquals(
                    p.firstLp().work().arithmeticWork() + p.secondLp().work().arithmeticWork(),
                    w.parentLpArithmeticWork());
        }
    }

    @Test
    void preservesBothActorOrientationsChanceReachAndOriginalPrivateInformation() {
        var b = controls.getFirst().audit();
        assertEquals(
                List.of(3, 5),
                b.minima().stream().map(a -> a.question().hero()).distinct().sorted().toList());
        for (int r = 0; r < 4; r++)
            for (int a = 0; a < 2; a++)
                assertEquals(
                        List.of(.125, .25, .125, .5).get(r),
                        b.minima().get(2 * r + a).originalQuestionReach());
        var large = controls.get(1).audit().minima().getFirst();
        assertEquals(67, large.continuationFlow().sequences().size());
        assertEquals(0, large.work().enumeratedContinuationPlans());
        assertTrue(
                large.minimum().upperBound()
                        < 1); // A bracket on the minimum is not the value at every opponent
        // mixture.
    }

    @Test
    @SuppressWarnings("unchecked")
    void capturesTheOriginalGameOnceAndFreezesTheQuestionList() throws Exception {
        var original = mixedRoots();
        int[] calls = {0};
        var once =
                (MultiPlayerCfrGame<Node>)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {MultiPlayerCfrGame.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("initialState"))
                                        assertEquals(1, ++calls[0]);
                                    return method.invoke(original, args);
                                });
        var input = new ArrayList<>(mixedQuestions().subList(0, 2));
        var result = FiniteTwoPlayerRootSequenceBatch.solve(once, input);
        input.clear();
        assertEquals(1, calls[0]);
        assertEquals(
                mixedQuestions().subList(0, 2),
                result.audit().minima().stream().map(a -> a.question()).toList());
    }

    @Test
    void exposesOnlyImmutableAuditEvidenceAndPrivateOwnedResultConstruction() throws Exception {
        var result = FiniteTwoPlayerRootSequenceBatch.Result.class;
        assertTrue(
                Arrays.stream(result.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        var a = controls.getFirst().audit();
        assertThrows(UnsupportedOperationException.class, () -> a.minima().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> a.minima().getFirst().problem().matrix().getFirst().clear());
        String json = SixMaxTexturePayoffTable.mapper().writeValueAsString(a);
        assertThrows(
                Exception.class, () -> SixMaxTexturePayoffTable.mapper().readValue(json, result));
    }

    @Test
    void rejectsEmptyDuplicateOversizedNullAndIncreasedLimitsBeforeCapturingAnyGame() {
        MultiPlayerCfrGame<Node> absent = null;
        var qs = mixedQuestions();
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSequenceBatch.solve(absent, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, List.of(qs.getFirst(), qs.getFirst())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, Collections.nCopies(9, qs.getFirst())));
        assertThrows(
                NullPointerException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, Arrays.asList(qs.getFirst(), null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, qs, 8_000_001, 10_000, 4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, qs, 8_000_000, 10_001, 4_000_000_000L));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                absent, qs, 8_000_000, 10_000, 4_000_000_001L));
    }

    @Test
    void validatesEveryQuestionBeforeTheSharedParent() {
        var game = mixedRoots();
        var valid = mixedQuestions().getFirst();
        for (var bad :
                List.of(
                        new Question(3, "3:missing", "risk"),
                        new Question(3, "3:root-0", "unknown"),
                        new Question(3, "3:private-0-0", "left"),
                        new Question(4, "4:root", "risk")))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerRootSequenceBatch.solve(game, List.of(valid, bad)));
        var limited = RootSequenceMinimaxTest.privateTypes(5, 64);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                limited, List.of(new Question(5, "5:root", "risk"))));
    }

    @Test
    void aZeroCompilerBudgetRejectsBeforeAnyParentSolve() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                mixedRoots(), mixedQuestions(), 0, 10_000, 4_000_000_000L));
    }

    @Test
    void cannotBorrowAnOwnedParentFromAnotherGameOrAlgorithm() throws Exception {
        var game = mixedRoots();
        var q = mixedQuestions().getFirst();
        var other =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        RootSequenceMinimaxTest.privateTypes(5, 32));
        var mode = FiniteTwoPlayerAffineSequenceForm.solve(game);
        for (var parent : List.of(other, mode))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootSequenceMinimax.solveCapturedParent(
                                    game, q, parent, 8_000_000, 10_000, 4_000_000_000L));
    }

    @Test
    void theCompilerBudgetIsSharedRatherThanResetForEachQuestion() {
        var a = controls.getFirst().audit();
        var first = a.minima().get(0);
        var second = a.minima().get(1);
        long cap =
                2L * first.baseline().treeNodes()
                        + first.work().compilerUnits()
                        + second.work().compilerUnits()
                        - 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootSequenceBatch.solve(
                                mixedRoots(),
                                mixedQuestions().subList(0, 2),
                                cap,
                                10_000,
                                4_000_000_000L));
    }

    @Test
    void thePivotBudgetIsSharedRatherThanResetForEachQuestion() {
        var a = controls.getFirst().audit();
        int cap =
                a.minima().get(0).work().endpointPivots()
                        + a.minima().get(1).work().endpointPivots()
                        - 1;
        assertTrue(
                a.minima().get(0).work().endpointPivots() > 0
                        && a.minima().get(1).work().endpointPivots() > 0);
        var error =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                FiniteTwoPlayerRootSequenceBatch.solve(
                                        mixedRoots(),
                                        mixedQuestions().subList(0, 2),
                                        8_000_000,
                                        cap,
                                        4_000_000_000L));
        assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, error.reason());
    }

    @Test
    void theArithmeticBudgetIsSharedRatherThanResetForEachQuestion() {
        var a = controls.getFirst().audit();
        long cap =
                a.minima().get(0).work().endpointArithmeticWork()
                        + a.minima().get(1).work().endpointArithmeticWork()
                        - 1;
        var error =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                FiniteTwoPlayerRootSequenceBatch.solve(
                                        mixedRoots(),
                                        mixedQuestions().subList(0, 2),
                                        8_000_000,
                                        10_000,
                                        cap));
        assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, error.reason());
    }

    @Test
    void rejectsQuestionMassBelowTheOriginalReachFloorBeforeSolvingTheParent() {
        var selected = RootSequenceMinimaxTest.privateTypes(5, 2);
        var rare =
                new Snapshot(
                        6,
                        new Node(
                                -1,
                                "",
                                List.of(),
                                List.of(.00001, .99999),
                                List.of(selected.initialState(), terminal(5, 0)),
                                List.of()));
        var error =
                assertThrows(
                        FiniteTwoPlayerRootSequenceMinimax.Rejected.class,
                        () ->
                                FiniteTwoPlayerRootSequenceBatch.solve(
                                        rare, List.of(new Question(5, "5:root", "risk"))));
        assertEquals(FiniteTwoPlayerRootSequenceMinimax.Failure.INSUFFICIENT_REACH, error.reason());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("One unused analytic output required");
        try (var stream = Files.newOutputStream(Path.of(args[0]), StandardOpenOption.CREATE_NEW)) {
            var captured = capture();
            byte[] raw = SixMaxTexturePayoffTable.mapper().writeValueAsBytes(captured);
            if (raw.length > 8 * 1024 * 1024)
                throw new IllegalArgumentException(
                        "Expanded analytic capture exceeds original cap");
            var bytes = new java.io.ByteArrayOutputStream();
            try (var zip = new GZIPOutputStream(bytes)) {
                zip.write(raw);
            }
            if (bytes.size() > 8 * 1024 * 1024)
                throw new IllegalArgumentException("Stored analytic capture exceeds original cap");
            stream.write(bytes.toByteArray());
        }
        System.out.println(
                "Captured two declared batches: two shared parents/four parent LPs/ten root-minimum LPs; no physical game or CFR");
    }
}
