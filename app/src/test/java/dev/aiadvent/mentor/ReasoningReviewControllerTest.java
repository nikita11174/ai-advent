package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReasoningReviewController.class)
@Import(ReviewController.ApiExceptionHandler.class)
class ReasoningReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReasoningReviewService service;

    @Test
    void returnsStrategyResult() throws Exception {
        when(service.analyze("task", ReasoningStrategy.SELF_PROMPT))
                .thenReturn(new ReasoningReviewService.ReasoningResult(
                        ReasoningStrategy.SELF_PROMPT, "analysis", "generated prompt"));

        mockMvc.perform(post("/api/reasoning-review")
                        .contentType("application/json")
                        .content("{\"input\":\"task\",\"strategy\":\"SELF_PROMPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("SELF_PROMPT"))
                .andExpect(jsonPath("$.analysis").value("analysis"))
                .andExpect(jsonPath("$.generatedPrompt").value("generated prompt"));
    }

    @Test
    void rejectsMissingStrategyWithoutCallingDeepSeek() throws Exception {
        mockMvc.perform(post("/api/reasoning-review")
                        .contentType("application/json")
                        .content("{\"input\":\"task\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
