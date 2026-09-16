package dev.aiadvent.mentor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentModelCatalogTest {
    @Test
    void catalogContainsOnlySafeSelectionAndPresentationData() throws Exception {
        var catalog = new AgentModelCatalog(new DeepSeekAgentModelExecutor(mock(DeepSeekTransport.class)),
                new OpenAiAgentModelExecutor(mock(OpenAiResponsesClient.class)));
        assertEquals(AgentModelCatalog.DEFAULT_KEY, catalog.resolve(null).option().key());
        assertEquals(AgentModelCatalog.DEFAULT_MODEL, catalog.resolve(AgentModelCatalog.DEFAULT_KEY).option().label());
        assertEquals(List.of("DEEPSEEK", "WEAK", "MEDIUM", "STRONG"), catalog.options().stream().map(AgentModelCatalog.Option::key).toList());
        assertThrows(IllegalArgumentException.class, () -> catalog.resolve(""));
        var node = new ObjectMapper().valueToTree(catalog.options());
        assertEquals(3, node.get(0).size());
        MockMvcBuilders.standaloneSetup(new AgentModelController(catalog)).build()
                .perform(get("/api/agent-model-options"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"key":"DEEPSEEK","provider":"DEEPSEEK","label":"deepseek-v4-flash"},
                         {"key":"WEAK","provider":"OPENAI","label":"GPT-5.6 Luna"},
                         {"key":"MEDIUM","provider":"OPENAI","label":"GPT-5.6 Terra"},
                         {"key":"STRONG","provider":"OPENAI","label":"GPT-5.6 Sol"}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
