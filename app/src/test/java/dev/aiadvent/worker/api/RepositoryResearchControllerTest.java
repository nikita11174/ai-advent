package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryResearchControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void requestCannotSupplyPathOrFractionalResultLimit() throws Exception {
        var pipeline = mock(RepositoryResearchPipeline.class);
        var controller = new RepositoryResearchController(pipeline);
        assertThrows(IllegalArgumentException.class, () -> controller.run(JSON.readTree(
                "{\"query\":\"marker\",\"maxResults\":2,\"path\":\"../escape.md\"}")));
        assertThrows(IllegalArgumentException.class, () -> controller.run(JSON.readTree(
                "{\"query\":\"marker\",\"maxResults\":1.5}")));
        verifyNoInteractions(pipeline);
    }
}
