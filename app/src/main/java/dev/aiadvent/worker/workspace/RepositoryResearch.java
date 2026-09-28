package dev.aiadvent.worker.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RepositoryResearch {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BYTES = 32_768;
    private final Path repository;
    private final Path reports;
    private final Duration timeout;
    private final ProcessStarter starter;

    @FunctionalInterface
    interface ProcessStarter { Process start(ProcessBuilder command) throws IOException; }

    public RepositoryResearch(Path repository, Path reports) {
        this(repository, reports, Duration.ofSeconds(5), ProcessBuilder::start);
    }

    RepositoryResearch(Path repository, Path reports, Duration timeout, ProcessStarter starter) {
        this.timeout = timeout;
        this.starter = starter;
        try {
            this.repository = repository.toRealPath();
            this.reports = reports.toAbsolutePath().normalize();
            if (this.reports.startsWith(this.repository)) throw failure("INVALID_REPORTS_DIRECTORY");
            Files.createDirectories(this.reports);
            if (this.reports.toRealPath().startsWith(this.repository)) throw failure("INVALID_REPORTS_DIRECTORY");
        } catch (IOException e) {
            throw failure("INVALID_REPORTS_DIRECTORY");
        }
    }

    public Map<String, Object> search(String query, int maxResults) {
        if (!validQuery(query) || maxResults < 1 || maxResults > 20) throw failure("INVALID_ARGUMENTS");
        var command = new ProcessBuilder("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
                "-C", repository.toString(), "grep", "-n", "-F", "-I", "--full-name", "-z", "-e", query, "--")
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        var inherited = new LinkedHashMap<>(command.environment());
        command.environment().clear();
        for (var entry : inherited.entrySet()) {
            if (Set.of("PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP")
                    .contains(entry.getKey().toUpperCase(java.util.Locale.ROOT))) command.environment().put(entry.getKey(), entry.getValue());
        }
        command.environment().put("GIT_OPTIONAL_LOCKS", "0");
        Process process;
        try { process = starter.start(command); }
        catch (IOException e) { throw failure("SEARCH_FAILED"); }
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            process.getOutputStream().close();
            FutureTask<byte[]> reader = new FutureTask<>(() -> process.getInputStream().readNBytes(MAX_BYTES + 1));
            Thread.ofVirtual().start(reader);
            byte[] bytes = reader.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (bytes.length > MAX_BYTES) throw failure("SEARCH_INCOMPLETE");
            if (!process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                throw failure("SEARCH_TIMEOUT");
            if (process.exitValue() > 1) throw failure("SEARCH_FAILED");
            if ((process.exitValue() == 0 && bytes.length == 0)
                    || (process.exitValue() == 1 && bytes.length != 0)) throw failure("SEARCH_INCOMPLETE");
            String output = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            List<Map<String, Object>> all = new ArrayList<>();
            if (!output.isEmpty()) {
                if (!output.endsWith("\n")) throw failure("SEARCH_INCOMPLETE");
                for (String row : output.substring(0, output.length() - 1).split("\n", -1)) {
                    int nul = row.indexOf('\0');
                    int lineSeparator = row.indexOf('\0', nul + 1);
                    if (nul < 1 || lineSeparator < 0) throw failure("SEARCH_INCOMPLETE");
                    String path = row.substring(0, nul);
                    int line;
                    try { line = Integer.parseInt(row.substring(nul + 1, lineSeparator)); }
                    catch (NumberFormatException e) { throw failure("SEARCH_INCOMPLETE"); }
                    if (!validPath(path) || line < 1) throw failure("SEARCH_INCOMPLETE");
                    all.add(Map.of("relativePath", path, "line", line));
                }
            }
            all.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("relativePath"))
                    .thenComparingInt(m -> (Integer) m.get("line")));
            boolean truncated = all.size() > maxResults;
            return Map.of("query", query, "observedAt", Instant.now().toString(),
                    "matchCount", Math.min(all.size(), maxResults), "truncated", truncated,
                    "matches", List.copyOf(all.subList(0, Math.min(all.size(), maxResults))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("SEARCH_TIMEOUT");
        } catch (java.util.concurrent.TimeoutException e) {
            throw failure("SEARCH_TIMEOUT");
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw failure("SEARCH_FAILED");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                try { if (!process.waitFor(1, TimeUnit.SECONDS)) throw failure("SEARCH_FAILED"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw failure("SEARCH_FAILED"); }
            }
        }
    }

    public static Map<String, Object> summarize(JsonNode search) {
        validateSearch(search);
        List<Map<String, Object>> evidence = new ArrayList<>();
        search.path("matches").forEach(match -> evidence.add(Map.of("relativePath", match.path("relativePath").textValue(),
                "line", match.path("line").intValue())));
        int files = (int) evidence.stream().map(e -> e.get("relativePath")).distinct().count();
        String markdown = markdown(search.path("query").textValue(), evidence, search.path("truncated").booleanValue());
        return Map.of("query", search.path("query").textValue(), "matchesSeen", evidence.size(),
                "filesMatched", files, "truncated", search.path("truncated").booleanValue(),
                "evidence", evidence, "summaryMarkdown", markdown);
    }

    public synchronized Map<String, Object> save(String operationId, JsonNode summary) {
        String ref = reportRef(operationId);
        validateSummary(summary);
        String markdown = summary.path("summaryMarkdown").textValue();
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        Path temporary = reports.resolve(ref + ".tmp");
        Path file = reports.resolve(ref + ".md");
        boolean temporaryCreated = false;
        boolean commitAttempted = false;
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!reports.toRealPath().equals(reports)) throw failure("SAVE_FAILED");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                try { return existing(file, ref, bytes, hash); }
                catch (IOException e) { throw failure("SAVE_OUTCOME_UNKNOWN"); }
            }
            if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) throw failure("SAVE_OUTCOME_UNKNOWN");
            Files.createFile(temporary);
            temporaryCreated = true;
            Files.write(temporary, bytes, StandardOpenOption.WRITE);
            commitAttempted = true;
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            return existing(file, ref, bytes, hash);
        } catch (IllegalStateException e) {
            if ("SAVE_OUTCOME_UNKNOWN".equals(e.getMessage())
                    || "REPORT_CONFLICT".equals(e.getMessage()) && !commitAttempted) throw e;
            throw failure(commitAttempted ? "SAVE_OUTCOME_UNKNOWN" : "SAVE_FAILED");
        } catch (Exception e) { throw failure(commitAttempted ? "SAVE_OUTCOME_UNKNOWN" : "SAVE_FAILED"); }
        finally { if (temporaryCreated) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    public static String reportRef(String operationId) {
        try {
            UUID id = UUID.fromString(operationId);
            if (!id.toString().equals(operationId)) throw new IllegalArgumentException();
            return UUID.nameUUIDFromBytes(("repository-report:" + operationId)
                    .getBytes(StandardCharsets.UTF_8)).toString();
        } catch (RuntimeException e) { throw failure("INVALID_ARGUMENTS"); }
    }

    public static String readReport(Path reports, String ref) {
        if (ref == null || !ref.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw failure("INVALID_REPORT_REF");
        Path directory = reports.toAbsolutePath().normalize();
        Path file = directory.resolve(ref + ".md");
        try {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !directory.toRealPath().equals(directory)) throw failure("REPORT_UNAVAILABLE");
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw failure("REPORT_NOT_FOUND");
            byte[] bytes;
            try (var stream = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = stream.readNBytes(8193);
            }
            if (bytes.length == 0 || bytes.length > 8192) throw failure("REPORT_UNAVAILABLE");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IOException e) { throw failure("REPORT_UNAVAILABLE"); }
    }

    private static Map<String, Object> existing(Path file, String ref, byte[] expected, String hash) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 8192)
            throw failure("REPORT_CONFLICT");
        byte[] actual;
        try (var stream = Files.newInputStream(file)) { actual = stream.readNBytes(8193); }
        if (actual.length > 8192) throw failure("REPORT_CONFLICT");
        if (!MessageDigest.isEqual(actual, expected)) throw failure("REPORT_CONFLICT");
        return Map.of("reportRef", ref, "createdAt", Files.getLastModifiedTime(file).toInstant().toString(),
                "bytesWritten", actual.length, "contentHash", hash);
    }

    public static void validateSearch(JsonNode node) {
        if (!exact(node, Set.of("query", "observedAt", "matchCount", "truncated", "matches"))
                || !validQuery(node.path("query").asText(null)) || !date(node.path("observedAt"))
                || !node.path("truncated").isBoolean() || !node.path("matches").isArray()
                || node.path("matches").size() > 20 || !number(node.path("matchCount"), node.path("matches").size()))
            throw failure("INVALID_SEARCH_RESULT");
        String lastPath = ""; int lastLine = 0;
        for (JsonNode match : node.path("matches")) {
            if (!exact(match, Set.of("relativePath", "line"))
                    || !validPath(match.path("relativePath").asText(null))
                    || !match.path("line").canConvertToInt() || match.path("line").intValue() < 1)
                throw failure("INVALID_SEARCH_RESULT");
            String path = match.path("relativePath").textValue(); int line = match.path("line").intValue();
            if (path.compareTo(lastPath) < 0 || path.equals(lastPath) && line <= lastLine)
                throw failure("INVALID_SEARCH_RESULT");
            lastPath = path; lastLine = line;
        }
    }

    public static void validateSummary(JsonNode node) {
        if (!exact(node, Set.of("query", "matchesSeen", "filesMatched", "truncated", "evidence", "summaryMarkdown"))
                || !validQuery(node.path("query").asText(null)) || !node.path("truncated").isBoolean()
                || !node.path("evidence").isArray() || node.path("evidence").size() > 20
                || !number(node.path("matchesSeen"), node.path("evidence").size())
                || !node.path("summaryMarkdown").isTextual()) throw failure("INVALID_SUMMARY_RESULT");
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (JsonNode e : node.path("evidence")) {
            if (!exact(e, Set.of("relativePath", "line")) || !validPath(e.path("relativePath").asText(null))
                    || !e.path("line").canConvertToInt() || e.path("line").intValue() < 1)
                throw failure("INVALID_SUMMARY_RESULT");
            evidence.add(Map.of("relativePath", e.path("relativePath").textValue(), "line", e.path("line").intValue()));
        }
        int files = (int) evidence.stream().map(e -> e.get("relativePath")).distinct().count();
        if (!number(node.path("filesMatched"), files)
                || !markdown(node.path("query").textValue(), evidence, node.path("truncated").booleanValue())
                .equals(node.path("summaryMarkdown").textValue())) throw failure("INVALID_SUMMARY_RESULT");
    }

    public static void validateReceipt(JsonNode node) {
        if (!exact(node, Set.of("reportRef", "createdAt", "bytesWritten", "contentHash"))
                || !node.path("reportRef").isTextual() || !node.path("reportRef").textValue()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !date(node.path("createdAt")) || !node.path("bytesWritten").canConvertToInt()
                || node.path("bytesWritten").intValue() < 1 || node.path("bytesWritten").intValue() > 8192
                || !node.path("contentHash").isTextual() || !node.path("contentHash").textValue().matches("[0-9a-f]{64}"))
            throw failure("INVALID_SAVE_RESULT");
    }

    private static String markdown(String query, List<Map<String, Object>> evidence, boolean truncated) {
        StringBuilder out = new StringBuilder("# Repository search\n\nQuery: ").append(JSON.valueToTree(query).toString())
                .append("\nMatches: ").append(evidence.size()).append("\nFiles: ")
                .append(evidence.stream().map(e -> e.get("relativePath")).distinct().count())
                .append("\nTruncated: ").append(truncated).append("\n\n");
        for (var item : evidence) out.append("- ").append(item.get("relativePath")).append(':')
                .append(item.get("line")).append('\n');
        if (out.toString().getBytes(StandardCharsets.UTF_8).length > 8192) throw failure("RESULT_LIMIT");
        return out.toString();
    }

    private static boolean exact(JsonNode node, Set<String> keys) {
        return node.isObject() && node.size() == keys.size()
                && java.util.stream.StreamSupport.stream(node.properties().spliterator(), false)
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).equals(keys);
    }
    private static boolean number(JsonNode node, int expected) { return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == expected; }
    private static boolean date(JsonNode node) {
        if (!node.isTextual()) return false;
        try { Instant.parse(node.textValue()); return true; } catch (RuntimeException e) { return false; }
    }
    private static boolean validQuery(String s) { return s != null && !s.isBlank() && s.length() <= 100 && s.chars().noneMatch(c -> c < 32 || c == 127); }
    private static boolean validPath(String s) { return s != null && !s.isBlank() && s.length() <= 240 && !s.startsWith("/")
            && !s.contains("\\") && !s.contains("..") && s.chars().noneMatch(c -> c < 32 || c == 127); }
    private static IllegalStateException failure(String code) { return new IllegalStateException(code); }
}
