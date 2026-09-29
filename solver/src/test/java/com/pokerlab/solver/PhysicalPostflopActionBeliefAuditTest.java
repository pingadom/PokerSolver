package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PhysicalPostflopActionBeliefAuditTest {
    @Test
    void reachedRiverSampleIsRepeatableAndSharesHeldOutBoards() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var truth = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var report =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        assertEquals(
                report,
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
        assertTrue(report.attemptedDeals() > report.sampledBoards());
        assertTrue(report.checkdownReachRate() > 0 && report.checkdownReachRate() < 1);
        assertEquals(1_000, report.staticRange().heldOutBoards());
        assertEquals(report.staticRange().heldOutBoards(), report.preflopOnly().heldOutBoards());
        assertEquals(
                report.staticRange().heldOutBoards(), report.postflopConditioned().heldOutBoards());
        assertEquals(report.staticRange().heldOutBoards(), report.responseAware().heldOutBoards());
        assertEquals(
                report.staticRange().physicalOracleGainBb(),
                report.postflopConditioned().physicalOracleGainBb());
        assertEquals(
                report.staticRange().physicalOracleGainBb(),
                report.responseAware().physicalOracleGainBb());
        assertEquals(
                report.postflopConditioned().selectedGainBb()
                        - report.preflopOnly().selectedGainBb(),
                report.postflopMinusPreflop().postflopMinusPreflopBb(),
                1e-9);
        assertEquals(
                report.responseAware().selectedGainBb()
                        - report.postflopConditioned().selectedGainBb(),
                report.responseMinusPostflop().responseMinusPostflopBb(),
                1e-9);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalPostflopActionBeliefAudit.assess(
                                3,
                                1,
                                42,
                                profile,
                                truth,
                                truth,
                                PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
    }

    @Test
    void misspecifiedObservationKeepsTrueReachAndOracleFixed() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var truth = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var uniform =
                ButtonBigBlindRangeValidationFixture.assumedPostflopBelief(
                        ButtonBigBlindRangeValidationFixture.PostflopBeliefAssumption
                                .UNINFORMATIVE);
        var reversed =
                ButtonBigBlindRangeValidationFixture.assumedPostflopBelief(
                        ButtonBigBlindRangeValidationFixture.PostflopBeliefAssumption.REVERSED);
        var correct =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        43,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        var uninformative =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        43,
                        profile,
                        truth,
                        uniform,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        var wrong =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        43,
                        profile,
                        truth,
                        reversed,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        assertEquals(correct.attemptedDeals(), uninformative.attemptedDeals());
        assertEquals(correct.attemptedDeals(), wrong.attemptedDeals());
        assertEquals(correct.staticRange(), wrong.staticRange());
        assertEquals(correct.preflopOnly(), wrong.preflopOnly());
        assertEquals(correct.preflopOnly(), uninformative.postflopConditioned());
        assertEquals(0, uninformative.postflopMinusPreflop().postflopMinusPreflopBb(), 1e-12);
        assertEquals(correct.generatingPostflopBeliefHash(), wrong.generatingPostflopBeliefHash());
        assertNotEquals(correct.assumedPostflopBeliefHash(), wrong.assumedPostflopBeliefHash());
    }

    @Test
    void handDependentRiverCallsChangeValuesWithoutChangingReachedBoards() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var truth = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var call =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        var pair =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL);
        assertEquals(call.attemptedDeals(), pair.attemptedDeals());
        assertEquals(
                call.postflopConditioned().heldOutBoards(),
                pair.postflopConditioned().heldOutBoards());
        assertNotEquals(
                call.postflopConditioned().physicalOracleGainBb(),
                pair.postflopConditioned().physicalOracleGainBb());
    }

    @Test
    void incorrectAssumedRiverResponseKeepsPhysicalSampleAndOracleFixed() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var belief = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var truth = PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL;
        var correct =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000, 5, 42, profile, belief, belief, truth, truth);
        var wrong =
                PhysicalPostflopActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        belief,
                        belief,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        assertEquals(correct.attemptedDeals(), wrong.attemptedDeals());
        assertEquals(correct.staticRange(), wrong.staticRange());
        assertEquals(correct.preflopOnly(), wrong.preflopOnly());
        assertEquals(correct.postflopConditioned(), wrong.postflopConditioned());
        assertEquals(
                correct.responseAware().physicalOracleGainBb(),
                wrong.responseAware().physicalOracleGainBb());
        assertEquals(truth, wrong.responseModel());
        assertNotEquals(truth, wrong.assumedResponseModel());
    }
}
