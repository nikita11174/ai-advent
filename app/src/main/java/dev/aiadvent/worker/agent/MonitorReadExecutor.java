package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolResult;

public interface MonitorReadExecutor {
    boolean monitorEnabled();
    ToolResult readMonitor(ToolRequest request);
}
