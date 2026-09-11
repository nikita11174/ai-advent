package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StickyFactsServiceTest {
    @Test
    void parsesFactsAndKeepsProviderUsageSeparate() throws Exception {
        DeepSeekClient client = mock(DeepSeekClient.class);
        var usage = new ProviderUsage(20L, 7L, 27L);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(new DeepSeekClient.Completion("{\"facts\":{\"project\":\"Helios\"}}", "stop", usage));
        var service = new StickyFactsService(client, new ApproximateTokenEstimator(),
                new ObjectMapper().findAndRegisterModules());

        StickyFactsGeneration generation = service.update(StickyFacts.empty(), "project is Helios",
                AgentConfig.defaults(), 1);

        assertEquals(new StickyFacts(1, java.util.Map.of("project", "Helios")), generation.facts());
        assertEquals(usage, generation.metrics().providerUsage());
        assertTrue(generation.metrics().contextTokens() > generation.metrics().currentRequestTokens());
    }

    @Test
    void rejectsMalformedFactsResponse() throws Exception {
        DeepSeekClient client = mock(DeepSeekClient.class);
        when(client.complete(anyList(), anyString(), isNull(), isNull()))
                .thenReturn(new DeepSeekClient.Completion("null", "stop", null));
        var service = new StickyFactsService(client, new ApproximateTokenEstimator(),
                new ObjectMapper().findAndRegisterModules());

        assertThrows(DeepSeekException.class, () -> service.update(StickyFacts.empty(), "input",
                AgentConfig.defaults(), 1));
    }
}
