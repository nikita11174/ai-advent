package dev.aiadvent.worker.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GitStatusReaderTest {
    @TempDir Path repository;

    @Test
    void interruptedReadConfirmsExactSubprocessTermination() throws Exception {
        Path marker = repository.resolve("started.marker");
        AtomicReference<Process> owned = new AtomicReference<>();
        var reader = new GitStatusReader(repository, Duration.ofSeconds(20), 65_536, ignored -> {
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                    "-cp", System.getProperty("java.class.path"), SlowGitProcessFixtureMain.class.getName(),
                    marker.toString()).start();
            owned.set(child);
            return child;
        });
        AtomicReference<String> failure = new AtomicReference<>();
        Thread active = Thread.ofPlatform().start(() -> {
            try { reader.read(true); }
            catch (GitStatusReader.StatusException e) { failure.set(e.getMessage()); }
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!Files.exists(marker) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(marker));
        active.interrupt();
        active.join(3000);
        assertFalse(active.isAlive());
        assertEquals("GIT_INTERRUPTED", failure.get());
        assertFalse(owned.get().isAlive());
    }

    @Test
    void reportsUnbornAttachedAndDetachedWithoutInventingHead() throws Exception {
        git("init", "-q", "-b", "main");
        var reader = new GitStatusReader(repository);
        var unborn = reader.read(false);
        assertEquals(GitStatusReader.HeadState.UNBORN, unborn.headState());
        assertEquals("main", unborn.branch());
        assertNull(unborn.head());
        assertFalse(unborn.dirty());
        assertNull(unborn.changeCounts());

        Files.writeString(repository.resolve("tracked.txt"), "initial");
        git("add", "tracked.txt");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "initial");
        var attached = reader.read(true);
        assertEquals(GitStatusReader.HeadState.ATTACHED, attached.headState());
        assertEquals("main", attached.branch());
        assertTrue(attached.head().matches("[0-9a-f]{40}|[0-9a-f]{64}"));
        assertFalse(attached.dirty());
        assertEquals(new GitStatusReader.ChangeCounts(0, 0, 0, 0, 0), attached.changeCounts());

        git("checkout", "-q", "--detach", "HEAD");
        var detached = reader.read(false);
        assertEquals(GitStatusReader.HeadState.DETACHED, detached.headState());
        assertNull(detached.branch());
        assertEquals(attached.head(), detached.head());
    }

    @Test
    void countsStagedUnstagedUntrackedAndExcludesIgnored() throws Exception {
        git("init", "-q", "-b", "main");
        Files.writeString(repository.resolve("tracked.txt"), "initial");
        Files.writeString(repository.resolve(".gitignore"), "ignored.txt\n");
        git("add", "tracked.txt", ".gitignore");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "initial");
        Files.writeString(repository.resolve("tracked.txt"), "staged");
        git("add", "tracked.txt");
        Files.writeString(repository.resolve("tracked.txt"), "unstaged");
        Files.writeString(repository.resolve("new.txt"), "untracked");
        Files.writeString(repository.resolve("ignored.txt"), "ignored");
        var reader = new GitStatusReader(repository);
        var withCounts = reader.read(true);
        assertTrue(withCounts.dirty());
        assertEquals(new GitStatusReader.ChangeCounts(1, 1, 1, 0, 0), withCounts.changeCounts());
        assertNull(reader.read(false).changeCounts());
        assertTrue(reader.read(false).dirty());
        assertEquals("workspace", withCounts.repositoryRef());
        assertNotNull(withCounts.observedAt());
    }

    @Test
    void incompleteMalformedAndFailedObservationsNeverClaimClean() throws Exception {
        git("init", "-q", "-b", "main");
        assertEquals("GIT_STATUS_INCOMPLETE", assertThrows(GitStatusReader.StatusException.class,
                () -> new GitStatusReader(repository, Duration.ofSeconds(5), 8).read(false)).getMessage());
        assertEquals("GIT_TIMEOUT", assertThrows(GitStatusReader.StatusException.class,
                () -> new GitStatusReader(repository, Duration.ofNanos(1), 65_536).read(false)).getMessage());
        assertEquals("GIT_STATUS_INCOMPLETE", assertThrows(GitStatusReader.StatusException.class,
                () -> GitStatusReader.parse("# branch.oid (initial)\0".getBytes(StandardCharsets.UTF_8), false)).getMessage());
        assertEquals("GIT_STATUS_INCOMPLETE", assertThrows(GitStatusReader.StatusException.class,
                () -> GitStatusReader.parse("# branch.oid (initial)\0# branch.head main\0bad\0"
                        .getBytes(StandardCharsets.UTF_8), false)).getMessage());
        assertEquals("GIT_STATUS_INCOMPLETE", assertThrows(GitStatusReader.StatusException.class,
                () -> GitStatusReader.parse("# branch.oid (initial)\0# branch.head main"
                        .getBytes(StandardCharsets.UTF_8), false)).getMessage());
    }

    @Test
    void nonRepositoryIsAnExplicitGitFailure() {
        assertEquals("GIT_FAILURE", assertThrows(GitStatusReader.StatusException.class,
                () -> new GitStatusReader(repository).read(false)).getMessage());
    }

    @Test
    void renameAndMergeConflictAreCountedFromPorcelainV2() throws Exception {
        git("init", "-q", "-b", "main");
        Files.writeString(repository.resolve("tracked.txt"), "base\n");
        git("add", "tracked.txt");
        commit("base");
        git("mv", "tracked.txt", "renamed.txt");
        var renamed = new GitStatusReader(repository).read(true);
        assertTrue(renamed.dirty());
        assertEquals(1, renamed.changeCounts().staged());
        git("reset", "-q", "--hard", "HEAD");

        git("checkout", "-q", "-b", "other");
        Files.writeString(repository.resolve("tracked.txt"), "other\n");
        git("add", "tracked.txt");
        commit("other");
        git("checkout", "-q", "main");
        Files.writeString(repository.resolve("tracked.txt"), "main\n");
        git("add", "tracked.txt");
        commit("main");
        Process conflict = new ProcessBuilder("git", "-C", repository.toString(), "merge", "other")
                .redirectErrorStream(true).start();
        assertTrue(conflict.waitFor(10, TimeUnit.SECONDS));
        assertNotEquals(0, conflict.exitValue());
        var conflicted = new GitStatusReader(repository).read(true);
        assertTrue(conflicted.dirty());
        assertEquals(1, conflicted.changeCounts().conflicted());
    }

    private void commit(String message) throws IOException, InterruptedException {
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", message);
    }

    private void git(String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "fixture Git timed out");
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
