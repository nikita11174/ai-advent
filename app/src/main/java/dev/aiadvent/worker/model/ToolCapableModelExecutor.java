package dev.aiadvent.worker.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public interface ToolCapableModelExecutor {
    PreparedToolTurn prepareToolTurn(AgentModelRequest base, ToolDefinition requiredTool)
            throws ModelExecutionException;

    ToolStep beginToolTurn(PreparedToolTurn prepared) throws ModelExecutionException;

    PreparedContinuation prepareContinuation(ToolContinuation continuation, ToolResult result)
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

    sealed interface ToolStep permits FinalAnswer, ToolRequestStep {
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
}
