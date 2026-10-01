package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiwayRakeSensitivityTest {
    @Test
    void committedExactSixSeatPayoffsCanBeRescoredWithoutBoardEnumeration() throws IOException {
        String json;
        try (var input = getClass().getResourceAsStream("/six-seat-side-pot-pack.json")) {
            if (input == null) throw new AssertionError("Missing committed exact pack");
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var pack = MultiwayPackJson.readSidePot(json);
        var report =
                MultiwayRakeSensitivity.assess(
                        pack, new CashRakeRule(0.05, 1, true), 500, CfrSolver.Variant.CFR_PLUS);
        assertEquals(pack.nashConvBb(), report.noRakeNashConvBb(), 1e-12);
        assertEquals(0, report.sourceMaxTerminalPayoffSEBb());
        assertEquals(0, report.rakedMaxTerminalPayoffSEBb());
        assertTrue(report.transferredExpectedRakeBb() >= 0);
        assertTrue(report.transferredExpectedRakeBb() <= 1);
        assertEquals(
                pack.spot().deadMoneyBb() - report.transferredExpectedRakeBb(),
                report.transferredUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
        assertTrue(report.resolvedNashConvBb() < 0.01);
    }

    @Test
    void validatedNoRakePolicyLosesItsThinCallAfterRakeAndResolvedPolicyAdapts() {
        var spot =
                new MultiwaySidePotSpot(
                        "thin-call-rake-control",
                        List.of(PreflopAllInSpot.Seat.UTG, PreflopAllInSpot.Seat.BB),
                        List.of(
                                List.of(new WeightedCombo(Card.parse("AS"), Card.parse("AH"), 1)),
                                List.of(new WeightedCombo(Card.parse("KS"), Card.parse("KH"), 1))),
                        List.of(10.0, 1.0),
                        List.of(10.0, 10.0),
                        0);
        var pack =
                MultiwaySidePotPackBuilder.build(
                        spot,
                        250,
                        CfrSolver.Variant.CFR_PLUS,
                        (dealt, mask) ->
                                new MultiwayShowdownEstimate(
                                        new double[] {0.53, 0.47}, new double[2], 100),
                        MultiwaySolutionPack.SEEDED_MONTE_CARLO,
                        42,
                        "2026-10-01T00:00:00Z");
        var report =
                MultiwayRakeSensitivity.assess(
                        pack, new CashRakeRule(0.05, 1, true), 250, CfrSolver.Variant.CFR_PLUS);
        assertEquals(pack.nashConvBb(), report.noRakeNashConvBb(), 1e-12);
        assertEquals(1, report.transferredExpectedRakeBb(), 0.01);
        assertTrue(report.transferredNashConvBb() > 0.05);
        assertTrue(report.resolvedNashConvBb() < 0.01);
        assertEquals(0, report.resolvedExpectedRakeBb(), 0.01);
        assertEquals(-1, report.resolvedUtilitiesBb().get(1), 0.01);
        assertEquals(
                0,
                report.resolvedUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                0.01);
    }
}
