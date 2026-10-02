package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SixMaxJointDealSamplerTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<List<WeightedCombo>> blockerRanges() {
        return List.of(
                List.of(combo("AS", "AH", 1), combo("KS", "KH", 3)),
                List.of(combo("AS", "AD", 1), combo("QS", "QH", 1)),
                List.of(combo("JS", "JH", 1)),
                List.of(combo("TS", "TH", 1)),
                List.of(combo("9S", "9H", 1)),
                List.of(combo("8S", "8H", 1)));
    }

    private static List<List<WeightedCombo>> wideDisjointRanges() {
        return List.of(
                List.of(combo("AS", "AH", 1), combo("AD", "AC", 1)),
                List.of(combo("KS", "KH", 1), combo("KD", "KC", 1)),
                List.of(combo("QS", "QH", 1), combo("QD", "QC", 1)),
                List.of(combo("JS", "JH", 1), combo("JD", "JC", 1)),
                List.of(combo("TS", "TH", 1), combo("TD", "TC", 1)),
                List.of(combo("9S", "9H", 1), combo("9D", "9C", 1)));
    }

    @Test
    void rejectionSamplingMatchesBlockerConditionedProductWeights() {
        var sample = SixMaxJointDealSampler.sample(blockerRanges(), 7000, 10000, 711);
        assertEquals(sample, SixMaxJointDealSampler.sample(blockerRanges(), 7000, 10000, 711));
        assertEquals(7000, sample.acceptedDraws());
        assertTrue(sample.rejectedDraws() > 0);
        assertEquals(3, sample.deals().size());
        Map<String, Double> shares =
                sample.deals().stream()
                        .collect(
                                Collectors.toMap(
                                        deal ->
                                                deal.hands().get(UTG.ordinal()).key()
                                                        + "/"
                                                        + deal.hands().get(HJ.ordinal()).key(),
                                        deal -> deal.occurrences() / 7000.0));
        assertEquals(1.0 / 7, shares.get("Ah As/Qh Qs"), 0.02);
        assertEquals(3.0 / 7, shares.get("Kh Ks/Ad As"), 0.02);
        assertEquals(3.0 / 7, shares.get("Kh Ks/Qh Qs"), 0.02);
    }

    @Test
    void exactAndSampledChanceModelsBuildTheSameBoundedPublicGame() {
        var ranges = wideDisjointRanges();
        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var exact =
                new SixMaxPreflopCheckdownGame(
                        rules,
                        ranges,
                        CashRakeRule.none(),
                        new SharedBoardMultiwayShowdownOracle(100, 2026));
        assertEquals(
                SixMaxPreflopCheckdownGame.ChanceModel.EXACT_RANGE_PRODUCT, exact.chanceModel());
        assertEquals(0, exact.chanceSamples());
        assertEquals(64, exact.chanceOutcomes(exact.initialState()).size());
        for (var outcome : exact.chanceOutcomes(exact.initialState()))
            assertEquals(1.0 / 64, outcome.probability(), 1e-12);
        var sample = SixMaxJointDealSampler.sample(ranges, 64, 64, 42);
        assertTrue(sample.deals().size() < exact.chanceOutcomes(exact.initialState()).size());
        var oracle = new SharedBoardMultiwayShowdownOracle(100, 2026);
        var game =
                SixMaxPreflopCheckdownGame.fromSampledDeals(
                        rules, sample, CashRakeRule.none(), oracle);
        assertEquals(
                SixMaxPreflopCheckdownGame.ChanceModel.EMPIRICAL_JOINT_DEALS, game.chanceModel());
        assertEquals(64, game.chanceSamples());
        assertEquals(exact.treeSummary(), game.treeSummary());
        assertEquals(sample.deals().size(), game.chanceOutcomes(game.initialState()).size());
        for (int index = 0; index < sample.deals().size(); index++)
            assertEquals(
                    sample.deals().get(index).occurrences() / 64.0,
                    game.chanceOutcomes(game.initialState()).get(index).probability(),
                    1e-12);
        assertEquals(100L * sample.deals().size(), oracle.boardsEvaluated());
        assertEquals(
                1,
                game.chanceOutcomes(game.initialState()).stream()
                        .mapToDouble(ChanceOutcome::probability)
                        .sum(),
                1e-12);
        assertTrue(game.maximumTerminalPayoffStandardErrorBb() > 0);
        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2);
        var audit = SixMaxPreflopConvergenceAudit.run(game, List.of(1));
        assertEquals(game.chanceModel(), audit.getFirst().chanceModel());
        assertEquals(64, audit.getFirst().chanceSamples());
        assertTrue(solution.strategy().size() > 6);
        var question = new SixMaxPreflopResearchTrainer(game, solution).question(17);
        assertEquals(game.chanceModel(), question.chanceModel());
        assertEquals(64, question.chanceSamples());
        assertEquals("VALIDATION_ONLY", question.publicationStatus());
    }

    @Test
    void rejectsImpossibleOrOversizedSampledSupportsBeforePayoffWork() {
        var blocked = new ArrayList<>(blockerRanges());
        blocked.set(HJ.ordinal(), List.of(combo("AS", "AD", 1)));
        blocked.set(UTG.ordinal(), List.of(combo("AS", "AH", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxJointDealSampler.sample(blocked, 2, 10, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxJointDealSampler.sample(blockerRanges(), 2, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxJointDealSampler.Sample(
                                1,
                                2,
                                0,
                                List.of(
                                        new SixMaxJointDealSampler.JointDeal(
                                                SixMaxJointDealSampler.sample(
                                                                blockerRanges(), 1, 10, 1)
                                                        .deals()
                                                        .getFirst()
                                                        .hands(),
                                                1))));

        var fullSupport = SixMaxJointDealSampler.sample(wideDisjointRanges(), 1000, 1000, 2026);
        assertEquals(64, fullSupport.deals().size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopCheckdownGame.fromSampledDeals(
                                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                                fullSupport,
                                CashRakeRule.none(),
                                (hands, mask) -> {
                                    throw new AssertionError("Payoff work must not begin");
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopCheckdownGame(
                                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                                wideDisjointRanges(),
                                CashRakeRule.none(),
                                (hands, mask) -> {
                                    throw new AssertionError("Payoff work must not begin");
                                }));
    }
}
