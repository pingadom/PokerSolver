package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RootEnvelopeStudyTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-root-envelope-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-root-envelope-report.json.gz");
    static SixMaxRootEnvelopeStudy.Request request;
    static SixMaxRootEnvelopeStudy.Result owned;

    @BeforeAll
    static void oneDeclaredCompletePhysicalReplay() throws Exception {
        request = SixMaxRootEnvelopeStudy.readRequest(REQUEST);
        owned = SixMaxRootEnvelopeStudy.solve(request);
    }

    @Test
    void completePhysicalReportAndUnchangedJointParentReproduceExactly() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var saved =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(REPORT, 8 * 1024 * 1024),
                        SixMaxRootEnvelopeStudy.Report.class);
        assertEquals(saved, owned.report());
        var original =
                mapper.readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of("../docs/data/supplied-flop-size-menu-report.json.gz"),
                                8 * 1024 * 1024),
                        SixMaxSuppliedFlopSizeMenuStudy.Report.class);
        assertEquals(original, owned.report().parent());
        assertEquals(1609, original.solve().treeNodes());
        assertEquals(
                List.of(80000, 160000),
                original.references().stream().map(r -> r.policy().iterations()).toList());
        assertTrue(owned.report().completeOuterBounds());
        assertNull(owned.report().rejection());
    }

    @Test
    void allOriginalMovesHaveConservativeBoundsWithinOneSharedBudget() {
        var a = owned.report().envelope();
        assertEquals(
                List.of("fold", "call", "raise:9.0"),
                a.actions().stream().map(r -> r.action()).toList());
        assertEquals(5, a.work().lpSolves());
        var witnesses = new ArrayList<>(List.of(a.guaranteeWitness(), a.questionUpperWitness()));
        a.actions().forEach(r -> witnesses.add(r.lowerWitness()));
        assertEquals(
                a.work().lpArithmeticWork(),
                witnesses.stream().mapToLong(w -> w.certificate().work().arithmeticWork()).sum());
        assertTrue(a.work().lpArithmeticWork() <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
        assertTrue(a.work().lpPivots() <= BoundedLinearProgram.MAX_PIVOTS);
        assertEquals(8_000_000, a.work().compilerLimit());
        assertTrue(a.work().compilerUnits() <= a.work().compilerLimit());
    }

    @Test
    void fixedCompletePurePolicyIsDerivedFromOwnedBaselineInOriginalActionOrder() {
        var a = owned.report().envelope();
        assertEquals(FiniteTwoPlayerRootSecurityEnvelope.POLICY, a.fixedPolicyAlgorithm());
        var expected = new TreeMap<String, String>();
        var flow = a.baseline().secondFlow();
        for (var row : flow.conservation()) {
            var moves =
                    row.childSequences().stream()
                            .map(i -> flow.sequences().get(i).action())
                            .toList();
            String best = moves.getFirst();
            for (String move : moves)
                if (owned.report().parent().candidate().strategy().get(row.key()).get(move)
                        > owned.report().parent().candidate().strategy().get(row.key()).get(best))
                    best = move;
            expected.put(row.key(), best);
        }
        assertEquals(33, expected.size());
        assertEquals(expected, a.fixedHeroChoices());
        assertEquals("ALL_OPPONENT_FLOWS", a.guaranteeWitness().domain());
        assertEquals("FULL_OPPONENT_SECURITY_FACE", a.questionUpperWitness().domain());
    }

    @Test
    void rootSupportAndCurrentDecisionPricesAreNotConditionedOnThePhysicalFlop() {
        var a = owned.report().envelope();
        assertEquals(1. / 3, a.questionChanceMass(), 1e-15);
        assertEquals(8, a.support().size());
        for (int i = 0; i < a.support().size(); i++) {
            assertEquals(i, a.support().get(i).rootIndex());
            assertEquals(1. / 24, a.support().get(i).chanceWeight());
        }
        var c = owned.report().context();
        assertEquals(4.5, c.potBb());
        assertEquals(2., c.toCallBb());
        assertEquals(List.of(0., 2., 8.), c.actions().stream().map(r -> r.amountBb()).toList());
        assertEquals(6, c.players().size());
    }

    @Test
    void globalGuaranteeAllowanceAndActionIntersectionFollowDeclaredInequality() {
        var a = owned.report().envelope();
        assertEquals(
                (a.globalHeroUpperValue() + a.securitySlack() - a.guaranteedGlobalLower())
                        / a.questionChanceMass(),
                a.securityAllowance());
        assertEquals(a.fixedQuestionUpper() + a.securityAllowance(), a.commonActionUpper());
        for (var action : a.actions()) {
            assertEquals(
                    Math.min(a.commonActionUpper(), action.literalUtilityCeiling() + 1e-8),
                    action.upperUtility());
            assertTrue(
                    action.lowerUtility()
                            <= action.lowerWitness().optimalContinuationUtility() + 1e-8);
            assertTrue(
                    action.lowerWitness().optimalContinuationUtility()
                            <= action.upperUtility() + 1e-8);
        }
    }

    @Test
    void wideRaiseOuterBoundDoesNotBecomeAnExactIntervalOrTrainerGrade() throws Exception {
        var a = owned.report().envelope();
        assertFalse(owned.report().trainerAdmission());
        assertFalse(a.trainerAdmission());
        assertEquals(FiniteTwoPlayerRootSecurityEnvelope.SCOPE, a.scope());
        var raise = a.actions().getLast();
        assertTrue(raise.upperUtility() - raise.lowerUtility() > 7);
        assertEquals(-1, a.actions().getFirst().lowerUtility(), 2e-8);
        var old =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        Path.of(
                                                "../docs/data/supplied-reduced-preflop-report.json.gz"),
                                        8 * 1024 * 1024),
                                SixMaxReducedPreflopStudy.Report.class);
        var call = old.endpoints().get(1).utility();
        assertTrue(a.actions().get(1).lowerUtility() <= call.lowerBb() + 1e-8);
        assertTrue(a.actions().get(1).upperUtility() >= call.upperBb() - 1e-8);
        assertEquals("WORK_LIMIT", old.endpoints().getLast().rejection().reason());
    }

    @Test
    void strictReaderRejectsRelabelledAuthorityPolicyAndIncompleteMenus() {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field : List.of("trainerAdmission", "completeOuterBounds")) {
            var n = (ObjectNode) mapper.valueToTree(owned.report());
            n.put(field, !n.get(field).booleanValue());
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxRootEnvelopeStudy.Report.class));
        }
        for (String field : List.of("algorithm", "scope", "fixedPolicyAlgorithm")) {
            var n = (ObjectNode) mapper.valueToTree(owned.report());
            ((ObjectNode) n.get("envelope")).put(field, "borrowed");
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(n, SixMaxRootEnvelopeStudy.Report.class));
        }
        var n = (ObjectNode) mapper.valueToTree(owned.report());
        ((com.fasterxml.jackson.databind.node.ArrayNode) n.get("envelope").get("actions"))
                .remove(2);
        assertThrows(
                Exception.class, () -> mapper.treeToValue(n, SixMaxRootEnvelopeStudy.Report.class));
        assertThrows(
                Exception.class,
                () ->
                        mapper.readValue(
                                "{\"schemaVersion\":\"wrong\"}",
                                SixMaxRootEnvelopeStudy.Request.class));
    }

    @Test
    void ownedResultsAndNestedEvidenceRemainImmutable() {
        assertTrue(
                Arrays.stream(SixMaxRootEnvelopeStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().envelope().fixedHeroChoices().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().envelope().actions().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> owned.report().envelope().guaranteeWitness().point().clear());
    }

    @Test
    void outputAliasesAndExistingFilesRejectBeforeNumericalWork(@TempDir Path temp)
            throws Exception {
        Path keep = temp.resolve("keep.json");
        Files.writeString(keep, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootEnvelopeMain.main(
                                new String[] {"solve", REQUEST.toString(), keep.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRootEnvelopeMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertEquals("keep", Files.readString(keep));
    }
}
