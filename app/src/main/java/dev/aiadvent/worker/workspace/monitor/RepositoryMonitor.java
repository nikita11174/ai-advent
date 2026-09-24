package dev.aiadvent.worker.workspace.monitor;

import dev.aiadvent.worker.workspace.GitStatusReader;
import dev.aiadvent.worker.workspace.GitStatusReader.RepositoryStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class RepositoryMonitor implements AutoCloseable {
    public record Receipt(String commandId, String operationStatus, long configRevision, boolean enabled) { }
    public record Health(String status, String code) { }
    public record View(String monitorId, String repositoryRef, boolean enabled, Integer intervalSeconds,
                       long configRevision, Instant nextRunAt, MonitorState.Aggregate aggregate,
                       MonitorState.Digest latestDigest, PublicCommand lastCommand, Health health) { }
    public record PublicCommand(String commandId, String action, Integer intervalSeconds,
                                String operationStatus, long configRevision) { }
    public record Mutation(Receipt receipt, View view) { }

    private final Object lock = new Object();
    private final MonitorStateStore store;
    private final Supplier<RepositoryStatus> readGit;
    private final Clock clock;
    private final boolean demoInterval;
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "repository-monitor");
        thread.setDaemon(true);
        return thread;
    });
    private MonitorState state;
    private ScheduledFuture<?> future;
    private long generation;
    private boolean running;
    private boolean closed;
    private String faultCode;
    private final CompletableFuture<Void> closeOutcome = new CompletableFuture<>();

    public RepositoryMonitor(MonitorStateStore store, GitStatusReader reader, Clock clock, boolean demoInterval) {
        this(store, () -> reader.read(true), clock, demoInterval);
    }

    RepositoryMonitor(MonitorStateStore store, Supplier<RepositoryStatus> readGit, Clock clock, boolean demoInterval) {
        this.store = store;
        this.readGit = readGit;
        this.clock = clock;
        this.demoInterval = demoInterval;
        timer.setRemoveOnCancelPolicy(true);
        try {
            state = store.load();
            if (!demoInterval && state.enabled() && state.intervalSeconds() < 60) {
                throw new IllegalStateException("MONITOR_FAULTED");
            }
            if (state.enabled()) schedule(state.nextRunAt());
        } catch (RuntimeException e) {
            timer.shutdownNow();
            throw e;
        }
    }

    public Mutation start(int intervalSeconds, String commandId, long expectedRevision) {
        if (intervalSeconds < (demoInterval ? 10 : 60) || intervalSeconds > 86_400) {
            throw new IllegalStateException("INVALID_ARGUMENTS");
        }
        return command("START", intervalSeconds, commandId, expectedRevision);
    }

    public Mutation stop(String commandId, long expectedRevision) {
        return command("STOP", null, commandId, expectedRevision);
    }

    private Mutation command(String action, Integer interval, String commandId, long expectedRevision) {
        if (expectedRevision < 0 || commandId == null || !isUuid(commandId)) {
            throw new IllegalStateException("INVALID_ARGUMENTS");
        }
        synchronized (lock) {
            if (closed) throw new IllegalStateException("MONITOR_DISABLED");
            if (faultCode != null && !action.equals("STOP")) throw new IllegalStateException(faultCode);
            String fingerprint = MonitorStateStore.sha256(action + ":" + interval + ":" + expectedRevision);
            MonitorState.Command previous = state.lastCommand();
            if (previous != null && previous.commandId().equals(commandId)) {
                if (!previous.fingerprint().equals(fingerprint)) throw new IllegalStateException("COMMAND_ID_CONFLICT");
                return new Mutation(new Receipt(commandId, "ALREADY_APPLIED", state.configRevision(), state.enabled()), view());
            }
            if (state.configRevision() != expectedRevision) throw new IllegalStateException("CONFIG_REVISION_CONFLICT");
            boolean nextEnabled = action.equals("START");
            boolean changed = state.enabled() != nextEnabled || (nextEnabled && !Objects.equals(state.intervalSeconds(), interval));
            long revision = state.configRevision() + 1;
            Instant due = changed && nextEnabled ? clock.instant().plusSeconds(interval) : state.nextRunAt();
            var receipt = new Receipt(commandId, "APPLIED", revision, nextEnabled);
            var saved = state.withCommand(nextEnabled, nextEnabled ? interval : state.intervalSeconds(),
                    nextEnabled ? due : null, new MonitorState.Command(commandId, fingerprint, action, interval, revision));
            persist(saved);
            if (!nextEnabled) faultCode = null;
            if (changed) {
                generation++;
                if (future != null) future.cancel(false);
                future = null;
                if (nextEnabled) schedule(due);
            }
            return new Mutation(receipt, view());
        }
    }

    public View read() {
        synchronized (lock) { return view(); }
    }

    private View view() {
        MonitorState.Command command = state.lastCommand();
        PublicCommand publicCommand = command == null ? null : new PublicCommand(command.commandId(),
                command.action(), command.intervalSeconds(), "APPLIED", command.configRevision());
        String health = faultCode != null ? "FAULTED" : running ? "RUNNING" : state.enabled() ? "WAITING" : "IDLE";
        return new View(MonitorState.ID, "workspace", state.enabled(), state.intervalSeconds(),
                state.configRevision(), state.nextRunAt(), state.aggregate(), state.latestDigest(),
                publicCommand, new Health(health, faultCode));
    }

    private void schedule(Instant due) {
        long delay = Math.max(0, Duration.between(clock.instant(), due).toMillis());
        long ticket = generation;
        future = timer.schedule(() -> tick(ticket), delay, TimeUnit.MILLISECONDS);
    }

    private void tick(long ticket) {
        synchronized (lock) {
            if (closed || faultCode != null || !state.enabled() || ticket != generation) return;
            running = true;
        }
        RepositoryStatus status = null;
        String failure = null;
        boolean unexpectedFailure = false;
        try {
            status = readGit.get();
        } catch (GitStatusReader.StatusException e) {
            String code = e.getMessage();
            failure = code != null && java.util.Set.of("GIT_FAILURE", "GIT_TIMEOUT", "GIT_STATUS_INCOMPLETE",
                    "GIT_INTERRUPTED", "INVALID_REPOSITORY").contains(code) ? code : "GIT_FAILURE";
        } catch (RuntimeException e) {
            unexpectedFailure = true;
        }
        Instant completed = clock.instant();
        String published = null;
        synchronized (lock) {
            running = false;
            if (closed || ticket != generation || !state.enabled()) return;
            if (unexpectedFailure) {
                faultCode = "MONITOR_FAULTED";
                return;
            }
            try {
                MonitorState.Aggregate aggregate = failure == null
                        ? state.aggregate().success(status, completed) : state.aggregate().failure(failure, completed);
                String text = RepositoryDigestFormatter.format(aggregate);
                Instant nextDue = completed.plusSeconds(state.intervalSeconds());
                MonitorState next = state.withObservation(nextDue, aggregate,
                        new MonitorState.Digest(state.snapshotRevision() + 1, completed, text));
                persist(next);
                schedule(nextDue);
                published = "REPOSITORY_MONITOR_DIGEST snapshotRevision=" + next.snapshotRevision() + " " + text;
            } catch (RuntimeException e) {
                faultCode = "RESULT_LIMIT".equals(e.getMessage()) ? "RESULT_LIMIT" : "PERSISTENCE_FAILED";
            }
        }
        if (published != null) System.err.println(published);
    }

    private void persist(MonitorState next) {
        try {
            store.save(next);
            state = next;
        } catch (RuntimeException e) {
            faultCode = "PERSISTENCE_FAILED";
            throw new IllegalStateException("PERSISTENCE_FAILED");
        }
    }

    private static boolean isUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException e) { return false; }
    }

    @Override public void close() {
        boolean owner;
        synchronized (lock) {
            owner = !closed;
            if (owner) {
                closed = true;
                generation++;
                if (future != null) future.cancel(false);
                timer.shutdown();
            }
        }
        if (!owner) {
            try { closeOutcome.join(); }
            catch (CompletionException e) { throw new IllegalStateException("MONITOR_SHUTDOWN_FAILED"); }
            return;
        }
        boolean interrupted = false;
        try {
            try {
                if (!timer.awaitTermination(8, TimeUnit.SECONDS)) {
                    timer.shutdownNow();
                    if (!timer.awaitTermination(2, TimeUnit.SECONDS)) throw new IllegalStateException("MONITOR_SHUTDOWN_FAILED");
                }
            } catch (InterruptedException e) {
                interrupted = true;
                timer.shutdownNow();
                try {
                    if (!timer.awaitTermination(2, TimeUnit.SECONDS)) throw new IllegalStateException("MONITOR_SHUTDOWN_FAILED");
                } catch (InterruptedException again) {
                    interrupted = true;
                    throw new IllegalStateException("MONITOR_SHUTDOWN_FAILED");
                }
            }
            closeOutcome.complete(null);
        } catch (RuntimeException e) {
            closeOutcome.completeExceptionally(e);
            throw e;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
