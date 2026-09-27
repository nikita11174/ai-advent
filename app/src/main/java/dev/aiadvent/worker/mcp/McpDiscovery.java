package dev.aiadvent.worker.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class McpDiscovery {
    private static final ObjectMapper JSON = new ObjectMapper();

    public Result discover(URI endpoint) throws IOException {
        ((Logger) LoggerFactory.getLogger("io.modelcontextprotocol")).setLevel(Level.WARN);
        var transport = HttpClientStreamableHttpTransport.builder("http://" + endpoint.getRawAuthority())
                .endpoint(endpoint.getRawPath())
                .jsonMapper(new JacksonMcpJsonMapper(JSON))
                .connectTimeout(Duration.ofSeconds(5))
                .maxResponseSize(1_048_576)
                .build();
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        McpSchema.InitializeResult initialization;
        List<String> descriptors;
        try (McpSyncClient client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            try {
                initialization = client.initialize();
            }
            catch (RuntimeException e) {
                throw new IllegalStateException("INITIALIZE_FAILED", e);
            }
            McpDiscoveryMain.checkDeadline(deadline);
            if (initialization == null || initialization.serverInfo() == null
                    || initialization.serverInfo().name() == null || initialization.serverInfo().name().isBlank()
                    || initialization.protocolVersion() == null || initialization.protocolVersion().isBlank()) {
                throw new IllegalStateException("MALFORMED_INITIALIZATION");
            }
            descriptors = McpDiscoveryMain.discover(client, deadline);
        }
        String header = JSON.writeValueAsString(new Header(initialization.serverInfo().name(),
                initialization.serverInfo().version(), initialization.protocolVersion(), descriptors.size()));
        if (header.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1_024) {
            throw new IllegalStateException("MALFORMED_INITIALIZATION");
        }
        List<JsonNode> tools = new ArrayList<>(descriptors.size());
        for (String descriptor : descriptors) {
            tools.add(JSON.readTree(descriptor));
        }
        return new Result(initialization.serverInfo().name(), initialization.serverInfo().version(),
                initialization.protocolVersion(), Instant.now(), List.copyOf(tools));
    }

    public record Result(String server, String serverVersion, String protocolVersion, Instant checkedAt,
                         List<JsonNode> tools) {
        @JsonProperty("toolCount")
        public int toolCount() { return tools.size(); }
    }

    private record Header(String server, String serverVersion, String protocolVersion, int toolCount) {
    }
}
