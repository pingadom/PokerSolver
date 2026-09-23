package com.pokerlab.core.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MonteCarloSimulationTest {

    @Test
    void equitiesSumToOneInHeadsUpSimulation() {
        PlayerHand hero = new PlayerHand("Hero", Card.parse("As"), Card.parse("Ks"));

        PlayerHand villain = new PlayerHand("Villain", Card.parse("Qd"), Card.parse("Qc"));

        SimulationRequest request =
                SimulationRequest.quickWithSeed(
                        List.of(hero, villain), List.of(), 10_000, OptionalLong.of(42));

        SimulationResult result = MonteCarloSimulation.run(request);

        double totalEquity = result.equity("Hero") + result.equity("Villain");

        assertEquals(1.0, totalEquity, 0.000001);
    }

    @Test
    void completeBoardGivesDeterministicWinner() {
        PlayerHand hero = new PlayerHand("Hero", Card.parse("As"), Card.parse("Ks"));

        PlayerHand villain = new PlayerHand("Villain", Card.parse("Qd"), Card.parse("Qc"));

        List<Card> board =
                List.of(
                        Card.parse("Ah"),
                        Card.parse("7c"),
                        Card.parse("2s"),
                        Card.parse("9d"),
                        Card.parse("Jc"));

        SimulationRequest request =
                SimulationRequest.quickWithSeed(
                        List.of(hero, villain), board, 100, OptionalLong.of(42));

        SimulationResult result = MonteCarloSimulation.run(request);

        assertEquals(1.0, result.equity("Hero"), 0.000001);
        assertEquals(0.0, result.equity("Villain"), 0.000001);
        assertEquals(100, result.wins("Hero"));
        assertEquals(0, result.wins("Villain"));
    }

    @Test
    void rejectsDuplicateCardsAcrossPlayers() {
        PlayerHand hero = new PlayerHand("Hero", Card.parse("As"), Card.parse("Ks"));

        PlayerHand villain = new PlayerHand("Villain", Card.parse("As"), Card.parse("Qc"));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SimulationRequest.quickWithSeed(
                                List.of(hero, villain), List.of(), 100, OptionalLong.of(42)));
    }

    @Test
    void supportsThreePlayers() {
        PlayerHand hero = new PlayerHand("Hero", Card.parse("As"), Card.parse("Ks"));

        PlayerHand villain1 = new PlayerHand("Villain 1", Card.parse("Qd"), Card.parse("Qc"));

        PlayerHand villain2 = new PlayerHand("Villain 2", Card.parse("Jh"), Card.parse("Th"));

        SimulationRequest request =
                SimulationRequest.quickWithSeed(
                        List.of(hero, villain1, villain2), List.of(), 10_000, OptionalLong.of(42));

        SimulationResult result = MonteCarloSimulation.run(request);

        double totalEquity =
                result.equity("Hero") + result.equity("Villain 1") + result.equity("Villain 2");

        assertEquals(1.0, totalEquity, 0.000001);
    }
}
