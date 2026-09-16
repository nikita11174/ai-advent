package dev.aiadvent.mentor;

import dev.aiadvent.mentor.model.AgentModelExecutor;
import dev.aiadvent.mentor.model.AgentModelRequest;
import dev.aiadvent.mentor.model.ModelExecutionException;
import dev.aiadvent.mentor.model.ProviderUsage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StickyFactsServiceTest {
    @Test
    void parsesFactsAndKeepsProviderUsageSeparate() throws Exception {
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        var usage = new ProviderUsage(20L, 7L, 27L);
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(new AgentModelExecutor.Completion("{\"facts\":{\"project\":\"Helios\"}}", usage));
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
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        when(client.complete(any(AgentModelRequest.class)))
                .thenReturn(new AgentModelExecutor.Completion("null", null));
        var service = new StickyFactsService(client, new ApproximateTokenEstimator(),
                new ObjectMapper().findAndRegisterModules());

        assertThrows(ModelExecutionException.class, () -> service.update(StickyFacts.empty(), "input",
                AgentConfig.defaults(), 1));
    }

    @Test
    void rejectsAnOverLimitExtractionPromptBeforeCallingTheProvider() {
        AgentModelExecutor client = mock(AgentModelExecutor.class);
        var service = new StickyFactsService(client, new ApproximateTokenEstimator(),
                new ObjectMapper().findAndRegisterModules());

        assertThrows(EngineeringReviewAgent.ContextLimitExceededException.class,
                () -> service.update(StickyFacts.empty(), "input", new AgentConfig("model", "system", null, null, 1), 1));

        verifyNoInteractions(client);
    }
}
