package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.agent.ToolExecutor;
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
public final class WorkspaceToolRuntime implements ToolExecutor, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration CHILD_START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CHILD_EXIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CHILD_STOP_TIMEOUT = Duration.ofSeconds(1);
    private enum State { OPEN, CLOSING, CLOSED }
    private final McpSyncClient client;
    private final OwnedStdioClientTransport transport;
    private final Process child;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final CompletableFuture<Void> closeOutcome = new CompletableFuture<>();

    @Autowired
    public WorkspaceToolRuntime(@Value("${mentor.git-status-tool.enabled:false}") boolean enabled,
                                @Value("${mentor.git-status-tool.repository:}") String repository) {
        this(enabled, repository, CHILD_START_TIMEOUT, null);
    }

    WorkspaceToolRuntime(boolean enabled, String repository, Duration startTimeout,
                         OwnedStdioClientTransport.ProcessStarter processStarter) {
        if (!enabled) {
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
                    || listed.tools().size() != 1 || !WorkspaceMcpServerMain.TOOL_NAME.equals(listed.tools().getFirst().name())
                    || !WorkspaceMcpServerMain.inputSchema().equals(listed.tools().getFirst().inputSchema())
                    || !WorkspaceMcpServerMain.outputSchema().equals(listed.tools().getFirst().outputSchema())) {
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
        this.client = Objects.requireNonNull(client);
        this.child = Objects.requireNonNull(child);
        this.transport = null;
        state.set(State.OPEN);
    }

    ProcessHandle ownedChildProcess() {
        return child == null ? null : child.toHandle();
    }

    @Override public boolean enabled() { return client != null && state.get() == State.OPEN; }

    @Override public synchronized ToolResult execute(ToolRequest request) {
        if (state.get() != State.OPEN) throw new IllegalStateException("MCP_RUNTIME_CLOSED");
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
        awaitTermination(process, CHILD_EXIT_TIMEOUT);
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
