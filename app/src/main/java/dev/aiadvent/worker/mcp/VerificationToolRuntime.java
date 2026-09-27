package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public final class VerificationToolRuntime implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final McpSyncClient client;
    private final OwnedStdioClientTransport transport;
    private final Process child;

    public VerificationToolRuntime(@Value("${mentor.verification-mcp.enabled:false}") boolean enabled,
                                   @Value("${mentor.git-status-tool.repository:}") String repository,
                                   @Value("${server.address:}") String address) {
        if (!enabled) { client = null; transport = null; child = null; return; }
        if (!Set.of("127.0.0.1", "::1").contains(address) || repository.isBlank())
            throw new IllegalStateException("VERIFICATION_LOCAL_BIND_REQUIRED");
        Path root;
        try { root = Path.of(repository).toRealPath(); }
        catch (Exception e) { throw new IllegalStateException("VERIFICATION_START_FAILED"); }
        if (!java.nio.file.Files.isRegularFile(root.resolve("pom.xml")))
            throw new IllegalStateException("VERIFICATION_START_FAILED");
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var builder = new ProcessBuilder(WorkspaceToolRuntime.childCommand(java,
                System.getProperty("java.class.path"), VerificationMcpServerMain.class));
        Map<String, String> inherited = new HashMap<>(builder.environment());
        builder.environment().clear();
        for (var entry : inherited.entrySet()) {
            if (Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                    .contains(entry.getKey().toUpperCase(Locale.ROOT)))
                builder.environment().put(entry.getKey(), entry.getValue());
        }
        builder.environment().put("AI_ADVENT_VERIFICATION_ROOT", root.toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        OwnedStdioClientTransport owned = OwnedStdioClientTransport.start(builder::start,
                new JacksonMcpJsonMapper(JSON), Duration.ofSeconds(10));
        McpSyncClient started = null;
        try {
            started = McpClient.sync(owned).initializationTimeout(Duration.ofSeconds(10))
                    .requestTimeout(Duration.ofSeconds(75)).build();
            started.initialize();
            var listed = started.listTools();
            if (listed == null || listed.nextCursor() != null || listed.tools() == null
                    || listed.tools().size() != 2 || !matches(listed.tools(), VerificationMcpServerMain.FIND,
                    VerificationMcpServerMain.findInput(), VerificationMcpServerMain.findOutput())
                    || !matches(listed.tools(), VerificationMcpServerMain.RUN,
                    VerificationMcpServerMain.runInput(), VerificationMcpServerMain.runOutput()))
                throw new IllegalStateException("TOOL_UNAVAILABLE");
        } catch (RuntimeException | Error e) {
            if (started != null) try { started.close(); } catch (RuntimeException ignored) { }
            owned.closeStreams();
            WorkspaceToolRuntime.awaitTermination(owned.process(), Duration.ofSeconds(5));
            owned.closeRemainingStreams();
            throw new IllegalStateException("VERIFICATION_DISCOVERY_FAILED", e);
        }
        client = started; transport = owned; child = owned.process();
    }

    private static boolean matches(List<McpSchema.Tool> tools, String name, Map<String, Object> input,
                                   Map<String, Object> output) {
        return tools.stream().filter(tool -> name.equals(tool.name())).count() == 1
                && tools.stream().filter(tool -> name.equals(tool.name())).allMatch(tool ->
                input.equals(tool.inputSchema()) && output.equals(tool.outputSchema()));
    }

    public boolean enabled() { return client != null && child.isAlive(); }

    public synchronized JsonNode call(String name, Map<String, Object> args) {
        if (!enabled()) throw new IllegalStateException("VERIFICATION_DISABLED");
        if (!Set.of(VerificationMcpServerMain.FIND, VerificationMcpServerMain.RUN).contains(name))
            throw new IllegalArgumentException("TOOL_NOT_ALLOWED");
        var response = client.callTool(new McpSchema.CallToolRequest(name, args));
        if (response == null || Boolean.TRUE.equals(response.isError()) || response.structuredContent() == null)
            throw new IllegalStateException("MCP_CALL_FAILED");
        JsonNode value = JSON.valueToTree(response.structuredContent());
        if (value.toString().getBytes(StandardCharsets.UTF_8).length > 4096 || !value.isObject())
            throw new IllegalStateException("INVALID_TOOL_RESULT");
        return value;
    }

    @Override public synchronized void close() {
        if (client == null) return;
        try { client.close(); } finally {
            transport.closeStreams();
            WorkspaceToolRuntime.awaitTermination(child, Duration.ofSeconds(12));
            transport.closeRemainingStreams();
        }
    }
}
