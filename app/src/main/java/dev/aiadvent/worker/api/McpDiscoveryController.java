package dev.aiadvent.worker.api;

import dev.aiadvent.worker.mcp.McpDiscovery;
import dev.aiadvent.worker.mcp.McpDiscoveryMain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
public class McpDiscoveryController {
    private final McpDiscovery discovery;
    private final String ideaEndpoint;

    public McpDiscoveryController(McpDiscovery discovery,
                                  @Value("${ai-advent.mcp.idea.endpoint:${AI_ADVENT_MCP_ENDPOINT:}}") String ideaEndpoint) {
        this.discovery = discovery;
        this.ideaEndpoint = ideaEndpoint;
    }

    @GetMapping("/api/mcp/connections")
    public List<Connection> connections() {
        return List.of(new Connection("idea", "IntelliJ IDEA MCP", !ideaEndpoint.isBlank()));
    }

    @PostMapping("/api/mcp/connections/{id}/discovery")
    public ResponseEntity<?> discover(@PathVariable String id) {
        if (!"idea".equals(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Error("UNKNOWN_CONNECTION"));
        }
        if (ideaEndpoint.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Error("NOT_CONFIGURED"));
        }
        URI endpoint;
        try {
            endpoint = McpDiscoveryMain.endpoint(new String[]{"--endpoint", ideaEndpoint}, Map.of());
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Error("INVALID_CONFIGURATION"));
        }
        try {
            return ResponseEntity.ok(discovery.discover(endpoint));
        }
        catch (IOException | RuntimeException e) {
            String code = e.getMessage();
            if (code != null) {
                code = code.split(":", 2)[0];
            }
            if ("TIMEOUT".equals(code)) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new Error("TIMEOUT"));
            }
            if (code == null || !List.of("INITIALIZE_FAILED", "MALFORMED_INITIALIZATION", "LIST_FAILED",
                    "MALFORMED_LIST", "TOOL_LIMIT", "DESCRIPTOR_LIMIT", "OUTPUT_LIMIT", "PAGINATION_LIMIT").contains(code)) {
                code = "DISCOVERY_FAILED";
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new Error(code));
        }
    }

    public record Connection(String id, String name, boolean configured) {
    }

    public record Error(String code) {
    }
}
