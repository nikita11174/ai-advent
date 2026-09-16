package dev.aiadvent.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
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

class DeepSeekTransportTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final DeepSeekTransport transport = new DeepSeekTransport(http, json, "test-key");
    private final AgentModelRequest request = new AgentModelRequest(List.of(
            new AgentModelMessage("system", "instruction"),
            new AgentModelMessage("user", " first\n"),
            new AgentModelMessage("assistant", "ответ"),
            new AgentModelMessage("user", "next")), "deepseek-v4-flash", 0.7, 450);

    @Test
    void sendsTheExactEndpointAndOrderedPayloadAndReturnsProviderMetadata() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"choices":[{"finish_reason":"length","message":{"content":" raw answer\\n"}}],
                 "usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}}
                """);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var completion = transport.complete(request);

        assertEquals(" raw answer\n", completion.content());
        assertEquals("length", completion.finishReason());
        assertEquals(new ProviderUsage(100L, 20L, 120L), completion.usage());
        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(URI.create("https://api.deepseek.com/chat/completions"), sent.getValue().uri());
        assertEquals("POST", sent.getValue().method());
        assertEquals("Bearer test-key", sent.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", sent.getValue().headers().firstValue("Content-Type").orElseThrow());
        var body = json.readTree(body(sent.getValue()));
        assertEquals(6, body.size());
        assertEquals(json.valueToTree(request.messages()), body.path("messages"));
        assertEquals(request.model(), body.path("model").asText());
        assertFalse(body.path("stream").asBoolean());
        assertEquals("disabled", body.path("thinking").path("type").asText());
        assertEquals(0.7, body.path("temperature").asDouble());
        assertEquals(450, body.path("max_tokens").asInt());
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 401, 500})
    void mapsHttpFailuresWithoutRetryOrRawBody(int status) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var error = assertThrows(DeepSeekException.class, () -> transport.complete(request));

        assertEquals("DeepSeek API returned HTTP " + status + ".", error.getMessage());
        assertNull(error.rawResponse());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(response, never()).body();
        verifyNoMoreInteractions(http);
    }

    @Test
    void mapsIoFailureWithoutRetry() throws Exception {
        var cause = new IOException("offline");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(cause);
        var error = assertThrows(DeepSeekException.class, () -> transport.complete(request));
        assertEquals("DeepSeek request failed due to a transport problem.", error.getMessage());
        assertSame(cause, error.getCause());
        assertNull(error.rawResponse());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoMoreInteractions(http);
    }

    @Test
    void preservesTheInterruptFlagAndMapsInterruptedRequest() throws Exception {
        var cause = new InterruptedException("interrupted");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(cause);
        try {
            var error = assertThrows(DeepSeekException.class, () -> transport.complete(request));
            assertEquals("DeepSeek request was interrupted.", error.getMessage());
            assertSame(cause, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(error.rawResponse());
            verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            verifyNoMoreInteractions(http);
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"choices\":[]}", "{\"choices\":[{\"message\":{}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
            "{\"choices\":[{\"message\":{\"content\":\" \"}}]}",
            "{\"choices\":[{\"message\":{\"content\":42}}]}"})
    void rejectsMissingOrEmptyContent(String body) throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var error = assertThrows(DeepSeekException.class, () -> transport.complete(request));
        assertEquals("DeepSeek returned an unexpected response without analysis text.", error.getMessage());
        assertNull(error.rawResponse());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void mapsMalformedJsonAndDefaultsMissingFinishReasonAndUsage() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not json");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var error = assertThrows(DeepSeekException.class, () -> transport.complete(request));
        assertEquals("DeepSeek returned a malformed response.", error.getMessage());
        assertInstanceOf(JsonProcessingException.class, error.getCause());
        assertNull(error.rawResponse());
        assertEquals(new DeepSeekTransport.Completion("answer", "stop", null),
                transport.extractCompletion("{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"));
    }

    @Test
    void executorPreservesTransportFailureAndRawResponse() throws Exception {
        var transport = mock(DeepSeekTransport.class);
        var cause = new DeepSeekException("provider failed", "raw content");
        when(transport.complete(request)).thenThrow(cause);
        var error = assertThrows(ModelExecutionException.class,
                () -> new DeepSeekAgentModelExecutor(transport).complete(request));
        assertEquals(cause.getMessage(), error.getMessage());
        assertEquals("raw content", error.rawResponse());
        assertSame(cause, error.getCause());
        verify(transport).complete(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "PASTE_KEY_HERE"})
    void rejectsMissingKeysBeforeHttp(String key) {
        var error = assertThrows(DeepSeekException.class,
                () -> new DeepSeekTransport(http, json, key).complete(request));
        assertEquals("DEEPSEEK_API_KEY is missing or still contains the placeholder.", error.getMessage());
        verifyNoInteractions(http);
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
