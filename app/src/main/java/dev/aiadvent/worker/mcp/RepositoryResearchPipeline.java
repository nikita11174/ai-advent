package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.workspace.RepositoryResearch;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public final class RepositoryResearchPipeline {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WorkspaceToolRuntime runtime;

    public RepositoryResearchPipeline(WorkspaceToolRuntime runtime) { this.runtime = runtime; }

    public Result run(String query, int maxResults) {
        if (query == null || query.isBlank() || query.length() > 100 || query.chars().anyMatch(c -> c < 32 || c == 127)
                || maxResults < 1 || maxResults > 20) throw new PipelineFailure("SEARCH", "INVALID_ARGUMENTS", 0);
        JsonNode search;
        try { search = runtime.researchCall(WorkspaceMcpServerMain.SEARCH,
                Map.of("query", query, "maxResults", maxResults)); }
        catch (RuntimeException e) { throw new PipelineFailure("SEARCH", e.getMessage(), 0); }
        JsonNode summary;
        try { summary = runtime.researchCall(WorkspaceMcpServerMain.SUMMARIZE,
                Map.of("searchResult", JSON.convertValue(search, Map.class))); }
        catch (RuntimeException e) { throw new PipelineFailure("SUMMARIZE", e.getMessage(), 1); }
        if (!summary.path("query").equals(search.path("query"))
                || !summary.path("truncated").equals(search.path("truncated"))
                || !summary.path("evidence").equals(search.path("matches")))
            throw new PipelineFailure("SUMMARIZE", "INVALID_TOOL_RESULT", 1);
        String operationId = UUID.randomUUID().toString();
        Map<String, Object> saveInput = Map.of("operationId", operationId,
                "summary", JSON.convertValue(summary, Map.class));
        JsonNode receipt;
        try { receipt = checkedSave(saveInput, summary, operationId); }
        catch (RuntimeException first) {
            if (first instanceof WorkspaceToolRuntime.ResearchCallFailure known && !known.uncertain())
                throw new PipelineFailure("SAVE", first.getMessage(), 2);
            try { receipt = checkedSave(saveInput, summary, operationId); }
            catch (RuntimeException second) { throw new PipelineUnknown("SAVE", 2); }
        }
        return new Result("COMPLETED", 3, search.path("matchCount").intValue(),
                summary.path("filesMatched").intValue(), search.path("truncated").booleanValue(), receipt);
    }

    private JsonNode checkedSave(Map<String, Object> input, JsonNode summary, String operationId) {
        JsonNode receipt = runtime.researchCall(WorkspaceMcpServerMain.SAVE, input);
        try {
            byte[] bytes = summary.path("summaryMarkdown").textValue().getBytes(StandardCharsets.UTF_8);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length != receipt.path("bytesWritten").intValue()
                    || !hash.equals(receipt.path("contentHash").textValue())
                    || !RepositoryResearch.reportRef(operationId).equals(receipt.path("reportRef").asText()))
                throw new IllegalStateException("INVALID_SAVE_RESULT");
        } catch (Exception e) { throw new WorkspaceToolRuntime.ResearchCallFailure("INVALID_SAVE_RESULT", true); }
        return receipt;
    }

    public record Result(String status, int stepsCompleted, int matchesSeen, int filesMatched,
                         boolean truncated, JsonNode receipt) { }

    public static final class PipelineUnknown extends RuntimeException {
        private final String step;
        private final int stepsCompleted;
        PipelineUnknown(String step, int stepsCompleted) {
            super("SAVE_OUTCOME_UNKNOWN"); this.step = step; this.stepsCompleted = stepsCompleted;
        }
        public String step() { return step; }
        public int stepsCompleted() { return stepsCompleted; }
    }

    public static final class PipelineFailure extends RuntimeException {
        private final String step;
        private final int stepsCompleted;
        PipelineFailure(String step, String code, int stepsCompleted) {
            super(code == null || !java.util.Set.of("INVALID_ARGUMENTS", "RESEARCH_DISABLED", "MCP_CALL_FAILED",
                    "INVALID_TOOL_RESULT", "INVALID_SAVE_RESULT", "SAVE_FAILED", "REPORT_CONFLICT").contains(code)
                    ? "MCP_CALL_FAILED" : code);
            this.step = step;
            this.stepsCompleted = stepsCompleted;
        }
        public String step() { return step; }
        public int stepsCompleted() { return stepsCompleted; }
    }
}
