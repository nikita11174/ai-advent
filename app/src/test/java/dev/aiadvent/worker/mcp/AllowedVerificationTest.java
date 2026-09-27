package dev.aiadvent.worker.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AllowedVerificationTest {
    @TempDir Path temporary;

    @Test
    void freshRunnerDistinguishesPassFailNoExecutionAndInfraErrorWithSourceIdentity() throws Exception {
        Path source = Path.of("").toAbsolutePath();
        Path fixture = temporary.resolve("fixture");
        Process clone = new ProcessBuilder("git", "clone", "--quiet", "--shared", "--no-checkout",
                source.toString(), fixture.toString()).start();
        assertEquals(0, clone.waitFor());
        Files.copy(source.resolve("pom.xml"), fixture.resolve("pom.xml"));
        Files.createDirectories(fixture.resolve("app/src/main/java"));
        Path testFile = fixture.resolve("app/src/test/java/dev/aiadvent/worker/api/AgentControllerTest.java");
        Files.createDirectories(testFile.getParent());
        AllowedVerification runner = new AllowedVerification(fixture);

        writeTest(testFile, "");
        Map<String, Object> passed = runner.run(AllowedVerification.TEST_ID);
        assertEquals("TEST_PASS", passed.get("status"));
        assertEquals(1, passed.get("tests"));
        assertEquals("SUCCESS", passed.get("toolExecutionStatus"));
        assertEquals(40, ((String) passed.get("head")).length());
        assertEquals(true, passed.get("dirty"));

        writeTest(testFile, "org.junit.jupiter.api.Assertions.fail(\"controlled failure\");");
        Map<String, Object> failed = runner.run(AllowedVerification.TEST_ID);
        assertEquals("TEST_FAIL", failed.get("status"));
        assertEquals("SUCCESS", failed.get("toolExecutionStatus"));
        assertNotEquals(passed.get("sourceHash"), failed.get("sourceHash"));

        writeTest(testFile, "", "@org.junit.jupiter.api.Disabled");
        Map<String, Object> skipped = runner.run(AllowedVerification.TEST_ID);
        assertEquals("TEST_NOT_RUN", skipped.get("status"));
        assertNotEquals(failed.get("sourceHash"), skipped.get("sourceHash"));

        Files.writeString(fixture.resolve("pom.xml"), "invalid pom");
        Map<String, Object> infrastructure = runner.run(AllowedVerification.TEST_ID);
        assertEquals("INFRA_ERROR", infrastructure.get("status"));
        assertEquals("SUCCESS", infrastructure.get("toolExecutionStatus"));
    }

    private static void writeTest(Path file, String body) throws Exception {
        writeTest(file, body, "");
    }

    private static void writeTest(Path file, String body, String annotation) throws Exception {
        Files.writeString(file, """
                package dev.aiadvent.worker.api;
                import org.junit.jupiter.api.Test;
                class AgentControllerTest {
                    %s @Test void missingBranchDoesNotStartRepositoryResearch() { %s }
                }
                """.formatted(annotation, body));
    }
}
