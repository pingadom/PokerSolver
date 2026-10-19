package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RefinedFlopFeedbackTest {
    static SixMaxRefinedFlopFeedback.Result owned;

    @BeforeAll
    static void solve() throws Exception {
        owned = RefinedFlopOwnedFixture.owned();
    }

    @Test
    void fullOwnedQualificationReproducesSavedEvidenceAndPrivateCapability() throws Exception {
        var r = owned.report();
        assertTrue(r.qualifiedForOfflineDrill());
        assertTrue(r.rejectionReasons().isEmpty());
        assertFalse(r.trainerAdmission());
        assertEquals(r, SixMaxRefinedFlopFeedback.readReport(RefinedFlopOwnedFixture.REPORT));
        assertEquals(
                SixMaxHeadsUpPreflopGame.hash(r),
                owned.qualifiedFeedback().orElseThrow().reportHash());
        assertTrue(
                Arrays.stream(SixMaxRefinedFlopFeedback.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertTrue(
                Arrays.stream(
                                SixMaxRefinedFlopFeedback.QualifiedFeedback.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertEquals(
                List.of(80000, 160000),
                r.references().stream().map(x -> x.policy().iterations()).toList());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                r.moves().getFirst().classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                r.moves().getLast().classification());
        for (var ref : r.references()) {
            assertTrue(ref.check().failures().isEmpty());
            assertTrue(ref.check().insideDeclaredFace());
            assertTrue(ref.check().boundsChecked());
            assertEquals(0, ref.check().maximumBoundViolationBb());
            assertTrue(ref.check().decisionRegretBb() < 1e-8);
            assertEquals(4L * ref.policy().iterations(), ref.work().prunedSuffixRoots());
        }
    }

    @Test
    void selectedDecisionRegretAndOpponentFaceFailuresRemainSeparate() throws Exception {
        var game = RefinedFlopOwnedFixture.game();
        var c = owned.report().conditional();
        var strategy = new TreeMap<>(c.parent().candidate().strategy());
        strategy.put(c.informationSet(), Map.of("fold", 1., "call", 0.));
        var bad =
                SixMaxRefinedFlopFeedback.assess(
                        game, c.request(), c.actions(), new CfrSolution(1, strategy));
        assertTrue(bad.failures().contains("SELECTED_DECISION_REGRET"));
        assertTrue(bad.decisionRegretBb() > 3);
        strategy = new TreeMap<>(c.parent().candidate().strategy());
        for (var key : new ArrayList<>(strategy.keySet()))
            if (key.startsWith("5:supplied-flop-one-bet:") && key.endsWith(":8d Kc Kd:"))
                strategy.put(key, Map.of("check", 1., "bet", 0.));
        var outside =
                SixMaxRefinedFlopFeedback.assess(
                        game, c.request(), c.actions(), new CfrSolution(1, strategy));
        assertTrue(outside.failures().contains("REFERENCE_OUTSIDE_DECLARED_FACE"));
        assertTrue(outside.failures().contains("INSUFFICIENT_ACTUAL_QUESTION_REACH"));
        assertFalse(outside.boundsChecked());
        assertNull(outside.maximumBoundViolationBb());
    }

    @Test
    void exactOwnedComparisonRejectsEditedReferenceEvenWithFreshHash(@TempDir Path temp)
            throws Exception {
        var tree = (ObjectNode) SixMaxTexturePayoffTable.mapper().valueToTree(owned.report());
        ((ObjectNode) tree.path("primary").path("actionUtilitiesBb")).put("call", 9.);
        var path = temp.resolve("changed.json");
        Files.writeString(path, tree.toString());
        var changed = SixMaxRefinedFlopFeedback.readReport(path);
        assertNotEquals(
                owned.qualifiedFeedback().orElseThrow().reportHash(),
                SixMaxHeadsUpPreflopGame.hash(changed));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedFlopFeedback.verifyReport(changed, owned));
    }

    @Test
    void exclusivePersistenceAndStrictRequestsRetainOriginalCaps(@TempDir Path temp)
            throws Exception {
        for (var name : List.of("feedback.json", "feedback.json.gz")) {
            var path = temp.resolve(name);
            SixMaxRefinedFlopFeedback.write(path, owned);
            assertEquals(owned.report(), SixMaxRefinedFlopFeedback.readReport(path));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxRefinedFlopFeedback.write(path, owned));
        }
        var q = owned.report().request();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopFeedback.ReferenceBudget(
                                q.referenceBudget().algorithm(), List.of(80000, 320000), 400000));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxRefinedFlopFeedback.ReferenceBudget(
                                "old", List.of(80000, 160000), 240000));
        String text = Files.readString(RefinedFlopOwnedFixture.INPUT);
        for (var changed :
                List.of(
                        text + "{}",
                        text.replace(
                                "\"maximumLossBb\":", "\"maximumLossBb\":0.01,\"maximumLossBb\":"),
                        text.replace("\"referenceBudget\"", "\"unexpected\""))) {
            var path = temp.resolve("bad.json");
            Files.writeString(path, changed);
            assertThrows(Exception.class, () -> SixMaxRefinedFlopFeedback.readRequest(path));
        }
        var large = temp.resolve("large.json");
        Files.write(large, new byte[SixMaxRefinedFlopFeedback.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class, () -> SixMaxRefinedFlopFeedback.readReport(large));
    }
}
