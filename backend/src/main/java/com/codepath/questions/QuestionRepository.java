package com.codepath.questions;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    Optional<Question> findBySlug(String slug);

    List<Question> findByTopicId(Long topicId);

    List<Question> findByConceptId(Long conceptId);

    List<Question> findByDifficulty(Difficulty difficulty);

    /** Used by the assessment module: diagnostic questions are tagged placement_tags = 'DIAGNOSTIC'. */
    List<Question> findByPlacementTagsOrderByIdAsc(String placementTags);

    /**
     * Every practice problem (diagnostic questions excluded) with its topic and concept loaded, ordered by id.
     * Used by the learning, roadmap and analytics modules.
     */
    @Query("select q from Question q join fetch q.topic join fetch q.concept "
            + "where q.placementTags is null or q.placementTags <> :diagnosticTag order by q.id")
    List<Question> findPracticeQuestions(@Param("diagnosticTag") String diagnosticTag);
}
