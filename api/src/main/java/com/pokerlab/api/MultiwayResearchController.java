package com.pokerlab.api;

import com.pokerlab.solver.MultiwayCallTrainer;
import com.pokerlab.solver.MultiwayCallTrainer.Action;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trainer/research/multiway")
@ConditionalOnProperty(name = "pokerlab.trainer.multiway-research-enabled", havingValue = "true")
public class MultiwayResearchController {
    public record GradeRequest(
            @NotBlank String sessionSeed,
            @NotNull @Min(0) @Max(9) Integer index,
            @NotNull @Min(0) @Max(5) Integer player,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull MultiwayCallTrainer.Action action) {}

    public record ReviewRequest(
            @NotBlank String sessionSeed,
            @NotNull @Min(0) @Max(5) Integer player,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull @Size(min = 10, max = 10) List<@NotNull Action> actions) {}

    private final MultiwayResearchService service;

    public MultiwayResearchController(MultiwayResearchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MultiwayResearchService.Metadata> metadata() {
        return response(service.metadata());
    }

    @GetMapping("/sessions/{seed}/questions/{index}")
    public ResponseEntity<MultiwayResearchService.QuestionView> question(
            @PathVariable("seed") String seed,
            @PathVariable("index") int index,
            @RequestParam(name = "player", defaultValue = "0") int player) {
        return response(service.question(parseSeed(seed), index, player));
    }

    @PostMapping("/grade")
    public ResponseEntity<MultiwayResearchService.GradedQuestion> grade(
            @Valid @RequestBody GradeRequest request) {
        return response(
                service.grade(
                        parseSeed(request.sessionSeed()),
                        request.index(),
                        request.player(),
                        request.packHash(),
                        request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<MultiwayResearchService.SessionReview> review(
            @Valid @RequestBody ReviewRequest request) {
        return response(
                service.review(
                        parseSeed(request.sessionSeed()),
                        request.player(),
                        request.packHash(),
                        request.actions()));
    }

    private static long parseSeed(String seed) {
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit integer", exception);
        }
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
