package com.codepath.ai;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Orchestrates a guidance request: validate and load (short transaction), call the LLM (no transaction),
 * then save the interaction (short transaction). A hint is saved, and so counted toward
 * {@code hints_used_count}, only after the model produced a guard-approved reply; failures cost the learner
 * nothing. Deliberately not transactional.
 */
@Service
@RequiredArgsConstructor
public class AiGuidanceService {

    private final AiInteractionStore store;
    private final AiAssistantService assistantService;

    public AiGuidanceDto hint(Long userId, HintRequest request) {
        HintLevel level = HintLevel.require(request.hintLevel());
        QuestionBrief question = store.loadForHint(userId, request.questionId(), level);
        AiGeneration generation = assistantService.generateHint(question, request.sourceCode(), request.verdict(),
                level);
        return store.record(userId, request.questionId(), null, level.interactionType(), level.number(), generation);
    }

    public AiGuidanceDto review(Long userId, ReviewRequest request) {
        ReviewContext context = store.loadForReview(userId, request.questionId(), request.submissionId());
        AiGeneration generation = assistantService.generateReview(context.question(), context.sourceCode());
        return store.record(userId, request.questionId(), context.submissionId(), InteractionType.AC_REVIEW, null,
                generation);
    }
}
