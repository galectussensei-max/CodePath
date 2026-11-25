package com.codepath.submissions;

/** Aggregate row: how many of a learner's submissions in one topic received one verdict. */
public record TopicVerdictCount(Long topicId, Verdict verdict, Long count) {
}
