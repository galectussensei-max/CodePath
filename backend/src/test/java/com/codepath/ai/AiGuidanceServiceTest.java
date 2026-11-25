package com.codepath.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codepath.common.ConflictException;
import com.codepath.common.ServiceUnavailableException;
import com.codepath.submissions.Verdict;

class AiGuidanceServiceTest {

    private final QuestionBrief brief = new QuestionBrief(1L, "t", "d", null, "EASY", "Arrays", "Two Pointers");

    private AiInteractionStore store;
    private AiAssistantService assistant;
    private AiGuidanceService service;

    @BeforeEach
    void setUp() {
        store = mock(AiInteractionStore.class);
        assistant = mock(AiAssistantService.class);
        service = new AiGuidanceService(store, assistant);
    }

    private static AiGuidanceDto dto(String type, Integer level) {
        return new AiGuidanceDto(1L, type, level, "text", false, AiGuidanceDto.UNVERIFIED_LABEL, 1, Instant.now());
    }

    @Test
    void hintValidatesThenGeneratesThenSaves() {
        AiGeneration generation = new AiGeneration("Use two pointers.", "prompt");
        when(store.loadForHint(7L, 1L, HintLevel.CONCEPTUAL_NUDGE)).thenReturn(brief);
        when(assistant.generateHint(brief, "code", Verdict.WRONG_ANSWER, HintLevel.CONCEPTUAL_NUDGE))
                .thenReturn(generation);
        when(store.record(7L, 1L, null, InteractionType.HINT_L1, 1, generation)).thenReturn(dto("HINT_L1", 1));

        AiGuidanceDto result = service.hint(7L, new HintRequest(1L, "code", Verdict.WRONG_ANSWER, 1));

        assertEquals("HINT_L1", result.interactionType());
        assertFalse(result.isVerified());
    }

    @Test
    void aFailedModelCallSavesNothingSoNoHintIsCounted() {
        when(store.loadForHint(7L, 1L, HintLevel.LOGICAL_GUIDANCE)).thenReturn(brief);
        when(assistant.generateHint(any(), any(), any(), eq(HintLevel.LOGICAL_GUIDANCE)))
                .thenThrow(new ServiceUnavailableException("down"));

        assertThrows(ServiceUnavailableException.class,
                () -> service.hint(7L, new HintRequest(1L, "", null, 2)));
        verify(store, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void skippingALevelStopsBeforeTheModelIsCalled() {
        when(store.loadForHint(7L, 1L, HintLevel.PARTIAL_CLOZE)).thenThrow(new ConflictException("Level 3 first"));

        assertThrows(ConflictException.class, () -> service.hint(7L, new HintRequest(1L, "", null, 4)));
        verify(assistant, never()).generateHint(any(), any(), any(), any());
    }

    @Test
    void invalidLevelIsABadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.hint(7L, new HintRequest(1L, "", null, 5)));
        assertThrows(IllegalArgumentException.class, () -> service.hint(7L, new HintRequest(1L, "", null, null)));
    }

    @Test
    void reviewReviewsTheSavedSubmissionAndLinksIt() {
        AiGeneration generation = new AiGeneration("O(n)", "prompt");
        when(store.loadForReview(7L, 1L, null)).thenReturn(new ReviewContext(brief, 55L, "class Main {}"));
        when(assistant.generateReview(brief, "class Main {}")).thenReturn(generation);
        when(store.record(7L, 1L, 55L, InteractionType.AC_REVIEW, null, generation))
                .thenReturn(dto("AC_REVIEW", null));

        AiGuidanceDto result = service.review(7L, new ReviewRequest(1L, null));

        assertEquals("AC_REVIEW", result.interactionType());
        assertFalse(result.isVerified());
    }
}
