package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.AgentModelRequest;
import dev.aiadvent.mentor.model.DeepSeekException;
import dev.aiadvent.mentor.model.DeepSeekTransport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeepSeekClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<String> response = mock(HttpResponse.class);
    private final DeepSeekTransport transport = spy(new DeepSeekTransport(http, json, "test-key"));
    private final DeepSeekClient client = new DeepSeekClient(transport, json);

    @Test
    void freeReasoningAndTemperatureReviewsSendTheirExactPromptsAndOptions() throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\" raw answer \"}}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        assertEquals(" raw answer ", client.analyze(" exact input\n"));
        assertEquals(" raw answer ", client.analyzeWithSystem("reasoning instruction", " exact input\n"));
        for (double temperature : new double[]{0.0, 0.7, 1.2}) {
            assertEquals(" raw answer ", client.analyzeAtTemperature("fixed instruction", " exact input\n", temperature));
        }

        ArgumentCaptor<String> requests = ArgumentCaptor.forClass(String.class);
        verify(transport, times(5)).send(requests.capture());
        var free = json.readTree(requests.getAllValues().get(0));
        assertEquals(4, free.size());
        assertEquals("deepseek-v4-flash", free.path("model").asText());
        assertEquals(2, free.path("messages").size());
        assertEquals("system", free.path("messages").get(0).path("role").asText());
        assertEquals("""
                You are an engineering review mentor.
                Analyze the provided code or engineering question.
                Explain potential engineering risks clearly and concisely.
                Respond in Russian.
                Format the response using Markdown when it improves readability.""",
                free.path("messages").get(0).path("content").asText());
        assertFalse(free.path("stream").asBoolean());
        assertEquals("disabled", free.path("thinking").path("type").asText());
        for (int i = 0; i < requests.getAllValues().size(); i++) {
            var body = json.readTree(requests.getAllValues().get(i));
            assertEquals(" exact input\n", body.path("messages").get(1).path("content").asText());
            assertEquals("user", body.path("messages").get(1).path("role").asText());
            if (i == 1) {
                assertEquals(4, body.size());
                assertEquals("reasoning instruction", body.path("messages").get(0).path("content").asText());
            } else if (i >= 2) {
                assertEquals(5, body.size());
                assertEquals("fixed instruction", body.path("messages").get(0).path("content").asText());
                assertEquals(new double[]{0.0, 0.7, 1.2}[i - 2], body.path("temperature").asDouble());
            }
        }
        verify(http, times(5)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoMoreInteractions(http);
    }

    @Test
    void controlledReviewParsesTheResponseAndPreservesExactRawContent() throws Exception {
        String raw = " {\"summary\":\"Рисков нет\",\"findings\":[],\"recommendation\":\"Продолжить проверку\"}\n";
        when(response.statusCode()).thenReturn(200);
        var envelope = json.createObjectNode();
        envelope.putArray("choices").addObject().put("finish_reason", "stop")
                .putObject("message").put("content", raw);
        when(response.body()).thenReturn(envelope.toString());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var controls = new ReviewControls(450, 2, 12, 14, 16, "Finish after JSON.");

        var result = client.analyzeControlled("exact input", controls);

        assertEquals(raw, result.rawResponse());
        assertEquals(new ControlledReview("Рисков нет", java.util.List.of(), "Продолжить проверку"), result.review());
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(transport).send(sent.capture());
        var body = json.readTree(sent.getValue());
        assertEquals(6, body.size());
        assertEquals(450, body.path("max_tokens").asInt());
        assertEquals("json_object", body.path("response_format").path("type").asText());
        assertEquals("deepseek-v4-flash", body.path("model").asText());
        assertEquals("disabled", body.path("thinking").path("type").asText());
        assertFalse(body.path("stream").asBoolean());
        assertEquals(2, body.path("messages").size());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("user", body.path("messages").get(1).path("role").asText());
        assertEquals("exact input", body.path("messages").get(1).path("content").asText());
        String prompt = body.path("messages").get(0).path("content").asText();
        assertTrue(prompt.contains("at most 12 whitespace-delimited words"));
        assertTrue(prompt.contains("Findings: 0 to 2"));
        assertTrue(prompt.contains("at most 14 whitespace-delimited words"));
        assertTrue(prompt.contains("at most 16 whitespace-delimited words"));
        assertTrue(prompt.contains("Finish after JSON."));
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verifyNoMoreInteractions(http);
    }

    @Test
    void controlledTransportResponsesRetainFailureMessagesAndRawContent() throws Exception {
        var transport = mock(DeepSeekTransport.class);
        var client = new DeepSeekClient(transport, json);
        when(transport.buildRequestBody(any(AgentModelRequest.class), eq(true))).thenReturn("request");
        when(transport.send("request")).thenReturn(new DeepSeekTransport.Completion("{partial", "length", null),
                new DeepSeekTransport.Completion("not-json", "stop", null));

        var length = assertThrows(DeepSeekException.class,
                () -> client.analyzeControlled("input", ReviewControls.defaults()));
        assertEquals("DeepSeek stopped the controlled response with finish_reason=length.", length.getMessage());
        assertEquals("{partial", length.rawResponse());
        var malformed = assertThrows(DeepSeekException.class,
                () -> client.analyzeControlled("input", ReviewControls.defaults()));
        assertEquals("DeepSeek returned malformed controlled JSON.", malformed.getMessage());
        assertEquals("not-json", malformed.rawResponse());
        verify(transport, times(2)).send("request");
    }

    @Test
    void rejectsEmptyInputsInAllReviewMethodsBeforeHttp() {
        for (String input : new String[]{null, "", " \n"}) {
            assertEquals("Input must not be empty.",
                    assertThrows(DeepSeekException.class, () -> client.analyze(input)).getMessage());
            assertEquals("Input must not be empty.",
                    assertThrows(DeepSeekException.class, () -> client.analyzeWithSystem("prompt", input)).getMessage());
            assertEquals("Input must not be empty.",
                    assertThrows(DeepSeekException.class, () -> client.analyzeAtTemperature("prompt", input, 0.7)).getMessage());
            assertEquals("Input must not be empty.",
                    assertThrows(DeepSeekException.class, () -> client.analyzeControlled(input, ReviewControls.defaults())).getMessage());
        }
        verifyNoInteractions(http);
    }

    @Test
    void preservesReviewSpecificSerializationErrorMapping() throws Exception {
        var transport = mock(DeepSeekTransport.class);
        var cause = new JsonProcessingException("serialization failed") {};
        when(transport.buildRequestBody(any(AgentModelRequest.class))).thenThrow(cause);
        when(transport.buildRequestBody(any(AgentModelRequest.class), eq(true))).thenThrow(cause);
        var client = new DeepSeekClient(transport, json);
        var free = assertThrows(DeepSeekException.class, () -> client.analyze("input"));
        var temperature = assertThrows(DeepSeekException.class, () -> client.analyzeAtTemperature("prompt", "input", 0.7));
        var controlled = assertThrows(DeepSeekException.class, () -> client.analyzeControlled("input", ReviewControls.defaults()));
        assertEquals("Could not create the DeepSeek request.", free.getMessage());
        assertEquals("Could not create the temperature DeepSeek request.", temperature.getMessage());
        assertEquals("Could not create the controlled DeepSeek request.", controlled.getMessage());
        assertSame(cause, free.getCause());
        assertSame(cause, temperature.getCause());
        assertSame(cause, controlled.getCause());
        verify(transport, never()).send(anyString());
    }
}
