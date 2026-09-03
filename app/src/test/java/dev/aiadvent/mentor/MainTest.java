package dev.aiadvent.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void buildsControlledRequestFromActualControls() throws Exception {
        ReviewControls controls = new ReviewControls(450, 2, 12, 14, 16, "Finish after JSON.");

        JsonNode request = JSON.readTree(client.buildControlledRequestBody("code", controls));

        assertEquals(450, request.path("max_tokens").intValue());
        assertEquals("json_object", request.path("response_format").path("type").textValue());
        assertTrue(request.path("stop").isMissingNode());
        String prompt = request.path("messages").path(0).path("content").textValue();
        assertTrue(prompt.contains("at most 12 whitespace-delimited words"));
        assertTrue(prompt.contains("Findings: 0 to 2"));
        assertTrue(prompt.contains("at most 14 whitespace-delimited words"));
        assertTrue(prompt.contains("at most 16 whitespace-delimited words"));
        assertTrue(prompt.contains("Finish after JSON."));
    }

    @Test
    void parsesValidControlledResponseIncludingEmptyFindings() throws Exception {
        String raw = "{\"summary\":\"Рисков нет\",\"findings\":[],\"recommendation\":\"Продолжить проверку\"}";

        ControlledReview review = client.parseControlledReview(raw, ReviewControls.defaults());

        assertEquals("Рисков нет", review.summary());
        assertEquals(0, review.findings().size());
        assertEquals("Продолжить проверку", review.recommendation());
    }

    @Test
    void acceptsEmptyFindingsWhenMaximumIsZero() throws Exception {
        ReviewControls controls = new ReviewControls(600, 0, 30, 30, 30,
                ReviewControls.DEFAULT_TERMINATION_INSTRUCTION);
        String raw = "{\"summary\":\"Результат\",\"findings\":[],\"recommendation\":\"Проверить тесты\"}";

        assertEquals(0, client.parseControlledReview(raw, controls).findings().size());
    }

    @Test
    void rejectsInvalidSeverity() {
        assertControlledRejected("""
                {"summary":"Результат","findings":[{"severity":"CRITICAL","title":"Риск","reason":"Причина"}],"recommendation":"Исправить"}
                """, ReviewControls.defaults());
    }

    @Test
    void rejectsTooManyFindings() {
        ReviewControls controls = new ReviewControls(600, 0, 30, 30, 30,
                ReviewControls.DEFAULT_TERMINATION_INSTRUCTION);
        assertControlledRejected("""
                {"summary":"Результат","findings":[{"severity":"LOW","title":"Риск","reason":"Причина"}],"recommendation":"Исправить"}
                """, controls);
    }

    @Test
    void rejectsSummaryWordOverflow() {
        assertWordOverflow("summary", "один два три", 2);
    }

    @Test
    void rejectsReasonWordOverflow() {
        assertWordOverflow("reason", "один два три", 2);
    }

    @Test
    void rejectsRecommendationWordOverflow() {
        assertWordOverflow("recommendation", "один два три", 2);
    }

    @Test
    void rejectsMalformedControlledJsonAndPreservesRawContent() {
        String raw = "not-json";

        DeepSeekException error = assertThrows(DeepSeekException.class,
                () -> client.parseControlledReview(raw, ReviewControls.defaults()));

        assertEquals(raw, error.rawResponse());
    }

    @Test
    void extractsFinishReasonForControlledValidation() throws Exception {
        DeepSeekClient.Completion completion = client.extractCompletion("""
                {"choices":[{"finish_reason":"length","message":{"content":"{partial"}}]}
                """);

        assertEquals("length", completion.finishReason());
        assertEquals("{partial", completion.content());
    }

    @Test
    void rejectsControlledResponseFinishedByLengthAndPreservesContent() {
        DeepSeekClient.Completion completion = new DeepSeekClient.Completion("{partial", "length");

        DeepSeekException error = assertThrows(DeepSeekException.class,
                () -> client.validateControlledCompletion(completion, ReviewControls.defaults()));

        assertEquals("{partial", error.rawResponse());
    }

    @Test
    void rejectsEmptyControlledContent() {
        String response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"  \"}}]}";

        assertThrows(DeepSeekException.class, () -> client.extractCompletion(response));
    }

    private void assertWordOverflow(String field, String value, int maximum) {
        ReviewControls controls = new ReviewControls(600, 3,
                field.equals("summary") ? maximum : 30,
                field.equals("reason") ? maximum : 30,
                field.equals("recommendation") ? maximum : 30,
                ReviewControls.DEFAULT_TERMINATION_INSTRUCTION);
        String summary = field.equals("summary") ? value : "Результат";
        String reason = field.equals("reason") ? value : "Причина";
        String recommendation = field.equals("recommendation") ? value : "Исправить";
        assertControlledRejected("""
                {"summary":"%s","findings":[{"severity":"LOW","title":"Риск","reason":"%s"}],"recommendation":"%s"}
                """.formatted(summary, reason, recommendation), controls);
    }

    private void assertControlledRejected(String raw, ReviewControls controls) {
        assertThrows(DeepSeekException.class, () -> client.parseControlledReview(raw, controls));
    }
}
