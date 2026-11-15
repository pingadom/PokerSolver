package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;

class RootSecurityParentReuseTest {
    @Test
    void explicitReusePerformsNoAdditionalParentLpAndKeepsEveryEnvelope() throws Exception {
        var game = RootSecurityEnvelopeTest.model(0, 0, .25);
        var questions = RootSecurityBatchTest.questions(0);
        var parent = FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(game);
        var reused =
                FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(game, questions, parent).audit();
        var standalone = FiniteTwoPlayerRootSecurityBatch.solve(game, questions).audit();
        assertEquals(FiniteTwoPlayerRootSecurityBatch.REUSED_PARENT_ALGORITHM, reused.algorithm());
        assertNotEquals(standalone.algorithm(), reused.algorithm());
        assertFalse(reused.trainerAdmission());
        assertSame(parent.audit(), reused.parent());
        assertTrue(reused.envelopes().stream().allMatch(e -> e.baseline() == parent.audit()));
        assertEquals(standalone.envelopes(), reused.envelopes());
        assertEquals(0, reused.work().parentSolveCalls());
        assertEquals(0, reused.work().parentLpSolves());
        assertEquals(0, reused.work().parentCompilerUnits());
        assertEquals(0, reused.work().parentLpArithmeticWork());
        assertEquals(standalone.work().endpointLpSolves(), reused.work().endpointLpSolves());
        assertEquals(standalone.work().compilerUnits(), reused.work().compilerUnits());
        assertEquals(standalone.firstParentWitness(), reused.firstParentWitness());
        assertEquals(standalone.secondParentWitness(), reused.secondParentWitness());
    }

    @Test
    void nullUnrelatedAndOlderAlgorithmParentsReject() throws Exception {
        var game = RootSecurityEnvelopeTest.model(0, 0, .25);
        var qs = RootSecurityBatchTest.questions(0);
        var unrelated =
                FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(
                        RootSecurityEnvelopeTest.model(1, 7, .25));
        var older = FiniteTwoPlayerAffineSequenceForm.solve(game);
        assertThrows(
                NullPointerException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(game, qs, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(game, qs, unrelated));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(game, qs, older));
    }

    @Test
    void rootPreflightAndInventoryLimitsRemainStrictOnReuse() throws Exception {
        var game = RootSecurityEnvelopeTest.model(0, 0, .25);
        var qs = RootSecurityBatchTest.questions(0);
        var parent = FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(game);
        for (var list :
                List.of(
                        List.<FiniteTwoPlayerRootSecurityEnvelope.Question>of(),
                        Collections.nCopies(2, qs.getFirst()),
                        Collections.nCopies(9, qs.getFirst()),
                        List.of(new FiniteTwoPlayerRootSecurityEnvelope.Question(0, "0:missing"))))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FiniteTwoPlayerRootSecurityBatch.reuseOwnedParent(game, list, parent));
    }

    @Test
    void parsedReportCannotConstructOwnedPhysicalOrBatchHandles() {
        for (var cls :
                List.of(
                        SixMaxSuppliedFlopSizeMenuStudy.Result.class,
                        FiniteTwoPlayerRootSecurityBatch.Result.class,
                        SixMaxPhysicalRootBankStudy.Result.class))
            assertTrue(
                    Arrays.stream(cls.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }
}
