package dev.aiadvent.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DeepSeekClient client = new DeepSeekClient(null, JSON, "test-key");

    @Test
    void buildsApprovedDayOneRequest() throws Exception {
        String input = "class Example {\n    int value;\n}";

        JsonNode request = JSON.readTree(client.buildRequestBody(input));

        assertEquals("deepseek-v4-flash", request.path("model").textValue());
        assertEquals(false, request.path("stream").booleanValue());
        assertEquals("disabled", request.path("thinking").path("type").textValue());
        assertEquals("system", request.path("messages").path(0).path("role").textValue());
        assertEquals("""
                        You are an engineering review mentor.
                        Analyze the provided code or engineering question.
                        Explain potential engineering risks clearly and concisely.
                        Respond in Russian.
                        Format the response using Markdown when it improves readability.""",
                request.path("messages").path(0).path("content").textValue());
        assertEquals("user", request.path("messages").path(1).path("role").textValue());
        assertEquals(input, request.path("messages").path(1).path("content").textValue());
    }

    @Test
    void extractsAnalysisContent() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":\"Check empty input.\"}}]}";

        assertEquals("Check empty input.", client.extractContent(response));
    }

    @Test
    void rejectsResponseWithoutAnalysisContent() {
        String response = "{\"choices\":[]}";

        assertThrows(DeepSeekException.class, () -> client.extractContent(response));
    }

    @Test
    void rejectsMalformedResponse() {
        assertThrows(JsonProcessingException.class, () -> client.extractContent("not json"));
    }
}
