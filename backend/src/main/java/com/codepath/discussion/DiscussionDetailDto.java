package com.codepath.discussion;

import java.time.Instant;
import java.util.List;

public record DiscussionDetailDto(
        Long id,
        Long questionId,
        String title,
        String content,
        String authorName,
        int upvotes,
        Instant createdAt,
        List<CommentDto> comments) {

    public static DiscussionDetailDto of(Discussion discussion, List<DiscussionComment> comments) {
        return new DiscussionDetailDto(discussion.getId(), discussion.getQuestion().getId(), discussion.getTitle(),
                discussion.getContent(), discussion.getUser().getName(), discussion.getUpvotes(),
                discussion.getCreatedAt(), comments.stream().map(CommentDto::from).toList());
    }
}
