package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Stateless replay of one connected flop-to-river hand against a saved exact-deck policy. */
public final class FlopTurnRiverHandSession {
    public record PublicAction(String street, int player, String action) {}

    public record DecisionFeedback(
            String street,
            String flopHistory,
            Card turn,
            String turnHistory,
            Card river,
            String riverHistory,
            String selectedAction,
            double selectedEvBb,
            double bestEvBb,
            double evLossBb,
            Map<String, Double> actionFrequency,
            Map<String, Double> actionEvBb) {
        public DecisionFeedback {
            actionFrequency = Map.copyOf(actionFrequency);
            actionEvBb = Map.copyOf(actionEvBb);
        }
    }

    public record Snapshot(
            String packHash,
            String publicationStatus,
            String seed,
            int heroPlayer,
            String heroCombo,
            String opponentCombo,
            List<Card> flop,
            Card turn,
            Card river,
            String street,
            String flopHistory,
            String turnHistory,
            String riverHistory,
            double potBb,
            double heroRemainingStackBb,
            List<PublicAction> publicActions,
            List<String> legalActions,
            List<DecisionFeedback> feedback,
            boolean complete,
            boolean showdown,
            Double heroCenteredResultBb,
            double gameGapBb) {
        public Snapshot {
            flop = List.copyOf(flop);
            publicActions = List.copyOf(publicActions);
            legalActions = List.copyOf(legalActions);
            feedback = List.copyOf(feedback);
        }
    }

    private final FlopTurnRiverSolutionPack pack;
    private final FlopTurnRiverGame game;
    private final FlopTurnRiverDecisionEvaluator evaluator;
    private final String packHash;

    public FlopTurnRiverHandSession(FlopTurnRiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        game = pack.spot().game();
        evaluator = new FlopTurnRiverDecisionEvaluator(pack);
        packHash = FlopTurnRiverPackJson.contentHash(pack);
    }

    public String packHash() {
        return packHash;
    }

    public FlopTurnRiverSolutionPack pack() {
        return pack;
    }

    /** Replays only hero actions; deal, opponent draws, turn and river are seeded events. */
    public Snapshot replay(
            long seed, String submittedPackHash, int heroPlayer, List<String> heroActions) {
        if (!packHash.equals(submittedPackHash))
            throw new IllegalArgumentException("Solution pack has changed; start a new hand");
        if (heroPlayer != 0 && heroPlayer != 1)
            throw new IllegalArgumentException("Hero player must be 0 or 1");
        if (heroActions == null
                || heroActions.size() > 6
                || heroActions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A hand needs at most six hero actions");
        FlopTurnRiverGame.State state =
                draw(game.chanceOutcomes(game.initialState()), random(seed, "deal"));
        List<PublicAction> publicActions = new ArrayList<>();
        List<DecisionFeedback> feedback = new ArrayList<>();
        int used = 0;
        while (!game.isTerminal(state)) {
            int player = game.currentPlayer(state);
            if (player == -1) {
                state =
                        draw(
                                game.chanceOutcomes(state),
                                random(
                                        seed,
                                        state.turn() == null
                                                ? "turn"
                                                : "river:" + state.turn().compact()));
                continue;
            }
            String street = street(state);
            String action;
            if (player == heroPlayer) {
                if (used == heroActions.size())
                    return snapshot(seed, heroPlayer, state, publicActions, feedback, false);
                action = heroActions.get(used++);
                if (!game.legalActions(state).contains(action))
                    throw new IllegalArgumentException(
                            "Illegal hero action at this decision: " + action);
                feedback.add(grade(state, player, action));
            } else {
                action = samplePolicy(state, seed);
            }
            publicActions.add(new PublicAction(street, player, action));
            state = game.afterAction(state, action);
        }
        if (used != heroActions.size())
            throw new IllegalArgumentException("Hero actions continue after the hand ends");
        return snapshot(seed, heroPlayer, state, publicActions, feedback, true);
    }

    private DecisionFeedback grade(FlopTurnRiverGame.State state, int player, String action) {
        FlopTurnRiverDecisionEvaluator.Decision decision =
                evaluator.evaluate(
                        player,
                        state.flopHistory(),
                        state.turn(),
                        state.turnHistory(),
                        state.river(),
                        state.riverHistory(),
                        (player == 0 ? state.first() : state.second()).key());
        double selected = decision.actionEvBb().get(action);
        double best =
                decision.actionEvBb().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElseThrow();
        return new DecisionFeedback(
                street(state),
                state.flopHistory(),
                state.turn(),
                state.turnHistory(),
                state.river(),
                state.riverHistory(),
                action,
                selected,
                best,
                Math.max(0, best - selected),
                decision.actionFrequency(),
                decision.actionEvBb());
    }

    private String samplePolicy(FlopTurnRiverGame.State state, long seed) {
        int player = game.currentPlayer(state);
        Map<String, Double> policy = pack.solution().at(player, game.informationSet(state));
        String event =
                street(state)
                        + ":"
                        + state.flopHistory()
                        + ":"
                        + (state.turn() == null ? "" : state.turn().compact())
                        + ":"
                        + state.turnHistory()
                        + ":"
                        + (state.river() == null ? "" : state.river().compact())
                        + ":"
                        + state.riverHistory();
        double draw = random(seed, event).nextDouble();
        List<String> actions = game.legalActions(state);
        double cumulative = 0;
        for (String action : actions) {
            cumulative += policy.get(action);
            if (draw < cumulative) return action;
        }
        return actions.getLast();
    }

    private Snapshot snapshot(
            long seed,
            int heroPlayer,
            FlopTurnRiverGame.State state,
            List<PublicAction> publicActions,
            List<DecisionFeedback> feedback,
            boolean complete) {
        double[] commitments = commitments(state);
        double currentPot = pack.spot().potBb() + commitments[0] + commitments[1];
        WeightedCombo hero = heroPlayer == 0 ? state.first() : state.second();
        WeightedCombo opponent = heroPlayer == 0 ? state.second() : state.first();
        boolean showdown =
                complete && state.river() != null && completeHistory(state.riverHistory());
        return new Snapshot(
                packHash,
                pack.publicationStatus(),
                Long.toString(seed),
                heroPlayer,
                hero.key(),
                showdown ? opponent.key() : null,
                pack.spot().flop(),
                state.turn(),
                state.river(),
                street(state),
                state.flopHistory(),
                state.turnHistory(),
                state.riverHistory(),
                currentPot,
                pack.spot().remainingStackBb() - commitments[heroPlayer],
                publicActions,
                complete ? List.of() : game.legalActions(state),
                feedback,
                complete,
                showdown,
                complete
                        ? (heroPlayer == 0
                                ? game.terminalUtility(state)
                                : -game.terminalUtility(state))
                        : null,
                pack.gameGapBb());
    }

    private double[] commitments(FlopTurnRiverGame.State state) {
        double[] invested = new double[2];
        addCommitments(invested, state.flopHistory(), pack.spot().flopBetBb());
        addCommitments(invested, state.turnHistory(), pack.spot().turnBetBb());
        addCommitments(invested, state.riverHistory(), pack.spot().riverBetBb());
        return invested;
    }

    private static void addCommitments(double[] invested, String history, double bet) {
        switch (history) {
            case "b", "bf" -> invested[0] += bet;
            case "kb", "kbf" -> invested[1] += bet;
            case "bc", "kbc" -> {
                invested[0] += bet;
                invested[1] += bet;
            }
            default -> { // No bet yet, or both players checked.
            }
        }
    }

    private static boolean completeHistory(String history) {
        return history.equals("kk") || history.equals("bc") || history.equals("kbc");
    }

    private static String street(FlopTurnRiverGame.State state) {
        return state.turn() == null ? "FLOP" : state.river() == null ? "TURN" : "RIVER";
    }

    private static FlopTurnRiverGame.State draw(
            List<ChanceOutcome<FlopTurnRiverGame.State>> outcomes, SplittableRandom random) {
        double target = random.nextDouble();
        double cumulative = 0;
        for (ChanceOutcome<FlopTurnRiverGame.State> outcome : outcomes) {
            cumulative += outcome.probability();
            if (target < cumulative) return outcome.state();
        }
        return outcomes.getLast().state();
    }

    private static SplittableRandom random(long seed, String event) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : event.getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return new SplittableRandom(seed ^ hash);
    }
}
