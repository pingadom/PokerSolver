package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Rank;
import com.pokerlab.core.card.Suit;
import java.util.*;

/** Bounded physical range inputs; these are assumptions, never solved action frequencies. */
public final class WeightedRangeNotation {
    public static final String WEIGHT_SCOPE = "PER_COMBO_RELATIVE_FREQUENCY_UNNORMALIZED/v1";
    public static final int MAX_CHARACTERS = 32768;
    public static final int MAX_TERMS = 1326;
    private static final String RANKS = "23456789TJQKA";
    private static final List<String> HAND_CLASSES = grid();
    private static final Map<String, List<WeightedCombo>> PHYSICAL = physical();

    public record ClassCoverage(String handClass, int physicalCombos, double weightMass) {}

    public static final class CompiledRange {
        private final List<WeightedCombo> combos;
        private final Map<String, ClassCoverage> coverage;
        private final String contentHash;

        private CompiledRange(Collection<WeightedCombo> input) {
            combos = List.copyOf(input);
            var rows = new LinkedHashMap<String, ClassCoverage>();
            for (String label : HAND_CLASSES) {
                var selected = combos.stream().filter(c -> handClass(c).equals(label)).toList();
                if (!selected.isEmpty())
                    rows.put(label, new ClassCoverage(label, selected.size(), mass(selected)));
            }
            coverage = Collections.unmodifiableMap(rows);
            StringBuilder text =
                    new StringBuilder("pokerlab-weighted-physical-range/v1|").append(WEIGHT_SCOPE);
            for (var combo : combos)
                text.append('\n')
                        .append(combo.key())
                        .append('@')
                        .append(Double.toHexString(combo.weight()));
            contentHash = MultiwayCallSpot.sha256(text.toString());
        }

        public List<WeightedCombo> combos() {
            return combos;
        }

        public Map<String, ClassCoverage> coverage() {
            return coverage;
        }

        public double weightMass() {
            return mass(combos);
        }

        public String contentHash() {
            return contentHash;
        }

        public String weightScope() {
            return WEIGHT_SCOPE;
        }

        /** Known cards remove physical support without inventing a normalized joint prior. */
        public ConditionedRange conditionOnDeadCards(List<Card> deadCards) {
            if (deadCards == null || deadCards.size() > 52)
                throw new IllegalArgumentException("At most52 distinct known cards required");
            Set<Card> dead = new HashSet<>();
            for (var card : deadCards)
                if (card == null || !dead.add(card))
                    throw new IllegalArgumentException("Null or duplicate known card");
            var kept = new ArrayList<WeightedCombo>();
            var blocked = new ArrayList<WeightedCombo>();
            for (var combo : combos)
                (dead.contains(combo.first()) || dead.contains(combo.second()) ? blocked : kept)
                        .add(combo);
            return new ConditionedRange(combos.size(), weightMass(), kept, blocked);
        }
    }

    public static final class ConditionedRange {
        private final int inputComboCount;
        private final double inputWeightMass;
        private final List<WeightedCombo> combos;
        private final int blockedComboCount;
        private final double blockedWeightMass;

        private ConditionedRange(
                int inputComboCount,
                double inputWeightMass,
                List<WeightedCombo> kept,
                List<WeightedCombo> blocked) {
            this.inputComboCount = inputComboCount;
            this.inputWeightMass = inputWeightMass;
            combos = List.copyOf(kept);
            blockedComboCount = blocked.size();
            blockedWeightMass = mass(blocked);
        }

        public int inputComboCount() {
            return inputComboCount;
        }

        public double inputWeightMass() {
            return inputWeightMass;
        }

        public List<WeightedCombo> combos() {
            return combos;
        }

        public int blockedComboCount() {
            return blockedComboCount;
        }

        public double blockedWeightMass() {
            return blockedWeightMass;
        }

        public double retainedWeightMass() {
            return mass(combos);
        }

        public boolean hasSupport() {
            return !combos.isEmpty();
        }

        public String weightScope() {
            return WEIGHT_SCOPE;
        }

        public List<WeightedCombo> requireSupportedCombos() {
            if (!hasSupport())
                throw new IllegalArgumentException("All declared range combos are blocked");
            return combos;
        }
    }

    private WeightedRangeNotation() {}

    /** Grid order is AA,AKs,...,A2s,AKo,KK,... with pairs on the diagonal. */
    public static List<String> handClasses() {
        return HAND_CLASSES;
    }

    public static String handClass(WeightedCombo combo) {
        Objects.requireNonNull(combo, "combo");
        int a = combo.first().rank().value(), b = combo.second().rank().value();
        char high = RANKS.charAt(Math.max(a, b) - 2), low = RANKS.charAt(Math.min(a, b) - 2);
        return ""
                + high
                + low
                + (a == b ? "" : combo.first().suit() == combo.second().suit() ? "s" : "o");
    }

    /** Commas form a union; @decimal weights apply to each physical combo. Overlaps reject. */
    public static CompiledRange compile(String expression) {
        if (expression == null || expression.isBlank() || expression.length() > MAX_CHARACTERS)
            throw new IllegalArgumentException("Nonempty bounded range expression required");
        String[] terms = expression.split(",", -1);
        if (terms.length > MAX_TERMS) throw new IllegalArgumentException("Range term cap exceeded");
        var result = new TreeMap<String, WeightedCombo>();
        for (String term : terms) {
            String[] parts = term.strip().split("@", -1);
            if (parts.length > 2 || parts[0].isBlank())
                throw new IllegalArgumentException("Malformed range term");
            double weight = 1.;
            if (parts.length == 2) {
                String literal = parts[1].strip();
                if (!literal.matches("(?:0|1)(?:\\.[0-9]+)?"))
                    throw new IllegalArgumentException("Use a decimal per-combo weight in(0,1]");
                var exact = new java.math.BigDecimal(literal);
                if (exact.signum() <= 0 || exact.compareTo(java.math.BigDecimal.ONE) > 0)
                    throw new IllegalArgumentException("Decimal per-combo weight must be in(0,1]");
                weight = exact.doubleValue();
                if (!Double.isFinite(weight) || weight <= 0 || weight > 1)
                    throw new IllegalArgumentException("Per-combo weight must be in(0,1]");
            }
            String selection = parts[0].strip();
            List<WeightedCombo> selected;
            if (selection.matches("[2-9TJQKA][cdhs][2-9TJQKA][cdhs]"))
                selected =
                        List.of(
                                new WeightedCombo(
                                        Card.parse(selection.substring(0, 2)),
                                        Card.parse(selection.substring(2, 4)),
                                        1));
            else {
                var values = new ArrayList<WeightedCombo>();
                for (String label : expand(selection)) values.addAll(PHYSICAL.get(label));
                selected = values;
            }
            for (var combo : selected) {
                var weighted = new WeightedCombo(combo.first(), combo.second(), weight);
                if (result.putIfAbsent(weighted.key(), weighted) != null)
                    throw new IllegalArgumentException(
                            "Overlapping physical combo: " + weighted.key());
            }
        }
        return new CompiledRange(result.values());
    }

    private static List<String> expand(String token) {
        if (token.equals("*")) return HAND_CLASSES;
        if (token.endsWith("+")) {
            String start = token.substring(0, token.length() - 1);
            validClass(start);
            String end =
                    start.length() == 2
                            ? "AA"
                            : ""
                                    + start.charAt(0)
                                    + RANKS.charAt(RANKS.indexOf(start.charAt(0)) - 1)
                                    + start.charAt(2);
            return span(start, end);
        }
        String[] ends = token.split("-", -1);
        if (ends.length == 1) {
            validClass(token);
            return List.of(token);
        }
        if (ends.length != 2) throw new IllegalArgumentException("One class interval required");
        return span(ends[0], ends[1]);
    }

    private static List<String> span(String first, String last) {
        validClass(first);
        validClass(last);
        boolean pair = first.length() == 2;
        if (pair != (last.length() == 2)
                || (!pair
                        && (first.charAt(0) != last.charAt(0)
                                || first.charAt(2) != last.charAt(2))))
            throw new IllegalArgumentException(
                    "Intervals require pairs or a fixed high rank and suitedness");
        int a = RANKS.indexOf(first.charAt(pair ? 0 : 1)),
                b = RANKS.indexOf(last.charAt(pair ? 0 : 1));
        var result = new ArrayList<String>();
        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
            char rank = RANKS.charAt(i);
            result.add(pair ? "" + rank + rank : "" + first.charAt(0) + rank + first.charAt(2));
        }
        return List.copyOf(result);
    }

    private static void validClass(String value) {
        if (!PHYSICAL.containsKey(value))
            throw new IllegalArgumentException(
                    "Use a canonical pair, suited or offsuit class: " + value);
    }

    private static double mass(List<WeightedCombo> combos) {
        return combos.stream().mapToDouble(WeightedCombo::weight).sum();
    }

    private static List<String> grid() {
        var labels = new ArrayList<String>();
        for (int row = 12; row >= 0; row--)
            for (int col = 12; col >= 0; col--)
                labels.add(
                        row == col
                                ? "" + RANKS.charAt(row) + RANKS.charAt(col)
                                : ""
                                        + RANKS.charAt(Math.max(row, col))
                                        + RANKS.charAt(Math.min(row, col))
                                        + (row > col ? "s" : "o"));
        return List.copyOf(labels);
    }

    private static Map<String, List<WeightedCombo>> physical() {
        var deck = new ArrayList<Card>();
        for (var rank : Rank.values())
            for (var suit : Suit.values()) deck.add(new Card(rank, suit));
        var groups = new LinkedHashMap<String, List<WeightedCombo>>();
        for (String label : HAND_CLASSES) groups.put(label, new ArrayList<>());
        for (int i = 0; i < deck.size(); i++)
            for (int j = i + 1; j < deck.size(); j++) {
                var combo = new WeightedCombo(deck.get(i), deck.get(j), 1);
                groups.get(handClass(combo)).add(combo);
            }
        var result = new LinkedHashMap<String, List<WeightedCombo>>();
        groups.forEach((label, combos) -> result.put(label, List.copyOf(combos)));
        return Collections.unmodifiableMap(result);
    }
}
