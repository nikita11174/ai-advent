package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryEvidenceReaderTest {
    @TempDir Path repository;

    @Test
    void readsOnlyBoundedAnchoredSource() throws Exception {
        Path source = repository.resolve("TaskService.java");
        Files.writeString(source, "class TaskService {\n  void apply() { }\n}\n");
        var evidence = new RepositoryEvidenceReader(repository.toString()).read(result("apply(",
                List.of(new RepositoryResearchPipeline.Anchor("TaskService.java", 2))));
        assertEquals(1, evidence.snippets().size());
        assertTrue(evidence.modelContext().contains("TaskService.java:1-4"));
        assertTrue(evidence.modelContext().contains("2:   void apply()"));
        assertFalse(evidence.modelContext().contains(repository.toString()));
    }

    @Test
    void rejectsTraversalStaleAnchorAndOversizedEvidence() throws Exception {
        var reader = new RepositoryEvidenceReader(repository.toString());
        assertEquals("EVIDENCE_INVALID_ANCHOR", assertThrows(RepositoryEvidenceReader.EvidenceFailure.class,
                () -> reader.read(result("apply(", List.of(new RepositoryResearchPipeline.Anchor("../outside.java", 1))))).getMessage());
        Files.writeString(repository.resolve("TaskService.java"), "class TaskService {}\n");
        assertEquals("EVIDENCE_STALE_ANCHOR", assertThrows(RepositoryEvidenceReader.EvidenceFailure.class,
                () -> reader.read(result("apply(", List.of(new RepositoryResearchPipeline.Anchor("TaskService.java", 1))))).getMessage());
        Files.writeString(repository.resolve("TaskService.java"), "apply(" + "x".repeat(3_000), StandardCharsets.UTF_8);
        assertEquals("EVIDENCE_LIMIT", assertThrows(RepositoryEvidenceReader.EvidenceFailure.class,
                () -> reader.read(result("apply(", List.of(new RepositoryResearchPipeline.Anchor("TaskService.java", 1))))).getMessage());
    }

    @Test
    void limitsAnchorsAndMarksEvidenceTruncated() throws Exception {
        Files.writeString(repository.resolve("TaskService.java"), "apply(\n");
        var anchors = java.util.stream.IntStream.range(0, 8)
                .mapToObj(ignored -> new RepositoryResearchPipeline.Anchor("TaskService.java", 1)).toList();
        var evidence = new RepositoryEvidenceReader(repository.toString()).read(result("apply(", anchors));
        assertEquals(6, evidence.snippets().size());
        assertTrue(evidence.truncated());
    }

    @Test
    void skipsUnsupportedAnchorBeforeValidSource() throws Exception {
        Files.writeString(repository.resolve("README.md"), "apply(\n");
        Files.writeString(repository.resolve("TaskService.java"), "class TaskService {\n  void apply() {}\n}\n");
        var evidence = new RepositoryEvidenceReader(repository.toString()).read(result("apply(", List.of(
                new RepositoryResearchPipeline.Anchor("README.md", 1),
                new RepositoryResearchPipeline.Anchor("TaskService.java", 2))));
        assertEquals(1, evidence.snippets().size());
        assertEquals("TaskService.java", evidence.snippets().getFirst().relativePath());
        assertTrue(evidence.truncated());
        assertFalse(evidence.modelContext().contains("README.md"));
    }

    @Test
    void failsWhenNoSourceAnchorIsUsable() throws Exception {
        Files.writeString(repository.resolve("README.md"), "apply(\n");
        var reader = new RepositoryEvidenceReader(repository.toString());
        assertEquals("EVIDENCE_UNAVAILABLE", assertThrows(RepositoryEvidenceReader.EvidenceFailure.class,
                () -> reader.read(result("apply(", List.of(
                        new RepositoryResearchPipeline.Anchor("README.md", 1))))).getMessage());
    }

    @Test
    void rejectsSymlinkEscapeWhenFilesystemAllowsLinkCreation() throws Exception {
        Path outside = Files.createTempFile("outside-evidence", ".java");
        try {
            Files.writeString(outside, "apply(\n");
            Path link = repository.resolve("Link.java");
            try { Files.createSymbolicLink(link, outside); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException | SecurityException e) {
                org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable");
            }
            var reader = new RepositoryEvidenceReader(repository.toString());
            assertEquals("EVIDENCE_UNAVAILABLE", assertThrows(RepositoryEvidenceReader.EvidenceFailure.class,
                    () -> reader.read(result("apply(", List.of(new RepositoryResearchPipeline.Anchor("Link.java", 1))))).getMessage());
        } finally { Files.deleteIfExists(outside); }
    }

    private static RepositoryResearchPipeline.AgentResult result(String query, List<RepositoryResearchPipeline.Anchor> anchors) {
        return new RepositoryResearchPipeline.AgentResult(new RepositoryResearchPipeline.Result(
                "COMPLETED", 3, anchors.size(), 1, false, new ObjectMapper().createObjectNode()), query, anchors);
    }
}
