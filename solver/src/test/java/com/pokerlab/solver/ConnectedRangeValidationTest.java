package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectedRangeValidationTest {
    @Test
    void independentRangeHasNineLegalDealsAndOwnGameIdentity() {
        var game = ButtonBigBlindRangeValidationFixture.createBucketed();
        var deals = game.chanceOutcomes(game.initialState());
        assertEquals(9, deals.size());
        assertEquals(1, deals.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertNotEquals(
                ButtonBigBlindPhysicalDeckFixture.createBucketed().contentHash(),
                game.contentHash());
        assertTrue(
                deals.stream()
                        .noneMatch(
                                deal ->
                                        deal.state()
                                                .button()
                                                .conflictsWith(deal.state().bigBlind())));
    }

    @Test
    void streetDeviationGroupsReachableObservationsAndIsSeeded() {
        var game = ButtonBigBlindRangeValidationFixture.createBucketed();
        var solution =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(50);
        for (var street : PhysicalConnectedStreetDeviationAudit.Street.values()) {
            int seed = 45 + street.ordinal();
            var report =
                    PhysicalConnectedStreetDeviationAudit.assess(
                            game, solution, street, 2_000, 2, seed);
            assertEquals(
                    report,
                    PhysicalConnectedStreetDeviationAudit.assess(
                            game, solution, street, 2_000, 2, seed));
            assertEquals(game.contentHash(), report.gameHash());
            assertEquals(street, report.street());
            assertTrue(
                    report.reachedStreet() > 0 && report.reachedStreet() < report.attemptedDeals());
            assertEquals(
                    report.reachedStreet(),
                    report.decisions().stream()
                            .mapToInt(PhysicalConnectedStreetDeviationAudit.Decision::sampledStates)
                            .sum());
            assertEquals(
                    1,
                    report.decisions().stream()
                            .mapToDouble(
                                    PhysicalConnectedStreetDeviationAudit.Decision
                                            ::reachedStreetWeight)
                            .sum(),
                    1e-12);
            int supported =
                    report.decisions().stream()
                            .filter(decision -> decision.heldOutStates() >= 10)
                            .mapToInt(PhysicalConnectedStreetDeviationAudit.Decision::sampledStates)
                            .sum();
            assertEquals(supported, report.sampledStatesWithHeldOutSupport(10));
            assertEquals(
                    (double) supported / report.reachedStreet(),
                    report.sampledStateSupportRate(10),
                    1e-12);
            assertThrows(IllegalArgumentException.class, () -> report.sampledStateSupportRate(0));
            assertTrue(
                    report.decisions().stream()
                            .allMatch(
                                    decision ->
                                            decision.informationSet().startsWith("B:F:")
                                                    && decision.discoveryStates()
                                                                    + decision.heldOutStates()
                                                            == decision.sampledStates()
                                                    && decision.discoveryStates()
                                                            >= decision.heldOutStates()
                                                    && decision.discoveryStates()
                                                                    - decision.heldOutStates()
                                                            <= 1
                                                    && decision.policyBetProbability() >= 0
                                                    && decision.policyBetProbability() <= 1
                                                    && decision.discoveryEstimatedImprovementBb()
                                                            >= -1e-12
                                                    && (decision.heldOutStates() == 0
                                                            || Double.isFinite(
                                                                    decision
                                                                            .heldOutPolicyGainBb()))));
            report.decisions().stream()
                    .filter(decision -> decision.heldOutStates() > 0)
                    .forEach(
                            decision -> {
                                double check = decision.heldOutCheckUtilityBb();
                                double bet = decision.heldOutBetUtilityBb();
                                double policy =
                                        decision.policyBetProbability() * bet
                                                + (1 - decision.policyBetProbability()) * check;
                                double selected =
                                        decision.selectedAction().equals("b") ? bet : check;
                                assertEquals(
                                        selected - policy, decision.heldOutPolicyGainBb(), 1e-12);
                            });
        }
    }
}
