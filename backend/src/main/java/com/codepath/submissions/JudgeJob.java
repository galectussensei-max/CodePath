package com.codepath.submissions;

import java.util.List;

/**
 * Everything the judge needs for one run, copied out of the database inside a short transaction so no
 * connection is held while Judge0 executes code. Internal only: it holds hidden test data and is never
 * returned to a client.
 */
record JudgeJob(Long questionId, int timeLimitMs, int memoryLimitKb, List<JobCase> cases) {

    record JobCase(Long testCaseId, boolean hidden, String input, String expectedOutput) {
    }
}
