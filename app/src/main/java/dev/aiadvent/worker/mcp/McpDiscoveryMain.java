package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class McpDiscoveryMain {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_PAGES = 4;
    private static final int MAX_TOOLS = 128;
    private static final int MAX_DESCRIPTOR_BYTES = 12_288;
    private static final int MAX_OUTPUT_BYTES = 262_144;

    private McpDiscoveryMain() {
    }

    public static void main(String[] args) throws IOException {
        URI endpoint = endpoint(args, System.getenv());
        McpDiscovery.Result result = new McpDiscovery().discover(endpoint);
        System.out.println(JSON.writeValueAsString(new DiscoveryHeader(result.server(), result.serverVersion(),
                result.protocolVersion(), result.tools().size())));
        for (JsonNode tool : result.tools()) {
            System.out.println(JSON.writeValueAsString(tool));
        }
    }

    public static URI endpoint(String[] args, Map<String, String> environment) {
        String value;
        if (args.length == 0) {
            value = environment.get("AI_ADVENT_MCP_ENDPOINT");
        }
        else if (args.length == 2 && "--endpoint".equals(args[0])) {
            value = args[1];
        }
        else {
            throw new IllegalArgumentException("Expected --endpoint http://127.0.0.1:<port>/stream or AI_ADVENT_MCP_ENDPOINT");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP endpoint is required");
        }
        URI uri;
        try {
            uri = URI.create(value);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid MCP endpoint", e);
        }
        if (!"http".equals(uri.getScheme())
                || !("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()))
                || uri.getPort() < 1 || uri.getPort() > 65_535
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !"/stream".equals(uri.getRawPath())) {
            throw new IllegalArgumentException("MCP endpoint must be loopback HTTP /stream with an explicit port");
        }
        return uri;
    }

    static List<String> discover(McpSyncClient client, long deadline) throws IOException {
        List<String> descriptors = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        int outputBytes = 0;
        for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
            checkDeadline(deadline);
            McpSchema.ListToolsResult page;
            try {
                page = client.listTools(cursor);
            }
            catch (RuntimeException e) {
                throw new IllegalStateException("LIST_FAILED", e);
            }
            checkDeadline(deadline);
            if (page == null || page.tools() == null) {
                throw new IllegalStateException("MALFORMED_LIST");
            }
            if (descriptors.size() + page.tools().size() > MAX_TOOLS) {
                throw new IllegalStateException("TOOL_LIMIT");
            }
            for (McpSchema.Tool tool : page.tools()) {
                String descriptor = normalize(tool);
                outputBytes += descriptor.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
                if (outputBytes > MAX_OUTPUT_BYTES) {
                    throw new IllegalStateException("OUTPUT_LIMIT");
                }
                descriptors.add(descriptor);
            }
            cursor = page.nextCursor();
            if (cursor == null) {
                return List.copyOf(descriptors);
            }
            if (!seenCursors.add(cursor)) {
                throw new IllegalStateException("PAGINATION_LIMIT: repeated cursor");
            }
        }
        throw new IllegalStateException("PAGINATION_LIMIT: too many pages");
    }

    private static String normalize(McpSchema.Tool tool) throws IOException {
        if (tool == null || tool.name() == null || tool.name().isBlank()
                || tool.inputSchema() == null || !"object".equals(tool.inputSchema().get("type"))) {
            throw new IllegalStateException("MALFORMED_LIST: invalid tool descriptor");
        }
        JsonNode schema = JSON.valueToTree(tool.inputSchema());
        String normalized = JSON.writeValueAsString(new ToolDescriptor(
                tool.name(), tool.description() == null ? "" : tool.description(), schema));
        if (normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DESCRIPTOR_BYTES) {
            throw new IllegalStateException("DESCRIPTOR_LIMIT");
        }
        return normalized;
    }

    static void checkDeadline(long deadline) {
        if (System.nanoTime() - deadline >= 0) {
            throw new IllegalStateException("TIMEOUT: discovery deadline exceeded");
        }
    }

    private record ToolDescriptor(String name, String description, JsonNode inputSchema) {
    }

    private record DiscoveryHeader(String server, String serverVersion, String protocolVersion, int toolCount) {
    }
}
