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

final class DeepSeekTransport {
    private static final URI API_URI = URI.create("https://api.deepseek.com/chat/completions");

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final String apiKey;

    DeepSeekTransport(HttpClient httpClient, ObjectMapper json, String apiKey) {
        this.httpClient = httpClient;
        this.json = json;
        this.apiKey = apiKey;
    }

    Completion complete(AgentModelRequest request) throws DeepSeekException {
        return complete(request, false);
    }

    Completion complete(AgentModelRequest request, boolean jsonObjectResponse) throws DeepSeekException {
        try {
            return send(buildRequestBody(request, jsonObjectResponse));
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException("Could not create the DeepSeek conversation request.", exception);
        }
    }

    String buildRequestBody(AgentModelRequest request) throws JsonProcessingException {
        return buildRequestBody(request, false);
    }

    String buildRequestBody(AgentModelRequest request, boolean jsonObjectResponse) throws JsonProcessingException {
        ObjectNode root = json.createObjectNode();
        root.put("model", request.model());
        root.put("stream", false);
        root.putObject("thinking").put("type", "disabled");
        if (request.temperature() != null) root.put("temperature", request.temperature());
        if (request.maxTokens() != null) root.put("max_tokens", request.maxTokens());
        if (jsonObjectResponse) root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        for (AgentModelMessage message : request.messages()) {
            messages.addObject().put("role", message.role()).put("content", message.content());
        }
        return json.writeValueAsString(root);
    }

    Completion extractCompletion(String responseBody) throws JsonProcessingException, DeepSeekException {
        JsonNode root = json.readTree(responseBody);
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new DeepSeekException("DeepSeek returned an unexpected response without analysis text.");
        }
        JsonNode finishReason = choice.path("finish_reason");
        JsonNode usage = root.path("usage");
        ProviderUsage providerUsage = usage.isObject()
                ? new ProviderUsage(number(usage, "prompt_tokens"), number(usage, "completion_tokens"),
                number(usage, "total_tokens"))
                : null;
        return new Completion(content.textValue(), finishReason.isTextual() ? finishReason.textValue() : "stop",
                providerUsage);
    }

    Completion send(String requestBody) throws DeepSeekException {
        requireApiKey();
        HttpRequest request = HttpRequest.newBuilder(API_URI)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new DeepSeekException("DeepSeek request failed due to a transport problem.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekException("DeepSeek request was interrupted.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DeepSeekException("DeepSeek API returned HTTP " + response.statusCode() + ".");
        }
        try {
            return extractCompletion(response.body());
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException("DeepSeek returned a malformed response.", exception);
        }
    }

    private void requireApiKey() throws DeepSeekException {
        if (apiKey == null || apiKey.isBlank() || "PASTE_KEY_HERE".equals(apiKey)) {
            throw new DeepSeekException("DEEPSEEK_API_KEY is missing or still contains the placeholder.");
        }
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

    record Completion(String content, String finishReason, ProviderUsage usage) {
    }
}
