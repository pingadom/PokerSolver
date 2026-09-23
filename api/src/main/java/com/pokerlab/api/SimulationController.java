package com.pokerlab.api;

import com.pokerlab.core.batch.PlayerResult;
import com.pokerlab.shared.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationStore store;
    private final SimulationCache cache;

    public SimulationController(SimulationStore store, SimulationCache cache) {
        this.store = store;
        this.cache = cache;
    }

    public record Created(UUID simulationId, SimulationStatus status, String statusUrl) {}

    public record ResultPlayer(String name, long wins, long ties, long losses, double equity) {
        static ResultPlayer from(PlayerResult player) {
            return new ResultPlayer(
                    player.name(), player.wins(), player.ties(), player.losses(), player.equity());
        }
    }

    public record ResultResponse(
            UUID simulationId,
            SimulationStatus status,
            long totalTrials,
            List<ResultPlayer> players,
            long elapsedMs) {}

    @PostMapping
    public ResponseEntity<Created> create(@Valid @RequestBody CreateSimulationRequest request) {
        UUID id = store.create(request.configuration());
        String url = "/api/v1/simulations/" + id;
        return ResponseEntity.accepted()
                .location(URI.create(url))
                .body(new Created(id, SimulationStatus.QUEUED, url));
    }

    @GetMapping("/{id}")
    public SimulationStatusResponse get(@PathVariable("id") UUID id) {
        return SimulationStatusResponse.from(cache.status(id));
    }

    @GetMapping
    public List<SimulationStatusResponse> recent(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return store.recent(limit, offset).stream().map(SimulationStatusResponse::from).toList();
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<?> results(@PathVariable("id") UUID id) {
        var view = store.get(id);
        if (view.status() == SimulationStatus.COMPLETED) {
            var result = cache.result(id);
            return ResponseEntity.ok(
                    new ResultResponse(
                            id,
                            result.status(),
                            result.totalTrials(),
                            result.players().stream().map(ResultPlayer::from).toList(),
                            result.elapsedMs()));
        }
        if (view.status().terminal())
            return ResponseEntity.status(409)
                    .body(
                            new ApiErrors.ErrorBody(
                                    "SIMULATION_TERMINAL",
                                    "Simulation ended with status " + view.status()));
        return ResponseEntity.accepted()
                .header("Retry-After", "2")
                .body(Map.of("simulationId", id, "status", view.status()));
    }
}
