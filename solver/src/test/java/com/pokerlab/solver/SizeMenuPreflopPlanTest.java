package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizeMenuPreflopPlanTest {
    static final Path INPUT = Path.of("../docs/data/supplied-flop-size-menu-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-size-menu-preflop-plan.json");
    static SixMaxSuppliedFlopSizeMenuStudy.Request request;
    static SixMaxSizeMenuPreflopPlan.Result owned;

    @BeforeAll
    static void planWithoutPhysicalEnumerationOrOptimization() throws Exception {
        request = SixMaxSuppliedFlopSizeMenuStudy.readRequest(INPUT);
        owned = SixMaxSizeMenuPreflopPlan.plan(request);
    }

    static SixMaxSizeMenuPreflopPlan.Question question(Seat seat, String hand, String history) {
        return owned.report().questions().stream()
                .filter(
                        q ->
                                q.hero() == seat
                                        && q.heroCards().equals(hand)
                                        && q.context().publicHistory().equals(history))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void completeSavedReportRebuildsWithoutNumericalWork() throws Exception {
        assertEquals(owned.report(), SixMaxSizeMenuPreflopPlan.replay(INPUT, REPORT).report());
        var w = owned.report().work();
        assertEquals(1609, w.symbolicTreeStates());
        assertEquals(17, w.preflopInformationSets());
        assertEquals(48, w.preflopActions());
        assertEquals(41, w.withinPlanLimitActions());
        assertEquals(7, w.overPlanLimitActions());
        assertEquals(0, w.enumeratedBoards());
        assertEquals(0, w.handEvaluations());
        assertEquals(0, w.lpSolves());
        assertEquals(0, w.cfrIterations());
        assertFalse(owned.report().trainerAdmission());
        assertEquals(64, owned.report().continuationPlanLimit());
        assertEquals(SixMaxSizeMenuPreflopPlan.CHANCE_SCOPE, owned.report().chanceMassScope());
        assertEquals(
                15_792_192,
                owned.report().estimatedPhysicalBudget().sourceBudget().enumeratedBoards());
    }

    @Test
    void allSymbolicInformationSetsAndMenusMatchThePublishedJointGame() throws Exception {
        var source =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-flop-size-menu-report.json.gz"),
                                        8 * 1024 * 1024),
                                SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(source.binding().budget(), owned.report().estimatedPhysicalBudget());
        for (var q : owned.report().questions()) {
            assertEquals(
                    source.candidate().strategy().get(q.informationSet()).keySet(),
                    new HashSet<>(q.actions().stream().map(a -> a.action()).toList()));
            for (var a : q.actions())
                for (var row : a.futureHeroDecisions())
                    assertEquals(
                            source.candidate().strategy().get(row.informationSet()).keySet(),
                            new HashSet<>(row.actions()));
        }
    }

    @Test
    void rootReraiseBranchesExceedTheOriginalLimitWithoutDroppingAnyMove() {
        for (String hand : List.of("7d 7h", "9d 9h", "Ac Jc")) {
            var q = question(Seat.BB, hand, "");
            assertEquals(
                    List.of("fold", "call", "raise:9.0"),
                    q.actions().stream().map(a -> a.action()).toList());
            assertEquals(
                    List.of(BigInteger.ONE, BigInteger.valueOf(6), BigInteger.valueOf(1296)),
                    q.actions().stream().map(a -> a.cartesianPlans()).toList());
            assertFalse(q.allMovesWithinPlanLimit());
            assertEquals("CONTINUATION_PLAN_LIMIT", q.actions().getLast().status());
            assertEquals(8, q.originalWorldIndices().size());
            assertEquals(1. / 3, q.priorChanceMass(), 1e-15);
            var fail =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> owned.requireCompleteMenuWithinPlanLimit(q.index()));
            assertTrue(fail.getMessage().startsWith("CONTINUATION_PLAN_LIMIT"));
        }
        for (String hand : List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th")) {
            var q = question(Seat.BTN, hand, "|BB:raise:9.0");
            assertEquals(BigInteger.valueOf(192), q.actions().getLast().cartesianPlans());
            assertEquals(6, q.originalWorldIndices().size());
        }
    }

    @Test
    void completeMenusPassOnlyTheStructuralGateAndUnknownQuestionsReject() {
        assertEquals(
                10,
                owned.report().questions().stream()
                        .filter(q -> q.allMovesWithinPlanLimit())
                        .count());
        var q = question(Seat.BB, "9d 9h", "|BB:raise:9.0|BTN:raise:22.0");
        assertSame(q, owned.requireCompleteMenuWithinPlanLimit(q.index()));
        assertEquals(BigInteger.valueOf(12), q.actions().getLast().cartesianPlans());
        assertThrows(
                IllegalArgumentException.class, () -> owned.requireCompleteMenuWithinPlanLimit(-1));
        assertThrows(
                IllegalArgumentException.class, () -> owned.requireCompleteMenuWithinPlanLimit(17));
    }

    @Test
    void plansShareEachHeroInformationSetAcrossAllHiddenWorldsAndRetainOffPolicyBranches() {
        for (var q : owned.report().questions()) {
            assertEquals(
                    q.originalWorldIndices().size(),
                    new HashSet<>(q.originalWorldIndices()).size());
            for (int i : q.originalWorldIndices())
                assertEquals(
                        q.heroCards(),
                        request.input().worlds().get(i).dealtCombos().get(q.hero().ordinal()));
            for (var a : q.actions()) {
                assertEquals(
                        a.futureHeroDecisions().size(),
                        a.futureHeroDecisions().stream()
                                .map(r -> r.informationSet())
                                .distinct()
                                .count());
                for (var row : a.futureHeroDecisions()) {
                    assertTrue(row.informationSet().startsWith(q.hero().ordinal() + ":"));
                    assertTrue(row.informationSet().contains(q.heroCards()));
                    assertNotEquals(q.informationSet(), row.informationSet());
                }
            }
        }
        assertTrue(
                question(Seat.BB, "9d 9h", "").actions().getLast().futureHeroDecisions().stream()
                        .anyMatch(r -> r.informationSet().endsWith("|BTN:raise:100.0")));
    }

    @Test
    void publicContextsKeepSixSeatsActualRaiseCostsAndSunkChips() throws Exception {
        var q = question(Seat.BB, "9d 9h", "");
        var table = q.context();
        assertEquals(4.5, table.potBb());
        assertEquals(2., table.toCallBb());
        assertEquals(List.of(0., 2., 8.), table.actions().stream().map(a -> a.amountBb()).toList());
        assertEquals(6, table.players().size());
        assertEquals("ACTING", table.players().get(5).status());
        for (var p : table.players()) assertEquals(100, p.committedBb() + p.remainingStackBb());
        var btn = question(Seat.BTN, "Ah Kh", "|BB:raise:9.0").context();
        assertEquals(12.5, btn.potBb());
        assertEquals(6., btn.toCallBb());
        assertEquals(19., btn.actions().getLast().amountBb());
        var tree = SixMaxTexturePayoffTable.mapper().valueToTree(table);
        assertFalse(tree.has("board"));
        assertFalse(tree.has("worlds"));
        assertFalse(tree.has("heroCards"));
        for (var p : tree.get("players")) assertFalse(p.has("cards"));
    }

    @Test
    void physicalFlopBlockersDoNotRemoveOriginalPreflopWorlds() {
        var worlds = new ArrayList<>(request.input().worlds());
        var old = worlds.getFirst();
        var combos = new ArrayList<>(old.dealtCombos());
        combos.set(0, combos.getFirst().split(" ")[0] + " 6d");
        worlds.set(0, new SixMaxSuppliedRangePreflopGame.World(combos, old.weight()));
        var input =
                new SixMaxSuppliedRangePreflopGame.Input(
                        request.input().schemaVersion(), request.input().specification(), worlds);
        var changed =
                SixMaxSizeMenuPreflopPlan.plan(
                                new SixMaxSuppliedFlopSizeMenuStudy.Request(
                                        request.schemaVersion(),
                                        input,
                                        request.flop(),
                                        request.menu()))
                        .report();
        assertEquals(24, changed.estimatedPhysicalBudget().sourceBudget().jointDeals());
        assertEquals(23, changed.estimatedPhysicalBudget().compatibleDeals());
        assertEquals(1557, changed.work().symbolicTreeStates());
        assertEquals(17, changed.questions().size());
        assertEquals(48, changed.work().preflopActions());
        assertEquals(7, changed.work().overPlanLimitActions());
    }

    @Test
    void changedJointWeightsChangeChanceMassWithoutInventingPolicyReach() {
        var worlds = new ArrayList<>(request.input().worlds());
        var first = worlds.getFirst();
        worlds.set(0, new SixMaxSuppliedRangePreflopGame.World(first.dealtCombos(), 2));
        var input =
                new SixMaxSuppliedRangePreflopGame.Input(
                        request.input().schemaVersion(), request.input().specification(), worlds);
        var changed =
                SixMaxSizeMenuPreflopPlan.plan(
                                new SixMaxSuppliedFlopSizeMenuStudy.Request(
                                        request.schemaVersion(),
                                        input,
                                        request.flop(),
                                        request.menu()))
                        .report();
        assertNotEquals(owned.report().sourceRequestHash(), changed.sourceRequestHash());
        var hand = first.dealtCombos().get(5);
        var q =
                changed.questions().stream()
                        .filter(
                                r ->
                                        r.hero() == Seat.BB
                                                && r.heroCards().equals(hand)
                                                && r.context().publicHistory().isEmpty())
                        .findFirst()
                        .orElseThrow();
        assertEquals(9. / 25, q.priorChanceMass(), 1e-15);
        assertEquals(BigInteger.valueOf(1296), q.actions().getLast().cartesianPlans());
    }

    @Test
    void alteredPlanCountsGatesOrNumericalWorkRejectBeforeAnyAuthorityIsIssued() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of("continuationPlanLimit", "trainerAdmission", "chanceMassScope")) {
            var n = (ObjectNode) mapper.valueToTree(owned.report());
            if (field.equals("trainerAdmission")) n.put(field, true);
            else if (field.equals("chanceMassScope")) n.put(field, "ON_POLICY");
            else n.put(field, 1296);
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxSizeMenuPreflopPlan.Report.class));
        }
        var a = question(Seat.BB, "9d 9h", "").actions().getLast();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSizeMenuPreflopPlan.Action(
                                a.action(),
                                a.futureHeroDecisions(),
                                BigInteger.valueOf(64),
                                a.enumerationPrechargeUnits(),
                                true,
                                "WITHIN_PLAN_LIMIT_ONLY"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxSizeMenuPreflopPlan.Work(1609, 17, 48, 41, 7, 0, 0, 1, 0));
    }

    @Test
    void ownedResultAndNestedReportsRemainImmutable() {
        assertTrue(
                Arrays.stream(SixMaxSizeMenuPreflopPlan.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class, () -> owned.report().questions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().questions().getFirst().originalWorldIndices().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        question(Seat.BB, "9d 9h", "")
                                .actions()
                                .getLast()
                                .futureHeroDecisions()
                                .clear());
    }

    @Test
    void commandsPreserveSourcesReplayWholeContextsAndSupportCompressedOutput(@TempDir Path temp)
            throws Exception {
        Path output = temp.resolve("plan.json.gz");
        SixMaxSizeMenuPreflopPlan.write(output, owned);
        assertEquals(owned.report(), SixMaxSizeMenuPreflopPlan.replay(INPUT, output).report());
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSizeMenuPreflopPlan.write(output, owned));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuPreflopPlanMain.main(
                                new String[] {"plan", INPUT.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuPreflopPlanMain.main(
                                new String[] {"plan", INPUT.toString(), INPUT.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuPreflopPlanMain.main(
                                new String[] {"solve", INPUT.toString(), output.toString()}));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var n = (ObjectNode) mapper.valueToTree(owned.report());
        ((ObjectNode) n.withArray("questions").get(8).get("context")).put("potBb", 100);
        Path forged = temp.resolve("forged.json");
        Files.write(forged, mapper.writeValueAsBytes(n));
        assertThrows(Exception.class, () -> SixMaxSizeMenuPreflopPlan.replay(INPUT, forged));
        String source = Files.readString(INPUT);
        for (String text :
                List.of(
                        source + " {}",
                        source.replaceFirst("\\{", "{\"extra\":0,"),
                        source.replaceFirst("\\{", "{\"schemaVersion\":\"wrong\","))) {
            Path invalid = temp.resolve("invalid.json");
            Files.writeString(invalid, text);
            assertThrows(
                    Exception.class, () -> SixMaxSuppliedFlopSizeMenuStudy.readRequest(invalid));
        }
    }
}
