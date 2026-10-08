package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline selection/replay from a bound prior joint policy; the menu is not a quality certificate.
 */
public final class SixMaxHistoryPhysicalMenuSelectionMain {
    private SixMaxHistoryPhysicalMenuSelectionMain() {}

    public static void main(String[] args) throws Exception {
        boolean select = args.length == 8 && args[0].equals("select");
        boolean replay = args.length == 7 && args[0].equals("replay");
        if (!select && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalMenuSelectionMain select <source> <rank-table> <reference-suit-table> <reference-checkpoint> <menu> <evidence> <fraction> | replay <source> <rank-table> <reference-suit-table> <reference-checkpoint> <menu> <evidence>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(6)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        double fraction = select ? Double.parseDouble(args[7]) : 0;
        if (select
                && (!Double.isFinite(fraction)
                        || fraction <= 0
                        || fraction > 1
                        || Files.exists(paths.get(4))
                        || Files.exists(paths.get(5))))
            throw new IllegalArgumentException(
                    "Selection requires a valid fraction and new outputs");
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var priorTable = SixMaxSuitRefinementPayoffTable.read(paths.get(2), source, parent);
        var reference = SixMaxSuitRefinementStudy.read(paths.get(3), source, parent, priorTable);
        SixMaxHistoryPhysicalMenuSelection.Evidence evidence;
        if (select) {
            evidence =
                    SixMaxHistoryPhysicalMenuSelection.select(
                            source, parent, reference.solution(), reference.selections(), fraction);
            // Serialize both within their caps before writing either output.
            if (SixMaxTextureStudy.json(evidence)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                    .length
                            > SixMaxHistoryPhysicalMenuSelection.MAX_EVIDENCE_BYTES
                    || SixMaxTextureStudy.json(evidence.menu())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                    .length
                            > SixMaxHistoryPhysicalPayoffTable.MAX_MENU_BYTES)
                throw new IllegalArgumentException("Menu selection output byte cap exceeded");
            SixMaxHistoryPhysicalPayoffTable.writeMenu(paths.get(4), evidence.menu());
            SixMaxHistoryPhysicalMenuSelection.write(paths.get(5), evidence);
        } else {
            evidence =
                    SixMaxHistoryPhysicalMenuSelection.replay(
                            paths.get(5), source, parent, reference.solution());
            if (!evidence.menu().equals(SixMaxHistoryPhysicalPayoffTable.readMenu(paths.get(4))))
                throw new IllegalArgumentException("Menu differs from replayed selection evidence");
        }
        System.out.println(
                "VALIDATION_ONLY frozen material HU fraction "
                        + evidence.selectedMaterialAllHeadsUpFraction()
                        + " sizing "
                        + evidence.sizing());
    }
}
