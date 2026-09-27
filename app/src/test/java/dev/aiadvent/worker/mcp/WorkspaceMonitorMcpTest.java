package dev.aiadvent.worker.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceMonitorMcpTest {
    @TempDir Path base;

    @Test void nullExceptionMessageIsSanitizedAtMcpBoundary() {
        var monitor = org.mockito.Mockito.mock(dev.aiadvent.worker.workspace.monitor.RepositoryMonitor.class);
        org.mockito.Mockito.when(monitor.read()).thenThrow(new IllegalStateException());
        var response = WorkspaceMcpServerMain.monitorCall(monitor,
                new io.modelcontextprotocol.spec.McpSchema.CallToolRequest(WorkspaceMcpServerMain.READ_MONITOR,
                        java.util.Map.of()));
        assertTrue(Boolean.TRUE.equals(response.isError()));
        assertEquals("MCP_CALL_FAILED", new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response)
                .path("content").path(0).path("text").asText());
    }

    @Test void realMcpStartReadStopAndExactChildClose() throws Exception {
        Path repository = java.nio.file.Files.createDirectory(base.resolve("repo"));
        Process git = new ProcessBuilder("git", "init", "-q", "-b", "main", repository.toString()).start();
        assertEquals(0, git.waitFor());
        String stateDirectory = base.resolve("state").toString();
        ProcessHandle owned;
        try (var runtime = new WorkspaceToolRuntime(false, repository.toString(), true, stateDirectory,
                true, "127.0.0.1", Duration.ofSeconds(10), null)) {
            owned = runtime.ownedChildProcess();
            assertTrue(owned.isAlive());
            assertFalse(runtime.enabled());
            assertTrue(runtime.monitorEnabled());
            assertEquals(0, runtime.getMonitor().path("configRevision").asLong());
            String startId = UUID.randomUUID().toString();
            var started = runtime.startMonitor(10, startId, 0);
            assertEquals("APPLIED", started.path("receipt").path("operationStatus").asText());
            assertEquals("ALREADY_APPLIED", runtime.startMonitor(10, startId, 0)
                    .path("receipt").path("operationStatus").asText());
            assertTrue(runtime.getMonitor().path("enabled").asBoolean());
            assertEquals("CONFIG_REVISION_CONFLICT", assertThrows(IllegalStateException.class,
                    () -> runtime.stopMonitor(UUID.randomUUID().toString(), 0)).getMessage());
            var stopped = runtime.stopMonitor(UUID.randomUUID().toString(), 1);
            assertFalse(stopped.path("view").path("enabled").asBoolean());
        }
        assertFalse(owned.isAlive());
    }

    @Test void externalBindRejectedBeforeChildCreation() throws Exception {
        Path repository = java.nio.file.Files.createDirectory(base.resolve("repo"));
        assertEquals("MONITOR_LOCAL_BIND_REQUIRED", assertThrows(IllegalStateException.class,
                () -> new WorkspaceToolRuntime(false, repository.toString(), true, base.resolve("state").toString(),
                        true, "0.0.0.0", Duration.ofSeconds(10), null)).getMessage());
    }

    @Test void corruptStateFailsStartupAndCleansSpawnedExactChild() throws Exception {
        Path repository = java.nio.file.Files.createDirectory(base.resolve("repo"));
        Process git = new ProcessBuilder("git", "init", "-q", "-b", "main", repository.toString()).start();
        assertEquals(0, git.waitFor());
        Path directory = java.nio.file.Files.createDirectory(base.resolve("state"));
        java.nio.file.Files.writeString(directory.resolve("state.json"), "{broken");
        var child = new java.util.concurrent.atomic.AtomicReference<Process>();
        assertEquals("MCP_DISCOVERY_FAILED", assertThrows(IllegalStateException.class,
                () -> new WorkspaceToolRuntime(false, repository.toString(), true, directory.toString(),
                        true, "127.0.0.1", Duration.ofSeconds(10), () -> {
                    var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                            "-cp", System.getProperty("java.class.path"), WorkspaceMcpServerMain.class.getName());
                    builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
                    builder.environment().put("AI_ADVENT_MONITOR_ENABLED", "true");
                    builder.environment().put("AI_ADVENT_MONITOR_STATE_DIRECTORY", directory.toString());
                    builder.environment().put("AI_ADVENT_MONITOR_DEMO", "true");
                    Process spawned = builder.start();
                    child.set(spawned);
                    return spawned;
                })).getMessage());
        assertNotNull(child.get());
        assertFalse(child.get().isAlive());
    }
}
