package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.agent.ToolExecutor;
import dev.aiadvent.worker.agent.MonitorReadExecutor;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public final class WorkspaceToolRuntime implements ToolExecutor, MonitorReadExecutor, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration CHILD_START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CHILD_EXIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CHILD_STOP_TIMEOUT = Duration.ofSeconds(1);
    private enum State { OPEN, CLOSING, CLOSED }
    private final McpSyncClient client;
    private final OwnedStdioClientTransport transport;
    private final Process child;
    private final boolean gitEnabled;
    private final boolean monitorEnabled;
    private final boolean demoIntervals;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final CompletableFuture<Void> closeOutcome = new CompletableFuture<>();

    @Autowired
    public WorkspaceToolRuntime(@Value("${mentor.git-status-tool.enabled:false}") boolean enabled,
                                @Value("${mentor.git-status-tool.repository:}") String repository,
                                @Value("${mentor.repository-monitor.enabled:false}") boolean monitorEnabled,
                                @Value("${mentor.repository-monitor.state-directory:}") String stateDirectory,
                                @Value("${mentor.repository-monitor.demo-intervals:false}") boolean demoIntervals,
                                @Value("${server.address:}") String serverAddress) {
        this(enabled, repository, monitorEnabled, stateDirectory, demoIntervals, serverAddress, CHILD_START_TIMEOUT, null);
    }

    public WorkspaceToolRuntime(boolean enabled, String repository) {
        this(enabled, repository, CHILD_START_TIMEOUT, null);
    }

    WorkspaceToolRuntime(boolean enabled, String repository, Duration startTimeout,
                         OwnedStdioClientTransport.ProcessStarter processStarter) {
        this(enabled, repository, false, "", false, "", startTimeout, processStarter);
    }

    WorkspaceToolRuntime(boolean enabled, String repository, boolean monitorEnabled, String stateDirectory,
                         boolean demoIntervals, String serverAddress, Duration startTimeout,
                         OwnedStdioClientTransport.ProcessStarter processStarter) {
        this.gitEnabled = enabled;
        this.monitorEnabled = monitorEnabled;
        this.demoIntervals = demoIntervals;
        if (monitorEnabled && !Set.of("127.0.0.1", "::1").contains(serverAddress)) {
            throw new IllegalStateException("MONITOR_LOCAL_BIND_REQUIRED");
        }
        if (!enabled && !monitorEnabled) {
            client = null;
            transport = null;
            child = null;
            closeOutcome.complete(null);
            return;
        }
        if (repository.isBlank()) throw new IllegalStateException("MCP_START_FAILED");
        Path root;
        try {
            root = Path.of(repository).toRealPath();
        } catch (Exception e) {
            throw new IllegalStateException("MCP_START_FAILED");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        String classPath = System.getProperty("java.class.path");
        var command = new ArrayList<String>();
        command.add(java);
        if (classPath.endsWith(".jar")) {
            command.addAll(List.of("-Dloader.main=" + WorkspaceMcpServerMain.class.getName(),
                    "-cp", classPath, "org.springframework.boot.loader.launch.PropertiesLauncher"));
        } else {
            command.addAll(List.of("-cp", classPath, WorkspaceMcpServerMain.class.getName()));
        }
        var builder = new ProcessBuilder(command);
        Map<String, String> inherited = new HashMap<>(builder.environment());
        builder.environment().clear();
        for (var entry : inherited.entrySet()) {
            if (Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                    .contains(entry.getKey().toUpperCase(Locale.ROOT))) {
                builder.environment().put(entry.getKey(), entry.getValue());
            }
        }
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", root.toString());
        if (monitorEnabled) {
            String directory = stateDirectory.isBlank() ? System.getenv("LOCALAPPDATA") : stateDirectory;
            if (directory == null || directory.isBlank()) throw new IllegalStateException("MCP_START_FAILED");
            if (stateDirectory.isBlank()) directory = Path.of(directory, "LocalAIWorker", "repository-monitor").toString();
            builder.environment().put("AI_ADVENT_MONITOR_ENABLED", "true");
            builder.environment().put("AI_ADVENT_MONITOR_STATE_DIRECTORY", directory);
            builder.environment().put("AI_ADVENT_MONITOR_DEMO", Boolean.toString(demoIntervals));
        }

        OwnedStdioClientTransport owned = OwnedStdioClientTransport.start(
                processStarter == null ? builder::start : processStarter,
                new JacksonMcpJsonMapper(JSON), startTimeout);
        McpSyncClient started = null;
        try {
            started = McpClient.sync(owned)
                    .initializationTimeout(Duration.ofSeconds(10)).requestTimeout(Duration.ofSeconds(5)).build();
            started.initialize();
            var listed = started.listTools();
            if (listed == null || listed.nextCursor() != null || listed.tools() == null
                    || listed.tools().size() != (monitorEnabled ? 4 : 1)
                    || !matches(listed.tools(), WorkspaceMcpServerMain.TOOL_NAME,
                    WorkspaceMcpServerMain.inputSchema(), WorkspaceMcpServerMain.outputSchema())
                    || (monitorEnabled && (!matches(listed.tools(), WorkspaceMcpServerMain.START_MONITOR,
                    WorkspaceMcpServerMain.startInputSchema(demoIntervals), WorkspaceMcpServerMain.mutationOutputSchema())
                    || !matches(listed.tools(), WorkspaceMcpServerMain.READ_MONITOR,
                    WorkspaceMcpServerMain.readInputSchema(), WorkspaceMcpServerMain.viewOutputSchema())
                    || !matches(listed.tools(), WorkspaceMcpServerMain.STOP_MONITOR,
                    WorkspaceMcpServerMain.stopInputSchema(), WorkspaceMcpServerMain.mutationOutputSchema())))) {
                throw new IllegalStateException("TOOL_UNAVAILABLE");
            }
        } catch (RuntimeException | Error e) {
            try {
                closeOwned(started, owned, owned.process());
            } catch (RuntimeException cleanupFailure) {
                cleanupFailure.addSuppressed(e);
                throw cleanupFailure;
            }
            throw new IllegalStateException("MCP_DISCOVERY_FAILED");
        }
        client = started;
        transport = owned;
        child = owned.process();
        state.set(State.OPEN);
    }

    WorkspaceToolRuntime(McpSyncClient client, Process child) {
        this.gitEnabled = true;
        this.monitorEnabled = false;
        this.demoIntervals = false;
        this.client = Objects.requireNonNull(client);
        this.child = Objects.requireNonNull(child);
        this.transport = null;
        state.set(State.OPEN);
    }

    ProcessHandle ownedChildProcess() {
        return child == null ? null : child.toHandle();
    }

    @Override public boolean enabled() { return gitEnabled && client != null && state.get() == State.OPEN; }

    @Override public boolean monitorEnabled() { return monitorEnabled && client != null && state.get() == State.OPEN; }

    @Override public synchronized ToolResult execute(ToolRequest request) {
        if (state.get() != State.OPEN) throw new IllegalStateException("MCP_RUNTIME_CLOSED");
        if (!gitEnabled) throw new IllegalStateException("TOOL_DISABLED");
        if (client == null) throw new IllegalStateException("TOOL_DISABLED");
        if (!WorkspaceMcpServerMain.TOOL_NAME.equals(request.name())) throw new IllegalArgumentException("TOOL_NOT_ALLOWED");
        JsonNode args = request.arguments();
        if (!args.isObject() || args.size() != 1 || !args.path("includeChangeCounts").isBoolean()) {
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
        }
        try {
            var response = client.callTool(new McpSchema.CallToolRequest(request.name(),
                    Map.of("includeChangeCounts", args.path("includeChangeCounts").booleanValue())));
            if (response == null || Boolean.TRUE.equals(response.isError()) || response.structuredContent() == null) {
                throw new IllegalStateException("MCP_CALL_FAILED");
            }
            JsonNode result = JSON.valueToTree(response.structuredContent());
            if (result.toString().getBytes(StandardCharsets.UTF_8).length > 4096 || !validResult(result)) {
                throw new IllegalStateException("INVALID_TOOL_RESULT");
            }
            return new ToolResult(request.name(), result);
        } catch (RuntimeException e) {
            throw new IllegalStateException("MCP_CALL_FAILED", e);
        }
    }

    private static boolean matches(List<McpSchema.Tool> tools, String name, Map<String, Object> input,
                                   Map<String, Object> output) {
        return tools.stream().filter(tool -> name.equals(tool.name())).count() == 1
                && tools.stream().filter(tool -> name.equals(tool.name())).allMatch(tool ->
                input.equals(tool.inputSchema()) && output.equals(tool.outputSchema()));
    }

    @Override public synchronized ToolResult readMonitor(ToolRequest request) {
        if (!WorkspaceMcpServerMain.READ_MONITOR.equals(request.name())
                || !request.arguments().isObject() || request.arguments().size() != 0) {
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS");
        }
        return new ToolResult(request.name(), monitorCall(request.name(), Map.of()));
    }

    public synchronized JsonNode startMonitor(int intervalSeconds, String commandId, long expectedRevision) {
        return monitorCall(WorkspaceMcpServerMain.START_MONITOR,
                Map.of("intervalSeconds", intervalSeconds, "commandId", commandId, "expectedRevision", expectedRevision));
    }

    public synchronized JsonNode stopMonitor(String commandId, long expectedRevision) {
        return monitorCall(WorkspaceMcpServerMain.STOP_MONITOR,
                Map.of("commandId", commandId, "expectedRevision", expectedRevision));
    }

    public synchronized JsonNode getMonitor() {
        return monitorCall(WorkspaceMcpServerMain.READ_MONITOR, Map.of());
    }

    private JsonNode monitorCall(String name, Map<String, Object> arguments) {
        if (state.get() != State.OPEN) throw new IllegalStateException("MCP_RUNTIME_CLOSED");
        if (!monitorEnabled) throw new IllegalStateException("MONITOR_DISABLED");
        McpSchema.CallToolResult response;
        try {
            response = client.callTool(new McpSchema.CallToolRequest(name, arguments));
        } catch (RuntimeException e) {
            throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                    ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN");
        }
        if (response == null) throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN");
        JsonNode envelope;
        try { envelope = JSON.valueToTree(response); }
        catch (RuntimeException e) { throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN"); }
        if (envelope.toString().getBytes(StandardCharsets.UTF_8).length > 16_384) {
            throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                    ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN");
        }
        if (Boolean.TRUE.equals(response.isError())) {
            JsonNode error = envelope.path("content").path(0).path("text");
            String code = error.asText("MCP_CALL_FAILED");
            if (!Set.of("INVALID_ARGUMENTS", "COMMAND_ID_CONFLICT", "CONFIG_REVISION_CONFLICT",
                    "PERSISTENCE_FAILED", "MONITOR_DISABLED", "MONITOR_FAULTED", "RESULT_LIMIT").contains(code)) {
                code = "MCP_CALL_FAILED";
            }
            throw new IllegalStateException(code);
        }
        JsonNode result;
        try { result = JSON.valueToTree(response.structuredContent()); }
        catch (RuntimeException e) { throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN"); }
        if (!result.isObject() || result.toString().getBytes(StandardCharsets.UTF_8).length > 16_384
                || (name.equals(WorkspaceMcpServerMain.READ_MONITOR) && !validMonitorView(result))
                || (!name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                && (!validReceipt(result.path("receipt")) || !validMonitorView(result.path("view"))
                || result.size() != 2))) {
            throw new IllegalStateException(name.equals(WorkspaceMcpServerMain.READ_MONITOR)
                    ? "MCP_CALL_FAILED" : "MONITOR_UNKNOWN");
        }
        return result;
    }

    private static boolean validMonitorView(JsonNode view) {
        if (!exact(view, Set.of("monitorId", "repositoryRef", "enabled", "intervalSeconds", "configRevision",
                "nextRunAt", "aggregate", "latestDigest", "lastCommand", "health"))) return false;
        if (!"repository-monitor".equals(view.path("monitorId").asText())
                || !"workspace".equals(view.path("repositoryRef").asText()) || !view.path("enabled").isBoolean()
                || !nonnegative(view.path("configRevision"))
                || !(view.path("intervalSeconds").isNull() || nonnegative(view.path("intervalSeconds")))
                || !dateOrNull(view.path("nextRunAt"))) return false;
        JsonNode aggregate = view.path("aggregate");
        if (!exact(aggregate, Set.of("successCount", "failureCount", "dirtySampleCount", "headTransitionCount",
                "branchTransitionCount", "firstSuccessAt", "lastSuccessAt", "lastCompletedAt", "lastOutcome",
                "lastFailureCode", "latestStatus"))) return false;
        for (String key : Set.of("successCount", "failureCount", "dirtySampleCount", "headTransitionCount", "branchTransitionCount")) {
            if (!nonnegative(aggregate.path(key))) return false;
        }
        for (String key : Set.of("firstSuccessAt", "lastSuccessAt", "lastCompletedAt")) {
            if (!dateOrNull(aggregate.path(key))) return false;
        }
        if (!(aggregate.path("lastOutcome").isNull() || Set.of("SUCCESS", "FAILURE").contains(aggregate.path("lastOutcome").asText()))
                || !(aggregate.path("lastFailureCode").isNull() || aggregate.path("lastFailureCode").isTextual())
                || !(aggregate.path("latestStatus").isNull() || validResult(aggregate.path("latestStatus")))) return false;
        JsonNode digest = view.path("latestDigest");
        if (!(digest.isNull() || (exact(digest, Set.of("snapshotRevision", "generatedAt", "text"))
                && nonnegative(digest.path("snapshotRevision")) && dateOrNull(digest.path("generatedAt"))
                && digest.path("generatedAt").isTextual() && digest.path("text").isTextual()
                && digest.path("text").asText().getBytes(StandardCharsets.UTF_8).length <= 2048))) return false;
        JsonNode command = view.path("lastCommand");
        if (!(command.isNull() || (exact(command, Set.of("commandId", "action", "intervalSeconds", "operationStatus", "configRevision"))
                && command.path("commandId").isTextual() && Set.of("START", "STOP").contains(command.path("action").asText())
                && (command.path("intervalSeconds").isNull() || nonnegative(command.path("intervalSeconds")))
                && "APPLIED".equals(command.path("operationStatus").asText())
                && nonnegative(command.path("configRevision"))))) return false;
        JsonNode health = view.path("health");
        return exact(health, Set.of("status", "code"))
                && Set.of("IDLE", "WAITING", "RUNNING", "FAULTED").contains(health.path("status").asText())
                && (health.path("code").isNull() || health.path("code").isTextual());
    }

    private static boolean validReceipt(JsonNode receipt) {
        return exact(receipt, Set.of("commandId", "operationStatus", "configRevision", "enabled"))
                && receipt.path("commandId").isTextual()
                && Set.of("APPLIED", "ALREADY_APPLIED").contains(receipt.path("operationStatus").asText())
                && nonnegative(receipt.path("configRevision")) && receipt.path("enabled").isBoolean();
    }

    private static boolean exact(JsonNode node, Set<String> names) {
        return node.isObject() && node.size() == names.size()
                && java.util.stream.StreamSupport.stream(node.properties().spliterator(), false)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(names);
    }

    private static boolean nonnegative(JsonNode node) { return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0; }

    private static boolean dateOrNull(JsonNode node) {
        if (node.isNull()) return true;
        if (!node.isTextual()) return false;
        try { java.time.Instant.parse(node.textValue()); return true; }
        catch (RuntimeException e) { return false; }
    }

    private static boolean validResult(JsonNode result) {
        if (!result.isObject() || result.size() != 7
                || !"workspace".equals(result.path("repositoryRef").asText())
                || !result.path("observedAt").isTextual()
                || !result.path("dirty").isBoolean()) return false;
        String state = result.path("headState").asText();
        if (!Set.of("ATTACHED", "DETACHED", "UNBORN").contains(state)) return false;
        if (!(result.path("branch").isNull() || result.path("branch").isTextual())
                || !(result.path("head").isNull() || result.path("head").isTextual())) return false;
        JsonNode counts = result.path("changeCounts");
        if (counts.isNull()) return true;
        if (!counts.isObject() || counts.size() != 5) return false;
        for (String key : Set.of("staged", "unstaged", "untracked", "conflicted", "submoduleChanged")) {
            if (!counts.path(key).canConvertToInt() || counts.path(key).intValue() < 0) return false;
        }
        return true;
    }

    @Override public void close() {
        if (!state.compareAndSet(State.OPEN, State.CLOSING)) {
            try {
                closeOutcome.join();
            } catch (CompletionException e) {
                throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
            }
            return;
        }
        try {
            synchronized (this) {
                closeOwned(client, transport, child);
            }
            state.set(State.CLOSED);
            closeOutcome.complete(null);
        } catch (RuntimeException e) {
            state.set(State.CLOSED);
            closeOutcome.completeExceptionally(e);
            throw e;
        }
    }

    private static void closeOwned(McpSyncClient client, OwnedStdioClientTransport transport, Process process) {
        boolean closeFailed = false;
        try {
            if (client != null) client.close();
        } catch (RuntimeException | Error e) {
            closeFailed = true;
        }
        try {
            if (transport != null) transport.closeStreams();
        } catch (RuntimeException | Error e) {
            closeFailed = true;
        }
        awaitTermination(process, transport == null ? CHILD_EXIT_TIMEOUT : Duration.ofSeconds(12));
        try {
            if (transport != null) transport.closeRemainingStreams();
        } catch (RuntimeException | Error e) {
            throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
        }
        if (closeFailed) throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
    }

    static void awaitTermination(Process process, Duration timeout) {
        boolean interrupted = false;
        try {
            boolean exited = false;
            try {
                exited = awaitExit(process, timeout);
                if (!exited) {
                    process.destroy();
                    exited = awaitExit(process, CHILD_STOP_TIMEOUT);
                }
            } catch (InterruptedException e) {
                interrupted = true;
            } catch (RuntimeException e) {
                // An unexpected graceful-close failure still reaches the exact-process fallback.
            }
            if (exited || !process.isAlive()) return;
            try {
                process.destroyForcibly();
                if (!awaitExit(process, CHILD_STOP_TIMEOUT) && process.isAlive()) {
                    throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
                }
            } catch (InterruptedException e) {
                interrupted = true;
                if (process.isAlive()) throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
            } catch (RuntimeException e) {
                throw new IllegalStateException("MCP_SHUTDOWN_FAILED");
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitExit(Process process, Duration timeout) throws InterruptedException {
        if (!process.isAlive()) return true;
        try {
            process.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return !process.isAlive();
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            return false;
        }
    }
}
