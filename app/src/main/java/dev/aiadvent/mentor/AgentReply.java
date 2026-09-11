package dev.aiadvent.mentor;

record AgentReply(String analysis, TokenMetrics metrics, TokenMetrics summaryMetrics,
                  ContextMetadata contextMetadata) {
}
