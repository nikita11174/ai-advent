package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.mcp.McpDiscovery;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class McpDiscoveryControllerTest {
    private final McpDiscovery discovery = mock(McpDiscovery.class);
    private final ObjectMapper json = new ObjectMapper();
    private final URI endpoint = URI.create("http://127.0.0.1:64687/stream");

    @Test
    void configuredConnectionNeedsExplicitPostAndUsesSharedDiscoveryOperation() throws Exception {
        MockMvc mvc = mvc(endpoint.toString());
        mvc.perform(get("/api/mcp/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("idea"))
                .andExpect(jsonPath("$[0].configured").value(true));
        verifyNoInteractions(discovery);

        when(discovery.discover(endpoint)).thenReturn(new McpDiscovery.Result("IntelliJ IDEA", "2026.2.2",
                "2025-11-25", Instant.parse("2026-09-28T10:00:00Z"),
                List.of(json.readTree("{\"name\":\"build_project\",\"description\":\"Build\",\"inputSchema\":{\"type\":\"object\"}}"))));
        mvc.perform(post("/api/mcp/connections/idea/discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("IntelliJ IDEA"))
                .andExpect(jsonPath("$.protocolVersion").value("2025-11-25"))
                .andExpect(jsonPath("$.toolCount").value(1))
                .andExpect(jsonPath("$.tools[0].name").value("build_project"));
        verify(discovery).discover(endpoint);
    }

    @Test
    void rejectsUnknownAndUnsafeConnectionsBeforeNetworkUse() throws Exception {
        mvc(endpoint.toString()).perform(post("/api/mcp/connections/other/discovery"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CONNECTION"));
        mvc("http://example.com:64687/stream").perform(post("/api/mcp/connections/idea/discovery"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIGURATION"));
        mvc("").perform(post("/api/mcp/connections/idea/discovery"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NOT_CONFIGURED"));
        verifyNoInteractions(discovery);
    }

    @Test
    void exposesOnlyBoundedFailureCodes() throws Exception {
        doThrow(new IllegalStateException("TOOL_LIMIT")).when(discovery).discover(endpoint);
        mvc(endpoint.toString()).perform(post("/api/mcp/connections/idea/discovery"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TOOL_LIMIT"));

        doThrow(new IllegalStateException("PAGINATION_LIMIT: repeated cursor")).when(discovery).discover(endpoint);
        mvc(endpoint.toString()).perform(post("/api/mcp/connections/idea/discovery"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAGINATION_LIMIT"));
    }

    private MockMvc mvc(String configuredEndpoint) {
        return standaloneSetup(new McpDiscoveryController(discovery, configuredEndpoint)).build();
    }
}
