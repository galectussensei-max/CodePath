package com.codepath.submissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.codepath.judging.JudgeCaseResult;
import com.fasterxml.jackson.databind.ObjectMapper;

class JudgeOutcomeTest {

    private static final String SECRET_INPUT = "HIDDEN-INPUT-91823";
    private static final String SECRET_EXPECTED = "HIDDEN-EXPECTED-55512";

    private final JudgeJob job = new JudgeJob(1L, 2000, 262144, List.of(
            new JudgeJob.JobCase(1L, false, "1 2\n", "3"),
            new JudgeJob.JobCase(2L, true, SECRET_INPUT, SECRET_EXPECTED)));

    private static JudgeCaseResult result(Verdict verdict, Integer time, Integer memory, String stdout,
            String stderr, String compile) {
        int status = switch (verdict) {
            case ACCEPTED -> 3;
            case WRONG_ANSWER -> 4;
            case TIME_LIMIT_EXCEEDED -> 5;
            case COMPILATION_ERROR -> 6;
            default -> 11;
        };
        return new JudgeCaseResult(verdict, status, verdict.name(), time, memory, stdout, stderr, compile, null);
    }

    @Test
    void allPassedIsAcceptedWithMaxTimeAndMemory() {
        JudgeOutcome outcome = JudgeOutcome.of(job, List.of(
                result(Verdict.ACCEPTED, 40, 1000, "3", null, null),
                result(Verdict.ACCEPTED, 90, 2000, SECRET_EXPECTED, null, null)));

        assertEquals(Verdict.ACCEPTED, outcome.verdict());
        assertEquals(90, outcome.executionTimeMs());
        assertEquals(2000, outcome.memoryUsedKb());
        assertEquals(2, outcome.passedCount());
        assertEquals(2, outcome.totalCount());
    }

    @Test
    void firstFailingCaseDecidesTheVerdict() {
        JudgeOutcome outcome = JudgeOutcome.of(job, List.of(
                result(Verdict.ACCEPTED, 40, 1000, "3", null, null),
                result(Verdict.TIME_LIMIT_EXCEEDED, 3000, 2000, null, null, null)));

        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, outcome.verdict());
        assertEquals(1, outcome.passedCount());
    }

    @Test
    void compilationErrorKeepsCompilerOutput() {
        JudgeOutcome outcome = JudgeOutcome.of(job, List.of(
                result(Verdict.COMPILATION_ERROR, null, null, null, null, "Main.java:1: error"),
                result(Verdict.COMPILATION_ERROR, null, null, null, null, "Main.java:1: error")));

        assertEquals(Verdict.COMPILATION_ERROR, outcome.verdict());
        assertEquals("Main.java:1: error", outcome.compileOutput());
        assertNull(outcome.executionTimeMs());
        assertTrue(outcome.log().contains("Compilation output"));
    }

    @Test
    void hiddenCaseDetailsNeverReachTheDtoOrTheStoredLog() throws Exception {
        JudgeOutcome outcome = JudgeOutcome.of(job, List.of(
                result(Verdict.ACCEPTED, 40, 1000, "3", null, null),
                result(Verdict.WRONG_ANSWER, 50, 1000, "leaked-output-" + SECRET_EXPECTED, "stderr " + SECRET_INPUT, null)));

        TestCaseResultDto hidden = outcome.testCases().get(1);
        assertTrue(hidden.hidden());
        assertNull(hidden.input());
        assertNull(hidden.expectedOutput());
        assertNull(hidden.actualOutput());
        assertNull(hidden.errorOutput());

        String json = new ObjectMapper().writeValueAsString(SubmissionResultDto.of("SUBMIT", 5L, null, outcome, null));
        assertFalse(json.contains(SECRET_INPUT));
        assertFalse(json.contains(SECRET_EXPECTED));
        assertFalse(outcome.log().contains(SECRET_INPUT));
        assertFalse(outcome.log().contains(SECRET_EXPECTED));
    }

    @Test
    void publicCaseShowsInputExpectedAndActualOutput() {
        JudgeOutcome outcome = JudgeOutcome.of(job, List.of(
                result(Verdict.WRONG_ANSWER, 40, 1000, "4", null, null),
                result(Verdict.ACCEPTED, 40, 1000, "x", null, null)));

        TestCaseResultDto visible = outcome.testCases().get(0);
        assertFalse(visible.hidden());
        assertEquals("1 2\n", visible.input());
        assertEquals("3", visible.expectedOutput());
        assertEquals("4", visible.actualOutput());
    }

    @Test
    void resultCountMustMatchCaseCount() {
        assertThrows(IllegalStateException.class,
                () -> JudgeOutcome.of(job, List.of(result(Verdict.ACCEPTED, 1, 1, "3", null, null))));
    }
}
