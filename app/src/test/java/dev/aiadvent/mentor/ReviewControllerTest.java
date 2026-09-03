package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeepSeekClient deepSeekClient;

    @Test
    void returnsAnalysis() throws Exception {
        when(deepSeekClient.analyze("code")).thenReturn("analysis");

        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("{\"input\":\"code\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"analysis\":\"analysis\"}"));
    }

    @Test
    void returnsAnalysisForExplicitFreeMode() throws Exception {
        when(deepSeekClient.analyze("code")).thenReturn("analysis");

        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("{\"input\":\"code\",\"mode\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"analysis\":\"analysis\"}"));
    }

    @Test
    void returnsStructuredControlledResponseAndRawJson() throws Exception {
        ReviewControls controls = ReviewControls.defaults();
        ControlledReview review = new ControlledReview("Резюме", java.util.List.of(), "Рекомендация");
        String raw = "{\"summary\":\"Резюме\",\"findings\":[],\"recommendation\":\"Рекомендация\"}";
        when(deepSeekClient.analyzeControlled("code", controls)).thenReturn(new ControlledAnalysis(review, raw));

        mockMvc.perform(post("/api/review")
                .contentType("application/json")
                        .content("{\"input\":\"code\",\"mode\":\"CONTROLLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.summary").value("Резюме"))
                .andExpect(jsonPath("$.review.findings").isEmpty())
                .andExpect(jsonPath("$.review.recommendation").value("Рекомендация"))
                .andExpect(jsonPath("$.rawResponse").value(raw));
    }

    @Test
    void propagatesCustomControls() throws Exception {
        ReviewControls controls = new ReviewControls(450, 1, 12, 13, 14, "Finish JSON.");
        ControlledReview review = new ControlledReview("Резюме", java.util.List.of(), "Рекомендация");
        when(deepSeekClient.analyzeControlled("code", controls)).thenReturn(new ControlledAnalysis(review, "{}"));

        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("""
                                {"input":"code","mode":"CONTROLLED","controls":{"maxTokens":450,"maxFindings":1,
                                "summaryMaxWords":12,"reasonMaxWords":13,"recommendationMaxWords":14,
                                "terminationInstruction":"Finish JSON."}}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidControlBoundsWithoutCallingDeepSeek() throws Exception {
        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("""
                                {"input":"code","mode":"CONTROLLED","controls":{"maxTokens":99,"maxFindings":3,
                                "summaryMaxWords":30,"reasonMaxWords":30,"recommendationMaxWords":30,
                                "terminationInstruction":"Finish JSON."}}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(deepSeekClient);
    }

    @Test
    void exposesRawContentForControlledResponseFailure() throws Exception {
        when(deepSeekClient.analyzeControlled("code", ReviewControls.defaults()))
                .thenThrow(new DeepSeekException("Invalid controlled response.", "{partial"));

        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("{\"input\":\"code\",\"mode\":\"CONTROLLED\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"Invalid controlled response.\",\"rawResponse\":\"{partial\"}"));
    }

    @Test
    void rejectsEmptyInput() throws Exception {
        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("{\"input\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Input must not be empty.\"}"));
    }

    @Test
    void reportsDeepSeekFailure() throws Exception {
        when(deepSeekClient.analyze("code")).thenThrow(new DeepSeekException("DeepSeek unavailable."));

        mockMvc.perform(post("/api/review")
                        .contentType("application/json")
                        .content("{\"input\":\"code\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"DeepSeek unavailable.\"}"));
    }
}
