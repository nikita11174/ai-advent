package dev.aiadvent.mentor.model;

public class ModelExecutionException extends Exception {
    private final String rawResponse;

    public ModelExecutionException(String message) {
        this(message, null, null);
    }

    public ModelExecutionException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String rawResponse() {
        return rawResponse;
    }
}
