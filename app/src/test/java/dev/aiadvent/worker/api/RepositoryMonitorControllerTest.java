package dev.aiadvent.worker.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aiadvent.worker.mcp.WorkspaceToolRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryMonitorControllerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkspaceToolRuntime runtime = mock(WorkspaceToolRuntime.class);
    private final RepositoryMonitorController controller = new RepositoryMonitorController(runtime);

    @Test void timeoutIsUnknownAndReadReconcilesWithoutNewCommand() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        when(runtime.startMonitor(eq(300), eq(id), eq(0L))).thenThrow(new IllegalStateException("MONITOR_UNKNOWN"));
        var accepted = controller.start(json.readTree("{\"commandId\":\"" + id + "\",\"expectedRevision\":0}"));
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        assertEquals("UNKNOWN", accepted.getBody().operationStatus());
        assertNull(accepted.getBody().receipt());
        when(runtime.getMonitor()).thenReturn(json.readTree("{\"configRevision\":1,\"lastCommand\":{\"commandId\":\"" + id + "\"}}"));
        assertEquals(id, controller.get().path("lastCommand").path("commandId").asText());
        verify(runtime, times(1)).startMonitor(anyInt(), anyString(), anyLong());
    }

    @Test void omittedIntervalUsesProductionDefault() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        when(runtime.startMonitor(300, id, 2)).thenReturn(json.readTree("{\"receipt\":{\"operationStatus\":\"APPLIED\"},\"view\":{\"intervalSeconds\":300}}"));
        var response = controller.start(json.readTree("{\"commandId\":\"" + id + "\",\"expectedRevision\":2}"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("APPLIED", response.getBody().operationStatus());
        assertEquals(300, response.getBody().view().path("intervalSeconds").asInt());
        verify(runtime).startMonitor(300, id, 2);
    }

    @Test void intervalConfigurationReflectsBackendDemoMode() {
        assertEquals(60, controller.configuration().minimumIntervalSeconds());
        ReflectionTestUtils.setField(controller, "demoIntervals", true);
        assertEquals(10, controller.configuration().minimumIntervalSeconds());
        assertEquals(300, controller.configuration().defaultIntervalSeconds());
        verifyNoInteractions(runtime);
    }

    @Test void rejectsExtraArgumentsAndMalformedTypes() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        for (String body : new String[] {
                "{\"commandId\":\"" + id + "\",\"expectedRevision\":0,\"path\":\".\"}",
                "{\"commandId\":\"" + id + "\",\"expectedRevision\":0,\"intervalSeconds\":10.5}",
                "{\"commandId\":\"bad\",\"expectedRevision\":0}"
        }) {
            assertEquals("INVALID_ARGUMENTS", assertThrows(IllegalStateException.class,
                    () -> controller.start(json.readTree(body))).getMessage());
        }
        verifyNoInteractions(runtime);
    }

    @Test void nullExceptionMessageIsSanitizedAtHttpBoundary() {
        var response = controller.error(new IllegalStateException());
        assertEquals("MCP_CALL_FAILED", response.getBody().error());
    }
}
