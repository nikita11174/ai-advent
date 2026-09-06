package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelReviewTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;
    private OpenAiResponsesClient client;
    private String response;
    private int responseStatus = 200;
    private static final String SECRET = "test-only-private-key";

    @BeforeEach
    void setup() throws Exception {
        response = fixture().toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/responses", exchange -> {
            calls.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new OpenAiResponsesClient(json, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"),
                Duration.ofSeconds(2), SECRET);
    }

    @AfterEach
    void stop() { server.stop(0); }

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) json.readTree("""
                {"model":"gpt-5.6-luna","status":"completed","service_tier":"default",
                 "output":[{"type":"reasoning"},{"type":"message","role":"assistant","content":[
                   {"type":"output_text","text":"## Риск"},{"type":"output_text","text":"Проверка"}]}],
                 "usage":{"input_tokens":100,"output_tokens":20,"total_tokens":120,
                   "input_tokens_details":{"cached_tokens":40,"cache_write_tokens":10},
                   "output_tokens_details":{"reasoning_tokens":0}}}
                """);
    }

    @Test
    void allProfilesRouteExactCommonPresetAndReturnRealMetadata() throws Exception {
        String input = "  class A {\n int value;\n}\n";
        for (ModelProfile model : ModelProfile.MODELS) {
            response = fixture().put("model", model.modelId()).toString();
            var result = client.analyze(model, input);
            var body = json.readTree(requestBody.get());
            assertEquals(model.modelId(), body.path("model").asText());
            assertEquals(7, body.size());
            assertEquals(OpenAiResponsesClient.INSTRUCTION, body.path("input").get(0).path("content").asText());
            assertEquals("developer", body.path("input").get(0).path("role").asText());
            assertEquals("user", body.path("input").get(1).path("role").asText());
            assertEquals(input, body.path("input").get(1).path("content").asText());
            assertEquals("none", body.path("reasoning").path("effort").asText());
            assertEquals(2000, body.path("max_output_tokens").asInt());
            assertEquals("default", body.path("service_tier").asText());
            assertFalse(body.path("stream").asBoolean()); assertFalse(body.path("store").asBoolean());
            assertEquals("## Риск\n\nПроверка", result.analysis());
            assertEquals(model.modelId(), result.returnedModel()); assertNull(result.error());
            assertTrue(result.apiLatencyMs() >= 0); assertNotNull(Instant.parse(result.startedAt()));
            assertEquals(100L, result.usage().inputTokens()); assertEquals(0L, result.usage().reasoningTokens());
            assertEquals("ESTIMATED", result.cost().status());
        }
        assertEquals(3, calls.get());
        assertEquals("gpt-5.6-luna", ModelProfile.resolve("WEAK").modelId());
        assertEquals("gpt-5.6-terra", ModelProfile.resolve("MEDIUM").modelId());
        assertEquals("gpt-5.6-sol", ModelProfile.resolve("STRONG").modelId());
    }

    @Test
    void unknownModelAndEmptyInputReturn400WithoutCall() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ModelReviewController(client))
                .setControllerAdvice(new ReviewController.ApiExceptionHandler()).build();
        for (String body : new String[]{"{\"modelKey\":\"OTHER\",\"input\":\"x\"}",
                "{\"modelKey\":\"WEAK\",\"input\":\" \"}", "{\"input\":\"x\"}"}) {
            mvc.perform(post("/api/model-review").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void pricesAllCategoriesUsingDecimalAndPreservesSnapshot() {
        var usage = new ModelProfile.Usage(100L, 20L, 120L, 40L, 10L, 0L);
        var result = ModelProfile.resolve("WEAK").pricing().calculate(usage, "default");
        assertEquals("0.0000373", result.amount());
        assertEquals("openai-gpt56-standard-2026-09-05", result.snapshot().id());
        assertEquals("USD", result.snapshot().currency());
        assertFalse(result.snapshot().sources().isEmpty());
    }

    @Test
    void missingMetadataRemainsUnknown() throws Exception {
        ObjectNode body = fixture(); body.remove("usage"); body.remove("model"); body.remove("status"); body.remove("service_tier");
        response = body.toString();
        var result = client.analyze(ModelProfile.resolve("WEAK"), "x");
        assertNull(result.usage().inputTokens()); assertNull(result.usage().reasoningTokens());
        assertNull(result.returnedModel()); assertNull(result.status()); assertNull(result.incompleteReason());
        assertEquals("UNKNOWN", result.cost().status()); assertNull(result.cost().amount());
        assertNotNull(result.error());
    }

    @Test
    void incompleteAndRefusalPreserveAvailableEvidence() throws Exception {
        ObjectNode body = fixture(); body.put("status", "incomplete");
        body.putObject("incomplete_details").put("reason", "max_output_tokens");
        response = body.toString();
        var result = client.analyze(ModelProfile.resolve("WEAK"), "x");
        assertEquals("max_output_tokens", result.incompleteReason());
        assertNotNull(result.analysis()); assertNotNull(result.error()); assertEquals(120L, result.usage().totalTokens());
        body = fixture();
        body.withArray("output").removeAll().addObject().put("type", "message").put("role", "assistant")
                .putArray("content").addObject().put("type", "refusal").put("refusal", "No");
        response = body.toString();
        assertNotNull(client.analyze(ModelProfile.resolve("WEAK"), "x").error());
    }

    @Test
    void unsupportedOrInconsistentPricingIsUnknown() {
        var price = ModelProfile.resolve("STRONG").pricing();
        for (var usage : new ModelProfile.Usage[]{
                new ModelProfile.Usage(100L, 20L, 120L, null, null, null),
                new ModelProfile.Usage(100L, 20L, 120L, 100L, 1L, 0L),
                new ModelProfile.Usage(100L, 20L, 999L, 0L, 0L, 0L),
                new ModelProfile.Usage(300000L, 20L, 300020L, 0L, 0L, 0L),
                new ModelProfile.Usage(100L, -1L, 99L, 0L, 0L, 0L)}) {
            assertNull(price.calculate(usage, "default").amount());
        }
        assertEquals("UNKNOWN", price.calculate(new ModelProfile.Usage(1L, 1L, 2L, 0L, 0L, 0L), "priority").status());
    }

    @Test
    void httpFailuresNeverExposeProviderBodyOrSecretAndNeverRetry() throws Exception {
        responseStatus = 401; response = "{\"error\":{\"message\":\"" + SECRET + "\"}}";
        var result = client.analyze(ModelProfile.resolve("WEAK"), "x");
        assertEquals(401, result.httpStatus()); assertNotNull(result.error());
        assertFalse(json.writeValueAsString(result).contains(SECRET)); assertEquals(1, calls.get());
        responseStatus = 200; response = "invalid-json";
        assertNotNull(client.analyze(ModelProfile.resolve("WEAK"), "x").error());
        response = "null";
        assertNotNull(client.analyze(ModelProfile.resolve("WEAK"), "x").error());
    }

    @Test
    void timeoutAndMissingKeyFailClearlyWithoutSecretOrRetry() throws Exception {
        HttpClient transport = mock(HttpClient.class);
        when(transport.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException(SECRET));
        var timed = new OpenAiResponsesClient(json, transport, URI.create("https://api.openai.com/v1/responses"), Duration.ofSeconds(120), SECRET);
        var result = timed.analyze(ModelProfile.resolve("WEAK"), "x");
        assertTrue(result.error().contains("время")); assertFalse(result.error().contains(SECRET));
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(transport).send(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals(Duration.ofSeconds(120), request.getValue().timeout().orElseThrow());
        var missing = new OpenAiResponsesClient(json, transport, URI.create("https://api.openai.com/v1/responses"), Duration.ofSeconds(120), null);
        assertTrue(missing.analyze(ModelProfile.resolve("WEAK"), "x").error().contains("OPENAI_API_KEY"));
        verifyNoMoreInteractions(transport);
    }

    @Test
    void persistedModelMetadataSurvivesStoreRestart(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        json.findAndRegisterModules();
        var result = client.analyze(ModelProfile.resolve("WEAK"), "benchmark");
        var state = json.createObjectNode();
        state.putObject("ui").put("experiment", "MODELS").put("selectedModelKey", "WEAK");
        var exchange = state.putArray("exchanges").addObject().put("input", "benchmark").put("mode", "MODELS")
                .put("modelConclusion", "Ничья");
        exchange.putObject("modelResults").set("WEAK", json.valueToTree(result));
        var store = new DialogStore(directory, json, Clock.systemUTC());
        var dialog = store.create(); store.update(dialog.id(), new DialogStore.DialogUpdate("Review", state));
        var restored = new DialogStore(directory, json, Clock.systemUTC()).load(dialog.id());
        assertEquals(json.readTree(json.writeValueAsString(state)), restored.state());
        assertFalse(json.writeValueAsString(restored).contains(SECRET));
    }

    @Test
    void unexpectedModelNeverUsesRequestedModelsPrice() throws Exception {
        response = fixture().put("model", "other-model").toString();
        assertEquals("UNKNOWN", client.analyze(ModelProfile.resolve("WEAK"), "x").cost().status());
    }
}
