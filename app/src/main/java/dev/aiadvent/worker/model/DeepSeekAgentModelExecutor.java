package dev.aiadvent.worker.model;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
    public PreparedToolTurn prepareChoiceTurn(AgentModelRequest base, List<ToolDefinition> tools)
            throws ModelExecutionException {
        try {
            String body = transport.buildChoiceRequestBody(base, tools);
            return new FirstRequest(body, null, footprint(body));
        } catch (DeepSeekException e) { throw modelError(e); }
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
            if (first.requiredToolName() == null)
                return choiceStep(first.body(), transport.sendToolBody(first.body()));
            var decision = transport.extractToolDecision(transport.sendBody(first.body()));
            if (decision.call() == null) return new FinalAnswer(decision.text(), decision.usage());
            if (first.requiredToolName() != null && !first.requiredToolName().equals(decision.call().name())) {
                throw new ModelExecutionException("UNKNOWN_TOOL");
            }
            return new ToolRequestStep(new ToolRequest(decision.call().name(), decision.call().arguments()),
                    new DeepSeekContinuation(first.body(), List.of(decision.call()), false), decision.usage());
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
            String body = transport.buildToolContinuationBody(state.firstBody(), state.calls().get(0), result,
                    state.keepTools());
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

    @Override
    public ToolStep continueChoiceTurn(PreparedContinuation prepared) throws ModelExecutionException {
        if (!(prepared instanceof NextRequest next)) throw new ModelExecutionException("INVALID_PREPARED_CONTINUATION");
        try {
            return choiceStep(next.body(), transport.sendToolBody(next.body()));
        } catch (DeepSeekException e) { throw modelError(e); }
    }

    @Override
    public PreparedContinuation prepareBatchContinuation(ToolContinuation continuation, List<ToolResult> results)
            throws ModelExecutionException {
        if (!(continuation instanceof DeepSeekContinuation state) || !state.keepTools())
            throw new ModelExecutionException("INVALID_TOOL_CONTINUATION");
        try {
            String body = transport.buildToolBatchContinuationBody(state.firstBody(), state.calls(), results, true);
            return new NextRequest(body, footprint(body));
        } catch (DeepSeekException e) { throw modelError(e); }
    }

    private ToolStep choiceStep(String body, String response) throws DeepSeekException {
        var decision = transport.extractChoiceDecision(response);
        if (decision.calls().isEmpty()) return new FinalAnswer(decision.text(), decision.usage());
        var requests = decision.calls().stream().map(call -> new ToolRequest(call.name(), call.arguments())).toList();
        var continuation = new DeepSeekContinuation(body, decision.calls(), true);
        return requests.size() == 1
                ? new ToolRequestStep(requests.get(0), continuation, decision.usage())
                : new ToolRequestBatchStep(requests, continuation, decision.usage());
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

    private record DeepSeekContinuation(String firstBody, List<DeepSeekTransport.NativeToolCall> calls, boolean keepTools)
            implements ToolContinuation {
    }

    private record NextRequest(String body, Footprint footprint) implements PreparedContinuation {
        @Override public long estimatedInputTokens() { return footprint.estimatedInputTokens(); }
        @Override public long serializedBytes() { return footprint.serializedBytes(); }
    }
}
