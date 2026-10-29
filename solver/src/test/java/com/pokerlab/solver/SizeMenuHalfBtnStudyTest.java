package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SizeMenuHalfBtnStudyTest {
    static final Path REQUEST = Path.of("../docs/data/supplied-size-menu-btn-half-request.json");
    static final Path REPORT = Path.of("../docs/data/supplied-size-menu-btn-half-report.json.gz");
    static SixMaxSizeMenuHalfBtnStudy.Result owned;
    static SixMaxSizeMenuHalfBtnStudy.Request request;
    static SixMaxSuppliedFlopSizeMenuGame game;

    @BeforeAll
    static void oneCompleteDeclaredPhysicalReplay() throws Exception {
        request = SixMaxSizeMenuHalfBtnStudy.readRequest(REQUEST);
        var p = request.parent();
        game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        owned = SixMaxSizeMenuHalfBtnStudy.solve(request, game);
    }

    static SixMaxSizeMenuBtnStudy.Report quarter() throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                Path.of(
                                        "../docs/data/supplied-size-menu-btn-quarter-report.json.gz"),
                                SixMaxSizeMenuBtnStudy.MAX_BYTES),
                        SixMaxSizeMenuBtnStudy.Report.class);
    }

    @Test
    void completeHalfPotReportReplaysExactlyAndParentIsTheOriginalJointGame() throws Exception {
        assertEquals(
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        REPORT, SixMaxSizeMenuHalfBtnStudy.MAX_BYTES),
                                SixMaxSizeMenuHalfBtnStudy.Report.class),
                owned.report());
        assertEquals(quarter().parent(), owned.report().parent());
        assertFalse(owned.report().trainerAdmission());
        assertEquals(
                List.of(0, 1, 2, 3), owned.report().cases().stream().map(c -> c.index()).toList());
        assertEquals(
                List.of("2c 3c", "Ah Kh", "As Qs", "Qh Th"),
                owned.report().cases().stream().map(c -> c.heroCards()).toList());
    }

    @Test
    void allEightFailedEndpointsKeepTheirOriginalReachCertificateAndReasons() {
        int attempts = 0;
        for (var c : owned.report().cases()) {
            assertEquals(
                    List.of("fold", "call"), c.endpoints().stream().map(e -> e.action()).toList());
            assertEquals(
                    List.of(
                            "ENDPOINT:call:CONDITIONAL:INSUFFICIENT_REACH",
                            "ENDPOINT:fold:CONDITIONAL:INSUFFICIENT_REACH"),
                    c.rejectionReasons());
            for (var e : c.endpoints()) {
                attempts++;
                assertNull(e.utility());
                assertEquals("CONDITIONAL", e.rejection().kind());
                assertEquals("INSUFFICIENT_REACH", e.rejection().reason());
                var a = e.rejection().reachFailure();
                assertNotNull(a);
                assertEquals(
                        FiniteTwoPlayerConditionalActionIntervals.CERTIFIED_REACH_FAILURE_ALGORITHM,
                        a.algorithm());
                assertEquals(owned.report().parent().solve(), a.baseline());
                assertEquals(
                        new FiniteTwoPlayerConditionalActionIntervals.Question(
                                3, c.informationSet(), e.action()),
                        a.question());
                assertEquals(List.of(), a.fixedHeroPast());
                assertEquals(.0001, a.requiredMinimumReach());
                assertEquals(1e-8, a.securitySlack());
                assertEquals(1, a.work().intervalLpSolves());
                assertTrue(a.certifiedReachLowerBound() < .0001);
                assertEquals(0, a.minimumReachWitness().questionReach());
                assertTrue(
                        a.minimumReachWitness().globalHeroBestResponse()
                                <= a.globalHeroUpperValue() + 2e-8);
            }
            assertTrue(c.moves().isEmpty());
            assertNull(c.primary());
            assertTrue(c.references().isEmpty());
            assertFalse(c.qualifiedForOfflineDrill());
        }
        assertEquals(8, attempts);
    }

    @Test
    void rejectedMassesRetainAllSixWorldsAndSizeSpecificOpponentSequence() {
        for (var c : owned.report().cases())
            for (var e : c.endpoints()) {
                var witness = e.rejection().reachFailure().minimumReachWitness();
                assertEquals(6, witness.masses().size());
                assertEquals(
                        List.of(0, 1, 2, 3, 4, 5),
                        witness.masses().stream().map(m -> m.rootIndex()).toList());
                for (var m : witness.masses()) {
                    assertTrue(m.chanceWeight() > 0);
                    assertEquals(0, m.originalRealizationWeight());
                    assertEquals(0, m.behavioralWeight());
                    var sequence = witness.opponentFlow().sequences().get(m.opponentSequence());
                    assertEquals("bet:0.5", sequence.action());
                    assertTrue(sequence.key().startsWith("5:supplied-size-menu-flop:"));
                }
                assertTrue(witness.opponentFlow().maximumResidual() <= 1e-8);
            }
    }

    @Test
    void halfPotTableHasOwnedPriceAndCannotBeMistakenForQuarterPot() throws Exception {
        for (var c : owned.report().cases()) {
            var context = c.context();
            assertEquals("BTN", context.actingSeat().name());
            assertEquals(9.75, context.potBb());
            assertEquals(3.25, context.toCallBb());
            assertEquals(
                    List.of(0.0, 3.25), context.actions().stream().map(a -> a.amountBb()).toList());
            assertEquals(6.25, context.players().get(5).committedBb());
            assertEquals(3, context.players().get(3).committedBb());
            assertEquals(6, context.players().size());
            for (var p : context.players())
                assertEquals(100, p.committedBb() + p.remainingStackBb());
            assertEquals(
                    context, SixMaxSizeMenuHalfBtnStudy.question(game, c.heroCards()).context());
        }
        assertEquals(1.625, quarter().cases().getFirst().context().toCallBb());
        assertEquals(List.of(0, 1, 2, 3), quarter().qualifiedIndices());
    }

    @Test
    void noHandOrEndpointReceivesAnExtraReferenceRun() {
        var references = owned.report().parent().references();
        assertEquals(
                List.of(80_000, 160_000),
                references.stream().map(r -> r.policy().iterations()).toList());
        assertEquals(240_000, references.stream().mapToInt(r -> r.policy().iterations()).sum());
        assertEquals(1609, owned.report().parent().solve().treeNodes());
        assertEquals(24, owned.report().parent().originalJointPrior().size());
    }

    @Test
    void privateOwnedResultCannotIssueAnyHalfPotCapability() {
        assertTrue(owned.report().qualifiedIndices().isEmpty());
        assertTrue(owned.qualifiedCases().isEmpty());
        assertThrows(
                UnsupportedOperationException.class, () -> owned.qualifiedCases().put(0, null));
        for (var type :
                List.of(
                        SixMaxSizeMenuHalfBtnStudy.Result.class,
                        SixMaxSizeMenuHalfBtnStudy.QualifiedCase.class))
            assertTrue(
                    Arrays.stream(type.getDeclaredConstructors())
                            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuBtnStudy.actions(
                                owned.report().cases().getFirst().endpoints()));
    }

    @Test
    void publishedQuarterGradesCannotAuthorizeAHalfPotReport() throws Exception {
        var half = owned.report().cases().getFirst();
        var q = quarter().cases().getFirst();
        var forged =
                new SixMaxSizeMenuHalfBtnStudy.Case(
                        half.index(),
                        half.heroCards(),
                        half.informationSet(),
                        half.context(),
                        q.endpoints(),
                        q.moves(),
                        q.primary(),
                        q.references(),
                        true,
                        List.of());
        var cases = new ArrayList<>(owned.report().cases());
        cases.set(0, forged);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSizeMenuHalfBtnStudy.Report(
                                SixMaxSizeMenuHalfBtnStudy.REPORT_SCHEMA,
                                SixMaxSizeMenuHalfBtnStudy.STATUS,
                                false,
                                request,
                                owned.report().parent(),
                                cases,
                                List.of(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxSizeMenuHalfBtnStudy.Case(
                                half.index(),
                                half.heroCards(),
                                half.informationSet(),
                                half.context(),
                                half.endpoints(),
                                q.moves(),
                                q.primary(),
                                q.references(),
                                true,
                                List.of()));
    }

    @Test
    void strictRequestsAndReportsRejectChangedGatesAndForgedAdmission() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var node = (ObjectNode) mapper.valueToTree(request);
        node.put("maximumLossBb", .02);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(node, SixMaxSizeMenuHalfBtnStudy.Request.class));
        var report = (ObjectNode) mapper.valueToTree(owned.report());
        report.put("trainerAdmission", true);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(report, SixMaxSizeMenuHalfBtnStudy.Report.class));
        String original = Files.readString(REQUEST);
        for (String text :
                List.of(
                        original + " {}",
                        original.replaceFirst("\\{", "{\"extra\":0,"),
                        original.replaceFirst("\\{", "{\"schemaVersion\":\"wrong\",")))
            assertThrows(
                    Exception.class,
                    () -> mapper.readValue(text, SixMaxSizeMenuHalfBtnStudy.Request.class));
    }

    @Test
    void commandCannotOverwriteOrAliasItsSourcesBeforeNumericalWork(@TempDir Path temp)
            throws Exception {
        Path output = temp.resolve("keep.json");
        Files.writeString(output, "keep");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuHalfBtnMain.main(
                                new String[] {"solve", REQUEST.toString(), output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSizeMenuHalfBtnMain.main(
                                new String[] {"solve", REQUEST.toString(), REQUEST.toString()}));
        assertEquals("keep", Files.readString(output));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSizeMenuHalfBtnMain.main(new String[] {"other"}));
    }
}
