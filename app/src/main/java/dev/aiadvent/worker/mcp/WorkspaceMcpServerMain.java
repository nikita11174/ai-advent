package dev.aiadvent.worker.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.workspace.GitStatusReader;
import dev.aiadvent.worker.workspace.RepositoryResearch;
import dev.aiadvent.worker.workspace.monitor.MonitorStateStore;
import dev.aiadvent.worker.workspace.monitor.RepositoryMonitor;
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
import java.time.Clock;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class WorkspaceMcpServerMain {
    static final String TOOL_NAME = "git_repository_status";
    static final String START_MONITOR = "start_repository_monitor";
    static final String READ_MONITOR = "get_repository_monitor_summary";
    static final String STOP_MONITOR = "stop_repository_monitor";
    static final String SEARCH = "search_repository";
    static final String SUMMARIZE = "summarize_repository_search";
    static final String SAVE = "save_repository_report";
    private static final String REPOSITORY_ENV = "AI_ADVENT_WORKSPACE_REPOSITORY";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        RepositoryMonitor monitor = null;
        RepositoryResearch research = null;
        try {
            if ("true".equals(System.getenv("AI_ADVENT_MONITOR_ENABLED"))) {
                String stateDirectory = System.getenv("AI_ADVENT_MONITOR_STATE_DIRECTORY");
                if (stateDirectory == null || stateDirectory.isBlank()) throw new IllegalStateException("MONITOR_FAULTED");
                monitor = new RepositoryMonitor(new MonitorStateStore(Path.of(System.getenv(REPOSITORY_ENV)),
                        Path.of(stateDirectory)), reader, Clock.systemUTC(),
                        "true".equals(System.getenv("AI_ADVENT_MONITOR_DEMO")));
            }
            if ("true".equals(System.getenv("AI_ADVENT_RESEARCH_ENABLED"))) {
                String directory = System.getenv("AI_ADVENT_REPORTS_DIRECTORY");
                if (directory == null || directory.isBlank()) throw new IllegalStateException("INVALID_REPORTS_DIRECTORY");
                research = new RepositoryResearch(Path.of(System.getenv(REPOSITORY_ENV)), Path.of(directory));
            }
        } catch (RuntimeException e) {
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
        var serverBuilder = McpServer.sync(transport)
                .serverInfo("ai-advent-workspace", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(Duration.ofSeconds(5))
                .validateToolInputs(false)
                .toolCall(tool, (exchange, request) -> call(reader, request));
        if (monitor != null) {
            RepositoryMonitor owned = monitor;
            serverBuilder.toolCall(monitorTool(START_MONITOR,
                    startInputSchema("true".equals(System.getenv("AI_ADVENT_MONITOR_DEMO"))), mutationOutputSchema(), false),
                    (exchange, request) -> monitorCall(owned, request));
            serverBuilder.toolCall(monitorTool(READ_MONITOR, readInputSchema(), viewOutputSchema(), true),
                    (exchange, request) -> monitorCall(owned, request));
            serverBuilder.toolCall(monitorTool(STOP_MONITOR, stopInputSchema(), mutationOutputSchema(), false),
                    (exchange, request) -> monitorCall(owned, request));
        }
        if (research != null) {
            RepositoryResearch owned = research;
            serverBuilder.toolCall(researchTool(SEARCH, searchInputSchema(), searchOutputSchema(), true),
                    (exchange, request) -> researchCall(owned, request));
            serverBuilder.toolCall(researchTool(SUMMARIZE, summaryInputSchema(), summaryOutputSchema(), true),
                    (exchange, request) -> researchCall(owned, request));
            serverBuilder.toolCall(researchTool(SAVE, saveInputSchema(), saveOutputSchema(), false),
                    (exchange, request) -> researchCall(owned, request));
        }
        var server = serverBuilder.build();
        System.err.println("WORKSPACE_MCP_PID=" + ProcessHandle.current().pid());
        try {
            eof.await();
        }
        finally {
            try {
                if (monitor != null) monitor.close();
            } finally {
                server.close();
            }
        }
    }

    private static McpSchema.Tool monitorTool(String name, Map<String, Object> input,
                                              Map<String, Object> output, boolean readOnly) {
        return McpSchema.Tool.builder().name(name).description(readOnly
                ? "Read the persisted repository monitor summary" : "Change the configured local repository monitor")
                .inputSchema(input).outputSchema(output)
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(!readOnly).build())
                .build();
    }

    private static McpSchema.Tool researchTool(String name, Map<String, Object> input,
                                               Map<String, Object> output, boolean readOnly) {
        return McpSchema.Tool.builder().name(name).description("Fixed repository research pipeline step")
                .inputSchema(input).outputSchema(output)
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(false).build()).build();
    }

    static McpSchema.CallToolResult researchCall(RepositoryResearch research, McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> args = request.arguments();
            if (args == null) return error("INVALID_ARGUMENTS");
            Map<String, Object> value;
            switch (request.name()) {
                case SEARCH -> {
                    if (args.size() != 2 || !(args.get("query") instanceof String query)
                            || !(args.get("maxResults") instanceof Integer max)) return error("INVALID_ARGUMENTS");
                    value = research.search(query, max);
                }
                case SUMMARIZE -> {
                    if (args.size() != 1 || !args.containsKey("searchResult")) return error("INVALID_ARGUMENTS");
                    value = RepositoryResearch.summarize(JSON.valueToTree(args.get("searchResult")));
                }
                case SAVE -> {
                    if (args.size() != 2 || !(args.get("operationId") instanceof String id)
                            || !args.containsKey("summary")) return error("INVALID_ARGUMENTS");
                    value = research.save(id, JSON.valueToTree(args.get("summary")));
                }
                default -> { return error("UNKNOWN_TOOL"); }
            }
            if (JSON.writeValueAsBytes(value).length > 16_384) return error("RESULT_LIMIT");
            return McpSchema.CallToolResult.builder().structuredContent(value).build();
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            return error(code != null && RESEARCH_ERRORS.contains(code) ? code : "MCP_CALL_FAILED");
        } catch (RuntimeException | IOException e) { return error("MCP_CALL_FAILED"); }
    }

    private static final java.util.Set<String> RESEARCH_ERRORS = java.util.Set.of("INVALID_ARGUMENTS", "SEARCH_FAILED",
            "SEARCH_TIMEOUT", "SEARCH_INCOMPLETE", "INVALID_SEARCH_RESULT", "INVALID_SUMMARY_RESULT",
            "SAVE_FAILED", "SAVE_OUTCOME_UNKNOWN", "REPORT_CONFLICT", "RESULT_LIMIT");

    static Map<String, Object> searchInputSchema() {
        return object(Map.of("query", Map.of("type", "string", "minLength", 1, "maxLength", 100),
                "maxResults", Map.of("type", "integer", "minimum", 1, "maximum", 20)));
    }
    static Map<String, Object> searchOutputSchema() {
        return object(Map.of("query", Map.of("type", "string"), "observedAt", Map.of("type", "string"),
                "matchCount", Map.of("type", "integer"), "truncated", Map.of("type", "boolean"),
                "matches", Map.of("type", "array", "items", object(Map.of("relativePath", Map.of("type", "string"),
                        "line", Map.of("type", "integer"))))));
    }
    static Map<String, Object> summaryInputSchema() { return object(Map.of("searchResult", searchOutputSchema())); }
    static Map<String, Object> summaryOutputSchema() {
        return object(Map.of("query", Map.of("type", "string"), "matchesSeen", Map.of("type", "integer"),
                "filesMatched", Map.of("type", "integer"), "truncated", Map.of("type", "boolean"),
                "evidence", Map.of("type", "array", "items", object(Map.of("relativePath", Map.of("type", "string"),
                        "line", Map.of("type", "integer")))),
                "summaryMarkdown", Map.of("type", "string")));
    }
    static Map<String, Object> saveInputSchema() {
        return object(Map.of("operationId", Map.of("type", "string", "format", "uuid"),
                "summary", summaryOutputSchema()));
    }
    static Map<String, Object> saveOutputSchema() {
        return object(Map.of("reportRef", Map.of("type", "string"), "createdAt", Map.of("type", "string"),
                "bytesWritten", Map.of("type", "integer"), "contentHash", Map.of("type", "string")));
    }

    static McpSchema.CallToolResult monitorCall(RepositoryMonitor monitor, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        if (args == null) return error("INVALID_ARGUMENTS");
        try {
            Object value;
            switch (request.name()) {
                case READ_MONITOR -> {
                    if (!args.isEmpty()) return error("INVALID_ARGUMENTS");
                    value = monitor.read();
                }
                case START_MONITOR -> {
                    if (args.size() != 3 || !(args.get("intervalSeconds") instanceof Integer interval)
                            || !(args.get("expectedRevision") instanceof Integer || args.get("expectedRevision") instanceof Long)
                            || !(args.get("commandId") instanceof String id)
                            || interval < 0) {
                        return error("INVALID_ARGUMENTS");
                    }
                    value = monitor.start(interval, id, ((Number) args.get("expectedRevision")).longValue());
                }
                case STOP_MONITOR -> {
                    if (args.size() != 2 || !(args.get("expectedRevision") instanceof Integer || args.get("expectedRevision") instanceof Long)
                            || !(args.get("commandId") instanceof String id)
                            ) return error("INVALID_ARGUMENTS");
                    value = monitor.stop(id, ((Number) args.get("expectedRevision")).longValue());
                }
                default -> { return error("UNKNOWN_TOOL"); }
            }
            Map<String, Object> result = JSON.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() { });
            var response = McpSchema.CallToolResult.builder().structuredContent(result).build();
            if (JSON.writeValueAsBytes(response).length > 16_384) return error("RESULT_LIMIT");
            return response;
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            return error(code != null && ERROR_CODES.contains(code) ? code : "MCP_CALL_FAILED");
        } catch (RuntimeException | IOException e) {
            return error("MCP_CALL_FAILED");
        }
    }

    private static final java.util.Set<String> ERROR_CODES = java.util.Set.of("INVALID_ARGUMENTS",
            "COMMAND_ID_CONFLICT", "CONFIG_REVISION_CONFLICT", "PERSISTENCE_FAILED", "MONITOR_DISABLED",
            "MONITOR_FAULTED", "RESULT_LIMIT");

    static Map<String, Object> startInputSchema() { return startInputSchema(false); }

    static Map<String, Object> startInputSchema(boolean demo) {
        return object(Map.of("intervalSeconds", Map.of("type", "integer", "minimum", demo ? 10 : 60, "maximum", 86400),
                "commandId", Map.of("type", "string", "format", "uuid"),
                "expectedRevision", Map.of("type", "integer", "minimum", 0)));
    }

    static Map<String, Object> stopInputSchema() {
        return object(Map.of("commandId", Map.of("type", "string", "format", "uuid"),
                "expectedRevision", Map.of("type", "integer", "minimum", 0)));
    }

    static Map<String, Object> readInputSchema() { return object(Map.of()); }

    static Map<String, Object> viewOutputSchema() {
        Map<String, Object> counts = Map.of("type", "integer", "minimum", 0);
        Map<String, Object> date = Map.of("type", List.of("string", "null"), "format", "date-time");
        Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
        Map<String, Object> status = new LinkedHashMap<>(outputSchema());
        status.put("type", List.of("object", "null"));
        Map<String, Object> aggregate = object(Map.ofEntries(
                Map.entry("successCount", counts), Map.entry("failureCount", counts),
                Map.entry("dirtySampleCount", counts), Map.entry("headTransitionCount", counts),
                Map.entry("branchTransitionCount", counts), Map.entry("firstSuccessAt", date),
                Map.entry("lastSuccessAt", date), Map.entry("lastCompletedAt", date),
                Map.entry("lastOutcome", nullableString), Map.entry("lastFailureCode", nullableString),
                Map.entry("latestStatus", status)));
        Map<String, Object> digest = nullable(object(Map.of("snapshotRevision", counts,
                "generatedAt", Map.of("type", "string", "format", "date-time"), "text", Map.of("type", "string"))));
        Map<String, Object> lastCommand = nullable(object(Map.of("commandId", Map.of("type", "string"),
                "action", Map.of("type", "string", "enum", List.of("START", "STOP")),
                "intervalSeconds", Map.of("type", List.of("integer", "null")),
                "operationStatus", Map.of("type", "string", "enum", List.of("APPLIED")),
                "configRevision", counts)));
        return object(Map.of("monitorId", Map.of("type", "string"), "repositoryRef", Map.of("type", "string"),
                "enabled", Map.of("type", "boolean"), "intervalSeconds", Map.of("type", List.of("integer", "null")),
                "configRevision", counts, "nextRunAt", date, "aggregate", aggregate,
                "latestDigest", digest, "lastCommand", lastCommand,
                "health", object(Map.of("status", Map.of("type", "string", "enum", List.of("IDLE", "WAITING", "RUNNING", "FAULTED")),
                        "code", nullableString))));
    }

    static Map<String, Object> mutationOutputSchema() {
        return object(Map.of("receipt", object(Map.of("commandId", Map.of("type", "string"),
                        "operationStatus", Map.of("type", "string", "enum", List.of("APPLIED", "ALREADY_APPLIED")),
                        "configRevision", Map.of("type", "integer", "minimum", 0), "enabled", Map.of("type", "boolean"))),
                "view", viewOutputSchema()));
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", properties.keySet().stream().sorted().toList(),
                "additionalProperties", false);
    }

    private static Map<String, Object> nullable(Map<String, Object> object) {
        var result = new LinkedHashMap<>(object);
        result.put("type", List.of("object", "null"));
        return result;
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

    static final class EofInputStream extends FilterInputStream {
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
