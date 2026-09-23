package dev.aiadvent.worker.agent;

import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolRequest;
import dev.aiadvent.worker.model.ToolCapableModelExecutor.ToolResult;

public interface ToolExecutor {
    boolean enabled();
    ToolResult execute(ToolRequest request);
}
