package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
