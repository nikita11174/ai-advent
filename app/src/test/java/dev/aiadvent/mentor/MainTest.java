package dev.aiadvent.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DeepSeekClient client = new DeepSeekClient(null, JSON, "test-key");

    @Test
    void buildsOrderedAgentRequestsWithOnlyConfiguredOptions() throws Exception {
        var transport = new DeepSeekTransport(null, JSON, "test-key");
        var messages = List.of(new AgentModelMessage("system", "instruction"),
                new AgentModelMessage("user", "first"),
                new AgentModelMessage("assistant", "answer"),
                new AgentModelMessage("user", "follow-up"));
        JsonNode defaults = JSON.readTree(transport.buildRequestBody(
                new AgentModelRequest(messages, "deepseek-v4-flash", null, null)));
        assertEquals(JSON.valueToTree(messages), defaults.path("messages"));
        assertEquals("deepseek-v4-flash", defaults.path("model").textValue());
        assertEquals(false, defaults.path("stream").booleanValue());
        assertEquals("disabled", defaults.path("thinking").path("type").textValue());
        assertEquals(4, defaults.size());
        JsonNode configured = JSON.readTree(transport.buildRequestBody(
                new AgentModelRequest(messages, "another-model", 0.7, 450)));
        assertEquals(JSON.valueToTree(messages), configured.path("messages"));
        assertEquals("another-model", configured.path("model").textValue());
        assertEquals(0.7, configured.path("temperature").doubleValue());
        assertEquals(450, configured.path("max_tokens").intValue());
        assertEquals(6, configured.size());
    }

    @Test
    void emptyProviderContentDoesNotEnterAgentHistory() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\" \"}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        var transport = spy(new DeepSeekTransport(http, JSON, "test-key"));
        var histories = mock(AgentHistoryStore.class);
        var agent = new EngineeringReviewAgent(UUID.randomUUID(), AgentConfig.defaults(),
                new ConversationContext(AgentConfig.defaults().systemPrompt()),
                new DeepSeekAgentModelExecutor(transport), histories,
                new ApproximateTokenEstimator(), mock(AgentSummaryStore.class),
                mock(ConversationSummaryService.class), mock(StickyFactsStore.class), mock(StickyFactsService.class),
                new FullContextPolicy(), new SummaryRecentContextPolicy(), new SlidingWindowContextPolicy(),
                new StickyFactsContextPolicy(), mock(AgentBranchStore.class), null);

        assertThrows(ModelExecutionException.class, () -> agent.reply("failed"));
        assertEquals("answer", agent.reply("next").analysis());

        verify(transport).complete(new AgentModelRequest(List.of(
                new AgentModelMessage("system", AgentConfig.defaults().systemPrompt()),
                new AgentModelMessage("user", "next")), "deepseek-v4-flash", null, null));
        verify(histories).save(any(), anyList());
        verify(http, times(2)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

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
        assertNull(client.extractCompletion(response).usage());
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
    void buildsTemperatureRequestWithOnlyTemperatureAsSamplingControl() throws Exception {
        String input = "exact input";
        String systemPrompt = "fixed system";

        for (double temperature : new double[]{0.0, 0.7, 1.2}) {
            JsonNode request = JSON.readTree(client.buildTemperatureRequestBody(systemPrompt, input, temperature));

            assertEquals(temperature, request.path("temperature").doubleValue());
            assertEquals("deepseek-v4-flash", request.path("model").textValue());
            assertEquals("disabled", request.path("thinking").path("type").textValue());
            assertEquals(false, request.path("stream").booleanValue());
            assertEquals(systemPrompt, request.path("messages").path(0).path("content").textValue());
            assertEquals(input, request.path("messages").path(1).path("content").textValue());
            assertTrue(request.path("top_p").isMissingNode());
            assertTrue(request.path("frequency_penalty").isMissingNode());
            assertTrue(request.path("presence_penalty").isMissingNode());
        }
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
    void extractsOptionalDeepSeekUsageWithoutTreatingItAsALocalEstimate() throws Exception {
        DeepSeekClient.Completion completion = client.extractCompletion("""
                {"choices":[{"finish_reason":"stop","message":{"content":"answer"}}],
                 "usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}}
                """);

        assertEquals(new ProviderUsage(100L, 20L, 120L), completion.usage());
    }

    @Test
    void rejectsControlledResponseFinishedByLengthAndPreservesContent() {
        DeepSeekClient.Completion completion = new DeepSeekClient.Completion("{partial", "length", null);

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
