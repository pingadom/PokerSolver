package com.pokerlab.solver;

import static com.pokerlab.solver.FiniteTwoPlayerConditionalActionIntervalsTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiniteTwoPlayerReachRejectionTest {
    static FiniteTwoPlayerConditionalActionIntervals.Rejected reject(
            int hero, boolean mandatory, double floor) {
        return assertThrows(
                FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                () ->
                        FiniteTwoPlayerConditionalActionIntervals.solve(
                                posteriorGame(hero, mandatory, false),
                                question(hero),
                                1e-8,
                                floor));
    }

    @Test
    void zeroReachRetainsOriginalCertificateAndUnnormalizedWorldsForEitherActor() throws Exception {
        for (int hero : List.of(3, 5)) {
            var error = reject(hero, false, .01);
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                    error.reason());
            var audit = error.reachFailure().orElseThrow();
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.ReachFailureStatus
                            .WITNESS_BELOW_REQUIRED_FLOOR,
                    audit.status());
            assertEquals(-1e-8, audit.certifiedReachLowerBound(), 1e-12);
            assertEquals(.01, audit.requiredMinimumReach());
            assertFalse(audit.trainerAdmission());
            assertEquals(1, audit.work().intervalLpSolves());
            assertEquals(
                    List.of(new FiniteTwoPlayerSequenceForm.OwnAction(hero + ":root", "enter")),
                    audit.fixedHeroPast());
            var w = audit.minimumReachWitness();
            assertEquals(0, w.questionReach(), 1e-12);
            assertEquals(2, w.masses().size());
            for (var mass : w.masses()) {
                assertEquals(.5, mass.chanceWeight());
                assertEquals(0, mass.behavioralWeight(), 1e-12);
                assertEquals(0, mass.originalRealizationWeight(), 1e-12);
            }
            assertTrue(
                    w.globalHeroBestResponse()
                            <= audit.globalHeroUpperValue() + audit.securitySlack() + 1e-8);
            assertTrue(w.certificate().maximumPrimalViolation() <= 1e-8);
            assertTrue(w.certificate().maximumDualViolation() <= 1e-8);
            assertTrue(w.certificate().absoluteDualityGap() <= 1e-8);
            assertEquals(
                    audit.denominator().constant() - w.certificate().dualValue() - 1e-8,
                    audit.certifiedReachLowerBound(),
                    1e-12);
            assertEquals(
                    w.questionReach(),
                    audit.denominator().constant() - w.certificate().primalValue(),
                    1e-12);
            var json = SixMaxTextureStudy.json(audit);
            assertFalse(json.contains("posterior"));
            assertFalse(json.contains("conditionalHeroBestResponse"));
            assertEquals(
                    audit,
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    json,
                                    FiniteTwoPlayerConditionalActionIntervals.ReachFailureAudit
                                            .class));
        }
    }

    @Test
    void conservativePaddingIsNotMisreportedAsAnOpponentBelowTheFloor() {
        var boundary = reject(5, true, .5).reachFailure().orElseThrow();
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.ReachFailureStatus
                        .CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR,
                boundary.status());
        assertEquals(.5, boundary.minimumReachWitness().questionReach(), 1e-12);
        assertEquals(.5 - 1e-8, boundary.certifiedReachLowerBound(), 1e-12);
        var actual = reject(5, true, .5001).reachFailure().orElseThrow();
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.ReachFailureStatus
                        .WITNESS_BELOW_REQUIRED_FLOOR,
                actual.status());
        assertEquals(.5, actual.minimumReachWitness().questionReach(), 1e-12);
    }

    @Test
    void witnessesAreImmutableAndMislabelledEvidenceCannotParse() throws Exception {
        var a = reject(3, false, .01).reachFailure().orElseThrow();
        var w = a.minimumReachWitness();
        assertThrows(UnsupportedOperationException.class, () -> w.point().clear());
        assertThrows(UnsupportedOperationException.class, () -> w.dual().clear());
        assertThrows(UnsupportedOperationException.class, () -> w.opponentVariables().clear());
        assertThrows(UnsupportedOperationException.class, () -> w.masses().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.fixedHeroPast().clear());
        ObjectNode tree = SixMaxTexturePayoffTable.mapper().valueToTree(a);
        tree.put("status", "CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR");
        assertThrows(
                Exception.class,
                () ->
                        SixMaxTexturePayoffTable.mapper()
                                .treeToValue(
                                        tree,
                                        FiniteTwoPlayerConditionalActionIntervals.ReachFailureAudit
                                                .class));
    }

    @Test
    void rejectedWitnessReconstructionAlsoHonorsTheSharedCompilerBudget() {
        var evidence = reject(5, false, .01).reachFailure().orElseThrow();
        var error =
                assertThrows(
                        FiniteTwoPlayerAffineSequenceForm.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solve(
                                        posteriorGame(5, false, false),
                                        question(5),
                                        1e-8,
                                        .01,
                                        evidence.work().compilerUnits() - 1,
                                        BoundedLinearProgram.MAX_PIVOTS,
                                        BoundedLinearProgram.MAX_ARITHMETIC_WORK));
        assertEquals(FiniteTwoPlayerAffineSequenceForm.Failure.WORK_LIMIT, error.reason());
    }

    @Test
    void workFailuresNeverReturnAReachCertificate() {
        var error =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () ->
                                FiniteTwoPlayerConditionalActionIntervals.solve(
                                        posteriorGame(5, false, false),
                                        question(5),
                                        1e-8,
                                        .01,
                                        FiniteTwoPlayerAffineSequenceForm.MAX_REDUCTION_WORK,
                                        0,
                                        0));
        assertEquals(BoundedLinearProgram.Failure.WORK_LIMIT, error.reason());
    }
}
