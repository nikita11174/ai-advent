package dev.aiadvent.worker.workspace.monitor;

import java.nio.charset.StandardCharsets;

public final class RepositoryDigestFormatter {
    private RepositoryDigestFormatter() { }

    public static String format(MonitorState.Aggregate aggregate) {
        String text = "Observations: success=" + aggregate.successCount() + ", failure=" + aggregate.failureCount()
                + ", dirty samples=" + aggregate.dirtySampleCount() + ", HEAD transitions="
                + aggregate.headTransitionCount() + ", branch transitions=" + aggregate.branchTransitionCount() + ". ";
        if ("FAILURE".equals(aggregate.lastOutcome())) {
            text += "Latest attempt failed at " + aggregate.lastCompletedAt() + " (" + aggregate.lastFailureCode() + "). ";
        }
        if (aggregate.latestStatus() == null) {
            text += "No successful Git observation yet.";
        } else {
            var status = aggregate.latestStatus();
            text += "Last successful observation at " + aggregate.lastSuccessAt() + ": branch="
                    + safe(status.branch()) + ", HEAD=" + safe(status.head()) + ", dirty=" + status.dirty()
                    + ". Change counts describe Git status records, not exact file counts.";
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > 2048) throw new IllegalStateException("RESULT_LIMIT");
        return text;
    }

    private static String safe(String value) {
        return value == null ? "none" : value.replaceAll("[\\p{Cntrl}]", "?");
    }
}
