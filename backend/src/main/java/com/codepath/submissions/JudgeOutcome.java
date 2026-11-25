package com.codepath.submissions;

import java.util.ArrayList;
import java.util.List;

import com.codepath.judging.JudgeCaseResult;

/** Aggregated result of running a program against every case of a {@link JudgeJob}. */
record JudgeOutcome(
        Verdict verdict,
        Integer executionTimeMs,
        Integer memoryUsedKb,
        int passedCount,
        int totalCount,
        String compileOutput,
        List<TestCaseResultDto> testCases,
        String log) {

    static final int MAX_LOG_CHARS = 8_000;

    /**
     * Overall verdict is ACCEPTED only when every case passed; otherwise it is the verdict of the first
     * failing case (public cases are ordered before hidden ones). Time and memory are the maximum over cases.
     * The stored log never contains hidden input or expected output.
     */
    static JudgeOutcome of(JudgeJob job, List<JudgeCaseResult> results) {
        if (results.size() != job.cases().size()) {
            throw new IllegalStateException(
                    "Judge returned " + results.size() + " results for " + job.cases().size() + " test cases");
        }
        Verdict overall = Verdict.ACCEPTED;
        int passed = 0;
        Integer maxTime = null;
        Integer maxMemory = null;
        String compileOutput = null;
        List<TestCaseResultDto> dtos = new ArrayList<>(results.size());
        StringBuilder log = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            JudgeJob.JobCase testCase = job.cases().get(i);
            JudgeCaseResult result = results.get(i);
            if (result.passed()) {
                passed++;
            } else if (overall == Verdict.ACCEPTED) {
                overall = result.verdict();
            }
            if (result.verdict() == Verdict.COMPILATION_ERROR && compileOutput == null) {
                compileOutput = result.compileOutput() == null ? "" : result.compileOutput();
            }
            if (result.timeMs() != null) {
                maxTime = maxTime == null ? result.timeMs() : Math.max(maxTime, result.timeMs());
            }
            if (result.memoryKb() != null) {
                maxMemory = maxMemory == null ? result.memoryKb() : Math.max(maxMemory, result.memoryKb());
            }
            dtos.add(TestCaseResultDto.of(i + 1, testCase, result));
            log.append("Test ").append(i + 1).append(testCase.hidden() ? " (hidden): " : " (public): ")
                    .append(result.verdict());
            if (result.timeMs() != null) {
                log.append(", ").append(result.timeMs()).append(" ms");
            }
            if (result.memoryKb() != null) {
                log.append(", ").append(result.memoryKb()).append(" KB");
            }
            log.append('\n');
        }
        if (compileOutput != null && !compileOutput.isBlank()) {
            log.append("--- Compilation output ---\n").append(compileOutput).append('\n');
        }
        String text = log.length() > MAX_LOG_CHARS ? log.substring(0, MAX_LOG_CHARS) : log.toString();
        return new JudgeOutcome(overall, maxTime, maxMemory, passed, results.size(), compileOutput,
                List.copyOf(dtos), text);
    }
}
