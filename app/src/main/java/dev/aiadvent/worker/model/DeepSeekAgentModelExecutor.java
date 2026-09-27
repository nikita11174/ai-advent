package dev.aiadvent.worker.model;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
class DeepSeekAgentModelExecutor implements AgentModelExecutor, ToolCapableModelExecutor {
    private static final long MAX_TOOL_REQUEST_BYTES = 262_144;
    private final DeepSeekTransport transport;

    DeepSeekAgentModelExecutor(DeepSeekTransport transport) {
        this.transport = transport;
    }

    @Override
    public Completion complete(AgentModelRequest request) throws ModelExecutionException {
        try {
            DeepSeekTransport.Completion completion = transport.complete(request);
            return new Completion(completion.content(), completion.usage());
        } catch (DeepSeekException exception) {
            throw new ModelExecutionException(exception.getMessage(), exception.rawResponse(), exception);
        }
    }

    @Override
    public PreparedToolTurn prepareToolTurn(AgentModelRequest base, ToolDefinition requiredTool)
            throws ModelExecutionException {
        try {
            String body = transport.buildToolRequestBody(base, requiredTool);
            Footprint footprint = footprint(body);
            return new FirstRequest(body, requiredTool.name(), footprint);
        }
        catch (DeepSeekException e) {
            throw modelError(e);
        }
    }

    @Override
    public ToolStep beginToolTurn(PreparedToolTurn prepared) throws ModelExecutionException {
        if (!(prepared instanceof FirstRequest first)) throw new ModelExecutionException("INVALID_PREPARED_TOOL_TURN");
        try {
            var decision = transport.extractToolDecision(transport.sendBody(first.body()));
            if (decision.call() == null) return new FinalAnswer(decision.text(), decision.usage());
            if (!first.requiredToolName().equals(decision.call().name())) {
                throw new ModelExecutionException("UNKNOWN_TOOL");
            }
            return new ToolRequestStep(new ToolRequest(decision.call().name(), decision.call().arguments()),
                    new DeepSeekContinuation(first.body(), decision.call()), decision.usage());
        }
        catch (DeepSeekException e) {
            throw modelError(e);
        }
    }

    @Override
    public PreparedContinuation prepareContinuation(ToolContinuation continuation, ToolResult result)
            throws ModelExecutionException {
        if (!(continuation instanceof DeepSeekContinuation state)) {
            throw new ModelExecutionException("INVALID_TOOL_CONTINUATION");
        }
        try {
            String body = transport.buildToolContinuationBody(state.firstBody(), state.call(), result);
            return new NextRequest(body, footprint(body));
        }
        catch (DeepSeekException e) {
            throw modelError(e);
        }
    }

    @Override
    public FinalAnswer continueToolTurn(PreparedContinuation prepared) throws ModelExecutionException {
        if (!(prepared instanceof NextRequest next)) throw new ModelExecutionException("INVALID_PREPARED_CONTINUATION");
        try {
            var completion = transport.extractToolFinal(transport.sendBody(next.body()));
            return new FinalAnswer(completion.content(), completion.usage());
        }
        catch (DeepSeekException e) {
            throw modelError(e);
        }
    }

    private static Footprint footprint(String body) throws ModelExecutionException {
        long bytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_TOOL_REQUEST_BYTES) throw new ModelExecutionException("TOOL_REQUEST_LIMIT");
        return new Footprint((bytes + 2) / 3, bytes);
    }

    private static ModelExecutionException modelError(DeepSeekException e) {
        return new ModelExecutionException(e.getMessage(), e.rawResponse(), e);
    }

    private record Footprint(long estimatedInputTokens, long serializedBytes) {
    }

    private record FirstRequest(String body, String requiredToolName, Footprint footprint)
            implements PreparedToolTurn {
        @Override public long estimatedInputTokens() { return footprint.estimatedInputTokens(); }
        @Override public long serializedBytes() { return footprint.serializedBytes(); }
    }

    private record DeepSeekContinuation(String firstBody, DeepSeekTransport.NativeToolCall call)
            implements ToolContinuation {
    }

    private record NextRequest(String body, Footprint footprint) implements PreparedContinuation {
        @Override public long estimatedInputTokens() { return footprint.estimatedInputTokens(); }
        @Override public long serializedBytes() { return footprint.serializedBytes(); }
    }
}
