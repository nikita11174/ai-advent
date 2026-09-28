package dev.aiadvent.worker.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryReportReadTest {
    @TempDir Path temporary;
    private static final String REF = "12345678-1234-1234-1234-123456789abc";

    @Test void readsOnlyBoundedReportUnderConfiguredDirectory() throws Exception {
        Path reports = Files.createDirectory(temporary.resolve("reports"));
        Path file = reports.resolve(REF + ".md");
        Files.writeString(file, "# Repository search\n- src/A.java:7\n");
        assertEquals("# Repository search\n- src/A.java:7\n", RepositoryResearch.readReport(reports, REF));
        assertEquals("INVALID_REPORT_REF", assertThrows(IllegalStateException.class,
                () -> RepositoryResearch.readReport(reports, "../secret")).getMessage());
        assertEquals("REPORT_NOT_FOUND", assertThrows(IllegalStateException.class,
                () -> RepositoryResearch.readReport(reports, "00000000-0000-0000-0000-000000000000")).getMessage());
        Files.writeString(file, "x".repeat(8193));
        assertEquals("REPORT_UNAVAILABLE", assertThrows(IllegalStateException.class,
                () -> RepositoryResearch.readReport(reports, REF)).getMessage());
    }

    @Test void rejectsLinkedReportWhenFilesystemSupportsLinks() throws Exception {
        Path reports = Files.createDirectory(temporary.resolve("reports"));
        Path outside = temporary.resolve("outside.md");
        Files.writeString(outside, "private");
        try { Files.createSymbolicLink(reports.resolve(REF + ".md"), outside); }
        catch (UnsupportedOperationException | java.io.IOException | SecurityException e) { return; }
        assertEquals("REPORT_NOT_FOUND", assertThrows(IllegalStateException.class,
                () -> RepositoryResearch.readReport(reports, REF)).getMessage());
    }
}
