package com.pokerlab.solver;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded captured JSON request to a private plan or complete validation-only source. */
public final class SixSeatRangeSourceMain {
    public static final int MAX_REQUEST_BYTES = 65_536;

    private SixSeatRangeSourceMain() {}

    public static void main(String[] args) throws Exception {
        if (args == null
                || args.length != 3
                || (!"plan".equals(args[0]) && !"solve".equals(args[0])))
            throw new IllegalArgumentException("Usage: plan|solve INPUT.json NEW-output.json");
        Path input = Path.of(args[1]), output = Path.of(args[2]);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new FileAlreadyExistsException(output.toString());
        byte[] bytes;
        try (var stream = Files.newInputStream(input)) {
            bytes = stream.readNBytes(MAX_REQUEST_BYTES + 1);
        }
        if (bytes.length > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("Source request exceeds 64 KiB");
        var mapper =
                SixMaxTexturePayoffTable.mapper()
                        .enable(
                                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
        // ALLOW_COERCION_OF_SCALARS excludes String targets; enforce the wire contract explicitly.
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        var request = mapper.readValue(bytes, SixSeatRangeSource.Request.class);
        var plan = SixSeatRangeSource.plan(request);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", "pokerlab-six-seat-range-source/v1");
        report.put("trainerAdmission", false);
        report.put("gradingAuthority", false);
        report.put(
                "requestSha256",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        report.put("request", request);
        report.put("sourcePlanHash", plan.contentHash());
        report.put("payoffPlanHash", plan.payoffPlan().contentHash());
        report.put("spotHash", plan.spot().contentHash());
        report.put("plannedCfrWork", plan.work());
        report.put("plannedPayoffWork", plan.payoffPlan().work());
        report.put("solved", args[0].equals("solve"));
        if (args[0].equals("solve")) {
            var result = plan.execute();
            report.put("sourcePackHash", result.packHash());
            // Preserve the existing pack's numeric long seed losslessly as an opaque JSON string.
            report.put("sourcePackJson", result.packJson());
            report.put("cfrStatistics", result.statistics());
            report.put("quality", result.quality());
            report.put("observedSharedBoards", result.payoffs().observedSharedBoards());
            report.put(
                    "payoffBoardsFromPerDealEstimates",
                    result.payoffs().boardsFromPerDealEstimates());
        }
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        Files.write(output, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
