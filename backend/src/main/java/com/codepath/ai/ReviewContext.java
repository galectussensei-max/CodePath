package com.codepath.ai;

/** What a post-AC review needs: the question facts and the learner's saved Accepted submission. */
public record ReviewContext(QuestionBrief question, Long submissionId, String sourceCode) {
}
