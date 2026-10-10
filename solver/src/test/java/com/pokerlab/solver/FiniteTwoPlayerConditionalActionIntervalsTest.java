package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerRootActionIntervalsTest.*;
import static com.pokerlab.solver.FiniteTwoPlayerSequenceForm.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerConditionalActionIntervalsTest {
    static Snapshot posteriorGame(int hero, boolean mandatory, boolean continuation) {
        int opponent = hero == 3 ? 5 : 3;
        var worlds = new ArrayList<Node>();
        for (int hidden = 0; hidden < 2; hidden++) {
            Node risk = terminal(hero, hidden == 0 ? -2 : -6);
            if (continuation) {
                var replies = new ArrayList<Node>();
                for (int reply = 0; reply < 2; reply++) {
                    var guesses = new Node[2];
                    for (int guess = 0; guess < 2; guess++)
                        guesses[guess] = terminal(hero, (guess == hidden ? -1 : -3) - reply);
                    replies.add(decision(hero, "guess", List.of("heads", "tails"), guesses));
                }
                risk =
                        decision(
                                opponent,
                                "reply:" + hidden,
                                List.of("x", "y"),
                                replies.toArray(Node[]::new));
            }
            var question =
                    decision(hero, "question", List.of("safe", "risk"), terminal(hero, 0), risk);
            var signal =
                    hidden == 0 && mandatory
                            ? decision(opponent, "signal:" + hidden, List.of("show"), question)
                            : decision(
                                    opponent,
                                    "signal:" + hidden,
                                    List.of("show", "hide"),
                                    question,
                                    terminal(hero, 0));
            worlds.add(decision(hero, "root", List.of("enter", "exit"), signal, terminal(hero, 0)));
        }
        return new Snapshot(6, new Node(-1, "", List.of(), List.of(.5, .5), worlds, List.of()));
    }

    static FiniteTwoPlayerConditionalActionIntervals.Question question(int hero) {
        return new FiniteTwoPlayerConditionalActionIntervals.Question(
                hero, hero + ":question", "risk");
    }

    @Test
    void opponentActionsChangeBothTheQuestionReachAndHiddenPosteriorForEitherHeroOrientation()
            throws Exception {
        for (int hero : List.of(3, 5)) {
            var r =
                    FiniteTwoPlayerConditionalActionIntervals.solve(
                                    posteriorGame(hero, true, false), question(hero))
                            .audit();
            assertEquals(-4, r.lowerUtility(), 1e-10);
            assertEquals(
                    -2, r.upperUtility(), 1e-10); // Fixed initial posterior would always give -4.
            assertEquals(.5, r.minimumReachWitness().questionReach(), 1e-10);
            assertEquals(1, r.maximumReachWitness().questionReach(), 1e-10);
            assertEquals(.5 - 1e-8, r.certifiedReachLowerBound(), 1e-10);
            assertEquals(1 + 1e-8, r.certifiedReachUpperBound(), 1e-10);
            assertEquals(1, r.lowerWitness().scaling(), 1e-10);
            assertEquals(2, r.upperWitnesses().get(r.upperPlan()).scaling(), 1e-10);
            assertEquals(List.of(new OwnAction(hero + ":root", "enter")), r.fixedHeroPast());
            assertEquals(
                    List.of(.5, .5),
                    r.lowerWitness().posterior().stream()
                            .map(FiniteTwoPlayerConditionalActionIntervals.Posterior::probability)
                            .toList());
            assertEquals(
                    List.of(1.0, 0.0),
                    r.upperWitnesses().get(r.upperPlan()).posterior().stream()
                            .map(FiniteTwoPlayerConditionalActionIntervals.Posterior::probability)
                            .toList());
            assertEquals(4, r.work().intervalLpSolves());
            assertFalse(r.trainerAdmission());
        }
    }

    @Test
    void completeContinuationUsesOneActionAcrossHiddenWorldsAndTheLowerEpigraph() throws Exception {
        var r =
                FiniteTwoPlayerConditionalActionIntervals.solve(
                                posteriorGame(5, true, true), question(5))
                        .audit();
        assertEquals(-3, r.lowerUtility(), 1e-10);
        assertEquals(-1, r.upperUtility(), 1e-10);
        assertEquals(2, r.numeratorPlans().size());
        assertEquals(5, r.work().intervalLpSolves());
        assertEquals(-3, r.lowerWitness().conditionalHeroBestResponse(), 1e-10);
        assertTrue(r.numeratorPlans().stream().allMatch(p -> p.continuation().size() == 1));
        assertEquals(.5, r.lowerWitness().posterior().getFirst().probability(), 1e-10);
    }

    @Test
    void zeroReachOpponentStrategiesAreRejectedInsteadOfRemovingThemFromTheSecurityFace() {
        var rejected =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solve(
                                        posteriorGame(5, false, false), question(5)));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                rejected.reason());
    }

    @Test
    void aDeclaredFloorIsVerifiedOverTheFullFaceWithConservativeDualPadding() throws Exception {
        var game = posteriorGame(5, true, false);
        var r =
                FiniteTwoPlayerConditionalActionIntervals.solve(game, question(5), 1e-8, .49)
                        .audit();
        assertTrue(r.certifiedReachLowerBound() >= .49);
        var rejected =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solve(
                                        game, question(5), 1e-8, .5));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                rejected.reason());
    }

    @Test
    void fixedHeroPastCancelsEvenWhenTheBaselineNeverChoosesIt() throws Exception {
        var r =
                FiniteTwoPlayerConditionalActionIntervals.solve(
                                posteriorGame(5, true, false), question(5))
                        .audit();
        var flow = r.baseline().secondFlow();
        int enter =
                flow.sequences().stream()
                        .filter(s -> s.key().equals("5:root") && s.action().equals("enter"))
                        .findFirst()
                        .orElseThrow()
                        .index();
        assertEquals(0, flow.realization().get(enter), 1e-12);
        assertTrue(r.minimumReachWitness().questionReach() > .49);
        assertEquals(-4, r.lowerUtility(), 1e-10);
    }

    @Test
    void rootControlMatchesTheExistingRootOnlyCompiler() throws Exception {
        var game = dominated(5);
        var r =
                FiniteTwoPlayerConditionalActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, "5:root", "risk"))
                        .audit();
        var old =
                FiniteTwoPlayerRootActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerRootActionIntervals.Question(
                                        5, "5:root", "risk"))
                        .audit();
        assertEquals(old.lowerUtility(), r.lowerUtility(), 1e-10);
        assertEquals(old.upperUtility(), r.upperUtility(), 1e-10);
        assertEquals(1, r.minimumReachWitness().questionReach());
        assertEquals(1, r.maximumReachWitness().questionReach());
    }

    @Test
    void repeatedPastOpponentActionsUseTheLastRealizationMassExactlyOnce() throws Exception {
        var worlds = new ArrayList<Node>();
        for (int hidden = 0; hidden < 2; hidden++) {
            var guess =
                    decision(
                            5,
                            "guess",
                            List.of("heads", "tails"),
                            terminal(5, hidden == 0 ? -1 : -5),
                            terminal(5, hidden == 0 ? -3 : -1));
            var question = decision(5, "question", List.of("safe", "risk"), terminal(5, 0), guess);
            var advance = decision(3, "advance:" + hidden, List.of("go"), question);
            var signal =
                    hidden == 0
                            ? decision(3, "signal:0", List.of("show"), advance)
                            : decision(
                                    3,
                                    "signal:1",
                                    List.of("show", "hide"),
                                    advance,
                                    terminal(5, 0));
            worlds.add(decision(5, "root", List.of("enter", "exit"), signal, terminal(5, 0)));
        }
        var game = new Snapshot(6, new Node(-1, "", List.of(), List.of(.5, .5), worlds, List.of()));
        var r = FiniteTwoPlayerConditionalActionIntervals.solve(game, question(5)).audit();
        assertEquals(-7.0 / 3, r.lowerUtility(), 1e-10);
        assertEquals(-1, r.upperUtility(), 1e-10);
        assertEquals(.75, r.lowerWitness().questionReach(), 1e-10);
        assertEquals(1.0 / 3, r.lowerWitness().posterior().get(1).probability(), 1e-10);
        var flow = r.lowerWitness().opponentFlow();
        for (String key : List.of("3:signal:1", "3:advance:1")) {
            var sequence =
                    flow.sequences().stream()
                            .filter(s -> s.key().equals(key))
                            .findFirst()
                            .orElseThrow();
            assertEquals(.5, flow.realization().get(sequence.index()), 1e-10);
        }
        assertEquals(.25, r.lowerWitness().posterior().get(1).originalRealizationWeight(), 1e-10);
        assertEquals(.25, r.lowerWitness().posterior().get(1).behavioralWeight(), 1e-10);
    }

    @Test
    void continuationProductIsBoundedBeforePlanEnumeration() {
        var later = new ArrayList<Node>();
        for (int i = 0; i < 7; i++)
            later.add(
                    decision(5, "later" + i, List.of("a", "b"), terminal(5, -2), terminal(5, -1)));
        var chance = new Node(-1, "", List.of(), Collections.nCopies(7, 1.0 / 7), later, List.of());
        var question = decision(5, "question", List.of("safe", "risk"), terminal(5, 0), chance);
        var game = new Snapshot(6, decision(3, "past", List.of("go"), question));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FiniteTwoPlayerConditionalActionIntervals.solve(game, question(5)));
        assertTrue(error.getMessage().contains("plan budget"));
    }

    @Test
    void allReachControlsAndBestResponsesUseOneImmutableInputSnapshot() throws Exception {
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

                    public Node afterAction(Node s, String action) {
                        return snapshot.afterAction(s, action);
                    }

                    public List<ChanceOutcome<Node>> chanceOutcomes(Node s) {
                        return snapshot.chanceOutcomes(s);
                    }
                };
        FiniteTwoPlayerConditionalActionIntervals.solve(
                game, new FiniteTwoPlayerConditionalActionIntervals.Question(5, "5:root", "risk"));
        assertArrayEquals(new int[] {1, 3}, calls);
    }

    @Test
    void invalidQuestionsAndLimitsAreRejected() {
        var game = posteriorGame(5, true, false);
        for (String key : List.of("5:missing", "5:signal:0"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerConditionalActionIntervals.solve(
                                    game,
                                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                                            5, key, "risk")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solve(
                                game,
                                new FiniteTwoPlayerConditionalActionIntervals.Question(
                                        5, "5:question", "bad")));
        for (double floor : List.of(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1.01, .00001))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            FiniteTwoPlayerConditionalActionIntervals.solve(
                                    game, question(5), 1e-8, floor));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerConditionalActionIntervals.solve(game, question(5), 0, .01));
    }

    @Test
    void sharedWorkCapsCoverReachOptimizationAndEveryFractionalControl() throws Exception {
        var game = posteriorGame(5, true, true);
        var q = question(5);
        var r = FiniteTwoPlayerConditionalActionIntervals.solve(game, q).audit();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                0,
                                BoundedLinearProgram.MAX_PIVOTS,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                8_000_000,
                                r.work().intervalLpPivots() - 1,
                                BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertThrows(
                BoundedLinearProgram.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solve(
                                game,
                                q,
                                1e-8,
                                .01,
                                8_000_000,
                                BoundedLinearProgram.MAX_PIVOTS,
                                r.work().intervalLpArithmeticWork() - 1));
        assertThrows(
                UnsupportedOperationException.class, () -> r.lowerWitness().posterior().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.lowerWitness().opponentVariables().clear());
        assertTrue(
                Arrays.stream(
                                FiniteTwoPlayerConditionalActionIntervals.Result.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }
}
