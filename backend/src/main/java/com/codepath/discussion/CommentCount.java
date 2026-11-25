package com.codepath.discussion;

/** Aggregate row: how many comments one discussion thread has. */
public record CommentCount(Long discussionId, Long count) {
}
