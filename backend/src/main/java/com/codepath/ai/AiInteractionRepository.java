package com.codepath.ai;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    List<AiInteraction> findByUserIdAndQuestionIdOrderByCreatedAtDesc(Long userId, Long questionId);

    long countByUserIdAndQuestionId(Long userId, Long questionId);

    /** Whether the learner has already received a given kind of interaction (used to enforce Level 1 -> 4 order). */
    boolean existsByUserIdAndQuestionIdAndInteractionType(Long userId, Long questionId,
            InteractionType interactionType);

    /** Used by the submissions module to count the hints a learner requested for a question. */
    long countByUserIdAndQuestionIdAndInteractionTypeIn(Long userId, Long questionId,
            Collection<InteractionType> interactionTypes);
}
