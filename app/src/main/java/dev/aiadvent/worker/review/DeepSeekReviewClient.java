package dev.aiadvent.worker.review;

import dev.aiadvent.worker.model.AgentModelMessage;
import dev.aiadvent.worker.model.AgentModelRequest;
import dev.aiadvent.worker.model.DeepSeekException;
import dev.aiadvent.worker.model.DeepSeekTransport;
import dev.aiadvent.worker.model.ProviderUsage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DeepSeekReviewClient {
    private static final String MODEL = "deepseek-v4-flash";
    private static final String SYSTEM_PROMPT = """
            You are an engineering review mentor.
            Analyze the provided code or engineering question.
            Explain potential engineering risks clearly and concisely.
            Respond in Russian.
            Format the response using Markdown when it improves readability.""";

    private final ObjectMapper json;
    private final DeepSeekTransport transport;

    public DeepSeekReviewClient(HttpClient httpClient, ObjectMapper json, String apiKey) {
        this(new DeepSeekTransport(httpClient, json, apiKey), json);
    }

    public DeepSeekReviewClient(DeepSeekTransport transport, ObjectMapper json) {
        this.transport = transport;
        this.json = json;
    }

    public String analyze(String input) throws DeepSeekException {
        return analyzeWithSystem(SYSTEM_PROMPT, input);
    }

    String analyzeWithSystem(String systemPrompt, String input) throws DeepSeekException {
        try {
            String requestBody = buildFreeRequestBody(systemPrompt, input);
            requireInput(input);
            return transport.send(requestBody).content();
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException("Could not create the DeepSeek request.", exception);
        }
    }

    String analyzeAtTemperature(String systemPrompt, String input, double temperature) throws DeepSeekException {
        try {
            String requestBody = buildTemperatureRequestBody(systemPrompt, input, temperature);
            requireInput(input);
            return transport.send(requestBody).content();
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException("Could not create the temperature DeepSeek request.", exception);
        }
    }

    ControlledAnalysis analyzeControlled(String input, ReviewControls controls) throws DeepSeekException {
        ReviewControls validatedControls = controls.validated();
        Completion completion = completion(transport.send(buildControlledRequestBody(input, validatedControls)));
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

    String buildRequestBody(String input) throws JsonProcessingException {
        return buildFreeRequestBody(input);
    }

    String buildFreeRequestBody(String input) throws JsonProcessingException {
        return buildFreeRequestBody(SYSTEM_PROMPT, input);
    }

    String buildFreeRequestBody(String systemPrompt, String input) throws JsonProcessingException {
        return transport.buildRequestBody(freeRequest(systemPrompt, input));
    }

    String buildTemperatureRequestBody(String systemPrompt, String input, double temperature)
            throws JsonProcessingException {
        return transport.buildRequestBody(temperatureRequest(systemPrompt, input, temperature));
    }

    String buildControlledRequestBody(String input, ReviewControls controls) throws DeepSeekException {
        requireInput(input);
        ReviewControls validated = controls.validated();
        try {
            return transport.buildRequestBody(controlledRequest(input, validated), true);
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("Could not create the controlled DeepSeek request.", e);
        }
    }

    String extractContent(String responseBody) throws JsonProcessingException, DeepSeekException {
        return extractCompletion(responseBody).content();
    }

    Completion extractCompletion(String responseBody) throws JsonProcessingException, DeepSeekException {
        return completion(transport.extractCompletion(responseBody));
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

    private static Completion completion(DeepSeekTransport.Completion completion) {
        return new Completion(completion.content(), completion.finishReason(), completion.usage());
    }

    private static AgentModelRequest freeRequest(String systemPrompt, String input) {
        return new AgentModelRequest(List.of(new AgentModelMessage("system", systemPrompt),
                new AgentModelMessage("user", input)), MODEL, null, null);
    }

    private static AgentModelRequest temperatureRequest(String systemPrompt, String input, double temperature) {
        return new AgentModelRequest(List.of(new AgentModelMessage("system", systemPrompt),
                new AgentModelMessage("user", input)), MODEL, temperature, null);
    }

    private static AgentModelRequest controlledRequest(String input, ReviewControls controls) {
        return new AgentModelRequest(List.of(new AgentModelMessage("system", controlledPrompt(controls)),
                new AgentModelMessage("user", input)), MODEL, null, controls.maxTokens());
    }

    private static void requireInput(String input) throws DeepSeekException {
        if (input == null || input.isBlank()) {
            throw new DeepSeekException("Input must not be empty.");
        }
    }

    record Completion(String content, String finishReason, ProviderUsage usage) {
    }
}
