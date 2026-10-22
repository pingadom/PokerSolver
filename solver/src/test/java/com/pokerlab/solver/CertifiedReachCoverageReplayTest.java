package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class CertifiedReachCoverageReplayTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-certified-reach-flop-coverage-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-certified-reach-flop-coverage-report.json.gz");
    static SixMaxRefinedFlopCoverageStudy.Result owned;

    @BeforeAll
    static void completeOrderedPhysicalReplayOnce() throws Exception {
        owned = SixMaxRefinedFlopCoverageStudy.replay(REQUEST, REPORT);
    }

    @Test
    void fourNegativeWitnessesRetainOriginalSecurityAndNeverInventPosteriorsOrMoves()
            throws Exception {
        var report = owned.report();
        assertFalse(report.trainerAdmission());
        assertEquals(new SixMaxRefinedFlopCoverageStudy.Counts(8, 4, 4, 4, 0), report.counts());
        assertEquals(0, report.reservation().freshReferenceRuns());
        for (int i = 0; i < 8; i += 2) {
            var item = report.cases().get(i);
            assertEquals("INSUFFICIENT_REACH", item.rejection().reason());
            assertNull(item.conditional());
            assertTrue(item.moves().isEmpty());
            var audit = item.rejection().reachFailure();
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.CERTIFIED_REACH_FAILURE_ALGORITHM,
                    audit.algorithm());
            assertFalse(audit.trainerAdmission());
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals.ReachFailureStatus
                            .WITNESS_BELOW_REQUIRED_FLOOR,
                    audit.status());
            assertEquals(-1e-8, audit.certifiedReachLowerBound());
            var witness = audit.minimumReachWitness();
            assertEquals(0., witness.questionReach());
            assertEquals(6, witness.masses().size());
            assertTrue(
                    witness.globalHeroBestResponse()
                            <= audit.globalHeroUpperValue() + audit.securitySlack() + 1e-8);
            assertEquals(1, audit.work().intervalLpSolves());
        }
        assertEquals(
                "5e2963854792ab98037c2d9e6735ca76e38a48316ad9f7f2e282d6a83c77b6e0",
                SixMaxHeadsUpPreflopGame.hash(report));
        assertTrue(
                Arrays.stream(SixMaxRefinedFlopCoverageStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void positiveEndpointsAreIdenticalToV8ExceptExplicitVersionIdentity() throws Exception {
        var old = SixMaxRefinedFlopCoverageStudy.readReport(RefinedFlopCoverageStudyTest.REPORT);
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (int i = 1; i < 8; i += 2) {
            var item = owned.report().cases().get(i);
            var node = (ObjectNode) mapper.valueToTree(item);
            ((ObjectNode) node.path("request"))
                    .put("intervalAlgorithm", old.request().intervalAlgorithm());
            var conditional = (ObjectNode) node.path("conditional");
            conditional.put(
                    "schemaVersion", SixMaxRefinedConditionalStudy.RESOLVABLE_BASIS_REPORT_SCHEMA);
            ((ObjectNode) conditional.path("request"))
                    .put("intervalAlgorithm", old.request().intervalAlgorithm());
            for (var action : conditional.path("actions"))
                ((ObjectNode) action.path("diagnostic"))
                        .put("algorithm", old.request().intervalAlgorithm());
            assertEquals(mapper.valueToTree(old.cases().get(i)), node);
        }
    }

    @Test
    void schemasAndNegativeProtocolCannotBeMixedOrRelabelled() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String mutation :
                List.of(
                        "schema",
                        "algorithm",
                        "protocol",
                        "hand",
                        "reach",
                        "status",
                        "admission")) {
            var node = (ObjectNode) mapper.valueToTree(owned.report());
            var audit =
                    (ObjectNode) node.path("cases").get(0).path("rejection").path("reachFailure");
            switch (mutation) {
                case "schema" ->
                        node.put("schemaVersion", SixMaxRefinedFlopCoverageStudy.REPORT_SCHEMA);
                case "algorithm" ->
                        ((ObjectNode) node.path("request"))
                                .put(
                                        "intervalAlgorithm",
                                        FiniteTwoPlayerConditionalActionIntervals
                                                .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM);
                case "protocol" ->
                        audit.put(
                                "algorithm",
                                FiniteTwoPlayerConditionalActionIntervals.REACH_FAILURE_ALGORITHM);
                case "hand" ->
                        ((ObjectNode) audit.path("question")).put("informationSet", "foreign");
                case "reach" -> audit.put("certifiedReachLowerBound", 1.);
                case "status" -> audit.put("status", "CONSERVATIVE_BOUND_BELOW_REQUIRED_FLOOR");
                case "admission" -> audit.put("trainerAdmission", true);
            }
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> mapper.treeToValue(node, SixMaxRefinedFlopCoverageStudy.Report.class),
                    mutation);
        }
    }
}
