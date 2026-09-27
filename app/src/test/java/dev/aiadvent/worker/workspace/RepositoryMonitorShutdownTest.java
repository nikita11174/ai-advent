package dev.aiadvent.worker.workspace;

import dev.aiadvent.worker.workspace.monitor.MonitorState;
import dev.aiadvent.worker.workspace.monitor.MonitorStateStore;
import dev.aiadvent.worker.workspace.monitor.RepositoryMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryMonitorShutdownTest {
    @TempDir Path base;

    @Test void shutdownDuringOwnedSubprocessReadLeavesNoChildAndPreservesEnabled() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        Path marker = base.resolve("started.marker");
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        AtomicReference<Process> owned = new AtomicReference<>();
        var reader = new GitStatusReader(repo, Duration.ofSeconds(20), 65_536, ignored -> {
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-cp", System.getProperty("java.class.path"), SlowGitProcessFixtureMain.class.getName(),
                    marker.toString()).start();
            owned.set(child);
            return child;
        });
        var monitor = new RepositoryMonitor(store, reader, Clock.systemUTC(), true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!Files.exists(marker) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(marker));
        monitor.close();
        assertFalse(owned.get().isAlive());
        assertTrue(store.load().enabled());
        assertEquals(0, store.load().aggregate().successCount());
        monitor.close();
    }
}
