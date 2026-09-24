package dev.aiadvent.worker.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryResearchFailureTest {
    @TempDir Path temp;

    @Test void startFailureAndTimeoutReturnStableCodes() {
        var failed = new RepositoryResearch(temp, temp.resolveSibling("day19-reports-failed"),
                Duration.ofMillis(100), command -> { throw new IOException(); });
        assertEquals("SEARCH_FAILED", assertThrows(IllegalStateException.class,
                () -> failed.search("marker", 1)).getMessage());

        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        var slow = new RepositoryResearch(temp, temp.resolveSibling("day19-reports-slow"),
                Duration.ofMillis(500), command -> new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                SlowGitProcessFixtureMain.class.getName(), temp.resolve("started").toString()).start());
        assertEquals("SEARCH_TIMEOUT", assertThrows(IllegalStateException.class,
                () -> slow.search("marker", 1)).getMessage());
    }
}
