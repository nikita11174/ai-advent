package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aiadvent.worker.mcp.WorkspaceToolRuntime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/repository-monitor")
final class RepositoryMonitorController {
    private final WorkspaceToolRuntime runtime;

    RepositoryMonitorController(WorkspaceToolRuntime runtime) { this.runtime = runtime; }

    @PostMapping("/start")
    ResponseEntity<CommandResponse> start(@RequestBody JsonNode body) {
        if (body == null || !body.isObject() || (body.size() != 2 && body.size() != 3)
                || !java.util.stream.StreamSupport.stream(body.properties().spliterator(), false)
                .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet())
                .equals(body.size() == 2 ? Set.of("commandId", "expectedRevision")
                        : Set.of("commandId", "expectedRevision", "intervalSeconds"))
                || (body.has("intervalSeconds") && (!body.path("intervalSeconds").isIntegralNumber()
                || !body.path("intervalSeconds").canConvertToInt()))
                || !body.path("expectedRevision").isIntegralNumber() || !body.path("expectedRevision").canConvertToLong()
                || !body.path("commandId").isTextual()) throw new IllegalStateException("INVALID_ARGUMENTS");
        var command = new StartCommand(body.has("intervalSeconds") ? body.path("intervalSeconds").intValue() : 300,
                uuid(body.path("commandId").textValue()), body.path("expectedRevision").longValue());
        try {
            JsonNode result = runtime.startMonitor(command.intervalSeconds(), command.commandId(), command.expectedRevision());
            return ResponseEntity.ok(response(result));
        } catch (IllegalStateException e) {
            if ("MONITOR_UNKNOWN".equals(e.getMessage())) return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new CommandResponse("UNKNOWN", "NOT_STARTED", null, null));
            throw e;
        }
    }

    @PostMapping("/stop")
    ResponseEntity<CommandResponse> stop(@RequestBody JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2
                || !java.util.stream.StreamSupport.stream(body.properties().spliterator(), false)
                .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("commandId", "expectedRevision"))
                || !body.path("expectedRevision").isIntegralNumber()
                || !body.path("expectedRevision").canConvertToLong()
                || !body.path("commandId").isTextual()) throw new IllegalStateException("INVALID_ARGUMENTS");
        var command = new StopCommand(uuid(body.path("commandId").textValue()), body.path("expectedRevision").longValue());
        try {
            return ResponseEntity.ok(response(runtime.stopMonitor(command.commandId(), command.expectedRevision())));
        } catch (IllegalStateException e) {
            if ("MONITOR_UNKNOWN".equals(e.getMessage())) return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new CommandResponse("UNKNOWN", "NOT_STARTED", null, null));
            throw e;
        }
    }

    @GetMapping
    JsonNode get() { return runtime.getMonitor(); }

    private static CommandResponse response(JsonNode result) {
        return new CommandResponse(result.path("receipt").path("operationStatus").asText(),
                "NOT_STARTED", result.path("receipt"), result.path("view"));
    }

    private static String uuid(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("INVALID_ARGUMENTS");
        }
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> error(IllegalStateException exception) {
        String code = exception.getMessage();
        if (code == null || !Set.of("INVALID_ARGUMENTS", "CONFIG_REVISION_CONFLICT", "COMMAND_ID_CONFLICT",
                "MONITOR_DISABLED", "PERSISTENCE_FAILED", "MONITOR_FAULTED", "MCP_RUNTIME_CLOSED",
                "MCP_CALL_FAILED", "RESULT_LIMIT").contains(code)) code = "MCP_CALL_FAILED";
        HttpStatus status = switch (code) {
            case "INVALID_ARGUMENTS" -> HttpStatus.BAD_REQUEST;
            case "CONFIG_REVISION_CONFLICT", "COMMAND_ID_CONFLICT" -> HttpStatus.CONFLICT;
            case "MONITOR_DISABLED", "MCP_RUNTIME_CLOSED", "MONITOR_FAULTED" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ApiError(code, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedBody() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ARGUMENTS", null));
    }

    record StartCommand(int intervalSeconds, String commandId, long expectedRevision) { }
    record StopCommand(String commandId, long expectedRevision) { }
    record CommandResponse(String operationStatus, String turnStatus, JsonNode receipt, JsonNode view) { }
}
