package dev.aiadvent.worker.agent;

import java.util.List;

public record AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                  List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics, ContextMetadata contextMetadata,
                  ToolTurnTrace toolTrace, OrchestrationTrace orchestrationTrace) {
    public AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                      List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics, ContextMetadata contextMetadata,
                      ToolTurnTrace toolTrace) {
        this(analysis, metrics, summaryMetrics, factsMetrics, guardMetrics, contextMetadata, toolTrace, null);
    }
    public AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                      List<TokenMetrics> factsMetrics, TokenMetrics guardMetrics, ContextMetadata contextMetadata) {
        this(analysis, metrics, summaryMetrics, factsMetrics, guardMetrics, contextMetadata, null, null);
    }
}
