package dev.aiadvent.mentor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reasoning-review")
class ReasoningReviewController {
    private final ReasoningReviewService service;

    ReasoningReviewController(ReasoningReviewService service) {
        this.service = service;
    }

    @PostMapping
    ReasoningReviewService.ReasoningResult review(@RequestBody ReasoningRequest request) throws DeepSeekException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (request.strategy() == null) {
            throw new IllegalArgumentException("Reasoning strategy is required.");
        }
        return service.analyze(request.input(), request.strategy());
    }

    record ReasoningRequest(String input, ReasoningStrategy strategy) {
    }
}
