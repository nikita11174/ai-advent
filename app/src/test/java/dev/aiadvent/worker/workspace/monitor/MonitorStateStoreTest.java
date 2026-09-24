package dev.aiadvent.worker.workspace.monitor;

import dev.aiadvent.worker.workspace.GitStatusReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonitorStateStoreTest {
    @TempDir Path base;

    @Test void atomicWriteReloadAndCorruptionFailClosed() throws Exception {
        Path repository = Files.createDirectory(base.resolve("repository"));
        Path directory = base.resolve("state");
        var store = new MonitorStateStore(repository, directory);
        var initial = store.load();
        assertEquals(0, initial.configRevision());
        assertFalse(initial.enabled());
        String id = UUID.randomUUID().toString();
        var started = initial.withCommand(true, 60, Instant.parse("2026-01-01T00:01:00Z"),
                new MonitorState.Command(id, "0".repeat(64), "START", 60, 1));
        store.save(started);
        assertEquals(started, store.load());
        assertEquals(1, Files.list(directory).count());

        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.parse("2026-01-01T00:01:00Z"),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), true,
                new GitStatusReader.ChangeCounts(0, 0, 1, 0, 0));
        var aggregate = started.aggregate().success(status, status.observedAt());
        var failed = aggregate.failure("GIT_TIMEOUT", Instant.parse("2026-01-01T00:02:00Z"));
        assertEquals(1, failed.successCount());
        assertEquals(1, failed.failureCount());
        assertEquals(status, failed.latestStatus());
        assertTrue(RepositoryDigestFormatter.format(failed).contains("Last successful observation"));
        assertTrue(RepositoryDigestFormatter.format(failed).contains("Latest attempt failed"));
        var sampled = started.withObservation(Instant.parse("2026-01-01T00:03:00Z"), failed,
                new MonitorState.Digest(2, failed.lastCompletedAt(), RepositoryDigestFormatter.format(failed)));
        store.save(sampled);
        assertEquals(sampled, store.load());
        assertThrows(IllegalStateException.class,
                () -> new MonitorStateStore(Files.createDirectory(base.resolve("other")), directory).load());

        Files.writeString(directory.resolve("state.json"), "{bad");
        assertEquals("MONITOR_FAULTED", assertThrows(IllegalStateException.class, store::load).getMessage());
        Files.writeString(directory.resolve("state.json"), "x".repeat(MonitorStateStore.MAX_BYTES + 1));
        assertEquals("MONITOR_FAULTED", assertThrows(IllegalStateException.class, store::load).getMessage());
    }

    @Test void rejectsStateDirectoryInsideRepository() throws Exception {
        Path repository = Files.createDirectory(base.resolve("repository"));
        assertEquals("INVALID_STATE_DIRECTORY", assertThrows(IllegalStateException.class,
                () -> new MonitorStateStore(repository, repository.resolve("monitor"))).getMessage());
    }

    @Test void transitionsCompareSuccessfulSamplesOnly() {
        var empty = MonitorState.empty("0".repeat(64)).aggregate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var one = new GitStatusReader.RepositoryStatus("workspace", now, GitStatusReader.HeadState.ATTACHED,
                "main", "a".repeat(40), false, null);
        var two = new GitStatusReader.RepositoryStatus("workspace", now.plusSeconds(1), GitStatusReader.HeadState.ATTACHED,
                "dev", "b".repeat(40), true, null);
        var result = empty.success(one, now).failure("GIT_FAILURE", now.plusSeconds(1))
                .success(two, now.plusSeconds(2));
        assertEquals(2, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, result.dirtySampleCount());
        assertEquals(1, result.headTransitionCount());
        assertEquals(1, result.branchTransitionCount());
    }
}
