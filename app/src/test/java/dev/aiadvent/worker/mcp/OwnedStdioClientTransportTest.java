package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnedStdioClientTransportTest {
    @TempDir Path repository;
    private final JacksonMcpJsonMapper mapper = new JacksonMcpJsonMapper(new ObjectMapper());

    @Test void inputLineIsBoundedEvenWithoutDelimiter() {
        assertThrows(IOException.class, () -> OwnedStdioClientTransport.readLine(
                new BufferedReader(new StringReader("x".repeat(100_000)))));
        assertEquals("ok", assertDoesNotThrow(() -> OwnedStdioClientTransport.readLine(
                new BufferedReader(new StringReader("ok\r\n")))));
    }

    @Test void startupTimeoutDoesNotAccumulateBlockedStartupThreads() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var child = mock(Process.class);
        when(child.waitFor(1, TimeUnit.SECONDS)).thenReturn(true);
        var attempts = new AtomicInteger();
        var daemon = new AtomicBoolean();
        assertEquals("MCP_START_FAILED", assertThrows(IllegalStateException.class,
                () -> OwnedStdioClientTransport.startProcess(() -> {
                    daemon.set(Thread.currentThread().isDaemon());
                    entered.countDown();
                    try { release.await(); } catch (InterruptedException e) { throw new IOException(e); }
                    return child;
                }, Duration.ofMillis(100))).getMessage());
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertTrue(daemon.get());
            assertEquals("MCP_START_FAILED", assertThrows(IllegalStateException.class,
                    () -> OwnedStdioClientTransport.startProcess(() -> {
                        attempts.incrementAndGet();
                        return mock(Process.class);
                    }, Duration.ofMillis(100))).getMessage());
            assertEquals(0, attempts.get());
        } finally {
            release.countDown();
        }
        verify(child, timeout(1000)).destroyForcibly();
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (OwnedStdioClientTransport.startupBusy() && System.nanoTime() < deadline) Thread.onSpinWait();
        assertFalse(OwnedStdioClientTransport.startupBusy());
    }

    @Test void oversizedAndMalformedStdoutFailWithinInitializationBound() throws Exception {
        for (String mode : new String[] {"oversize", "malformed"}) {
            var transport = OwnedStdioClientTransport.start(fixture(mode)::start, mapper, Duration.ofSeconds(3));
            try {
                var client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(1))
                        .requestTimeout(Duration.ofSeconds(1)).build();
                try {
                    assertThrows(RuntimeException.class, client::initialize);
                } finally {
                    client.close();
                }
            } finally {
                transport.closeStreams();
                WorkspaceToolRuntime.awaitTermination(transport.process(), Duration.ofSeconds(1));
                transport.closeRemainingStreams();
            }
            assertFalse(transport.process().isAlive());
        }
    }

    @Test void largeStderrDoesNotBlockProtocolOrGrowDiagnostics() throws Exception {
        var git = new ProcessBuilder("git", "-C", repository.toString(), "init", "-q", "-b", "main").start();
        assertTrue(git.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, git.exitValue());
        var transport = OwnedStdioClientTransport.start(fixture("large-stderr")::start,
                mapper, Duration.ofSeconds(3));
        try {
            var client = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(5))
                    .requestTimeout(Duration.ofSeconds(3)).build();
            try {
                client.initialize();
                assertEquals(1, client.listTools().tools().size());
                var result = client.callTool(new McpSchema.CallToolRequest("git_repository_status",
                        Map.of("includeChangeCounts", true)));
                assertFalse(Boolean.TRUE.equals(result.isError()));
                assertTrue(transport.lastStderrLine().length() <= 512);
            } finally {
                client.close();
            }
        } finally {
            transport.closeStreams();
            WorkspaceToolRuntime.awaitTermination(transport.process(), Duration.ofSeconds(2));
            transport.closeRemainingStreams();
        }
    }

    @Test void stdoutContinuesDrainingAfterStdinEof() throws Exception {
        var transport = OwnedStdioClientTransport.start(fixture("drain-after-eof")::start,
                mapper, Duration.ofSeconds(3));
        try {
            transport.connect(message -> Mono.empty()).block(Duration.ofSeconds(1));
            transport.closeGracefully().block(Duration.ofSeconds(8));
            assertFalse(transport.process().isAlive());
            assertEquals(0, transport.process().exitValue());
        } finally {
            WorkspaceToolRuntime.awaitTermination(transport.process(), Duration.ofSeconds(1));
            transport.closeRemainingStreams();
        }
    }

    private ProcessBuilder fixture(String mode) {
        var java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                WorkspaceMcpTransportFixtureMain.class.getName(), mode);
        builder.environment().put("AI_ADVENT_WORKSPACE_REPOSITORY", repository.toString());
        return builder;
    }
}
