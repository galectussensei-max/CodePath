package com.codepath.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.codepath.TestData;
import com.codepath.common.ConflictException;
import com.codepath.common.ResourceNotFoundException;
import com.codepath.config.DataSeeder;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.submissions.Submission;
import com.codepath.submissions.SubmissionRepository;
import com.codepath.submissions.Verdict;
import com.codepath.users.User;
import com.codepath.users.UserRepository;

class AiInteractionStoreTest {

    private QuestionRepository questionRepository;
    private SubmissionRepository submissionRepository;
    private UserRepository userRepository;
    private AiInteractionRepository interactionRepository;
    private AiInteractionStore store;

    private final TestData data = new TestData();
    private final User user = TestData.user(7L);

    @BeforeEach
    void setUp() {
        questionRepository = mock(QuestionRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        userRepository = mock(UserRepository.class);
        interactionRepository = mock(AiInteractionRepository.class);
        store = new AiInteractionStore(questionRepository, submissionRepository, userRepository,
                interactionRepository);
        when(questionRepository.findById(data.a1.getId())).thenReturn(Optional.of(data.a1));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    private Submission submission(Long id, Long ownerId, Question question, Verdict verdict) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setUser(TestData.user(ownerId));
        submission.setQuestion(question);
        submission.setVerdict(verdict);
        submission.setSourceCode("class Main {}");
        return submission;
    }

    @Test
    void levelOneNeedsNoEarlierHint() {
        QuestionBrief brief = store.loadForHint(7L, data.a1.getId(), HintLevel.CONCEPTUAL_NUDGE);

        assertEquals("Arrays", brief.topic());
        assertEquals("Two Pointers", brief.concept());
        assertEquals("EASY", brief.difficulty());
    }

    @Test
    void levelTwoRequiresLevelOneFirst() {
        when(interactionRepository.existsByUserIdAndQuestionIdAndInteractionType(7L, 1L, InteractionType.HINT_L1))
                .thenReturn(false);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> store.loadForHint(7L, 1L, HintLevel.LOGICAL_GUIDANCE));
        assertTrue(ex.getMessage().contains("Level 1"));

        when(interactionRepository.existsByUserIdAndQuestionIdAndInteractionType(7L, 1L, InteractionType.HINT_L1))
                .thenReturn(true);
        assertEquals(1L, store.loadForHint(7L, 1L, HintLevel.LOGICAL_GUIDANCE).id());
    }

    @Test
    void levelFourRequiresLevelThree() {
        when(interactionRepository.existsByUserIdAndQuestionIdAndInteractionType(7L, 1L, InteractionType.HINT_L3))
                .thenReturn(false);

        assertThrows(ConflictException.class, () -> store.loadForHint(7L, 1L, HintLevel.PARTIAL_CLOZE));
    }

    @Test
    void diagnosticAndUnknownQuestionsLookMissing() {
        Question diagnostic = new Question();
        diagnostic.setId(99L);
        diagnostic.setPlacementTags(DataSeeder.DIAGNOSTIC_TAG);
        when(questionRepository.findById(99L)).thenReturn(Optional.of(diagnostic));

        assertThrows(ResourceNotFoundException.class,
                () -> store.loadForHint(7L, 99L, HintLevel.CONCEPTUAL_NUDGE));
        assertThrows(ResourceNotFoundException.class,
                () -> store.loadForHint(7L, 12345L, HintLevel.CONCEPTUAL_NUDGE));
        assertThrows(ResourceNotFoundException.class, () -> store.loadForReview(7L, 99L, null));
    }

    @Test
    void reviewWithoutAnAcceptedSubmissionIsRefused() {
        when(submissionRepository.findFirstByUserIdAndQuestionIdAndVerdictOrderByCreatedAtDesc(
                7L, 1L, Verdict.ACCEPTED)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> store.loadForReview(7L, 1L, null));
    }

    @Test
    void reviewUsesTheSavedAcceptedCodeNotClientCode() {
        Submission accepted = submission(55L, 7L, data.a1, Verdict.ACCEPTED);
        when(submissionRepository.findFirstByUserIdAndQuestionIdAndVerdictOrderByCreatedAtDesc(
                7L, 1L, Verdict.ACCEPTED)).thenReturn(Optional.of(accepted));

        ReviewContext context = store.loadForReview(7L, 1L, null);

        assertEquals(55L, context.submissionId());
        assertEquals("class Main {}", context.sourceCode());
    }

    @Test
    void reviewOfAnotherUsersOrWrongQuestionSubmissionLooksMissing() {
        when(submissionRepository.findById(60L))
                .thenReturn(Optional.of(submission(60L, 8L, data.a1, Verdict.ACCEPTED)));
        when(submissionRepository.findById(61L))
                .thenReturn(Optional.of(submission(61L, 7L, data.a2, Verdict.ACCEPTED)));

        assertThrows(ResourceNotFoundException.class, () -> store.loadForReview(7L, 1L, 60L));
        assertThrows(ResourceNotFoundException.class, () -> store.loadForReview(7L, 1L, 61L));
    }

    @Test
    void reviewOfANonAcceptedSubmissionIsRefused() {
        when(submissionRepository.findById(62L))
                .thenReturn(Optional.of(submission(62L, 7L, data.a1, Verdict.WRONG_ANSWER)));

        assertThrows(ConflictException.class, () -> store.loadForReview(7L, 1L, 62L));
    }

    @Test
    void recordSavesTheInteractionAndReturnsAnUnverifiedDtoWithTheHintCount() {
        when(interactionRepository.save(any(AiInteraction.class))).thenAnswer(invocation -> {
            AiInteraction saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });
        when(interactionRepository.countByUserIdAndQuestionIdAndInteractionTypeIn(anyLong(), anyLong(),
                anyCollection())).thenReturn(2L);

        AiGuidanceDto dto = store.record(7L, 1L, null, InteractionType.HINT_L2, 2,
                new AiGeneration("Check the empty array.", "prompt"));

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertEquals(InteractionType.HINT_L2, captor.getValue().getInteractionType());
        assertEquals("Check the empty array.", captor.getValue().getAiResponse());
        assertEquals("prompt", captor.getValue().getPromptContext());
        assertNull(captor.getValue().getSubmission());
        assertEquals(500L, dto.interactionId());
        assertEquals(2, dto.hintLevel());
        assertFalse(dto.isVerified());
        assertEquals(2L, dto.hintsUsedForQuestion());
        assertTrue(dto.label().contains("Unverified"));
    }

    @Test
    void recordLinksTheSubmissionForReviews() {
        when(interactionRepository.save(any(AiInteraction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Submission reference = submission(55L, 7L, data.a1, Verdict.ACCEPTED);
        when(submissionRepository.getReferenceById(55L)).thenReturn(reference);

        store.record(7L, 1L, 55L, InteractionType.AC_REVIEW, null, new AiGeneration("O(n)", "p"));

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertEquals(reference, captor.getValue().getSubmission());
    }

    @Test
    void longPromptContextIsTruncatedBeforeSaving() {
        when(interactionRepository.save(any(AiInteraction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        store.record(7L, 1L, null, InteractionType.HINT_L1, 1,
                new AiGeneration("ok", "x".repeat(AiInteractionStore.MAX_STORED_PROMPT_CHARS + 500)));

        ArgumentCaptor<AiInteraction> captor = ArgumentCaptor.forClass(AiInteraction.class);
        verify(interactionRepository).save(captor.capture());
        assertEquals(AiInteractionStore.MAX_STORED_PROMPT_CHARS, captor.getValue().getPromptContext().length());
    }

    @Test
    void unknownUserIsNotFoundAndNothingIsSaved() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> store.record(404L, 1L, null, InteractionType.HINT_L1, 1,
                new AiGeneration("ok", "p")));
        verify(interactionRepository, never()).save(any());
    }
}
