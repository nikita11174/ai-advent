/*
 * Stdio wire handling adapted from MCP Java SDK 2.0.1 StdioClientTransport.
 * Copyright 2024 the original author or authors. Licensed under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package dev.aiadvent.worker.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Workspace-only process-owned stdio adapter for MCP Java SDK 2.0.1 (MIT license,
 * https://github.com/modelcontextprotocol/java-sdk). Mirrors its newline-delimited
 * UTF-8 JSON-RPC transport; the SDK still owns sessions, requests and protocol logic.
 */
final class OwnedStdioClientTransport implements McpClientTransport {
    private static final Logger LOG = LoggerFactory.getLogger(OwnedStdioClientTransport.class);
    private static final int MAX_LINE = 65_536;
    private static final AtomicBoolean STARTING = new AtomicBoolean();
    private static final AtomicReference<Process> UNCONFIRMED_LATE_CHILD = new AtomicReference<>();
    private final Process process;
    private final McpJsonMapper mapper;
    private final Sinks.Many<McpSchema.JSONRPCMessage> inbound = Sinks.many().unicast()
            .onBackpressureBuffer(new ArrayBlockingQueue<>(8));
    private final Sinks.Many<McpSchema.JSONRPCMessage> outbound = Sinks.many().unicast()
            .onBackpressureBuffer(new ArrayBlockingQueue<>(8));
    private final Scheduler writer = Schedulers.newSingle("workspace-mcp-writer", true);
    private volatile boolean closing;
    private volatile String lastStderrLine = "";
    private Disposable incomingSubscription;
    private Disposable outgoingSubscription;

    private OwnedStdioClientTransport(Process process, McpJsonMapper mapper) {
        this.process = process;
        this.mapper = mapper;
    }

    static OwnedStdioClientTransport start(ProcessStarter starter, McpJsonMapper mapper, Duration timeout) {
        Process process = startProcess(starter, timeout);
        try {
            return new OwnedStdioClientTransport(process, mapper);
        } catch (RuntimeException | Error e) {
            terminateLate(process);
            throw new IllegalStateException("MCP_START_FAILED");
        }
    }

    @FunctionalInterface
    interface ProcessStarter { Process start() throws IOException; }

    static Process startProcess(ProcessStarter starter, Duration timeout) {
        Process unconfirmed = UNCONFIRMED_LATE_CHILD.get();
        if (unconfirmed != null) {
            if (unconfirmed.isAlive()) throw new IllegalStateException("MCP_START_FAILED");
            UNCONFIRMED_LATE_CHILD.compareAndSet(unconfirmed, null);
        }
        if (!STARTING.compareAndSet(false, true)) throw new IllegalStateException("MCP_START_FAILED");
        var attempt = new StartAttempt(starter);
        try {
            Thread.ofPlatform().daemon().name("workspace-mcp-start").start(attempt::run);
        } catch (RuntimeException | Error e) {
            STARTING.set(false);
            throw new IllegalStateException("MCP_START_FAILED");
        }
        try {
            return attempt.result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            attempt.cancel();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP_START_FAILED");
        } catch (ExecutionException | TimeoutException e) {
            attempt.cancel();
            throw new IllegalStateException("MCP_START_FAILED");
        }
    }

    private static final class StartAttempt {
        private final ProcessStarter starter;
        private final CompletableFuture<Process> result = new CompletableFuture<>();
        private boolean cancelled;
        private Process process;

        private StartAttempt(ProcessStarter starter) { this.starter = starter; }

        private void run() {
            Process started = null;
            boolean handedOff = false;
            try {
                started = Objects.requireNonNull(starter.start());
                synchronized (this) {
                    process = started;
                    if (!cancelled) handedOff = result.complete(started);
                }
            } catch (Throwable e) {
                result.completeExceptionally(e);
            } finally {
                if (started != null && !handedOff) terminateLate(started);
                STARTING.set(false);
            }
        }

        private void cancel() {
            Process started;
            synchronized (this) {
                cancelled = true;
                started = process;
            }
            if (started != null) terminateLate(started);
        }
    }

    private static void terminateLate(Process started) {
        boolean exited = false;
        try {
            started.destroyForcibly();
            exited = started.waitFor(1, TimeUnit.SECONDS) && !started.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            try {
                started.destroy();
                exited = started.waitFor(1, TimeUnit.SECONDS) && !started.isAlive();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) { }
        }
        if (!exited) {
            UNCONFIRMED_LATE_CHILD.set(started);
            LOG.error("MCP_LATE_CHILD_SHUTDOWN_FAILED");
        }
    }

    Process process() { return process; }
    String lastStderrLine() { return lastStderrLine; }
    static boolean startupBusy() { return STARTING.get(); }

    @Override public synchronized Mono<Void> connect(
            Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        if (closing) return Mono.error(new IllegalStateException("MCP_RUNTIME_CLOSED"));
        incomingSubscription = inbound.asFlux().flatMap(message -> Mono.just(message).transform(handler)).subscribe();
        outgoingSubscription = outbound.asFlux().publishOn(writer).subscribe(this::writeMessage);
        Thread.ofPlatform().daemon().name("workspace-mcp-stdout").start(this::readMessages);
        Thread.ofPlatform().daemon().name("workspace-mcp-stderr").start(this::readErrors);
        return Mono.empty();
    }

    @Override public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        if (closing || !outbound.tryEmitNext(message).isSuccess()) {
            return Mono.error(new IllegalStateException("MCP_RUNTIME_CLOSED"));
        }
        return Mono.empty();
    }

    private void writeMessage(McpSchema.JSONRPCMessage message) {
        if (closing) return;
        try {
            String json = mapper.writeValueAsString(message)
                    .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
            var stream = process.getOutputStream();
            synchronized (stream) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                stream.write('\n');
                stream.flush();
            }
        } catch (IOException | RuntimeException e) {
            inbound.tryEmitError(new IllegalStateException("MCP_TRANSPORT_FAILED"));
        }
    }

    private void readMessages() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                try {
                    line = readLine(reader);
                } catch (IOException e) {
                    if (!closing) inbound.tryEmitError(new IllegalStateException("MCP_TRANSPORT_FAILED"));
                    drain(reader);
                    break;
                }
                if (line == null) break;
                if (closing) continue;
                try {
                    var message = McpSchema.deserializeJsonRpcMessage(mapper, line);
                    if (!inbound.tryEmitNext(message).isSuccess()) {
                        drain(reader);
                        break;
                    }
                } catch (RuntimeException e) {
                    inbound.tryEmitError(new IllegalStateException("MCP_TRANSPORT_FAILED"));
                    drain(reader);
                    break;
                }
            }
        } catch (IOException e) {
            if (!closing) inbound.tryEmitError(new IllegalStateException("MCP_TRANSPORT_FAILED"));
        } finally {
            inbound.tryEmitComplete();
        }
    }

    private static void drain(BufferedReader reader) {
        try {
            char[] buffer = new char[4096];
            while (reader.read(buffer) != -1) { }
        } catch (IOException ignored) { }
    }

    static String readLine(BufferedReader reader) throws IOException {
        var line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c == '\r') {
                reader.mark(1);
                int next = reader.read();
                if (next != '\n' && next != -1) reader.reset();
                return line.toString();
            }
            if (line.length() == MAX_LINE) throw new IOException("MCP line limit");
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private void readErrors() {
        try (var reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
            var line = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    lastStderrLine = line.toString();
                    line.setLength(0);
                } else if (line.length() < 512) {
                    line.append((char) c);
                }
            }
        } catch (IOException ignored) {
            // Closing the process streams ends this diagnostic drain.
        }
    }

    @Override public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            closeStreams();
            WorkspaceToolRuntime.awaitTermination(process, Duration.ofSeconds(5));
            closeRemainingStreams();
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override public void close() { closeStreams(); }

    synchronized void closeStreams() {
        if (closing) return;
        closing = true;
        outbound.tryEmitComplete();
        inbound.tryEmitComplete();
        if (incomingSubscription != null) incomingSubscription.dispose();
        if (outgoingSubscription != null) outgoingSubscription.dispose();
        writer.dispose();
        Thread.ofPlatform().daemon().name("workspace-mcp-stdin-close").start(() -> {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
        });
    }

    void closeRemainingStreams() {
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
    }

    @Override public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return mapper.convertValue(data, typeRef);
    }
}
