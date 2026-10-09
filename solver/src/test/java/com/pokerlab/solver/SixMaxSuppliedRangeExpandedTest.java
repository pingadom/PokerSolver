package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxHeadsUpPreflopGame.State;
import java.util.*;
import org.junit.jupiter.api.*;

/** Literal controls include a later hero decision, with one response across its hidden worlds. */
class SixMaxSuppliedRangeExpandedTest {
    static SixMaxSuppliedRangePreflopStudy.Result study;

    @BeforeAll
    static void replay() throws Exception {
        study =
                SixMaxSuppliedRangePreflopStudy.replay(
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-expanded-three-target-input.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-expanded-three-target-policy.json"),
                        SixMaxSuppliedRangePreflopGameTest.data(
                                "supplied-range-expanded-three-target-report.json"));
    }

    @Test
    void twentySevenWorldsAndBothPlayersLaterDecisionsFitUnchangedBudgets() {
        var report = study.report();
        var budget = report.binding().budget();
        assertTrue(report.qualifiedForOfflinePractice());
        assertEquals(7, report.stableDecisions());
        assertEquals(27, budget.jointDeals());
        assertEquals(244, budget.completeTreeStates());
        assertEquals(17766216, budget.enumeratedBoards());
        assertEquals(35532432, budget.handEvaluations());
        assertEquals(Map.of(Seat.BTN, 3, Seat.BB, 6), budget.informationSets());
        assertEquals(Map.of(Seat.BTN, 10, Seat.BB, 16), budget.sequences());
        assertTrue(
                report.decisions().stream()
                        .flatMap(d -> d.comparisons().stream())
                        .allMatch(
                                c ->
                                        c.failures().isEmpty()
                                                && c.maximumActionEvDriftBb() < .000364));
        assertFalse(report.trainerAdmission());
    }

    @Test
    void allFiveThousandEightHundredThirtyTwoPurePlanPairsCertifyTheEquilibrium() {
        var report = study.report();
        var policy = study.artifact().orElseThrow().solution();
        var button =
                report.prior().stream()
                        .map(w -> w.dealtCombos().get(3))
                        .distinct()
                        .sorted()
                        .toList();
        var blind =
                report.prior().stream()
                        .map(w -> w.dealtCombos().get(5))
                        .distinct()
                        .sorted()
                        .toList();
        assertEquals(3, button.size());
        assertEquals(3, blind.size());
        double[] first = new double[27], second = new double[216];
        var actions = List.of("fold", "call", "raise:22.0");
        for (int plan = 0; plan < 27; plan++) {
            int code = plan;
            double p = 1;
            for (var hand : button) {
                p *=
                        policy.strategy()
                                .get("3:supplied-conditional-preflop:" + hand + ":|BB:raise:9.0")
                                .get(actions.get(code % 3));
                code /= 3;
            }
            first[plan] = p;
        }
        var rootActions = List.of("fold", "call", "raise:9.0");
        for (int plan = 0; plan < 216; plan++) {
            int code = plan;
            double p = 1;
            for (var hand : blind) {
                int digit = code % 6;
                code /= 6;
                p *=
                        policy.strategy()
                                .get("5:supplied-conditional-preflop:" + hand + ":")
                                .get(rootActions.get(digit % 3));
                p *=
                        policy.strategy()
                                .get(
                                        "5:supplied-conditional-preflop:"
                                                + hand
                                                + ":|BB:raise:9.0|BTN:raise:22.0")
                                .get(digit / 3 == 0 ? "fold" : "call");
            }
            second[plan] = p;
        }
        assertEquals(1, Arrays.stream(first).sum(), 1e-12);
        assertEquals(1, Arrays.stream(second).sum(), 1e-12);
        double[][] matrix = new double[27][216];
        for (int a = 0; a < 27; a++)
            for (int b = 0; b < 216; b++)
                for (var world : report.prior()) {
                    int hero = button.indexOf(world.dealtCombos().get(3)),
                            villain = blind.indexOf(world.dealtCombos().get(5));
                    int btn = a / (int) Math.pow(3, hero) % 3,
                            bb = b / (int) Math.pow(6, villain) % 6;
                    var counts = report.payoffs().get(world.dealIndex()).counts();
                    double share = (counts.firstWins() + .5 * counts.ties()) / counts.boards();
                    double u =
                            bb % 3 == 0
                                    ? 1.5
                                    : bb % 3 == 1
                                            ? 6.5 * share - 3
                                            : btn == 0
                                                    ? -3
                                                    : btn == 1
                                                            ? 18.5 * share - 9
                                                            : bb / 3 == 0 ? 9.5 : 44.5 * share - 22;
                    matrix[a][b] += world.conditionalProbability() * u;
                }
        double value = 0;
        for (int a = 0; a < 27; a++)
            for (int b = 0; b < 216; b++) value += first[a] * second[b] * matrix[a][b];
        assertEquals(.2633447521970912, value, 1e-12);
        assertEquals(report.solve().lowerValue(), value, 1e-12);
        for (int a = 0; a < 27; a++) {
            double u = 0;
            for (int b = 0; b < 216; b++) u += second[b] * matrix[a][b];
            assertTrue(u <= value + 1e-10);
        }
        for (int b = 0; b < 216; b++) {
            double u = 0;
            for (int a = 0; a < 27; a++) u += first[a] * matrix[a][b];
            assertTrue(u >= value - 1e-10);
        }
    }

    @Test
    void everyMaterialActionEvUsesLiteralBayesAndOneHiddenInformationSetContinuation() {
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
        for (var d : study.report().decisions())
            if (d.material()) {
                var row = d.primary();
                var worlds = new TreeMap<Integer, Double>();
                double mass = 0;
                for (var world : game.prior())
                    if (world.dealtCombos().get(row.actor().ordinal()).equals(row.ownHand())) {
                        double p = world.conditionalProbability();
                        if (!row.publicHistory().isEmpty())
                            p *=
                                    primary.strategy()
                                            .get(
                                                    "5:"
                                                            + game.informationSet(
                                                                    new State(
                                                                            world.dealIndex(), "")))
                                            .get("raise:9.0");
                        if (row.publicHistory().contains("BTN:"))
                            p *=
                                    primary.strategy()
                                            .get(
                                                    "3:"
                                                            + game.informationSet(
                                                                    new State(
                                                                            world.dealIndex(),
                                                                            "|BB:raise:9.0")))
                                            .get("raise:22.0");
                        if (p > 0) {
                            worlds.put(world.dealIndex(), p);
                            mass += p;
                        }
                    }
                assertEquals(row.decisionProbability(), mass, 1e-12);
                for (int i = 0; i < policies.size(); i++) {
                    var policy = policies.get(i);
                    var values =
                            i == 0
                                    ? row.values()
                                    : d.comparisons().get(i - 1).fixedQuestionReferenceValues();
                    for (String action : values.actionEvBb().keySet()) {
                        double ev = 0, continuationFold = 0, continuationCall = 0;
                        for (var world : worlds.entrySet()) {
                            var share =
                                    game.payoffs().get(world.getKey()).counts().estimate().shares();
                            double p = world.getValue() / mass;
                            if (action.equals("fold")) continue;
                            if (row.actor() == Seat.BTN) {
                                if (action.equals("call")) ev += p * (18.5 * share[3] - 6);
                                else {
                                    var response =
                                            policy.strategy()
                                                    .get(
                                                            "5:"
                                                                    + game.informationSet(
                                                                            new State(
                                                                                    world.getKey(),
                                                                                    "|BB:raise:9.0|BTN:raise:22.0")));
                                    ev +=
                                            p
                                                    * (response.get("fold") * 12.5
                                                            + response.get("call")
                                                                    * (44.5 * share[3] - 19));
                                }
                            } else if (!row.publicHistory().isEmpty())
                                ev += p * (44.5 * share[5] - 13);
                            else if (action.equals("call")) ev += p * (6.5 * share[5] - 2);
                            else {
                                var response =
                                        policy.strategy()
                                                .get(
                                                        "3:"
                                                                + game.informationSet(
                                                                        new State(
                                                                                world.getKey(),
                                                                                "|BB:raise:9.0")));
                                ev +=
                                        p
                                                * (response.get("fold") * 4.5
                                                        + response.get("call")
                                                                * (18.5 * share[5] - 8));
                                continuationFold += p * response.get("raise:22.0") * (-8);
                                continuationCall +=
                                        p * response.get("raise:22.0") * (44.5 * share[5] - 21);
                            }
                        }
                        ev += Math.max(continuationFold, continuationCall);
                        assertEquals(
                                ev,
                                values.actionEvBb().get(action),
                                1e-11,
                                row.informationSet() + ":" + action + ":" + i);
                    }
                }
            }
    }

    @Test
    void trainerIncludesLaterBigBlindDecisionWithoutLeakingPrivateWorlds() throws Exception {
        var trainer = new SixMaxSuppliedRangePreflopTrainer(study);
        var seen = new HashSet<String>();
        boolean later = false;
        for (int seed = 0; seed < 200; seed++) {
            var q = trainer.question(seed);
            seen.add(q.hero() + ":" + q.heroCards() + ":" + q.history().size());
            if (q.history().size() == 7) {
                later = true;
                assertEquals(Seat.BB, q.hero());
                assertEquals("9d 9h", q.heroCards());
                assertEquals(31.5, q.table().potBb());
                assertEquals(13, q.table().toCallBb());
                assertEquals(List.of("fold", "call"), q.legalActions());
            }
            assertEquals(SixMaxSuppliedRangePreflopGame.BELIEFS, q.beliefs());
            assertFalse(q.trainerAdmission());
            assertFalse(SixMaxTextureStudy.json(q).contains("dealtCombos"));
        }
        assertTrue(later);
        assertEquals(7, seen.size());
    }
}
