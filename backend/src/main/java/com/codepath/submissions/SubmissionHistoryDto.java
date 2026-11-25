package com.codepath.submissions;

import java.time.Instant;

/** One of the learner's own past submissions. The log contains no hidden test data. */
public record SubmissionHistoryDto(
        Long id,
        Long questionId,
        Language language,
        Verdict verdict,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        Integer timeTakenSeconds,
        int hintsUsedCount,
        String sourceCode,
        String judgeOutputLog,
        Instant createdAt) {

    static SubmissionHistoryDto from(Submission submission, Long questionId) {
        return new SubmissionHistoryDto(submission.getId(), questionId, submission.getLanguage(),
                submission.getVerdict(), submission.getExecutionTimeMs(), submission.getMemoryUsedKb(),
                submission.getTimeTakenSeconds(), submission.getHintsUsedCount(), submission.getSourceCode(),
                submission.getJudgeOutputLog(), submission.getCreatedAt());
    }
}
