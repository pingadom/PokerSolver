package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/**
 * Private offline planning or explicit payoff evaluation, with no strategy solving or admission.
 */
public final class SixSeatPayoffWorkMain {
    private SixSeatPayoffWorkMain() {}

    public static void main(String[] args) throws Exception {
        if (args == null
                || args.length != 11
                || (!"plan".equals(args[0]) && !"evaluate".equals(args[0]))
                || args[1] == null
                || args[1].isBlank())
            throw new IllegalArgumentException(
                    "Usage: plan|evaluate NEW-output.json exact|exact-suit-reuse|shared:TRIALS:SEED MAX-BOARDS MAX-HAND-EVALUATIONS UTG HJ CO BTN SB BB");
        Path output = Path.of(args[1]);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new FileAlreadyExistsException(output.toString());
        var parts = Objects.requireNonNull(args[2], "method").split(":", -1);
        SixSeatPayoffWork.Configuration config;
        if (parts.length == 1 && parts[0].equals("exact"))
            config = SixSeatPayoffWork.Configuration.exact();
        else if (parts.length == 1 && parts[0].equals("exact-suit-reuse"))
            config = SixSeatPayoffWork.Configuration.exactSuitReuse();
        else if (parts.length == 3 && parts[0].equals("shared"))
            config =
                    SixSeatPayoffWork.Configuration.shared(
                            Integer.parseInt(parts[1]), Long.parseLong(parts[2]));
        else
            throw new IllegalArgumentException(
                    "Explicit exact, exact-suit-reuse or shared:TRIALS:SEED method required");
        var budget = new SixSeatPayoffWork.Budget(Long.parseLong(args[3]), Long.parseLong(args[4]));
        var input =
                SixSeatRangePreflight.plan(
                        List.of(Arrays.copyOfRange(args, 5, 11)),
                        SixSeatRangePreflight.Limits.defaults());
        var plan = SixSeatPayoffWork.plan(input, config, budget);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", "pokerlab-six-seat-payoff-work/v1");
        report.put("trainerAdmission", false);
        report.put("gradingAuthority", false);
        report.put("inputHash", input.inputHash());
        report.put("planHash", plan.contentHash());
        report.put("configuration", config.metadata());
        report.put("priorScope", input.priorScope());
        report.put("ranges", input.ranges());
        report.put("deals", input.deals());
        report.put("seatSupport", input.seatSupport());
        report.put("budget", budget);
        report.put("plannedWork", plan.work());
        boolean suitReuse = config.method() == SixSeatPayoffWork.Method.EXACT_SUIT_REUSE;
        if (suitReuse) report.put("plannedEvaluatedDeals", plan.evaluatedDeals());
        report.put("executed", args[0].equals("evaluate"));
        if (args[0].equals("evaluate")) {
            var result = plan.execute();
            report.put("boardsFromPerDealEstimates", result.boardsFromPerDealEstimates());
            report.put("observedSharedBoards", result.observedSharedBoards());
            if (suitReuse)
                report.put("boardsFromEvaluatedDeals", result.boardsFromEvaluatedDeals());
            report.put("payoffs", result.rows());
        }
        byte[] bytes =
                SixMaxTexturePayoffTable.mapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(report);
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
