package dev.aiadvent.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
    }
}
