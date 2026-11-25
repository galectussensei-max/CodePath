package com.codepath.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codepath.common.ServiceUnavailableException;
import com.codepath.config.AiProperties;
import com.codepath.submissions.Verdict;

class AiAssistantServiceTest {

    private final QuestionBrief question = new QuestionBrief(1L, "Two Sum Sorted", "Find a pair.", null, "EASY",
            "Arrays", "Two Pointers");

    private LlmClient llm;
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        AiProperties properties = new AiProperties("http://ai.test", "key", "model", 1000, 1000, 512, 0.3, 8000);
        service = new AiAssistantService(llm, properties);
    }

    @Test
    void levelOneReturnsTheConceptualHintWithoutCode() {
        when(llm.complete(anyString(), anyString())).thenReturn("  Think about two pointers on a sorted array.  ");

        AiGeneration result = service.generateHint(question, "int x;", Verdict.WRONG_ANSWER,
                HintLevel.CONCEPTUAL_NUDGE);

        assertEquals("Think about two pointers on a sorted array.", result.content());
        assertTrue(result.promptContext().contains("<student_code>"));
        verify(llm).complete(contains("LEVEL 1"), anyString());
    }

    @Test
    void retriesOnceWhenTheFirstReplyLeaksCode() {
        when(llm.complete(anyString(), anyString()))
                .thenReturn("```java\nint x = 1;\n```")
                .thenReturn("Consider a pointer from each end.");

        AiGeneration result = service.generateHint(question, "", null, HintLevel.ALGORITHMIC_STEPS);

        assertEquals("Consider a pointer from each end.", result.content());
        assertTrue(result.promptContext().contains("previous reply was rejected"));
        verify(llm, times(2)).complete(anyString(), anyString());
    }

    @Test
    void givesUpAfterTwoUnsafeRepliesInsteadOfReturningThem() {
        when(llm.complete(anyString(), anyString())).thenReturn("public static void main(String[] a) {}");

        assertThrows(ServiceUnavailableException.class,
                () -> service.generateHint(question, "", null, HintLevel.LOGICAL_GUIDANCE));
        verify(llm, times(2)).complete(anyString(), anyString());
    }

    @Test
    void levelFourAcceptsASkeletonAndRejectsAFullSolution() {
        String skeleton = "```java\nint l = ____;\nint r = ____;\nwhile (____) {\n  ____;\n}\n```\nFill the blanks.";
        when(llm.complete(anyString(), anyString())).thenReturn(skeleton);
        assertEquals(skeleton, service.generateHint(question, "", null, HintLevel.PARTIAL_CLOZE).content());

        LlmClient second = mock(LlmClient.class);
        when(second.complete(anyString(), anyString())).thenReturn(
                "```java\nint l = 0;\nint r = n - 1;\nwhile (l < r) {\n  l++;\n}\n```");
        AiAssistantService strict = new AiAssistantService(second,
                new AiProperties("u", "k", "m", 1, 1, 1, 0.1, 100));
        assertThrows(ServiceUnavailableException.class,
                () -> strict.generateHint(question, "", null, HintLevel.PARTIAL_CLOZE));
    }

    @Test
    void reviewUsesTheReviewPromptAndRejectsCode() {
        when(llm.complete(anyString(), anyString())).thenReturn("Time O(n), space O(1).");

        AiGeneration result = service.generateReview(question, "class Main {}");

        assertEquals("Time O(n), space O(1).", result.content());
        verify(llm).complete(contains("AI reviewer"), contains("ACCEPTED (verified)"));
    }

    @Test
    void propagatesProviderFailures() {
        when(llm.complete(anyString(), anyString())).thenThrow(new ServiceUnavailableException("down"));

        assertThrows(ServiceUnavailableException.class,
                () -> service.generateReview(question, "code"));
        verify(llm, times(1)).complete(anyString(), eq(AiPromptBuilder.reviewUserPrompt(question, "code", 8000)));
    }
}
