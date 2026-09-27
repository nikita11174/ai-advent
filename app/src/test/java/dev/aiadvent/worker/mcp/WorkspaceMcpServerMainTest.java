package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMcpServerMainTest {
    @TempDir Path repository;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void realStdioServerListsAndCallsOnlyGitStatusThenExits() throws Exception {
        git("init", "-q", "-b", "main");
        Files.writeString(repository.resolve("tracked.txt"), "initial");
        git("add", "tracked.txt");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "initial");
        Files.writeString(repository.resolve("new.txt"), "untracked");
        AtomicLong serverPid = new AtomicLong();
        AtomicReference<String> serverError = new AtomicReference<>("");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var parameters = ServerParameters.builder(javaExecutable)
                .args("-cp", System.getProperty("java.class.path"), WorkspaceMcpServerMain.class.getName())
                .build();
        var transport = new StdioClientTransport(parameters, new JacksonMcpJsonMapper(JSON)) {
            @Override
            protected ProcessBuilder getProcessBuilder() {
                ProcessBuilder builder = super.getProcessBuilder();
                Map<String, String> inherited = new HashMap<>(builder.environment());
                builder.environment().clear();
                for (var entry : inherited.entrySet()) {
                    if (Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                            .contains(entry.getKey().toUpperCase(java.util.Locale.ROOT))) {
                        builder.environment().put(entry.getKey(), entry.getValue());
                    }
                }
                builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
                return builder;
            }
        };
        transport.setStdErrorHandler(line -> {
            serverError.updateAndGet(previous -> previous + line + "\n");
            if (line.startsWith("WORKSPACE_MCP_PID=")) {
                serverPid.set(Long.parseLong(line.substring("WORKSPACE_MCP_PID=".length())));
            }
        });
        try (var client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            var initialization = client.initialize();
            assertEquals("2025-11-25", initialization.protocolVersion());
            assertEquals("ai-advent-workspace", initialization.serverInfo().name());
            var list = client.listTools();
            assertEquals(1, list.tools().size());
            assertNull(list.nextCursor());
            var tool = list.tools().getFirst();
            assertEquals("git_repository_status", tool.name());
            assertEquals(WorkspaceMcpServerMain.inputSchema(), tool.inputSchema());
            assertEquals(WorkspaceMcpServerMain.outputSchema(), tool.outputSchema());

            var withoutCounts = client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("includeChangeCounts", false)));
            assertFalse(Boolean.TRUE.equals(withoutCounts.isError()));
            var result = JSON.valueToTree(withoutCounts.structuredContent());
            assertEquals("ATTACHED", result.get("headState").asText());
            assertEquals("main", result.get("branch").asText());
            assertTrue(result.get("dirty").asBoolean());
            assertTrue(result.get("changeCounts").isNull());

            var withCounts = client.callTool(new McpSchema.CallToolRequest(tool.name(), Map.of("includeChangeCounts", true)));
            assertFalse(Boolean.TRUE.equals(withCounts.isError()));
            var counted = JSON.valueToTree(withCounts.structuredContent());
            assertEquals(1, counted.get("changeCounts").get("untracked").asInt());
            assertEquals(0, counted.get("changeCounts").get("staged").asInt());
            assertFalse(counted.toString().contains(repository.toString()));
            assertFalse(counted.toString().contains("new.txt"));

            var invalid = client.callTool(new McpSchema.CallToolRequest(tool.name(),
                    Map.of("includeChangeCounts", true, "path", ".")));
            assertTrue(Boolean.TRUE.equals(invalid.isError()));
            assertEquals("INVALID_ARGUMENTS", ((McpSchema.TextContent) invalid.content().getFirst()).text());

            assertTrue(serverPid.get() > 0, "direct Java MCP server was not observed: " + serverError.get());
            assertTrue(ProcessHandle.of(serverPid.get()).map(ProcessHandle::isAlive).orElse(false));
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (ProcessHandle.of(serverPid.get()).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(ProcessHandle.of(serverPid.get()).map(ProcessHandle::isAlive).orElse(false),
                "owned MCP server remains after client close: " + serverError.get());
        assertFalse(serverError.get().contains("Exception"), serverError.get());
    }

    @Test
    void invalidArgumentsAreRejectedBeforeGit() {
        var reader = new dev.aiadvent.worker.workspace.GitStatusReader(repository);
        assertEquals("INVALID_ARGUMENTS", ((McpSchema.TextContent) WorkspaceMcpServerMain.call(reader,
                new McpSchema.CallToolRequest("git_repository_status", Map.of("includeChangeCounts", true, "path", ".")))
                .content().getFirst()).text());
        assertEquals("INVALID_ARGUMENTS", ((McpSchema.TextContent) WorkspaceMcpServerMain.call(reader,
                new McpSchema.CallToolRequest("git_repository_status", Map.of("includeChangeCounts", "true")))
                .content().getFirst()).text());
        assertEquals("UNKNOWN_TOOL", ((McpSchema.TextContent) WorkspaceMcpServerMain.call(reader,
                new McpSchema.CallToolRequest("other", Map.of("includeChangeCounts", true)))
                .content().getFirst()).text());
    }

    @Test
    void serverExitsZeroOnStdinEof() throws Exception {
        git("init", "-q", "-b", "main");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                WorkspaceMcpServerMain.class.getName());
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
        Process process = builder.start();
        process.getOutputStream().close();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "server did not exit on stdin EOF");
        assertEquals(0, process.exitValue(), new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        assertFalse(process.isAlive());
    }

    @Test
    void invalidConfiguredRepositoryFailsWithoutLeakingPathToEitherStream() throws Exception {
        String sentinel = "private-repository-" + UUID.randomUUID();
        Path missingRepository = repository.resolve(sentinel);
        assertFalse(Files.exists(missingRepository));
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                WorkspaceMcpServerMain.class.getName());
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", missingRepository.toString());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "server did not fail promptly");
            assertEquals(2, process.exitValue());
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("WORKSPACE_MCP_STARTUP_FAILED", stderr.strip());
            assertFalse(stderr.contains(sentinel));
            assertFalse(stderr.contains(missingRepository.toString()));
            assertFalse(stderr.contains("Exception"));
            assertEquals("", stdout);
        }
        finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private void git(String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
