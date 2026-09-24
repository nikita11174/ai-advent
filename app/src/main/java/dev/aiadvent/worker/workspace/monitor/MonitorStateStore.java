package dev.aiadvent.worker.workspace.monitor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

public final class MonitorStateStore {
    public static final int MAX_BYTES = 65_536;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Set<String> FIELDS = Set.of("schemaVersion", "monitorId", "repositoryBinding",
            "configRevision", "snapshotRevision", "enabled", "intervalSeconds", "nextRunAt",
            "aggregate", "latestDigest", "lastCommand");
    private final Path file;
    private final String binding;

    public MonitorStateStore(Path repository, Path directory) {
        try {
            Path root = repository.toRealPath();
            Path candidate = directory.toAbsolutePath().normalize();
            Path existing = candidate;
            while (!Files.exists(existing)) existing = existing.getParent();
            if (candidate.startsWith(root) || existing.toRealPath().startsWith(root)) {
                throw new IllegalStateException("INVALID_STATE_DIRECTORY");
            }
            Files.createDirectories(candidate);
            Path stateDirectory = candidate.toRealPath();
            if (stateDirectory.startsWith(root)) throw new IllegalStateException("INVALID_STATE_DIRECTORY");
            file = stateDirectory.resolve("state.json");
            binding = sha256(root.toString());
        } catch (IOException e) {
            throw new IllegalStateException("PERSISTENCE_FAILED");
        }
    }

    public MonitorState load() {
        if (!Files.exists(file)) return MonitorState.empty(binding);
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("MONITOR_FAULTED");
            JsonNode tree = JSON.readTree(bytes);
            if (!exact(tree, FIELDS) || !exact(tree.path("aggregate"), Set.of("successCount", "failureCount",
                    "dirtySampleCount", "headTransitionCount", "branchTransitionCount", "firstSuccessAt",
                    "lastSuccessAt", "lastCompletedAt", "lastOutcome", "lastFailureCode", "latestStatus"))
                    || !(tree.path("latestDigest").isNull() || exact(tree.path("latestDigest"),
                    Set.of("snapshotRevision", "generatedAt", "text")))
                    || !(tree.path("lastCommand").isNull() || exact(tree.path("lastCommand"),
                    Set.of("commandId", "fingerprint", "action", "intervalSeconds", "configRevision")))
                    || !(tree.path("aggregate").path("latestStatus").isNull()
                    || exact(tree.path("aggregate").path("latestStatus"),
                    Set.of("repositoryRef", "observedAt", "headState", "branch", "head", "dirty", "changeCounts")))
                    || !(tree.path("aggregate").path("latestStatus").isNull()
                    || tree.path("aggregate").path("latestStatus").path("changeCounts").isNull()
                    || exact(tree.path("aggregate").path("latestStatus").path("changeCounts"),
                    Set.of("staged", "unstaged", "untracked", "conflicted", "submoduleChanged")))
                    || !tree.path("schemaVersion").isIntegralNumber()
                    || !tree.path("configRevision").isIntegralNumber()
                    || !tree.path("snapshotRevision").isIntegralNumber()
                    || !tree.path("enabled").isBoolean()) {
                throw new IllegalStateException("MONITOR_FAULTED");
            }
            MonitorState state = JSON.treeToValue(tree, MonitorState.class);
            validate(state);
            if (!binding.equals(state.repositoryBinding())) throw new IllegalStateException("MONITOR_FAULTED");
            return state;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("MONITOR_FAULTED");
        }
    }

    public void save(MonitorState state) {
        validate(state);
        if (!binding.equals(state.repositoryBinding())) throw new IllegalStateException("PERSISTENCE_FAILED");
        Path temporary = null;
        try {
            byte[] bytes = JSON.writeValueAsBytes(state);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("PERSISTENCE_FAILED");
            temporary = Files.createTempFile(file.getParent(), "monitor-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("PERSISTENCE_FAILED");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private static void validate(MonitorState state) {
        MonitorState.Aggregate a = state == null ? null : state.aggregate();
        if (state == null || state.schemaVersion() != 1 || !MonitorState.ID.equals(state.monitorId())
                || state.repositoryBinding() == null || !state.repositoryBinding().matches("[0-9a-f]{64}")
                || state.configRevision() < 0 || state.snapshotRevision() < state.configRevision()
                || a == null || a.successCount() < 0 || a.failureCount() < 0
                || a.dirtySampleCount() < 0 || a.headTransitionCount() < 0 || a.branchTransitionCount() < 0
                || a.dirtySampleCount() > a.successCount()
                || a.headTransitionCount() > Math.max(0, a.successCount() - 1)
                || a.branchTransitionCount() > Math.max(0, a.successCount() - 1)
                || (a.successCount() == 0) != (a.latestStatus() == null)
                || (a.successCount() == 0) != (a.firstSuccessAt() == null)
                || (a.successCount() == 0) != (a.lastSuccessAt() == null)
                || (a.successCount() + a.failureCount() == 0) != (a.lastCompletedAt() == null)
                || !java.util.Set.of("SUCCESS", "FAILURE", "NONE").contains(a.lastOutcome() == null ? "NONE" : a.lastOutcome())
                || (a.failureCount() == 0 && a.lastFailureCode() != null)
                || ("FAILURE".equals(a.lastOutcome()) && a.lastFailureCode() == null)
                || (a.lastFailureCode() != null && !Set.of("GIT_FAILURE", "GIT_TIMEOUT",
                "GIT_STATUS_INCOMPLETE", "GIT_INTERRUPTED", "INVALID_REPOSITORY").contains(a.lastFailureCode()))
                || (a.successCount() + a.failureCount() == 0) != (a.lastOutcome() == null)
                || (a.successCount() + a.failureCount() == 0) != (state.latestDigest() == null)
                || (a.latestStatus() != null && (!"workspace".equals(a.latestStatus().repositoryRef())
                || a.latestStatus().observedAt() == null || a.latestStatus().headState() == null
                || (a.latestStatus().branch() != null && a.latestStatus().branch().getBytes(StandardCharsets.UTF_8).length > 512)
                || (a.latestStatus().head() != null && !a.latestStatus().head().matches("(?i:[0-9a-f]{40}|[0-9a-f]{64})"))
                || (a.latestStatus().changeCounts() != null && (a.latestStatus().changeCounts().staged() < 0
                || a.latestStatus().changeCounts().unstaged() < 0 || a.latestStatus().changeCounts().untracked() < 0
                || a.latestStatus().changeCounts().conflicted() < 0 || a.latestStatus().changeCounts().submoduleChanged() < 0))))
                || (state.enabled() && (state.intervalSeconds() == null || state.nextRunAt() == null))
                || (!state.enabled() && state.nextRunAt() != null)
                || (state.intervalSeconds() != null && (state.intervalSeconds() < 10 || state.intervalSeconds() > 86_400))
                || (state.lastCommand() != null && (!isUuid(state.lastCommand().commandId())
                || !state.lastCommand().fingerprint().matches("[0-9a-f]{64}")
                || !java.util.Set.of("START", "STOP").contains(state.lastCommand().action())
                || state.lastCommand().configRevision() != state.configRevision()))
                || (state.latestDigest() != null && (state.latestDigest().text() == null
                || state.latestDigest().generatedAt() == null
                || !state.latestDigest().generatedAt().equals(a.lastCompletedAt())
                || !state.latestDigest().text().equals(RepositoryDigestFormatter.format(a))
                || state.latestDigest().snapshotRevision() > state.snapshotRevision()
                || state.latestDigest().text().getBytes(StandardCharsets.UTF_8).length > 2048))) {
            throw new IllegalStateException("MONITOR_FAULTED");
        }
    }

    private static boolean exact(JsonNode node, Set<String> names) {
        return node != null && node.isObject() && node.size() == names.size()
                && node.properties().stream().map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet()).equals(names);
    }

    private static boolean isUuid(String text) {
        try { return java.util.UUID.fromString(text).toString().equals(text); }
        catch (IllegalArgumentException | NullPointerException e) { return false; }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("PERSISTENCE_FAILED");
        }
    }
}
