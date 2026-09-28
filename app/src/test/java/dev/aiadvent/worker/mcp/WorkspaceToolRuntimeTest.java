package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import io.modelcontextprotocol.client.McpSyncClient;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkspaceToolRuntimeTest {
    @TempDir Path repository;

    @Test void launchesWorkspaceMainFromIdeClasspathAndBootJarFromPackage() throws Exception {
        String ideClasspath = "target/classes" + java.io.File.pathSeparator + "spring-jcl.jar";
        assertEquals("dev.aiadvent.worker.mcp.WorkspaceMcpServerMain",
                WorkspaceToolRuntime.childCommand("java", ideClasspath).getLast());
        Path classpathJar = jar("classpath.jar", false);
        assertEquals("dev.aiadvent.worker.mcp.WorkspaceMcpServerMain",
                WorkspaceToolRuntime.childCommand("java", classpathJar.toString()).getLast());
        Path bootJar = jar("local-ai-worker.jar", true);
        assertEquals("org.springframework.boot.loader.launch.PropertiesLauncher",
                WorkspaceToolRuntime.childCommand("java", bootJar.toString()).getLast());
    }

    private Path jar(String name, boolean boot) throws Exception {
        Path path = repository.resolve(name);
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (boot) manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS,
                "org.springframework.boot.loader.launch.JarLauncher");
        try (var out = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            if (boot) {
                out.putNextEntry(new JarEntry("BOOT-INF/classes/dev/aiadvent/worker/mcp/WorkspaceMcpServerMain.class"));
                out.closeEntry();
            }
        }
        return path;
    }

    @Test void ownershipIsRequiredBeforeOpen() {
        assertThrows(NullPointerException.class, () -> new WorkspaceToolRuntime(mock(McpSyncClient.class), null));
    }

    @Test void disabledRuntimeDoesNotStartAndEnabledRuntimeReusesClientForTwoCalls() throws Exception {
        try (var disabled = new WorkspaceToolRuntime(false, "")) {
            assertFalse(disabled.enabled());
        }
        initRepository();
        var request = new ToolRequest("git_repository_status", new ObjectMapper().readTree("{\"includeChangeCounts\":true}"));
        ProcessHandle child;
        try (var runtime = new WorkspaceToolRuntime(true, repository.toString())) {
            child = runtime.ownedChildProcess();
            assertNotNull(child);
            assertTrue(child.isAlive());
            assertTrue(runtime.enabled());
            var first = runtime.execute(request);
            Files.writeString(repository.resolve("fresh-untracked.txt"), "fixture");
            var second = runtime.execute(request);
            assertEquals("workspace", first.structuredResult().path("repositoryRef").asText());
            assertEquals(first.structuredResult().path("headState"), second.structuredResult().path("headState"));
            assertFalse(first.structuredResult().path("dirty").asBoolean());
            assertTrue(second.structuredResult().path("dirty").asBoolean());
            assertEquals(1, second.structuredResult().path("changeCounts").path("untracked").asInt());
            try (var gitChildren = child.children()) {
                var activeGit = gitChildren.filter(process -> process.info().commandLine().orElse("")
                        .contains("git.exe")).toList();
                assertTrue(activeGit.isEmpty(), "status call left a Git subprocess");
            }
            assertThrows(IllegalArgumentException.class, () -> runtime.execute(
                    new ToolRequest("other", request.arguments())));
        }
        assertFalse(child.isAlive(), "close returned before its MCP child terminated");
    }

    @Test void closeWaitsForActiveExecuteRejectsNewExecuteAndIsIdempotent() throws Exception {
        var client = mock(McpSyncClient.class);
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(false);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closed = new AtomicBoolean();
        when(client.callTool(any())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            throw new IllegalStateException("test call finished");
        });
        doAnswer(call -> { closed.set(true); return null; }).when(client).close();
        var runtime = new WorkspaceToolRuntime(client, child);
        var request = new ToolRequest("git_repository_status", new ObjectMapper().readTree("{\"includeChangeCounts\":true}"));
        var active = CompletableFuture.runAsync(() -> assertThrows(IllegalStateException.class, () -> runtime.execute(request)));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var closing = CompletableFuture.runAsync(runtime::close);
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (runtime.enabled() && System.nanoTime() < deadline) Thread.onSpinWait();
        assertFalse(runtime.enabled(), "close did not enter CLOSING");
        var rejected = CompletableFuture.supplyAsync(() -> assertThrows(IllegalStateException.class,
                () -> runtime.execute(request)).getMessage());
        assertFalse(closing.isDone());
        assertFalse(closed.get());
        release.countDown();
        active.get(5, TimeUnit.SECONDS);
        closing.get(5, TimeUnit.SECONDS);
        assertEquals("MCP_RUNTIME_CLOSED", rejected.get(5, TimeUnit.SECONDS));
        assertEquals("MCP_RUNTIME_CLOSED", assertThrows(IllegalStateException.class,
                () -> runtime.execute(request)).getMessage());
        runtime.close();
        verify(client, times(1)).callTool(any());
        verify(client, times(1)).close();
    }

    @Test void discoveryFailureCleansUpStartedChild() throws Exception {
        initRepository();
        List<Long> before;
        try (var children = ProcessHandle.current().children()) {
            before = children.map(ProcessHandle::pid).toList();
        }
        try (MockedStatic<WorkspaceMcpServerMain> server = mockStatic(WorkspaceMcpServerMain.class,
                CALLS_REAL_METHODS)) {
            server.when(WorkspaceMcpServerMain::inputSchema).thenReturn(Map.of("type", "invalid"));
            var failure = assertThrows(IllegalStateException.class,
                    () -> new WorkspaceToolRuntime(true, repository.toString()));
            assertEquals("MCP_DISCOVERY_FAILED", failure.getMessage());
        }
        try (var children = ProcessHandle.current().children()) {
            assertTrue(children.noneMatch(process -> !before.contains(process.pid())),
                    "initialization failure left an owned MCP child");
        }
    }

    @Test void initializationFailureBeforeServerReadyLeavesNoChild() throws Exception {
        Path notDirectory = Files.writeString(repository.resolve("not-directory"), "fixture");
        List<Long> before;
        try (var children = ProcessHandle.current().children()) {
            before = children.map(ProcessHandle::pid).toList();
        }
        var failure = assertThrows(IllegalStateException.class,
                () -> new WorkspaceToolRuntime(true, notDirectory.toString()));
        assertEquals("MCP_DISCOVERY_FAILED", failure.getMessage());
        try (var children = ProcessHandle.current().children()) {
            assertTrue(children.noneMatch(process -> !before.contains(process.pid())));
        }
    }

    @Test void lateStartAfterTimeoutDestroysExactProcess() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var child = mock(Process.class);
        var unrelated = mock(Process.class);
        when(child.waitFor(1, TimeUnit.SECONDS)).thenReturn(true);
        var failure = assertThrows(IllegalStateException.class, () -> new WorkspaceToolRuntime(true, repository.toString(),
                Duration.ofMillis(100), () -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
            returned.countDown();
            return child;
        }));
        assertEquals("MCP_START_FAILED", failure.getMessage());
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        release.countDown();
        assertTrue(returned.await(1, TimeUnit.SECONDS));
        verify(child, timeout(1000)).destroyForcibly();
        verifyNoInteractions(unrelated);
    }

    @Test void lateRealWorkspaceChildCannotSurviveFailedRuntimeConstruction() throws Exception {
        initRepository();
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var process = new AtomicReference<Process>();
        var failure = assertThrows(IllegalStateException.class, () -> new WorkspaceToolRuntime(true, repository.toString(),
                Duration.ofMillis(100), () -> {
                    try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                    var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
                    var builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                            WorkspaceMcpServerMain.class.getName());
                    builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
                    var started = builder.start();
                    process.set(started);
                    returned.countDown();
                    return started;
                }));
        assertEquals("MCP_START_FAILED", failure.getMessage());
        try {
            release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            assertNotNull(process.get());
            assertTrue(process.get().onExit().get(5, TimeUnit.SECONDS).isAlive() == false);
        } finally {
            release.countDown();
            if (process.get() != null && process.get().isAlive()) process.get().destroyForcibly();
        }
    }

    @Test void interruptedShutdownStillForcesOwnedChild() {
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(true, true, false);
        when(child.onExit()).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            WorkspaceToolRuntime.awaitTermination(child, Duration.ofMillis(10));
            verify(child).destroyForcibly();
        } finally {
            Thread.interrupted();
        }
    }

    @Test void boundedShutdownFailureIsExplicit() {
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(true);
        when(child.onExit()).thenReturn(new CompletableFuture<>());
        var failure = assertThrows(IllegalStateException.class,
                () -> WorkspaceToolRuntime.awaitTermination(child, Duration.ofMillis(10)));
        assertEquals("MCP_SHUTDOWN_FAILED", failure.getMessage());
        verify(child).destroy();
        verify(child).destroyForcibly();
    }

    @Test void unexpectedGracefulWaitFailureStillForcesTheOwnedChild() {
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(true, true, false);
        when(child.onExit()).thenThrow(new IllegalStateException("unexpected wait failure"));
        WorkspaceToolRuntime.awaitTermination(child, Duration.ofMillis(10));
        verify(child).destroyForcibly();
    }

    @Test void clientCloseFailureStillCleansExactChild() {
        var client = mock(McpSyncClient.class);
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(false);
        doThrow(new IllegalStateException("private SDK detail")).when(client).close();
        var runtime = new WorkspaceToolRuntime(client, child);
        assertEquals("MCP_SHUTDOWN_FAILED", assertThrows(IllegalStateException.class, runtime::close).getMessage());
        verify(child, atLeastOnce()).isAlive();
        assertEquals("MCP_SHUTDOWN_FAILED", assertThrows(IllegalStateException.class, runtime::close).getMessage());
        verify(client, times(1)).close();
    }

    @Test void realChildWithoutStdinClosureIsTerminatedWithinBound() throws Exception {
        initRepository();
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                WorkspaceMcpServerMain.class.getName());
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
        var child = builder.start();
        try {
            assertTrue(child.isAlive());
            WorkspaceToolRuntime.awaitTermination(child, Duration.ofMillis(100));
            assertFalse(child.isAlive());
        } finally {
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    @Test void interruptedRealChildShutdownStillConfirmsExit() throws Exception {
        initRepository();
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                WorkspaceMcpServerMain.class.getName());
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
        var child = builder.start();
        try {
            Thread.currentThread().interrupt();
            WorkspaceToolRuntime.awaitTermination(child, Duration.ofSeconds(5));
            assertFalse(child.isAlive());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            if (child.isAlive()) child.destroyForcibly();
        }
    }

    @Test void closeSurfacesUnconfirmedTerminationAndRepeatedCloseRemainsStable() {
        var client = mock(McpSyncClient.class);
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(true);
        when(child.onExit()).thenReturn(new CompletableFuture<>());
        var runtime = new WorkspaceToolRuntime(client, child);
        assertEquals("MCP_SHUTDOWN_FAILED", assertThrows(IllegalStateException.class, runtime::close).getMessage());
        assertEquals("MCP_SHUTDOWN_FAILED", assertThrows(IllegalStateException.class, runtime::close).getMessage());
        verify(client, times(1)).close();
    }

    @Test void concurrentCloseUsesOneCleanupOutcome() throws Exception {
        var client = mock(McpSyncClient.class);
        var child = mock(Process.class);
        when(child.isAlive()).thenReturn(false);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(client).close();
        var runtime = new WorkspaceToolRuntime(client, child);
        var first = CompletableFuture.runAsync(runtime::close);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var second = CompletableFuture.runAsync(runtime::close);
        assertFalse(second.isDone());
        release.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        verify(client, times(1)).close();
    }

    @Test void closeTargetsOnlyTheRetainedProcess() {
        var client = mock(McpSyncClient.class);
        var owned = mock(Process.class);
        var unrelated = mock(Process.class);
        when(owned.isAlive()).thenReturn(false);
        new WorkspaceToolRuntime(client, owned).close();
        verifyNoInteractions(unrelated);
        verify(client).close();
    }

    private void initRepository() throws Exception {
        var process = new ProcessBuilder(List.of("git", "-C", repository.toString(), "init", "-q", "-b", "main")).start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
    }

}
