package com.codepath.ai;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

/**
 * The short database transactions around an AI call. Kept apart from {@link AiGuidanceService} so no
 * transaction (and no connection) stays open while the slow LLM request is running.
 */
@Service
@RequiredArgsConstructor
public class AiInteractionStore {

    static final List<InteractionType> HINT_TYPES = List.of(
            InteractionType.HINT_L1, InteractionType.HINT_L2, InteractionType.HINT_L3, InteractionType.HINT_L4);

    /** The stored {@code prompt_context} is capped so very long code cannot bloat the table. */
    static final int MAX_STORED_PROMPT_CHARS = 12000;

    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final AiInteractionRepository aiInteractionRepository;

    /** Checks the question exists and that the learner is not skipping ahead of the Level 1 -> 4 order. */
    @Transactional(readOnly = true)
    public QuestionBrief loadForHint(Long userId, Long questionId, HintLevel level) {
        Question question = findPracticeQuestion(questionId);
        HintLevel previous = level.previous();
        if (previous != null && !aiInteractionRepository.existsByUserIdAndQuestionIdAndInteractionType(
                userId, question.getId(), previous.interactionType())) {
            throw level.skippedException();
        }
        return brief(question);
    }

    /** Loads the learner's own ACCEPTED submission (a given id, or the latest one) for review. */
    @Transactional(readOnly = true)
    public ReviewContext loadForReview(Long userId, Long questionId, Long submissionId) {
        Question question = findPracticeQuestion(questionId);
        Submission submission;
        if (submissionId != null) {
            submission = submissionRepository.findById(submissionId)
                    .filter(found -> found.getUser().getId().equals(userId)
                            && found.getQuestion().getId().equals(question.getId()))
                    .orElseThrow(() -> ResourceNotFoundException.of("Submission", submissionId));
            if (submission.getVerdict() != Verdict.ACCEPTED) {
                throw new ConflictException("An AI review is only available for an ACCEPTED submission");
            }
        } else {
            submission = submissionRepository
                    .findFirstByUserIdAndQuestionIdAndVerdictOrderByCreatedAtDesc(
                            userId, question.getId(), Verdict.ACCEPTED)
                    .orElseThrow(() -> new ConflictException(
                            "Solve this question (verdict ACCEPTED) before asking for an AI review"));
        }
        return new ReviewContext(brief(question), submission.getId(), submission.getSourceCode());
    }

    /** Saves the interaction and returns the learner's hint total for the question, counted after saving. */
    @Transactional
    public AiGuidanceDto record(Long userId, Long questionId, Long submissionId, InteractionType type,
            Integer hintLevel, AiGeneration generation) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Question question = findPracticeQuestion(questionId);

        AiInteraction interaction = new AiInteraction();
        interaction.setUser(user);
        interaction.setQuestion(question);
        if (submissionId != null) {
            interaction.setSubmission(submissionRepository.getReferenceById(submissionId));
        }
        interaction.setInteractionType(type);
        interaction.setPromptContext(truncate(generation.promptContext()));
        interaction.setAiResponse(generation.content());
        AiInteraction saved = aiInteractionRepository.save(interaction);

        long hintsUsed = aiInteractionRepository.countByUserIdAndQuestionIdAndInteractionTypeIn(
                userId, question.getId(), HINT_TYPES);
        return AiGuidanceDto.of(saved, hintLevel, hintsUsed);
    }

    private Question findPracticeQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .filter(question -> !DataSeeder.DIAGNOSTIC_TAG.equals(question.getPlacementTags()))
                .orElseThrow(() -> ResourceNotFoundException.of("Question", questionId));
    }

    private static QuestionBrief brief(Question question) {
        return new QuestionBrief(question.getId(), question.getTitle(), question.getDescription(),
                question.getConstraints(), question.getDifficulty().name(), question.getTopic().getName(),
                question.getConcept().getName());
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_STORED_PROMPT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_STORED_PROMPT_CHARS);
    }
}
