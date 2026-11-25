package com.codepath.judging;

import com.codepath.submissions.Verdict;

/**
 * Judge0's outcome for one test case. All text fields are decoded and length-capped; any of them may be null.
 *
 * @param verdict           CodePath verdict derived from the Judge0 status
 * @param judge0StatusId    raw Judge0 status id
 * @param statusDescription raw Judge0 status text, e.g. "Wrong Answer"
 * @param timeMs            CPU time in milliseconds
 * @param memoryKb          peak memory in kilobytes
 */
public record JudgeCaseResult(
        Verdict verdict,
        int judge0StatusId,
        String statusDescription,
        Integer timeMs,
        Integer memoryKb,
        String stdout,
        String stderr,
        String compileOutput,
        String message) {

    public boolean passed() {
        return verdict == Verdict.ACCEPTED;
    }
}
