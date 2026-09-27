package dev.aiadvent.worker.workspace.monitor;

import dev.aiadvent.worker.workspace.GitStatusReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryMonitorTest {
    @TempDir Path base;

    @Test void commandRevisionIdempotencyAndRecovery() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        var calls = new AtomicInteger();
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), false, null);
        String id = UUID.randomUUID().toString();
        try (var monitor = new RepositoryMonitor(store, () -> { calls.incrementAndGet(); return status; },
                Clock.systemUTC(), true)) {
            assertEquals("APPLIED", monitor.start(10, id, 0).receipt().operationStatus());
            assertEquals("ALREADY_APPLIED", monitor.start(10, id, 0).receipt().operationStatus());
            assertEquals("COMMAND_ID_CONFLICT", assertThrows(IllegalStateException.class,
                    () -> monitor.stop(id, 1)).getMessage());
            assertEquals("CONFIG_REVISION_CONFLICT", assertThrows(IllegalStateException.class,
                    () -> monitor.stop(UUID.randomUUID().toString(), 0)).getMessage());
            assertEquals(0, calls.get());
            assertEquals("APPLIED", monitor.stop(UUID.randomUUID().toString(), 1).receipt().operationStatus());
            assertEquals(2, monitor.read().configRevision());
        }
        assertFalse(store.load().enabled());

        var state = store.load();
        store.save(state.withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 3)));
        assertEquals("MONITOR_FAULTED", assertThrows(IllegalStateException.class,
                () -> new RepositoryMonitor(store, () -> status, Clock.systemUTC(), false)).getMessage());
        CountDownLatch observed = new CountDownLatch(1);
        try (var resumed = new RepositoryMonitor(store, () -> { observed.countDown(); return status; },
                Clock.systemUTC(), true)) {
            assertTrue(observed.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (resumed.read().aggregate().successCount() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, resumed.read().aggregate().successCount());
        }
        assertEquals(1, store.load().aggregate().successCount());
    }

    @Test void futureDueAndDisabledRestartDoNotRunImmediately() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().plusSeconds(5),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        AtomicInteger calls = new AtomicInteger();
        try (var future = new RepositoryMonitor(store, () -> { calls.incrementAndGet(); return null; },
                Clock.systemUTC(), true)) {
            Thread.sleep(250);
            assertEquals(0, calls.get());
        }
        var enabled = store.load();
        store.save(enabled.withCommand(false, 10, null,
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "STOP", null, 2)));
        try (var disabled = new RepositoryMonitor(store, () -> { calls.incrementAndGet(); return null; },
                Clock.systemUTC(), true)) {
            Thread.sleep(250);
            assertEquals(0, calls.get());
            assertFalse(disabled.read().enabled());
        }
    }

    @Test void futureRecoveryWaitsForPersistedDueBeyondOneInterval() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().plusMillis(12_500),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        AtomicInteger calls = new AtomicInteger();
        try (var monitor = new RepositoryMonitor(store, () -> {
            calls.incrementAndGet();
            throw new SecurityException();
        }, Clock.systemUTC(), true)) {
            Thread.sleep(10_300);
            assertEquals(0, calls.get());
            assertEquals("WAITING", monitor.read().health().status());
        }
    }

    @Test void stoppedDemoStateLoadsInProductionWithoutLosingObservation() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), true, null);
        try (var demo = new RepositoryMonitor(store, () -> status, Clock.systemUTC(), true)) {
            demo.start(10, UUID.randomUUID().toString(), 0);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (demo.read().aggregate().successCount() == 0 && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(1, demo.read().aggregate().successCount());
            demo.stop(UUID.randomUUID().toString(), 1);
        }
        try (var production = new RepositoryMonitor(store, () -> fail("Disabled monitor must not run"),
                Clock.systemUTC(), false)) {
            assertFalse(production.read().enabled());
            assertEquals(10, production.read().intervalSeconds());
            assertEquals(1, production.read().aggregate().successCount());
            assertEquals(status, production.read().aggregate().latestStatus());
            assertEquals("INVALID_ARGUMENTS", assertThrows(IllegalStateException.class,
                    () -> production.start(10, UUID.randomUUID().toString(), 2)).getMessage());
            assertEquals("APPLIED", production.start(300, UUID.randomUUID().toString(), 2)
                    .receipt().operationStatus());
            assertEquals(300, production.read().intervalSeconds());
        }
    }

    @Test void unexpectedObservationFailuresFaultWithoutPublishingOrRearming() throws Exception {
        for (RuntimeException unexpected : new RuntimeException[] {
                new SecurityException("secret path"), new IllegalStateException("secret path") }) {
            Path repo = Files.createDirectory(base.resolve("repo-" + unexpected.getClass().getSimpleName()));
            var store = new MonitorStateStore(repo, base.resolve("state-" + unexpected.getClass().getSimpleName()));
            var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                    GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), true, null);
            var initial = store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                    new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1));
            Instant previousAt = Instant.now().minusSeconds(20);
            var aggregate = initial.aggregate().success(status, previousAt);
            store.save(initial.withObservation(Instant.now().minusSeconds(1), aggregate,
                    new MonitorState.Digest(2, previousAt, RepositoryDigestFormatter.format(aggregate))));
            var calls = new AtomicInteger();
            try (var monitor = new RepositoryMonitor(store, () -> {
                calls.incrementAndGet();
                throw unexpected;
            }, Clock.systemUTC(), true)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (!"FAULTED".equals(monitor.read().health().status()) && System.nanoTime() < deadline)
                    Thread.sleep(10);
                var view = monitor.read();
                assertEquals("FAULTED", view.health().status());
                assertEquals("MONITOR_FAULTED", view.health().code());
                assertEquals(1, calls.get());
                assertEquals(1, view.aggregate().successCount());
                assertEquals(0, view.aggregate().failureCount());
                assertEquals(status, view.aggregate().latestStatus());
                assertEquals(1, view.configRevision());
                assertEquals(2, store.load().snapshotRevision());
                assertEquals("MONITOR_FAULTED", assertThrows(IllegalStateException.class,
                        () -> monitor.start(10, UUID.randomUUID().toString(), 1)).getMessage());
                if (unexpected instanceof SecurityException) {
                    Thread.sleep(10_300);
                    assertEquals(1, calls.get());
                }
                assertEquals("APPLIED", monitor.stop(UUID.randomUUID().toString(), 1).receipt().operationStatus());
                assertEquals("IDLE", monitor.read().health().status());
                assertFalse(store.load().enabled());
            }
            assertEquals(1, calls.get());
        }
    }

    @Test void faultedEnabledStateAttemptsOneCurrentObservationOnRestart() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        CountDownLatch failed = new CountDownLatch(1);
        try (var faulted = new RepositoryMonitor(store, () -> {
            failed.countDown();
            throw new SecurityException("private detail");
        }, Clock.systemUTC(), true)) {
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!"FAULTED".equals(faulted.read().health().status()) && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertEquals("FAULTED", faulted.read().health().status());
            assertTrue(store.load().enabled());
            assertEquals(0, store.load().aggregate().failureCount());
        }
        CountDownLatch recovered = new CountDownLatch(1);
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), false, null);
        try (var restarted = new RepositoryMonitor(store, () -> {
            recovered.countDown();
            return status;
        }, Clock.systemUTC(), true)) {
            assertTrue(recovered.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (restarted.read().aggregate().successCount() == 0 && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertEquals(1, restarted.read().aggregate().successCount());
            assertEquals(0, restarted.read().aggregate().failureCount());
        }
    }

    @Test void stopInvalidatesActiveObservation() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), false, null);
        try (var monitor = new RepositoryMonitor(store, () -> {
            entered.countDown();
            try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return status;
        }, Clock.systemUTC(), true)) {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertEquals("APPLIED", monitor.stop(UUID.randomUUID().toString(), 1).receipt().operationStatus());
            release.countDown();
        }
        assertEquals(0, store.load().aggregate().successCount());
        assertFalse(store.load().enabled());
    }

    @Test void failedObservationPreservesSuccessAndNextTickRecovers() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var store = new MonitorStateStore(repo, base.resolve("state"));
        store.save(store.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        AtomicInteger attempts = new AtomicInteger();
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), true, null);
        try (var monitor = new RepositoryMonitor(store, () -> {
            if (attempts.getAndIncrement() == 0) throw new GitStatusReader.StatusException("GIT_FAILURE");
            return status;
        }, Clock.systemUTC(), true)) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (monitor.read().aggregate().failureCount() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, monitor.read().aggregate().failureCount());
            assertNull(monitor.read().aggregate().latestStatus());
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (monitor.read().aggregate().successCount() == 0 && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(1, monitor.read().aggregate().successCount());
            assertEquals(1, monitor.read().aggregate().failureCount());
            assertEquals(status, monitor.read().aggregate().latestStatus());
        }
    }

    @Test void persistenceFailureDoesNotPublishMutation() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var backing = new MonitorStateStore(repo, base.resolve("state"));
        var store = org.mockito.Mockito.spy(backing);
        org.mockito.Mockito.doThrow(new IllegalStateException("disk detail")).when(store).save(org.mockito.ArgumentMatchers.any());
        try (var monitor = new RepositoryMonitor(store, () -> null, Clock.systemUTC(), true)) {
            assertEquals("PERSISTENCE_FAILED", assertThrows(IllegalStateException.class,
                    () -> monitor.start(10, UUID.randomUUID().toString(), 0)).getMessage());
            assertEquals(0, monitor.read().configRevision());
            assertEquals("FAULTED", monitor.read().health().status());
        }
        assertFalse(Files.exists(base.resolve("state/state.json")));
    }

    @Test void closeWaitsForActivePersistenceAndConcurrentCloseSharesOutcome() throws Exception {
        Path repo = Files.createDirectory(base.resolve("repo"));
        var backing = new MonitorStateStore(repo, base.resolve("state"));
        backing.save(backing.load().withCommand(true, 10, Instant.now().minusSeconds(1),
                new MonitorState.Command(UUID.randomUUID().toString(), "0".repeat(64), "START", 10, 1)));
        var store = org.mockito.Mockito.spy(backing);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            writing.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(store).save(org.mockito.ArgumentMatchers.any());
        var status = new GitStatusReader.RepositoryStatus("workspace", Instant.now(),
                GitStatusReader.HeadState.ATTACHED, "main", "a".repeat(40), false, null);
        var monitor = new RepositoryMonitor(store, () -> status, Clock.systemUTC(), true);
        assertTrue(writing.await(3, TimeUnit.SECONDS));
        Thread first = Thread.ofPlatform().start(monitor::close);
        Thread second = Thread.ofPlatform().start(monitor::close);
        assertTrue(first.isAlive() || second.isAlive());
        release.countDown();
        first.join(3000);
        second.join(3000);
        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(1, backing.load().aggregate().successCount());
    }
}
