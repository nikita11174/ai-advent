package dev.aiadvent.worker.context;

public class ContextLimitExceededException extends RuntimeException {
    public ContextLimitExceededException(long contextTokens, int contextTokenLimit) {
        super("Estimated context limit exceeded: " + contextTokens + " > " + contextTokenLimit + ".");
    }
}
