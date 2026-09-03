package dev.aiadvent.mentor;

public class DeepSeekException extends Exception {
    DeepSeekException(String message) {
        super(message);
    }

    DeepSeekException(String message, Throwable cause) {
        super(message, cause);
    }
}
