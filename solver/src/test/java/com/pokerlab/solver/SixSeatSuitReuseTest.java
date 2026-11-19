package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatSuitReuseTest {
    private static final List<String> EXPANDED =
            List.of("AA", "KK", "QcQd", "JcJd", "TcTd", "9c9d");
    private static final List<String> EQUIVALENT =
            List.of("AcAh@0.5,AdAs", "KdKs,KcKh", "QcQd", "JcJd", "TcTd", "9c9d");

    @TempDir Path temp;

    private static SixSeatRangePreflight.Result input(List<String> range) {
        return SixSeatRangePreflight.plan(range, SixSeatRangePreflight.Limits.defaults());
    }

    @Test
    void thirtySixWorldsFitOnlyWhenPhysicalRepresentativeWorkIsCharged() {
        var input = input(EXPANDED);
        assertEquals(36, input.deals().size());
        var budget = new SixSeatPayoffWork.Budget(7896096, 47376576);
        var error =
                assertThrows(
                        SixSeatPayoffWork.BudgetExceeded.class,
                        () ->
                                SixSeatPayoffWork.plan(
                                        input, SixSeatPayoffWork.Configuration.exact(), budget));
        assertEquals(23688288, error.required().boardEvaluations());
        var plan =
                SixSeatPayoffWork.plan(
                        input, SixSeatPayoffWork.Configuration.exactSuitReuse(), budget);
        assertEquals(12, plan.evaluatedDeals());
        assertEquals(36, plan.work().jointDeals());
        assertEquals(2052, plan.work().activeSubsetEntries());
        assertEquals(7896096, plan.work().boardEvaluations());
        assertEquals(47376576, plan.work().handEvaluations());
        assertThrows(
                SixSeatPayoffWork.BudgetExceeded.class,
                () ->
                        SixSeatPayoffWork.plan(
                                input,
                                SixSeatPayoffWork.Configuration.exactSuitReuse(),
                                new SixSeatPayoffWork.Budget(7896095, 47376576)));
    }

    @Test
    void canonicalizationKeepsSeatOrderAndCrossSeatSuitOverlap() {
        var world =
                input(List.of("AcAh", "KdKs", "QcQd", "JcJd", "TcTd", "9c9d")).deals().getFirst();
        var equivalent =
                input(List.of("AdAs", "KcKh", "QcQd", "JcJd", "TcTd", "9c9d")).deals().getFirst();
        var changedOverlap =
                input(List.of("AcAh", "KcKh", "QcQd", "JcJd", "TcTd", "9c9d")).deals().getFirst();
        var key = SixSeatSuitClasses.canonicalKey(world.hands());
        assertEquals(key, SixSeatSuitClasses.canonicalKey(equivalent.hands()));
        assertNotEquals(key, SixSeatSuitClasses.canonicalKey(changedOverlap.hands()));
        var reordered = new ArrayList<>(world.hands());
        Collections.swap(reordered, 0, 1);
        assertNotEquals(key, SixSeatSuitClasses.canonicalKey(reordered));
    }

    @Test
    void reusePreservesEveryWeightedWorldAndMatchesAllDirectMaskShares() {
        var input = input(EQUIVALENT);
        var budget = SixSeatPayoffWork.Budget.defaults();
        var direct =
                SixSeatPayoffWork.plan(input, SixSeatPayoffWork.Configuration.exact(), budget)
                        .execute();
        var plan =
                SixSeatPayoffWork.plan(
                        input, SixSeatPayoffWork.Configuration.exactSuitReuse(), budget);
        var reused = plan.execute();
        assertSame(input, plan.input());
        assertEquals(4, input.deals().size());
        assertEquals(2, plan.evaluatedDeals());
        assertEquals(228, reused.rows().size());
        assertEquals(4L * 658008, reused.boardsFromPerDealEstimates());
        assertEquals(2L * 658008, reused.boardsFromEvaluatedDeals());
        assertNotEquals(direct.plan().contentHash(), plan.contentHash());
        for (var deal : input.deals()) {
            for (int mask = 1; mask < 64; mask++) {
                if (Integer.bitCount(mask) < 2) continue;
                assertArrayEquals(
                        direct.oracle().estimate(deal.hands(), mask).shares(),
                        reused.oracle().estimate(deal.hands(), mask).shares(),
                        1e-12);
            }
        }
        var spot =
                input.toResearchSpot(
                        "suit-reuse",
                        new SixMaxPreflopBetting.Rules(2, .5, List.of(2.)),
                        CashRakeRule.none());
        var before = spot.game(direct.oracle());
        var after = spot.game(reused.oracle());
        assertEquals(
                before.chanceOutcomes(before.initialState()),
                after.chanceOutcomes(after.initialState()));
        assertTrue(
                after.chanceOutcomes(after.initialState()).stream()
                        .anyMatch(o -> o.probability() < .2));
    }

    @Test
    void explicitPayoffCliPlansExpandedInputAndKeepsValidationOnly() throws Exception {
        var out = temp.resolve("plan.json");
        SixSeatPayoffWorkMain.main(
                new String[] {
                    "plan",
                    out.toString(),
                    "exact-suit-reuse",
                    "7896096",
                    "47376576",
                    "AA",
                    "KK",
                    "QcQd",
                    "JcJd",
                    "TcTd",
                    "9c9d"
                });
        var report = SixMaxTexturePayoffTable.mapper().readTree(Files.readAllBytes(out));
        assertFalse(report.get("trainerAdmission").asBoolean());
        assertEquals(12, report.get("plannedEvaluatedDeals").asInt());
        assertEquals(36, report.at("/plannedWork/jointDeals").asInt());
        assertFalse(report.get("executed").asBoolean());
    }
}
