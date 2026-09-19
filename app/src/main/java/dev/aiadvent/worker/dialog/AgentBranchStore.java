package dev.aiadvent.worker.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AgentBranchStore {
    private final Path directory;
    private final ObjectMapper json;

    @Autowired
    public AgentBranchStore(@Value("${mentor.agent-branches.directory:docs/local/agent-branches}") Path directory,
                     ObjectMapper json) {
        this.directory = directory.toAbsolutePath().normalize();
        this.json = json;
    }

    public synchronized Checkpoint createCheckpoint(UUID dialogId, List<ConversationContext.Message> baseHistory)
            throws IOException {
        ConversationContext.validate(baseHistory);
        Topology topology = load(dialogId).orElse(new Topology(List.of()));
        Checkpoint checkpoint = new Checkpoint(UUID.randomUUID().toString(), List.copyOf(baseHistory), List.of());
        save(dialogId, new Topology(append(topology.checkpoints(), checkpoint)));
        return checkpoint;
    }

    public synchronized Branch createBranch(UUID dialogId, String checkpointId) throws IOException {
        Topology topology = requireTopology(dialogId);
        Checkpoint checkpoint = topology.checkpoints().stream()
                .filter(candidate -> candidate.id().equals(checkpointId))
                .findFirst()
                .orElseThrow(() -> new BranchNotFoundException("Checkpoint not found: " + checkpointId));
        Branch branch = new Branch(UUID.randomUUID().toString(), checkpoint.id(), checkpoint.baseHistory());
        var checkpoints = new ArrayList<Checkpoint>();
        for (Checkpoint candidate : topology.checkpoints()) {
            checkpoints.add(candidate.id().equals(checkpoint.id())
                    ? new Checkpoint(candidate.id(), candidate.baseHistory(), append(candidate.branches(), branch))
                    : candidate);
        }
        save(dialogId, new Topology(checkpoints));
        return branch;
    }

    public synchronized List<Branch> branches(UUID dialogId) throws IOException {
        return load(dialogId).orElse(new Topology(List.of())).checkpoints().stream()
                .flatMap(checkpoint -> checkpoint.branches().stream()).toList();
    }

    public synchronized List<Checkpoint> checkpoints(UUID dialogId) throws IOException {
        return load(dialogId).orElse(new Topology(List.of())).checkpoints();
    }

    public synchronized void delete(UUID dialogId) throws IOException {
        Files.deleteIfExists(path(dialogId));
    }

    public synchronized List<ConversationContext.Message> loadBranch(UUID dialogId, String branchId) throws IOException {
        return findBranch(dialogId, branchId).orElseThrow(() ->
                new BranchNotFoundException("Branch not found: " + branchId)).history();
    }

    public synchronized void saveBranch(UUID dialogId, String branchId,
                                 List<ConversationContext.Message> completed) throws IOException {
        ConversationContext.validate(completed);
        Topology topology = requireTopology(dialogId);
        var checkpoints = new ArrayList<Checkpoint>();
        boolean found = false;
        for (Checkpoint checkpoint : topology.checkpoints()) {
            var branches = new ArrayList<Branch>();
            for (Branch branch : checkpoint.branches()) {
                if (!branch.id().equals(branchId)) {
                    branches.add(branch);
                    continue;
                }
                if (completed.size() != branch.history().size() + 2
                        || !completed.subList(0, branch.history().size()).equals(branch.history())) {
                    throw new IllegalArgumentException("Completed turn does not extend the branch history.");
                }
                branches.add(new Branch(branch.id(), branch.checkpointId(), List.copyOf(completed)));
                found = true;
            }
            checkpoints.add(new Checkpoint(checkpoint.id(), checkpoint.baseHistory(), branches));
        }
        if (!found) {
            throw new BranchNotFoundException("Branch not found: " + branchId);
        }
        save(dialogId, new Topology(checkpoints));
    }

    private Optional<Branch> findBranch(UUID dialogId, String branchId) throws IOException {
        return load(dialogId).orElse(new Topology(List.of())).checkpoints().stream()
                .flatMap(checkpoint -> checkpoint.branches().stream())
                .filter(branch -> branch.id().equals(branchId)).findFirst();
    }

    private Topology requireTopology(UUID dialogId) throws IOException {
        return load(dialogId).orElseThrow(() -> new BranchNotFoundException("No branching topology for dialog."));
    }

    private Optional<Topology> load(UUID dialogId) throws IOException {
        Path path = path(dialogId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            Topology topology = json.readValue(path.toFile(), Topology.class);
            validate(topology);
            return Optional.of(topology);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Agent branch topology is malformed.", exception);
        }
    }

    private void save(UUID dialogId, Topology topology) throws IOException {
        Files.createDirectories(directory);
        Path destination = path(dialogId);
        Path temporary = Files.createTempFile(directory, dialogId.toString(), ".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), topology);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validate(Topology topology) {
        if (topology == null || topology.checkpoints() == null) {
            throw new IllegalArgumentException("Branch topology is empty.");
        }
        var checkpointIds = new HashSet<String>();
        var branchIds = new HashSet<String>();
        for (Checkpoint checkpoint : topology.checkpoints()) {
            if (checkpoint == null || checkpoint.id() == null || checkpoint.id().isBlank()
                    || checkpoint.baseHistory() == null || checkpoint.branches() == null) {
                throw new IllegalArgumentException("Branch checkpoint is invalid.");
            }
            if (!checkpointIds.add(checkpoint.id())) {
                throw new IllegalArgumentException("Branch checkpoint IDs must be unique.");
            }
            ConversationContext.validate(checkpoint.baseHistory());
            for (Branch branch : checkpoint.branches()) {
                if (branch == null || branch.id() == null || branch.id().isBlank()
                        || !checkpoint.id().equals(branch.checkpointId()) || branch.history() == null
                        || branch.history().size() < checkpoint.baseHistory().size()
                        || !branch.history().subList(0, checkpoint.baseHistory().size()).equals(checkpoint.baseHistory())) {
                    throw new IllegalArgumentException("Branch continuation is invalid.");
                }
                if (!branchIds.add(branch.id())) {
                    throw new IllegalArgumentException("Branch IDs must be unique.");
                }
                ConversationContext.validate(branch.history());
            }
        }
    }

    private Path path(UUID dialogId) {
        Path path = directory.resolve(dialogId + ".json").normalize();
        if (!path.getParent().equals(directory)) {
            throw new IllegalArgumentException("Invalid dialog id.");
        }
        return path;
    }

    private static <T> List<T> append(List<T> values, T value) {
        var result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    record Topology(List<Checkpoint> checkpoints) {
    }

    public record Checkpoint(String id, List<ConversationContext.Message> baseHistory, List<Branch> branches) {
    }

    public record Branch(String id, String checkpointId, List<ConversationContext.Message> history) {
    }

    public static class BranchNotFoundException extends IllegalArgumentException {
        public BranchNotFoundException(String message) {
            super(message);
        }
    }
}
