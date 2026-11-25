package com.codepath.discussion;

import java.time.Instant;

public record CommentDto(Long id, String authorName, String content, Instant createdAt) {

    public static CommentDto from(DiscussionComment comment) {
        return new CommentDto(comment.getId(), comment.getUser().getName(), comment.getContent(),
                comment.getCreatedAt());
    }
}
