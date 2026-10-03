package com.pokerlab.api;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trainer/research/sixmax-preflop")
@ConditionalOnProperty(
        name = "pokerlab.trainer.sixmax-preflop-research-enabled",
        havingValue = "true")
public class SixMaxPreflopResearchController {
    public record GradeRequest(
            @NotBlank String sessionSeed,
            @NotNull @Min(0) @Max(9) Integer index,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotBlank @Size(max = 32) String action) {}

    public record ReviewRequest(
            @NotBlank String sessionSeed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String packHash,
            @NotNull @Size(min = 10, max = 10) List<@NotBlank @Size(max = 32) String> actions) {}

    private final SixMaxPreflopResearchService service;

    public SixMaxPreflopResearchController(SixMaxPreflopResearchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SixMaxPreflopResearchService.Metadata> metadata() {
        return response(service.metadata());
    }

    @GetMapping("/sessions/{seed}/questions/{index}")
    public ResponseEntity<SixMaxPreflopResearchService.QuestionView> question(
            @PathVariable("seed") String seed, @PathVariable("index") int index) {
        return response(service.question(parseSeed(seed), index));
    }

    @PostMapping("/grade")
    public ResponseEntity<SixMaxPreflopResearchService.GradedQuestion> grade(
            @Valid @RequestBody GradeRequest request) {
        return response(
                service.grade(
                        parseSeed(request.sessionSeed()),
                        request.index(),
                        request.packHash(),
                        request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<SixMaxPreflopResearchService.SessionReview> review(
            @Valid @RequestBody ReviewRequest request) {
        return response(
                service.review(
                        parseSeed(request.sessionSeed()), request.packHash(), request.actions()));
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
