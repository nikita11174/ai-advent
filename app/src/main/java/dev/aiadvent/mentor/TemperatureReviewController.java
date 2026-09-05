package dev.aiadvent.mentor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/temperature-review")
class TemperatureReviewController {
    private static final Set<Double> ALLOWED_TEMPERATURES = Set.of(0.0, 0.7, 1.2);
    private static final String SYSTEM_PROMPT = """
            You are an engineering review mentor.
            Analyze the provided code and identify real engineering risks and practical reliability improvements.
            Clearly separate confirmed issues from assumptions not proven by the snippet.
            Respond in Russian using Markdown when it improves readability.""";

    private final DeepSeekClient client;

    TemperatureReviewController(DeepSeekClient client) {
        this.client = client;
    }

    @PostMapping
    TemperatureResponse review(@RequestBody TemperatureRequest request) throws DeepSeekException {
        if (request.input() == null || request.input().isBlank()) {
            throw new IllegalArgumentException("Input must not be empty.");
        }
        if (request.temperature() == null || !ALLOWED_TEMPERATURES.contains(request.temperature())) {
            throw new IllegalArgumentException("Temperature must be 0, 0.7 or 1.2.");
        }
        return new TemperatureResponse(request.temperature(),
                client.analyzeAtTemperature(SYSTEM_PROMPT, request.input(), request.temperature()));
    }

    record TemperatureRequest(String input, Double temperature) {
    }

    record TemperatureResponse(double temperature, String analysis) {
    }
}
