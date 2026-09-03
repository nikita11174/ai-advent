package dev.aiadvent.mentor;

import org.springframework.stereotype.Service;

@Service
class ReasoningReviewService {
    private static final String DIRECT_PROMPT = """
            You are an engineering review mentor. Analyze the provided code or engineering question.
            Explain potential engineering risks clearly and concisely. Respond in Russian.
            Format the response using Markdown when it improves readability.""";
    private static final String STEP_PROMPT = DIRECT_PROMPT + """

            Solve the review task step by step: check assumptions, invariants, edge cases and adjacent effects.
            Return only the final review, not hidden chain-of-thought.""";
    private static final String PROMPT_GENERATOR = """
            Create a precise prompt for reviewing the original engineering task below.
            Preserve every constraint from the original task. Do not solve the task.
            Return only the generated prompt, without Markdown fences or commentary.""";
    private static final String EXPERTS_PROMPT = """
            You are an engineering review council. Analyze the original task and respond in Russian Markdown.
            Provide a clearly separated conclusion from EACH of these experts:
            1. Java Correctness Reviewer
            2. Reliability / Concurrency / Idempotency Reviewer
            3. Architecture Critic
            Do not replace individual conclusions with a combined vote or summary.""";

    private final DeepSeekClient client;

    ReasoningReviewService(DeepSeekClient client) {
        this.client = client;
    }

    ReasoningResult analyze(String input, ReasoningStrategy strategy) throws DeepSeekException {
        return switch (strategy) {
            case DIRECT -> new ReasoningResult(strategy, client.analyzeWithSystem(DIRECT_PROMPT, input), null);
            case STEP_BY_STEP -> new ReasoningResult(strategy, client.analyzeWithSystem(STEP_PROMPT, input), null);
            case EXPERTS -> new ReasoningResult(strategy, client.analyzeWithSystem(EXPERTS_PROMPT, input), null);
            case SELF_PROMPT -> selfPrompt(input);
        };
    }

    private ReasoningResult selfPrompt(String input) throws DeepSeekException {
        String generatedPrompt = client.analyzeWithSystem(PROMPT_GENERATOR, input);
        String analysis = client.analyzeWithSystem(generatedPrompt, input);
        return new ReasoningResult(ReasoningStrategy.SELF_PROMPT, analysis, generatedPrompt);
    }

    record ReasoningResult(ReasoningStrategy strategy, String analysis, String generatedPrompt) {
    }
}
