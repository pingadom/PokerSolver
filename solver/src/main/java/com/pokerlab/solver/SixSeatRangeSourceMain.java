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
        if (args == null || args.length < 3)
            throw new IllegalArgumentException(
                    "Usage: plan|solve INPUT.json NEW-output.json, or compare|compare-trainer with exact work limits");
        int expectedArguments =
                switch (args[0]) {
                    case "plan", "solve" -> 3;
                    case "compare" -> 5;
                    case "compare-trainer" -> 10;
                    default -> throw new IllegalArgumentException("Invalid source command");
                };
        if (args.length != expectedArguments)
            throw new IllegalArgumentException("Invalid source argument count");
        boolean compare = "compare".equals(args[0]);
        boolean trainerAudit = "compare-trainer".equals(args[0]);
        var exactBudget =
                compare || trainerAudit
                        ? new SixSeatPayoffWork.Budget(
                                Long.parseLong(args[3]), Long.parseLong(args[4]))
                        : null;
        var reference =
                trainerAudit ? exactReference(args[5]) : SixSeatPayoffWork.Configuration.exact();
        var options =
                trainerAudit
                        ? new SixSeatTrainerActionAudit.Options(
                                Integer.parseInt(args[6]), Long.parseLong(args[7]),
                                Double.parseDouble(args[8]), Double.parseDouble(args[9]))
                        : null;
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
        report.put(
                "schemaVersion",
                switch (args[0]) {
                    case "compare" -> "pokerlab-six-seat-payoff-comparison/v1";
                    case "compare-trainer" -> "pokerlab-six-seat-trainer-action-audit/v1";
                    default -> "pokerlab-six-seat-range-source/v1";
                });
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
        boolean suitReuse = request.payoff().method() == SixSeatPayoffWork.Method.EXACT_SUIT_REUSE;
        if (suitReuse) report.put("plannedEvaluatedDeals", plan.payoffPlan().evaluatedDeals());
        if (compare) {
            var exactPlan = SixSeatPayoffComparison.planExactReference(plan, exactBudget);
            report.put("plannedExactReferenceWork", exactPlan.work());
            report.put("exactReferenceBudget", exactBudget);
            report.put(
                    "comparison",
                    SixSeatPayoffComparison.assess(plan.execute(), exactPlan.execute()));
        }
        if (trainerAudit) {
            var exactPlan =
                    SixSeatPayoffComparison.planExactReference(plan, reference, exactBudget);
            report.put("plannedExactReferenceWork", exactPlan.work());
            report.put("plannedExactEvaluatedDeals", exactPlan.evaluatedDeals());
            report.put("exactReferenceBudget", exactBudget);
            report.put("exactReferenceMethod", reference.method());
            report.put(
                    "trainerActionAudit",
                    SixSeatTrainerActionAudit.assess(plan.execute(), exactPlan.execute(), options));
        }
        if (!compare && !trainerAudit) report.put("solved", args[0].equals("solve"));
        if (args[0].equals("solve")) {
            var result = plan.execute();
            report.put("sourcePackHash", result.packHash());
            // Preserve the existing pack's numeric long seed losslessly as an opaque JSON string.
            report.put("sourcePackJson", result.packJson());
            report.put("cfrStatistics", result.statistics());
            report.put("quality", result.quality());
            if (suitReuse)
                report.put("boardsFromEvaluatedDeals", result.payoffs().boardsFromEvaluatedDeals());
            report.put("observedSharedBoards", result.payoffs().observedSharedBoards());
            report.put(
                    "payoffBoardsFromPerDealEstimates",
                    result.payoffs().boardsFromPerDealEstimates());
        }
        byte[] encoded = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        Files.write(output, encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static SixSeatPayoffWork.Configuration exactReference(String value) {
        return switch (value) {
            case "exact" -> SixSeatPayoffWork.Configuration.exact();
            case "exact-suit-reuse" -> SixSeatPayoffWork.Configuration.exactSuitReuse();
            default ->
                    throw new IllegalArgumentException("Reference needs exact or exact-suit-reuse");
        };
    }
}
