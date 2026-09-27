package dev.aiadvent.worker.agent;

import com.fasterxml.jackson.databind.JsonNode;
import dev.aiadvent.worker.mcp.RepositoryResearchPipeline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public final class RepositoryEvidenceReader {
    private static final int MAX_ANCHORS = 6;
    private static final int WINDOW = 10;
    private static final int MAX_FILE_BYTES = 131_072;
    private static final int MAX_SNIPPET_BYTES = 2_500;
    private static final int MAX_TOTAL_BYTES = 10_000;
    private static final Set<String> SOURCE_EXTENSIONS = Set.of("java", "ts", "html", "css", "js", "xml", "py", "go");
    private final String repository;

    public RepositoryEvidenceReader(@Value("${mentor.git-status-tool.repository:}") String repository) {
        this.repository = repository;
    }

    public Evidence read(RepositoryResearchPipeline.AgentResult research) {
        if (repository.isBlank() || !"COMPLETED".equals(research.result().status()))
            throw new EvidenceFailure("EVIDENCE_UNAVAILABLE");
        Path root;
        try { root = Path.of(repository).toRealPath(); }
        catch (Exception e) { throw new EvidenceFailure("EVIDENCE_UNAVAILABLE"); }
        var snippets = new ArrayList<Snippet>();
        int totalBytes = 0;
        for (var anchor : research.anchors()) {
            if (snippets.size() == MAX_ANCHORS) break;
            String relative = anchor.relativePath();
            if (relative == null || relative.isBlank() || relative.length() > 240 || relative.startsWith("/")
                    || relative.contains("\\") || relative.contains(":") || relative.contains("..") || anchor.line() < 1
                    || relative.chars().anyMatch(c -> c < 32 || c == 127)) throw new EvidenceFailure("EVIDENCE_INVALID_ANCHOR");
            int dot = relative.lastIndexOf('.');
            if (dot < 0 || !SOURCE_EXTENSIONS.contains(relative.substring(dot + 1))) continue;
            Path file;
            try { file = root.resolve(relative).normalize(); }
            catch (java.nio.file.InvalidPathException e) { throw new EvidenceFailure("EVIDENCE_INVALID_ANCHOR"); }
            if (!file.startsWith(root)) throw new EvidenceFailure("EVIDENCE_INVALID_ANCHOR");
            try {
                if (!file.toRealPath().startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(file) > MAX_FILE_BYTES)
                    throw new EvidenceFailure("EVIDENCE_UNAVAILABLE");
                byte[] bytes;
                try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
                }
                if (bytes.length > MAX_FILE_BYTES) throw new EvidenceFailure("EVIDENCE_UNAVAILABLE");
                for (byte value : bytes) if (value == 0) throw new EvidenceFailure("EVIDENCE_UNSUPPORTED_FILE");
                String content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                String[] lines = content.split("\n", -1);
                if (anchor.line() > lines.length) throw new EvidenceFailure("EVIDENCE_STALE_ANCHOR");
                if (!lines[anchor.line() - 1].contains(research.query())) throw new EvidenceFailure("EVIDENCE_STALE_ANCHOR");
                int start = Math.max(1, anchor.line() - WINDOW);
                int end = Math.min(lines.length, anchor.line() + WINDOW);
                StringBuilder excerpt = new StringBuilder();
                for (int line = start; line <= end; line++) {
                    excerpt.append(line).append(": ").append(lines[line - 1].replace("\r", "")).append('\n');
                }
                int length = excerpt.toString().getBytes(StandardCharsets.UTF_8).length;
                if (length > MAX_SNIPPET_BYTES || totalBytes + length > MAX_TOTAL_BYTES)
                    throw new EvidenceFailure("EVIDENCE_LIMIT");
                totalBytes += length;
                snippets.add(new Snippet(relative, start, end, excerpt.toString()));
            } catch (IOException | SecurityException e) {
                throw new EvidenceFailure("EVIDENCE_UNAVAILABLE");
            }
        }
        if (snippets.isEmpty()) throw new EvidenceFailure("EVIDENCE_UNAVAILABLE");
        return new Evidence(research.query(), research.result().matchesSeen(), research.result().filesMatched(),
                research.result().truncated() || research.anchors().size() > snippets.size(),
                List.copyOf(snippets), totalBytes);
    }

    public Evidence readSearch(JsonNode search) {
        var anchors = new ArrayList<RepositoryResearchPipeline.Anchor>();
        search.path("matches").forEach(match -> anchors.add(new RepositoryResearchPipeline.Anchor(
                match.path("relativePath").textValue(), match.path("line").intValue())));
        var result = new RepositoryResearchPipeline.Result("COMPLETED", 1, search.path("matchCount").intValue(),
                (int) anchors.stream().map(RepositoryResearchPipeline.Anchor::relativePath).distinct().count(),
                search.path("truncated").asBoolean(), null);
        return read(new RepositoryResearchPipeline.AgentResult(result, search.path("query").textValue(), anchors));
    }

    public record Snippet(String relativePath, int startLine, int endLine, String text) { }
    public record Evidence(String query, int matchesSeen, int filesMatched, boolean truncated,
                           List<Snippet> snippets, int bytes) {
        public String modelContext() {
            StringBuilder out = new StringBuilder("Repository source excerpts for this turn:\n")
                    .append("Query: ").append(query).append("; matches: ").append(matchesSeen)
                    .append("; files: ").append(filesMatched).append("; truncated: ").append(truncated).append('\n');
            for (Snippet snippet : snippets) out.append("\n").append(snippet.relativePath()).append(':')
                    .append(snippet.startLine()).append('-').append(snippet.endLine()).append('\n')
                    .append(snippet.text());
            return out.toString();
        }
    }

    public static final class EvidenceFailure extends RuntimeException {
        public EvidenceFailure(String code) { super(code); }
    }
}
