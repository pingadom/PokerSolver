package com.pokerlab.solver;

import java.util.*;

/** Owned complete private-support payoff work; never a solved policy or trainer admission. */
public final class SixSeatPayoffWork {
    public static final long EXACT_BOARDS_PER_DEAL =
            SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL; // choose(52 - 12, 5)
    public static final long MAX_BOARDS = 10_000_000;
    public static final long MAX_HAND_EVALUATIONS = 60_000_000;
    public static final int ACTIVE_SUBSETS = 57; // 2^6 - six singletons - empty

    public enum Method {
        EXACT_ENUMERATION,
        SHARED_BOARD_MONTE_CARLO,
        EXACT_SUIT_REUSE;

        public boolean isExact() {
            return this == EXACT_ENUMERATION || this == EXACT_SUIT_REUSE;
        }
    }

    public record Configuration(Method method, int trialsPerDeal, long seed) {
        public Configuration {
            Objects.requireNonNull(method, "method");
            if (method.isExact() ? trialsPerDeal != 0 || seed != 0 : trialsPerDeal < 1)
                throw new IllegalArgumentException(
                        "Exact needs zero trials/seed; sampled needs positive trials");
        }

        public static Configuration exact() {
            return new Configuration(Method.EXACT_ENUMERATION, 0, 0);
        }

        public static Configuration exactSuitReuse() {
            return new Configuration(Method.EXACT_SUIT_REUSE, 0, 0);
        }

        public static Configuration shared(int trials, long seed) {
            return new Configuration(Method.SHARED_BOARD_MONTE_CARLO, trials, seed);
        }

        public Map<String, Object> metadata() {
            return Map.of(
                    "method",
                    method.name(),
                    "trialsPerDeal",
                    trialsPerDeal,
                    "seed",
                    Long.toString(seed));
        }
    }

    public record Budget(long maximumBoards, long maximumHandEvaluations) {
        public Budget {
            if (maximumBoards < 1
                    || maximumBoards > MAX_BOARDS
                    || maximumHandEvaluations < 1
                    || maximumHandEvaluations > MAX_HAND_EVALUATIONS)
                throw new IllegalArgumentException("Payoff budgets can only lower the hard limits");
        }

        public static Budget defaults() {
            return new Budget(MAX_BOARDS, MAX_HAND_EVALUATIONS);
        }
    }

    public record Work(
            int jointDeals,
            long boardsPerDeal,
            long boardEvaluations,
            long handEvaluations,
            int activeSubsetEntries) {}

    public static final class BudgetExceeded extends IllegalArgumentException {
        private final Work required;
        private final Budget budget;

        private BudgetExceeded(Work required, Budget budget) {
            super("Complete payoff work exceeds budget; no execution or sampled fallback");
            this.required = required;
            this.budget = budget;
        }

        public Work required() {
            return required;
        }

        public Budget budget() {
            return budget;
        }
    }

    public record Row(List<String> hands, int activeMask, MultiwayShowdownEstimate estimate) {
        public Row {
            hands = List.copyOf(hands);
        }
    }

    private record Key(List<String> hands, int activeMask) {}

    public static final class Result {
        private final Plan plan;
        private final List<Row> rows;
        private final long boardsFromPerDealEstimates;
        private final Long observedSharedBoards;
        private final long boardsFromEvaluatedDeals;
        private final Map<Key, MultiwayShowdownEstimate> table;

        private Result(
                Plan plan,
                List<Row> rows,
                long boardsFromPerDealEstimates,
                Long observedSharedBoards,
                long boardsFromEvaluatedDeals) {
            this.plan = plan;
            this.rows = List.copyOf(rows);
            this.boardsFromPerDealEstimates = boardsFromPerDealEstimates;
            this.observedSharedBoards = observedSharedBoards;
            this.boardsFromEvaluatedDeals = boardsFromEvaluatedDeals;
            var values = new LinkedHashMap<Key, MultiwayShowdownEstimate>();
            for (var row : rows)
                if (values.put(new Key(row.hands(), row.activeMask()), row.estimate()) != null)
                    throw new IllegalStateException("Duplicate owned payoff entry");
            table = Map.copyOf(values);
        }

        public Plan plan() {
            return plan;
        }

        public List<Row> rows() {
            return rows;
        }

        public long boardsFromPerDealEstimates() {
            return boardsFromPerDealEstimates;
        }

        public Long observedSharedBoards() {
            return observedSharedBoards;
        }

        /** Sums trial counts only once for each physical oracle pass. */
        public long boardsFromEvaluatedDeals() {
            return boardsFromEvaluatedDeals;
        }

        /** Shares depend on physical hands and mask, not their prior frequencies. */
        public MultiwayShowdownOracle oracle() {
            return (hands, mask) -> {
                if (hands == null || hands.size() != 6 || hands.stream().anyMatch(Objects::isNull))
                    throw new IllegalArgumentException("Six owned physical hands required");
                var value =
                        table.get(new Key(hands.stream().map(WeightedCombo::key).toList(), mask));
                if (value == null)
                    throw new IllegalArgumentException("Unknown ordered deal or active subset");
                return value;
            };
        }
    }

    public static final class Plan {
        private final SixSeatRangePreflight.Result input;
        private final Configuration configuration;
        private final Budget budget;
        private final Work work;
        private final String contentHash;
        private final List<Integer> representatives;
        private final int evaluatedDeals;

        private Plan(
                SixSeatRangePreflight.Result input, Configuration configuration, Budget budget) {
            this.input = input;
            this.configuration = configuration;
            this.budget = budget;
            int deals = input.deals().size();
            representatives =
                    configuration.method() == Method.EXACT_SUIT_REUSE
                            ? SixSeatSuitClasses.representatives(input)
                            : java.util.stream.IntStream.range(0, deals).boxed().toList();
            evaluatedDeals = (int) representatives.stream().distinct().count();
            long perDeal =
                    configuration.method().isExact()
                            ? EXACT_BOARDS_PER_DEAL
                            : configuration.trialsPerDeal();
            long boards = Math.multiplyExact(evaluatedDeals, perDeal);
            work =
                    new Work(
                            deals,
                            perDeal,
                            boards,
                            Math.multiplyExact(boards, 6),
                            Math.multiplyExact(deals, ACTIVE_SUBSETS));
            if (boards > budget.maximumBoards()
                    || work.handEvaluations() > budget.maximumHandEvaluations())
                throw new BudgetExceeded(work, budget);
            contentHash =
                    MultiwayCallSpot.sha256(
                            "six-seat-payoff-work/v1|"
                                    + input.inputHash()
                                    + "|"
                                    + configuration.method().name()
                                    + "|"
                                    + configuration.trialsPerDeal()
                                    + "|"
                                    + configuration.seed());
        }

        public SixSeatRangePreflight.Result input() {
            return input;
        }

        public Configuration configuration() {
            return configuration;
        }

        public Budget budget() {
            return budget;
        }

        public Work work() {
            return work;
        }

        /** Physical deal streams to evaluate, with all original worlds retained in work(). */
        public int evaluatedDeals() {
            return evaluatedDeals;
        }

        public String contentHash() {
            return contentHash;
        }

        /**
         * Explicit execution only. The plan owns the oracle, its cache and complete query order.
         */
        public Result execute() {
            var shared =
                    configuration.method() == Method.SHARED_BOARD_MONTE_CARLO
                            ? new SharedBoardMultiwayShowdownOracle(
                                    configuration.trialsPerDeal(),
                                    configuration.seed(),
                                    work.jointDeals())
                            : null;
            MultiwayShowdownOracle oracle =
                    shared != null ? shared : new ExactMultiwayShowdownOracle();
            var rows = new ArrayList<Row>();
            var evaluated = new MultiwayShowdownEstimate[work.jointDeals()][64];
            long logicalBoards = 0;
            long evaluatedBoards = 0;
            for (int world = 0; world < work.jointDeals(); world++) {
                var deal = input.deals().get(world);
                var keys = deal.hands().stream().map(WeightedCombo::key).toList();
                int representative = representatives.get(world);
                for (int mask = 1; mask < 64; mask++) {
                    if (Integer.bitCount(mask) < 2) continue;
                    MultiwayShowdownEstimate value;
                    if (representative == world) {
                        value = oracle.estimate(deal.hands(), mask);
                        validate(value, mask, work.boardsPerDeal());
                        evaluated[world][mask] = value;
                        if (mask == 3) evaluatedBoards += value.trials();
                    } else {
                        value = evaluated[representative][mask];
                    }
                    if (mask == 3) logicalBoards += value.trials();
                    rows.add(new Row(keys, mask, value));
                }
            }
            if (rows.size() != work.activeSubsetEntries()
                    || logicalBoards != Math.multiplyExact(work.jointDeals(), work.boardsPerDeal())
                    || evaluatedBoards != work.boardEvaluations()
                    || (shared != null && shared.boardsEvaluated() != evaluatedBoards))
                throw new IllegalStateException("Actual owned payoff execution differs from plan");
            return new Result(
                    this,
                    rows,
                    logicalBoards,
                    shared == null ? null : shared.boardsEvaluated(),
                    evaluatedBoards);
        }
    }

    private SixSeatPayoffWork() {}

    public static Plan plan(
            SixSeatRangePreflight.Result input, Configuration configuration, Budget budget) {
        return new Plan(
                Objects.requireNonNull(input, "input"),
                Objects.requireNonNull(configuration, "configuration"),
                Objects.requireNonNull(budget, "budget"));
    }

    private static void validate(MultiwayShowdownEstimate value, int mask, long trials) {
        if (value == null
                || value.trials() != trials
                || value.shares().length != 6
                || value.standardErrors().length != 6)
            throw new IllegalStateException("Oracle returned an incomplete payoff estimate");
        double sum = 0;
        double[] shares = value.shares(), errors = value.standardErrors();
        for (int seat = 0; seat < 6; seat++) {
            if (!Double.isFinite(shares[seat])
                    || shares[seat] < 0
                    || shares[seat] > 1
                    || !Double.isFinite(errors[seat])
                    || errors[seat] < 0
                    || ((mask & (1 << seat)) == 0 && (shares[seat] != 0 || errors[seat] != 0)))
                throw new IllegalStateException("Invalid payoff share or error");
            sum += shares[seat];
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalStateException("Payoff shares do not conserve the pot");
    }
}
