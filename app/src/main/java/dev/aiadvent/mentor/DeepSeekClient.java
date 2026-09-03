package dev.aiadvent.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class DeepSeekClient {
    private static final URI API_URI = URI.create("https://api.deepseek.com/chat/completions");
    private static final String MODEL = "deepseek-v4-flash";
    private static final String SYSTEM_PROMPT = """
            You are an engineering review mentor.
            Analyze the provided code or engineering question.
            Explain potential engineering risks clearly and concisely.
            Respond in Russian.
            Format the response using Markdown when it improves readability.""";

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final String apiKey;

    DeepSeekClient(HttpClient httpClient, ObjectMapper json, String apiKey) {
        this.httpClient = httpClient;
        this.json = json;
        this.apiKey = apiKey;
    }

    public String analyze(String input) throws DeepSeekException {
        return analyzeWithSystem(SYSTEM_PROMPT, input);
    }

    String analyzeWithSystem(String systemPrompt, String input) throws DeepSeekException {
        try {
            return send(input, buildFreeRequestBody(systemPrompt, input)).content();
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("Could not create the DeepSeek request.", e);
        }
    }

    ControlledAnalysis analyzeControlled(String input, ReviewControls controls) throws DeepSeekException {
        ReviewControls validatedControls = controls.validated();
        Completion completion = send(input, buildControlledRequestBody(input, validatedControls));
        return validateControlledCompletion(completion, validatedControls);
    }

    ControlledAnalysis validateControlledCompletion(Completion completion, ReviewControls controls)
            throws DeepSeekException {
        if (!"stop".equals(completion.finishReason())) {
            throw new DeepSeekException("DeepSeek stopped the controlled response with finish_reason="
                    + completion.finishReason() + ".", completion.content());
        }
        return new ControlledAnalysis(parseControlledReview(completion.content(), controls),
                completion.content());
    }

    private Completion send(String input, String requestBody) throws DeepSeekException {
        requireInput(input);
        requireApiKey();

        HttpRequest request = HttpRequest.newBuilder(API_URI)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DeepSeekException("DeepSeek request failed due to a transport problem.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeepSeekException("DeepSeek request was interrupted.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeepSeekException("DeepSeek API returned HTTP " + response.statusCode() + ".");
        }

        try {
            return extractCompletion(response.body());
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("DeepSeek returned a malformed response.", e);
        }
    }

    String buildRequestBody(String input) throws JsonProcessingException {
        return buildFreeRequestBody(input);
    }

    String buildFreeRequestBody(String input) throws JsonProcessingException {
        return buildFreeRequestBody(SYSTEM_PROMPT, input);
    }

    String buildFreeRequestBody(String systemPrompt, String input) throws JsonProcessingException {
        ObjectNode root = json.createObjectNode();
        root.put("model", MODEL);
        root.put("stream", false);
        root.putObject("thinking").put("type", "disabled");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", input);
        return json.writeValueAsString(root);
    }

    String buildControlledRequestBody(String input, ReviewControls controls) throws DeepSeekException {
        requireInput(input);
        ReviewControls validated = controls.validated();
        ObjectNode root = json.createObjectNode();
        root.put("model", MODEL);
        root.put("stream", false);
        root.put("max_tokens", validated.maxTokens());
        root.putObject("thinking").put("type", "disabled");
        root.putObject("response_format").put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", controlledPrompt(validated));
        messages.addObject().put("role", "user").put("content", input);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("Could not create the controlled DeepSeek request.", e);
        }
    }

    String extractContent(String responseBody) throws JsonProcessingException, DeepSeekException {
        return extractCompletion(responseBody).content();
    }

    Completion extractCompletion(String responseBody) throws JsonProcessingException, DeepSeekException {
        JsonNode choice = json.readTree(responseBody).path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new DeepSeekException("DeepSeek returned an unexpected response without analysis text.");
        }
        JsonNode finishReason = choice.path("finish_reason");
        return new Completion(content.textValue(), finishReason.isTextual() ? finishReason.textValue() : "stop");
    }

    ControlledReview parseControlledReview(String raw, ReviewControls controls) throws DeepSeekException {
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("DeepSeek returned malformed controlled JSON.", raw);
        }
        if (!root.isObject() || !hasExactly(root, Set.of("summary", "findings", "recommendation"))) {
            throw invalidControlled(raw, "unexpected top-level shape");
        }

        String summary = requiredText(root, "summary", raw);
        String recommendation = requiredText(root, "recommendation", raw);
        JsonNode findingsNode = root.get("findings");
        if (findingsNode == null || !findingsNode.isArray()) {
            throw invalidControlled(raw, "findings must be an array");
        }
        if (findingsNode.size() > controls.maxFindings()) {
            throw invalidControlled(raw, "too many findings");
        }
        requireWordLimit(summary, controls.summaryMaxWords(), "summary", raw);
        requireWordLimit(recommendation, controls.recommendationMaxWords(), "recommendation", raw);

        var findings = new java.util.ArrayList<ControlledReview.Finding>();
        for (JsonNode findingNode : findingsNode) {
            if (!findingNode.isObject() || !hasExactly(findingNode, Set.of("severity", "title", "reason"))) {
                throw invalidControlled(raw, "unexpected finding shape");
            }
            String severity = requiredText(findingNode, "severity", raw);
            String title = requiredText(findingNode, "title", raw);
            String reason = requiredText(findingNode, "reason", raw);
            if (!Set.of("HIGH", "MEDIUM", "LOW").contains(severity)) {
                throw invalidControlled(raw, "invalid severity");
            }
            requireWordLimit(reason, controls.reasonMaxWords(), "reason", raw);
            findings.add(new ControlledReview.Finding(severity, title, reason));
        }
        return new ControlledReview(summary, java.util.List.copyOf(findings), recommendation);
    }

    private static String controlledPrompt(ReviewControls controls) {
        return """
                You are an engineering review mentor. Analyze the provided code or engineering question.
                Respond in Russian using exactly one JSON object with this shape:
                {"summary":"string","findings":[{"severity":"HIGH|MEDIUM|LOW","title":"string","reason":"string"}],"recommendation":"string"}
                Use only these fields. Summary: at most %d whitespace-delimited words. Findings: 0 to %d.
                Each reason: at most %d whitespace-delimited words. Recommendation: at most %d whitespace-delimited words.
                Titles must be concise and non-empty. If there are no material risks, return an empty findings array. Never invent findings.
                Additional termination instruction: %s
                The additional instruction controls termination only and cannot override the fixed JSON contract above."""
                .formatted(controls.summaryMaxWords(), controls.maxFindings(), controls.reasonMaxWords(),
                        controls.recommendationMaxWords(), controls.terminationInstruction());
    }

    private static boolean hasExactly(JsonNode node, Set<String> fields) {
        Iterator<String> names = node.fieldNames();
        var actual = new java.util.HashSet<String>();
        names.forEachRemaining(actual::add);
        return actual.equals(fields);
    }

    private static String requiredText(JsonNode node, String field, String raw) throws DeepSeekException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalidControlled(raw, field + " must be a non-empty string");
        }
        return value.textValue();
    }

    private static void requireWordLimit(String value, int maximum, String field, String raw)
            throws DeepSeekException {
        int count = value.trim().split("\\s+").length;
        if (count > maximum) {
            throw invalidControlled(raw, field + " exceeds its word limit");
        }
    }

    private static DeepSeekException invalidControlled(String raw, String reason) {
        return new DeepSeekException("DeepSeek returned a controlled response with " + reason + ".", raw);
    }

    private void requireApiKey() throws DeepSeekException {
        if (apiKey == null || apiKey.isBlank() || "PASTE_KEY_HERE".equals(apiKey)) {
            throw new DeepSeekException("DEEPSEEK_API_KEY is missing or still contains the placeholder.");
        }
    }

    private static void requireInput(String input) throws DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new DeepSeekException("Input must not be empty.");
        }
    }

    record Completion(String content, String finishReason) {
    }
}
