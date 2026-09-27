package dev.aiadvent.worker.mcp;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class AllowedVerification {
    static final String TEST_ID = "branch-preflight";
    private final Path root;

    AllowedVerification(Path root) { this.root = root; }

    Map<String, Object> find(String concept) {
        if (concept == null || concept.isBlank() || concept.length() > 120) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        String lower = concept.toLowerCase(java.util.Locale.ROOT);
        boolean relevant = lower.contains("branch") || lower.contains("ветк") || lower.contains("preflight");
        return Map.of("checks", relevant ? List.of(Map.of("testId", TEST_ID,
                "displayName", "Unknown branch ID preflight",
                "purpose", "Request explicitly supplies branchId=missing-branch; test expects 404 before repository research and model execution",
                "anchor", "app/src/test/java/dev/aiadvent/worker/api/AgentControllerTest.java:missingBranchDoesNotStartRepositoryResearch",
                "executionAllowed", true)) : List.of());
    }

    Map<String, Object> run(String testId) {
        if (!TEST_ID.equals(testId)) throw new IllegalArgumentException("UNKNOWN_TEST_ID");
        String runId = UUID.randomUUID().toString();
        String head = git("rev-parse", "HEAD").trim();
        boolean dirty = !git("status", "--porcelain", "--untracked-files=all").isBlank();
        String sourceHash = sourceHash();
        Path report = root.resolve("target/surefire-reports/TEST-dev.aiadvent.worker.api.AgentControllerTest.xml");
        try { Files.deleteIfExists(report); } catch (Exception e) { return result(runId, head, dirty, sourceHash, "INFRA_ERROR", 0, "REPORT_PREP_FAILED"); }
        Process process = null;
        try {
            String mvn = System.getProperty("os.name").startsWith("Windows") ? "mvn.cmd" : "mvn";
            process = new ProcessBuilder(mvn, "-q", "-Dtest=AgentControllerTest#missingBranchDoesNotStartRepositoryResearch",
                    "-DfailIfNoTests=true", "test").directory(root.toFile()).redirectErrorStream(true)
                    .start();
            Process owned = process;
            Thread.ofVirtual().start(() -> {
                try (var input = owned.getInputStream()) { input.transferTo(java.io.OutputStream.nullOutputStream()); }
                catch (Exception ignored) { }
            });
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return result(runId, head, dirty, sourceHash, "TIMEOUT", 0, "RUNNER_TIMEOUT");
            }
            if (!head.equals(git("rev-parse", "HEAD").trim()) || !sourceHash.equals(sourceHash()))
                return result(runId, head, dirty, sourceHash, "INFRA_ERROR", 0, "SOURCE_CHANGED");
            if (!Files.isRegularFile(report)) return result(runId, head, dirty, sourceHash,
                    process.exitValue() == 0 ? "TEST_NOT_RUN" : "INFRA_ERROR", 0, "NO_FRESH_REPORT");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var suite = factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
            int tests = Integer.parseInt(suite.getAttribute("tests"));
            int failures = Integer.parseInt(suite.getAttribute("failures"));
            int errors = Integer.parseInt(suite.getAttribute("errors"));
            int skipped = Integer.parseInt(suite.getAttribute("skipped"));
            if (tests == 0 || tests == skipped) return result(runId, head, dirty, sourceHash, "TEST_NOT_RUN", tests, "ZERO_EXECUTED");
            String status = failures + errors > 0 ? "TEST_FAIL" : process.exitValue() == 0 ? "TEST_PASS" : "INFRA_ERROR";
            return result(runId, head, dirty, sourceHash, status, tests, status);
        } catch (Exception e) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return result(runId, head, dirty, sourceHash, "INFRA_ERROR", 0, "RUNNER_ERROR");
        }
    }

    private Map<String, Object> result(String runId, String head, boolean dirty, String hash, String status,
                                       int tests, String code) {
        return Map.of("runId", runId, "testId", TEST_ID, "status", status, "tests", tests,
                "code", code, "head", head, "dirty", dirty, "sourceHash", hash,
                "checkedBehavior", "Explicit unknown branchId=missing-branch is rejected before research/model calls",
                "toolExecutionStatus", "SUCCESS");
    }

    private String sourceHash() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String directory : List.of("app/src/main/java", "app/src/test/java")) {
                try (var paths = Files.walk(root.resolve(directory))) {
                    for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                        digest.update(root.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(path));
                    }
                }
            }
            digest.update(Files.readAllBytes(root.resolve("pom.xml")));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new IllegalStateException("SOURCE_IDENTITY_FAILED", e); }
    }

    private String git(String... args) {
        try {
            var command = new java.util.ArrayList<String>(); command.add("git"); command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).directory(root.toFile()).start();
            byte[] output = process.getInputStream().readNBytes(65536);
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("SOURCE_IDENTITY_FAILED");
            }
            if (process.exitValue() != 0) throw new IllegalStateException("SOURCE_IDENTITY_FAILED");
            return new String(output, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("SOURCE_IDENTITY_FAILED", e); }
    }
}
