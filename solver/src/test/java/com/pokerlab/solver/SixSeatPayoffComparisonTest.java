package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SixSeatPayoffComparisonTest {
    private static final SixSeatPayoffWork.Budget ONE_EXACT_WORLD =
            new SixSeatPayoffWork.Budget(658008, 3948048);

    @TempDir Path temp;

    private static SixSeatRangeSource.Plan sampled() {
        return SixSeatRangeSource.plan(
                SixSeatRangeSourceTest.request(
                        SixSeatRangeSourceTest.SINGLE,
                        SixSeatPayoffWork.Configuration.shared(4, 711)));
    }

    @Test
    void exactRevaluationKeepsThePolicyAndBoundsEachSeatsDeviationShift() {
        var planned = sampled();
        var reference =
                SixSeatPayoffComparison.planExactReference(planned, ONE_EXACT_WORLD).execute();
        var source = planned.execute();
        var report = SixSeatPayoffComparison.assess(source, reference);
        assertEquals(source.packHash(), report.sourcePackHash());
        assertEquals(source.quality().nashConvBb(), report.sampledNashConvBb());
        assertEquals(4, report.sampledBoards());
        assertEquals(658008, report.exactBoards());
        assertEquals(6, report.seats().size());
        assertEquals(
                report.exactNashConvBb(),
                report.seats().stream()
                        .mapToDouble(SixSeatPayoffComparison.SeatComparison::exactDeviationGainBb)
                        .sum(),
                1e-10);
        var exact = planned.spot().game(reference.oracle());
        var tree = exact.treeSummary();
        assertEquals(
                tree.allInTerminals() + tree.checkdownTerminals() + tree.uncontestedTerminals(),
                report.comparedTerminalStates());
        assertTrue(report.seats().stream().anyMatch(s -> s.deviationShiftBoundBb() > .01));
        assertNotEquals(report.sampledNashConvBb(), report.exactNashConvBb(), 1e-5);
        for (var seat : report.seats()) {
            assertInBounds(seat.exactProfileEvBb() - seat.sampledProfileEvBb(), seat);
            assertInBounds(seat.exactBestResponseEvBb() - seat.sampledBestResponseEvBb(), seat);
            assertTrue(
                    Math.abs(seat.exactDeviationGainBb() - seat.sampledDeviationGainBb())
                            <= seat.deviationShiftBoundBb() + 1e-9);
        }

        var ranges = new ArrayList<>(SixSeatRangeSourceTest.SINGLE);
        ranges.set(0, "AcAd@0.5"); // Identical cards and chance support, different declared range.
        var other =
                SixSeatRangeSource.plan(
                                SixSeatRangeSourceTest.request(
                                        ranges, SixSeatPayoffWork.Configuration.shared(4, 711)))
                        .execute();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatPayoffComparison.assess(other, reference));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatPayoffComparison.assess(source, source.payoffs()));
    }

    @Test
    void comparisonRefusesAnExactTrainingSourceOrInsufficientReferenceBudget() {
        assertThrows(
                SixSeatPayoffWork.BudgetExceeded.class,
                () ->
                        SixSeatPayoffComparison.planExactReference(
                                sampled(), new SixSeatPayoffWork.Budget(658007, 3948048)));
        var exact =
                SixSeatRangeSource.plan(
                        SixSeatRangeSourceTest.request(
                                SixSeatRangeSourceTest.SINGLE,
                                SixSeatPayoffWork.Configuration.exact()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatPayoffComparison.planExactReference(exact, ONE_EXACT_WORLD));
    }

    @Test
    void cliCapturesComparisonAndRejectsInsufficientReferenceWorkWithoutOutput() throws Exception {
        var input = temp.resolve("input.json");
        var output = temp.resolve("comparison.json");
        var mapper = SixMaxTexturePayoffTable.mapper();
        Files.write(input, mapper.writeValueAsBytes(sampled().request()));
        var args =
                new String[] {"compare", input.toString(), output.toString(), "658007", "3948048"};
        assertThrows(
                SixSeatPayoffWork.BudgetExceeded.class, () -> SixSeatRangeSourceMain.main(args));
        assertFalse(Files.exists(output));
        args[3] = "658008";
        SixSeatRangeSourceMain.main(args);
        var report = mapper.readTree(output.toFile());
        assertEquals(
                "pokerlab-six-seat-payoff-comparison/v1", report.get("schemaVersion").asText());
        assertFalse(report.get("trainerAdmission").asBoolean());
        assertFalse(report.get("gradingAuthority").asBoolean());
        assertEquals(658008, report.at("/plannedExactReferenceWork/boardEvaluations").asLong());
        assertEquals(658008, report.at("/comparison/exactBoards").asLong());
        assertEquals(6, report.at("/comparison/seats").size());
        assertFalse(report.has("sourcePackJson"));
        assertThrows(
                java.nio.file.FileAlreadyExistsException.class,
                () -> SixSeatRangeSourceMain.main(args));
    }

    private static void assertInBounds(double shift, SixSeatPayoffComparison.SeatComparison seat) {
        assertTrue(shift >= seat.minimumTerminalShiftBb() - 1e-9);
        assertTrue(shift <= seat.maximumTerminalShiftBb() + 1e-9);
    }
}
