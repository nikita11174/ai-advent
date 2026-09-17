package dev.aiadvent.worker.agent;

import java.util.List;

public record AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                  List<TokenMetrics> factsMetrics, ContextMetadata contextMetadata) {
}
