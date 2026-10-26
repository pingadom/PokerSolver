package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConditionalPairwiseRegretTest {
    @Test
    void dominantActionHasZeroRegretDespiteItsOwnEvWidth() {
        var result =
                ConditionalPairwiseRegret.bounds(
                        List.of(
                                new ConditionalPairwiseRegret.Interval("fold", -3, -3),
                                new ConditionalPairwiseRegret.Interval("call", -2.65, -2.23)));
        assertEquals(0, result.get(1).lower());
        assertEquals(0, result.get(1).upper());
        assertTrue(result.getFirst().lower() > .34);
        assertTrue(result.getFirst().upper() > .77);
    }

    @Test
    void allVerticesOf21952ThreeActionIntervalBoxesContainThePointwiseRegret() {
        var intervals = new ArrayList<double[]>();
        for (int a = -3; a <= 3; a++)
            for (int b = a; b <= 3; b++) intervals.add(new double[] {a, b});
        long menus = 0, points = 0;
        for (var a : intervals)
            for (var b : intervals)
                for (var c : intervals) {
                    var menu = List.of(a, b, c);
                    var bounds =
                            ConditionalPairwiseRegret.bounds(
                                    List.of(
                                            new ConditionalPairwiseRegret.Interval("a", a[0], a[1]),
                                            new ConditionalPairwiseRegret.Interval("b", b[0], b[1]),
                                            new ConditionalPairwiseRegret.Interval(
                                                    "c", c[0], c[1])));
                    menus++;
                    for (int mask = 0; mask < 8; mask++) {
                        double[] values = new double[3];
                        for (int i = 0; i < 3; i++) values[i] = menu.get(i)[(mask >> i) & 1];
                        double best = Arrays.stream(values).max().orElseThrow();
                        for (int i = 0; i < 3; i++) {
                            double regret = best - values[i];
                            assertTrue(
                                    bounds.get(i).lower() <= regret
                                            && regret <= bounds.get(i).upper());
                            points++;
                        }
                    }
                }
        assertEquals(21952, menus);
        assertEquals(526848, points);
    }

    @Test
    void binary64SubtractionIsPaddedOutwardsAgainstExactArithmetic() {
        double[][] values = {
            {.1, .3},
            {-1e-20, .1},
            {Math.nextUp(1.), 1.},
            {1e100, Math.nextDown(1e100)},
            {Double.MIN_VALUE, 0}
        };
        for (var p : values) {
            var result =
                    ConditionalPairwiseRegret.bounds(
                            List.of(
                                    new ConditionalPairwiseRegret.Interval("a", p[0], p[0]),
                                    new ConditionalPairwiseRegret.Interval("b", p[1], p[1])));
            var exact = new BigDecimal(p[1]).subtract(new BigDecimal(p[0])).max(BigDecimal.ZERO);
            assertTrue(new BigDecimal(result.getFirst().lower()).compareTo(exact) <= 0);
            assertTrue(new BigDecimal(result.getFirst().upper()).compareTo(exact) >= 0);
        }
    }

    @Test
    void invalidMenusNonfiniteValuesOverflowAndMutableResultsReject() {
        var one = new ConditionalPairwiseRegret.Interval("x", 0, 1);
        for (var menu :
                List.of(
                        List.<ConditionalPairwiseRegret.Interval>of(),
                        List.of(one),
                        List.of(one, one)))
            assertThrows(
                    IllegalArgumentException.class, () -> ConditionalPairwiseRegret.bounds(menu));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConditionalPairwiseRegret.Interval("x", Double.NaN, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ConditionalPairwiseRegret.Interval("x", 2, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ConditionalPairwiseRegret.bounds(
                                List.of(
                                        new ConditionalPairwiseRegret.Interval(
                                                "x", -Double.MAX_VALUE, -Double.MAX_VALUE),
                                        new ConditionalPairwiseRegret.Interval(
                                                "y", Double.MAX_VALUE, Double.MAX_VALUE))));
        var result =
                ConditionalPairwiseRegret.bounds(
                        List.of(one, new ConditionalPairwiseRegret.Interval("y", 0, 1)));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }
}
