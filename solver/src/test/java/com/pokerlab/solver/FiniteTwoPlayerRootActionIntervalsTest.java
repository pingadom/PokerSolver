package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerRootActionIntervalsTest {
    static Node terminal(int hero, double value) {
        var u = new ArrayList<>(List.of(-1.0, -1.0, -1.0, -1.0, -1.0, -1.0));
        u.set(hero, value);
        u.set(hero == 3 ? 5 : 3, 4 - value);
        return new Node(-2, "", List.of(), List.of(), List.of(), u);
    }

    static Node decision(int actor, String key, List<String> actions, Node... children) {
        return new Node(actor, actor + ":" + key, actions, List.of(), List.of(children), List.of());
    }

    static Snapshot dominated(int hero) {
        int opponent = hero == 3 ? 5 : 3;
        return new Snapshot(
                6,
                decision(
                        hero,
                        "root",
                        List.of("safe", "risk"),
                        terminal(hero, 0),
                        decision(
                                opponent,
                                "reply",
                                List.of("x", "y"),
                                terminal(hero, -2),
                                terminal(hero, -1))));
    }

    @Test
    void dominatedActionHasAnIntervalEvenThoughEveryOpponentPolicyIsGloballyOptimal()
            throws Exception {
        for (int hero : List.of(3, 5)) {
            var audit =
                    FiniteTwoPlayerRootActionIntervals.solve(
                                    dominated(hero),
                                    new FiniteTwoPlayerRootActionIntervals.Question(
                                            hero, hero + ":root", "risk"))
                            .audit();
            assertEquals(-2, audit.lowerUtility(), 1e-10);
            assertEquals(-1, audit.upperUtility(), 1e-10);
            assertEquals(0, audit.globalHeroUpperValue(), 1e-10);
            assertEquals(4, audit.baseline().constantSum());
            assertEquals(1, audit.plans().size());
            assertEquals(2, audit.work().intervalLpSolves());
            assertEquals(0, audit.lowerWitness().globalHeroBestResponse(), 1e-10);
            assertEquals(-2, audit.lowerWitness().conditionalHeroBestResponse(), 1e-10);
            assertEquals(
                    -1, audit.upperWitnesses().getFirst().conditionalHeroBestResponse(), 1e-10);
            assertFalse(audit.trainerAdmission());
        }
    }

    @Test
    void uniqueMatchingPenniesOpponentHasOnlyTheExplicitSecuritySlackWidth() throws Exception {
        for (int hero : List.of(3, 5)) {
            int opponent = hero == 3 ? 5 : 3;
            var game =
                    new Snapshot(
                            6,
                            decision(
                                    hero,
                                    "root",
                                    List.of("a", "b"),
                                    decision(
                                            opponent,
                                            "hidden",
                                            List.of("x", "y"),
                                            terminal(hero, 1),
                                            terminal(hero, -1)),
                                    decision(
                                            opponent,
                                            "hidden",
                                            List.of("x", "y"),
                                            terminal(hero, -1),
                                            terminal(hero, 1))));
            double slack = 1e-6;
            var audit =
                    FiniteTwoPlayerRootActionIntervals.solve(
                                    game,
                                    new FiniteTwoPlayerRootActionIntervals.Question(
                                            hero, hero + ":root", "a"),
                                    slack)
                            .audit();
            assertEquals(-slack, audit.lowerUtility(), 1e-10);
            assertEquals(slack, audit.upperUtility(), 1e-10);
            assertEquals(slack, audit.securitySlack());
            assertEquals(0, audit.globalHeroUpperValue(), 1e-12);
        }
    }

    @Test
    void heroMustChooseOneContinuationAcrossHiddenWorldsAndOpponentActions() throws Exception {
        var worlds = new ArrayList<Node>();
        for (int hidden = 0; hidden < 2; hidden++) {
            var replies = new ArrayList<Node>();
            for (int opponent = 0; opponent < 2; opponent++) {
                var guesses = new Node[2];
                for (int guess = 0; guess < 2; guess++)
                    guesses[guess] = terminal(5, (guess == hidden ? -1 : -3) - opponent);
                replies.add(decision(5, "guess", List.of("heads", "tails"), guesses));
            }
            worlds.add(
                    decision(
                            5,
                            "root",
                            List.of("safe", "risk"),
                            terminal(5, 0),
                            decision(
                                    3,
                                    "hidden-reply",
                                    List.of("x", "y"),
                                    replies.toArray(Node[]::new))));
        }
        var game = new Snapshot(6, new Node(-1, "", List.of(), List.of(.5, .5), worlds, List.of()));
        var audit =
                FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "risk"))
                        .audit();
        assertEquals(2, audit.plans().size());
        assertEquals(-3, audit.lowerUtility(), 1e-10);
        assertEquals(
                -2,
                audit.upperUtility(),
                1e-10); // A per-world maximizer would incorrectly give -1.
        assertEquals(1, audit.questionChanceMass());
        assertTrue(audit.plans().stream().allMatch(p -> p.continuation().size() == 1));
    }

    @Test
    void lowerEpigraphMinimizesTheBestContinuationRatherThanEachPlanSeparately() throws Exception {
        var game =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 0),
                                decision(
                                        3,
                                        "hidden",
                                        List.of("x", "y"),
                                        decision(
                                                5,
                                                "guess",
                                                List.of("x", "y"),
                                                terminal(5, -1),
                                                terminal(5, -3)),
                                        decision(
                                                5,
                                                "guess",
                                                List.of("x", "y"),
                                                terminal(5, -3),
                                                terminal(5, -1)))));
        var audit =
                FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "risk"))
                        .audit();
        assertEquals(
                -2, audit.lowerUtility(), 1e-10); // Both fixed-plan minima would instead be -3.
        assertEquals(-1, audit.upperUtility(), 1e-10);
        assertEquals(2, audit.plans().size());
        assertEquals(3, audit.work().intervalLpSolves());
        assertEquals(.5, audit.lowerWitness().opponentFlow().realization().get(1), 1e-10);
        assertEquals(.5, audit.lowerWitness().opponentFlow().realization().get(2), 1e-10);
    }

    @Test
    void conditioningUsesOnlyTheSelectedRootTypeAndItsChanceProbability() throws Exception {
        var game =
                new Snapshot(
                        6,
                        new Node(
                                -1,
                                "",
                                List.of(),
                                List.of(.25, .75),
                                List.of(
                                        dominated(5).initialState(),
                                        decision(
                                                5,
                                                "other",
                                                List.of("safe", "risk"),
                                                terminal(5, 0),
                                                decision(
                                                        3,
                                                        "other-reply",
                                                        List.of("x", "y"),
                                                        terminal(5, -100),
                                                        terminal(5, -99)))),
                                List.of()));
        var audit =
                FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "risk"))
                        .audit();
        assertEquals(.25, audit.questionChanceMass());
        assertEquals(-2, audit.lowerUtility(), 1e-10);
        assertEquals(-1, audit.upperUtility(), 1e-10);
    }

    @Test
    void laterAndIllegalQuestionsAndUnboundedSlackAreRejected() {
        var game = dominated(5);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        3, "3:reply", "x")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "bad")));
        for (double slack : List.of(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, .1))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerRootActionIntervals.solve(
                                    game,
                                    new FiniteTwoPlayerRootActionIntervals.Question(
                                            5, "5:root", "risk"),
                                    slack));
    }

    @Test
    void workCapsFailWithoutReturningACertificateAndAuditIsImmutableAndOpaque() throws Exception {
        var game = dominated(5);
        var question = new FiniteTwoPlayerRootActionIntervals.Question(5, "5:root", "risk");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                question,
                                1e-8,
                                0,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                question,
                                1e-8,
                                8_000_000,
                                0,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                question,
                                1e-8,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                0));
        var audit = FiniteTwoPlayerRootActionIntervals.solve(game, question).audit();
        assertThrows(UnsupportedOperationException.class, () -> audit.plans().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> audit.plans().getFirst().cost().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> audit.lowerWitness().point().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> audit.plans().getFirst().continuation().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerRootActionIntervals.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void continuationProductIsRejectedBeforeEnumeratingTooManyPlans() {
        var later = new ArrayList<Node>();
        for (int i = 0; i < 7; i++)
            later.add(
                    decision(5, "later" + i, List.of("a", "b"), terminal(5, -2), terminal(5, -1)));
        var chance = new Node(-1, "", List.of(), Collections.nCopies(7, 1.0 / 7), later, List.of());
        var game =
                new Snapshot(
                        6,
                        decision(
                                5,
                                "root",
                                List.of("safe", "risk"),
                                terminal(5, 0),
                                decision(3, "reply", List.of("x", "y"), chance, chance)));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                FiniteTwoPlayerRootActionIntervals.solve(
                                        game,
                                        new FiniteTwoPlayerRootActionIntervals.Question(
                                                5, "5:root", "risk")));
        assertTrue(failure.getMessage().contains("plan budget"));
    }

    @Test
    void inputCallbacksAreUsedOnceAndAllSolvesAndResponsesUseTheImmutableSnapshot()
            throws Exception {
        var snapshot = dominated(5);
        int[] calls = new int[2];
        var game =
                new MultiPlayerCfrGame<Node>() {
                    public int playerCount() {
                        return 6;
                    }

                    public Node initialState() {
                        calls[0]++;
                        return snapshot.initialState();
                    }

                    public boolean isTerminal(Node s) {
                        return snapshot.isTerminal(s);
                    }

                    public double[] terminalUtilities(Node s) {
                        calls[1]++;
                        return snapshot.terminalUtilities(s);
                    }

                    public int currentPlayer(Node s) {
                        return snapshot.currentPlayer(s);
                    }

                    public List<String> legalActions(Node s) {
                        return snapshot.legalActions(s);
                    }

                    public String informationSet(Node s) {
                        return snapshot.informationSet(s);
                    }

                    public Node afterAction(Node s, String a) {
                        return snapshot.afterAction(s, a);
                    }

                    public List<ChanceOutcome<Node>> chanceOutcomes(Node s) {
                        return snapshot.chanceOutcomes(s);
                    }
                };
        FiniteTwoPlayerRootActionIntervals.solve(
                game, new FiniteTwoPlayerRootActionIntervals.Question(5, "5:root", "risk"));
        assertArrayEquals(new int[] {1, 3}, calls);
    }

    @Test
    void aggregateLpBudgetCoversEveryContinuationAndTheLowerEpigraph() throws Exception {
        var game = dominated(5);
        var q = new FiniteTwoPlayerRootActionIntervals.Question(5, "5:root", "risk");
        var work = FiniteTwoPlayerRootActionIntervals.solve(game, q).audit().work();
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                q,
                                1e-8,
                                8_000_000,
                                work.intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                q,
                                1e-8,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                work.intervalLpArithmeticWork() - 1));
    }
}
