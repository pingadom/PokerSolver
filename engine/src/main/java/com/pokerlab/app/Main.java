package com.pokerlab.app;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.MonteCarloSimulation;
import com.pokerlab.core.simulation.PlayerHand;
import com.pokerlab.core.simulation.SimulationFormatter;
import com.pokerlab.core.simulation.SimulationRequest;
import com.pokerlab.core.simulation.SimulationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public class Main {

    public static void main(String[] args) {
        try {
            CliOptions options = parseArgs(args);

            PlayerHand hero =
                    new PlayerHand(
                            "Hero",
                            Card.parse(options.heroHand.substring(0, 2)),
                            Card.parse(options.heroHand.substring(2, 4)));

            List<PlayerHand> players = new ArrayList<>();
            players.add(hero);

            for (int i = 0; i < options.villainHands.size(); i++) {
                String villainHand = options.villainHands.get(i);

                players.add(
                        new PlayerHand(
                                "Villain " + (i + 1),
                                Card.parse(villainHand.substring(0, 2)),
                                Card.parse(villainHand.substring(2, 4))));
            }

            List<Card> board = parseBoard(options.boardCards);

            SimulationRequest request;

            if (options.verbose) {
                request =
                        SimulationRequest.verboseWithSeed(
                                players,
                                board,
                                options.simulations,
                                OptionalLong.of(options.seed),
                                options.verboseLimit);
            } else {
                request =
                        SimulationRequest.quickWithSeed(
                                players, board, options.simulations, OptionalLong.of(options.seed));
            }

            SimulationResult result = MonteCarloSimulation.run(request);

            System.out.println(SimulationFormatter.formatSummary(result));

        } catch (IllegalArgumentException exception) {
            System.err.println("Error: " + exception.getMessage());
            printUsage();
        }
    }

    private static CliOptions parseArgs(String[] args) {
        if (args.length == 0) {
            printUsage();
            throw new IllegalArgumentException("No arguments provided");
        }

        CliOptions options = new CliOptions();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--hero" -> {
                    options.heroHand = requireNext(args, ++i, "--hero");
                    validateHandString(options.heroHand, "--hero");
                }

                case "--villain" -> {
                    String villainHand = requireNext(args, ++i, "--villain");
                    validateHandString(villainHand, "--villain");
                    options.villainHands.add(villainHand);
                }

                case "--villains" -> {
                    String villains = requireNext(args, ++i, "--villains");
                    String[] splitHands = villains.split(",");

                    for (String hand : splitHands) {
                        String trimmed = hand.trim();
                        validateHandString(trimmed, "--villains");
                        options.villainHands.add(trimmed);
                    }
                }

                case "--board" -> {
                    options.boardCards = requireNext(args, ++i, "--board");
                    validateBoardString(options.boardCards);
                }

                case "--sims" -> {
                    String sims = requireNext(args, ++i, "--sims");
                    options.simulations = Integer.parseInt(sims);

                    if (options.simulations <= 0) {
                        throw new IllegalArgumentException("--sims must be positive");
                    }
                }

                case "--seed" -> {
                    String seed = requireNext(args, ++i, "--seed");
                    options.seed = Long.parseLong(seed);
                }

                case "--verbose" -> options.verbose = true;

                case "--verbose-limit" -> {
                    String limit = requireNext(args, ++i, "--verbose-limit");
                    options.verboseLimit = Integer.parseInt(limit);

                    if (options.verboseLimit < 0) {
                        throw new IllegalArgumentException("--verbose-limit cannot be negative");
                    }
                }

                case "--help" -> {
                    printUsage();
                    System.exit(0);
                }

                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (options.heroHand == null) {
            throw new IllegalArgumentException("Missing required argument: --hero");
        }

        if (options.villainHands.isEmpty()) {
            throw new IllegalArgumentException("At least one villain is required");
        }

        return options;
    }

    private static String requireNext(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value after " + optionName);
        }

        return args[index];
    }

    private static void validateHandString(String hand, String optionName) {
        if (hand.length() != 4) {
            throw new IllegalArgumentException(
                    optionName + " must contain exactly 4 characters, e.g. AsKs or QdQc");
        }
    }

    private static void validateBoardString(String board) {
        if (board.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "--board must contain pairs of cards, e.g. Ah7c2s or Ah7c2s9dJc");
        }

        int cards = board.length() / 2;

        if (!(cards == 0 || cards == 3 || cards == 4 || cards == 5)) {
            throw new IllegalArgumentException("--board must contain 0, 3, 4, or 5 cards");
        }
    }

    private static List<Card> parseBoard(String boardString) {
        List<Card> board = new ArrayList<>();

        if (boardString == null || boardString.isBlank()) {
            return board;
        }

        for (int i = 0; i < boardString.length(); i += 2) {
            String cardString = boardString.substring(i, i + 2);
            board.add(Card.parse(cardString));
        }

        return board;
    }

    private static void printUsage() {
        System.out.println(
                """
                PokerLab CLI

                Usage:
                  mvn exec:java -Dexec.args="--hero AsKs --villain QdQc --sims 100000"

                Required:
                  --hero <cards>           Hero hand, e.g. AsKs
                  --villain <cards>        One villain hand, e.g. QdQc

                Optional:
                  --villains <hands>       Multiple villains, comma-separated, e.g. QdQc,JhTh,9s9c
                  --board <cards>          Board cards, e.g. Ah7c2s or Ah7c2s9dJc
                  --sims <number>          Number of simulations. Default: 100000
                  --seed <number>          Random seed. Default: 42
                  --verbose                Print card-by-card trial output
                  --verbose-limit <number> Number of verbose trials to print. Default: 10
                  --help                   Show this help message

                Card format:
                  Rank: 2 3 4 5 6 7 8 9 T J Q K A
                  Suit: s h d c

                Examples:
                  AsKs = Ace of spades, King of spades
                  QdQc = Queen of diamonds, Queen of clubs
                  Ah7c2s = Ace hearts, Seven clubs, Two spades
                """);
    }

    private static class CliOptions {
        private String heroHand;
        private final List<String> villainHands = new ArrayList<>();
        private String boardCards = "";
        private int simulations = 100_000;
        private long seed = 42;
        private boolean verbose = false;
        private int verboseLimit = 10;
    }
}
