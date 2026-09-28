package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.aiadvent.worker.agent.RepositoryEvidenceReader;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolDefinition;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public final class OrchestrationTools {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WorkspaceToolRuntime workspace;
    private final VerificationToolRuntime verification;
    private final RepositoryEvidenceReader evidenceReader;
    private final Map<String, Entry> entries;

    public OrchestrationTools(WorkspaceToolRuntime workspace, VerificationToolRuntime verification,
                              RepositoryEvidenceReader evidenceReader) {
        this.workspace = workspace; this.verification = verification; this.evidenceReader = evidenceReader;
        Map<String, Entry> registered = new LinkedHashMap<>();
        add(registered, "workspace", WorkspaceMcpServerMain.TOOL_NAME, "Read workspace Git status",
                WorkspaceMcpServerMain.inputSchema());
        add(registered, "workspace", WorkspaceMcpServerMain.SEARCH, "Search tracked repository files for a fixed literal term",
                Map.of("type", "object", "properties", Map.of(
                        "query", Map.of("type", "string", "minLength", 1, "maxLength", 100),
                        "maxResults", Map.of("type", "integer", "minimum", 1, "maximum", 8)),
                        "required", List.of("query", "maxResults"), "additionalProperties", false));
        add(registered, "verification", VerificationMcpServerMain.FIND,
                "Find allowlisted checks for a concept; call before selecting a testId",
                VerificationMcpServerMain.findInput());
        add(registered, "verification", VerificationMcpServerMain.RUN,
                "Run one allowlisted local backend check by testId; may write build outputs",
                VerificationMcpServerMain.runInput());
        entries = Map.copyOf(registered);
    }

    static void add(Map<String, Entry> entries, String server, String name, String description,
                    Map<String, Object> schema) {
        if (entries.putIfAbsent(name, new Entry(server,
                new ToolDefinition(name, description, JSON.valueToTree(schema)))) != null)
            throw new IllegalStateException("DUPLICATE_TOOL_NAME");
    }

    public boolean ready() { return workspace.orchestrationReady() && verification.enabled(); }
    public List<ToolDefinition> definitions() { return entries.values().stream().map(Entry::definition).toList(); }
    public String server(String name) { return require(name).server(); }

    public void validate(ToolRequest request) {
        require(request.name());
        JsonNode args = request.arguments();
        if (!args.isObject()) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
        switch (request.name()) {
            case WorkspaceMcpServerMain.TOOL_NAME -> {
                if (args.size() != 1 || !args.path("includeChangeCounts").isBoolean())
                    throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
            }
            case WorkspaceMcpServerMain.SEARCH -> {
                if (args.size() != 2) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
                text(args, "query", 100);
                if (!args.path("maxResults").isInt() || args.path("maxResults").intValue() < 1
                        || args.path("maxResults").intValue() > 8)
                    throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
            }
            case VerificationMcpServerMain.FIND -> {
                if (args.size() != 1) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
                text(args, "concept", 120);
            }
            case VerificationMcpServerMain.RUN -> {
                if (args.size() != 1 || !AllowedVerification.TEST_ID.equals(text(args, "testId", 64)))
                    throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
            }
            default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
        }
    }

    public ToolResult execute(ToolRequest request) {
        validate(request);
        JsonNode args = request.arguments();
        if (!args.isObject() || args.size() != (request.name().equals(WorkspaceMcpServerMain.SEARCH) ? 2 : 1))
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
        JsonNode result;
        switch (request.name()) {
            case WorkspaceMcpServerMain.TOOL_NAME -> {
                if (!args.path("includeChangeCounts").isBoolean()) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
                result = workspace.execute(request).structuredResult();
            }
            case WorkspaceMcpServerMain.SEARCH -> {
                String query = text(args, "query", 100);
                if (!args.path("maxResults").isInt() || args.path("maxResults").intValue() < 1
                        || args.path("maxResults").intValue() > 8) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
                result = workspace.orchestrationSearch(query);
                if (result.path("matchCount").intValue() > 0) {
                    var evidence = evidenceReader.readSearch(result);
                    ObjectNode projection = result.deepCopy();
                    projection.put("sourceEvidence", evidence.modelContext());
                    result = projection;
                }
            }
            case VerificationMcpServerMain.FIND -> {
                result = verification.call(request.name(), Map.of("concept", text(args, "concept", 120)));
                if (result.size() != 1 || !result.path("checks").isArray() || result.path("checks").size() > 3)
                    throw new IllegalStateException("INVALID_TOOL_RESULT");
                for (JsonNode check : result.path("checks")) {
                    if (!check.isObject() || check.size() != 5
                            || !AllowedVerification.TEST_ID.equals(check.path("testId").asText())
                            || !check.path("displayName").isTextual() || !check.path("purpose").isTextual()
                            || !check.path("anchor").isTextual() || !check.path("executionAllowed").asBoolean())
                        throw new IllegalStateException("INVALID_TOOL_RESULT");
                }
            }
            case VerificationMcpServerMain.RUN -> {
                String id = text(args, "testId", 64);
                if (!AllowedVerification.TEST_ID.equals(id)) throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
                result = verification.call(request.name(), Map.of("testId", id));
                if (result.size() != 10 || !Set.of("TEST_PASS", "TEST_FAIL", "TEST_NOT_RUN", "TIMEOUT", "INFRA_ERROR")
                        .contains(result.path("status").asText()) || !result.path("runId").isTextual()
                        || !result.path("head").asText().matches("[0-9a-f]{40}") || !result.path("dirty").isBoolean()
                        || !result.path("sourceHash").asText().matches("[0-9a-f]{64}")
                        || !"SUCCESS".equals(result.path("toolExecutionStatus").asText())
                        || !AllowedVerification.TEST_ID.equals(result.path("testId").asText())
                        || !result.path("tests").canConvertToInt()
                        || !result.path("checkedBehavior").isTextual() || !result.path("code").isTextual()
                        || ("TEST_PASS".equals(result.path("status").asText()) && result.path("tests").intValue() < 1))
                    throw new IllegalStateException("INVALID_TOOL_RESULT");
            }
            default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
        }
        if (!result.isObject() || result.toString().getBytes(StandardCharsets.UTF_8).length > 16_384)
            throw new IllegalStateException("INVALID_TOOL_RESULT");
        return new ToolResult(request.name(), result);
    }

    private static String text(JsonNode args, String key, int max) {
        JsonNode value = args.path(key);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max)
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
        return value.textValue();
    }

    private Entry require(String name) {
        Entry entry = entries.get(name);
        if (entry == null) throw new IllegalArgumentException("UNKNOWN_TOOL");
        return entry;
    }

    record Entry(String server, ToolDefinition definition) { }
}
