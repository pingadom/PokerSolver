package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.*;

/** Complete bounded card-legality planning before payoff construction or strategy solving. */
public final class SixSeatRangePreflight {
    public static final int MAX_CANDIDATE_CHECKS = 1_000_000;
    public static final String PRIOR_SCOPE =
            "COMPLETE_INPUT_PRODUCT_CONDITIONED_ON_CARD_LEGALITY_BINARY64/v1";

    public record Limits(int maximumJointDeals, int maximumCandidateChecks) {
        public Limits {
            if (maximumJointDeals < 1
                    || maximumJointDeals > SixMaxPreflopCheckdownGame.MAX_JOINT_DEALS
                    || maximumCandidateChecks < 1
                    || maximumCandidateChecks > MAX_CANDIDATE_CHECKS)
                throw new IllegalArgumentException(
                        "Only lower original world and preflight limits are allowed");
        }

        public static Limits defaults() {
            return new Limits(64, MAX_CANDIDATE_CHECKS);
        }
    }

    public record Work(long candidateChecks, int completedLegalDeals, Limits limits) {}

    public enum Reason {
        CANDIDATE_CHECK_CAP,
        JOINT_DEAL_CAP,
        NO_LEGAL_DEALS,
        PROBABILITY_UNDERFLOW
    }

    public static final class Rejection extends IllegalArgumentException {
        private final Reason reason;
        private final Work work;
        private final int legalDealLowerBound;

        private Rejection(Reason reason, Work work, int lowerBound) {
            super(
                    reason
                            + ": checks="
                            + work.candidateChecks()
                            + ", legal-deal lower bound="
                            + lowerBound
                            + "; no complete plan returned");
            this.reason = reason;
            this.work = work;
            legalDealLowerBound = lowerBound;
        }

        public Reason reason() {
            return reason;
        }

        public Work work() {
            return work;
        }

        public int legalDealLowerBound() {
            return legalDealLowerBound;
        }
    }

    public record JointDeal(List<WeightedCombo> hands, double probability, double logWeight) {
        public JointDeal {
            hands = List.copyOf(hands);
        }
    }

    public record SeatSupport(
            Seat seat,
            int inputCombos,
            int supportedCombos,
            List<String> unsupportedCombos,
            Map<String, Double> marginalProbabilities) {
        public SeatSupport {
            unsupportedCombos = List.copyOf(unsupportedCombos);
            marginalProbabilities =
                    Collections.unmodifiableMap(new LinkedHashMap<>(marginalProbabilities));
        }
    }

    public static final class Result {
        private final List<List<WeightedCombo>> ranges;
        private final List<String> rangeHashes;
        private final List<JointDeal> deals;
        private final List<SeatSupport> support;
        private final Work work;
        private final long nominalProducts;
        private final double logLegalWeightMass;
        private final String inputHash;

        private Result(
                List<WeightedRangeNotation.CompiledRange> inputs, List<RawDeal> raw, Work work) {
            ranges = inputs.stream().map(WeightedRangeNotation.CompiledRange::combos).toList();
            rangeHashes =
                    inputs.stream().map(WeightedRangeNotation.CompiledRange::contentHash).toList();
            this.work = work;
            long product = 1;
            for (var range : ranges) product = Math.multiplyExact(product, range.size());
            nominalProducts = product;
            double maximum = raw.stream().mapToDouble(RawDeal::logWeight).max().orElseThrow();
            double scaledMass =
                    raw.stream().mapToDouble(d -> Math.exp(d.logWeight() - maximum)).sum();
            logLegalWeightMass = maximum + Math.log(scaledMass);
            var weighted = new ArrayList<JointDeal>();
            for (var d : raw) {
                double probability = Math.exp(d.logWeight() - maximum) / scaledMass;
                if (!Double.isFinite(probability) || probability <= 0 || probability > 1)
                    throw new Rejection(Reason.PROBABILITY_UNDERFLOW, work, raw.size());
                weighted.add(new JointDeal(d.hands(), probability, d.logWeight()));
            }
            deals = List.copyOf(weighted);
            var rows = new ArrayList<SeatSupport>();
            for (int seat = 0; seat < 6; seat++) {
                var marginal = new LinkedHashMap<String, Double>();
                var unsupported = new ArrayList<String>();
                int supported = 0;
                for (var combo : ranges.get(seat)) {
                    final int index = seat;
                    var matching =
                            deals.stream()
                                    .filter(d -> d.hands().get(index).key().equals(combo.key()))
                                    .toList();
                    double probability =
                            matching.stream().mapToDouble(JointDeal::probability).sum();
                    if (matching.isEmpty()) unsupported.add(combo.key());
                    else supported++;
                    marginal.put(combo.key(), probability);
                }
                rows.add(
                        new SeatSupport(
                                Seat.values()[seat],
                                ranges.get(seat).size(),
                                supported,
                                unsupported,
                                marginal));
            }
            support = List.copyOf(rows);
            inputHash =
                    MultiwayCallSpot.sha256(
                            "pokerlab-six-seat-range-input/v1|"
                                    + PRIOR_SCOPE
                                    + "|"
                                    + String.join("|", rangeHashes));
        }

        public List<List<WeightedCombo>> ranges() {
            return ranges;
        }

        public List<String> rangeHashes() {
            return rangeHashes;
        }

        public List<JointDeal> deals() {
            return deals;
        }

        public List<SeatSupport> seatSupport() {
            return support;
        }

        public Work work() {
            return work;
        }

        public long nominalProducts() {
            return nominalProducts;
        }

        public double logLegalWeightMass() {
            return logLegalWeightMass;
        }

        public String inputHash() {
            return inputHash;
        }

        public String priorScope() {
            return PRIOR_SCOPE;
        }

        public boolean allInputCombosSupported() {
            return support.stream().allMatch(s -> s.unsupportedCombos().isEmpty());
        }

        /** Existing spot validation still enforces public-state and private/public-product caps. */
        public SixMaxPreflopResearchSpot toResearchSpot(
                String id, SixMaxPreflopBetting.Rules rules, CashRakeRule rake) {
            if (!allInputCombosSupported())
                throw new IllegalArgumentException(
                        "Unsupported declared combos must be resolved explicitly; no silent range pruning");
            return new SixMaxPreflopResearchSpot(
                    id, rules, ranges, rake, SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
        }
    }

    private record RawDeal(List<WeightedCombo> hands, double logWeight) {
        private RawDeal {
            hands = List.copyOf(hands);
        }
    }

    private static final class Enumeration {
        final List<WeightedRangeNotation.CompiledRange> inputs;
        final Limits limits;
        final List<RawDeal> deals = new ArrayList<>();
        long checks;

        Enumeration(List<WeightedRangeNotation.CompiledRange> inputs, Limits limits) {
            this.inputs = inputs;
            this.limits = limits;
        }

        Work work() {
            return new Work(checks, deals.size(), limits);
        }

        void visit(int seat, long used, List<WeightedCombo> hands, double logWeight) {
            if (seat == 6) {
                if (deals.size() == limits.maximumJointDeals())
                    throw new Rejection(Reason.JOINT_DEAL_CAP, work(), deals.size() + 1);
                deals.add(new RawDeal(hands, logWeight));
                return;
            }
            for (var combo : inputs.get(seat).combos()) {
                if (checks == limits.maximumCandidateChecks())
                    throw new Rejection(Reason.CANDIDATE_CHECK_CAP, work(), deals.size());
                checks++;
                long mask = mask(combo.first()) | mask(combo.second());
                if ((used & mask) != 0) continue;
                hands.add(combo);
                visit(seat + 1, used | mask, hands, logWeight + Math.log(combo.weight()));
                hands.removeLast();
            }
        }
    }

    private SixSeatRangePreflight() {}

    public static Result plan(List<String> expressions, Limits limits) {
        if (expressions == null || expressions.size() != 6 || limits == null)
            throw new IllegalArgumentException("Six explicit seat ranges and limits required");
        expressions = List.copyOf(expressions);
        var inputs = expressions.stream().map(WeightedRangeNotation::compile).toList();
        var enumeration = new Enumeration(inputs, limits);
        enumeration.visit(0, 0, new ArrayList<>(), 0);
        if (enumeration.deals.isEmpty())
            throw new Rejection(Reason.NO_LEGAL_DEALS, enumeration.work(), 0);
        return new Result(inputs, enumeration.deals, enumeration.work());
    }

    private static long mask(Card card) {
        return 1L << (card.rank().ordinal() * 4 + card.suit().ordinal());
    }
}
