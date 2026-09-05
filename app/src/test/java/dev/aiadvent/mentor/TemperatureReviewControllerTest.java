package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemperatureReviewController.class)
@Import(ReviewController.ApiExceptionHandler.class)
class TemperatureReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeepSeekClient client;

    @Test
    void propagatesEveryApprovedTemperatureWithExactInput() throws Exception {
        for (double temperature : new double[]{0.0, 0.7, 1.2}) {
            when(client.analyzeAtTemperature(contains("confirmed issues"),
                    org.mockito.ArgumentMatchers.eq("exact input"),
                    org.mockito.ArgumentMatchers.eq(temperature))).thenReturn("analysis");

            mockMvc.perform(post("/api/temperature-review")
                            .contentType("application/json")
                            .content("{\"input\":\"exact input\",\"temperature\":" + temperature + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.temperature").value(temperature))
                    .andExpect(jsonPath("$.analysis").value("analysis"));

            verify(client).analyzeAtTemperature(contains("confirmed issues"),
                    org.mockito.ArgumentMatchers.eq("exact input"),
                    org.mockito.ArgumentMatchers.eq(temperature));
        }
    }

    @Test
    void rejectsUnsupportedTemperatureWithoutDeepSeekCall() throws Exception {
        mockMvc.perform(post("/api/temperature-review")
                        .contentType("application/json")
                        .content("{\"input\":\"task\",\"temperature\":2.0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(client);
    }
}
