package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline full private-input plan. Never send its concealed joint hands as trainer questions. */
public final class SixSeatRangePreflightMain {
    private SixSeatRangePreflightMain() {}

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 7 || args[0] == null || args[0].isBlank())
            throw new IllegalArgumentException("Usage: NEW-output.json UTG HJ CO BTN SB BB");
        var plan =
                SixSeatRangePreflight.plan(
                        List.of(Arrays.copyOfRange(args, 1, 7)),
                        SixSeatRangePreflight.Limits.defaults());
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", "pokerlab-six-seat-range-preflight/v1");
        report.put("trainerAdmission", false);
        report.put("gradingAuthority", false);
        report.put("priorScope", plan.priorScope());
        report.put("inputHash", plan.inputHash());
        report.put("rangeHashes", plan.rangeHashes());
        report.put("ranges", plan.ranges());
        report.put("nominalProducts", Long.toString(plan.nominalProducts()));
        report.put("legalDeals", plan.deals().size());
        report.put("logLegalWeightMass", plan.logLegalWeightMass());
        report.put("allInputCombosSupported", plan.allInputCombosSupported());
        report.put("work", plan.work());
        report.put("seatSupport", plan.seatSupport());
        report.put("deals", plan.deals());
        byte[] bytes =
                SixMaxTexturePayoffTable.mapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(report);
        Files.write(
                Path.of(args[0]), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
