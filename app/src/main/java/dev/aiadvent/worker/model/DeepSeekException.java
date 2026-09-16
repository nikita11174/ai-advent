package dev.aiadvent.worker.model;

public class DeepSeekException extends Exception {
    private final String rawResponse;

    public DeepSeekException(String message) {
        this(message, null, null);
    }

    public DeepSeekException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public DeepSeekException(String message, String rawResponse) {
        this(message, rawResponse, null);
    }

    private DeepSeekException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String rawResponse() {
        return rawResponse;
    }
}
