package dev.aiadvent.worker.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeepSeekToolCallingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final DeepSeekTransport transport = new DeepSeekTransport(http, json, "test-key");
    private final ToolCapableModelExecutor executor = new DeepSeekAgentModelExecutor(transport);
    private final AgentModelRequest request = new AgentModelRequest(List.of(
            new AgentModelMessage("system", "Answer using repository evidence."),
            new AgentModelMessage("user", "What is the current branch?")), "deepseek-v4-flash", 0.2, 100);
    private final ToolCapableModelExecutor.ToolDefinition tool = new ToolCapableModelExecutor.ToolDefinition(
            "git_repository_status", "Read repository status", json.readTree("""
                    {"type":"object","properties":{"includeChangeCounts":{"type":"boolean"}},
                     "required":["includeChangeCounts"],"additionalProperties":false}
                    """));

    DeepSeekToolCallingTest() throws Exception {
    }

    @Test
    void preparesForcedNativeCallThenReplaysExactCallIdAndToolResult() throws Exception {
        String rawArguments = " \n{ \"includeChangeCounts\" : true } \t";
        String firstResponse = toolCallResponse("call-abc", tool.name(), rawArguments, 60, 18);
        String secondResponse = finalResponse("The project is on main.", 103, 25);
        mockResponses(firstResponse, secondResponse);

        var first = executor.prepareToolTurn(request, tool);
        assertPositiveFootprint(first.estimatedInputTokens(), first.serializedBytes());
        verifyNoInteractions(http);

        var step = assertInstanceOf(ToolCapableModelExecutor.ToolRequestStep.class, executor.beginToolTurn(first));
        assertEquals(tool.name(), step.request().name());
        assertEquals(true, step.request().arguments().path("includeChangeCounts").booleanValue());
        assertEquals(new ProviderUsage(60L, 18L, 78L), step.usage());
        var firstWire = sentBody(0);
        assertEquals("deepseek-v4-flash", firstWire.path("model").asText());
        assertEquals("disabled", firstWire.path("thinking").path("type").asText());
        assertEquals("function", firstWire.path("tool_choice").path("type").asText());
        assertEquals(tool.name(), firstWire.path("tool_choice").path("function").path("name").asText());
        assertEquals(1, firstWire.path("tools").size());
        assertEquals(tool.name(), firstWire.path("tools").path(0).path("function").path("name").asText());
        assertEquals(tool.inputSchema(), firstWire.path("tools").path(0).path("function").path("parameters"));
        assertEquals(json.valueToTree(request.messages()), firstWire.path("messages"));

        JsonNode status = json.readTree("{\"repositoryRef\":\"workspace\",\"branch\":\"main\"}");
        var next = executor.prepareContinuation(step.continuation(),
                new ToolCapableModelExecutor.ToolResult(tool.name(), status));
        assertPositiveFootprint(next.estimatedInputTokens(), next.serializedBytes());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var answer = executor.continueToolTurn(next);
        assertEquals("The project is on main.", answer.text());
        assertEquals(new ProviderUsage(103L, 25L, 128L), answer.usage());
        var secondWire = sentBody(1);
        assertEquals("none", secondWire.path("tool_choice").asText());
        assertFalse(secondWire.has("tools"));
        assertEquals(request.messages().size() + 2, secondWire.path("messages").size());
        var assistant = secondWire.path("messages").path(2);
        assertEquals("assistant", assistant.path("role").asText());
        assertTrue(assistant.path("content").isNull());
        assertEquals("call-abc", assistant.path("tool_calls").path(0).path("id").asText());
        assertEquals(rawArguments, assistant.path("tool_calls").path(0).path("function").path("arguments").asText());
        var resultMessage = secondWire.path("messages").path(3);
        assertEquals("tool", resultMessage.path("role").asText());
        assertEquals("call-abc", resultMessage.path("tool_call_id").asText());
        assertEquals(status, json.readTree(resultMessage.path("content").asText()));
    }

    @Test
    void directFirstAnswerRemainsDistinctFromRequiredToolDecision() throws Exception {
        mockResponses(finalResponse("I can answer directly.", 20, 8));
        var step = assertInstanceOf(ToolCapableModelExecutor.FinalAnswer.class,
                executor.beginToolTurn(executor.prepareToolTurn(request, tool)));
        assertEquals("I can answer directly.", step.text());
        assertEquals(new ProviderUsage(20L, 8L, 28L), step.usage());
    }

    @Test
    void rejectsMalformedZeroMultipleAndWrongNativeCalls() throws Exception {
        assertToolError("MALFORMED_TOOL_ARGUMENTS", toolCallResponse("call-1", tool.name(), "{", 1, 1));
        assertToolError("MALFORMED_TOOL_ARGUMENTS", toolCallResponse("call-1", tool.name(),
                "{\"includeChangeCounts\":true} garbage", 1, 1));
        assertToolError("MALFORMED_TOOL_ARGUMENTS", toolCallResponse("call-1", tool.name(),
                "{\"includeChangeCounts\":true} {}", 1, 1));
        assertToolError("MALFORMED_TOOL_ARGUMENTS", toolCallResponse("call-1", tool.name(), "[]", 1, 1));
        JsonNode emptyText = json.readTree(toolCallResponse("call-1", tool.name(), "{}", 1, 1));
        ((com.fasterxml.jackson.databind.node.ObjectNode) emptyText.path("choices").path(0).path("message"))
                .put("content", "");
        assertNotNull(transport.extractToolDecision(json.writeValueAsString(emptyText)).call());
        assertToolError("MALFORMED_TOOL_RESPONSE", """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[]}}]}
                """);
        JsonNode root = json.readTree(toolCallResponse("call-1", tool.name(), "{}", 1, 1));
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("choices").path(0).path("message").path("tool_calls"))
                .add(root.path("choices").path(0).path("message").path("tool_calls").path(0).deepCopy());
        assertToolError("TOOL_CALL_LIMIT", json.writeValueAsString(root));
        mockResponses(toolCallResponse("call-1", "other_tool", "{}", 1, 1));
        assertEquals("UNKNOWN_TOOL", assertThrows(ModelExecutionException.class,
                () -> executor.beginToolTurn(executor.prepareToolTurn(request, tool))).getMessage());
    }

    @Test
    void continuationRejectsAnotherNativeToolCall() throws Exception {
        mockResponses(toolCallResponse("call-1", tool.name(), "{\"includeChangeCounts\":false}", 2, 2),
                toolCallResponse("call-2", tool.name(), "{\"includeChangeCounts\":false}", 3, 3));
        var step = assertInstanceOf(ToolCapableModelExecutor.ToolRequestStep.class,
                executor.beginToolTurn(executor.prepareToolTurn(request, tool)));
        var next = executor.prepareContinuation(step.continuation(),
                new ToolCapableModelExecutor.ToolResult(tool.name(), json.readTree("{\"branch\":\"main\"}")));
        assertEquals("TOOL_CALL_LIMIT", assertThrows(ModelExecutionException.class,
                () -> executor.continueToolTurn(next)).getMessage());
        verify(http, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void preparationFailsClosedBeforeNetworkOnOversizedInputOrWrongResult() throws Exception {
        var huge = new AgentModelRequest(List.of(new AgentModelMessage("user", "x".repeat(262_144))),
                request.model(), null, null);
        assertEquals("TOOL_REQUEST_LIMIT", assertThrows(ModelExecutionException.class,
                () -> executor.prepareToolTurn(huge, tool)).getMessage());
        mockResponses(toolCallResponse("call-1", tool.name(), "{\"includeChangeCounts\":false}", 2, 2));
        var step = assertInstanceOf(ToolCapableModelExecutor.ToolRequestStep.class,
                executor.beginToolTurn(executor.prepareToolTurn(request, tool)));
        assertEquals("INVALID_TOOL_RESULT", assertThrows(ModelExecutionException.class,
                () -> executor.prepareContinuation(step.continuation(),
                        new ToolCapableModelExecutor.ToolResult("other_tool", json.readTree("{}")))).getMessage());
        var hugeResult = new ToolCapableModelExecutor.ToolResult(tool.name(),
                json.readTree("{\"value\":\"" + "x".repeat(262_144) + "\"}"));
        assertEquals("TOOL_REQUEST_LIMIT", assertThrows(ModelExecutionException.class,
                () -> executor.prepareContinuation(step.continuation(), hugeResult)).getMessage());
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private void assertToolError(String expected, String responseBody) throws Exception {
        var error = assertThrows(DeepSeekException.class, () -> transport.extractToolDecision(responseBody));
        assertEquals(expected, error.getMessage());
    }

    private void mockResponses(String... bodies) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(bodies[0], java.util.Arrays.copyOfRange(bodies, 1, bodies.length));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    private JsonNode sentBody(int index) throws Exception {
        var sent = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(index + 1)).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        return json.readTree(body(sent.getAllValues().get(index)));
    }

    private String toolCallResponse(String id, String name, String rawArguments, int prompt, int completion)
            throws Exception {
        var root = json.createObjectNode();
        var choice = root.putArray("choices").addObject().put("finish_reason", "tool_calls");
        var message = choice.putObject("message").put("role", "assistant").putNull("content");
        var call = message.putArray("tool_calls").addObject().put("id", id).put("type", "function");
        call.putObject("function").put("name", name).put("arguments", rawArguments);
        root.putObject("usage").put("prompt_tokens", prompt).put("completion_tokens", completion)
                .put("total_tokens", prompt + completion);
        return json.writeValueAsString(root);
    }

    private String finalResponse(String text, int prompt, int completion) throws Exception {
        var root = json.createObjectNode();
        root.putArray("choices").addObject().put("finish_reason", "stop")
                .putObject("message").put("role", "assistant").put("content", text);
        root.putObject("usage").put("prompt_tokens", prompt).put("completion_tokens", completion)
                .put("total_tokens", prompt + completion);
        return json.writeValueAsString(root);
    }

    private static void assertPositiveFootprint(long tokens, long bytes) {
        assertTrue(tokens > 0);
        assertTrue(bytes > 0 && bytes <= 262_144);
        assertTrue(tokens * 3 >= bytes);
    }

    private static String body(HttpRequest request) {
        var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            public void onNext(ByteBuffer bytes) { subscriber.onNext(List.of(bytes)); }
            public void onError(Throwable error) { subscriber.onError(error); }
            public void onComplete() { subscriber.onComplete(); }
        });
        return subscriber.getBody().toCompletableFuture().join();
    }
}
