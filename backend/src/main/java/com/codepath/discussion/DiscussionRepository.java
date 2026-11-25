package com.codepath.discussion;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    List<Discussion> findByQuestionIdOrderByUpvotesDescCreatedAtDesc(Long questionId);

    /** Threads of one question, most upvoted first, with the author loaded. */
    @Query("select d from Discussion d join fetch d.user where d.question.id = :questionId "
            + "order by d.upvotes desc, d.createdAt desc")
    List<Discussion> findThreads(@Param("questionId") Long questionId);

    /** One thread with its author and question loaded. */
    @Query("select d from Discussion d join fetch d.user join fetch d.question where d.id = :id")
    Optional<Discussion> findDetailById(@Param("id") Long id);

    /** Atomic increment, so two simultaneous upvotes are both counted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Discussion d set d.upvotes = d.upvotes + 1 where d.id = :id")
    int incrementUpvotes(@Param("id") Long id);
}
