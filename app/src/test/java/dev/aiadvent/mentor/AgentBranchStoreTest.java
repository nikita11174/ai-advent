package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentBranchStoreTest {
    @TempDir
    Path directory;

    @Test
    void createsImmutableCheckpointAndIndependentBranchesAcrossRestart() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID dialogId = UUID.randomUUID();
        var base = List.of(new ConversationContext.Message("system", "instruction"),
                new ConversationContext.Message("user", "shared"),
                new ConversationContext.Message("assistant", "ack"));
        var first = new AgentBranchStore(directory, json);
        var checkpoint = first.createCheckpoint(dialogId, base);
        var branchA = first.createBranch(dialogId, checkpoint.id());
        var branchB = first.createBranch(dialogId, checkpoint.id());
        var branchAHistory = List.of(base.get(0), base.get(1), base.get(2),
                new ConversationContext.Message("user", "PostgreSQL"),
                new ConversationContext.Message("assistant", "A answer"));

        first.saveBranch(dialogId, branchA.id(), branchAHistory);

        var restored = new AgentBranchStore(directory, json);
        assertEquals(branchAHistory, restored.loadBranch(dialogId, branchA.id()));
        assertEquals(base, restored.loadBranch(dialogId, branchB.id()));
        assertEquals(2, restored.branches(dialogId).size());
        assertEquals(List.of(checkpoint.id()), restored.checkpoints(dialogId).stream().map(AgentBranchStore.Checkpoint::id).toList());
    }

    @Test
    void rejectsDuplicateCheckpointOrBranchIdsInPersistedTopology() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UUID dialogId = UUID.randomUUID();
        var history = List.of(new ConversationContext.Message("system", "instruction"));
        var branch = new AgentBranchStore.Branch("branch", "checkpoint", history);
        var duplicateCheckpoint = new AgentBranchStore.Topology(List.of(
                new AgentBranchStore.Checkpoint("checkpoint", history, List.of(branch)),
                new AgentBranchStore.Checkpoint("checkpoint", history, List.of())));
        json.writeValue(directory.resolve(dialogId + ".json").toFile(), duplicateCheckpoint);

        assertThrows(IOException.class, () -> new AgentBranchStore(directory, json).checkpoints(dialogId));

        var firstCheckpoint = new AgentBranchStore.Checkpoint("first", history,
                List.of(new AgentBranchStore.Branch("branch", "first", history)));
        var secondCheckpoint = new AgentBranchStore.Checkpoint("second", history,
                List.of(new AgentBranchStore.Branch("branch", "second", history)));
        var duplicateBranch = new AgentBranchStore.Topology(List.of(firstCheckpoint, secondCheckpoint));
        json.writeValue(directory.resolve(dialogId + ".json").toFile(), duplicateBranch);

        assertThrows(IOException.class, () -> new AgentBranchStore(directory, json).branches(dialogId));
    }
}
