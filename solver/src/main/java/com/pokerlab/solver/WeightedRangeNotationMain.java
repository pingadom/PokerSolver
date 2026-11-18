package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.file.*;
import java.util.*;

/** Compile explicit range assumptions without starting equity enumeration or strategy solving. */
public final class WeightedRangeNotationMain {
    private WeightedRangeNotationMain() {}

    public static void main(String[] args) throws Exception {
        if (args == null
                || args.length < 2
                || args.length > 3
                || args[1] == null
                || args[1].isBlank())
            throw new IllegalArgumentException(
                    "Usage: <range> <NEW-output.json> [comma-separated-known-cards]");
        var range = WeightedRangeNotation.compile(args[0]);
        var known = new ArrayList<Card>();
        if (args.length == 3) {
            if (args[2] == null || args[2].length() > 156 || args[2].isBlank())
                throw new IllegalArgumentException("Bounded nonempty known-card list required");
            for (String card : args[2].split(",", -1)) known.add(Card.parse(card.strip()));
        }
        var conditioned = range.conditionOnDeadCards(known);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", "pokerlab-weighted-range-input/v1");
        report.put("weightScope", WeightedRangeNotation.WEIGHT_SCOPE);
        report.put("trainerAdmission", false);
        report.put("gradingAuthority", false);
        report.put("sourceRangeHash", range.contentHash());
        report.put("knownCards", known.stream().map(Card::compact).sorted().toList());
        report.put("inputCombos", conditioned.inputComboCount());
        report.put("blockedCombos", conditioned.blockedComboCount());
        report.put("retainedCombos", conditioned.combos().size());
        report.put("inputWeightMass", conditioned.inputWeightMass());
        report.put("blockedWeightMass", conditioned.blockedWeightMass());
        report.put("retainedWeightMass", conditioned.retainedWeightMass());
        report.put("hasSupport", conditioned.hasSupport());
        report.put(
                "combos",
                conditioned.combos().stream()
                        .map(c -> Map.of("cards", c.key(), "weight", c.weight()))
                        .toList());
        byte[] bytes =
                SixMaxTexturePayoffTable.mapper()
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(report);
        Files.write(
                Path.of(args[1]), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
