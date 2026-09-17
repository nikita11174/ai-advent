package dev.aiadvent.worker.api;

public record ApiError(String error, String rawResponse) {
}
