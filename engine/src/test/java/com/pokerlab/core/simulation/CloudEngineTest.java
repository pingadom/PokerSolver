package com.pokerlab.core.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.*;
import org.junit.jupiter.api.Test;

class CloudEngineTest {
    private final List<PlayerHand> players =
            List.of(
                    new PlayerHand("AA", Card.parse("AS"), Card.parse("AH")),
                    new PlayerHand("KK", Card.parse("KS"), Card.parse("KH")),
                    new PlayerHand("QQ", Card.parse("QS"), Card.parse("QH")));

    @Test
    void fixedSeedReproducesEveryCountAndAcesLead() {
        var request =
                SimulationRequest.quickWithSeed(players, List.of(), 20_000, OptionalLong.of(123));
        var first = MonteCarloSimulation.run(request);
        var second = MonteCarloSimulation.run(request);
        for (var player : players) {
            var name = player.playerName();
            assertEquals(first.wins(name), second.wins(name));
            assertEquals(first.ties(name), second.ties(name));
            assertEquals(first.equityShare(name), second.equityShare(name));
            assertEquals(20_000, first.wins(name) + first.ties(name) + first.losses(name));
        }
        assertTrue(first.equity("AA") > first.equity("KK"));
        assertTrue(first.equity("KK") > first.equity("QQ"));
    }

    @Test
    void royalFlushBoardSplitsThreeWays() {
        var board = List.of("TC", "JC", "QC", "KC", "AC").stream().map(Card::parse).toList();
        var result = MonteCarloSimulation.run(SimulationRequest.quick(players, board, 100));
        for (var player : players) {
            assertEquals(0, result.wins(player.playerName()));
            assertEquals(100, result.ties(player.playerName()));
            assertEquals(0, result.losses(player.playerName()));
            assertEquals(1.0 / 3, result.equity(player.playerName()), 1e-12);
        }
    }

    @Test
    void everyBoardLengthIsSupportedWithoutSamplingKnownCards() {
        var board = List.of("2C", "3C", "4C", "5C", "6C").stream().map(Card::parse).toList();
        for (int size = 0; size <= 5; size++) {
            var result =
                    MonteCarloSimulation.run(
                            SimulationRequest.verboseWithSeed(
                                    players, board.subList(0, size), 20, OptionalLong.of(7), 20));
            assertEquals(20, result.verboseTrials().size());
            assertEquals(
                    1,
                    players.stream().mapToDouble(p -> result.equity(p.playerName())).sum(),
                    1e-12);
        }
    }

    @Test
    void rejectsInvalidScenariosAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationRequest.quick(players, List.of(Card.parse("AS")), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationRequest.quick(players.subList(0, 1), List.of(), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationRequest.quick(players, List.of(), 0));
        var tenPlayers = new ArrayList<PlayerHand>();
        var deck = new com.pokerlab.core.card.Deck().cards();
        for (int i = 0; i < 10; i++)
            tenPlayers.add(new PlayerHand("P" + i, deck.get(i * 2), deck.get(i * 2 + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulationRequest.quick(tenPlayers, List.of(), 1));
        assertDoesNotThrow(() -> SimulationRequest.quick(tenPlayers.subList(0, 9), List.of(), 1));
    }
}
