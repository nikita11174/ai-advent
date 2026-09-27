package dev.aiadvent.worker.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.workspace.GitStatusReader;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class WorkspaceMcpServerMain {
    static final String TOOL_NAME = "git_repository_status";
    private static final String REPOSITORY_ENV = "AI_ADVENT_WORKSPACE_REPOSITORY";
    private static final ObjectMapper JSON = new ObjectMapper();

    private WorkspaceMcpServerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
        GitStatusReader reader;
        try {
            if (args.length != 0) {
                throw new IllegalArgumentException("No command-line arguments are accepted");
            }
            String configuredRepository = System.getenv(REPOSITORY_ENV);
            if (configuredRepository == null || configuredRepository.isBlank()) {
                throw new IllegalArgumentException(REPOSITORY_ENV + " is required");
            }
            reader = new GitStatusReader(Path.of(configuredRepository));
        }
        catch (RuntimeException e) {
            System.err.println("WORKSPACE_MCP_STARTUP_FAILED");
            System.exit(2);
            return;
        }
        CountDownLatch eof = new CountDownLatch(1);
        var mapper = new JacksonMcpJsonMapper(JSON);
        var transport = new StdioServerTransportProvider(mapper, new EofInputStream(System.in, eof), System.out, 65_536);
        var tool = McpSchema.Tool.builder()
                .name(TOOL_NAME)
                .description("Read the configured workspace Git HEAD and complete working-tree status")
                .inputSchema(inputSchema())
                .outputSchema(outputSchema())
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).build())
                .build();
        var server = McpServer.sync(transport)
                .serverInfo("ai-advent-workspace", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(Duration.ofSeconds(5))
                .validateToolInputs(false)
                .toolCall(tool, (exchange, request) -> call(reader, request))
                .build();
        System.err.println("WORKSPACE_MCP_PID=" + ProcessHandle.current().pid());
        try {
            eof.await();
        }
        finally {
            server.close();
        }
    }

    static McpSchema.CallToolResult call(GitStatusReader reader, McpSchema.CallToolRequest request) {
        if (!TOOL_NAME.equals(request.name())) {
            return error("UNKNOWN_TOOL");
        }
        Map<String, Object> arguments = request.arguments();
        if (arguments == null || arguments.size() != 1 || !(arguments.get("includeChangeCounts") instanceof Boolean includeCounts)) {
            return error("INVALID_ARGUMENTS");
        }
        try {
            var status = reader.read(includeCounts);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("repositoryRef", status.repositoryRef());
            result.put("observedAt", status.observedAt().toString());
            result.put("headState", status.headState().name());
            result.put("branch", status.branch());
            result.put("head", status.head());
            result.put("dirty", status.dirty());
            result.put("changeCounts", status.changeCounts() == null ? null : Map.of(
                    "staged", status.changeCounts().staged(),
                    "unstaged", status.changeCounts().unstaged(),
                    "untracked", status.changeCounts().untracked(),
                    "conflicted", status.changeCounts().conflicted(),
                    "submoduleChanged", status.changeCounts().submoduleChanged()));
            String serialized = JSON.writeValueAsString(result);
            if (serialized.getBytes(StandardCharsets.UTF_8).length > 4_096) {
                return error("RESULT_LIMIT");
            }
            return McpSchema.CallToolResult.builder()
                    .structuredContent(result)
                    .addTextContent(serialized)
                    .build();
        }
        catch (GitStatusReader.StatusException e) {
            return error(e.getMessage());
        }
        catch (IOException e) {
            return error("RESULT_SERIALIZATION_FAILED");
        }
    }

    private static McpSchema.CallToolResult error(String code) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(code).build();
    }

    static Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of("includeChangeCounts", Map.of("type", "boolean")),
                "required", java.util.List.of("includeChangeCounts"), "additionalProperties", false);
    }

    static Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "repositoryRef", Map.of("type", "string"),
                "observedAt", Map.of("type", "string", "format", "date-time"),
                "headState", Map.of("type", "string", "enum", java.util.List.of("ATTACHED", "DETACHED", "UNBORN")),
                "branch", Map.of("type", java.util.List.of("string", "null")),
                "head", Map.of("type", java.util.List.of("string", "null")),
                "dirty", Map.of("type", "boolean"),
                "changeCounts", Map.of("type", java.util.List.of("object", "null"), "properties", Map.of(
                        "staged", Map.of("type", "integer"), "unstaged", Map.of("type", "integer"),
                        "untracked", Map.of("type", "integer"), "conflicted", Map.of("type", "integer"),
                        "submoduleChanged", Map.of("type", "integer")), "required", java.util.List.of(
                        "staged", "unstaged", "untracked", "conflicted", "submoduleChanged"),
                        "additionalProperties", false)),
                "required", java.util.List.of("repositoryRef", "observedAt", "headState", "branch", "head", "dirty", "changeCounts"),
                "additionalProperties", false);
    }

    private static final class EofInputStream extends FilterInputStream {
        private final CountDownLatch eof;

        EofInputStream(InputStream input, CountDownLatch eof) {
            super(input);
            this.eof = eof;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) eof.countDown();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int value = super.read(bytes, offset, length);
            if (value == -1) eof.countDown();
            return value;
        }
    }
}
