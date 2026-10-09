package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.*;
import org.junit.jupiter.api.*;

class SixMaxSuppliedRangeCutoffTest {
    static SixMaxSuppliedRangePreflopStudy.Result study;

    @BeforeAll
    static void replay() throws Exception {
        study =
                SixMaxSuppliedRangePreflopStudy.replay(
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-cutoff-threebet-input.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-cutoff-threebet-policy.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-cutoff-threebet-report.json"));
    }

    @Test
    void newSeatPairQualifiesWithoutChangingSourceDerivedContent() {
        assertEquals(List.of(Seat.CO, Seat.BTN), study.core().activeSeats());
        assertEquals(2, study.report().solve().firstActor());
        assertEquals(3, study.report().solve().secondActor());
        assertEquals(1.5, study.report().solve().constantSum());
        assertTrue(study.report().qualifiedForOfflinePractice());
        assertEquals(5, study.report().stableDecisions());
        assertTrue(
                study.report().decisions().stream()
                        .allMatch(d -> d.material() && d.stable() && d.comparisons().size() == 2));
        assertFalse(study.report().trainerAdmission());
    }

    @Test
    void literalPayoutsRetainBothFoldedBlindsAndReturnUncalledExcess() {
        var game = study.core();
        for (var root : game.chanceOutcomes(game.initialState())) {
            var state = root.state();
            var shares = game.payoffs().get(state.dealIndex()).counts().estimate().shares();
            assertArrayEquals(
                    new double[] {0, 0, -3, 4.5, -.5, -1},
                    game.terminalUtilities(game.afterAction(state, "fold")),
                    1e-12);
            assertArrayEquals(
                    new double[] {0, 0, 19.5 * shares[2] - 9, 19.5 * shares[3] - 9, -.5, -1},
                    game.terminalUtilities(game.afterAction(state, "call")),
                    1e-12);
            var raise = game.afterAction(state, "raise:22.0");
            assertArrayEquals(
                    new double[] {0, 0, 10.5, -9, -.5, -1},
                    game.terminalUtilities(game.afterAction(raise, "fold")),
                    1e-12);
            assertArrayEquals(
                    new double[] {0, 0, 45.5 * shares[2] - 22, 45.5 * shares[3] - 22, -.5, -1},
                    game.terminalUtilities(game.afterAction(raise, "call")),
                    1e-12);
            assertEquals(OptionalDouble.of(-1), game.inactivePlayerUtility(state, 5));
        }
    }

    @Test
    void allMoveFeedbackMatchesIndependentLiteralConditionalPayoffs() {
        var game = study.core();
        var primary = study.artifact().orElseThrow().solution();
        var policies = new ArrayList<CfrSolution>();
        policies.add(primary);
        for (int t : List.of(500, 1000))
            policies.add(
                    new MultiPlayerCfrSolver<>(
                                    game,
                                    CfrSolver.Variant.CFR_PLUS,
                                    MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY)
                            .solve(t));
        for (var d : study.report().decisions()) {
            var row = d.primary();
            var worlds = new TreeMap<Integer, Double>();
            double mass = 0;
            for (var world : game.prior())
                if (world.dealtCombos().get(row.actor().ordinal()).equals(row.ownHand())) {
                    double p = world.conditionalProbability();
                    if (row.actor() == Seat.BTN)
                        p *=
                                primary.strategy()
                                        .get(
                                                "2:"
                                                        + game.informationSet(
                                                                new SixMaxHeadsUpPreflopGame.State(
                                                                        world.dealIndex(), "")))
                                        .get("raise:22.0");
                    if (p > 0) {
                        worlds.put(world.dealIndex(), p);
                        mass += p;
                    }
                }
            assertEquals(row.decisionProbability(), mass, 1e-12);
            for (int i = 0; i < policies.size(); i++) {
                var values =
                        i == 0
                                ? row.values()
                                : d.comparisons().get(i - 1).fixedQuestionReferenceValues();
                for (String action : values.actionEvBb().keySet()) {
                    double ev = 0;
                    for (var world : worlds.entrySet()) {
                        var shares =
                                game.payoffs().get(world.getKey()).counts().estimate().shares();
                        double v;
                        if (row.actor() == Seat.BTN)
                            v = action.equals("fold") ? 0 : 45.5 * shares[3] - 13;
                        else if (action.equals("fold")) v = 0;
                        else if (action.equals("call")) v = 19.5 * shares[2] - 6;
                        else {
                            var future =
                                    new SixMaxHeadsUpPreflopGame.State(
                                            world.getKey(), "|CO:raise:22.0");
                            var response =
                                    policies.get(i)
                                            .strategy()
                                            .get("3:" + game.informationSet(future));
                            v =
                                    response.get("fold") * 13.5
                                            + response.get("call") * (45.5 * shares[2] - 19);
                        }
                        ev += world.getValue() / mass * v;
                    }
                    assertEquals(ev, values.actionEvBb().get(action), 1e-11);
                }
            }
        }
    }

    @Test
    void questionsExposePublicSeatActionsAndRejectForeignOrAlteredAnswers() throws Exception {
        var trainer = new SixMaxSuppliedRangePreflopTrainer(study);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var seen = new HashSet<Seat>();
        for (int seed = 0; seed < 100; seed++) {
            var q = trainer.question(seed);
            seen.add(q.hero());
            assertEquals(q.hero(), q.table().actingSeat());
            assertEquals(6, q.table().players().size());
            if (q.hero() == Seat.CO) {
                assertEquals(13.5, q.table().potBb());
                assertEquals(6, q.table().toCallBb());
            } else {
                assertEquals(Seat.BTN, q.hero());
                assertEquals(32.5, q.table().potBb());
                assertEquals(13, q.table().toCallBb());
            }
            String json = mapper.writeValueAsString(q);
            for (String hidden :
                    List.of("4c 4d", "5c 6c", "8d 8s", "actionEvBb", "frequencies", "solution"))
                assertFalse(json.contains(hidden));
        }
        assertEquals(Set.of(Seat.CO, Seat.BTN), seen);
        var q = trainer.question(711);
        var altered = (ObjectNode) mapper.valueToTree(q);
        altered.put("heroCards", "As Ad");
        var forged = mapper.treeToValue(altered, SixMaxSuppliedRangePreflopTrainer.Question.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(forged, forged.legalActions().getFirst()));
        altered = (ObjectNode) mapper.valueToTree(q);
        altered.put("studyHash", "0".repeat(64));
        var foreign = mapper.treeToValue(altered, SixMaxSuppliedRangePreflopTrainer.Question.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(foreign, foreign.legalActions().getFirst()));
    }
}
