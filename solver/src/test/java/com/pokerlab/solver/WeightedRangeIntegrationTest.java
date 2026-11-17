package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WeightedRangeIntegrationTest {
    @Test
    void exactPhysicalInputsBuildTheExistingSixSeatGameWithoutTrainingOrShowdownEnumeration() {
        var ranges =
                List.of("2c2d", "3c3d", "4c4d", "5c5d", "6c6d", "7c7d").stream()
                        .map(s -> WeightedRangeNotation.compile(s).combos())
                        .toList();
        var calls = new AtomicInteger();
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            calls.incrementAndGet();
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & (1 << i)) != 0)
                                    shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var roots = game.chanceOutcomes(game.initialState());
        assertEquals(1, roots.size());
        assertEquals(1., roots.getFirst().probability());
        assertEquals(57, calls.get());
        assertEquals(6, game.dealtHands(roots.getFirst().state()).size());
    }

    @Test
    void broaderCompiledRangesKeepTheOriginalExactGameCapAndCannotSilentlyTruncate() {
        var ranges =
                List.of("22", "33", "44", "55", "66", "77").stream()
                        .map(s -> WeightedRangeNotation.compile(s).combos())
                        .toList();
        var calls = new AtomicInteger();
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new SixMaxPreflopCheckdownGame(
                                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                                        ranges,
                                        CashRakeRule.none(),
                                        (hands, mask) -> {
                                            calls.incrementAndGet();
                                            throw new AssertionError(
                                                    "Payoff oracle must not run beyond the unchanged support cap");
                                        }));
        assertTrue(error.getMessage().contains("64"));
        assertEquals(0, calls.get());
        assertEquals(64, SixMaxPreflopCheckdownGame.MAX_JOINT_DEALS);
    }

    @Test
    void weightedRangesFeedTheExistingSeededJointSamplerWithoutChangingItsEmpiricalMeaning() {
        var ranges =
                List.of("22@0.5", "33@0.5", "44@0.5", "55@0.5", "66@0.5", "77@0.5").stream()
                        .map(s -> WeightedRangeNotation.compile(s).combos())
                        .toList();
        var a = SixMaxJointDealSampler.sample(ranges, 32, 32, 2026L);
        var b = SixMaxJointDealSampler.sample(ranges, 32, 32, 2026L);
        assertEquals(a, b);
        assertEquals(32, a.acceptedDraws());
        assertEquals(0, a.rejectedDraws());
        assertEquals(
                32,
                a.deals().stream().mapToInt(SixMaxJointDealSampler.JointDeal::occurrences).sum());
        for (var deal : a.deals())
            for (int seat = 0; seat < 6; seat++)
                assertTrue(ranges.get(seat).contains(deal.hands().get(seat)));
    }
}
