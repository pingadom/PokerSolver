package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.RaiseSchedule.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixMaxStagedRaiseScheduleTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static SixMaxPreflopBetting staged(List<Double> targets) {
        return new SixMaxPreflopBetting(
                new SixMaxPreflopBetting.Rules(100, .5, targets, NEXT_TARGET));
    }

    private static SixMaxPreflopBetting.Move move(
            PreflopAllInSpot.Seat seat, SixMaxPreflopBetting.Kind kind, double amount) {
        return new SixMaxPreflopBetting.Move(seat, kind, amount);
    }

    @Test
    void limitsOpensAndThreeBetsAndReopensPriorCallersWithCorrectCommitments() {
        var game = staged(List.of(3.0, 9.0));
        var start = game.initialState();
        assertEquals(
                List.of(move(UTG, FOLD, 0), move(UTG, CALL, 1), move(UTG, RAISE_TO, 3)),
                start.legalActions());
        assertThrows(
                IllegalArgumentException.class, () -> game.apply(start, move(UTG, RAISE_TO, 9)));
        var state = game.apply(start, move(UTG, CALL, 1));
        state = game.apply(state, move(HJ, RAISE_TO, 3));
        state = game.apply(state, move(CO, CALL, 3));
        state = game.apply(state, move(BTN, RAISE_TO, 9));
        assertEquals(List.of(move(SB, FOLD, 0), move(SB, CALL, 9)), state.legalActions());
        state = game.apply(state, move(SB, FOLD, 0));
        state = game.apply(state, move(BB, FOLD, 0));
        assertEquals(UTG, state.actingSeat());
        assertEquals(8, state.toCallBb());
        state = game.apply(state, move(UTG, FOLD, 0));
        assertEquals(HJ, state.actingSeat());
        assertEquals(6, state.toCallBb());
        state = game.apply(state, move(HJ, CALL, 9));
        state = game.apply(state, move(CO, CALL, 9));
        assertEquals(SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED, state.status());
        assertEquals(List.of(HJ, CO, BTN), state.liveSeats());
        assertEquals(29.5, state.potBb());
        assertEquals(91, state.remainingStackBb(HJ));
        assertEquals(1.5, start.potBb());
        assertTrue(state.legalActions().isEmpty());
    }

    @Test
    void allowsBigBlindToOpenOverLimpsAndOnlyThenOffersTheNextTarget() {
        var game = staged(List.of(3.0, 9.0));
        var state = game.initialState();
        for (var seat : List.of(UTG, HJ, CO, BTN, SB))
            state = game.apply(state, move(seat, CALL, 1));
        assertEquals(List.of(move(BB, CHECK, 1), move(BB, RAISE_TO, 3)), state.legalActions());
        state = game.apply(state, move(BB, RAISE_TO, 3));
        assertEquals(UTG, state.actingSeat());
        assertEquals(
                List.of(move(UTG, FOLD, 0), move(UTG, CALL, 3), move(UTG, RAISE_TO, 9)),
                state.legalActions());
    }

    @Test
    void validatesEveryScheduledIncrementWithoutSkippingAnIllegalStage() {
        for (var targets :
                List.of(
                        List.of(1.5),
                        List.of(3.0, 4.0),
                        List.of(3.0, 9.0, 14.0),
                        List.of(3.0, 101.0),
                        List.of(3.0, 3.0)))
            assertThrows(IllegalArgumentException.class, () -> staged(targets));
        var game = staged(List.of(3.0, 9.0, 100.0));
        var state = game.apply(game.initialState(), move(UTG, RAISE_TO, 3));
        state = game.apply(state, move(HJ, RAISE_TO, 9));
        state = game.apply(state, move(CO, RAISE_TO, 100));
        for (var seat : List.of(BTN, SB, BB, UTG, HJ))
            state = game.apply(state, move(seat, CALL, 100));
        assertEquals(SixMaxPreflopBetting.Status.ALL_IN_SHOWDOWN, state.status());
        assertEquals(600, state.potBb());
        // Historical global menus may contain targets that are too small at some histories.
        assertDoesNotThrow(
                () ->
                        new SixMaxPreflopBetting(
                                new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0, 4.0, 9.0))));
    }

    @Test
    void preservesCanonicalLegacyRulesAndExplicitlyHashesTheNewSchedule() throws Exception {
        String legacy = "{\"raiseToBb\":[3.0,9.0],\"smallBlindBb\":0.5,\"stackBb\":100.0}";
        var rules = JSON.readValue(legacy, SixMaxPreflopBetting.Rules.class);
        assertEquals(GLOBAL_TARGETS, rules.raiseSchedule());
        assertEquals(legacy, JSON.writeValueAsString(rules));
        var next = staged(List.of(3.0, 9.0)).rules();
        assertEquals(
                "{\"raiseSchedule\":\"NEXT_TARGET\",\"raiseToBb\":[3.0,9.0],\"smallBlindBb\":0.5,\"stackBb\":100.0}",
                JSON.writeValueAsString(next));
        assertEquals(
                next,
                JSON.readValue(JSON.writeValueAsString(next), SixMaxPreflopBetting.Rules.class));
        var original = SixMaxPreflopPayoffReuseTest.source().spot();
        var global =
                new SixMaxPreflopResearchSpot(
                        "schedule-test",
                        rules,
                        original.ranges(),
                        original.rake(),
                        original.continuationModel());
        var staged =
                new SixMaxPreflopResearchSpot(
                        global.id(),
                        next,
                        global.ranges(),
                        global.rake(),
                        global.continuationModel());
        assertNotEquals(global.contentHash(), staged.contentHash());
        assertEquals(
                staged,
                MultiwayPackJson.readFullRoundSpot(MultiwayPackJson.writeFullRoundSpot(staged)));
        assertEquals(
                "7d077588d06a4100336cc989892cf953c94f82220f73008c8962a6f974b9cbc8",
                MultiwayPackJson.fullRoundContentHash(SixMaxCorrelatedSourcePackTest.source()));
    }

    @Test
    void customDecoderRejectsMissingNullUnknownDuplicateAndCoercedFields() throws Exception {
        var valid = JSON.writeValueAsString(staged(List.of(3.0, 9.0)).rules());
        for (var field : List.of("stackBb", "smallBlindBb", "raiseToBb", "raiseSchedule")) {
            var nulled = (ObjectNode) JSON.readTree(valid);
            nulled.putNull(field);
            assertThrows(
                    Exception.class,
                    () -> JSON.readValue(nulled.toString(), SixMaxPreflopBetting.Rules.class));
            if (!field.equals("raiseSchedule")) {
                var missing = (ObjectNode) JSON.readTree(valid);
                missing.remove(field);
                assertThrows(
                        Exception.class,
                        () -> JSON.readValue(missing.toString(), SixMaxPreflopBetting.Rules.class));
            }
        }
        for (var invalid :
                List.of(
                        "null",
                        "[]",
                        "{}",
                        "{\"stackBb\":100,\"stackBb\":100,\"smallBlindBb\":0.5,\"raiseToBb\":[3,9]}",
                        valid.replace("100.0", "\"100\""),
                        valid.replace("3.0", "\"3\""),
                        valid.replace("3.0", "null"),
                        valid.replace("NEXT_TARGET", "unknown"),
                        valid.replace("\"NEXT_TARGET\"", "0"),
                        valid.replace("0.5", "1e400"),
                        valid.replace("{", "{\"unexpected\":true,"),
                        valid.substring(0, valid.length() - 1)))
            assertThrows(
                    Exception.class,
                    () -> JSON.readValue(invalid, SixMaxPreflopBetting.Rules.class));
    }

    @Test
    void buildsFreshV2PackAndRejectsBothDirectionsOfSchemaMismatch() throws Exception {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var target =
                new SixMaxPreflopResearchSpot(
                        "staged-fixture",
                        staged(List.of(3.0, 9.0)).rules(),
                        source.spot().ranges(),
                        source.spot().rake(),
                        source.spot().continuationModel());
        var result =
                SixMaxPreflopPayoffReuse.buildExact(
                        source,
                        target,
                        2,
                        CfrSolver.Variant.CFR_PLUS,
                        SixMaxPreflopPayoffReuseTest.TIME);
        var pack = result.pack();
        assertEquals(SixMaxPreflopSolutionPack.STAGED_SCHEMA_VERSION, pack.schemaVersion());
        var json = MultiwayPackJson.writeFullRound(pack);
        assertEquals(json, MultiwayPackJson.writeFullRound(MultiwayPackJson.readFullRound(json)));
        assertEquals(114, result.provenance().reusedPayoffEntries());
        assertEquals(11_566, pack.rebuildGame().treeSummary().totalStates());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MultiwayPackJson.readFullRound(
                                json.replace(
                                        SixMaxPreflopSolutionPack.STAGED_SCHEMA_VERSION,
                                        SixMaxPreflopSolutionPack.SCHEMA_VERSION)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MultiwayPackJson.readFullRound(
                                MultiwayPackJson.writeFullRound(source)
                                        .replace(
                                                SixMaxPreflopSolutionPack.SCHEMA_VERSION,
                                                SixMaxPreflopSolutionPack.STAGED_SCHEMA_VERSION)));
        var removedSchedule = (ObjectNode) JSON.readTree(json);
        ((ObjectNode) removedSchedule.path("spot").path("rules")).remove("raiseSchedule");
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiwayPackJson.readFullRound(removedSchedule.toString()));
    }

    @Test
    void richerSchedulesStillFailTheUnchangedPublicTreeCapBeforeAnyPayoff() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPreflopCheckdownGame(
                                staged(List.of(3.0, 9.0, 27.0)).rules(),
                                SixMaxPreflopConvergenceMain.ranges("button-mix"),
                                CashRakeRule.none(),
                                (hands, mask) -> {
                                    calls.incrementAndGet();
                                    return null;
                                }));
        assertEquals(0, calls.get());
    }

    @Test
    void legacyCheckpointCannotBeResumedWithAStagedSource(@TempDir Path temp) throws Exception {
        var source = SixMaxConnectedPolicyCheckpointTest.source();
        var snapshot = SixMaxConnectedPolicyCheckpointTest.snapshot(source);
        var file = temp.resolve("policy.json");
        SixMaxConnectedPolicyCheckpoint.write(file, snapshot, source);
        var target =
                new SixMaxPreflopResearchSpot(
                        source.spot().id(),
                        new SixMaxPreflopBetting.Rules(
                                source.spot().rules().stackBb(),
                                source.spot().rules().smallBlindBb(),
                                source.spot().rules().raiseToBb(),
                                NEXT_TARGET),
                        source.spot().ranges(),
                        source.spot().rake(),
                        source.spot().continuationModel());
        var staged =
                SixMaxPreflopPayoffReuse.buildExact(
                                source,
                                target,
                                1,
                                CfrSolver.Variant.CFR_PLUS,
                                SixMaxPreflopPayoffReuseTest.TIME)
                        .pack();
        var before = Files.readAllBytes(file);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxConnectedPolicyCheckpoint.read(file, staged));
        assertArrayEquals(before, Files.readAllBytes(file));
    }
}
