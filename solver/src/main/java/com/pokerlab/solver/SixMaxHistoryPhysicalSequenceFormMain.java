package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Offline bounded sequence-form with exact payoff, predecessor, matrix and behavioral-policy
 * replay.
 */
public final class SixMaxHistoryPhysicalSequenceFormMain {
    private SixMaxHistoryPhysicalSequenceFormMain() {}

    public static void main(String[] args) throws Exception {
        boolean derived = args.length > 0 && args[0].endsWith("-derived");
        boolean refine =
                args.length == (derived ? 12 : 10)
                        && args[0].equals(derived ? "refine-derived" : "refine");
        boolean replay =
                args.length == (derived ? 10 : 8)
                        && args[0].equals(derived ? "replay-derived" : "replay");
        if (!refine && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalSequenceFormMain <refine|replay|refine-derived|replay-derived> <source> <rank-table> <physical-table> <checkpoint> <study-report> [<cfr-policy> <cfr-report> for -derived] <sequence-form-policy> <sequence-form-report> [<maximum-cases> <target-gap-bb> for refine]");
        int count = derived ? 9 : 7, output = derived ? 7 : 5;
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(count)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var settings =
                refine
                        ? new SixMaxHistoryPhysicalSequenceForm.Settings(
                                Integer.parseInt(args[count + 1]),
                                Double.parseDouble(args[count + 2]))
                        : null;
        if (refine && (Files.exists(paths.get(output)) || Files.exists(paths.get(output + 1))))
            throw new IllegalArgumentException("Sequence-form outputs must be new paths");
        SixMaxHistoryPhysicalSequenceForm.Result result;
        if (replay) result = replay(paths, derived);
        else {
            var predecessor = SixMaxHistoryPhysicalConditionalRefinementMain.predecessor(paths);
            Consumer<SixMaxHistoryPhysicalSequenceForm.Branch> progress =
                    b ->
                            System.out.println(
                                    "SEQUENCE_FORM "
                                            + b.observationKey()
                                            + " gap="
                                            + b.before().nashConvBb()
                                            + " -> "
                                            + b.after().nashConvBb()
                                            + " pivots="
                                            + (b.solve().firstLp().work().pivots()
                                                    + b.solve().secondLp().work().pivots()));
            result =
                    derived
                            ? SixMaxHistoryPhysicalSequenceForm.refine(
                                    SixMaxHistoryPhysicalConditionalRefinement.replay(
                                            paths.get(5), paths.get(6), predecessor),
                                    settings,
                                    progress)
                            : SixMaxHistoryPhysicalSequenceForm.refine(
                                    predecessor, settings, progress);
            SixMaxHistoryPhysicalSequenceForm.write(
                    paths.get(output), paths.get(output + 1), result);
        }
        System.out.println(
                "VALIDATION_ONLY trainerAdmission=false "
                        + (result.report().accepted()
                                ? "ACCEPTED"
                                : "REJECTED " + result.report().rejectionReasons())
                        + " cases="
                        + result.report().branches().size()
                        + " parent="
                        + result.report().after().parentWitness().parentQuality().nashConvBb());
    }

    static SixMaxHistoryPhysicalSequenceForm.Result replay(List<Path> paths, boolean derived)
            throws Exception {
        var predecessor = SixMaxHistoryPhysicalConditionalRefinementMain.predecessor(paths);
        return derived
                ? SixMaxHistoryPhysicalSequenceForm.replay(
                        paths.get(7),
                        paths.get(8),
                        SixMaxHistoryPhysicalConditionalRefinement.replay(
                                paths.get(5), paths.get(6), predecessor))
                : SixMaxHistoryPhysicalSequenceForm.replay(paths.get(5), paths.get(6), predecessor);
    }
}
