package dev.aiadvent.worker.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.workspace.RepositoryResearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RepositoryResearchPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test void threeRealMcpCallsTransferExactResultsAndSaveOutsideRepository() throws Exception {
        Path repo = repository();
        Path reports = temp.resolve("reports");
        ProcessHandle child;
        try (var runtime = new WorkspaceToolRuntime(false, repo.toString(), false, "", false,
                true, reports.toString(), "127.0.0.1", java.time.Duration.ofSeconds(10), null)) {
            child = runtime.ownedChildProcess();
            var pipeline = new RepositoryResearchPipeline(runtime);
            var result = pipeline.run("DAY19_MARKER", 20);
            assertEquals("COMPLETED", result.status());
            assertEquals(3, result.stepsCompleted());
            assertEquals(2, result.matchesSeen());
            assertEquals(2, result.filesMatched());
            assertFalse(result.truncated());
            Path report = reports.resolve(result.receipt().path("reportRef").asText() + ".md");
            assertTrue(Files.exists(report));
            assertEquals(1, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
            String text = Files.readString(report);
            assertTrue(text.contains("a.txt:1"));
            assertTrue(text.contains("b.txt:1"));
            assertFalse(text.contains("DAY19_MARKER alpha"));
            assertFalse(text.contains("DAY19_MARKER beta"));
            var direct = new RepositoryResearch(repo, reports);
            assertEquals(RepositoryResearch.summarize(JSON.valueToTree(direct.search("DAY19_MARKER", 20)))
                    .get("summaryMarkdown"), text);
            assertEquals(Files.size(report), result.receipt().path("bytesWritten").longValue());
            assertEquals(0, Files.list(repo).filter(p -> p.getFileName().toString().endsWith(".md")).count());
            assertEquals(0, pipeline.run("NO_SUCH_MARKER", 20).matchesSeen());
            assertTrue(pipeline.run("DAY19_MARKER", 1).truncated());
            assertEquals("INVALID_ARGUMENTS", assertThrows(RepositoryResearchPipeline.PipelineFailure.class,
                    () -> pipeline.run(" ", 2)).getMessage());
            assertThrows(RepositoryResearchPipeline.PipelineFailure.class,
                    () -> pipeline.run("x".repeat(101), 2));
        }
        assertFalse(child.isAlive());
    }

    @Test void deterministicSummaryRejectsInventedContentAndUntrustedDirectory() throws Exception {
        Path repo = repository();
        var research = new RepositoryResearch(repo, temp.resolve("reports"));
        JsonNode search = JSON.valueToTree(research.search("DAY19_MARKER", 20));
        JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(search));
        assertEquals(2, summary.path("matchesSeen").intValue());
        assertEquals(2, summary.path("filesMatched").intValue());
        assertEquals("a.txt", summary.path("evidence").path(0).path("relativePath").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode) summary).put("summaryMarkdown", "Invented claim");
        assertEquals("INVALID_SUMMARY_RESULT", assertThrows(IllegalStateException.class,
                () -> research.save(java.util.UUID.randomUUID().toString(), summary)).getMessage());
        assertThrows(IllegalStateException.class, () -> new RepositoryResearch(repo, repo.resolve("reports")));
    }

    @Test void failureAtEachStepStopsLaterCalls() throws Exception {
        var runtime = mock(WorkspaceToolRuntime.class);
        var pipeline = new RepositoryResearchPipeline(runtime);
        when(runtime.researchCall(anyString(), any())).thenThrow(new IllegalStateException("MCP_CALL_FAILED"));
        var searchFailure = assertThrows(RepositoryResearchPipeline.PipelineFailure.class,
                () -> pipeline.run("marker", 2));
        assertEquals("SEARCH", searchFailure.step());
        assertEquals(0, searchFailure.stepsCompleted());
        verify(runtime, times(1)).researchCall(anyString(), any());
        reset(runtime);

        Path repo = repository();
        var research = new RepositoryResearch(repo, temp.resolve("reports"));
        JsonNode search = JSON.valueToTree(research.search("DAY19_MARKER", 20));
        when(runtime.researchCall(eq(WorkspaceMcpServerMain.SEARCH), any())).thenReturn(search);
        when(runtime.researchCall(eq(WorkspaceMcpServerMain.SUMMARIZE), any()))
                .thenThrow(new IllegalStateException("MCP_CALL_FAILED"));
        var summaryFailure = assertThrows(RepositoryResearchPipeline.PipelineFailure.class,
                () -> pipeline.run("DAY19_MARKER", 20));
        assertEquals("SUMMARIZE", summaryFailure.step());
        assertEquals(1, summaryFailure.stepsCompleted());
        verify(runtime, never()).researchCall(eq(WorkspaceMcpServerMain.SAVE), any());
        reset(runtime);

        JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(search));
        List<Map<String, Object>> inputs = new ArrayList<>();
        when(runtime.researchCall(anyString(), any())).thenAnswer(call -> {
            String name = call.getArgument(0);
            Map<String, Object> input = call.getArgument(1);
            inputs.add(input);
            if (name.equals(WorkspaceMcpServerMain.SEARCH)) return search;
            if (name.equals(WorkspaceMcpServerMain.SUMMARIZE)) return summary;
            throw new WorkspaceToolRuntime.ResearchCallFailure("SAVE_FAILED", false);
        });
        var saveFailure = assertThrows(RepositoryResearchPipeline.PipelineFailure.class,
                () -> pipeline.run("DAY19_MARKER", 20));
        assertEquals("SAVE", saveFailure.step());
        assertEquals(2, saveFailure.stepsCompleted());
        assertEquals(JSON.valueToTree(inputs.get(1).get("searchResult")), search);
        assertEquals(JSON.valueToTree(inputs.get(2).get("summary")), summary);
        assertEquals(3, inputs.size());
    }

    @Test void lostSaveResponseReconcilesOneCommittedReport() throws Exception {
        Path repo = repository();
        Path reports = temp.resolve("reports");
        var research = new RepositoryResearch(repo, reports);
        JsonNode search = JSON.valueToTree(research.search("DAY19_MARKER", 20));
        JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(search));
        var runtime = mock(WorkspaceToolRuntime.class);
        List<Map<String, Object>> saves = new ArrayList<>();
        when(runtime.researchCall(anyString(), any())).thenAnswer(call -> {
            String name = call.getArgument(0);
            Map<String, Object> input = call.getArgument(1);
            if (name.equals(WorkspaceMcpServerMain.SEARCH)) return search;
            if (name.equals(WorkspaceMcpServerMain.SUMMARIZE)) return summary;
            saves.add(input);
            JsonNode receipt = JSON.valueToTree(research.save((String) input.get("operationId"),
                    JSON.valueToTree(input.get("summary"))));
            if (saves.size() == 1) throw new WorkspaceToolRuntime.ResearchCallFailure("MCP_CALL_FAILED", true);
            return receipt;
        });
        var result = new RepositoryResearchPipeline(runtime).run("DAY19_MARKER", 20);
        assertEquals("COMPLETED", result.status());
        assertEquals(3, result.stepsCompleted());
        assertEquals(2, saves.size());
        assertEquals(saves.get(0), saves.get(1));
        assertEquals(1, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
        assertEquals(RepositoryResearch.reportRef((String) saves.get(0).get("operationId")),
                result.receipt().path("reportRef").asText());
        assertEquals("REPORT_CONFLICT", assertThrows(IllegalStateException.class,
                () -> research.save((String) saves.get(0).get("operationId"),
                        JSON.valueToTree(RepositoryResearch.summarize(JSON.valueToTree(research.search("NO_MATCH", 20))))))
                .getMessage());
        assertEquals(1, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
    }

    @Test void unresolvedSaveTransportFailureIsUnknown() throws Exception {
        Path repo = repository();
        var research = new RepositoryResearch(repo, temp.resolve("reports"));
        JsonNode search = JSON.valueToTree(research.search("DAY19_MARKER", 20));
        JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(search));
        var runtime = mock(WorkspaceToolRuntime.class);
        when(runtime.researchCall(eq(WorkspaceMcpServerMain.SEARCH), any())).thenReturn(search);
        when(runtime.researchCall(eq(WorkspaceMcpServerMain.SUMMARIZE), any())).thenReturn(summary);
        when(runtime.researchCall(eq(WorkspaceMcpServerMain.SAVE), any()))
                .thenThrow(new WorkspaceToolRuntime.ResearchCallFailure("MCP_CALL_FAILED", true));
        var unknown = assertThrows(RepositoryResearchPipeline.PipelineUnknown.class,
                () -> new RepositoryResearchPipeline(runtime).run("DAY19_MARKER", 20));
        assertEquals("SAVE", unknown.step());
        assertEquals(2, unknown.stepsCompleted());
        verify(runtime, times(2)).researchCall(eq(WorkspaceMcpServerMain.SAVE), any());
    }

    @Test void realMcpSaveReusesReceiptAndRejectsConflictingSummary() throws Exception {
        Path repo = repository();
        Path reports = temp.resolve("reports");
        try (var runtime = new WorkspaceToolRuntime(false, repo.toString(), false, "", false,
                true, reports.toString(), "127.0.0.1", java.time.Duration.ofSeconds(10), null)) {
            var research = new RepositoryResearch(repo, reports);
            JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(
                    JSON.valueToTree(research.search("DAY19_MARKER", 20))));
            String id = java.util.UUID.randomUUID().toString();
            Map<String, Object> input = Map.of("operationId", id, "summary", JSON.convertValue(summary, Map.class));
            JsonNode first = runtime.researchCall(WorkspaceMcpServerMain.SAVE, input);
            assertEquals(first, runtime.researchCall(WorkspaceMcpServerMain.SAVE, input));
            assertEquals(1, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
            JsonNode different = JSON.valueToTree(RepositoryResearch.summarize(
                    JSON.valueToTree(research.search("NO_MATCH", 20))));
            var conflict = assertThrows(WorkspaceToolRuntime.ResearchCallFailure.class,
                    () -> runtime.researchCall(WorkspaceMcpServerMain.SAVE,
                            Map.of("operationId", id, "summary", JSON.convertValue(different, Map.class))));
            assertEquals("REPORT_CONFLICT", conflict.getMessage());
            assertFalse(conflict.uncertain());
            assertEquals(1, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
        }
    }

    @Test void preCommitSaveFailureHasNoReceiptOrReport() throws Exception {
        Path repo = repository();
        Path reports = temp.resolve("reports");
        var research = new RepositoryResearch(repo, reports);
        JsonNode summary = JSON.valueToTree(RepositoryResearch.summarize(
                JSON.valueToTree(research.search("DAY19_MARKER", 20))));
        String id = java.util.UUID.randomUUID().toString();
        Files.writeString(reports.resolve(RepositoryResearch.reportRef(id) + ".tmp"), "partial");
        assertEquals("SAVE_FAILED", assertThrows(IllegalStateException.class,
                () -> research.save(id, summary)).getMessage());
        assertEquals(0, Files.list(reports).filter(p -> p.toString().endsWith(".md")).count());
        assertEquals("INVALID_ARGUMENTS", assertThrows(IllegalStateException.class,
                () -> research.save("../outside", summary)).getMessage());
    }

    private Path repository() throws Exception {
        Path repo = Files.createDirectories(temp.resolve("repo"));
        Files.writeString(repo.resolve("a.txt"), "DAY19_MARKER alpha\n");
        Files.writeString(repo.resolve("b.txt"), "DAY19_MARKER beta\n");
        git(repo, "init");
        git(repo, "add", "a.txt", "b.txt");
        return repo;
    }

    private static void git(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(); command.add("git"); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        assertEquals(0, process.waitFor(), new String(process.getInputStream().readAllBytes()));
    }
}
