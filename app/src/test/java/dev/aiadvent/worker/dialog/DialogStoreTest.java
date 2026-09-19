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
        var state = json.readTree("""
                {"exchanges":[{"input":"newer"}],"ui":{"selectedAgentModelKey":"MEDIUM","appliedTaskId":"task"}}
                """);
        store.update(created.id(), new DialogStore.DialogUpdate("Newer title", state));

        var updated = store.updateProfileSelection(created.id(), profileId);

        assertEquals("Newer title", updated.title());
        assertEquals("newer", updated.state().path("exchanges").get(0).path("input").textValue());
        assertEquals("MEDIUM", updated.state().path("ui").path("selectedAgentModelKey").textValue());
        assertEquals("task", updated.state().path("ui").path("appliedTaskId").textValue());
        assertEquals(profileId.toString(), updated.state().path("ui").path("selectedProfileId").textValue());
    }

    @Test
    void rejectsMissingAndUnsafeIds() throws Exception {
        DialogStore store = new DialogStore(directory, json, clock);

        assertThrows(IllegalArgumentException.class, () -> store.load("../../secret"));
        assertThrows(DialogStore.DialogNotFoundException.class,
                () -> store.load("00000000-0000-0000-0000-000000000000"));
    }
}
