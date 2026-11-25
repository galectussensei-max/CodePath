package com.codepath.discussion;

import java.time.Instant;

/** One row of a question's thread list. The author is shown by name only; emails are never exposed. */
public record DiscussionSummaryDto(
        Long id,
        Long questionId,
        String title,
        String authorName,
        int upvotes,
        long commentCount,
        Instant createdAt) {

    public static DiscussionSummaryDto of(Discussion discussion, long commentCount) {
        return new DiscussionSummaryDto(discussion.getId(), discussion.getQuestion().getId(), discussion.getTitle(),
                discussion.getUser().getName(), discussion.getUpvotes(), commentCount, discussion.getCreatedAt());
    }
}
