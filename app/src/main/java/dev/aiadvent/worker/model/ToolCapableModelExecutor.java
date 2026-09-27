package dev.aiadvent.worker.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.List;

public interface ToolCapableModelExecutor {
    PreparedToolTurn prepareChoiceTurn(AgentModelRequest base, List<ToolDefinition> tools)
            throws ModelExecutionException;

    ToolStep continueChoiceTurn(PreparedContinuation prepared) throws ModelExecutionException;

    PreparedToolTurn prepareToolTurn(AgentModelRequest base, ToolDefinition requiredTool)
            throws ModelExecutionException;

    ToolStep beginToolTurn(PreparedToolTurn prepared) throws ModelExecutionException;

    PreparedContinuation prepareContinuation(ToolContinuation continuation, ToolResult result)
            throws ModelExecutionException;

    PreparedContinuation prepareBatchContinuation(ToolContinuation continuation, List<ToolResult> results)
            throws ModelExecutionException;

    FinalAnswer continueToolTurn(PreparedContinuation prepared) throws ModelExecutionException;

    interface PreparedToolTurn {
        long estimatedInputTokens();
        long serializedBytes();
    }

    interface PreparedContinuation {
        long estimatedInputTokens();
        long serializedBytes();
    }

    interface ToolContinuation {
    }

    sealed interface ToolStep permits FinalAnswer, ToolRequestStep, ToolRequestBatchStep {
    }

    record ToolDefinition(String name, String description, JsonNode inputSchema) {
        public ToolDefinition {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
            inputSchema = Objects.requireNonNull(inputSchema).deepCopy();
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }
    }

    record ToolRequest(String name, JsonNode arguments) {
        public ToolRequest {
            Objects.requireNonNull(name);
            arguments = Objects.requireNonNull(arguments).deepCopy();
        }

        @Override
        public JsonNode arguments() {
            return arguments.deepCopy();
        }
    }

    record ToolResult(String name, JsonNode structuredResult) {
        public ToolResult {
            Objects.requireNonNull(name);
            structuredResult = Objects.requireNonNull(structuredResult).deepCopy();
        }

        @Override
        public JsonNode structuredResult() {
            return structuredResult.deepCopy();
        }
    }

    record FinalAnswer(String text, ProviderUsage usage) implements ToolStep {
    }

    record ToolRequestStep(ToolRequest request, ToolContinuation continuation, ProviderUsage usage)
            implements ToolStep {
    }

    record ToolRequestBatchStep(List<ToolRequest> requests, ToolContinuation continuation, ProviderUsage usage)
            implements ToolStep {
        public ToolRequestBatchStep { requests = List.copyOf(requests); }
    }
}
