package dev.aiadvent.mentor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class ModelReviewController {
    private final OpenAiResponsesClient client;

    ModelReviewController(OpenAiResponsesClient client) { this.client = client; }

    @GetMapping("/api/model-options")
    List<ModelProfile> options() { return ModelProfile.MODELS; }

    @PostMapping("/api/model-review")
    ResponseEntity<OpenAiResponsesClient.Result> review(@RequestBody Request request) {
        ModelProfile model = ModelProfile.resolve(request.modelKey());
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Введите код или инженерный вопрос.");
        }
        var result = client.analyze(model, request.input());
        return ResponseEntity.status(result.error() == null ? 200 : 502).body(result);
    }

    record Request(String input, String modelKey) { }
}
