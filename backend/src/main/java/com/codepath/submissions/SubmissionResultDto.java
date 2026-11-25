package com.codepath.submissions;

import java.time.Instant;
import java.util.List;

import com.codepath.learning.MasteryUpdate;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Verified Judge0 verdict for a RUN or a SUBMIT. {@code submissionId}, {@code createdAt} and
 * {@code masteryUpdate} are only present for a SUBMIT, because a RUN is neither saved nor counted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionResultDto(
        String mode,
        Long submissionId,
        Verdict verdict,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        int passedCount,
        int totalCount,
        String compileOutput,
        List<TestCaseResultDto> testCases,
        MasteryUpdate masteryUpdate,
        Instant createdAt) {

    static SubmissionResultDto of(String mode, Long submissionId, Instant createdAt, JudgeOutcome outcome,
            MasteryUpdate masteryUpdate) {
        return new SubmissionResultDto(mode, submissionId, outcome.verdict(), outcome.executionTimeMs(),
                outcome.memoryUsedKb(), outcome.passedCount(), outcome.totalCount(), outcome.compileOutput(),
                outcome.testCases(), masteryUpdate, createdAt);
    }
}
