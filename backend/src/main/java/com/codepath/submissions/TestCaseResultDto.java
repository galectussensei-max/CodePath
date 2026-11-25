package com.codepath.submissions;

import com.codepath.judging.JudgeCaseResult;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Verified Judge0 result for one test case. For a hidden case only the pass/fail verdict, time and memory
 * are filled in: the input, expected output and the program's output are never included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestCaseResultDto(
        int index,
        boolean hidden,
        Verdict verdict,
        boolean passed,
        Integer timeMs,
        Integer memoryKb,
        String input,
        String expectedOutput,
        String actualOutput,
        String errorOutput) {

    static TestCaseResultDto of(int index, JudgeJob.JobCase testCase, JudgeCaseResult result) {
        if (testCase.hidden()) {
            return new TestCaseResultDto(index, true, result.verdict(), result.passed(), result.timeMs(),
                    result.memoryKb(), null, null, null, null);
        }
        String error = firstNonBlank(result.stderr(), result.message());
        return new TestCaseResultDto(index, false, result.verdict(), result.passed(), result.timeMs(),
                result.memoryKb(), testCase.input(), testCase.expectedOutput(), result.stdout(), error);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
