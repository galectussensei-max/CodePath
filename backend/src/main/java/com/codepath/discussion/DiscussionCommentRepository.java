package com.codepath.discussion;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, Long> {

    List<DiscussionComment> findByDiscussionIdOrderByCreatedAtAsc(Long discussionId);

    /** Comments of one thread, oldest first, with the author loaded. */
    @Query("select c from DiscussionComment c join fetch c.user where c.discussion.id = :discussionId "
            + "order by c.createdAt asc, c.id asc")
    List<DiscussionComment> findThreadComments(@Param("discussionId") Long discussionId);

    /** Comment counts for many threads in one query. */
    @Query("select new com.codepath.discussion.CommentCount(c.discussion.id, count(c)) "
            + "from DiscussionComment c where c.discussion.id in :ids group by c.discussion.id")
    List<CommentCount> countByDiscussionIds(@Param("ids") Collection<Long> ids);
}
