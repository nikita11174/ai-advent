package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.ModelTestFixtures;

import dev.aiadvent.mentor.model.AgentModelMessage;
import dev.aiadvent.mentor.model.ModelProfile;
import dev.aiadvent.mentor.model.OpenAiResponsesClient;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
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
        client = ModelTestFixtures.openAiClient(json, HttpClient.newHttpClient(),
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

}
