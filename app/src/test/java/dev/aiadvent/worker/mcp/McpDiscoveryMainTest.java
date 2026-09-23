package dev.aiadvent.worker.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class McpDiscoveryMainTest {
    @Test
    void acceptsTrustedLoopbackEndpointFromCliOrEnvironment() {
        assertEquals("http://127.0.0.1:64687/stream",
                McpDiscoveryMain.endpoint(new String[]{"--endpoint", "http://127.0.0.1:64687/stream"}, Map.of()).toString());
        assertEquals("http://[::1]:64687/stream",
                McpDiscoveryMain.endpoint(new String[0], Map.of("AI_ADVENT_MCP_ENDPOINT", "http://[::1]:64687/stream")).toString());
    }

    @Test
    void rejectsNonLoopbackAndAmbiguousEndpoints() {
        for (String endpoint : List.of(
                "http://example.com:64687/stream",
                "https://127.0.0.1:64687/stream",
                "http://localhost:64687/stream",
                "http://127.0.0.1/stream",
                "http://user@127.0.0.1:64687/stream",
                "http://127.0.0.1:64687/stream?x=1",
                "http://127.0.0.1:64687/other")) {
            assertThrows(IllegalArgumentException.class,
                    () -> McpDiscoveryMain.endpoint(new String[]{"--endpoint", endpoint}, Map.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> McpDiscoveryMain.endpoint(new String[0], Map.of()));
    }

    @Test
    void discoversPagesAndNormalizesDescriptorsWithoutExecutingTools() throws Exception {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(tool("one", null)), "next"));
        when(client.listTools("next")).thenReturn(new McpSchema.ListToolsResult(List.of(tool("two", "second")), null));

        List<String> result = McpDiscoveryMain.discover(client, deadline());

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("\"name\":\"one\""));
        assertTrue(result.get(0).contains("\"description\":\"\""));
        assertTrue(result.get(1).contains("\"inputSchema\":{\"type\":\"object\"}"));
        verify(client).listTools((String) null);
        verify(client).listTools("next");
        verifyNoMoreInteractions(client);
    }

    @Test
    void emptyListIsValidButMalformedListFails() throws Exception {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(), null));
        assertTrue(McpDiscoveryMain.discover(client, deadline()).isEmpty());

        when(client.listTools((String) null)).thenReturn(null);
        assertEquals("MALFORMED_LIST", assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage());
    }

    @Test
    void rejectsRepeatedCursorAndPageLimit() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(), "a"));
        when(client.listTools("a")).thenReturn(new McpSchema.ListToolsResult(List.of(), "a"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage().startsWith("PAGINATION_LIMIT"));

        when(client.listTools("a")).thenReturn(new McpSchema.ListToolsResult(List.of(), "b"));
        when(client.listTools("b")).thenReturn(new McpSchema.ListToolsResult(List.of(), "c"));
        when(client.listTools("c")).thenReturn(new McpSchema.ListToolsResult(List.of(), "d"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage().startsWith("PAGINATION_LIMIT"));
    }

    @Test
    void rejectsToolAndDescriptorLimits() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(
                Collections.nCopies(129, tool("one", "")), null));
        assertEquals("TOOL_LIMIT", assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage());

        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(
                List.of(tool("large", "x".repeat(13_000))), null));
        assertEquals("DESCRIPTOR_LIMIT", assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage());

        List<McpSchema.Tool> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(tool("tool-" + i, "x".repeat(9_000)));
        }
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(many, null));
        assertEquals("OUTPUT_LIMIT", assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage());
    }

    @Test
    void rejectsMalformedToolAndExpiredDeadline() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(
                List.of(McpSchema.Tool.builder("bad", Map.of("type", "string")).build()), null));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage().startsWith("MALFORMED_LIST"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, System.nanoTime() - 1)).getMessage().startsWith("TIMEOUT"));
    }

    @Test
    void reportsListFailure() {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.listTools((String) null)).thenThrow(new RuntimeException("server disconnected"));
        assertEquals("LIST_FAILED", assertThrows(IllegalStateException.class,
                () -> McpDiscoveryMain.discover(client, deadline())).getMessage());
    }

    private static McpSchema.Tool tool(String name, String description) {
        return McpSchema.Tool.builder(name, Map.of("type", "object")).description(description).build();
    }

    private static long deadline() {
        return System.nanoTime() + Duration.ofSeconds(5).toNanos();
    }
}
