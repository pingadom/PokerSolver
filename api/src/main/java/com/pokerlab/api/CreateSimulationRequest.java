package com.pokerlab.api;

import com.pokerlab.core.batch.SimulationConfiguration;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.simulation.PlayerHand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record CreateSimulationRequest(
        @NotNull @Size(min = 2, max = 9) List<@NotNull @Valid Player> players,
        @NotNull @Size(max = 5) List<@NotBlank String> board,
        @Min(1) @Max(100_000_000) long iterations,
        @Min(1) @Max(1_000_000) Integer batchSize,
        Long seed) {
    public record Player(
            @NotBlank @Size(max = 80) String name,
            @NotNull @Size(min = 2, max = 2) List<@NotBlank String> cards) {}

    public SimulationConfiguration configuration() {
        return new SimulationConfiguration(
                players.stream()
                        .map(
                                p ->
                                        new PlayerHand(
                                                p.name(),
                                                Card.parse(p.cards().get(0)),
                                                Card.parse(p.cards().get(1))))
                        .toList(),
                board.stream().map(Card::parse).toList(),
                iterations,
                batchSize == null ? 100_000 : batchSize,
                seed == null ? ThreadLocalRandom.current().nextLong() : seed);
    }
}
