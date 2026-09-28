package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import dev.aiadvent.worker.mcp.WorkspaceToolRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryResearchControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void requestCannotSupplyPathOrFractionalResultLimit() throws Exception {
        var pipeline = mock(RepositoryResearchPipeline.class);
        var controller = new RepositoryResearchController(pipeline, mock(WorkspaceToolRuntime.class));
        assertThrows(IllegalArgumentException.class, () -> controller.run(JSON.readTree(
                "{\"query\":\"marker\",\"maxResults\":2,\"path\":\"../escape.md\"}")));
        assertThrows(IllegalArgumentException.class, () -> controller.run(JSON.readTree(
                "{\"query\":\"marker\",\"maxResults\":1.5}")));
        verifyNoInteractions(pipeline);
    }

    @Test void reportReadUsesOnlyConfiguredRuntimeAndMapsInvalidRef() {
        var pipeline = mock(RepositoryResearchPipeline.class);
        var runtime = mock(WorkspaceToolRuntime.class);
        var controller = new RepositoryResearchController(pipeline, runtime);
        String ref = "12345678-1234-1234-1234-123456789abc";
        when(runtime.readReport(ref)).thenReturn("# Repository search\n");
        assertEquals("# Repository search\n", controller.readReport(ref).content());
        when(runtime.readReport("../secret")).thenThrow(new IllegalStateException("INVALID_REPORT_REF"));
        assertEquals(400, controller.reportFailure(new IllegalStateException("INVALID_REPORT_REF")).getStatusCode().value());
        verifyNoInteractions(pipeline);
    }
}
