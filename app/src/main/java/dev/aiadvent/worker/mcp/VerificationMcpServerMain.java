package dev.aiadvent.worker.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class VerificationMcpServerMain {
    static final String FIND = "find_allowed_tests";
    static final String RUN = "run_allowed_test";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws InterruptedException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
        if (args.length != 0) throw new IllegalArgumentException("No arguments accepted");
        String configured = System.getenv("AI_ADVENT_VERIFICATION_ROOT");
        if (configured == null || configured.isBlank()) throw new IllegalStateException("VERIFICATION_ROOT_REQUIRED");
        var verification = new AllowedVerification(Path.of(configured));
        var mapper = new JacksonMcpJsonMapper(JSON);
        CountDownLatch eof = new CountDownLatch(1);
        var transport = new StdioServerTransportProvider(mapper,
                new WorkspaceMcpServerMain.EofInputStream(System.in, eof), System.out, 65536);
        var server = McpServer.sync(transport).serverInfo("ai-advent-verification", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(Duration.ofSeconds(70)).validateToolInputs(false)
                .toolCall(tool(FIND, findInput(), findOutput(), true), (exchange, request) -> call(verification, request))
                .toolCall(tool(RUN, runInput(), runOutput(), false), (exchange, request) -> call(verification, request))
                .build();
        System.err.println("VERIFICATION_MCP_PID=" + ProcessHandle.current().pid());
        try { eof.await(); }
        finally { server.close(); }
    }

    static McpSchema.CallToolResult call(AllowedVerification verification, McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            if (args == null || args.size() != 1) return error("INVALID_ARGUMENTS");
            Map<String, Object> result = switch (request.name()) {
                case FIND -> args.get("concept") instanceof String concept ? verification.find(concept) : null;
                case RUN -> args.get("testId") instanceof String id ? verification.run(id) : null;
                default -> null;
            };
            if (result == null) return error("INVALID_ARGUMENTS");
            return McpSchema.CallToolResult.builder().structuredContent(result).build();
        } catch (IllegalArgumentException e) { return error("INVALID_ARGUMENTS"); }
        catch (RuntimeException e) { return error("VERIFICATION_FAILED"); }
    }

    private static McpSchema.CallToolResult error(String code) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(code).build();
    }

    private static McpSchema.Tool tool(String name, Map<String, Object> input, Map<String, Object> output, boolean readOnly) {
        return McpSchema.Tool.builder().name(name).description(name.equals(FIND)
                ? "Find pre-registered backend checks relevant to a concept"
                : "Execute one pre-registered local backend check by testId")
                .inputSchema(input).outputSchema(output)
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(false).build()).build();
    }

    private static Map<String, Object> object(String key, Map<String, Object> value) {
        return Map.of("type", "object", "properties", Map.of(key, value), "required", List.of(key), "additionalProperties", false);
    }
    static Map<String, Object> findInput() { return object("concept", Map.of("type", "string", "minLength", 1, "maxLength", 120)); }
    static Map<String, Object> runInput() { return object("testId", Map.of("type", "string", "enum", List.of(AllowedVerification.TEST_ID))); }
    static Map<String, Object> findOutput() { return object("checks", Map.of("type", "array")); }
    static Map<String, Object> runOutput() { return Map.of("type", "object"); }
}
