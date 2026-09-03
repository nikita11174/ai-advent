package dev.aiadvent.mentor;

public class DeepSeekException extends Exception {
    private final String rawResponse;

    DeepSeekException(String message) {
        this(message, null, null);
    }

    DeepSeekException(String message, Throwable cause) {
        this(message, null, cause);
    }

    DeepSeekException(String message, String rawResponse) {
        this(message, rawResponse, null);
    }

    private DeepSeekException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    String rawResponse() {
        return rawResponse;
    }
}
