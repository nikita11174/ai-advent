package dev.aiadvent.worker.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolDefinition;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class DeepSeekTransport {
    private static final URI API_URI = URI.create("https://api.deepseek.com/chat/completions");

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final String apiKey;

    public DeepSeekTransport(HttpClient httpClient, ObjectMapper json, String apiKey) {
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

    public String buildRequestBody(AgentModelRequest request) throws JsonProcessingException {
        return buildRequestBody(request, false);
    }

    public String buildRequestBody(AgentModelRequest request, boolean jsonObjectResponse) throws JsonProcessingException {
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

    String buildToolRequestBody(AgentModelRequest request, ToolDefinition tool) throws DeepSeekException {
        if (!tool.name().matches("[A-Za-z0-9_-]{1,128}") || !tool.inputSchema().isObject()) {
            throw new DeepSeekException("INVALID_TOOL_DEFINITION");
        }
        try {
            ObjectNode root = (ObjectNode) json.readTree(buildRequestBody(request));
            ObjectNode function = root.putArray("tools").addObject().put("type", "function").putObject("function");
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.inputSchema());
            root.putObject("tool_choice").put("type", "function").putObject("function").put("name", tool.name());
            return json.writeValueAsString(root);
        }
        catch (JsonProcessingException e) {
            throw new DeepSeekException("TOOL_REQUEST_PREPARATION_FAILED", e);
        }
    }

    String buildToolContinuationBody(String firstBody, NativeToolCall call, ToolResult result)
            throws DeepSeekException {
        if (!call.name().equals(result.name()) || !result.structuredResult().isObject()) {
            throw new DeepSeekException("INVALID_TOOL_RESULT");
        }
        try {
            ObjectNode root = (ObjectNode) json.readTree(firstBody);
            root.remove("tools");
            root.put("tool_choice", "none");
            ArrayNode messages = (ArrayNode) root.path("messages");
            ObjectNode assistant = messages.addObject().put("role", "assistant");
            if (call.assistantContent() == null) assistant.putNull("content");
            else assistant.put("content", call.assistantContent());
            ObjectNode nativeCall = assistant.putArray("tool_calls").addObject();
            nativeCall.put("id", call.id());
            nativeCall.put("type", "function");
            nativeCall.putObject("function").put("name", call.name()).put("arguments", call.rawArguments());
            messages.addObject().put("role", "tool").put("tool_call_id", call.id())
                    .put("content", json.writeValueAsString(result.structuredResult()));
            return json.writeValueAsString(root);
        }
        catch (JsonProcessingException e) {
            throw new DeepSeekException("TOOL_CONTINUATION_PREPARATION_FAILED", e);
        }
    }

    ToolDecision extractToolDecision(String responseBody) throws DeepSeekException {
        JsonNode root = parseToolResponse(responseBody);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (!"assistant".equals(message.path("role").asText())) {
            throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
        }
        String finishReason = choice.path("finish_reason").asText();
        ProviderUsage usage = toolUsage(root);
        if ("tool_calls".equals(finishReason)) {
            JsonNode calls = message.path("tool_calls");
            if (!calls.isArray() || calls.isEmpty()) throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
            if (calls.size() != 1) throw new DeepSeekException("TOOL_CALL_LIMIT");
            JsonNode nativeCall = calls.get(0);
            String id = nativeCall.path("id").asText();
            String name = nativeCall.path("function").path("name").asText();
            JsonNode rawArguments = nativeCall.path("function").path("arguments");
            JsonNode content = message.path("content");
            if (!nativeCall.path("id").isTextual() || id.isBlank()
                    || !nativeCall.path("function").path("name").isTextual() || name.isBlank()
                    || !"function".equals(nativeCall.path("type").asText())
                    || !rawArguments.isTextual() || (!content.isNull() && !content.isTextual())) {
                throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
            }
            JsonNode arguments;
            try (var parser = json.createParser(rawArguments.textValue())) {
                arguments = json.readTree(parser);
                if (arguments == null || !arguments.isObject() || parser.nextToken() != null) {
                    throw new DeepSeekException("MALFORMED_TOOL_ARGUMENTS");
                }
            }
            catch (IOException e) {
                throw new DeepSeekException("MALFORMED_TOOL_ARGUMENTS", e);
            }
            return new ToolDecision(null, new NativeToolCall(id, name, arguments,
                    rawArguments.textValue(), content.isTextual() ? content.textValue() : null), usage);
        }
        if ("stop".equals(finishReason) && (!message.path("tool_calls").isArray()
                || message.path("tool_calls").isEmpty())) {
            JsonNode content = message.path("content");
            if (content.isTextual() && !content.textValue().isBlank()) {
                return new ToolDecision(content.textValue(), null, usage);
            }
        }
        throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
    }

    AgentModelExecutor.Completion extractToolFinal(String responseBody) throws DeepSeekException {
        JsonNode root = parseToolResponse(responseBody);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        if ("tool_calls".equals(choice.path("finish_reason").asText())
                || (message.path("tool_calls").isArray() && !message.path("tool_calls").isEmpty())) {
            throw new DeepSeekException("TOOL_CALL_LIMIT");
        }
        JsonNode content = message.path("content");
        if (!"stop".equals(choice.path("finish_reason").asText()) || !"assistant".equals(message.path("role").asText())
                || !content.isTextual() || content.textValue().isBlank()) {
            throw new DeepSeekException("MODEL_CONTINUATION_INCOMPLETE");
        }
        return new AgentModelExecutor.Completion(content.textValue(), toolUsage(root));
    }

    private JsonNode parseToolResponse(String responseBody) throws DeepSeekException {
        if (responseBody == null || responseBody.getBytes(StandardCharsets.UTF_8).length > 262_144) {
            throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
        }
        try {
            JsonNode root = json.readTree(responseBody);
            if (root == null || !root.path("choices").isArray() || root.path("choices").size() != 1
                    || !root.path("choices").path(0).path("message").isObject()) {
                throw new DeepSeekException("MALFORMED_TOOL_RESPONSE");
            }
            return root;
        }
        catch (JsonProcessingException e) {
            throw new DeepSeekException("MALFORMED_TOOL_RESPONSE", e);
        }
    }

    private static ProviderUsage toolUsage(JsonNode root) throws DeepSeekException {
        JsonNode usage = root.path("usage");
        try {
            return usage.isObject()
                    ? new ProviderUsage(number(usage, "prompt_tokens"), number(usage, "completion_tokens"),
                    number(usage, "total_tokens"))
                    : null;
        }
        catch (IllegalArgumentException e) {
            throw new DeepSeekException("MALFORMED_TOOL_RESPONSE", e);
        }
    }

    public Completion extractCompletion(String responseBody) throws JsonProcessingException, DeepSeekException {
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

    public Completion send(String requestBody) throws DeepSeekException {
        try {
            return extractCompletion(sendBody(requestBody));
        } catch (JsonProcessingException exception) {
            throw new DeepSeekException("DeepSeek returned a malformed response.", exception);
        }
    }

    String sendBody(String requestBody) throws DeepSeekException {
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
        return response.body();
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

    public record Completion(String content, String finishReason, ProviderUsage usage) {
    }

    record ToolDecision(String text, NativeToolCall call, ProviderUsage usage) {
    }

    record NativeToolCall(String id, String name, JsonNode arguments, String rawArguments, String assistantContent) {
    }
}
