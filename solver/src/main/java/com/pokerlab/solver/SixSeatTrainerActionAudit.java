package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Measures conditional trainer feedback for one frozen policy under two matched payoff tables. */
public final class SixSeatTrainerActionAudit {
    public static final int MAX_DRAWS = 512;

    public record Options(
            int draws, long firstSeed, double acceptableLossBb, double maximumLossDriftBb) {
        public Options {
            if (draws < 1 || draws > MAX_DRAWS)
                throw new IllegalArgumentException("Trainer audit needs 1 to 512 question draws");
            if (firstSeed > Long.MAX_VALUE - (draws - 1L))
                throw new IllegalArgumentException("Question seeds exceed the signed 64-bit range");
            requireNonnegative(acceptableLossBb);
            requireNonnegative(maximumLossDriftBb);
        }

        private static void requireNonnegative(double value) {
            if (!Double.isFinite(value) || value < 0)
                throw new IllegalArgumentException(
                        "Action loss limits must be finite and nonnegative");
        }
    }

    public record Prompt(
            Seat seat,
            String heroCombo,
            List<SixMaxPreflopResearchTrainer.PublicAction> priorActions,
            List<String> legalActions) {
        public Prompt {
            priorActions = List.copyOf(priorActions);
            legalActions = List.copyOf(legalActions);
        }

        private static Prompt of(SixMaxPreflopResearchTrainer.Question q) {
            return new Prompt(q.actingSeat(), q.heroCombo(), q.priorActions(), q.legalActions());
        }
    }

    public record Action(String action, double sampledLossBb, double exactLossBb) {}

    public record Row(
            Prompt prompt,
            String firstSeed,
            int draws,
            List<Action> actions,
            List<String> sampledAcceptableActions,
            List<String> exactAcceptableActions,
            double maximumLossDriftBb,
            boolean stable) {
        public Row {
            actions = List.copyOf(actions);
            sampledAcceptableActions = List.copyOf(sampledAcceptableActions);
            exactAcceptableActions = List.copyOf(exactAcceptableActions);
        }
    }

    public record SeatSummary(Seat seat, int draws, int distinctPrompts, int stablePrompts) {}

    public record Report(
            String sourcePackHash,
            String sampledPayoffPlanHash,
            String exactPayoffPlanHash,
            int draws,
            String firstSeed,
            double acceptableLossBb,
            double maximumLossDriftBb,
            int distinctPrompts,
            int stablePrompts,
            List<SeatSummary> seats,
            List<Row> prompts) {
        public Report {
            seats = List.copyOf(seats);
            prompts = List.copyOf(prompts);
        }
    }

    private static final class Sample {
        private final SixMaxPreflopResearchTrainer.Question sampled;
        private final SixMaxPreflopResearchTrainer.Question exact;
        private int count;

        private Sample(
                SixMaxPreflopResearchTrainer.Question sampled,
                SixMaxPreflopResearchTrainer.Question exact) {
            this.sampled = sampled;
            this.exact = exact;
        }
    }

    private SixSeatTrainerActionAudit() {}

    public static Report assess(
            SixSeatRangeSource.Result sampledSource,
            SixSeatPayoffWork.Result exactPayoffs,
            Options options) {
        Objects.requireNonNull(options, "options");
        var games = SixSeatPayoffComparison.matchedGames(sampledSource, exactPayoffs);
        var solution = sampledSource.pack().solution();
        var sampledTrainer = new SixMaxPreflopResearchTrainer(games.sampled(), solution);
        var exactTrainer = new SixMaxPreflopResearchTrainer(games.exact(), solution);

        var samples = new LinkedHashMap<Prompt, Sample>();
        for (int i = 0; i < options.draws(); i++) {
            long seed = options.firstSeed() + i;
            var sampled = sampledTrainer.question(seed);
            var exact = exactTrainer.question(seed);
            var prompt = Prompt.of(sampled);
            if (!prompt.equals(Prompt.of(exact)))
                throw new IllegalStateException(
                        "Matched payoff games produced different questions");
            samples.computeIfAbsent(prompt, ignored -> new Sample(sampled, exact)).count++;
        }
        var rows =
                samples.entrySet().stream()
                        .map(
                                entry -> {
                                    var prompt = entry.getKey();
                                    var pair = entry.getValue();
                                    var action = prompt.legalActions().getFirst();
                                    // grade() computes every legal action EV irrespective of the
                                    // selected action.
                                    var sampled = sampledTrainer.grade(pair.sampled, action);
                                    var exact = exactTrainer.grade(pair.exact, action);
                                    return compare(
                                            prompt,
                                            pair.sampled.seed(),
                                            pair.count,
                                            sampled,
                                            exact,
                                            options);
                                })
                        .toList();
        var seats =
                java.util.Arrays.stream(Seat.values())
                        .map(
                                seat -> {
                                    var matching =
                                            rows.stream()
                                                    .filter(row -> row.prompt().seat() == seat)
                                                    .toList();
                                    return new SeatSummary(
                                            seat,
                                            matching.stream().mapToInt(Row::draws).sum(),
                                            matching.size(),
                                            (int) matching.stream().filter(Row::stable).count());
                                })
                        .toList();
        return new Report(
                sampledSource.packHash(),
                sampledSource.payoffs().plan().contentHash(),
                exactPayoffs.plan().contentHash(),
                options.draws(),
                Long.toString(options.firstSeed()),
                options.acceptableLossBb(),
                options.maximumLossDriftBb(),
                rows.size(),
                (int) rows.stream().filter(Row::stable).count(),
                seats,
                rows);
    }

    private static Row compare(
            Prompt prompt,
            long seed,
            int count,
            SixMaxPreflopResearchTrainer.Feedback sampled,
            SixMaxPreflopResearchTrainer.Feedback exact,
            Options options) {
        var actions =
                prompt.legalActions().stream()
                        .map(
                                action ->
                                        new Action(
                                                action,
                                                sampled.bestEvBb()
                                                        - sampled.actionEvBb().get(action),
                                                exact.bestEvBb() - exact.actionEvBb().get(action)))
                        .toList();
        var verdict = verdict(actions, options.acceptableLossBb(), options.maximumLossDriftBb());
        return new Row(
                prompt,
                Long.toString(seed),
                count,
                actions,
                verdict.sampledAcceptableActions(),
                verdict.exactAcceptableActions(),
                verdict.maximumLossDriftBb(),
                verdict.stable());
    }

    record Verdict(
            List<String> sampledAcceptableActions,
            List<String> exactAcceptableActions,
            double maximumLossDriftBb,
            boolean stable) {}

    static Verdict verdict(
            List<Action> actions, double acceptableLossBb, double maximumLossDriftBb) {
        var sampledAcceptable =
                actions.stream()
                        .filter(a -> a.sampledLossBb() <= acceptableLossBb)
                        .map(Action::action)
                        .toList();
        var exactAcceptable =
                actions.stream()
                        .filter(a -> a.exactLossBb() <= acceptableLossBb)
                        .map(Action::action)
                        .toList();
        double drift =
                actions.stream()
                        .mapToDouble(a -> Math.abs(a.sampledLossBb() - a.exactLossBb()))
                        .max()
                        .orElseThrow();
        return new Verdict(
                sampledAcceptable,
                exactAcceptable,
                drift,
                sampledAcceptable.equals(exactAcceptable) && drift <= maximumLossDriftBb);
    }
}
