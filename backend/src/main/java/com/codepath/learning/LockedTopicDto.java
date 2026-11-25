package com.codepath.learning;

/** A topic whose problems are not recommended yet because a prerequisite is not met. */
public record LockedTopicDto(Long topicId, String topic, String reason) {
}
