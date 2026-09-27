package dev.aiadvent.worker.workspace.monitor;

import dev.aiadvent.worker.workspace.GitStatusReader.RepositoryStatus;

import java.time.Instant;

public record MonitorState(int schemaVersion, String monitorId, String repositoryBinding,
                           long configRevision, long snapshotRevision, boolean enabled,
                           Integer intervalSeconds, Instant nextRunAt, Aggregate aggregate,
                           Digest latestDigest, Command lastCommand) {
    public static final String ID = "repository-monitor";

    public static MonitorState empty(String binding) {
        return new MonitorState(1, ID, binding, 0, 0, false, null, null,
                new Aggregate(0, 0, 0, 0, 0, null, null, null, null, null, null), null, null);
    }

    public MonitorState withCommand(boolean nextEnabled, Integer interval, Instant due, Command command) {
        return new MonitorState(1, ID, repositoryBinding, command.configRevision(), snapshotRevision + 1,
                nextEnabled, interval, due, aggregate, latestDigest, command);
    }

    public MonitorState withObservation(Instant due, Aggregate nextAggregate, Digest digest) {
        return new MonitorState(1, ID, repositoryBinding, configRevision, snapshotRevision + 1,
                enabled, intervalSeconds, due, nextAggregate, digest, lastCommand);
    }

    public record Aggregate(long successCount, long failureCount, long dirtySampleCount,
                            long headTransitionCount, long branchTransitionCount,
                            Instant firstSuccessAt, Instant lastSuccessAt, Instant lastCompletedAt,
                            String lastOutcome, String lastFailureCode, RepositoryStatus latestStatus) {
        public Aggregate success(RepositoryStatus status, Instant completedAt) {
            RepositoryStatus previous = latestStatus;
            return new Aggregate(successCount + 1, failureCount, dirtySampleCount + (status.dirty() ? 1 : 0),
                    headTransitionCount + (previous != null && !java.util.Objects.equals(previous.head(), status.head()) ? 1 : 0),
                    branchTransitionCount + (previous != null && !java.util.Objects.equals(previous.branch(), status.branch()) ? 1 : 0),
                    firstSuccessAt == null ? completedAt : firstSuccessAt, completedAt, completedAt,
                    "SUCCESS", null, status);
        }

        public Aggregate failure(String code, Instant completedAt) {
            return new Aggregate(successCount, failureCount + 1, dirtySampleCount, headTransitionCount,
                    branchTransitionCount, firstSuccessAt, lastSuccessAt, completedAt, "FAILURE", code, latestStatus);
        }
    }

    public record Digest(long snapshotRevision, Instant generatedAt, String text) { }

    public record Command(String commandId, String fingerprint, String action, Integer intervalSeconds,
                          long configRevision) { }
}
