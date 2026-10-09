package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.*;

/** Deterministic offline practice with explicit supplied-range provenance on every question. */
public final class SixMaxSuppliedRangePreflopTrainer {
    public static final int SESSION_LENGTH = 10;
    public static final String SAMPLING = "UNIFORM_STABLE_MATERIAL_INFORMATION_SETS/v1";

    public record Question(
            long seed,
            String studyHash,
            String publicationStatus,
            boolean trainerAdmission,
            String model,
            String beliefs,
            String sourceHistoryReachStatus,
            String sampling,
            Seat hero,
            String heroCards,
            List<PublicAction> history,
            SixMaxPreflopPublicTable.Snapshot table,
            List<String> legalActions) {
        public Question {
            history = List.copyOf(history);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Feedback(
            String studyHash,
            String publicationStatus,
            boolean trainerAdmission,
            String evScope,
            String selectedAction,
            double selectedActionEvBb,
            double evLossBb,
            SixMaxHeadsUpPreflopDecisionValues.Values values) {}

    public record Attempt(int index, Question question, Feedback feedback) {}

    public record Review(
            long sessionSeed,
            String studyHash,
            List<Attempt> attempts,
            double totalEvLossBb,
            double averageEvLossBb) {
        public Review {
            attempts = List.copyOf(attempts);
        }
    }

    private final SixMaxSuppliedRangePreflopStudy.Result study;
    private final String hash;
    private final List<SixMaxHeadsUpPreflopStudy.Decision> decisions;

    public SixMaxSuppliedRangePreflopTrainer(SixMaxSuppliedRangePreflopStudy.Result study)
            throws Exception {
        this.study = Objects.requireNonNull(study);
        if (!study.report().qualifiedForOfflinePractice() || study.artifact().isEmpty())
            throw new IllegalArgumentException("Complete decision screening required");
        hash = SixMaxHeadsUpPreflopGame.hash(study.report());
        decisions =
                study.report().decisions().stream()
                        .filter(SixMaxHeadsUpPreflopStudy.Decision::stable)
                        .sorted(Comparator.comparing(d -> d.primary().informationSet()))
                        .toList();
        if (decisions.isEmpty()) throw new IllegalArgumentException("No stable material decisions");
    }

    public String studyHash() {
        return hash;
    }

    private SixMaxHeadsUpPreflopStudy.Decision decision(long seed) {
        return decisions.get(new SplittableRandom(seed).nextInt(decisions.size()));
    }

    public Question question(long seed) {
        var row = decision(seed).primary();
        var game = study.core();
        var state =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(
                                r ->
                                        new SixMaxHeadsUpPreflopGame.State(
                                                r.state().dealIndex(), row.publicHistory()))
                        .filter(
                                s ->
                                        (game.currentPlayer(s) + ":" + game.informationSet(s))
                                                .equals(row.informationSet()))
                        .findFirst()
                        .orElseThrow();
        var history =
                game.publicBettingState(state).history().stream()
                        .filter(
                                m ->
                                        m.kind() != SixMaxPreflopBetting.Kind.POST_SMALL_BLIND
                                                && m.kind()
                                                        != SixMaxPreflopBetting.Kind.POST_BIG_BLIND)
                        .map(m -> new PublicAction(m.seat(), SixMaxHeadsUpPreflopGame.encode(m)))
                        .toList();
        return new Question(
                seed,
                hash,
                SixMaxSuppliedRangePreflopStudy.STATUS,
                false,
                SixMaxSuppliedRangePreflopGame.MODEL,
                SixMaxSuppliedRangePreflopGame.BELIEFS,
                SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                SAMPLING,
                row.actor(),
                row.ownHand(),
                history,
                SixMaxPreflopPublicTable.replay(game.input().specification().rules(), history),
                game.legalActions(state));
    }

    public Feedback grade(Question question, String action) {
        Objects.requireNonNull(question);
        var expected = question(question.seed());
        if (!expected.equals(question))
            throw new IllegalArgumentException("Question does not match this study and seed");
        if (!expected.legalActions().contains(action))
            throw new IllegalArgumentException("Illegal question action");
        var values = decision(question.seed()).primary().values();
        double selected = values.actionEvBb().get(action);
        double loss = Math.max(0, Collections.max(values.actionEvBb().values()) - selected);
        return new Feedback(
                hash,
                SixMaxSuppliedRangePreflopStudy.STATUS,
                false,
                SixMaxHeadsUpPreflopDecisionValues.EV_SCOPE,
                action,
                selected,
                loss,
                values);
    }

    public Question sessionQuestion(long sessionSeed, int index) {
        if (index < 0 || index >= SESSION_LENGTH)
            throw new IllegalArgumentException("Session index must be between 0 and 9");
        var random = new SplittableRandom(sessionSeed);
        long seed = 0;
        for (int i = 0; i <= index; i++) seed = random.nextLong();
        return question(seed);
    }

    public Review review(long sessionSeed, String studyHash, List<String> actions) {
        if (!hash.equals(studyHash)) throw new IllegalArgumentException("Wrong conditional study");
        if (actions == null
                || actions.size() != SESSION_LENGTH
                || actions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Exactly ten actions required");
        var attempts = new ArrayList<Attempt>();
        double total = 0;
        for (int i = 0; i < SESSION_LENGTH; i++) {
            var question = sessionQuestion(sessionSeed, i);
            var feedback = grade(question, actions.get(i));
            total += feedback.evLossBb();
            attempts.add(new Attempt(i, question, feedback));
        }
        return new Review(sessionSeed, hash, attempts, total, total / SESSION_LENGTH);
    }
}
