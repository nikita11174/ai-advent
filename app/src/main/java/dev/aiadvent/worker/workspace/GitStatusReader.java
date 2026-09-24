package dev.aiadvent.worker.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GitStatusReader {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_OUTPUT_BYTES = 65_536;
    private final Path repository;
    private final Duration timeout;
    private final int maxOutputBytes;
    private final ProcessStarter processStarter;

    @FunctionalInterface
    interface ProcessStarter { Process start(ProcessBuilder builder) throws IOException; }

    public GitStatusReader(Path repository) {
        this(repository, DEFAULT_TIMEOUT, DEFAULT_MAX_OUTPUT_BYTES);
    }

    GitStatusReader(Path repository, Duration timeout, int maxOutputBytes) {
        this(repository, timeout, maxOutputBytes, ProcessBuilder::start);
    }

    GitStatusReader(Path repository, Duration timeout, int maxOutputBytes, ProcessStarter processStarter) {
        if (timeout.isZero() || timeout.isNegative() || maxOutputBytes < 1) {
            throw new IllegalArgumentException("Invalid Git observation limits");
        }
        try {
            this.repository = repository.toRealPath();
        }
        catch (IOException e) {
            throw new StatusException("INVALID_REPOSITORY", e);
        }
        if (!Files.isDirectory(this.repository)) {
            throw new StatusException("INVALID_REPOSITORY");
        }
        this.timeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
        this.processStarter = processStarter;
    }

    public RepositoryStatus read(boolean includeChangeCounts) {
        var command = Arrays.asList("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
                "-C", repository.toString(), "status", "--porcelain=v2", "--branch", "-z",
                "--untracked-files=normal", "--ignore-submodules=none");
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> environment = builder.environment();
        Map<String, String> inherited = new HashMap<>(environment);
        environment.clear();
        for (var entry : inherited.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("PATH") || entry.getKey().equalsIgnoreCase("SystemRoot")
                    || entry.getKey().equalsIgnoreCase("WINDIR") || entry.getKey().equalsIgnoreCase("TEMP")
                    || entry.getKey().equalsIgnoreCase("TMP")) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
        environment.put("GIT_OPTIONAL_LOCKS", "0");
        long deadline = System.nanoTime() + timeout.toNanos();
        Process process;
        try {
            process = processStarter.start(builder);
        }
        catch (IOException e) {
            throw new StatusException("GIT_FAILURE", e);
        }
        var input = process.getInputStream();
        try {
            process.getOutputStream().close();
            FutureTask<byte[]> read = new FutureTask<>(() -> input.readNBytes(maxOutputBytes + 1));
            Thread.ofVirtual().start(read);
            byte[] output = read.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (output.length > maxOutputBytes) {
                throw new StatusException("GIT_STATUS_INCOMPLETE");
            }
            if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw new StatusException("GIT_TIMEOUT");
            }
            if (process.exitValue() != 0) {
                throw new StatusException("GIT_FAILURE");
            }
            return parse(output, includeChangeCounts);
        }
        catch (TimeoutException e) {
            throw new StatusException("GIT_TIMEOUT", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StatusException("GIT_INTERRUPTED", e);
        }
        catch (ExecutionException | IOException e) {
            throw new StatusException("GIT_FAILURE", e);
        }
        finally {
            boolean interrupted = Thread.interrupted();
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                    while (process.isAlive() && System.nanoTime() < stopDeadline) {
                        try {
                            process.waitFor(Math.max(1, stopDeadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    if (process.isAlive()) throw new StatusException("GIT_FAILURE");
                }
            } catch (RuntimeException e) {
                throw new StatusException("GIT_FAILURE");
            } finally {
                try { input.close(); } catch (IOException ignored) { }
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private static long remaining(long deadline) throws TimeoutException {
        long left = deadline - System.nanoTime();
        if (left <= 0) {
            throw new TimeoutException();
        }
        return left;
    }

    static RepositoryStatus parse(byte[] output, boolean includeChangeCounts) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output)).toString();
        }
        catch (CharacterCodingException e) {
            throw new StatusException("GIT_STATUS_INCOMPLETE", e);
        }
        if (text.isEmpty() || text.charAt(text.length() - 1) != '\0') {
            throw new StatusException("GIT_STATUS_INCOMPLETE");
        }
        String oid = null;
        String branch = null;
        boolean hasOid = false;
        boolean hasBranch = false;
        int staged = 0, unstaged = 0, untracked = 0, conflicted = 0, submoduleChanged = 0;
        boolean dirty = false;
        String[] records = text.substring(0, text.length() - 1).split("\0", -1);
        for (int i = 0; i < records.length; i++) {
            String record = records[i];
            if (record.startsWith("# branch.oid ")) {
                if (hasOid) throw new StatusException("GIT_STATUS_INCOMPLETE");
                hasOid = true;
                oid = record.substring(13);
            }
            else if (record.startsWith("# branch.head ")) {
                if (hasBranch) throw new StatusException("GIT_STATUS_INCOMPLETE");
                hasBranch = true;
                branch = record.substring(14);
            }
            else if (record.startsWith("# ")) {
                continue;
            }
            else if (record.startsWith("? ") && record.length() > 2) {
                dirty = true;
                untracked++;
            }
            else if (record.startsWith("1 ") || record.startsWith("2 ") || record.startsWith("u ")) {
                char type = record.charAt(0);
                int fields = type == '1' ? 9 : type == '2' ? 10 : 11;
                String[] parts = record.split(" ", fields);
                if (parts.length != fields || parts[fields - 1].isEmpty()
                        || !parts[1].matches("[.MADRCUT]{2}")
                        || !parts[2].matches("N\\.\\.\\.|S[.C][.M][.U]")) {
                    throw new StatusException("GIT_STATUS_INCOMPLETE");
                }
                dirty = true;
                if (type == 'u') {
                    conflicted++;
                }
                else {
                    if (parts[1].charAt(0) != '.') staged++;
                    if (parts[1].charAt(1) != '.') unstaged++;
                }
                if (parts[2].charAt(0) == 'S' && !parts[2].equals("S...")) submoduleChanged++;
                if (type == '2' && (++i >= records.length || records[i].isEmpty())) {
                    throw new StatusException("GIT_STATUS_INCOMPLETE");
                }
            }
            else {
                throw new StatusException("GIT_STATUS_INCOMPLETE");
            }
        }
        if (!hasOid || !hasBranch || branch.isBlank()) {
            throw new StatusException("GIT_STATUS_INCOMPLETE");
        }
        HeadState state;
        if (oid.equals("(initial)")) {
            if (branch.equals("(detached)")) throw new StatusException("GIT_STATUS_INCOMPLETE");
            state = HeadState.UNBORN;
            oid = null;
        }
        else if (oid.matches("(?i:[0-9a-f]{40}|[0-9a-f]{64})")) {
            if (branch.equals("(detached)")) {
                state = HeadState.DETACHED;
                branch = null;
            }
            else state = HeadState.ATTACHED;
        }
        else throw new StatusException("GIT_STATUS_INCOMPLETE");
        ChangeCounts counts = includeChangeCounts
                ? new ChangeCounts(staged, unstaged, untracked, conflicted, submoduleChanged) : null;
        return new RepositoryStatus("workspace", Instant.now(), state, branch, oid, dirty, counts);
    }

    public enum HeadState { ATTACHED, DETACHED, UNBORN }

    public record ChangeCounts(int staged, int unstaged, int untracked, int conflicted, int submoduleChanged) { }

    public record RepositoryStatus(String repositoryRef, Instant observedAt, HeadState headState,
                                   String branch, String head, boolean dirty, ChangeCounts changeCounts) { }

    public static final class StatusException extends RuntimeException {
        public StatusException(String code) { super(code); }
        public StatusException(String code, Throwable cause) { super(code, cause); }
    }
}
