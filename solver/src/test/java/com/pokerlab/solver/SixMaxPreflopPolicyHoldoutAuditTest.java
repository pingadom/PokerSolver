package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopPolicyHoldoutAuditTest {
    private static final SixMaxPreflopBetting.Rules RULES =
            new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));

    @Test
    void independentPayoffsChangeFrozenPolicyValueAndBestResponse() {
        var training = game(ranges(), RULES, equalShareOracle());
        var holdout = game(ranges(), RULES, firstActiveWinsOracle());
        var policy = new MultiPlayerCfrSolver<>(training, CfrSolver.Variant.CFR_PLUS).solve(8);

        var report = SixMaxPreflopPolicyHoldoutAudit.assess(training, holdout, policy);
        var independentlyEvaluated = MultiPlayerStrategyEvaluator.utilities(holdout, policy);
        for (int seat = 0; seat < 6; seat++) {
            assertEquals(
                    independentlyEvaluated[seat],
                    report.holdout().profileUtilitiesBb().get(seat),
                    1e-9);
            assertEquals(
                    report.holdout().profileUtilitiesBb().get(seat)
                            - report.training().profileUtilitiesBb().get(seat),
                    report.profileUtilityDriftBb().get(seat),
                    1e-9);
            assertTrue(report.holdout().deviationGainsBb().get(seat) >= 0);
        }
        assertTrue(report.maximumAbsoluteProfileUtilityDriftBb() > 0.01);
        assertEquals(0, report.trainingMaximumTerminalPayoffStandardErrorBb());
        assertEquals(0, report.holdoutMaximumTerminalPayoffStandardErrorBb());
        assertEquals(
                report.holdout().nashConvBb() - report.training().nashConvBb(),
                report.deviationGainDriftBb().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
    }

    @Test
    void rejectsDifferentRulesAndPhysicalChanceSupport() {
        var training = game(ranges(), RULES, equalShareOracle());
        var policy = new MultiPlayerCfrSolver<>(training, CfrSolver.Variant.CFR_PLUS).solve(1);
        var otherRules =
                game(
                        ranges(),
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                        equalShareOracle());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopPolicyHoldoutAudit.assess(training, otherRules, policy));

        var otherRanges = new ArrayList<>(ranges());
        otherRanges.set(1, List.of(new WeightedCombo(Card.parse("8S"), Card.parse("8H"), 1)));
        var otherDeal = game(otherRanges, RULES, equalShareOracle());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopPolicyHoldoutAudit.assess(training, otherDeal, policy));
    }

    private static SixMaxPreflopCheckdownGame game(
            List<List<WeightedCombo>> ranges,
            SixMaxPreflopBetting.Rules rules,
            MultiwayShowdownOracle oracle) {
        return new SixMaxPreflopCheckdownGame(rules, ranges, CashRakeRule.none(), oracle);
    }

    private static List<List<WeightedCombo>> ranges() {
        return List.of(
                List.of(combo("AS", "AH")),
                List.of(combo("KS", "KH")),
                List.of(combo("QS", "QH")),
                List.of(combo("JS", "JH")),
                List.of(combo("TS", "TH")),
                List.of(combo("9S", "9H")));
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static MultiwayShowdownOracle equalShareOracle() {
        return (hands, mask) -> {
            double[] shares = new double[6];
            for (int seat = 0; seat < 6; seat++)
                if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / Integer.bitCount(mask);
            return MultiwayShowdownEstimate.certain(shares);
        };
    }

    private static MultiwayShowdownOracle firstActiveWinsOracle() {
        return (hands, mask) -> {
            double[] shares = new double[6];
            shares[Integer.numberOfTrailingZeros(mask)] = 1;
            return MultiwayShowdownEstimate.certain(shares);
        };
    }
}
