package dev.aiadvent.worker.agent;

public final class ToolTurnException extends RuntimeException {
    private final ToolTurnTrace trace;

    public ToolTurnException(String code, ToolTurnTrace trace, Throwable cause) {
        super(code, cause);
        this.trace = trace;
    }

    public ToolTurnTrace trace() {
        return trace;
    }
}
