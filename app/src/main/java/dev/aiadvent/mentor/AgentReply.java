package dev.aiadvent.mentor;

import java.util.List;

record AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                  List<TokenMetrics> factsMetrics, ContextMetadata contextMetadata) {
}
