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
        requireInput(input);
        requireApiKey();

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(API_URI)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(input), StandardCharsets.UTF_8))
                    .build();
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("Could not create the DeepSeek request.", e);
        }

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
            return extractContent(response.body());
        } catch (JsonProcessingException e) {
            throw new DeepSeekException("DeepSeek returned a malformed response.", e);
        }
    }

    String buildRequestBody(String input) throws JsonProcessingException {
        ObjectNode root = json.createObjectNode();
        root.put("model", MODEL);
        root.put("stream", false);
        root.putObject("thinking").put("type", "disabled");

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", input);
        return json.writeValueAsString(root);
    }

    String extractContent(String responseBody) throws JsonProcessingException, DeepSeekException {
        JsonNode content = json.readTree(responseBody).path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.textValue().isBlank()) {
            throw new DeepSeekException("DeepSeek returned an unexpected response without analysis text.");
        }
        return content.textValue();
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
}
