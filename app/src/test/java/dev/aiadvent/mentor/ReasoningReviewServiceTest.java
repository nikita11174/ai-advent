package dev.aiadvent.mentor;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReasoningReviewServiceTest {
    private final DeepSeekClient client = mock(DeepSeekClient.class);
    private final ReasoningReviewService service = new ReasoningReviewService(client);

    @Test
    void directUsesOneCallWithUnchangedInput() throws Exception {
        when(client.analyzeWithSystem(contains("engineering review mentor"), org.mockito.ArgumentMatchers.eq("task")))
                .thenReturn("answer");

        var result = service.analyze("task", ReasoningStrategy.DIRECT);

        assertEquals("answer", result.analysis());
        assertNull(result.generatedPrompt());
        verify(client).analyzeWithSystem(contains("engineering review mentor"), org.mockito.ArgumentMatchers.eq("task"));
    }

    @Test
    void stepByStepUsesStrategyInstructionWithoutChangingInput() throws Exception {
        when(client.analyzeWithSystem(contains("step by step"), org.mockito.ArgumentMatchers.eq("task")))
                .thenReturn("answer");

        service.analyze("task", ReasoningStrategy.STEP_BY_STEP);

        verify(client).analyzeWithSystem(contains("step by step"), org.mockito.ArgumentMatchers.eq("task"));
    }

    @Test
    void selfPromptUsesGeneratedPromptUnchangedForSecondCall() throws Exception {
        when(client.analyzeWithSystem(contains("Create a precise prompt"), org.mockito.ArgumentMatchers.eq("task")))
                .thenReturn("generated prompt");
        when(client.analyzeWithSystem("generated prompt", "task")).thenReturn("answer");

        var result = service.analyze("task", ReasoningStrategy.SELF_PROMPT);

        assertEquals("generated prompt", result.generatedPrompt());
        assertEquals("answer", result.analysis());
        InOrder order = inOrder(client);
        order.verify(client).analyzeWithSystem(contains("Create a precise prompt"), org.mockito.ArgumentMatchers.eq("task"));
        order.verify(client).analyzeWithSystem("generated prompt", "task");
    }

    @Test
    void expertsUsesOneCallAndRequiresEveryApprovedPerspective() throws Exception {
        when(client.analyzeWithSystem(contains("Java Correctness Reviewer"), org.mockito.ArgumentMatchers.eq("task")))
                .thenReturn("answer");

        service.analyze("task", ReasoningStrategy.EXPERTS);

        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).analyzeWithSystem(prompt.capture(), org.mockito.ArgumentMatchers.eq("task"));
        assertTrue(prompt.getValue().contains("Reliability / Concurrency / Idempotency Reviewer"));
        assertTrue(prompt.getValue().contains("Architecture Critic"));
    }
}
