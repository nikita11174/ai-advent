package dev.aiadvent.worker.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogStoreTest {
    @TempDir
    Path directory;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsUpdatesAndLoadsAcrossStoreInstances() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);
        var created = store.create();
        var state = json.createObjectNode().put("value", "saved");

        store.update(created.id(), new DialogStore.DialogUpdate("Payment review", state));
        var restored = new DialogStore(directory, json, clock).load(created.id());

        assertEquals("Payment review", restored.title());
        assertEquals("saved", restored.state().path("value").textValue());
    }

    @Test
    void listsMostRecentlyUpdatedFirst() throws Exception {
        DialogStore firstClock = new DialogStore(directory, json, clock);
        var first = firstClock.create();
        Clock later = Clock.fixed(Instant.parse("2026-09-03T11:00:00Z"), ZoneOffset.UTC);
        DialogStore laterStore = new DialogStore(directory, json, later);
        var second = laterStore.create();

        assertEquals(second.id(), laterStore.list().get(0).id());
        assertEquals(first.id(), laterStore.list().get(1).id());
    }

    @Test
    void updatesOnlyProfileSelectionWithoutReplacingOtherDialogState() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);
        var created = store.create();
        UUID profileId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        var state = json.readTree("""
                {"exchanges":[{"input":"newer"}],"ui":{"selectedAgentModelKey":"MEDIUM"}}
                """);
        store.update(created.id(), new DialogStore.DialogUpdate("Newer title", state));
        store.updateTaskSelection(created.id(), taskId);

        var updated = store.updateProfileSelection(created.id(), profileId);

        assertEquals("Newer title", updated.title());
        assertEquals("newer", updated.state().path("exchanges").get(0).path("input").textValue());
        assertEquals("MEDIUM", updated.state().path("ui").path("selectedAgentModelKey").textValue());
        assertEquals(taskId.toString(), updated.state().path("ui").path("appliedTaskId").textValue());
        assertEquals(profileId.toString(), updated.state().path("ui").path("selectedProfileId").textValue());
    }

    @Test
    void updatesAndClearsOnlyTaskSelectionWithoutReplacingOtherDialogState() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);
        var created = store.create();
        UUID taskId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        var state = json.readTree("""
                {"exchanges":[{"input":"newer"}],"ui":{"selectedAgentModelKey":"MEDIUM"}}
                """);
        store.update(created.id(), new DialogStore.DialogUpdate("Newer title", state));
        store.updateProfileSelection(created.id(), profileId);

        var selected = store.updateTaskSelection(created.id(), taskId);
        var cleared = store.updateTaskSelection(created.id(), null);

        assertEquals("newer", selected.state().path("exchanges").get(0).path("input").textValue());
        assertEquals("MEDIUM", selected.state().path("ui").path("selectedAgentModelKey").textValue());
        assertEquals(profileId.toString(), selected.state().path("ui").path("selectedProfileId").textValue());
        assertEquals(taskId.toString(), selected.state().path("ui").path("appliedTaskId").textValue());
        assertTrue(cleared.state().path("ui").path("appliedTaskId").isNull());
    }

    @Test
    void genericUpdatePreservesNewerTaskAndProfileSelectionsWhileSavingOtherState() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);
        var created = store.create();
        UUID newProfile = UUID.randomUUID();
        UUID newTask = UUID.randomUUID();
        store.updateProfileSelection(created.id(), newProfile);
        store.updateTaskSelection(created.id(), newTask);
        var staleState = json.readTree("""
                {"exchanges":[{"input":"saved"}],"ui":{"selectedProfileId":"old-profile","appliedTaskId":"old-task","selectedAgentModelKey":"MEDIUM"}}
                """);

        var updated = store.update(created.id(), new DialogStore.DialogUpdate("Saved title", staleState));

        assertEquals("Saved title", updated.title());
        assertEquals("saved", updated.state().path("exchanges").get(0).path("input").textValue());
        assertEquals("MEDIUM", updated.state().path("ui").path("selectedAgentModelKey").textValue());
        assertEquals(newProfile.toString(), updated.state().path("ui").path("selectedProfileId").textValue());
        assertEquals(newTask.toString(), updated.state().path("ui").path("appliedTaskId").textValue());
    }

    @Test
    void rejectsMissingAndUnsafeIds() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);

        assertThrows(IllegalArgumentException.class, () -> store.load("../../secret"));
        assertThrows(DialogStore.DialogNotFoundException.class,
                () -> store.load("00000000-0000-0000-0000-000000000000"));
    }
}
