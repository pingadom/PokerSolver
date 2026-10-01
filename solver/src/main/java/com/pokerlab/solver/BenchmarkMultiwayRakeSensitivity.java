package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Compares a validated no-rake multiway policy with a fresh solve under an explicit rake rule. */
public final class BenchmarkMultiwayRakeSensitivity {
    private BenchmarkMultiwayRakeSensitivity() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkMultiwayRakeSensitivity <side-pot-pack.json> <rake-fraction> <cap-bb> <no-flop-no-drop:true|false> <iterations>");
        var pack = MultiwayPackJson.readSidePot(Files.readString(Path.of(args[0])));
        var rakeRule =
                new CashRakeRule(
                        Double.parseDouble(args[1]),
                        Double.parseDouble(args[2]),
                        switch (args[3]) {
                            case "true" -> true;
                            case "false" -> false;
                            default ->
                                    throw new IllegalArgumentException(
                                            "no-flop-no-drop must be true or false");
                        });
        var report =
                MultiwayRakeSensitivity.assess(
                        pack, rakeRule, Integer.parseInt(args[4]), CfrSolver.Variant.CFR_PLUS);
        System.out.printf(
                Locale.ROOT,
                "Spot %s (%d seats, %d exact/sampled payoff entries, source %s): rake %.2f%% cap %.3fbb, no-flop-no-drop %s%n",
                pack.spot().id(),
                pack.spot().seats().size(),
                pack.payoffs().size(),
                pack.payoffMethod(),
                100 * rakeRule.fraction(),
                rakeRule.capBb(),
                rakeRule.noFlopNoDrop());
        System.out.printf(
                Locale.ROOT,
                "NashConv in finite game: source no-rake %.6fbb; source policy under rake %.6fbb; raked re-solve %.6fbb%n",
                report.noRakeNashConvBb(),
                report.transferredNashConvBb(),
                report.resolvedNashConvBb());
        System.out.printf(
                Locale.ROOT,
                "Expected rake: source policy %.6fbb; raked re-solve %.6fbb%n",
                report.transferredExpectedRakeBb(),
                report.resolvedExpectedRakeBb());
        System.out.printf(
                Locale.ROOT,
                "Maximum terminal payoff sampling SE: source %.6fbb; raked %.6fbb%n",
                report.sourceMaxTerminalPayoffSEBb(),
                report.rakedMaxTerminalPayoffSEBb());
        for (int seat = 0; seat < pack.spot().seats().size(); seat++)
            System.out.printf(
                    Locale.ROOT,
                    "%s: no-rake EV %+.6fbb, transfer EV %+.6fbb (deviation %+.6fbb), re-solve EV %+.6fbb (deviation %+.6fbb)%n",
                    pack.spot().seats().get(seat),
                    report.noRakeUtilitiesBb().get(seat),
                    report.transferredUtilitiesBb().get(seat),
                    report.transferredDeviationGainsBb().get(seat),
                    report.resolvedUtilitiesBb().get(seat),
                    report.resolvedDeviationGainsBb().get(seat));
        System.out.println(
                "This is a bounded forced-shove call/fold game and a declared rake assumption. It is not a general 6-max cash chart or a trainer pack.");
    }
}
