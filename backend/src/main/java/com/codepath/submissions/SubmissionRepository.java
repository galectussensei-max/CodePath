package com.codepath.submissions;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserIdAndQuestionIdOrderByCreatedAtDesc(Long userId, Long questionId);

    /** The learner's most recent submission with the given verdict (used by the post-AC AI review). */
    Optional<Submission> findFirstByUserIdAndQuestionIdAndVerdictOrderByCreatedAtDesc(Long userId, Long questionId,
            Verdict verdict);

    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndQuestionId(Long userId, Long questionId);

    boolean existsByUserIdAndQuestionIdAndVerdict(Long userId, Long questionId, Verdict verdict);

    /** Earlier attempts at a question, excluding the given submission itself. */
    long countByUserIdAndQuestionIdAndIdNot(Long userId, Long questionId, Long submissionId);

    /** Whether the learner had already solved the question before the given submission. */
    boolean existsByUserIdAndQuestionIdAndVerdictAndIdNot(Long userId, Long questionId, Verdict verdict,
            Long submissionId);

    /** Ids of every question the learner has submitted at least once. */
    @Query("select distinct s.question.id from Submission s where s.user.id = :userId")
    List<Long> findAttemptedQuestionIds(@Param("userId") Long userId);

    /** Ids of the questions for which the learner has at least one submission with the given verdict. */
    @Query("select distinct s.question.id from Submission s where s.user.id = :userId and s.verdict = :verdict")
    List<Long> findQuestionIdsByVerdict(@Param("userId") Long userId, @Param("verdict") Verdict verdict);

    /** Submission counts per topic and verdict for one learner. */
    @Query("select new com.codepath.submissions.TopicVerdictCount(s.question.topic.id, s.verdict, count(s)) "
            + "from Submission s where s.user.id = :userId group by s.question.topic.id, s.verdict")
    List<TopicVerdictCount> countVerdictsByTopic(@Param("userId") Long userId);

    /** Mean judged run time of the learner's submissions with the given verdict, or null when there are none. */
    @Query("select avg(s.executionTimeMs) from Submission s "
            + "where s.user.id = :userId and s.verdict = :verdict and s.executionTimeMs is not null")
    Double averageExecutionTimeMs(@Param("userId") Long userId, @Param("verdict") Verdict verdict);
}
