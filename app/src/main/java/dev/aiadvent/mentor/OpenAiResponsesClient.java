package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

@Component
class OpenAiResponsesClient {
    static final String INSTRUCTION = """
            You are an engineering review mentor.
            Analyze the provided code and identify real engineering risks and practical reliability improvements.
            Clearly separate confirmed issues from assumptions not proven by the snippet.
            Respond in Russian using Markdown when it improves readability.""";
    static final Preset PRESET = new Preset("day5-model-review-v1", INSTRUCTION, "none", 2000,
            "default", false, false);
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;
    private final Duration timeout;
    private final String apiKey;

    @Autowired
    OpenAiResponsesClient(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                URI.create("https://api.openai.com/v1/responses"), Duration.ofSeconds(120),
                System.getenv("OPENAI_API_KEY"));
    }

    OpenAiResponsesClient(ObjectMapper json, HttpClient http, URI endpoint, Duration timeout, String apiKey) {
        this.json = json; this.http = http; this.endpoint = endpoint; this.timeout = timeout; this.apiKey = apiKey;
    }

    Result analyze(ModelProfile model, String input) {
        Instant startedAt = Instant.now();
        if (apiKey == null || apiKey.isBlank() || "PASTE_KEY_HERE".equals(apiKey)) {
            return failure(model, startedAt, null, null, "OPENAI_API_KEY не задан в backend environment.");
        }
        HttpRequest request;
        try {
            var body = json.createObjectNode();
            body.put("model", model.modelId());
            var messages = body.putArray("input");
            messages.addObject().put("role", "developer").put("content", INSTRUCTION);
            messages.addObject().put("role", "user").put("content", input);
            body.putObject("reasoning").put("effort", PRESET.reasoningEffort);
            body.put("max_output_tokens", PRESET.maxOutputTokens);
            body.put("service_tier", PRESET.serviceTier);
            body.put("stream", false); body.put("store", false);
            request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
        } catch (IOException | IllegalArgumentException exception) {
            return failure(model, startedAt, null, null, "Не удалось подготовить запрос OpenAI.");
        }
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            return failure(model, startedAt, elapsed(start), null, "Истекло время ожидания OpenAI; стоимость неизвестна.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(model, startedAt, elapsed(start), null, "Запрос OpenAI прерван; стоимость неизвестна.");
        } catch (IOException exception) {
            return failure(model, startedAt, elapsed(start), null, "Ошибка связи с OpenAI; стоимость неизвестна.");
        }
        long latency = elapsed(start);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return failure(model, startedAt, latency, response.statusCode(), "OpenAI HTTP " + response.statusCode()
                    + ": проверьте доступ, баланс или лимиты. Автоповтор отключён.");
        }
        try {
            return parse(model, json.readTree(response.body()), startedAt, latency, response.statusCode());
        } catch (IOException | IllegalArgumentException exception) {
            return failure(model, startedAt, latency, response.statusCode(), "Некорректный ответ OpenAI.");
        }
    }

    Result parse(ModelProfile model, JsonNode root, Instant startedAt, long latency, int httpStatus) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid response object");
        var parts = new ArrayList<String>();
        boolean refusal = false;
        if (root.path("output").isArray()) {
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(text(item, "type")) || !"assistant".equals(text(item, "role"))) continue;
                if (!item.path("content").isArray()) continue;
                for (JsonNode content : item.path("content")) {
                    if ("refusal".equals(text(content, "type"))) refusal = true;
                    if ("output_text".equals(text(content, "type")) && text(content, "text") != null) {
                        parts.add(text(content, "text"));
                    }
                }
            }
        }
        String analysis = String.join("\n\n", parts);
        var usageNode = root.path("usage");
        var usage = new ModelProfile.Usage(number(usageNode, "input_tokens"), number(usageNode, "output_tokens"),
                number(usageNode, "total_tokens"), number(usageNode.path("input_tokens_details"), "cached_tokens"),
                number(usageNode.path("input_tokens_details"), "cache_write_tokens"),
                number(usageNode.path("output_tokens_details"), "reasoning_tokens"));
        String status = text(root, "status"), tier = text(root, "service_tier");
        String error = !"completed".equals(status) ? "OpenAI не завершил ответ; смотрите статус и причину."
                : refusal ? "OpenAI отказался отвечать." : analysis.isBlank() ? "OpenAI не вернул текст анализа."
                : root.hasNonNull("error") ? "OpenAI вернул ошибку генерации." : null;
        if (tier != null && !"default".equals(tier)) error = "OpenAI использовал отличный от default service tier.";
        String returnedModel = text(root, "model");
        var cost = model.modelId().equals(returnedModel) ? model.pricing().calculate(usage, tier)
                : model.pricing().unknown("Возвращённая модель не подтверждает тарифный snapshot.");
        return new Result(model, returnedModel, status,
                text(root.path("incomplete_details"), "reason"), tier, startedAt.toString(), latency,
                httpStatus, analysis.isBlank() ? null : analysis, usage, cost, PRESET, error);
    }

    private Result failure(ModelProfile model, Instant start, Long latency, Integer status, String error) {
        return new Result(model, null, null, null, null, start.toString(), latency, status, null,
                new ModelProfile.Usage(null, null, null, null, null, null), model.pricing().unknown(error), PRESET, error);
    }

    private static long elapsed(long start) { return Duration.ofNanos(System.nanoTime() - start).toMillis(); }
    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key); return value.isTextual() ? value.textValue() : null;
    }
    private static Long number(JsonNode node, String key) {
        JsonNode value = node.path(key); return value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

    record Preset(String version, String developerInstruction, String reasoningEffort, int maxOutputTokens,
                  String serviceTier, boolean stream, boolean store) { }
    record Result(ModelProfile model, String returnedModel, String status, String incompleteReason,
                  String serviceTier, String startedAt, Long apiLatencyMs, Integer httpStatus, String analysis,
                  ModelProfile.Usage usage, ModelProfile.Cost cost, Preset configuration, String error) { }
}
