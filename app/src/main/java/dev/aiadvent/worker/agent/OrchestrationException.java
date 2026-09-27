package dev.aiadvent.worker.agent;

public final class OrchestrationException extends RuntimeException {
    private final OrchestrationTrace trace;
    public OrchestrationException(String code, OrchestrationTrace trace, Throwable cause) {
        super(code, cause); this.trace = trace;
    }
    public OrchestrationTrace trace() { return trace; }
}
