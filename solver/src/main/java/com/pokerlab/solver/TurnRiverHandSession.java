package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Stateless replay of one connected turn-to-river hand against the saved policy. The opponent's
 * private cards are retained across both streets and disclosed only after a showdown.
 */
public final class TurnRiverHandSession {
    public record PublicAction(String street, int player, String action) {}

    public record DecisionFeedback(
            String street,
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
            List<Card> turnBoard,
            Card river,
            String street,
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
            turnBoard = List.copyOf(turnBoard);
            publicActions = List.copyOf(publicActions);
            legalActions = List.copyOf(legalActions);
            feedback = List.copyOf(feedback);
        }
    }

    private final TurnRiverSolutionPack pack;
    private final TurnRiverGame game;
    private final TurnRiverDecisionEvaluator evaluator;
    private final String packHash;

    public TurnRiverHandSession(TurnRiverSolutionPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        pack.validate();
        game = pack.spot().game();
        evaluator = new TurnRiverDecisionEvaluator(pack);
        packHash = TurnRiverPackJson.contentHash(pack);
    }

    public String packHash() {
        return packHash;
    }

    /** Replays only hero actions; the joint deal, opponent policy draws and river are seeded. */
    public Snapshot replay(
            long seed, String submittedPackHash, int heroPlayer, List<String> heroActions) {
        if (!packHash.equals(submittedPackHash))
            throw new IllegalArgumentException("Solution pack has changed; start a new hand");
        if (heroPlayer != 0 && heroPlayer != 1)
            throw new IllegalArgumentException("Hero player must be 0 or 1");
        if (heroActions == null
                || heroActions.size() > 4
                || heroActions.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("A hand needs at most four hero actions");
        TurnRiverGame.State state =
                draw(game.chanceOutcomes(game.initialState()), random(seed, "deal"));
        List<PublicAction> publicActions = new ArrayList<>();
        List<DecisionFeedback> feedback = new ArrayList<>();
        int used = 0;
        while (!game.isTerminal(state)) {
            int player = game.currentPlayer(state);
            if (player == -1) {
                state = draw(game.chanceOutcomes(state), random(seed, "river"));
                continue;
            }
            String street = state.river() == null ? "TURN" : "RIVER";
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

    private DecisionFeedback grade(TurnRiverGame.State state, int player, String action) {
        TurnRiverDecisionEvaluator.Decision decision =
                evaluator.evaluate(
                        player,
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
                state.river() == null ? "TURN" : "RIVER",
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

    private String samplePolicy(TurnRiverGame.State state, long seed) {
        int player = game.currentPlayer(state);
        Map<String, Double> policy = pack.solution().at(player, game.informationSet(state));
        String event =
                (state.river() == null ? "T" : "R" + state.river().compact())
                        + ":"
                        + state.turnHistory()
                        + ":"
                        + state.riverHistory();
        double draw = random(seed, event).nextDouble();
        List<String> actions = game.legalActions(state);
        double cumulative = 0;
        for (String action : actions) {
            cumulative += policy.get(action);
            if (draw < cumulative) return action;
        }
        return actions.getLast(); // Covers floating-point rounding at one.
    }

    private Snapshot snapshot(
            long seed,
            int heroPlayer,
            TurnRiverGame.State state,
            List<PublicAction> publicActions,
            List<DecisionFeedback> feedback,
            boolean complete) {
        double[] commitments = commitments(state.turnHistory(), state.riverHistory());
        double currentPot = pack.spot().potBb() + commitments[0] + commitments[1];
        WeightedCombo hero = heroPlayer == 0 ? state.first() : state.second();
        WeightedCombo opponent = heroPlayer == 0 ? state.second() : state.first();
        boolean showdown =
                complete
                        && state.river() != null
                        && (state.riverHistory().equals("kk")
                                || state.riverHistory().equals("bc")
                                || state.riverHistory().equals("kbc"));
        return new Snapshot(
                packHash,
                pack.publicationStatus(),
                Long.toString(seed),
                heroPlayer,
                hero.key(),
                showdown ? opponent.key() : null,
                pack.spot().turnBoard(),
                state.river(),
                state.river() == null ? "TURN" : "RIVER",
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

    private double[] commitments(String turnHistory, String riverHistory) {
        double[] invested = new double[2];
        addCommitments(invested, turnHistory, pack.spot().turnBetBb());
        addCommitments(invested, riverHistory, pack.spot().riverBetBb());
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

    private static TurnRiverGame.State draw(
            List<ChanceOutcome<TurnRiverGame.State>> outcomes, SplittableRandom random) {
        double target = random.nextDouble();
        double cumulative = 0;
        for (ChanceOutcome<TurnRiverGame.State> outcome : outcomes) {
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
